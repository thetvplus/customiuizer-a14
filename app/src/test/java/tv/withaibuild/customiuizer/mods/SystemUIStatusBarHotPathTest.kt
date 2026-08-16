package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.graphics.Typeface
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

private val NETSPEED_NUMBER_VIEW_TAG = ResourceHooks.getFakeResId("netspeed_number_view")
private val NETSPEED_UNIT_VIEW_TAG = ResourceHooks.getFakeResId("netspeed_unit_view")
private val NETSPEED_TYPEFACE_STATE_TAG = ResourceHooks.getFakeResId("netspeed_typeface_state")
private val NETSPEED_ORIGINAL_STYLE_STATE_TAG = ResourceHooks.getFakeResId("netspeed_original_style_state")
private val VIEW_INITED_TAG = ResourceHooks.getFakeResId("view_inited_tag")
private const val NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID = "netspeed_last_full_style_snapshot_id"

/**
 * A [Map] implementation that records every key access. Used to prove that the hot path does not
 * touch [MainModule.mPrefs] (or any [PrefMap]) after the snapshot has been built.
 */
private class CountingMap : AbstractMap<String, Any>() {
    private val delegate = HashMap<String, Any>()
    val reads = mutableListOf<String>()

    override val entries: Set<Map.Entry<String, Any>> get() = delegate.entries
    override val size: Int get() = delegate.size

    fun putAll(values: Map<String, Any>) = delegate.putAll(values)

    override operator fun get(key: String): Any? {
        reads.add(key)
        return delegate[key]
    }

    fun reset() = reads.clear()
    fun readCount(): Int = reads.size
}

private fun countingPrefMap(values: Map<String, Any>): Pair<PrefMap, CountingMap> {
    val prefMap = PrefMap().apply { clear() }
    val countingMap = CountingMap().apply { putAll(values) }

    val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
    snapshotField.isAccessible = true
    snapshotField.set(prefMap, AtomicReference<Map<String, Any>>(countingMap))

    return prefMap to countingMap
}

private fun countingMainPrefs(values: Map<String, Any>): CountingMap {
    val countingMap = CountingMap().apply { putAll(values) }
    MainModule.mPrefs.clear()
    val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
    snapshotField.isAccessible = true
    snapshotField.set(MainModule.mPrefs, AtomicReference<Map<String, Any>>(countingMap))
    return countingMap
}

private fun snapshotFrom(values: Map<String, Any>): NetSpeedTextStyleSnapshot {
    val (prefs, _) = countingPrefMap(values)
    return SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)
}

@Suppress("DEPRECATION")
private class FakeResources(density: Float = 2.0f) : Resources(null, DisplayMetrics().apply { this.density = density }, android.content.res.Configuration()) {
    val metrics = DisplayMetrics().apply { this.density = density }
    override fun getDisplayMetrics(): DisplayMetrics = metrics
    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int =
        if (name == "TextAppearance.StatusBar.Clock" && defType == "style") 123456 else 0
}

private open class RecordingTextView(density: Float = 2.0f) : TextView(null) {
    val setTextSizeCalls = mutableListOf<Pair<Int, Float>>()
    val setTextColorCalls = mutableListOf<Int>()
    val setTypefaceCalls = mutableListOf<Pair<Typeface?, Int>>()
    val setGravityCalls = mutableListOf<Int>()
    val setTextAlignmentCalls = mutableListOf<Int>()
    val setVisibilityCalls = mutableListOf<Int>()
    val setSingleLineCalls = mutableListOf<Boolean>()
    val setMaxLinesCalls = mutableListOf<Int>()
    val setLineSpacingCalls = mutableListOf<Pair<Float, Float>>()
    val setPaddingCalls = mutableListOf<Quad<Int, Int, Int, Int>>()
    val onTextChangedCalls = mutableListOf<CharSequence?>()
    val layoutParamsCalls = mutableListOf<ViewGroup.LayoutParams?>()
    val setTextAppearanceCalls = mutableListOf<Int>()

    private val fakePaint = TextPaint()
    private var storedTypeface: Typeface? = null
    private var storedTextSize: Float = 0f
    private var storedGravity: Int = 0
    private var storedTextAlignment: Int = View.TEXT_ALIGNMENT_GRAVITY
    private var storedSingleLine: Boolean = false
    private var storedMaxLines: Int = 0
    private var storedLineSpacingExtra: Float = 0f
    private var storedLineSpacingMultiplier: Float = 1f
    private var storedVisibility: Int = View.VISIBLE
    private var storedPaddingLeft: Int = 0
    private var storedPaddingTop: Int = 0
    private var storedPaddingRight: Int = 0
    private var storedPaddingBottom: Int = 0
    private var storedPaddingStart: Int = 0
    private var storedPaddingEnd: Int = 0
    private var storedLayoutParams: ViewGroup.LayoutParams? = null
    private val keyedTags = HashMap<Int, Any>()

    val fakeResources = FakeResources(density)

    override fun getTag(key: Int): Any? = keyedTags[key]

    override fun setTag(key: Int, tag: Any?) {
        if (tag == null) keyedTags.remove(key) else keyedTags[key] = tag
    }

    override fun getResources(): Resources = fakeResources

    override fun getPaint(): TextPaint = fakePaint

    override fun getTypeface(): Typeface? = storedTypeface

    override fun getTextSize(): Float = storedTextSize

    override fun getGravity(): Int = storedGravity

    override fun getTextAlignment(): Int = storedTextAlignment

    override fun isSingleLine(): Boolean = storedSingleLine

    override fun getMaxLines(): Int = storedMaxLines

    override fun getLineSpacingExtra(): Float = storedLineSpacingExtra

    override fun getLineSpacingMultiplier(): Float = storedLineSpacingMultiplier

    override fun getVisibility(): Int = storedVisibility

    override fun getPaddingStart(): Int = if (storedPaddingStart != 0) storedPaddingStart else storedPaddingLeft

    override fun getPaddingTop(): Int = storedPaddingTop

    override fun getPaddingEnd(): Int = if (storedPaddingEnd != 0) storedPaddingEnd else storedPaddingRight

    override fun getPaddingBottom(): Int = storedPaddingBottom

    override fun getPaddingLeft(): Int = storedPaddingLeft

    override fun getPaddingRight(): Int = storedPaddingRight

    override fun setTypeface(tf: Typeface?) {
        storedTypeface = tf
        setTypefaceCalls.add(tf to -1)
    }

    override fun setTypeface(tf: Typeface?, style: Int) {
        val created = Typeface.create(tf, style)
        storedTypeface = created
        setTypefaceCalls.add(created to style)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        onTextChangedCalls.add(text)
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
    }

    override fun setTextSize(unit: Int, size: Float) {
        setTextSizeCalls.add(unit to size)
        storedTextSize = when (unit) {
            TypedValue.COMPLEX_UNIT_DIP -> size * fakeResources.displayMetrics.density
            else -> size
        }
    }

    override fun setGravity(gravity: Int) {
        setGravityCalls.add(gravity)
        storedGravity = gravity
    }

    override fun setTextAlignment(textAlignment: Int) {
        setTextAlignmentCalls.add(textAlignment)
        storedTextAlignment = textAlignment
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        storedVisibility = visibility
        setVisibilityCalls.add(visibility)
    }

