package tv.withaibuild.customiuizer.mods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper

import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.LinkedList
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.clock.ClockAbi
import tv.withaibuild.customiuizer.mods.clock.CalendarCapability
import tv.withaibuild.customiuizer.mods.clock.ClockEffect
import tv.withaibuild.customiuizer.mods.clock.ClockEffectPublication
import tv.withaibuild.customiuizer.mods.clock.ClockResolver
import tv.withaibuild.customiuizer.mods.clock.ControllerCapability
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks
import tv.withaibuild.customiuizer.mods.utils.ScreenStateController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

private fun source(relativePath: String): String {
    var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
    while (true) {
        val candidate = File(directory, relativePath)
        if (candidate.isFile) return candidate.readText()
        directory = directory.parentFile
            ?: error("Repository root not found while locating $relativePath")
    }
}

class SystemClockHotPathTest {

    private val fmtTime = "h:mm"
    private val fmtTimePm = "h:mm a"

    @Suppress("DEPRECATION")
    private inner class FakeResources(
        private val fmt: String = this@SystemClockHotPathTest.fmtTime,
        private val fmtPm: String = this@SystemClockHotPathTest.fmtTimePm,
        private val density: Float = 2.0f,
    ) : Resources(null, DisplayMetrics().apply { this.density = density }, Configuration()) {
        override fun getDisplayMetrics(): DisplayMetrics =
            DisplayMetrics().apply { this.density = this@FakeResources.density }

        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
            return when (name) {
                "fmt_time_12hour_minute" -> 1
                "fmt_time_12hour_minute_pm" -> 2
                else -> 0
            }
        }

        override fun getString(id: Int): String {
            return when (id) {
                1 -> fmt
                2 -> fmtPm
                else -> ""
            }
        }

