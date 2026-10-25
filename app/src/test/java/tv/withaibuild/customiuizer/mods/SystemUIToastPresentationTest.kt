package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Insets
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.feature.SystemUiFeatures
import tv.withaibuild.customiuizer.mods.utils.feature.ToastPresentationFeatureId
import tv.withaibuild.customiuizer.utils.PrefMap
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Proxy
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemUIToastPresentationTest {
    private var foreheadHeightDp = 48
    private val base: Context get() = RuntimeEnvironment.getApplication()
    private val nativeContext get() = object : ContextWrapper(base) {
        private val nativeResources = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun getIdentifier(name: String, type: String?, pkg: String?): Int = when (name) {
                "strong_toast_width" -> WIDTH
                "strong_toast_height" -> HEIGHT
                else -> super.getIdentifier(name, type, pkg)
            }
            override fun getDimensionPixelSize(id: Int): Int = when (id) {
                WIDTH -> (320 * displayMetrics.density).toInt()
                HEIGHT -> (48 * displayMetrics.density).toInt()
                else -> super.getDimensionPixelSize(id)
            }
        }
        override fun getResources() = nativeResources
        override fun getSystemService(name: String): Any? {
            if (name != Context.WINDOW_SERVICE) return super.getSystemService(name)
            return Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, _ ->
                if (method.name != "getCurrentWindowMetrics") error(method.name)
                WindowMetrics(Rect(0, 0, 1080, 2400), WindowInsets.Builder()
                    .setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(),
                        Insets.of(0, (foreheadHeightDp * nativeResources.displayMetrics.density).toInt(), 0, 0)).build())
            } as WindowManager
        }
    }

    @Test
    fun disabledOptionsDoNotTouchTheSystemUiLoaderAndEitherOptionEnablesOnlyItsOwner() {
        val param = Proxy.newProxyInstance(PackageReadyParam::class.java.classLoader,
            arrayOf(PackageReadyParam::class.java)) { _, method, _ -> error("Unexpected loader access: ${method.name}") } as PackageReadyParam
        for (bottom in listOf(0, 120)) for (capsule in listOf(false, true)) {
            val prefs = PrefMap().apply { put("system_toast_bottom", bottom); put("system_toast_capsule", capsule) }
            val spec = SystemUiFeatures.all(param, prefs).single { it.id == ToastPresentationFeatureId }
            assertEquals(bottom > 0 || capsule, spec.isEnabled(prefs))
        }
        assertEquals(800, ToastPresentationConfig.read(PrefMap().apply { put("system_toast_bottom", 10000) }).offsetDp)
        assertEquals(20500L, ToastPresentationConfig.read(PrefMap().apply { put("system_toasttime", 19) }).minimumTimeoutMs)
    }

    @Test
    fun positionUsesTheSafeBottomAndPreservesHorizontalAndWindowBehavior() {
        val metrics = WindowMetrics(Rect(0, 0, 1080, 2400), WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 80, 0, 100)).build())
        val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)) { _, method, _ ->
            if (method.name == "getCurrentWindowMetrics") metrics else error(method.name)
        } as WindowManager
        val context = object : ContextWrapper(base) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.WINDOW_SERVICE) manager else super.getSystemService(name)
        }
        val text = TextView(context).apply { this.text = "复制成功" }
        val params = WindowManager.LayoutParams().apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            x = 18
            verticalMargin = 0.3f
            horizontalMargin = 0.2f
        }
        SystemUIToastPresentation.applyPosition(text, params, ToastPosition.BOTTOM, 120)
        assertEquals((120 * text.resources.displayMetrics.density).toInt(), params.y)
        assertEquals(Gravity.BOTTOM or Gravity.RIGHT, params.gravity)
        assertEquals(0f, params.verticalMargin)
        assertEquals(0.2f, params.horizontalMargin)
        assertEquals(18, params.x)
        assertEquals(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, params.flags)
        SystemUIToastPresentation.applyPosition(text, params, ToastPosition.BOTTOM, 10000)
        assertEquals(2400 - 80 - 100 - text.measuredHeight, params.y)
    }

    @Test
    fun eachPositionKeepsItsOffsetAndExplicitDefaultOverridesLegacyBottom() {
        val prefs = PrefMap().apply {
            put("system_toast_top", 48)
            put("system_toast_center", 350)
            put("system_toast_bottom", 120)
        }
        assertEquals(ToastPosition.BOTTOM, ToastPresentationConfig.read(prefs).position)
        for ((position, offset) in listOf(0 to 0, 1 to 48, 2 to -50, 3 to 120)) {
            prefs.put("system_toast_position", position.toString())
            val config = ToastPresentationConfig.read(prefs)
            assertEquals(ToastPosition.fromValue(position), config.position)
            assertEquals(offset, config.offsetDp)
            assertEquals(position != 0, config.enabled)
        }
        prefs.put("system_toast_position", "999")
        assertFalse(ToastPresentationConfig.read(prefs).enabled)
        prefs.put("system_toast_position", "2")
        prefs.put("system_toast_center", Int.MIN_VALUE)
        assertEquals(-400, ToastPresentationConfig.read(prefs).offsetDp)
        prefs.put("system_toast_center", Int.MAX_VALUE)
        assertEquals(400, ToastPresentationConfig.read(prefs).offsetDp)
    }

    @Test
    fun seededToastReuseRotationAndInsetChangesNeverLeaveTextOutsideTheSafeArea() {
        for (seed in intArrayOf(0xA14, 0x70A5)) {
            val random = Random(seed)
            var display = Rect(0, 0, 1080, 2400)
            var top = 80
            var bottom = 100
            val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader,
                arrayOf(WindowManager::class.java)) { _, method, _ ->
                if (method.name != "getCurrentWindowMetrics") error(method.name)
                WindowMetrics(display, WindowInsets.Builder()
                    .setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(), Insets.of(0, top, 0, 0))
                    .setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, bottom)).build())
            } as WindowManager
            val context = object : ContextWrapper(base) {
                override fun getSystemService(name: String): Any? =
                    if (name == Context.WINDOW_SERVICE) manager else super.getSystemService(name)
            }
            fun newToast() = TextView(context).apply { layoutParams = FrameLayout.LayoutParams(-2, -2) }
            var toast = newToast()
            val params = WindowManager.LayoutParams()
            repeat(48) { step ->
                display = if (random.nextBoolean()) Rect(0, 0, 1080, 2400) else Rect(0, 0, 2400, 1080)
                top = random.nextInt(160)
                bottom = random.nextInt(160)
                if (random.nextInt(5) == 0) toast = newToast() // native View rebuild
                toast.text = "提示 🌤 ".repeat(random.nextInt(1, 100))
                toast.textSize = random.nextInt(12, 41).toFloat()
                val mode = listOf(ToastPosition.TOP, ToastPosition.CENTER, ToastPosition.BOTTOM)[random.nextInt(3)]
                val offset = listOf(Int.MIN_VALUE, -24, 0, 24, Int.MAX_VALUE)[random.nextInt(5)]
                SystemUIToastPresentation.applyPosition(toast, params, mode, offset)
                val available = display.height() - top - bottom
                val room = (available - toast.measuredHeight).coerceAtLeast(0)
                val y = when (mode) {
                    ToastPosition.TOP -> params.y
                    ToastPosition.CENTER -> room / 2 + params.y
                    else -> room - params.y
                }
                assertTrue("Toast seed=$seed step=$step mode=$mode offset=$offset display=$display",
                    y >= 0 && y + toast.measuredHeight <= available)
                assertEquals(mode.gravity, params.gravity and Gravity.VERTICAL_GRAVITY_MASK)
            }
        }
    }

    @Test
    fun modesClampActualTextWithinSafeBoundsAfterRotationAndWithHiddenBars() {
        var display = Rect(0, 0, 1080, 2400)
        val insets = WindowInsets.Builder()
            .setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(), Insets.of(0, 80, 0, 0))
            .setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 100))
            .setInsetsIgnoringVisibility(WindowInsets.Type.displayCutout(), Insets.of(60, 90, 0, 0))
            .setVisible(WindowInsets.Type.systemBars(), false).build()
        val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)) { _, method, _ ->
            if (method.name == "getCurrentWindowMetrics") WindowMetrics(display, insets) else error(method.name)
        } as WindowManager
        val context = object : ContextWrapper(base) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.WINDOW_SERVICE) manager else super.getSystemService(name)
        }
        val toast = TextView(context).apply { text = "大字体提示与 emoji 🌤 ".repeat(40); textSize = 32f }
        val params = WindowManager.LayoutParams().apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
        for (bounds in listOf(Rect(0, 0, 1080, 2400), Rect(0, 0, 2400, 1080), Rect(0, 0, 320, 320))) {
            display = bounds
            for (position in listOf(ToastPosition.TOP, ToastPosition.CENTER, ToastPosition.BOTTOM)) {
                for (offset in intArrayOf(-10000, -24, 0, 24, 10000)) {
                    SystemUIToastPresentation.applyPosition(toast, params, position, offset)
                    val height = display.height() - 90 - 100
                    val maximum = (height - toast.measuredHeight).coerceAtLeast(0)
                    val top = when (position) {
                        ToastPosition.TOP -> params.y
                        ToastPosition.CENTER -> maximum / 2 + params.y
                        else -> maximum - params.y
                    }
                    assertTrue("$position $offset in $display", top >= 0 && top + toast.measuredHeight <= height)
                    assertEquals(position.gravity, params.gravity and Gravity.VERTICAL_GRAVITY_MASK)
                    if (position != ToastPosition.CENTER) assertTrue(params.y >= 0)
                }
            }
        }
        params.y = 123
        params.verticalMargin = .2f
        params.gravity = Gravity.TOP
        SystemUIToastPresentation.applyPosition(toast, params, ToastPosition.SYSTEM, 0)
        assertEquals(123, params.y)
        assertEquals(.2f, params.verticalMargin)
        assertEquals(Gravity.TOP, params.gravity)
    }

    @Test
    fun nativeModeGatesAndBusyWindowsKeepOrdinaryToastsWithoutChangingNativeState() {
        var mode = StrongToastPresentationMode.SYSTEM_DEFAULT
        val controller = ControllerFixture(nativeContext)
        val bridge = NativeToastCapsule(ControllerFixture::class.java, RootFixture::class.java) { mode }
        controller.before = bridge::onNativeShow
        bridge.capture(controller)
        for (disabled in listOf(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.HIDE)) {
            mode = disabled
            assertNull(bridge.show("完成", "example", 7000))
        }
        assertEquals(0, controller.calls)
        mode = StrongToastPresentationMode.DYNAMIC_ISLAND
        assertNull(bridge.show("副屏提示", "example", 7000, 2))
        assertEquals(0, controller.calls)
        // The smaller automatic wing must retain ordinary Toasts when all text cannot fit.
        assertNull(bridge.show("复制成功", "example", 9000))
        val lease = bridge.show("完成", "example", 9000)!!
        assertEquals("完成", controller.mMiuiStrongToast!!.mLeftTextView.text.toString())
        assertTrue(controller.duration >= 9000)
        assertNull(bridge.show("新提示", "example", 7000))
        assertEquals(1, controller.calls)
        bridge.release(lease)
        assertEquals(1, controller.mMiuiStrongToast!!.hideCalls)
        assertNull(controller.mMiuiStrongToast!!.parent)
        bridge.release(lease)
        assertEquals(1, controller.mMiuiStrongToast!!.hideCalls)
        mode = StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT
        assertNotNull(bridge.show("完成", "example", 7000))
    }

    @Test
    fun lateToastHideCannotCloseAReplacementNativeEventOrController() {
        val controller = ControllerFixture(nativeContext)
        val bridge = NativeToastCapsule(ControllerFixture::class.java, RootFixture::class.java) {
            StrongToastPresentationMode.DYNAMIC_ISLAND
        }
        controller.before = bridge::onNativeShow
        bridge.capture(controller)
        val lease = bridge.show("完成", "example", 7000)!!
        val root = controller.mMiuiStrongToast!!
        assertTrue(bridge.onNativeShow(root))
        root.mCurrentToastCategory = "charge"
        bridge.release(lease)
        assertEquals(0, root.hideCalls)
        assertNotNull(root.parent)
        val replacement = ControllerFixture(nativeContext).apply { before = bridge::onNativeShow }
        bridge.capture(replacement)
        assertNotNull(bridge.show("新提示", "example", 7000))
        assertEquals(1, replacement.calls)
        assertEquals(1, controller.calls)
    }

    @Test
    fun unsupportedTextAndNativeRejectionNeverDisappearIntoAnUnshownCapsule() {
        val controller = ControllerFixture(nativeContext)
        val bridge = NativeToastCapsule(ControllerFixture::class.java, RootFixture::class.java) {
            StrongToastPresentationMode.DYNAMIC_ISLAND
        }
        controller.before = bridge::onNativeShow
        bridge.capture(controller)
        for (text in listOf("", "a\nb", "a\rb", "很长的提示".repeat(100))) assertNull(bridge.show(text, "example", 7000))
        assertEquals(0, controller.calls)
        controller.gated = true
        assertNull(bridge.show("完成", "example", 7000))
        controller.gated = false
        controller.fontScale = 8f
        assertNull(bridge.show("完成", "example", 7000))
        assertNull(controller.mMiuiStrongToast!!.parent)
        assertEquals(0, controller.mMiuiStrongToast!!.hideCalls)
    }

    @Test
    fun collapsedForeheadChecksActualContentHeightBeforeOpeningItsWindow() {
        val controller = ControllerFixture(nativeContext)
        val bridge = NativeToastCapsule(ControllerFixture::class.java, RootFixture::class.java) {
            StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT
        }
        controller.before = bridge::onNativeShow
        bridge.capture(controller)
        foreheadHeightDp = 4
        assertNull(bridge.show("好", "example", 7000))
        assertNull(controller.mMiuiStrongToast!!.parent)
        assertEquals(0, controller.mMiuiStrongToast!!.hideCalls)
        foreheadHeightDp = 48
        assertNotNull(bridge.show("好", "example", 7000))
    }

    @Test
    fun nativeOrdinaryFailureFallsBackAndFatalFailureEscapes() {
        val controller = ControllerFixture(nativeContext)
        val bridge = NativeToastCapsule(ControllerFixture::class.java, RootFixture::class.java) {
            StrongToastPresentationMode.DYNAMIC_ISLAND
        }
        controller.before = bridge::onNativeShow
        bridge.capture(controller)
        controller.failure = IllegalStateException("native failure")
        assertNull(bridge.show("完成", "example", 7000))
        val fatal = OutOfMemoryError("native fatal")
        controller.failure = fatal
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { bridge.show("完成", "example", 7000) })
    }

    class ControllerFixture(@JvmField val mContext: Context) {
        @JvmField var mMiuiStrongToast: RootFixture? = null
        var before: (View) -> Boolean = { true }
        var calls = 0
        var duration = 0L
        var gated = false
        var fontScale = 1f
        var failure: Throwable? = null
        fun showCustomStrongToast(bundle: Bundle) {
            calls++
            failure?.let { throw it }
            if (gated) return
            val root = mMiuiStrongToast ?: RootFixture(mContext).also {
                it.mLeftTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    it.mLeftTextView.textSize * fontScale)
                mMiuiStrongToast = it
            }
            if (!before(root)) return
            root.mCurrentToastCategory = bundle.getString("strong_toast_category")!!
            root.mLeftTextView.text = JSONObject(bundle.getString("param")!!).getJSONObject("left")
                .getJSONObject("textParams").getString("text")
            duration = bundle.getLong("duration")
            FrameLayout(mContext).addView(root)
            root.mStrongToastShow = true
        }
    }
    class RootFixture(context: Context) : FrameLayout(context) {
        @JvmField var mStrongToastShow = false
        @JvmField var mCheckStartAnimation = false
        @JvmField var mCheckInOutStrongToasting = false
        @JvmField var mCurrentToastCategory = ""
        @JvmField val mLeftTextView = TextView(context)
        var hideCalls = 0
        fun hideStrongToast() {
            hideCalls++
            (parent as? ViewGroup)?.removeView(this)
            mStrongToastShow = false
        }
    }
    companion object {
        private const val WIDTH = 0x7f07fff0
        private const val HEIGHT = 0x7f07fff1
    }
}
