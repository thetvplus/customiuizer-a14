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
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IosBatteryHooksTest {
    private val savedModule = XposedHelpers.moduleInst
    private val hooks = LinkedHashMap<String, XposedInterface.Hooker>()
    private var failAt = 0
    private var attempts = 0
    private var unhooks = 0
    private val bitmap = Bitmap.createBitmap(88, 44, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)

    @Before fun setup() {
        DarkIconDispatcher.failure = null; DarkIconDispatcher.resolves = 0
        XposedHelpers.moduleInst = proxy(XposedInterface::class.java) { name, args ->
            assertEquals("hook", name)
            builder(args!![0] as Method)
        }
    }
    @After fun cleanup() {
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