    override fun setSingleLine(singleLine: Boolean) {
        setSingleLineCalls.add(singleLine)
        storedSingleLine = singleLine
    }

    override fun setMaxLines(maxLines: Int) {
        setMaxLinesCalls.add(maxLines)
        storedMaxLines = maxLines
    }

    override fun setLineSpacing(add: Float, multiplier: Float) {
        setLineSpacingCalls.add(add to multiplier)
        storedLineSpacingExtra = add
        storedLineSpacingMultiplier = multiplier
    }

    override fun setTextAppearance(resId: Int) {
        setTextAppearanceCalls.add(resId)
    }

    fun clearCalls() {
        setTextSizeCalls.clear()
        setTextColorCalls.clear()
        setTypefaceCalls.clear()
        setGravityCalls.clear()
        setTextAlignmentCalls.clear()
        setVisibilityCalls.clear()
        setSingleLineCalls.clear()
        setMaxLinesCalls.clear()
        setLineSpacingCalls.clear()
        setPaddingCalls.clear()
        onTextChangedCalls.clear()
        layoutParamsCalls.clear()
        setTextAppearanceCalls.clear()
    }

    /**
     * Simulates the framework text-update path in unit tests where the stub TextView.setText()
     * does not emit onTextChanged. Production NetworkSpeedView.setNetworkSpeed() updates the text
     * directly; our after-hook never calls this.
     */
    fun recordTextChange(text: CharSequence?) {
        onTextChanged(text, 0, 0, 0)
    }

    override fun setTextColor(color: Int) {
        setTextColorCalls.add(color)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        setPaddingCalls.add(Quad(left, top, right, bottom))
        storedPaddingLeft = left
        storedPaddingTop = top
        storedPaddingRight = right
        storedPaddingBottom = bottom
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        setPaddingCalls.add(Quad(start, top, end, bottom))
        storedPaddingStart = start
        storedPaddingTop = top
        storedPaddingEnd = end
        storedPaddingBottom = bottom
    }

    override fun getLayoutParams(): ViewGroup.LayoutParams? = storedLayoutParams

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        storedLayoutParams = params
        layoutParamsCalls.add(params)
    }
}

private class RecordingLinearLayout(density: Float = 2.0f) : LinearLayout(null) {
    val setPaddingRelativeCalls = mutableListOf<Quad<Int, Int, Int, Int>>()
    val setPaddingCalls = mutableListOf<Quad<Int, Int, Int, Int>>()
    val setTranslationYCalls = mutableListOf<Float>()

    @JvmField var mNetworkSpeedNumberText: TextView? = null
    @JvmField var mNetworkSpeedUnitText: TextView? = null

    private var storedTranslationY: Float = 0f
    private var storedPaddingStart: Int = 0
    private var storedPaddingTop: Int = 0
    private var storedPaddingEnd: Int = 0
    private var storedPaddingBottom: Int = 0
    private var storedPaddingLeft: Int = 0
    private var storedPaddingRight: Int = 0
    private val keyedTags = HashMap<Int, Any>()
    val fakeResources = FakeResources(density)

    override fun getTag(key: Int): Any? = keyedTags[key]

    override fun setTag(key: Int, tag: Any?) {
        if (tag == null) keyedTags.remove(key) else keyedTags[key] = tag
    }

    override fun getResources(): Resources = fakeResources

    override fun getTranslationY(): Float = storedTranslationY

    override fun getPaddingStart(): Int = if (storedPaddingStart != 0) storedPaddingStart else storedPaddingLeft

    override fun getPaddingTop(): Int = storedPaddingTop

    override fun getPaddingEnd(): Int = if (storedPaddingEnd != 0) storedPaddingEnd else storedPaddingRight

    override fun getPaddingBottom(): Int = storedPaddingBottom

    override fun getPaddingLeft(): Int = storedPaddingLeft

    override fun getPaddingRight(): Int = storedPaddingRight

    fun clearCalls() {
        setTranslationYCalls.clear()
        setPaddingRelativeCalls.clear()
        setPaddingCalls.clear()
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        setPaddingRelativeCalls.add(Quad(start, top, end, bottom))
        storedPaddingStart = start
        storedPaddingTop = top
        storedPaddingEnd = end
        storedPaddingBottom = bottom
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        setPaddingCalls.add(Quad(left, top, right, bottom))
        storedPaddingLeft = left
        storedPaddingTop = top
        storedPaddingRight = right
        storedPaddingBottom = bottom
    }

    override fun setTranslationY(translationY: Float) {
        setTranslationYCalls.add(translationY)
        storedTranslationY = translationY
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private class FailingTextView(density: Float = 2.0f) : RecordingTextView(density) {
    var failNextSetTextSize = false

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        if (failNextSetTextSize) {
            failNextSetTextSize = false
            throw IllegalStateException("simulated setTextSize failure")
        }
    }
}

class SystemUIStatusBarHotPathTest {

    @Before
    fun setUp() {
        SystemUIStatusBarHooks.NetSpeedStyleHook(fakePackageReadyParam())
        resetNetSpeedSnapshotState()
    }

    @After
    fun tearDown() {
        resetNetSpeedSnapshotState()
        resetNetSpeedRuntimeState()
        MainModule.mPrefs.clear()
    }

    private fun fakePackageReadyParam(): io.github.libxposed.api.XposedModuleInterface.PackageReadyParam {
        return java.lang.reflect.Proxy.newProxyInstance(
            io.github.libxposed.api.XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(io.github.libxposed.api.XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.android.systemui"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                "toString" -> "FakePackageReadyParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
    }

    private fun getNetSpeedRuntimeState(): Any? {
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("netSpeedRuntimeState").apply { isAccessible = true }
        return field.get(SystemUIStatusBarHooks)
    }

    private fun getStyleState(): Any? {
        val runtimeState = getNetSpeedRuntimeState() ?: return null
        val field = runtimeState::class.java.getDeclaredField("styleState").apply { isAccessible = true }
        return field.get(runtimeState)
    }

    private fun resetNetSpeedSnapshotState() {
        val styleState = getStyleState() ?: return
        val field = styleState::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        (field.get(styleState) as? AtomicReference<NetSpeedTextStyleSnapshot?>)?.set(null)
    }

    private fun resetNetSpeedRuntimeState() {
        try {
            tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry.unregisterPreferenceObserver(SystemUIStatusBarHooks)
        } catch (_: Throwable) {
            // ignore if not registered
        }
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("netSpeedRuntimeState").apply { isAccessible = true }
        field.set(SystemUIStatusBarHooks, null)
    }

    private fun speedViewWith(
        number: RecordingTextView,
        unit: RecordingTextView? = null,
        applyOriginalStyle: Boolean = true,
    ): RecordingLinearLayout {
        val speedView = RecordingLinearLayout(number.fakeResources.displayMetrics.density)
        speedView.mNetworkSpeedNumberText = number
        if (unit != null) speedView.mNetworkSpeedUnitText = unit
        speedView.setTag(NETSPEED_NUMBER_VIEW_TAG, number)
        if (unit != null) speedView.setTag(NETSPEED_UNIT_VIEW_TAG, unit)
        if (applyOriginalStyle) setOriginalNetSpeedStyle(number, unit, speedView)
        return speedView
    }

    /**
     * Sets a realistic original NetworkSpeed style on the supplied views so that reversibility
     * tests can observe a restore to a non-trivial baseline.
     */
    private fun setOriginalNetSpeedStyle(
        number: RecordingTextView,
        unit: RecordingTextView? = null,
        speedView: RecordingLinearLayout,
    ) {
        speedView.setTranslationY(0f)
        speedView.setPaddingRelative(8, 4, 8, 4)

        number.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28f)
        number.setGravity(Gravity.CENTER)
        number.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY)
        number.setSingleLine(true)
        number.setMaxLines(1)
        number.setLineSpacing(0f, 1f)

        val numberLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        numberLp.width = LinearLayout.LayoutParams.WRAP_CONTENT
        numberLp.height = LinearLayout.LayoutParams.WRAP_CONTENT
        numberLp.weight = 0f
        numberLp.gravity = Gravity.CENTER
        numberLp.leftMargin = 4
        numberLp.rightMargin = 4
        numberLp.topMargin = 2
        numberLp.bottomMargin = 2
        numberLp.marginStart = 4
        numberLp.marginEnd = 4
        number.layoutParams = numberLp

        unit?.let { u ->
            u.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f)
            u.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY)
            u.setVisibility(View.VISIBLE)
            val unitLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            unitLp.width = LinearLayout.LayoutParams.WRAP_CONTENT
            unitLp.height = LinearLayout.LayoutParams.WRAP_CONTENT
            unitLp.weight = 0f
            unitLp.gravity = Gravity.CENTER
            u.layoutParams = unitLp
        }

