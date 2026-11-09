package tv.withaibuild.customiuizer.mods.statusicons

import android.graphics.Bitmap
import android.graphics.Canvas
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.statusbar.views.MiuiBatteryMeterIconView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IosBatteryHooksTest {
    private val savedModule = XposedHelpers.moduleInst
    private val savedPrefs = MainModule.mPrefs.getAll()
    private val hooks = LinkedHashMap<String, XposedInterface.Hooker>()
    private var failAt = 0
    private var attempts = 0
    private var unhooks = 0
    private val bitmap = Bitmap.createBitmap(88, 44, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)

    @Before fun setup() {
        MainModule.mPrefs.replaceSnapshot(emptyMap<String, Any>())
        DarkIconDispatcher.failure = null; DarkIconDispatcher.resolves = 0
        XposedHelpers.moduleInst = proxy(XposedInterface::class.java) { name, args ->
            assertEquals("hook", name)
            builder(args!![0] as Method)
        }
    }
    @After fun cleanup() {
        MainModule.mPrefs.replaceSnapshot(savedPrefs)
        XposedHelpers.removeAdditionalStaticField(com.android.systemui.statusbar.views.MiuiStatusBatteryContainer::class.java,
            "a14.status.spacing.measure")
        DarkIconDispatcher.failure = null
        XposedHelpers.moduleInst = savedModule
        bitmap.recycle()
    }
    private fun builder(method: Method): XposedInterface.HookBuilder = proxy(XposedInterface.HookBuilder::class.java) { name, args ->
        when (name) {
            "setPriority", "setExceptionMode" -> builder(method)
            "intercept" -> {
                hooks[method.name] = args!![0] as XposedInterface.Hooker
                attempts++
                if (attempts == failAt) throw IllegalStateException("partial installation")
                proxy(XposedInterface.HookHandle::class.java) { member, _ -> when (member) {
                    "getExecutable" -> method
                    "unhook" -> { unhooks++; null }
                    else -> throw AssertionError(member)
                } }
            }
            else -> throw AssertionError(name)
        }
    }
    private fun install(loader: ClassLoader = MiuiBatteryMeterIconView::class.java.classLoader!!) =
        IosBatteryHooks.install(proxy(PackageReadyParam::class.java) { name, _ -> when (name) {
            "getClassLoader" -> loader
            "getPackageName" -> "com.android.systemui"
            else -> throw AssertionError(name)
        } })
    private fun view() = MiuiBatteryMeterIconView().apply { layout(0, 0, 88, 44) }
    private fun ink(view: MiuiBatteryMeterIconView) =
        XposedHelpers.getAdditionalInstanceField(view, "a14.status.battery.ink") as? IosBatteryInk

    @Test fun partialInstallationLeavesEveryCapturedInterceptorPassive() {
        failAt = 2
        assertEquals(FeatureInstallResult.FAILED_TRANSIENT, install())
        assertEquals(1, unhooks)
        val view = view()
        for (name in hooks.keys) {
            val chain = Chain(name, view)
            assertSame(chain.result, chain.invoke())
            assertEquals(1, chain.nativeCalls)
        }
        assertNull(ink(view))
    }
    @Test fun missingClassInOneLoaderDoesNotPoisonAnotherLoader() {
        val parent = MiuiBatteryMeterIconView::class.java.classLoader!!
        val missing = object : ClassLoader(parent) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.endsWith("MiuiBatteryMeterIconView")) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        assertTrue(runCatching { install(missing) }.exceptionOrNull() is ClassNotFoundException)
        assertEquals(0, attempts)
        assertEquals(FeatureInstallResult.INSTALLED, install(parent))
        assertEquals(2, attempts)
    }
    @Test fun unsupportedModesAndSubclassKeepNativeRenderingAndCanRecover() {
        assertEquals(FeatureInstallResult.INSTALLED, install())
        val view = view()
        for (mode in 0..7) {
            view.mBatteryStyle = mode
            Chain("onDarkChangeInternal", view).invoke()
            val chain = Chain("onDraw", view); chain.invoke()
            assertEquals("mode $mode", if (mode == 0 || mode == 3) 0 else 1, chain.nativeCalls)
        }
        view.mBatteryStyle = 0
        for (field in listOf("mUseLegacyDrawable", "mPerformanceMode", "mQuickCharging")) {
            view.javaClass.getField(field).setBoolean(view, true)
            Chain("onDarkChangeInternal", view).invoke()
            val fallback = Chain("onDraw", view); fallback.invoke(); assertEquals(1, fallback.nativeCalls)
            view.javaClass.getField(field).setBoolean(view, false)
        }
        Chain("onDarkChangeInternal", view).invoke()
        val recovered = Chain("onDraw", view); recovered.invoke(); assertEquals(0, recovered.nativeCalls)
        val subclass = object : MiuiBatteryMeterIconView() {}
        val untouched = Chain("onDraw", subclass); untouched.invoke(); assertEquals(1, untouched.nativeCalls)
    }
    @Test fun inlineDigitsUseTheRealNativeTextViewAndReleaseOnNativeFallback() {
        assertEquals(FeatureInstallResult.INSTALLED, install())
        val meter = com.android.systemui.statusbar.views.MiuiBatteryMeterView()
        val text = android.widget.TextView(meter.context).apply { this.text = "100"; setTextColor(android.graphics.Color.WHITE) }
        meter.mBatteryTextDigitView = text
        val view = view().apply { mBatteryStyle = 1; mLevel = 100 }
        meter.addView(view); meter.addView(text)
        meter.layout(0, 0, 88, 44); view.layout(0, 0, 88, 44); text.layout(0, 0, 88, 44)
        Chain("onDarkChangeInternal", view).invoke()
        assertTrue(ink(view)!!.active)
        val shader = text.paint.shader
        assertNotNull(shader)
        assertEquals("100", text.text.toString())
        assertEquals(android.graphics.Color.WHITE, text.currentTextColor)
        repeat(10) { Chain("onDraw", view).invoke(); assertSame(shader, text.paint.shader) }
        view.mQuickCharging = true
        Chain("onDarkChangeInternal", view).invoke()
        assertFalse(ink(view)!!.active)
        assertNull(text.paint.shader)
        val fallback = Chain("onDraw", view); fallback.invoke(); assertEquals(1, fallback.nativeCalls)
    }
    @Test fun failedStateRefreshDropsOldInkAndNativeExceptionsEscapeExactlyOnce() {
        assertEquals(FeatureInstallResult.INSTALLED, install())
        val view = view()
        Chain("onDarkChangeInternal", view).invoke()
        assertTrue(ink(view)!!.active)
        DarkIconDispatcher.failure = IllegalStateException("theme resolution")
        val update = Chain("onDarkChangeInternal", view)
        assertSame(update.result, update.invoke()); assertEquals(1, update.nativeCalls)
        assertFalse(ink(view)!!.active)
        val fallback = Chain("onDraw", view); fallback.invoke(); assertEquals(1, fallback.nativeCalls)
        DarkIconDispatcher.failure = null
        for (failure in listOf(IllegalStateException("native"), OutOfMemoryError("native fatal"))) {
            val chain = Chain("onDraw", view, failure)
            assertSame(failure, runCatching { chain.invoke() }.exceptionOrNull())
            assertEquals(1, chain.nativeCalls)
        }
        val fatal = OutOfMemoryError("module fatal")
        DarkIconDispatcher.failure = fatal
        val chain = Chain("onDarkChangeInternal", view)
        assertSame(fatal, runCatching { chain.invoke() }.exceptionOrNull())
        assertEquals(1, chain.nativeCalls)
    }
    @Test fun unchangedFramesReuseInkWithoutThemeQueriesOrArgumentArrays() {
        assertEquals(FeatureInstallResult.INSTALLED, install())
        val view = view()
        Chain("onDarkChangeInternal", view).invoke()
        val first = ink(view)
        DarkIconDispatcher.resolves = 0
        repeat(100) {
            val chain = Chain("onDraw", view)
            assertNull(chain.invoke()); assertEquals(0, chain.nativeCalls)
        }
        assertSame(first, ink(view)); assertEquals(0, DarkIconDispatcher.resolves)
    }
    private fun inlineMeter(): Triple<com.android.systemui.statusbar.views.MiuiBatteryMeterView, MiuiBatteryMeterIconView, android.widget.TextView> {
        val meter = com.android.systemui.statusbar.views.MiuiBatteryMeterView().apply { setPadding(2, 0, 3, 0) }
        val number = android.widget.TextView(meter.context).apply { text = "100" }
        meter.mBatteryTextDigitView = number
        val icon = view().apply { mBatteryStyle = 1; mLevel = 100 }
        meter.addView(icon); meter.addView(number)
        meter.layout(0, 0, 88, 44); icon.layout(0, 0, 88, 44); number.layout(0, 0, 88, 44)
        return Triple(meter, icon, number)
    }
    private fun enableSpacing() {
        MainModule.mPrefs.replaceSnapshot(mapOf("system_statusbar_ios_battery_leftmargin" to 4,
            "system_statusbar_ios_battery_rightmargin" to 8))
    }
    @Test fun failedThemeSyncReturnsNativeSpacingAndTheNativeDigitShaderTogether() {
        enableSpacing(); assertEquals(FeatureInstallResult.INSTALLED, install())
        val (meter, view, text) = inlineMeter()
        try {
            Chain("onDarkChangeInternal", view).invoke()
            assertEquals(4, meter.paddingLeft); assertEquals(7, meter.paddingRight)
            assertNotNull(text.paint.shader); assertTrue(ink(view)!!.active)
            DarkIconDispatcher.failure = IllegalStateException("theme resolution")
            val callback = Chain("onDarkChangeInternal", view)
            assertSame(callback.result, callback.invoke()); assertEquals(1, callback.nativeCalls)
            assertEquals(2, meter.paddingLeft); assertEquals(3, meter.paddingRight)
            assertNull(text.paint.shader); assertFalse(ink(view)!!.active)
            val native = Chain("onDraw", view); native.invoke(); assertEquals(1, native.nativeCalls)
        } finally { ink(view)?.clearDigits(); StatusIconSpacing.restore(meter) }
    }
    @Test fun initialSyncFailureCleansUnpublishedDigitAndSpacingOwnersAndPreservesFatalIdentity() {
        enableSpacing(); assertEquals(FeatureInstallResult.INSTALLED, install())
        for (failure in listOf(IllegalStateException("first spacing write"), OutOfMemoryError("first spacing write fatal"))) {
            val (meter, view, text) = inlineMeter()
            try {
                meter.paddingFailure = failure // Fails after native text contrast was already installed.
                val callback = Chain("onDarkChangeInternal", view)
                val thrown = runCatching { callback.invoke() }.exceptionOrNull()
                if (failure is OutOfMemoryError) assertSame(failure, thrown) else assertNull(thrown)
                assertEquals(1, callback.nativeCalls)
                assertNull("An uncommitted ink must not keep its native text shader", text.paint.shader)
                assertNull("Only a complete sync may publish the ink", ink(view))
                assertNull(XposedHelpers.getAdditionalInstanceField(meter, "a14.status.spacing"))
                assertEquals(2, meter.paddingLeft); assertEquals(3, meter.paddingRight)
            } finally { ink(view)?.clearDigits(); StatusIconSpacing.restore(meter) }
        }
    }
    private inner class Chain(val name: String, val view: MiuiBatteryMeterIconView, val failure: Throwable? = null) : XposedInterface.Chain {
        val result = Any()
        var nativeCalls = 0
        fun invoke() = hooks.getValue(name).intercept(this)
        override fun getExecutable() = if (name == "onDraw") MiuiBatteryMeterIconView::class.java.getDeclaredMethod(name, Canvas::class.java)
            else MiuiBatteryMeterIconView::class.java.getDeclaredMethod(name)
        override fun getThisObject(): Any = view
        override fun getArg(index: Int): Any = canvas
        override fun getArgs(): List<Any?> = throw AssertionError("argument array")
        override fun proceed(): Any { nativeCalls++; failure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any? = throw AssertionError("replacement arguments")
        override fun proceedWith(receiver: Any): Any? = throw AssertionError("receiver changed")
        override fun proceedWith(receiver: Any, args: Array<Any?>): Any? = throw AssertionError("receiver changed")
    }
    private fun <T> proxy(type: Class<T>, body: (String, Array<out Any?>?) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> body(method.name, args) })!!
}