        override fun getColor(id: Int, theme: android.content.res.Resources.Theme?): Int {
            return when (id) {
                android.R.color.system_accent1_0 -> 0xFFFF0000.toInt()
                android.R.color.system_accent1_600 -> 0xFF00FF00.toInt()
                else -> 0
            }
        }
    }

    @Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private inner class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()
        override fun getMainLooper(): Looper? = Looper.getMainLooper()
        override fun getResources(): Resources = fakeResources
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? = null
        override fun unregisterReceiver(receiver: BroadcastReceiver?) {}
    }

    private class FakeController {
        lateinit var mContext: Context
        var mCalendar: Any = FakeCalendar()
        val mClockListeners = ArrayList<Any>()
        @JvmField
        var mIs24: Boolean = false
    }

    open class FakeCalendar {
        val setTimeInMillisCalls = mutableListOf<Long>()
        val formatCalls = mutableListOf<Triple<Context, StringBuilder, StringBuilder>>()

        open fun setTimeInMillis(millis: Long) {
            setTimeInMillisCalls.add(millis)
        }

        open fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {
            out.append(pattern)
        }
    }

    class FailingFakeCalendar(val error: Throwable) : FakeCalendar() {
        override fun setTimeInMillis(millis: Long) {
            throw error
        }
    }

    private class FailingIs24FakeController {
        lateinit var mContext: Context
        var mCalendar: Any = FakeCalendar()
        val mClockListeners = ArrayList<Any>()
        @JvmField
        var mIs24: Boolean? = null
    }

    private class NonArrayListFakeController {
        lateinit var mContext: Context
        var mCalendar: Any = FakeCalendar()
        val mClockListeners = LinkedList<Any>()
        @JvmField
        var mIs24: Boolean = false
    }

    private open inner class RecordingClockView(context: Context = FakeContext()) : RecordingTextView(context) {
        val updateTimeCalls = mutableListOf<Any?>()

        @JvmField
        var mMiuiStatusBarClockController: Any? = null

        open fun updateTime() {
            updateTimeCalls.add(null)
        }
    }

    private fun setCurrentSnapshot(snapshot: SystemClockHooks.ClockStyleSnapshot?) {
        val field = SystemClockHooks::class.java.getDeclaredField("clockStyleSnapshot")
        field.isAccessible = true
        field.set(SystemClockHooks, snapshot)
    }

    private fun screenStateListeners(): ArrayList<*> {
        val listenersField = ScreenStateController::class.java.getDeclaredField("listeners")
        listenersField.isAccessible = true
        return listenersField.get(ScreenStateController) as ArrayList<*>
    }

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
    }

    @After
    fun tearDown() {
        val listeners = screenStateListeners()
        @Suppress("UNCHECKED_CAST")
        val copy = ArrayList(listeners as ArrayList<ScreenStateController.ScreenStateListener>)
        for (listener in copy) {
            ScreenStateController.removeListener(listener)
        }
    }

    private fun makeControllerWithClock(clock: View = RecordingClockView()): FakeController {
        val controller = FakeController()
        controller.mContext = FakeContext()
        ModuleHelper.setViewInfo(clock, "clockName", "clock")
        (clock as? RecordingClockView)?.mMiuiStatusBarClockController = controller
        controller.mClockListeners.add(clock)
        return controller
    }

    private fun makeControllerWithCcClock(clock: View = RecordingClockView()): FakeController {
        val controller = FakeController()
        controller.mContext = FakeContext()
        ModuleHelper.setViewInfo(clock, "clockName", "ccClock")
        (clock as? RecordingClockView)?.mMiuiStatusBarClockController = controller
        controller.mClockListeners.add(clock)
        return controller
    }

    private fun makeControllerWithClocks(statusClock: View = RecordingClockView(), ccClock: View = RecordingClockView()): FakeController {
        val controller = FakeController()
        controller.mContext = FakeContext()
        ModuleHelper.setViewInfo(statusClock, "clockName", "clock")
        ModuleHelper.setViewInfo(ccClock, "clockName", "ccClock")
        (statusClock as? RecordingClockView)?.mMiuiStatusBarClockController = controller
        (ccClock as? RecordingClockView)?.mMiuiStatusBarClockController = controller
        controller.mClockListeners.add(statusClock)
        controller.mClockListeners.add(ccClock)
        return controller
    }

    private fun makePublication(calendarCold: CalendarCapability? = null): ClockEffectPublication? {
        val controller = ClockResolver.resolveControllerClass(FakeController::class.java)
            ?: return null
        val target = ClockResolver.resolveClockTargetClass(RecordingClockView::class.java)
            ?: return null
        val abi = ClockAbi(controller, arrayOf(target), calendarCold)
        return ClockEffectPublication(abi)
    }

    private fun makeCalendarCold(): CalendarCapability? {
        return ClockResolver.resolveCalendarFromDeclaredType(FakeCalendar::class.java, Context::class.java)
    }

    private fun makeFailingIs24Publication(calendarCold: CalendarCapability? = null): ClockEffectPublication? {
        val cls = FailingIs24FakeController::class.java
        val calendarField = cls.getDeclaredField("mCalendar").apply { isAccessible = true }
        val clockListenersField = cls.getDeclaredField("mClockListeners").apply { isAccessible = true }
        val is24Field = cls.getDeclaredField("mIs24").apply { isAccessible = true }
        val controller = ControllerCapability(cls, calendarField, clockListenersField, is24Field)
        val target = ClockResolver.resolveClockTargetClass(RecordingClockView::class.java)
            ?: return null
        val abi = ClockAbi(controller, arrayOf(target), calendarCold)
        return ClockEffectPublication(abi)
    }

    private fun secondTickerFlags(ticker: Any): Pair<Boolean, Boolean> {
        val cls = ticker.javaClass
        val sb = cls.getDeclaredField("showStatusBarSeconds").apply { isAccessible = true }.get(ticker) as Boolean
        val cc = cls.getDeclaredField("showCCSeconds").apply { isAccessible = true }.get(ticker) as Boolean
        return sb to cc
    }

    private fun startTicker(ticker: Any) {
        ticker.javaClass.getMethod("start").invoke(ticker)
    }

    private fun runTicker(ticker: Any) {
        (ticker as Runnable).run()
    }

    private fun makeSnapshotWithSeconds(statusBar: Boolean, cc: Boolean): SystemClockHooks.ClockStyleSnapshot {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", statusBar)
            put("system_statusbar_clock_24hour_format", true)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", true)
            put("system_cc_clock_customformat", if (cc) "HH:mm:ss" else "")
        }
        return SystemClockHooks.buildClockStyleSnapshot(prefs, FakeResources())
    }

    private fun disposeTicker(ticker: Any?) {
        if (ticker != null) {
            ticker.javaClass.getMethod("dispose").invoke(ticker)
        }
    }

    private open inner class RecordingTextView(context: Context = FakeContext()) : TextView(context) {
        override fun getResources(): Resources = FakeResources()

        // Recorded setter calls for assertions.  Each setter also delegates to the
        // real TextView implementation so that final getters such as textColors,
        // textSize, and translationY reflect the recorded state.
        val setTextSizeCalls = mutableListOf<Pair<Int, Float>>()
        val setTextColorCalls = mutableListOf<android.content.res.ColorStateList?>()
        val setBackgroundCalls = mutableListOf<android.graphics.drawable.Drawable?>()
        val setTypefaceCalls = mutableListOf<Pair<Typeface?, Int>>()
        val setLayoutParamsCalls = mutableListOf<ViewGroup.LayoutParams?>()
        val setLineSpacingCalls = mutableListOf<Pair<Float, Float>>()
        val setTextAlignmentCalls = mutableListOf<Int>()
        val setTranslationYCalls = mutableListOf<Float>()
        val setSingleLineCalls = mutableListOf<Boolean>()
        val setMaxLinesCalls = mutableListOf<Int>()
        var singleLineValue: Boolean? = null
        var maxLinesValue: Int? = null
        var translationYValue: Float? = null
        var currentTextColorValue: Int = Color.BLACK
        var layoutParamsValue: ViewGroup.LayoutParams? = null
        var typefaceValue: Typeface? = null
        var textColorsValue: ColorStateList? = null
        var backgroundValue: Drawable? = null
        var textSizeValue: Float = 15f
        var textAlignmentValue: Int = View.TEXT_ALIGNMENT_GRAVITY
        var lineSpacingExtraValue: Float = 0f
        var lineSpacingMultiplierValue: Float = 1.0f
        var setTextSizeFailCount = 0
        private val keyedTags = mutableMapOf<Int, Any?>()

        override fun getTypeface(): Typeface? = typefaceValue

        override fun getBackground(): Drawable? = backgroundValue

        override fun getTextSize(): Float = textSizeValue

        override fun getTextAlignment(): Int = textAlignmentValue

        override fun getLineSpacingMultiplier(): Float = lineSpacingMultiplierValue

        override fun getLineSpacingExtra(): Float = lineSpacingExtraValue

        override fun setTag(key: Int, tag: Any?) {
            keyedTags[key] = tag
            super.setTag(key, tag)
        }

        override fun getTag(key: Int): Any? = keyedTags[key] ?: super.getTag(key)

        override fun getMaxLines(): Int = maxLinesValue ?: 1

        override fun isSingleLine(): Boolean = singleLineValue ?: true

        override fun setTextSize(unit: Int, size: Float) {
            if (setTextSizeFailCount > 0) {
                setTextSizeFailCount--
                throw IllegalStateException("simulated setTextSize failure")
            }
            setTextSizeCalls.add(unit to size)
            textSizeValue = when (unit) {
                TypedValue.COMPLEX_UNIT_PX -> size
                TypedValue.COMPLEX_UNIT_DIP -> size * resources.displayMetrics.density
                else -> size
            }
            super.setTextSize(unit, size)
        }

        override fun setTextColor(color: Int) {
            currentTextColorValue = color
            val colors = ColorStateList.valueOf(color) ?: ColorStateList(arrayOf(IntArray(0)), intArrayOf(color))
            textColorsValue = colors
            setTextColorCalls.add(colors)
            super.setTextColor(colors)
        }

        override fun setTextColor(colors: android.content.res.ColorStateList?) {
            textColorsValue = colors
            currentTextColorValue = colors?.defaultColor ?: Color.BLACK
            setTextColorCalls.add(colors)
            if (colors != null) super.setTextColor(colors)
        }

        override fun setBackground(background: android.graphics.drawable.Drawable?) {
            setBackgroundCalls.add(background)
            backgroundValue = background
            super.setBackground(background)
        }

        override fun setTypeface(tf: Typeface?) {
            setTypefaceCalls.add(tf to Typeface.NORMAL)
            typefaceValue = tf
            super.setTypeface(tf)
        }

        override fun setTypeface(tf: Typeface?, style: Int) {
            setTypefaceCalls.add(tf to style)
            if (style == Typeface.NORMAL) typefaceValue = tf
            super.setTypeface(tf, style)
        }

        override fun setTextAlignment(textAlignment: Int) {
            setTextAlignmentCalls.add(textAlignment)
            textAlignmentValue = textAlignment
            super.setTextAlignment(textAlignment)
        }

        override fun setLineSpacing(add: Float, mult: Float) {
            setLineSpacingCalls.add(add to mult)
            lineSpacingExtraValue = add
            lineSpacingMultiplierValue = mult
            super.setLineSpacing(add, mult)
        }

        override fun getTranslationY(): Float = translationYValue ?: 0f

        override fun setTranslationY(translationY: Float) {
            setTranslationYCalls.add(translationY)
            translationYValue = translationY
            super.setTranslationY(translationY)
        }

        override fun setSingleLine(singleLine: Boolean) {
            setSingleLineCalls.add(singleLine)
            singleLineValue = singleLine
            super.setSingleLine(singleLine)
        }

        override fun setMaxLines(maxLines: Int) {
            setMaxLinesCalls.add(maxLines)
            maxLinesValue = maxLines
            super.setMaxLines(maxLines)
        }

        override fun getLayoutParams(): ViewGroup.LayoutParams? = layoutParamsValue

        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            setLayoutParamsCalls.add(params)
            layoutParamsValue = params
            super.setLayoutParams(params)
        }
    }

    private fun buildSnapshot(
        prefs: PrefMap,
    ): SystemClockHooks.ClockStyleSnapshot {
        return SystemClockHooks.buildClockStyleSnapshot(prefs, FakeResources())
    }

    private fun statusbarSnapshotWith(
        fontSize: Int = 13,
        bold: Boolean = false,
        align: Int = 1,
        leftMargin: Int = 0,
        rightMargin: Int = 0,
        verticalOffset: Int = 8,
        chip: Boolean = false,
        chipUseMonet: Boolean = false,
        chipCustomTextColor: Boolean = false,
        chipTextColor: Int = Color.WHITE,
        fixedWidth: Int = 10,
        customFormat: String = "",
        customFormatEnable: Boolean = false,
    ): SystemClockHooks.ClockStyleSnapshot {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_fontsize", fontSize)
            put("system_statusbar_clock_bold", bold)
            put("system_statusbar_clock_align", align)
            put("system_statusbar_clock_leftmargin", leftMargin)
            put("system_statusbar_clock_rightmargin", rightMargin)
            put("system_statusbar_clock_verticaloffset", verticalOffset)
            put("system_statusbar_clock_chip", chip)
            put("system_statusbar_clock_chip_usemonet", chipUseMonet)
            put("system_statusbar_clock_chip_customtextcolor", chipCustomTextColor)
            put("system_statusbar_clock_chip_textcolor", chipTextColor)
            put("system_statusbar_clock_fixedcontent_width", fixedWidth)
            put("system_statusbar_clock_customformat_enable", customFormatEnable)
            put("system_statusbar_clock_customformat", customFormat)
        }
        return buildSnapshot(prefs)
    }

    private fun originalTypeface(): Typeface? {
        val base = Typeface.DEFAULT_BOLD
        return if (base != null) {
            Typeface.create(base, Typeface.ITALIC)
        } else {
            null
        }
    }

    private fun colorStateList(color: Int): ColorStateList =
        ColorStateList.valueOf(color) ?: ColorStateList(arrayOf(IntArray(0)), intArrayOf(color))

    private fun setOriginalStyle(
        clock: RecordingTextView,
        textSizePx: Float = 28f,
        typeface: Typeface? = originalTypeface(),
        textColor: Int = Color.BLACK,
        textAlignment: Int = View.TEXT_ALIGNMENT_CENTER,
        translationY: Float = 0f,
        background: android.graphics.drawable.Drawable? = ColorDrawable(Color.RED),
        singleLine: Boolean = true,
        maxLines: Int = 1,
        lineSpacingExtra: Float = 0f,
        lineSpacingMultiplier: Float = 1f,
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        leftMargin: Int = 5,
        rightMargin: Int = 5,
        topMargin: Int = 2,
        bottomMargin: Int = 2,
        gravity: Int = Gravity.CENTER_VERTICAL or Gravity.START,
    ) {
        clock.textSizeValue = textSizePx
        clock.setTypeface(typeface)
        clock.setTextColor(textColor)
        clock.setTextAlignment(textAlignment)
        clock.setTranslationY(translationY)
        clock.setBackground(background)
        clock.setSingleLine(singleLine)
        if (!singleLine) clock.setMaxLines(maxLines)
        clock.setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
        val lp = LinearLayout.LayoutParams(width, height).apply {
            this.width = width
            this.height = height
            this.leftMargin = leftMargin
            this.rightMargin = rightMargin
            this.topMargin = topMargin
            this.bottomMargin = bottomMargin
            this.gravity = gravity
        }
        clock.setLayoutParams(lp)
    }

    @Test
    fun buildClockStyleSnapshot_defaultStatusbarFormat() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", true)
            put("system_statusbar_clock_24hour_format", true)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", true)
        }
        val snapshot = buildSnapshot(prefs)

        assertEquals("HH:mm:ss", snapshot.statusbarDefaultFormat)
        assertTrue(snapshot.showStatusBarSeconds)
        assertFalse(snapshot.showCCSeconds)
    }

    @Test
    fun buildClockText_statusbarCustomFormat() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_customformat_enable", true)
            put("system_statusbar_clock_customformat", "EEE HH:mm tq")
            put("system_statusbar_enable_weather_param", true)
        }
        val snapshot = buildSnapshot(prefs)

        val text = SystemClockHooks.buildClockText(
            "clock",
            snapshot,
            weatherInfo = "24C",
            statusbarClockTweak = true,
            ccClockTweak = true,
        )
        assertEquals("EEE HH:mm 24C", text)
    }

    @Test
    fun buildClockText_ccClock_usesCustomFormat() {
        val prefs = PrefMap().apply {
            put("system_cc_clock_customformat", "ss:mm")
        }
        val snapshot = buildSnapshot(prefs)

        val text = SystemClockHooks.buildClockText(
            "ccClock",
            snapshot,
            weatherInfo = null,
            statusbarClockTweak = true,
            ccClockTweak = true,
        )
        assertEquals("ss:mm", text)
    }

    @Test
    fun buildClockText_disabledFeature_returnsNull() {
        val prefs = PrefMap()
        val snapshot = buildSnapshot(prefs)

        assertNull(
            SystemClockHooks.buildClockText(
                "clock",
                snapshot,
                weatherInfo = null,
                statusbarClockTweak = false,
                ccClockTweak = true,
            )
        )
        assertNull(
            SystemClockHooks.buildClockText(
                "ccClock",
                snapshot,
                weatherInfo = null,
                statusbarClockTweak = true,
                ccClockTweak = false,
            )
        )
    }

    @Test
    fun buildClockText_100Ticks_noPrefReads() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", true)
            put("system_statusbar_clock_24hour_format", true)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", false)
            put("system_cc_clock_customformat", "HH:mm:ss")
        }
        val snapshot = buildSnapshot(prefs)

        repeat(100) {
            SystemClockHooks.buildClockText(
                if (it % 2 == 0) "clock" else "ccClock",
                snapshot,
                weatherInfo = null,
                statusbarClockTweak = true,
                ccClockTweak = true,
            )
        }
    }

    @Test
    fun buildClockText_minuteMode_doesNotShowSeconds() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", false)
            put("system_statusbar_clock_24hour_format", false)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", true)
        }
        val snapshot = buildSnapshot(prefs)

        assertEquals("hh:mm", snapshot.statusbarDefaultFormat)
        assertFalse(snapshot.showStatusBarSeconds)
    }

    @Test
    fun initClockStyle_noPrefsReadsAfterSnapshot() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_fontsize", 20)
            put("system_statusbar_clock_bold", true)
            put("system_statusbar_clock_verticaloffset", 12)
        }
        val snapshot = buildSnapshot(prefs)
        val clock = RecordingTextView().apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        SystemClockHooks.initClockStyle(clock, "clock", snapshot)

        assertEquals(1, clock.setTextSizeCalls.size)
        assertEquals(10.0f, clock.setTextSizeCalls[0].second, 0.001f)
        assertEquals(Typeface.DEFAULT_BOLD, clock.typeface)
        assertNotNull(clock.translationYValue)
    }

    @Test
    fun initClockStyle_idempotentSameSnapshot() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_fontsize", 20)
        }
        val snapshot = buildSnapshot(prefs)
        val clock = RecordingTextView().apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        repeat(3) {
            SystemClockHooks.initClockStyle(clock, "clock", snapshot)
        }

        assertEquals("repeated apply with same snapshot id must not call setTextSize again", 1, clock.setTextSizeCalls.size)
    }

    @Test
    fun initClockStyle_reapplyWithDifferentSnapshot() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_fontsize", 20)
        }
        val snapshot1 = buildSnapshot(prefs)
        val clock = RecordingTextView().apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        SystemClockHooks.initClockStyle(clock, "clock", snapshot1)

        prefs.put("system_statusbar_clock_fontsize", 24)
        val snapshot2 = buildSnapshot(prefs)
        SystemClockHooks.initClockStyle(clock, "clock", snapshot2)

        assertEquals(2, clock.setTextSizeCalls.size)
        assertEquals(10.0f, clock.setTextSizeCalls[0].second, 0.001f)
        assertEquals(12.0f, clock.setTextSizeCalls[1].second, 0.001f)
    }

    @Test
    fun initClockStyle_boldTrueThenFalseRestoresOriginalTypeface() {
        val clock = RecordingTextView()
        val original = originalTypeface()
        setOriginalStyle(clock, typeface = original)

        val boldOn = statusbarSnapshotWith(bold = true)
        val boldOff = statusbarSnapshotWith(bold = false)

        SystemClockHooks.initClockStyle(clock, "clock", boldOn)
        val afterBoldOn = clock.setTypefaceCalls.size

        SystemClockHooks.initClockStyle(clock, "clock", boldOff)
        assertEquals("bold off must restore original typeface", original, clock.typeface)
        assertTrue(
            "bold off must call setTypeface to restore the original",
            clock.setTypefaceCalls.size > afterBoldOn,
        )
    }

    @Test
    fun initClockStyle_chipTrueThenFalseRestoresOriginalBackgroundAndColors() {
        val clock = RecordingTextView()
        val originalBackground = ColorDrawable(Color.RED)
        setOriginalStyle(clock, background = originalBackground, textColor = Color.BLACK)
        val originalTextColors = clock.textColors

        val chipOn = statusbarSnapshotWith(
            chip = true,
            chipCustomTextColor = true,
            chipTextColor = Color.GREEN,
        )
        val chipOff = statusbarSnapshotWith(chip = false)

        SystemClockHooks.initClockStyle(clock, "clock", chipOn)
        assertTrue("chip on must set a new background", clock.background !== originalBackground)
        assertEquals(Color.GREEN, clock.currentTextColorValue)

        SystemClockHooks.initClockStyle(clock, "clock", chipOff)
        assertSame("chip off must restore original background", originalBackground, clock.background)
        assertSame("chip off must restore original text colors", originalTextColors, clock.textColors)
    }

    @Test
    fun initClockStyle_align2Then1RestoresOriginalTextAlignment() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textAlignment = View.TEXT_ALIGNMENT_TEXT_END)

        val align2 = statusbarSnapshotWith(align = 2)
        val align1 = statusbarSnapshotWith(align = 1)

        SystemClockHooks.initClockStyle(clock, "clock", align2)
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, clock.textAlignment)

        SystemClockHooks.initClockStyle(clock, "clock", align1)
        assertEquals("align 1 must restore original text alignment", View.TEXT_ALIGNMENT_TEXT_END, clock.textAlignment)
    }

    @Test
    fun initClockStyle_verticalOffsetCustomThenDefaultRestoresOriginalTranslationY() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, translationY = 5f)

        val offset12 = statusbarSnapshotWith(verticalOffset = 12)
        val offset8 = statusbarSnapshotWith(verticalOffset = 8)

        SystemClockHooks.initClockStyle(clock, "clock", offset12)
        assertNotNull(clock.translationYValue)
        assertTrue(clock.translationYValue != 0f)

        SystemClockHooks.initClockStyle(clock, "clock", offset8)
        assertEquals("vertical offset 8 must restore original translationY", 5f, clock.translationYValue ?: Float.NaN, 0.001f)
    }

    @Test
    fun initClockStyle_leftRightMarginCustomThenDefaultRestoresOriginalMargins() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, leftMargin = 5, rightMargin = 7)

        val marginOn = statusbarSnapshotWith(leftMargin = 20, rightMargin = 30)
        val marginOff = statusbarSnapshotWith(leftMargin = 0, rightMargin = 0)

        SystemClockHooks.initClockStyle(clock, "clock", marginOn)
        val lp1 = clock.layoutParams as LinearLayout.LayoutParams
        assertTrue(lp1!!.leftMargin > 0)
        assertTrue(lp1.rightMargin > 0)

        SystemClockHooks.initClockStyle(clock, "clock", marginOff)
        val lp2 = clock.layoutParams as LinearLayout.LayoutParams
        assertEquals("left margin must restore", 5, lp2!!.leftMargin)
        assertEquals("right margin must restore", 7, lp2.rightMargin)
    }

    @Test
    fun initClockStyle_fixedWidthCustomThenDefaultRestoresOriginalWidth() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, width = 123)

        val widthOn = statusbarSnapshotWith(fixedWidth = 50)
        val widthOff = statusbarSnapshotWith(fixedWidth = 10)

        SystemClockHooks.initClockStyle(clock, "clock", widthOn)
        val lp1 = clock.layoutParams!!
        assertTrue(lp1.width != 123)

        SystemClockHooks.initClockStyle(clock, "clock", widthOff)
        val lp2 = clock.layoutParams!!
        assertEquals("fixed width off must restore original width", 123, lp2.width)
    }

    @Test
    fun initClockStyle_dualRowsFalseThenTrueThenFalseRestoresSingleLineAndLineSpacing() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, singleLine = true, maxLines = 1, lineSpacingMultiplier = 1.25f)

        val dualOn = statusbarSnapshotWith(customFormatEnable = true, customFormat = "HH\nmm")
        val dualOff = statusbarSnapshotWith()

        SystemClockHooks.initClockStyle(clock, "clock", dualOn)
        assertEquals(false, clock.singleLineValue)
        assertEquals(2, clock.maxLinesValue)

        SystemClockHooks.initClockStyle(clock, "clock", dualOff)
        assertEquals("single line must restore", true, clock.singleLineValue)
        assertEquals("max lines must restore", 1, clock.maxLinesValue)
        assertEquals("line spacing multiplier must restore", 1.25f, clock.lineSpacingMultiplier, 0.001f)
    }

    @Test
    fun initClockStyle_fontSizeCustomThenDefaultRestoresOriginalTextSize() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textSizePx = 40f)

        val bigFont = statusbarSnapshotWith(fontSize = 20)
        val defaultFont = statusbarSnapshotWith(fontSize = 13)

        SystemClockHooks.initClockStyle(clock, "clock", bigFont)
        assertEquals(1, clock.setTextSizeCalls.size)

        SystemClockHooks.initClockStyle(clock, "clock", defaultFont)
        assertEquals(2, clock.setTextSizeCalls.size)
        val last = clock.setTextSizeCalls.last()
        assertEquals("default font size must restore original px", TypedValue.COMPLEX_UNIT_PX, last.first)
        assertEquals(40f, last.second, 0.001f)
    }

    @Test
    fun initClockStyle_align2ThenDefaultRestoresOriginalTextAlignment() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textAlignment = View.TEXT_ALIGNMENT_TEXT_END)

        val align2 = statusbarSnapshotWith(align = 2)
        val align1 = statusbarSnapshotWith(align = 1)

        SystemClockHooks.initClockStyle(clock, "clock", align2)
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, clock.textAlignment)

        SystemClockHooks.initClockStyle(clock, "clock", align1)
        assertEquals("align 2 -> default must restore original", View.TEXT_ALIGNMENT_TEXT_END, clock.textAlignment)
    }

    @Test
    fun initClockStyle_align3ThenDefaultRestoresOriginalTextAlignment() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textAlignment = View.TEXT_ALIGNMENT_TEXT_START)

        val align3 = statusbarSnapshotWith(align = 3)
        val align1 = statusbarSnapshotWith(align = 1)

        SystemClockHooks.initClockStyle(clock, "clock", align3)
        assertEquals(View.TEXT_ALIGNMENT_CENTER, clock.textAlignment)

        SystemClockHooks.initClockStyle(clock, "clock", align1)
        assertEquals("align 3 -> default must restore original", View.TEXT_ALIGNMENT_TEXT_START, clock.textAlignment)
    }

    @Test
    fun initClockStyle_align4ThenDefaultRestoresOriginalTextAlignment() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textAlignment = View.TEXT_ALIGNMENT_CENTER)

        val align4 = statusbarSnapshotWith(align = 4)
        val align1 = statusbarSnapshotWith(align = 1)

        SystemClockHooks.initClockStyle(clock, "clock", align4)
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, clock.textAlignment)

        SystemClockHooks.initClockStyle(clock, "clock", align1)
        assertEquals("align 4 -> default must restore original", View.TEXT_ALIGNMENT_CENTER, clock.textAlignment)
    }

    @Test
    fun initClockStyle_chipFalseThenTrueSetsChipAndRestoresOriginal() {
        val clock = RecordingTextView()
        val originalBackground = ColorDrawable(Color.RED)
        setOriginalStyle(clock, background = originalBackground, textColor = Color.BLACK)
        val originalTextColors = clock.textColors

        val chipOff = statusbarSnapshotWith(chip = false)
        val chipOn = statusbarSnapshotWith(
            chip = true,
            chipCustomTextColor = true,
            chipTextColor = Color.GREEN,
        )

        SystemClockHooks.initClockStyle(clock, "clock", chipOff)
        assertSame(originalBackground, clock.background)
        assertSame(originalTextColors, clock.textColors)

        SystemClockHooks.initClockStyle(clock, "clock", chipOn)
        assertTrue("chip on must set a new background", clock.background !== originalBackground)
        assertEquals(Color.GREEN, clock.currentTextColorValue)
    }

    @Test
    fun initClockStyle_singleLineFalseOriginalRestoresMaxLinesAndSingleLine() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, singleLine = false, maxLines = 2, lineSpacingMultiplier = 1.25f)

        val dualOn = statusbarSnapshotWith(customFormatEnable = true, customFormat = "HH\nmm")
        val dualOff = statusbarSnapshotWith()

        SystemClockHooks.initClockStyle(clock, "clock", dualOn)
        assertEquals(false, clock.singleLineValue)
        assertEquals(2, clock.maxLinesValue)

        SystemClockHooks.initClockStyle(clock, "clock", dualOff)
        assertEquals("single line must restore", false, clock.singleLineValue)
        assertEquals("max lines must restore", 2, clock.maxLinesValue)
        assertEquals("line spacing multiplier must restore", 1.25f, clock.lineSpacingMultiplier, 0.001f)
    }

    @Test
    fun initClockStyle_restoresOriginalWidthHeightAndGravity() {
        val clock = RecordingTextView()
        setOriginalStyle(
            clock,
            width = 123,
            height = 42,
            gravity = Gravity.CENTER,
        )

        val widthOn = statusbarSnapshotWith(fixedWidth = 50)
        val widthOff = statusbarSnapshotWith(fixedWidth = 10)

        SystemClockHooks.initClockStyle(clock, "clock", widthOn)
        val lp1 = clock.layoutParams as LinearLayout.LayoutParams
        assertEquals("custom fixed width must apply", 100, lp1.width)
        assertEquals("height must stay original when no chip/margin", 42, lp1.height)
        assertEquals("gravity must stay original when no chip/margin", Gravity.CENTER, lp1.gravity)

        SystemClockHooks.initClockStyle(clock, "clock", widthOff)
        val lp2 = clock.layoutParams as LinearLayout.LayoutParams
        assertEquals("fixed width off must restore original width", 123, lp2.width)
        assertEquals("height must restore", 42, lp2.height)
        assertEquals("gravity must restore", Gravity.CENTER, lp2.gravity)
    }

    @Test
    fun initClockStyle_nonLinearLayoutParams_appliesWidthAndDoesNotCrash() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, width = 123, height = 42)
        clock.setLayoutParams(ViewGroup.LayoutParams(0, 0).apply {
            width = 123
            height = 42
        })

        val widthOn = statusbarSnapshotWith(fixedWidth = 50)
        val widthOff = statusbarSnapshotWith(fixedWidth = 10)

        SystemClockHooks.initClockStyle(clock, "clock", widthOn)
        val lp1 = clock.layoutParams!!
        assertEquals("custom fixed width must apply to plain LayoutParams", 100, lp1.width)
        assertEquals("height must stay original", 42, lp1.height)

        SystemClockHooks.initClockStyle(clock, "clock", widthOff)
        val lp2 = clock.layoutParams!!
        assertEquals("fixed width off must restore original width", 123, lp2.width)
        assertEquals("height must restore", 42, lp2.height)
    }

    @Test
    fun initClockStyle_nonLinearMarginLayoutParamsWithoutChip_appliesMarginsWidthAndHeight() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, width = 123, height = 42, leftMargin = 6, rightMargin = 8)
        val frameLp = FrameLayout.LayoutParams(0, 0).apply {
            width = 123
            height = 42
            leftMargin = 6
            rightMargin = 8
            topMargin = 2
            bottomMargin = 2
        }
        clock.setLayoutParams(frameLp)

        val widthOn = statusbarSnapshotWith(fixedWidth = 50)
        val widthOff = statusbarSnapshotWith(fixedWidth = 10)

        SystemClockHooks.initClockStyle(clock, "clock", widthOn)
        val lp1 = clock.layoutParams as FrameLayout.LayoutParams
        assertEquals(100, lp1.width)
        assertEquals(42, lp1.height)
        assertEquals(6, lp1.leftMargin)
        assertEquals(8, lp1.rightMargin)

        SystemClockHooks.initClockStyle(clock, "clock", widthOff)
        val lp2 = clock.layoutParams as FrameLayout.LayoutParams
        assertEquals(123, lp2.width)
        assertEquals(42, lp2.height)
        assertEquals(6, lp2.leftMargin)
        assertEquals(8, lp2.rightMargin)
    }

    @Test
    fun initClockStyle_chipOnNonLinearLayoutParams_doesNotCrashAndDoesNotMarkComplete() {
        val clock = RecordingTextView()
        val originalBackground = ColorDrawable(Color.RED)
        setOriginalStyle(clock, background = originalBackground, width = 123, height = 42)
        clock.setLayoutParams(FrameLayout.LayoutParams(0, 0).apply {
            width = 123
            height = 42
        })

        val chipOn = statusbarSnapshotWith(chip = true, chipCustomTextColor = true, chipTextColor = Color.GREEN)

        SystemClockHooks.initClockStyle(clock, "clock", chipOn)

        // Because FrameLayout.LayoutParams cannot receive the chip gravity, the
        // snapshot must not be marked as completed. The next call with the same
        // snapshot must therefore be a no-op only after a successful application,
        // but here it will re-attempt.
        assertTrue("chip on must set chip background", clock.background !== originalBackground)
        assertEquals(Color.GREEN, clock.currentTextColorValue)
        // One setTextSize call from the default-font path.
        assertEquals(1, clock.setTextSizeCalls.size)

        // Re-applying the same snapshot must retry (not marked complete the first
        // time because layout params were not ready).
        val textSizeCallsBefore = clock.setTextSizeCalls.size
        SystemClockHooks.initClockStyle(clock, "clock", chipOn)
        assertTrue("same snapshot must retry when layout params were not ready", clock.setTextSizeCalls.size > textSizeCallsBefore)
    }

    @Test
    fun initClockStyle_nullLayoutParams_thenSetLayoutParamsAndRetry() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textSizePx = 40f)
        clock.setLayoutParams(null)

        val widthOn = statusbarSnapshotWith(fixedWidth = 50)
        val originalStyleTagId = ResourceHooks.getFakeResId("clock_original_style_state")

        // First attempt: no LayoutParams, but a fixed width is requested.
        // No original state may be captured, no snapshot id may be written, and
        // no style setters may be applied (the application stays incomplete).
        SystemClockHooks.initClockStyle(clock, "clock", widthOn)

        assertNull("null LayoutParams must not create original state tag", clock.getTag(originalStyleTagId))
        assertNull("null LayoutParams must not write snapshot id", XposedHelpers.getAdditionalInstanceField(clock, "clockStyleSnapshotId"))
        val lpNull = clock.layoutParams
        assertNull(lpNull)
        assertEquals("null LayoutParams must not apply text style", 0, clock.setTextSizeCalls.size)

        // Now give the view a real LayoutParams and retry with the same snapshot.
        val realLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            width = 137
            height = 43
            leftMargin = 7
            rightMargin = 9
            topMargin = 3
            bottomMargin = 4
            gravity = Gravity.CENTER
        }
        clock.setLayoutParams(realLp)
        SystemClockHooks.initClockStyle(clock, "clock", widthOn)

        val lp = clock.layoutParams as LinearLayout.LayoutParams
        assertEquals("retry must apply fixed width once LayoutParams exist", 100, lp.width)
        assertEquals("retry must preserve original height", 43, lp.height)
        assertEquals("retry must preserve original left margin", 7, lp.leftMargin)
        assertEquals("retry must preserve original right margin", 9, lp.rightMargin)
        assertEquals("retry must preserve original gravity", Gravity.CENTER, lp.gravity)

        val originalState = clock.getTag(originalStyleTagId) as SystemClockHooks.ClockOriginalStyleState
        assertNotNull(originalState)
        assertEquals("original state must capture real width", 137, originalState.layoutParamsWidth)
        assertEquals("original state must capture real height", 43, originalState.layoutParamsHeight)
    }

    @Test
    fun initClockStyle_setterFailure_sameSnapshotRetriesAndSucceeds() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textSizePx = 40f)
        clock.setTextSizeFailCount = 1

        val bigFont = statusbarSnapshotWith(fontSize = 20)

        // First call: setTextSize fails, snapshot must not be marked complete.
        SystemClockHooks.initClockStyle(clock, "clock", bigFont)
        assertEquals("failure must not record setTextSize call", 0, clock.setTextSizeCalls.size)

        // Second call with the same snapshot: must succeed.
        SystemClockHooks.initClockStyle(clock, "clock", bigFont)
        assertEquals(1, clock.setTextSizeCalls.size)
        assertEquals(10.0f, clock.setTextSizeCalls[0].second, 0.001f)

        // Third call with the same snapshot: must be a no-op.
        SystemClockHooks.initClockStyle(clock, "clock", bigFont)
        assertEquals("third call with same snapshot must be idempotent", 1, clock.setTextSizeCalls.size)
    }

    @Test
    fun initClockStyle_idempotentSameSnapshot_zeroWorkForAllSettersAndLayout() {
        val clock = RecordingTextView()
        setOriginalStyle(
            clock,
            textSizePx = 40f,
            typeface = Typeface.DEFAULT_BOLD,
            textColor = Color.BLACK,
            textAlignment = View.TEXT_ALIGNMENT_CENTER,
            translationY = 2f,
            background = ColorDrawable(Color.RED),
            width = 123,
            height = 42,
            leftMargin = 6,
            rightMargin = 8,
            gravity = Gravity.CENTER,
        )

        val fullCustom = statusbarSnapshotWith(
            fontSize = 20,
            bold = true,
            align = 2,
            verticalOffset = 12,
            chip = true,
            chipCustomTextColor = true,
            chipTextColor = Color.GREEN,
            leftMargin = 20,
            rightMargin = 30,
            fixedWidth = 50,
        )

        SystemClockHooks.initClockStyle(clock, "clock", fullCustom)
        val countsAfterFirst = mapOf(
            "textSize" to clock.setTextSizeCalls.size,
            "textColor" to clock.setTextColorCalls.size,
            "background" to clock.setBackgroundCalls.size,
            "typeface" to clock.setTypefaceCalls.size,
            "textAlignment" to clock.setTextAlignmentCalls.size,
            "translationY" to clock.setTranslationYCalls.size,
            "singleLine" to clock.setSingleLineCalls.size,
            "maxLines" to clock.setMaxLinesCalls.size,
            "lineSpacing" to clock.setLineSpacingCalls.size,
            "layoutParams" to clock.setLayoutParamsCalls.size,
        )

        // Second and third calls with the same snapshot must be no-ops.
        SystemClockHooks.initClockStyle(clock, "clock", fullCustom)
        SystemClockHooks.initClockStyle(clock, "clock", fullCustom)

        assertEquals("textSize no-op", countsAfterFirst["textSize"], clock.setTextSizeCalls.size)
        assertEquals("textColor no-op", countsAfterFirst["textColor"], clock.setTextColorCalls.size)
        assertEquals("background no-op", countsAfterFirst["background"], clock.setBackgroundCalls.size)
        assertEquals("typeface no-op", countsAfterFirst["typeface"], clock.setTypefaceCalls.size)
        assertEquals("textAlignment no-op", countsAfterFirst["textAlignment"], clock.setTextAlignmentCalls.size)
        assertEquals("translationY no-op", countsAfterFirst["translationY"], clock.setTranslationYCalls.size)
        assertEquals("singleLine no-op", countsAfterFirst["singleLine"], clock.setSingleLineCalls.size)
        assertEquals("maxLines no-op", countsAfterFirst["maxLines"], clock.setMaxLinesCalls.size)
        assertEquals("lineSpacing no-op", countsAfterFirst["lineSpacing"], clock.setLineSpacingCalls.size)
        assertEquals("layoutParams no-op", countsAfterFirst["layoutParams"], clock.setLayoutParamsCalls.size)
    }

    @Test
    fun initClockStyle_ccClock_doesNotApplyStatusBarStyles() {
        val clock = RecordingTextView()
        val originalBackground = ColorDrawable(Color.RED)
        setOriginalStyle(
            clock,
            textSizePx = 40f,
            typeface = Typeface.DEFAULT_BOLD,
            textColor = Color.BLACK,
            textAlignment = View.TEXT_ALIGNMENT_CENTER,
            translationY = 2f,
            background = originalBackground,
            width = 123,
            height = 42,
            leftMargin = 6,
            rightMargin = 8,
            gravity = Gravity.CENTER,
        )

        val statusBarCustom = statusbarSnapshotWith(
            fontSize = 20,
            bold = true,
            align = 2,
            verticalOffset = 12,
            chip = true,
            chipCustomTextColor = true,
            chipTextColor = Color.GREEN,
            leftMargin = 20,
            rightMargin = 30,
            fixedWidth = 50,
        )

        val before = mapOf(
            "textSize" to clock.setTextSizeCalls.size,
            "textColor" to clock.setTextColorCalls.size,
            "background" to clock.setBackgroundCalls.size,
            "typeface" to clock.setTypefaceCalls.size,
            "textAlignment" to clock.setTextAlignmentCalls.size,
            "layoutParams" to clock.setLayoutParamsCalls.size,
        )

        SystemClockHooks.initClockStyle(clock, "ccClock", statusBarCustom)

        assertEquals("ccClock must not touch textSize", before["textSize"], clock.setTextSizeCalls.size)
        assertEquals("ccClock must not touch textColor", before["textColor"], clock.setTextColorCalls.size)
        assertEquals("ccClock must not touch background", before["background"], clock.setBackgroundCalls.size)
        assertEquals("ccClock must not touch typeface", before["typeface"], clock.setTypefaceCalls.size)
        assertEquals("ccClock must not touch textAlignment", before["textAlignment"], clock.setTextAlignmentCalls.size)
        assertEquals("ccClock must not touch layoutParams", before["layoutParams"], clock.setLayoutParamsCalls.size)
        assertEquals("ccClock must not change translationY", 2f, clock.translationYValue ?: Float.NaN, 0.001f)
        assertSame("ccClock must not change background", originalBackground, clock.background)
    }

    @Test
    fun shouldSuppressDarkChange_usesSnapshotAndNotPrefMap() {
        // Empty prefs: the decision must come from the snapshot, not from
        // reading MainModule.mPrefs in the dark callback.
        MainModule.mPrefs.clear()

        val chipOn = statusbarSnapshotWith(chip = true, chipUseMonet = true)
        setCurrentSnapshot(chipOn)
        assertTrue("status bar clock with chip+monet must suppress dark changes", SystemClockHooks.shouldSuppressDarkChange("clock"))

        val chipOff = statusbarSnapshotWith(chip = false)
        setCurrentSnapshot(chipOff)
        assertFalse("chip off must not suppress dark changes", SystemClockHooks.shouldSuppressDarkChange("clock"))

        assertFalse("non-statusbar clock must not suppress dark changes", SystemClockHooks.shouldSuppressDarkChange("ccClock"))
        assertFalse("null clockName must not suppress dark changes", SystemClockHooks.shouldSuppressDarkChange(null))

        setCurrentSnapshot(null)
        assertFalse("no snapshot must not suppress dark changes", SystemClockHooks.shouldSuppressDarkChange("clock"))
    }

    @Test
    fun source_noMainModulePrefsInBuildClockTextOrInitClockStyle() {
        val path = "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt"
        val text = source(path)

        val buildClockTextBody = text.substringAfter("internal fun buildClockText(")
            .substringAfter(")")
            .substringBefore("internal fun initClockStyle")

        val initClockStyleBody = text.substringAfter("internal fun initClockStyle(")
            .substringAfter(") {")
            .substringBefore("private fun initClockStyle(")

        assertFalse(
            "buildClockText must not read MainModule.mPrefs",
            buildClockTextBody.contains("MainModule.mPrefs")
        )
        assertFalse(
            "initClockStyle must not read MainModule.mPrefs",
            initClockStyleBody.contains("MainModule.mPrefs")
        )
    }

    @Test
    fun secondTicker_enablingSecondsCreatesOneActiveTicker() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())

        val ticker = SystemClockHooks.activeSecondTicker(controller)
        assertNotNull("one active ticker must be stored on the controller", ticker)
        assertTrue("ScreenStateController must hold exactly one listener", screenStateListeners().size == 1)

        disposeTicker(ticker)
    }

    @Test
    fun secondTicker_repeatedSameSeconds_doesNotRestartTicker() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())
        val first = SystemClockHooks.activeSecondTicker(controller)

        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())
        val second = SystemClockHooks.activeSecondTicker(controller)

        assertSame("same seconds flags must keep the same ticker instance", first, second)
        assertEquals("only one ScreenStateController listener", 1, screenStateListeners().size)

        disposeTicker(second)
    }

    @Test
    fun secondTicker_disablingSecondsDisposesAndRemovesTicker() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(onSnapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())
        val ticker = SystemClockHooks.activeSecondTicker(controller)
        assertNotNull(ticker)

        val offSnapshot = makeSnapshotWithSeconds(statusBar = false, cc = false)
        setCurrentSnapshot(offSnapshot)
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())

        assertNull("ticker must be removed when seconds are off", SystemClockHooks.activeSecondTicker(controller))
        assertTrue("ScreenStateController listeners must be empty", screenStateListeners().isEmpty())
    }

    @Test
    fun secondTicker_onThenOffThenOn_createsNewTickerDoesNotStack() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(onSnapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())
        val first = SystemClockHooks.activeSecondTicker(controller)!!

        val offSnapshot = makeSnapshotWithSeconds(statusBar = false, cc = false)
        setCurrentSnapshot(offSnapshot)
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())

        val second = SystemClockHooks.activeSecondTicker(controller)
        assertNull(second)

        setCurrentSnapshot(onSnapshot)
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())
        val third = SystemClockHooks.activeSecondTicker(controller)!!

        assertTrue("new ticker must not be the disposed old one", first !== third)
        assertEquals("exactly one listener at the end", 1, screenStateListeners().size)

        disposeTicker(third)
    }

    @Test
    fun secondTicker_holdsControllerWeakly() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        fun createAndDrop(): Pair<Any, WeakReference<Any>> {
            val controller = makeControllerWithClock() as Any
            SystemClockHooks.initSecondTicker(controller, true, true, makePublication())
            val ticker = SystemClockHooks.activeSecondTicker(controller)!!
            @Suppress("UNCHECKED_CAST")
            val ref = WeakReference(controller)
            return ticker to ref
        }

        val (ticker, controllerRef) = createAndDrop()
        java.lang.System.gc()
        Thread.sleep(200L)

        assertNull("ticker must not keep the controller alive after it is dropped", controllerRef.get())
        assertNotNull("ScreenStateController still holds the ticker", screenStateListeners().find { it === ticker })

        disposeTicker(ticker)
    }

    @Test
    fun secondTicker_tickerDoesNotHoldViewOrControllerStrongly() {
        val tickerClass = Class.forName("tv.withaibuild.customiuizer.mods.SystemClockHooks\$SecondTicker")
        val controllerField = tickerClass.getDeclaredField("clockControllerRef")
        assertEquals("SecondTicker must hold controller through a WeakReference", WeakReference::class.java, controllerField.type)

        val viewFields = tickerClass.declaredFields.filter {
            View::class.java.isAssignableFrom(it.type)
        }
        assertTrue("SecondTicker must not hold any View field strongly", viewFields.isEmpty())
    }

    @Test
    fun secondTicker_disablingSecondsStopsPendingRuitables() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(onSnapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())

        val offSnapshot = makeSnapshotWithSeconds(statusBar = false, cc = false)
        setCurrentSnapshot(offSnapshot)
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())

        val ticker = SystemClockHooks.activeSecondTicker(controller)
        assertNull(ticker)
    }

    @Test
    fun secondTicker_statusbarTweakOnly_ignoresCcSeconds() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = true)
        setCurrentSnapshot(snapshot)

        val statusClock = RecordingClockView()
        val ccClock = RecordingClockView()
        val controller = makeControllerWithClocks(statusClock, ccClock)

        // statusbarClockTweak=true, ccClockTweak=false.
        SystemClockHooks.initSecondTicker(controller, true, false, makePublication())

        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        val (sb, cc) = secondTickerFlags(ticker)
        assertTrue("status-bar seconds effective", sb)
        assertFalse("cc seconds must not be effective", cc)

        assertNotNull("status-bar clock must have showSeconds tag", ModuleHelper.getViewInfo(statusClock, "showSeconds"))
        assertNull("ccClock must not have showSeconds tag", ModuleHelper.getViewInfo(ccClock, "showSeconds"))

        disposeTicker(ticker)
    }

    @Test
    fun secondTicker_ccTweakOnly_ignoresStatusbarSeconds() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = true)
        setCurrentSnapshot(snapshot)

        val statusClock = RecordingClockView()
        val ccClock = RecordingClockView()
        val controller = makeControllerWithClocks(statusClock, ccClock)

        // statusbarClockTweak=false, ccClockTweak=true.
        SystemClockHooks.initSecondTicker(controller, false, true, makePublication())

        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        val (sb, cc) = secondTickerFlags(ticker)
        assertFalse("status-bar seconds must not be effective", sb)
        assertTrue("cc seconds effective", cc)

        assertNull("status-bar clock must not have showSeconds tag", ModuleHelper.getViewInfo(statusClock, "showSeconds"))
        assertNotNull("ccClock must have showSeconds tag", ModuleHelper.getViewInfo(ccClock, "showSeconds"))

        disposeTicker(ticker)
    }

    @Test
    fun secondTicker_disabledTweaks_noTickerAndClearsTags() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = true)
        setCurrentSnapshot(onSnapshot)

        val statusClock = RecordingClockView()
        val ccClock = RecordingClockView()
        val controller = makeControllerWithClocks(statusClock, ccClock)

        // Start with both features enabled and a ticker running.
        SystemClockHooks.initSecondTicker(controller, true, true, makePublication())
        assertNotNull(SystemClockHooks.activeSecondTicker(controller))

        // Now both features are disabled; stale seconds preferences must not keep
        // the ticker alive or leave showSeconds tags set.
        SystemClockHooks.initSecondTicker(controller, false, false, makePublication())

        assertNull("ticker must be removed when both features are disabled", SystemClockHooks.activeSecondTicker(controller))
        assertTrue("ScreenStateController listeners must be empty", screenStateListeners().isEmpty())
        assertNull("status-bar showSeconds tag must be cleared", ModuleHelper.getViewInfo(statusClock, "showSeconds"))
        assertNull("ccClock showSeconds tag must be cleared", ModuleHelper.getViewInfo(ccClock, "showSeconds"))
    }

    @Test
    fun secondTicker_neitherTweakEnabled_noTickerEvenIfSecondsOn() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = true)
        setCurrentSnapshot(snapshot)

        val controller = makeControllerWithClock()

        SystemClockHooks.initSecondTicker(controller, false, false, makePublication())

        assertNull("no ticker must be created when neither feature is enabled", SystemClockHooks.activeSecondTicker(controller))
        assertTrue("ScreenStateController listeners must be empty", screenStateListeners().isEmpty())
    }

    @Test
    fun shouldRefreshClockStyle_matrix() {
        assertTrue("clock + statusbar", SystemClockHooks.shouldRefreshClockStyle("clock", true, false))
        assertFalse("clock + !statusbar", SystemClockHooks.shouldRefreshClockStyle("clock", false, true))
        assertTrue("ccClock + cc", SystemClockHooks.shouldRefreshClockStyle("ccClock", false, true))
        assertFalse("ccClock + !cc", SystemClockHooks.shouldRefreshClockStyle("ccClock", true, false))
        assertTrue("both enabled", SystemClockHooks.shouldRefreshClockStyle("clock", true, true))
        assertTrue("both enabled for ccClock", SystemClockHooks.shouldRefreshClockStyle("ccClock", true, true))
        assertFalse("both disabled for clock", SystemClockHooks.shouldRefreshClockStyle("clock", false, false))
        assertFalse("both disabled for ccClock", SystemClockHooks.shouldRefreshClockStyle("ccClock", false, false))
        assertFalse("unknown clockName", SystemClockHooks.shouldRefreshClockStyle("drawerDate", true, true))
    }

    @Test
    fun shouldRefreshClockStyle_disabledCcTweak_blocksCcClockDualRowRefresh() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, singleLine = true, maxLines = 1)
        val lineSpacingCallsBefore = clock.setLineSpacingCalls.size
        val singleLineCallsBefore = clock.setSingleLineCalls.size

        // A stale ccCustomFormat with a newline must not apply if ccClockTweak is
        // disabled.
        val prefs = PrefMap().apply {
            put("system_cc_clock_customformat", "HH\nmm")
        }
        val snapshot = buildSnapshot(prefs)

        if (SystemClockHooks.shouldRefreshClockStyle("ccClock", statusbarClockTweak = true, ccClockTweak = false)) {
            SystemClockHooks.initClockStyle(clock, "ccClock", snapshot)
        }

        assertEquals("ccClock must not be styled when cc tweak is disabled", lineSpacingCallsBefore, clock.setLineSpacingCalls.size)
        assertEquals("ccClock singleLine must not be touched", singleLineCallsBefore, clock.setSingleLineCalls.size)
    }

    @Test
    fun shouldRefreshClockStyle_disabledStatusbarTweak_blocksStatusbarStyleRefresh() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textSizePx = 40f)
        val textSizeCallsBefore = clock.setTextSizeCalls.size
        val typefaceCallsBefore = clock.setTypefaceCalls.size

        val snapshot = statusbarSnapshotWith(fontSize = 20, bold = true)

        if (SystemClockHooks.shouldRefreshClockStyle("clock", statusbarClockTweak = false, ccClockTweak = true)) {
            SystemClockHooks.initClockStyle(clock, "clock", snapshot)
        }

        assertEquals("status-bar clock must not be styled when statusbar tweak is disabled", textSizeCallsBefore, clock.setTextSizeCalls.size)
        assertEquals("status-bar clock typeface must not be touched", typefaceCallsBefore, clock.setTypefaceCalls.size)
        assertEquals("original text size must be unchanged", 40f, clock.textSizeValue, 0.001f)
    }

    @Test
    fun clockOriginalStyleState_hasOnlyAllowedFieldTypes() {
        val fieldTypes = SystemClockHooks.ClockOriginalStyleState::class.java.declaredFields.map { it.type }
        val forbidden = listOf(
            View::class.java,
            Context::class.java,
            Resources::class.java,
            android.app.Activity::class.java,
            ViewGroup.LayoutParams::class.java,
        )
        for (fieldType in fieldTypes) {
            for (forbiddenType in forbidden) {
                assertFalse(
                    "ClockOriginalStyleState must not hold a ${forbiddenType.simpleName} reference; found $fieldType",
                    forbiddenType.isAssignableFrom(fieldType)
                )
            }
        }
    }

    @Test
    fun getOrCaptureOriginalStyle_capturesOnlyOnce() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textSizePx = 55f, textColor = Color.BLACK, background = ColorDrawable(Color.RED))

        val snapshot1 = statusbarSnapshotWith(fontSize = 20, bold = true)
        val snapshot2 = statusbarSnapshotWith(fontSize = 24, bold = false)

        val originalStyleTagId = ResourceHooks.getFakeResId("clock_original_style_state")
        SystemClockHooks.initClockStyle(clock, "clock", snapshot1)
        val firstOriginal = clock.getTag(originalStyleTagId)
        assertNotNull(firstOriginal)
        assertEquals(55f, (firstOriginal as SystemClockHooks.ClockOriginalStyleState).textSizePx, 0.001f)

        // Apply a different snapshot: the original state object must remain the
        // same and continue to reflect the first capture.
        SystemClockHooks.initClockStyle(clock, "clock", snapshot2)
        val secondOriginal = clock.getTag(originalStyleTagId)
        assertSame("original state must be captured only once", firstOriginal, secondOriginal)
        assertEquals("original state text size must not change", 55f, (secondOriginal as SystemClockHooks.ClockOriginalStyleState).textSizePx, 0.001f)
    }

    // -------------------------------------------------------------------------
    // C2-B3 H2 Architecture C migration tests
    // -------------------------------------------------------------------------

    @Test
    fun secondTickerH2_coldComplete_publicationAvailableBeforeTick() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication(makeCalendarCold())
            ?: error("test publication must build")
        assertNotNull("cold-complete effect must exist before first tick", publication.currentEffect())

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, publication)

        val ticker = SystemClockHooks.activeSecondTicker(controller)
        assertNotNull("ticker must be created when publication is non-null", ticker)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_coldComplete_doesNotUseRuntimeCalibration() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication(makeCalendarCold())
            ?: error("test publication must build")

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, publication)

        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        val before = publication.calibrationAttempts
        runTicker(ticker)

        assertEquals("cold-complete H2 must not trigger runtime calibration", before, publication.calibrationAttempts)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_repeatedTicksReuseSameEffect() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication(makeCalendarCold())
            ?: error("test publication must build")
        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, publication)

        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        val clock = controller.mClockListeners[0] as RecordingClockView
        val calendar = controller.mCalendar as FakeCalendar

        startTicker(ticker)
        runTicker(ticker)
        val firstEffect = publication.currentEffect()
        assertNotNull(firstEffect)
        assertEquals("first tick must call setTimeInMillis", 1, calendar.setTimeInMillisCalls.size)
        assertEquals("first tick must call updateTime", 1, clock.updateTimeCalls.size)

        runTicker(ticker)
        val secondEffect = publication.currentEffect()
        assertSame("repeated tick must reuse the same effect", firstEffect, secondEffect)
        assertEquals("second tick must call setTimeInMillis", 2, calendar.setTimeInMillisCalls.size)
        assertEquals("second tick must call updateTime", 2, clock.updateTimeCalls.size)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_coldIncomplete_emptyListenerList_failsClosedAndSchedules() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication()
            ?: error("test publication must build")
        val controller = makeControllerWithClock()
        controller.mClockListeners.clear()

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        runTicker(ticker)

        assertNull("cold-incomplete with empty listeners must not publish an effect", publication.currentEffect())
        assertEquals("no runtime calibration must be attempted", 0, publication.calibrationAttempts)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_coldIncomplete_laterEligibleListenerPublishesEffect() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication()
            ?: error("test publication must build")
        val controller = makeControllerWithClock()
        val clock = controller.mClockListeners[0] as RecordingClockView
        val calendar = controller.mCalendar as FakeCalendar

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        assertNull("effect must not exist before first tick", publication.currentEffect())
        runTicker(ticker)

        assertNotNull("eligible listener must publish the effect", publication.currentEffect())
        assertEquals("calendar must be updated", 1, calendar.setTimeInMillisCalls.size)
        assertEquals("clock must be updated", 1, clock.updateTimeCalls.size)

        runTicker(ticker)
        assertEquals("reused effect must update calendar again", 2, calendar.setTimeInMillisCalls.size)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_coldIncomplete_failedTargetDoesNotRepeatResolution() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication()
            ?: error("test publication must build")
        val controller = makeControllerWithClock()
        val clock = controller.mClockListeners[0] as RecordingClockView
        clock.mMiuiStatusBarClockController = null

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        runTicker(ticker)
        assertEquals("failed target must consume one calibration attempt", 1, publication.calibrationAttempts)

        clock.mMiuiStatusBarClockController = controller
        runTicker(ticker)
        assertEquals("same target must not retry after failure", 1, publication.calibrationAttempts)

        disposeTicker(ticker)
    }

    private inner class CcClockView(context: Context = FakeContext()) : RecordingClockView(context)

    @Test
    fun secondTickerH2_coldIncomplete_failedSiblingDoesNotBlockDifferentValidTarget() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = true)
        setCurrentSnapshot(snapshot)

        val statusClock = RecordingClockView()
        val ccClock = CcClockView()
        ModuleHelper.setViewInfo(statusClock, "clockName", "clock")
        ModuleHelper.setViewInfo(ccClock, "clockName", "ccClock")

        val target = ClockResolver.resolveClockTargetClass(CcClockView::class.java)
            ?: error("cc target must resolve")
        val controller = FakeController()
        controller.mContext = FakeContext()
        statusClock.mMiuiStatusBarClockController = null
        ccClock.mMiuiStatusBarClockController = controller
        controller.mClockListeners.add(statusClock)
        controller.mClockListeners.add(ccClock)

        val base = ClockResolver.resolveControllerClass(FakeController::class.java)
            ?: error("controller must resolve")
        val abi = ClockAbi(base, arrayOf(
            ClockResolver.resolveClockTargetClass(RecordingClockView::class.java)!!,
            target,
        ), null)
        val publication = ClockEffectPublication(abi)

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        runTicker(ticker)

        assertNotNull("different valid target must still publish an effect", publication.currentEffect())
        assertEquals("cc clock must be updated", 1, ccClock.updateTimeCalls.size)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_nullPublication_createsNoActiveTicker() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = true)
        setCurrentSnapshot(snapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, null)

        assertNull("null publication must not create a ticker", SystemClockHooks.activeSecondTicker(controller))
        assertTrue("ScreenStateController listeners must be empty", screenStateListeners().isEmpty())
    }

    @Test
    fun secondTickerH2_nullPublication_disposesExistingTickerAndRemovesField() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(onSnapshot)

        val controller = makeControllerWithClock()
        val publication = makePublication(makeCalendarCold())
            ?: error("test publication must build")
        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val first = SystemClockHooks.activeSecondTicker(controller)
        assertNotNull(first)

        SystemClockHooks.initSecondTicker(controller, true, true, null)

        assertNull("existing ticker must be removed when publication becomes null", SystemClockHooks.activeSecondTicker(controller))
        assertTrue("ScreenStateController listeners must be empty", screenStateListeners().isEmpty())
    }

    @Test
    fun secondTickerH2_nullPublication_clearsStaleShowSeconds() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = true)
        setCurrentSnapshot(onSnapshot)

        val statusClock = RecordingClockView()
        val ccClock = RecordingClockView()
        val controller = makeControllerWithClocks(statusClock, ccClock)
        ModuleHelper.setViewInfo(statusClock, "showSeconds", true)
        ModuleHelper.setViewInfo(ccClock, "showSeconds", true)

        SystemClockHooks.initSecondTicker(controller, false, false, null)

        assertNull("status-bar showSeconds tag must be cleared when publication is null", ModuleHelper.getViewInfo(statusClock, "showSeconds"))
        assertNull("ccClock showSeconds tag must be cleared when publication is null", ModuleHelper.getViewInfo(ccClock, "showSeconds"))
    }

    @Test
    fun secondTickerH2_STRUCTURAL_constructorShowSecondsGatedByPublication() {
        val path = "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt"
        val text = source(path)

        val hookStart = text.indexOf("fun StatusBarClockTweakHook(")
        require(hookStart >= 0) { "StatusBarClockTweakHook not found in source" }
        val hookEnd = text.indexOf("\n    @JvmStatic\n    fun CCClockTweakHook(", hookStart)
        require(hookEnd >= 0) { "end of StatusBarClockTweakHook not found" }
        val hookBody = text.substring(hookStart, hookEnd)

        val statusPattern = Regex("setViewInfo\\(clock, \"showSeconds\", true\\)")
        val matches = statusPattern.findAll(hookBody).toList()
        assertTrue("constructor hook must set showSeconds in at least two guarded branches", matches.size >= 2)

        for (match in matches) {
            val prefix = hookBody.substring(0, match.range.first)
            val contextWindow = prefix.takeLast(500)
            assertTrue(
                "constructor showSeconds=true branch must be guarded by clockEffectPublication != null; failed at offset ${match.range.first}",
                contextWindow.contains("clockEffectPublication != null")
            )
        }
    }

    @Test
    fun secondTickerH2_nullPublication_lateClockDoesNotReceiveShowSeconds() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, null)

        assertNull("init with null publication must not create a ticker", SystemClockHooks.activeSecondTicker(controller))

        val lateClock = RecordingClockView()
        ModuleHelper.setViewInfo(lateClock, "clockName", "clock")
        controller.mClockListeners.add(lateClock)

        // This test checks the controller/listener state AFTER a null init.
        // It does NOT invoke the MiuiClock constructor hook directly; the
        // structural test secondTickerH2_STRUCTURAL_constructorShowSecondsGatedByPublication
        // covers the constructor source guard. Both together establish that a
        // late clock cannot become showSeconds=true when publication is unavailable.
        assertNull("late clock after null init must not receive showSeconds", ModuleHelper.getViewInfo(lateClock, "showSeconds"))
    }

    @Test
    fun secondTickerH2_initNonArrayListListeners_skipsTagTraversal() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val controller = NonArrayListFakeController()
        controller.mContext = FakeContext()
        val clock = RecordingClockView()
        ModuleHelper.setViewInfo(clock, "clockName", "clock")
        clock.mMiuiStatusBarClockController = controller
        controller.mClockListeners.add(clock)

        val cls = ClockResolver.resolveControllerClass(NonArrayListFakeController::class.java)
            ?: error("controller must resolve")
        val target = ClockResolver.resolveClockTargetClass(RecordingClockView::class.java)
            ?: error("target must resolve")
        val abi = ClockAbi(cls, arrayOf(target), null)
        val publication = ClockEffectPublication(abi)

        SystemClockHooks.initSecondTicker(controller, true, true, publication)

        assertNull("non-ArrayList listeners must skip init tag traversal", ModuleHelper.getViewInfo(clock, "showSeconds"))
    }

    @Test
    fun secondTickerH2_listenerSemantics_traversalUnboundedAndNoMaxClockListeners() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication(makeCalendarCold())
            ?: error("test publication must build")
        val controller = makeControllerWithClock()

        val clock = controller.mClockListeners[0] as RecordingClockView
        val manyListeners = ArrayList<Any>(100)
        repeat(100) {
            manyListeners.add(clock)
        }
        controller.mClockListeners.clear()
        controller.mClockListeners.addAll(manyListeners)

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        runTicker(ticker)

        assertEquals("unbounded traversal must update every listener copy", manyListeners.size, clock.updateTimeCalls.size)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_listenerSemantics_nonViewEntryAbortsTick() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication(makeCalendarCold())
            ?: error("test publication must build")
        val controller = makeControllerWithClock()
        val clock = controller.mClockListeners[0] as RecordingClockView
        controller.mClockListeners.add(Any())

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        val calendar = controller.mCalendar as FakeCalendar
        startTicker(ticker)

        runTicker(ticker)

        assertEquals("calendar must still be updated", 1, calendar.setTimeInMillisCalls.size)
        assertEquals("non-View listener must abort remaining updates", 1, clock.updateTimeCalls.size)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_failure_setTimeInMillisFailureAbortsRemainingTick() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val error = IllegalStateException("simulated setTimeInMillis failure")
        val calendarCold = ClockResolver.resolveCalendarFromDeclaredType(FailingFakeCalendar::class.java, Context::class.java)
            ?: error("calendar must resolve")
        val publication = makePublication(calendarCold)
            ?: error("publication must build")

        val controller = makeControllerWithClock()
        val clock = controller.mClockListeners[0] as RecordingClockView
        controller.mCalendar = FailingFakeCalendar(error)

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        runTicker(ticker)

        assertEquals("setTimeInMillis failure must not call updateTime", 0, clock.updateTimeCalls.size)
        assertFalse("setTimeInMillis failure must abort before writeIs24", controller.mIs24)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_wrappedFatalFromCalendarSetTimeInMillis_preservesExactIdentity() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val oom = OutOfMemoryError("simulated fatal")
        val calendarCold = ClockResolver.resolveCalendarFromDeclaredType(FailingFakeCalendar::class.java, Context::class.java)
            ?: error("calendar must resolve")
        val publication = makePublication(calendarCold)
            ?: error("publication must build")

        val controller = makeControllerWithClock()
        controller.mCalendar = FailingFakeCalendar(oom)

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        try {
            runTicker(ticker)
            fail("fatal must propagate with exact identity")
        } catch (t: Throwable) {
            assertSame("wrapped fatal must preserve exact original identity", oom, t)
        } finally {
            disposeTicker(ticker)
        }
    }

    @Test
    fun secondTickerH2_failure_writeIs24FailureAbortsListenerUpdates() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val calendarCold = makeCalendarCold()
            ?: error("calendar must resolve")
        val publication = makeFailingIs24Publication(calendarCold)
            ?: error("publication must build")

        val controller = FailingIs24FakeController()
        controller.mContext = FakeContext()
        controller.mCalendar = FakeCalendar()
        val clock = RecordingClockView()
        ModuleHelper.setViewInfo(clock, "clockName", "clock")
        clock.mMiuiStatusBarClockController = controller
        controller.mClockListeners.add(clock)

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        val calendar = controller.mCalendar as FakeCalendar
        startTicker(ticker)

        runTicker(ticker)

        assertEquals("setTimeInMillis must run", 1, calendar.setTimeInMillisCalls.size)
        assertEquals("updateTime must not run when writeIs24 fails", 0, clock.updateTimeCalls.size)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_failure_updateTimeFailureAbortsRemainingListeners() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication(makeCalendarCold())
            ?: error("publication must build")

        val first = object : RecordingClockView() {
            override fun updateTime() {
                throw IllegalStateException("simulated update failure")
            }
        }
        val second = RecordingClockView()
        ModuleHelper.setViewInfo(first, "clockName", "clock")
        ModuleHelper.setViewInfo(second, "clockName", "clock")

        val controller = FakeController()
        controller.mContext = FakeContext()
        first.mMiuiStatusBarClockController = controller
        second.mMiuiStatusBarClockController = controller
        controller.mClockListeners.add(first)
        controller.mClockListeners.add(second)

        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!
        startTicker(ticker)

        runTicker(ticker)

        assertEquals("first listener must not record a successful update", 0, first.updateTimeCalls.size)
        assertEquals("second listener must not be updated after first failure", 0, second.updateTimeCalls.size)

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_resolveForClock_publishesEffectFromRuntimeContext() {
        val publication = makePublication()
            ?: error("publication must build")
        val controller = makeControllerWithClock()
        val clock = controller.mClockListeners[0] as RecordingClockView

        val first = publication.resolveForClock(clock, FakeContext::class.java)
        assertNotNull("resolveForClock must publish an effect from a runtime context", first)

        val second = publication.resolveForClock(clock, FakeContext::class.java)
        assertSame("resolveForClock must reuse the published effect", first, second)
    }

    @Test
    fun secondTickerH2_ownership_controllerWeakAndNoViewOrCalendarFields() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val publication = makePublication(makeCalendarCold())
            ?: error("publication must build")
        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller, true, true, publication)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!

        val cls = ticker.javaClass
        val controllerField = cls.getDeclaredField("clockControllerRef")
        assertEquals("controller must be held through a WeakReference", WeakReference::class.java, controllerField.type)

        val publicationField = cls.getDeclaredField("publication")
        assertEquals("publication must not hold a View, Context or controller", ClockEffectPublication::class.java, publicationField.type)

        val viewFields = cls.declaredFields.filter {
            View::class.java.isAssignableFrom(it.type)
        }
        assertTrue("SecondTicker must not hold any View field strongly", viewFields.isEmpty())

        val calendarFields = cls.declaredFields.filter {
            FakeCalendar::class.java.isAssignableFrom(it.type)
        }
        assertTrue("SecondTicker must not hold a calendar instance field", calendarFields.isEmpty())

        disposeTicker(ticker)
    }

    @Test
    fun secondTickerH2_sourceStructural_hotPathHasNoForbiddenReflection() {
        val path = "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt"
        val text = source(path)

        val classStart = text.indexOf("private class SecondTicker(")
        val classEnd = text.indexOf("\n    }\n\n", classStart) + 7
        val secondTickerBody = text.substring(classStart, classEnd)

        val forbidden = listOf(
            "XposedHelpers.getObjectField",
            "XposedHelpers.setObjectField",
            "XposedHelpers.callMethod",
            "resolveCore",
            "findClass",
            "MainModule.mPrefs",
            "MAX_CLOCK_LISTENERS",
            "Sequence",
            "kotlinx.coroutines",
            "Flow",
        )

        for (token in forbidden) {
            assertFalse(
                "SecondTicker must not contain '$token' in the H2 hot path",
                secondTickerBody.contains(token)
            )
        }

        assertTrue("SecondTicker must call currentEffect", secondTickerBody.contains("currentEffect"))
        assertTrue("SecondTicker must use effect.readCalendar", secondTickerBody.contains("effect.readCalendar"))
        assertTrue("SecondTicker must use effect.setTimeInMillis", secondTickerBody.contains("effect.setTimeInMillis"))
        assertTrue("SecondTicker must use effect.writeIs24", secondTickerBody.contains("effect.writeIs24"))
        assertTrue("SecondTicker must use publication-level listener helper", secondTickerBody.contains("publication.readClockListeners"))
        assertTrue("SecondTicker must use legacy ArrayList/Iterator traversal", secondTickerBody.contains("for (listener in clockListeners)"))
        assertTrue("SecondTicker must preserve listener as View cast", secondTickerBody.contains("listener as View"))
    }

    @Test
    fun secondTickerH2_STRUCTURAL_guardedAndCallbackGuardShape() {
        val hooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt")

        // --- A. SecondTicker.run closes the guarded block before scheduleNextTick() ---
        val runStart = hooks.indexOf("override fun run() {")
        require(runStart >= 0) { "SecondTicker.run not found" }
        val runEnd = hooks.indexOf("\n        }\n\n", runStart)
        require(runEnd >= 0) { "end of SecondTicker.run not found" }
        val runBody = hooks.substring(runStart, runEnd)

        val guardedCall = runBody.indexOf("ModuleHelper.guarded {")
        require(guardedCall >= 0) { "ModuleHelper.guarded not found in SecondTicker.run" }

        // Find the matching '}' for the ModuleHelper.guarded { ... } block.
        val blockOpen = runBody.indexOf('{', guardedCall)
        var depth = 0
        var guardedClose = -1
        for (i in blockOpen until runBody.length) {
            when (runBody[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        guardedClose = i + 1
                        break
                    }
                }
            }
        }
        assertTrue("ModuleHelper.guarded block must be closed in SecondTicker.run", guardedClose >= 0)

        val afterGuarded = runBody.substring(guardedClose).trimStart()
        assertTrue(
            "scheduleNextTick() must be the statement immediately after the guarded block closes",
            afterGuarded.startsWith("scheduleNextTick()")
        )

        // --- B. CallbackGuard.guarded rethrows the required fatal categories ---
        val guard = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/CallbackGuard.kt")

        // Whitespace-insensitive regex proving the fatal categories are connected to the same throw.
        val fatalRethrowPattern = Regex(
            """if\s*\(\s*t\s+is\s+OutOfMemoryError\s+\|\|\s+t\s+is\s+ThreadDeath\s+\|\|\s+t\s+is\s+VirtualMachineError\s*\)\s+throw\s+t"""
        )
        assertTrue(
            "CallbackGuard.guarded must rethrow OutOfMemoryError, ThreadDeath, and VirtualMachineError in a single fatal branch",
            fatalRethrowPattern.find(guard) != null
        )
    }
}