        // Reset call counts so tests can assert only what applyNetSpeedTextStyle does.
        number.clearCalls()
        speedView.clearCalls()
        unit?.clearCalls()
    }

    private fun setMainPrefs(values: Map<String, Any>) {
        countingMainPrefs(values)
    }

    private fun getFullSnapshotId(speedView: LinearLayout): Long? =
        XposedHelpers.getAdditionalInstanceField(speedView, NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID) as? Long

    private fun makeDefaultSnapshot(): NetSpeedTextStyleSnapshot {
        return snapshotFrom(mapOf())
    }

    private fun makeCustomSnapshot(): NetSpeedTextStyleSnapshot {
        return snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 1,
                "system_netspeed_boldfont" to true,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_fixedcontent_width" to 20,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
                "system_netspeed_verticaloffset" to 12,
                "system_detailednetspeed_align" to 3,
                "system_netspeed_rowspacing" to 110,
            )
        )
    }

    // 1. 初次初始化只集中读取一次相关偏好；2. 单次旧路径等价配置包含原有 9 个读取语义
    @Test
    fun buildNetSpeedTextStyleSnapshot_readsAllNineRelevantKeysAndProducesEquivalentValues() {
        val (prefs, countingMap) = countingPrefMap(
            mapOf(
                "system_detailednetspeed_style" to "2",
                "system_netspeed_boldfont" to true,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_fixedcontent_width" to 20,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
                "system_netspeed_verticaloffset" to 12,
                "system_detailednetspeed_align" to "3",
                "system_netspeed_rowspacing" to 110,
            )
        )

        val snapshot = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)

        assertEquals(9, countingMap.readCount())
        assertEquals(2, snapshot.speedStyle)
        assertTrue(snapshot.bold)
        assertEquals(20, snapshot.fontSize)
        assertEquals(20, snapshot.fixedWidth)
        assertEquals(10, snapshot.leftMargin)
        assertEquals(12, snapshot.rightMargin)
        assertEquals(12, snapshot.verticalOffset)
        assertEquals(3, snapshot.align)
        assertEquals(110, snapshot.adjustment)
    }

    // 3. 连续执行 100 次样式热路径，PrefMap 读取次数保持 0
    @Test
    fun applyNetSpeedTextStyle_100HotCalls_withSnapshot_doesNotReadPrefs() {
        val (prefs, countingMap) = countingPrefMap(mapOf("system_netspeed_fontsize" to 20))
        val snapshot = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)
        countingMap.reset()

        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        repeat(100) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, true)
        }

        assertEquals(0, countingMap.readCount())
    }

    // 4. 连续 100 次回调，相同 View 的样式 setter 只执行首次必要次数
    @Test
    fun applyNetSpeedTextStyle_100HotCalls_sameViewSameSnapshot_skipsAllSetters() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)
        repeat(100) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, true)
        }

        val firstFullTextSizeCount = number.setTextSizeCalls.size
        val firstFullTypefaceCount = number.setTypefaceCalls.size

        assertTrue("expected at least one text-size setter on first full apply", firstFullTextSizeCount >= 1)
        assertTrue("expected at least one typeface setter on first full apply", firstFullTypefaceCount >= 1)

        assertEquals(firstFullTextSizeCount, number.setTextSizeCalls.size)
        assertEquals(firstFullTypefaceCount, number.setTypefaceCalls.size)
        assertEquals(number.setTextSizeCalls.size, unit.setTextSizeCalls.size)
        assertEquals(number.setTypefaceCalls.size, unit.setTypefaceCalls.size)
        assertEquals(1, speedView.setTranslationYCalls.size)
        assertEquals(1, speedView.setPaddingRelativeCalls.size)
        assertEquals(1, number.layoutParamsCalls.size)
        assertEquals(1, number.setTextAlignmentCalls.size)
        assertEquals(1, number.setGravityCalls.size) // speedStyle == 1, restores original
        assertEquals(1, number.setSingleLineCalls.size) // restores original
        assertEquals(1, number.setMaxLinesCalls.size) // restores original
        assertEquals(1, number.setLineSpacingCalls.size) // restores original
    }

    // 5. 网速文本更新不因样式幂等机制被跳过（apply 不触发文本改变）
    @Test
    fun applyNetSpeedTextStyle_doesNotTriggerTextChange() {
        val snapshot = makeDefaultSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        // Setting the text via the final setText(...) triggers onTextChanged once.
        number.text = "100"
        val textChangesBeforeApply = number.onTextChangedCalls.size

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)

        assertEquals(textChangesBeforeApply, number.onTextChangedCalls.size)
    }

    // 6. 相关偏好变化只重建一次 snapshot
    @Test
    fun netSpeedTextStyleObserver_relevantKey_rebuildsSnapshotOnce() {
        val countingMap = countingMainPrefs(mapOf("system_netspeed_fontsize" to 15))
        val first = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(MainModule.mPrefs)

        val styleState = getStyleState() ?: error("style state not installed")
        val snapshotField = styleState::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        val ref = snapshotField.get(styleState) as AtomicReference<NetSpeedTextStyleSnapshot?>
        ref.set(first)

        val observer = getNetSpeedTextStyleObserver()
        countingMap.reset()

        observer.onChange("system_netspeed_fontsize")

        assertEquals(9, countingMap.readCount())
        val second = ref.get() as NetSpeedTextStyleSnapshot
        assertNotEquals(first.id, second.id)
    }

    // 7. 无关偏好变化不重建 snapshot
    @Test
    fun netSpeedTextStyleObserver_irrelevantKey_doesNotRebuildSnapshot() {
        val countingMap = countingMainPrefs(mapOf())
        val first = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(MainModule.mPrefs)

        val styleState = getStyleState() ?: error("style state not installed")
        val snapshotField = styleState::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        val ref = snapshotField.get(styleState) as AtomicReference<NetSpeedTextStyleSnapshot?>
        ref.set(first)

        val observer = getNetSpeedTextStyleObserver()
        countingMap.reset()

        observer.onChange("system_statusbar_clock_position")

        assertEquals(0, countingMap.readCount())
        val still = ref.get() as NetSpeedTextStyleSnapshot
        assertEquals(first.id, still.id)
    }

    // 8. 新 snapshot 在下一次正常回调中应用（typeface-only 路径能感知到 bold 等变化）
    @Test
    fun applyNetSpeedTextStyle_newSnapshot_appliedOnNextCallback() {
        val first = makeCustomSnapshot()
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, first, false)

        val second = snapshotFrom(mapOf("system_netspeed_boldfont" to false))
        val typefaceCallsBefore = number.setTypefaceCalls.size

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, second, true)

        assertTrue(number.setTypefaceCalls.size > typefaceCallsBefore)
    }

    // 9. 两个不同 View 共用当前快照，但各自完整应用一次
    @Test
    fun applyNetSpeedTextStyle_twoDistinctViews_eachAppliesSnapshot() {
        val snapshot = makeCustomSnapshot()

        val number1 = RecordingTextView()
        val speedView1 = speedViewWith(number1)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView1, snapshot, false)

        val number2 = RecordingTextView()
        val speedView2 = speedViewWith(number2)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView2, snapshot, false)

        assertEquals(1, speedView1.setTranslationYCalls.size)
        assertEquals(1, speedView2.setTranslationYCalls.size)
        assertEquals(1, number1.setTextSizeCalls.size)
        assertEquals(1, number2.setTextSizeCalls.size)
    }

    // 10. View 重建后正确应用
    @Test
    fun applyNetSpeedTextStyle_afterViewRecreation_appliesAgain() {
        val snapshot = makeCustomSnapshot()

        val number1 = RecordingTextView()
        val speedView1 = speedViewWith(number1)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView1, snapshot, false)

        val number2 = RecordingTextView()
        val speedView2 = speedViewWith(number2)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView2, snapshot, false)

        assertEquals(1, number1.setTextSizeCalls.size)
        assertEquals(1, number2.setTextSizeCalls.size)
        assertEquals(1, speedView1.setTranslationYCalls.size)
        assertEquals(1, speedView2.setTranslationYCalls.size)
    }

    // 11. 默认配置保持原生行为
    @Test
    fun applyNetSpeedTextStyle_defaultConfig_doesNotOverrideUnnecessaryProperties() {
        val snapshot = makeDefaultSnapshot()
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        // Clear any setup noise so we can assert only what applyNetSpeedTextStyle does.
        number.setTextSizeCalls.clear()
        number.layoutParamsCalls.clear()
        number.setTextAlignmentCalls.clear()
        number.setSingleLineCalls.clear()
        number.setTypefaceCalls.clear()
        unit.setVisibilityCalls.clear()
        speedView.setTranslationYCalls.clear()

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)

        assertEquals(1, number.setTextSizeCalls.size) // full apply restores the original px size once
        assertEquals(1, number.layoutParamsCalls.size) // full apply builds a fresh LP from the original once
        assertEquals(1, speedView.setTranslationYCalls.size) // verticalOffset == 8 -> translateY = original
        assertEquals(0f, speedView.setTranslationYCalls.firstOrNull() ?: Float.NaN, 0f)
        assertEquals(1, number.setTextAlignmentCalls.size) // full apply restores the original alignment once
        assertEquals(1, number.setSingleLineCalls.size) // full apply restores the original singleLine once
        assertFalse(unit.setVisibilityCalls.contains(View.GONE)) // speedStyle == 1, unit must stay visible
        assertTrue(number.setTypefaceCalls.size >= 1)
    }

    // 12. 重置后 styleState snapshot 为空
    @Test
    fun netSpeedTextStyle_initialState_noSnapshotOrObserverSideEffects() {
        val styleState = getStyleState() ?: error("style state not installed")
        val field = styleState::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        val ref = field.get(styleState) as AtomicReference<*>
        assertNull(ref.get())
    }

    // 13. 无效字号、颜色、位置或样式值安全回退
    @Test
    fun applyNetSpeedTextStyle_invalidValues_fallsBackSafely() {
        val snapshot = snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 99,
                "system_netspeed_fontsize" to -1,
                "system_netspeed_leftmargin" to -100,
                "system_netspeed_verticaloffset" to -100,
                "system_detailednetspeed_align" to 99,
            )
        )

        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)

        // Even with invalid values the function completes and applies typeface.
        assertTrue(number.setTypefaceCalls.size >= 1)
    }

    // 14. 重复应用不产生累计字号、padding、margin 或位移
    @Test
    fun applyNetSpeedTextStyle_repeatedApplications_noCumulativeChanges() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)
        val firstTranslation = speedView.setTranslationYCalls.last()

        repeat(10) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)
        }

        assertEquals(firstTranslation, speedView.setTranslationYCalls.last())
        assertEquals(1, speedView.setTranslationYCalls.toSet().size)
    }

    // 15. SystemUI 重建后结果一致：模拟进程重建后重新生成 snapshot
    @Test
    fun buildNetSpeedTextStyleSnapshot_afterResetProducesFreshSnapshot() {
        val (prefs, _) = countingPrefMap(mapOf("system_netspeed_fontsize" to 18))
        val first = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)

        resetNetSpeedSnapshotState()

        val second = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)

        assertNotEquals(first.id, second.id)
        assertEquals(first.fontSize, second.fontSize)
    }

    // 16. observer 重复初始化不产生重复注册
    @Test
    fun netSpeedTextStyleObserver_isSameInstance() {
        val observer = getNetSpeedTextStyleObserver()
        val observer2 = getNetSpeedTextStyleObserver()
        assertTrue(observer === observer2)
    }

    // 17. 配置变化与热路径并发读取时不会读取到部分构造状态
    @Test
    fun applyNetSpeedTextStyle_readsFullyConstructedSnapshot() {
        val (prefs, _) = countingPrefMap(
            mapOf(
                "system_detailednetspeed_style" to 2,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_rowspacing" to 120,
            )
        )
        val snapshot = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)

        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)

        assertEquals(1, number.setSingleLineCalls.size)
        assertEquals(1, number.setMaxLinesCalls.size)
        assertEquals(1, number.setLineSpacingCalls.size)
        assertEquals(1, number.setTextSizeCalls.size) // number + speedStyle > 13
    }

    // 18. density/fontScale 等相关配置变化时不会继续使用失效尺寸
    @Test
    fun applyNetSpeedTextStyle_usesCurrentResourcesForPixelConversion() {
        val snapshot = snapshotFrom(mapOf("system_netspeed_fixedcontent_width" to 20))

        val number1 = RecordingTextView(density = 2.0f)
        number1.layoutParams = LinearLayout.LayoutParams(0, 0)
        val speedView1 = speedViewWith(number1)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView1, snapshot, false)
        val widthAtDensity2 = (number1.layoutParams as LinearLayout.LayoutParams).width

        val number2 = RecordingTextView(density = 4.0f)
        number2.layoutParams = LinearLayout.LayoutParams(0, 0)
        val speedView2 = speedViewWith(number2)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView2, snapshot, false)
        val widthAtDensity4 = (number2.layoutParams as LinearLayout.LayoutParams).width

        // Logical fixedWidth is converted to px using the View's current resources, so different
        // densities produce different physical widths.  20 dp * 2.0 = 40 px; 20 dp * 4.0 = 80 px.
        assertTrue(widthAtDensity2 > 0)
        assertTrue(widthAtDensity4 > 0)
        assertEquals(40, widthAtDensity2)
        assertEquals(80, widthAtDensity4)
    }

    // 19. 模拟 setNetworkSpeed 每秒回调：首次完整应用，之后 99 次 0 完整 setter，文本仍更新
    @Test
    fun setNetworkSpeedSimulation_100Ticks_zeroFullSetters_textStillUpdates() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        // 100 ticks. The production setNetworkSpeed after-hook passes typefaceOnly = false;
        // applyNetSpeedTextStyle short-circuits when the full style is already applied.
        // The text content update is independent; recordTextChange simulates the framework text setter.
        repeat(100) { index ->
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
            number.recordTextChange("${index + 1}")
            unit.recordTextChange("KB")
        }

        assertEquals(100, number.onTextChangedCalls.size)
        assertEquals(1, number.setTextSizeCalls.size) // only first full apply (fontSize 20 > 13)
        assertEquals(1, number.layoutParamsCalls.size) // fixedWidth 20 > 10
        assertEquals(1, speedView.setTranslationYCalls.size) // verticalOffset 12 != 8
        assertEquals(1, speedView.setPaddingRelativeCalls.size)
        assertEquals(1, number.setTypefaceCalls.size) // set once on first full apply, then idempotent
        assertEquals(0, number.setTextColorCalls.size)
        assertEquals(0, number.setPaddingCalls.size)
        assertEquals(0, unit.setPaddingCalls.size)
    }

    // 20. snapshot 变化后下一次完整样式只应用一次，之后再变化前不再重复
    @Test
    fun setNetworkSpeedSimulation_snapshotChange_fullStyleOnceThenZero() {
        val snapshotA = makeCustomSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotA, typefaceOnly = false)
        val textSizeAfterA = number.setTextSizeCalls.size
        val typefaceAfterA = number.setTypefaceCalls.size

        repeat(50) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotA, typefaceOnly = false)
        }
        assertEquals(textSizeAfterA, number.setTextSizeCalls.size)
        assertEquals(typefaceAfterA, number.setTypefaceCalls.size)

        val snapshotB = snapshotFrom(mapOf("system_netspeed_boldfont" to false))
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotB, typefaceOnly = false)

        // Snapshot B has a different id; the full apply runs once and writes the original text size
        // (fontSize 13 -> original px) once. Subsequent repeats with the same snapshot are idempotent.
        assertEquals(textSizeAfterA + 1, number.setTextSizeCalls.size)
        assertTrue(number.setTypefaceCalls.size > typefaceAfterA)

        val textSizeAfterB = number.setTextSizeCalls.size
        val typefaceAfterB = number.setTypefaceCalls.size
        repeat(50) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotB, typefaceOnly = false)
        }
        assertEquals(textSizeAfterB, number.setTextSizeCalls.size)
        assertEquals(typefaceAfterB, number.setTypefaceCalls.size)
    }

    // 21. setTextAppearance 后只恢复 Typeface，不重复 size / padding / gravity / layout
    @Test
    fun setTextAppearanceSimulation_typefaceOnlyRestoresTypefaceWithoutFullSetters() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        // onFinishInflate (useClockStyle = false) performs a full style apply.
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)

        val textSizeBefore = number.setTextSizeCalls.size
        val layoutParamsBefore = number.layoutParamsCalls.size
        val gravityBefore = number.setGravityCalls.size
        val paddingRelativeBefore = speedView.setPaddingRelativeCalls.size
        val paddingBefore = speedView.setPaddingCalls.size
        val textColorBefore = number.setTextColorCalls.size
        val typefaceBefore = number.setTypefaceCalls.size

        // Simulate the framework calling setTextAppearance on the number TextView. In production the
        // setTextAppearance after-hook calls applyNetSpeedTextStyle(speedView, snapshot, true).
        // The framework call would have changed the base typeface; the typefaceOnly path must only
        // restore the network-speed typeface and must not re-apply size, padding, gravity or layout.
        number.setTypeface(Typeface.DEFAULT)
        val snapshotAfterTextAppearance = snapshot.copy(bold = !snapshot.bold)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotAfterTextAppearance, typefaceOnly = true)

        assertEquals(textSizeBefore, number.setTextSizeCalls.size)
        assertEquals(layoutParamsBefore, number.layoutParamsCalls.size)
        assertEquals(gravityBefore, number.setGravityCalls.size)
        assertEquals(paddingRelativeBefore, speedView.setPaddingRelativeCalls.size)
        assertEquals(paddingBefore, speedView.setPaddingCalls.size)
        assertEquals(textColorBefore, number.setTextColorCalls.size)
        assertTrue(number.setTypefaceCalls.size > typefaceBefore)
    }

    // 22. 明确记录三个生产回调传入的 typefaceOnly 参数语义
    @Test
    fun callback_typefaceOnly_values() {
        val snapshot = makeDefaultSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        // onFinishInflate (useClockStyle = false) and the first setNetworkSpeed both request a full
        // style apply: typefaceOnly = false.
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
        assertEquals(1, speedView.setTranslationYCalls.size)

        // setTextAppearance after-hook always requests typeface-only restoration.
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = true)

        // Subsequent setNetworkSpeed calls request a full style, but the implementation
        // short-circuits when the same snapshot has already been fully applied.
        val callsBefore = number.setTextSizeCalls.size
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
        assertEquals(callsBefore, number.setTextSizeCalls.size)
    }

    // 23. typefaceOnly = false 时，相同 View + 相同 snapshot 第二次直接 return
    @Test
    fun applyNetSpeedTextStyle_fullSameViewSameSnapshot_secondCall_zeroSetters() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
        val textSize = number.setTextSizeCalls.size
        val translationY = speedView.setTranslationYCalls.size
        val padding = speedView.setPaddingRelativeCalls.size
        val layoutParams = number.layoutParamsCalls.size

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)

        assertEquals(textSize, number.setTextSizeCalls.size)
        assertEquals(translationY, speedView.setTranslationYCalls.size)
        assertEquals(padding, speedView.setPaddingRelativeCalls.size)
        assertEquals(layoutParams, number.layoutParamsCalls.size)
    }

    // A. original capture once, no forbidden field types, identity unchanged across snapshots
    @Test
    fun netSpeedOriginalStyleState_capturesOnce_andStoresOnlyPrimitiveValues() {
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)
        setOriginalNetSpeedStyle(number, unit, speedView)

        val custom = makeCustomSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)

        val first = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as? SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertNotNull(first)

        val forbidden = arrayOf(
            View::class.java,
            android.content.Context::class.java,
            Resources::class.java,
            ViewGroup.LayoutParams::class.java,
        )
        SystemUIStatusBarHooks.NetSpeedOriginalStyleState::class.java.declaredFields.forEach { field ->
            forbidden.forEach {
                assertFalse(
                    "Field ${field.name} must not hold a View/Context/Resources/LayoutParams reference",
                    it.isAssignableFrom(field.type)
                )
            }
        }

        val another = snapshotFrom(mapOf("system_netspeed_fontsize" to 25))
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, another, false)

        val second = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as? SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertSame(first, second)
    }

    // B. font default->custom->default (number and unit textSize)
    @Test
    fun applyNetSpeedTextStyle_fontSizeTransition_reversible() {
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)
        setOriginalNetSpeedStyle(number, unit, speedView)

        val default = makeDefaultSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, default, false)
        assertEquals(28f, number.textSize)
        assertEquals(20f, unit.textSize)

        val custom = makeCustomSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        assertEquals(20f, number.textSize) // 20 * 0.5sp = 10dp * density 2 = 20px
        assertEquals(20f, unit.textSize)

        val default2 = makeDefaultSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, default2, false)
        assertEquals(28f, number.textSize)
        assertEquals(20f, unit.textSize)
    }

    // C. style transitions 1->2->1, 1->3->1, 2->3, 3->2, 2->1
    @Test
    fun applyNetSpeedTextStyle_styleTransitions_reversible() {
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)
        setOriginalNetSpeedStyle(number, unit, speedView)

        val style1 = snapshotFrom(mapOf("system_detailednetspeed_style" to 1))
        val style2 = snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 2,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_rowspacing" to 110,
            )
        )
        val style3 = snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 3,
                "system_netspeed_fontsize" to 20,
            )
        )

        // 1 -> 2
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, style2, false)
        assertEquals(View.GONE, unit.visibility)
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.START, number.gravity)
        assertFalse(number.isSingleLine)
        assertEquals(2, number.maxLines)
        assertEquals(0f, number.lineSpacingExtra, 0f)
        assertEquals(resolveNetSpeedLineSpacing(20, 110), number.lineSpacingMultiplier, 0.001f)

        // 2 -> 1
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, style1, false)
        assertEquals(View.VISIBLE, unit.visibility)
        assertEquals(Gravity.CENTER, number.gravity)
        assertTrue(number.isSingleLine)
        assertEquals(1, number.maxLines)
        assertEquals(0f, number.lineSpacingExtra, 0f)
        assertEquals(1f, number.lineSpacingMultiplier, 0f)

        // 1 -> 3
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, style3, false)
        assertEquals(View.GONE, unit.visibility)
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.START, number.gravity)
        assertTrue(number.isSingleLine)
        assertEquals(1, number.maxLines)
        assertEquals(0f, number.lineSpacingExtra, 0f)
        assertEquals(1f, number.lineSpacingMultiplier, 0f)

        // 3 -> 2
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, style2, false)
        assertEquals(View.GONE, unit.visibility)
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.START, number.gravity)
        assertFalse(number.isSingleLine)
        assertEquals(2, number.maxLines)

        // 2 -> 3
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, style3, false)
        assertEquals(View.GONE, unit.visibility)
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.START, number.gravity)
        assertTrue(number.isSingleLine)
        assertEquals(1, number.maxLines)

        // 3 -> 1
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, style1, false)
        assertEquals(View.VISIBLE, unit.visibility)
        assertEquals(Gravity.CENTER, number.gravity)
        assertTrue(number.isSingleLine)
        assertEquals(1, number.maxLines)
    }

    // D. fixedWidth and layout params original->custom->original
    @Test
    fun applyNetSpeedTextStyle_layoutParams_reversible() {
        val number = RecordingTextView()
        val speedView = speedViewWith(number)
        setOriginalNetSpeedStyle(number, null, speedView)

        val default = makeDefaultSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, default, false)
        val lpDefault = number.layoutParams as LinearLayout.LayoutParams
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lpDefault.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lpDefault.height)
        assertEquals(0f, lpDefault.weight, 0f)
        assertEquals(Gravity.CENTER, lpDefault.gravity)
        assertEquals(4, lpDefault.leftMargin)
        assertEquals(2, lpDefault.topMargin)
        assertEquals(4, lpDefault.rightMargin)
        assertEquals(2, lpDefault.bottomMargin)

        val custom = snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 1,
                "system_netspeed_fixedcontent_width" to 20,
            )
        )
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        val lpCustom = number.layoutParams as LinearLayout.LayoutParams
        assertEquals(40, lpCustom.width) // 20dp * density 2
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lpCustom.height)
        assertEquals(0f, lpCustom.weight, 0f)
        assertEquals(Gravity.CENTER, lpCustom.gravity)

        val default2 = makeDefaultSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, default2, false)
        val lpRestored = number.layoutParams as LinearLayout.LayoutParams
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lpRestored.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lpRestored.height)
        assertNotSame(lpDefault, lpRestored)
    }

    // E. parent translationY and padding custom->default restore original
    @Test
    fun applyNetSpeedTextStyle_parentTranslationYPadding_reversible() {
        val number = RecordingTextView()
        val speedView = speedViewWith(number)
        setOriginalNetSpeedStyle(number, null, speedView)

        val default = makeDefaultSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, default, false)
        assertEquals(0f, speedView.translationY, 0f)
        assertEquals(8, speedView.paddingStart)
        assertEquals(4, speedView.paddingTop)
        assertEquals(8, speedView.paddingEnd)
        assertEquals(4, speedView.paddingBottom)

        val custom = snapshotFrom(
            mapOf(
                "system_netspeed_verticaloffset" to 12,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
            )
        )
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        assertEquals(4f, speedView.translationY, 0f) // (12-8)*0.5 = 2dp * density 2
        assertEquals(10, speedView.paddingStart)
        assertEquals(4, speedView.paddingTop)
        assertEquals(12, speedView.paddingEnd)
        assertEquals(4, speedView.paddingBottom)

        val default2 = makeDefaultSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, default2, false)
        assertEquals(0f, speedView.translationY, 0f)
        assertEquals(8, speedView.paddingStart)
        assertEquals(4, speedView.paddingTop)
        assertEquals(8, speedView.paddingEnd)
        assertEquals(4, speedView.paddingBottom)
    }

    // F. alignment 2/3/4 and 2/3/4->1 restore original
    @Test
    fun applyNetSpeedTextStyle_alignment_reversible() {
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)
        setOriginalNetSpeedStyle(number, unit, speedView)

        val align2 = snapshotFrom(mapOf("system_detailednetspeed_align" to 2))
        val align3 = snapshotFrom(mapOf("system_detailednetspeed_align" to 3))
        val align4 = snapshotFrom(mapOf("system_detailednetspeed_align" to 4))
        val align1 = makeDefaultSnapshot()

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, align2, false)
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, number.textAlignment)
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, unit.textAlignment)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, align3, false)
        assertEquals(View.TEXT_ALIGNMENT_CENTER, number.textAlignment)
        assertEquals(View.TEXT_ALIGNMENT_CENTER, unit.textAlignment)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, align4, false)
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, number.textAlignment)
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, unit.textAlignment)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, align1, false)
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, number.textAlignment)
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, unit.textAlignment)
    }

    // G. typeface-only callback after full snapshot
    @Test
    fun onNetworkSpeedTextAppearanceChanged_invalidatesFullStyleAndRestoresTypefaceOnly() {
        val number = RecordingTextView()
        val speedView = speedViewWith(number)
        setOriginalNetSpeedStyle(number, null, speedView)

        val custom = makeCustomSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        assertNotNull(getFullSnapshotId(speedView))
        val original = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertNotNull(original)

        val textSizeBefore = number.setTextSizeCalls.size
        val typefaceBefore = number.setTypefaceCalls.size

        SystemUIStatusBarHooks.onNetworkSpeedTextAppearanceChanged(number, speedView)

        assertNull(getFullSnapshotId(speedView))
        val after = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as? SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertNotNull(after)
        assertSame(original, after)
        assertEquals(textSizeBefore, number.setTextSizeCalls.size)
        assertTrue(number.setTypefaceCalls.size > typefaceBefore)

        // next full apply re-applies once
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        assertEquals(textSizeBefore + 1, number.setTextSizeCalls.size)
        assertNotNull(getFullSnapshotId(speedView))

        // then zero-work
        val textSizeAfter = number.setTextSizeCalls.size
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        assertEquals(textSizeAfter, number.setTextSizeCalls.size)
    }

    // H. useClockStyle path
    @Test
    fun onNetworkSpeedViewInflated_useClockStyle_appliesFullCustomStyleAndCallbackDoesNotComplete() {
        setMainPrefs(
            mapOf(
                "system_netspeed_use_clock_style" to true,
                "system_netspeed_clock_style" to 123456,
                "system_detailednetspeed_style" to 1,
                "system_netspeed_boldfont" to true,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_fixedcontent_width" to 20,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
                "system_netspeed_verticaloffset" to 12,
                "system_detailednetspeed_align" to 3,
                "system_netspeed_rowspacing" to 110,
            )
        )
        resetNetSpeedSnapshotState()

        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)
        setOriginalNetSpeedStyle(number, unit, speedView)

        // Original baseline is not captured before the first full apply.
        assertNull(speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG))

        SystemUIStatusBarHooks.onNetworkSpeedViewInflated(speedView)

        assertEquals(1, number.setTextAppearanceCalls.size)
        assertEquals(1, unit.setTextAppearanceCalls.size)
        assertEquals(1, number.setTextSizeCalls.size)
        assertNotNull(getFullSnapshotId(speedView))
        val original = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertNotNull(original)

        // Simulate a later framework setTextAppearance on the number view.
        val textSizeBefore = number.setTextSizeCalls.size
        val typefaceBefore = number.setTypefaceCalls.size
        SystemUIStatusBarHooks.onNetworkSpeedTextAppearanceChanged(number, speedView)

        assertEquals(textSizeBefore, number.setTextSizeCalls.size)
        assertTrue(number.setTypefaceCalls.size > typefaceBefore)
        assertNull(getFullSnapshotId(speedView))
        val after = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as? SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertNotNull(after)
        assertSame(original, after)
    }

    // I. simulated setter failure
    @Test
    fun applyNetSpeedTextStyle_setterFailure_noCompletedId_thenRetrySucceeds() {
        val number = FailingTextView()
        val speedView = speedViewWith(number)
        setOriginalNetSpeedStyle(number, null, speedView)

        val custom = makeCustomSnapshot()
        number.failNextSetTextSize = true
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)

        assertNull(getFullSnapshotId(speedView))
        assertEquals(1, number.setTextSizeCalls.size)
        assertEquals(20f, number.textSize) // the setter recorded but then threw

        number.failNextSetTextSize = false
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)

        assertNotNull(getFullSnapshotId(speedView))
        assertEquals(2, number.setTextSizeCalls.size)
        assertEquals(20f, number.textSize)

        // idempotent
        val textSizeAfter = number.setTextSizeCalls.size
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        assertEquals(textSizeAfter, number.setTextSizeCalls.size)
    }

    // J. 100 hot-path callbacks
    @Test
    fun applyNetSpeedTextStyle_100FullCallbacks_firstFullThenZeroSetters() {
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)
        setOriginalNetSpeedStyle(number, unit, speedView)

        val custom = makeCustomSnapshot()
        repeat(100) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        }

        assertEquals(1, number.setTextSizeCalls.size)
        assertEquals(1, number.layoutParamsCalls.size)
        assertEquals(1, number.setTextAlignmentCalls.size)
        assertEquals(1, number.setGravityCalls.size)
        assertEquals(1, number.setSingleLineCalls.size)
        assertEquals(1, number.setMaxLinesCalls.size)
        assertEquals(1, number.setLineSpacingCalls.size)
        assertEquals(1, speedView.setTranslationYCalls.size)
        assertEquals(1, speedView.setPaddingRelativeCalls.size)
        assertEquals(1, unit.setTextSizeCalls.size)
        assertEquals(1, unit.setTextAlignmentCalls.size)
        assertEquals(1, unit.setVisibilityCalls.size)
    }

    /**
     * C2B-R1: TextAppearance after-hook must invalidate full-style completion only.
     * The per-view original style baseline must survive for the lifetime of the view.
     */
    @Test
    fun textAppearance_doesNotReplaceOriginalBaselineWithCustomState() {
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)
        setRichOriginalNetSpeedStyle(number, unit, speedView)

        // original is captured on the first full apply
        val style2 = snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 2,
                "system_netspeed_boldfont" to true,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_fixedcontent_width" to 20,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
                "system_netspeed_verticaloffset" to 12,
                "system_detailednetspeed_align" to 3,
                "system_netspeed_rowspacing" to 110,
            )
        )
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, style2, false)
        val original = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertNotNull(getFullSnapshotId(speedView))
        assertNotNull(original)

        // Framework TextAppearance callback: only typeface, full ID gone,
        // original state must keep the same object identity.
        number.setTag(NETSPEED_TYPEFACE_STATE_TAG, SystemUIStatusBarHooks.NetSpeedTypefaceState())
        SystemUIStatusBarHooks.onNetworkSpeedTextAppearanceChanged(number, speedView)
        assertNull(getFullSnapshotId(speedView))
        val afterTextAppearance = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertSame(original, afterTextAppearance)

        // Next full default/style1 apply must restore the original baseline precisely.
        val default = makeDefaultSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, default, false)
        val afterDefault = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertSame(original, afterDefault)

        assertEquals(3f, speedView.translationY)
        assertEquals(7, speedView.paddingStart)
        assertEquals(4, speedView.paddingTop)
        assertEquals(9, speedView.paddingEnd)
        assertEquals(5, speedView.paddingBottom)

        assertEquals(26f, number.textSize)
        assertEquals(Gravity.CENTER, number.gravity)
        assertTrue(number.isSingleLine)
        assertEquals(1, number.maxLines)
        assertEquals(0f, number.lineSpacingExtra)
        assertEquals(1f, number.lineSpacingMultiplier)
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, number.textAlignment)

        val numberLp = number.layoutParams as LinearLayout.LayoutParams
        assertEquals(137, numberLp.width)
        assertEquals(43, numberLp.height)
        assertEquals(0.5f, numberLp.weight)
        assertEquals(Gravity.CENTER, numberLp.gravity)
        assertEquals(6, numberLp.leftMargin)
        assertEquals(8, numberLp.rightMargin)
        assertEquals(2, numberLp.topMargin)
        assertEquals(3, numberLp.bottomMargin)

        assertEquals(View.VISIBLE, unit.visibility)
        assertEquals(18f, unit.textSize)
    }

    /**
     * C2B-R1: A null number LayoutParams must not trigger partial/full style application
     * or guessed original-state capture. The view must wait for real LayoutParams.
     */
    @Test
    fun nullNumberLayoutParams_doesNotCaptureOrApplyUntilRealLayoutExists() {
        val number = RecordingTextView()
        val speedView = speedViewWith(number, applyOriginalStyle = false)

        // Precondition: the framework has not yet attached a LayoutParams.
        assertNull(number.layoutParams)
        assertNull(speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG))

        val custom = snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 1,
                "system_netspeed_boldfont" to true,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_fixedcontent_width" to 20,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
                "system_netspeed_verticaloffset" to 12,
                "system_detailednetspeed_align" to 3,
                "system_netspeed_rowspacing" to 110,
            )
        )
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)

        assertNull(number.layoutParams)
        assertNull(speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG))
        assertNull(getFullSnapshotId(speedView))
        assertEquals(0, number.setTextSizeCalls.size)
        assertEquals(0, speedView.setTranslationYCalls.size)
        assertEquals(0, speedView.setPaddingRelativeCalls.size)
        assertEquals(0, number.setTypefaceCalls.size)
        assertEquals(0, number.layoutParamsCalls.size)

        // Framework now provides a real LinearLayout.LayoutParams.
        val realLp = LinearLayout.LayoutParams(137, 43)
        realLp.width = 137
        realLp.height = 43
        realLp.weight = 0.5f
        realLp.gravity = Gravity.CENTER
        realLp.leftMargin = 6
        realLp.rightMargin = 8
        realLp.topMargin = 2
        realLp.bottomMargin = 3
        number.layoutParams = realLp
        number.clearCalls()

        // Same snapshot: should now capture the real baseline and fully apply.
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, custom, false)
        val original = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertNotNull(original)
        assertNotNull(getFullSnapshotId(speedView))
        assertEquals(1, number.setTextSizeCalls.size)
        assertEquals(1, number.layoutParamsCalls.size)

        // Default snapshot must restore the real baseline, not a guessed WRAP_CONTENT/0.
        number.clearCalls()
        val default = makeDefaultSnapshot()
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, default, false)
        val afterDefault = number.layoutParams as LinearLayout.LayoutParams
        assertSame(original, speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG))
        assertEquals(137, afterDefault.width)
        assertEquals(43, afterDefault.height)
        assertEquals(0.5f, afterDefault.weight)
        assertEquals(Gravity.CENTER, afterDefault.gravity)
        assertEquals(6, afterDefault.leftMargin)
        assertEquals(8, afterDefault.rightMargin)
        assertEquals(2, afterDefault.topMargin)
        assertEquals(3, afterDefault.bottomMargin)
    }

    /**
     * C2B-R1: useClockStyle initial path — the original state must not exist before the first full
     * apply, must be captured exactly once after it, and must survive later TextAppearance calls.
     */
    @Test
    fun onNetworkSpeedViewInflated_initialClockStyle_baselineCapturedOnce() {
        setMainPrefs(
            mapOf(
                "system_netspeed_use_clock_style" to true,
                "system_netspeed_clock_style" to 123456,
                "system_detailednetspeed_style" to 1,
                "system_netspeed_boldfont" to true,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_fixedcontent_width" to 20,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
                "system_netspeed_verticaloffset" to 12,
                "system_detailednetspeed_align" to 3,
                "system_netspeed_rowspacing" to 110,
            )
        )
        resetNetSpeedSnapshotState()

        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)
        setOriginalNetSpeedStyle(number, unit, speedView)

        assertNull(speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG))

        // Simulate the framework setTextAppearance callback that would fire during useClockStyle.
        number.setTag(NETSPEED_TYPEFACE_STATE_TAG, SystemUIStatusBarHooks.NetSpeedTypefaceState())
        unit.setTag(NETSPEED_TYPEFACE_STATE_TAG, SystemUIStatusBarHooks.NetSpeedTypefaceState())
        SystemUIStatusBarHooks.onNetworkSpeedTextAppearanceChanged(number, speedView)
        assertNull(speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG))
        assertNull(getFullSnapshotId(speedView))

        // First full apply captures the post-clock baseline.
        val snapshot = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(MainModule.mPrefs)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)
        val original = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertNotNull(original)
        assertNotNull(getFullSnapshotId(speedView))

        // A later TextAppearance must not discard the original baseline.
        SystemUIStatusBarHooks.onNetworkSpeedTextAppearanceChanged(number, speedView)
        val after = speedView.getTag(NETSPEED_ORIGINAL_STYLE_STATE_TAG) as? SystemUIStatusBarHooks.NetSpeedOriginalStyleState
        assertSame(original, after)
        assertNull(getFullSnapshotId(speedView))
    }

    private fun setRichOriginalNetSpeedStyle(
        number: RecordingTextView,
        unit: RecordingTextView,
        speedView: RecordingLinearLayout,
    ) {
        speedView.setTranslationY(3f)
        speedView.setPaddingRelative(7, 4, 9, 5)

        number.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26f)
        number.setGravity(Gravity.CENTER)
        number.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY)
        number.setSingleLine(true)
        number.setMaxLines(1)
        number.setLineSpacing(0f, 1f)

        val numberLp = LinearLayout.LayoutParams(137, 43)
        // The test stub LayoutParams constructor is a no-op, so assign width/height explicitly.
        numberLp.width = 137
        numberLp.height = 43
        numberLp.weight = 0.5f
        numberLp.gravity = Gravity.CENTER
        numberLp.leftMargin = 6
        numberLp.rightMargin = 8
        numberLp.topMargin = 2
        numberLp.bottomMargin = 3
        number.layoutParams = numberLp

        unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18f)
        unit.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY)
        unit.setVisibility(View.VISIBLE)
        val unitLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        unitLp.width = LinearLayout.LayoutParams.WRAP_CONTENT
        unitLp.height = LinearLayout.LayoutParams.WRAP_CONTENT
        unitLp.weight = 0f
        unitLp.gravity = Gravity.CENTER
        unit.layoutParams = unitLp

        number.clearCalls()
        speedView.clearCalls()
        unit.clearCalls()
    }

    private fun getNetSpeedTextStyleObserver(): ModuleHelper.PreferenceObserver {
        val runtimeState = getNetSpeedRuntimeState() ?: error("net speed runtime state not installed")
        val field = runtimeState::class.java.getDeclaredField("observer").apply { isAccessible = true }
        return field.get(runtimeState) as ModuleHelper.PreferenceObserver
    }
}
