package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import android.util.DisplayMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarHeightArchitectureCH2Test {

    @Test
    fun readWindowAttrs_frozenField() {
        val effect = effectForWindow()
        val win = FakeWindowState()

        val attrs = effect.readWindowAttrs(win)

        assertTrue(attrs is FakeLayoutParams)
        assertEquals(TYPE_STATUS_BAR, effect.readAttrsType(attrs!!))
    }

    @Test
    fun readAttrsType_typeFieldMissing_returnsUnresolved() {
        val effect = effectWithoutType()
        val win = FakeWindowState()

        assertEquals(-1, effect.readAttrsType(win.mAttrs))
    }

    @Test
    fun readPackageName_frozenField() {
        val effect = effectForWindow()
        val attrs = FakeLayoutParams(type = 1, packageName = "com.android.systemui", height = 80)

        assertEquals("com.android.systemui", effect.readPackageName(attrs))
    }

    @Test
    fun readWindowDisplayMetrics_directMethod() {
        val effect = effectForWindow()
        val win = FakeWindowState()

        val metrics = effect.readWindowDisplayMetrics(win)

        assertSame(win.mDisplayMetrics, metrics)
    }

    @Test
    fun readWindowDisplayMetrics_displayContentFallback() {
        val effect = effectWithDisplayContentFallback()
        val win = FakeWindowState(directDisplayMetrics = false)

        val metrics = effect.readWindowDisplayMetrics(win)

        assertSame(win.mDisplayContent!!.mMetrics, metrics)
    }

    @Test
    fun readWindowDisplayMetrics_directThrowDoesNotFallback() {
        val oom = OutOfMemoryError("OOM")
        val effect = effectForWindow()
        val win = FakeWindowState(throwDisplayMetrics = oom)

        val thrown = try {
            effect.readWindowDisplayMetrics(win)
            null
        } catch (t: Throwable) {
            t
        }

        assertSame(oom, thrown)
    }

    @Test
    fun readDisplayId_frozenMethod() {
        val effect = effectForWindow()
        val win = FakeWindowState()
        win.mDisplayId = 42

        assertEquals(42, effect.readDisplayId(win))
    }

    @Test
    fun readWindowFrame_directMethod() {
        val effect = effectForWindow()
        val win = FakeWindowState()
        val expected = Rect().apply { set(0, 0, 1080, 80) }
        win.mWindowFrames.mFrame = expected

        val actual = effect.readWindowFrame(win)
        assertNotNull("getFrame method should be resolved in capability", actual)
        assertSame(expected, actual)
    }

    @Test
    fun readWindowFrame_mWindowFramesFallback() {
        val effect = effectWithWindowFramesFallback()
        val win = FakeWindowState(directFrame = false)
        val expected = Rect().apply { set(0, 0, 1080, 80) }
        win.mWindowFrames.mFrame = expected

        val actual = effect.readWindowFrame(win)
        assertNotNull(actual)
        assertSame(expected, actual)
    }

    @Test
    fun applyAndReadStatusBarHeight() {
        val effect = effectForWindow()
        val win = FakeWindowState()

        assertTrue(effect.applyStatusBarHeight(win, 44))
        assertEquals(44, win.mAttrs.height)
        assertEquals(44, effect.readStatusBarHeight(win.mAttrs))
    }

    @Test
    fun isWindowState_knownClass() {
        val effect = effectForWindow()
        val win = FakeWindowState()
        assertTrue(effect.isWindowState(win))
    }

    @Test
    fun isWindowState_unknownByClassName_fallsBackToNameCheck() {
        val effect = effectWithoutClass()
        val win = FakeWindowState()
        assertFalse(effect.isWindowState(win))
    }

    @Test
    fun isWindowState_rejectsForeignObject() {
        val effect = effectForWindow()
        assertFalse(effect.isWindowState(Any()))
    }

    @Test
    fun setHeight_onlyLayoutParams() {
        val effect = effectForWindow()
        val attrs = FakeLayoutParams(type = 1, packageName = "", height = 80)

        assertTrue(effect.setHeight(attrs, 44))
        assertEquals(44, attrs.height)
    }

    @Test
    fun setHeight_rejectsNonLayoutParams() {
        val effect = effectForWindow()
        assertFalse(effect.setHeight(Any(), 44))
    }

    private fun effectForWindow(): StatusBarHeightEffect {
        val wm = StatusBarHeightResolver.resolveWindowManagerClass(
            FakeWindowState::class.java,
            FakeLayoutParams::class.java,
        )
        val decor = DecorInsetsCapability(
            infoClass = null,
            updateMethod = null,
            displayContentClass = FakeDisplayContent::class.java,
            displayContentGetDisplayMetricsMethod = FakeDisplayContent::class.java.getDeclaredMethod("getDisplayMetrics").also { it.isAccessible = true },
            nonDecorInsetsField = null,
            nonDecorFrameField = null,
        )
        val insets = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = InsetsTypeInfo(InsetsTypeEncoding.MODERN_PUBLIC, 1, 2, 128),
            typeField = null,
            getTypeMethod = null,
            getIdMethod = null,
            getFrameMethod = null,
        )
        return StatusBarHeightEffect(StatusBarHeightAbi(insets, wm, decor, StatusBarHeightRefreshCapability(null, null, null, null, null)))
    }

    private fun effectWithDisplayContentFallback(): StatusBarHeightEffect {
        val wm = StatusBarHeightResolver.resolveWindowManagerClass(
            FakeWindowStateNoDirectMetrics::class.java,
            FakeLayoutParams::class.java,
        )
        val decor = DecorInsetsCapability(
            infoClass = null,
            updateMethod = null,
            displayContentClass = FakeDisplayContent::class.java,
            displayContentGetDisplayMetricsMethod = FakeDisplayContent::class.java.getDeclaredMethod("getDisplayMetrics").also { it.isAccessible = true },
            nonDecorInsetsField = null,
            nonDecorFrameField = null,
        )
        return StatusBarHeightEffect(StatusBarHeightAbi(emptyInsets(), wm, decor, StatusBarHeightRefreshCapability(null, null, null, null, null)))
    }

    private fun effectWithWindowFramesFallback(): StatusBarHeightEffect {
        val wm = StatusBarHeightResolver.resolveWindowManagerClass(
            FakeWindowStateNoDirectFrame::class.java,
            FakeLayoutParams::class.java,
        )
        val decor = DecorInsetsCapability(
            infoClass = null,
            updateMethod = null,
            displayContentClass = null,
            displayContentGetDisplayMetricsMethod = null,
            nonDecorInsetsField = null,
            nonDecorFrameField = null,
        )
        return StatusBarHeightEffect(StatusBarHeightAbi(emptyInsets(), wm, decor, StatusBarHeightRefreshCapability(null, null, null, null, null)))
    }

    private fun effectWithoutType(): StatusBarHeightEffect {
        val wm = WindowManagerCapability(
            windowStateClass = FakeWindowState::class.java,
            displayPolicyClass = null,
            windowStateAttrsField = FakeWindowState::class.java.getDeclaredField("mAttrs").also { it.isAccessible = true },
            windowStateDisplayContentField = null,
            windowStateWindowManagerServiceField = null,
            windowStateGetFrameMethod = null,
            windowStateGetDisplayMetricsMethod = null,
            windowStateGetDisplayIdMethod = null,
            windowStateWindowFramesField = null,
            windowFramesFrameField = null,
            clientWindowFramesClass = null,
            clientWindowFramesFrameField = null,
            layoutParamsClass = FakeLayoutParams::class.java,
            layoutParamsTypeField = null,
            layoutParamsHeightField = FakeLayoutParams::class.java.getDeclaredField("height").also { it.isAccessible = true },
            layoutParamsPackageNameField = FakeLayoutParams::class.java.getDeclaredField("packageName").also { it.isAccessible = true },
        )
        return StatusBarHeightEffect(StatusBarHeightAbi(emptyInsets(), wm, emptyDecor(), StatusBarHeightRefreshCapability(null, null, null, null, null)))
    }

    private fun effectWithoutClass(): StatusBarHeightEffect {
        val wm = WindowManagerCapability(
            windowStateClass = null,
            displayPolicyClass = null,
            windowStateAttrsField = null,
            windowStateDisplayContentField = null,
            windowStateWindowManagerServiceField = null,
            windowStateGetFrameMethod = null,
            windowStateGetDisplayMetricsMethod = null,
            windowStateGetDisplayIdMethod = null,
            windowStateWindowFramesField = null,
            windowFramesFrameField = null,
            clientWindowFramesClass = null,
            clientWindowFramesFrameField = null,
            layoutParamsClass = FakeLayoutParams::class.java,
            layoutParamsTypeField = FakeLayoutParams::class.java.getDeclaredField("type").also { it.isAccessible = true },
            layoutParamsHeightField = FakeLayoutParams::class.java.getDeclaredField("height").also { it.isAccessible = true },
            layoutParamsPackageNameField = FakeLayoutParams::class.java.getDeclaredField("packageName").also { it.isAccessible = true },
        )
        return StatusBarHeightEffect(StatusBarHeightAbi(emptyInsets(), wm, emptyDecor(), StatusBarHeightRefreshCapability(null, null, null, null, null)))
    }

    private fun emptyInsets() = InsetsSourceCapability(
        sourceClass = null,
        setFrameOneArg = true,
        setFrameFourArg = true,
        typeInfo = InsetsTypeInfo(InsetsTypeEncoding.MODERN_PUBLIC, 1, 2, 128),
        typeField = null,
        getTypeMethod = null,
        getIdMethod = null,
        getFrameMethod = null,
    )

    private fun emptyDecor() = DecorInsetsCapability(
        infoClass = null,
        updateMethod = null,
        displayContentClass = null,
        displayContentGetDisplayMetricsMethod = null,
        nonDecorInsetsField = null,
        nonDecorFrameField = null,
    )

    companion object {
        const val TYPE_STATUS_BAR = 2000
    }

    open class FakeWindowState(
        private val directDisplayMetrics: Boolean = true,
        private val directFrame: Boolean = true,
        private val throwDisplayMetrics: Throwable? = null,
    ) {
        @JvmField
        val mAttrs: FakeLayoutParams = FakeLayoutParams(
            type = TYPE_STATUS_BAR,
            packageName = "com.android.systemui",
            height = 80,
        )

        @JvmField
        var mDisplayContent: FakeDisplayContent? = FakeDisplayContent(DisplayMetrics().apply {
            densityDpi = 160
            density = 1.0f
        })

        @JvmField
        val mWindowFrames: FakeWindowFrames = FakeWindowFrames()

        var mDisplayId = 0
        val mDisplayMetrics = DisplayMetrics().apply {
            densityDpi = 160
            density = 1.0f
        }

        fun getDisplayMetrics(): DisplayMetrics? {
            throwDisplayMetrics?.let { throw it }
            return if (directDisplayMetrics) mDisplayMetrics else null
        }

        fun getDisplayId(): Int = mDisplayId

        fun getFrame(): Rect? {
            return if (directFrame) mWindowFrames.mFrame else null
        }
    }

    class FakeWindowStateNoDirectMetrics : FakeWindowState(directDisplayMetrics = false)
    class FakeWindowStateNoDirectFrame : FakeWindowState(directFrame = false)

    class FakeDisplayContent(val mMetrics: DisplayMetrics) {
        fun getDisplayMetrics(): DisplayMetrics = mMetrics
    }

    class FakeLayoutParams(
        @JvmField var type: Int,
        @JvmField var packageName: String,
        @JvmField var height: Int,
    )

    class FakeWindowFrames {
        @JvmField
        var mFrame: Rect = Rect().apply { set(0, 0, 1080, 80) }
    }
}
