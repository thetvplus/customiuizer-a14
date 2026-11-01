package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import android.provider.Settings
import com.android.systemui.statusbar.policy.NetworkSpeedController
import com.android.systemui.statusbar.phone.SystemBarAttributesListener
import com.miui.systemui.functions.MiuiTopActivityObserver
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.MainModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ForegroundMonitorInstallationTest {
    @Test fun repeatedHostConstructionDoesNotInstallMoreHooksAndEventsKeepTheirValues() {
        val previous = XposedHelpers.moduleInst
        val config = Various.variousConfig
        val preferences = MainModule.mPrefs.getAll()
        val installed = LinkedHashMap<Executable, XposedInterface.Hooker>()
        try {
            MainModule.mPrefs.replaceSnapshot(preferences + ("various_showcallui" to "1"))
            XposedHelpers.moduleInst = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, call, args ->
                if (call.name != "hook") return@newProxyInstance null
                val target = args!![0] as Executable
                object : XposedInterface.HookBuilder {
                    override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
                    override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
                    override fun setId(id: String?): XposedInterface.HookBuilder = error("No new ID")
                    override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                        assertNull("Every target must install once", installed.put(target, hooker))
                        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.HookHandle::class.java)) { _, _, _ -> null } as XposedInterface.HookHandle
                    }
                }
            } as XposedInterface
            val param = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PackageReadyParam::class.java)) { _, call, _ ->
                when (call.name) { "getClassLoader" -> javaClass.classLoader; "getPackageName" -> "com.android.systemui"; else -> null }
            } as PackageReadyParam
            GlobalActions.setupForegroundMonitor(param)
            assertEquals(3, installed.size)
            val constructor = NetworkSpeedController::class.java.declaredConstructors.single()
            val app = RuntimeEnvironment.getApplication()
            repeat(5) {
                installed.getValue(constructor).intercept(Chain(constructor, NetworkSpeedController(app), listOf(app)))
                assertEquals(3, installed.size)
            }
            val observer = MiuiTopActivityObserver().apply { mTopActivity = ComponentName("example.foreground", "example.foreground.Main") }
            val update = observer.javaClass.getDeclaredMethod("updateTopActivity")
            installed.getValue(update).intercept(Chain(update, observer))
            assertEquals("example.foreground", Settings.Global.getString(app.contentResolver, "tv.withaibuild.customiuizer.r14.foreground.package"))
            val attributes = SystemBarAttributesListener()
            val changed = attributes.javaClass.getDeclaredMethod("onSystemBarAttributesChanged")
            attributes.statusBarStateController.mIsFullscreen = true
            installed.getValue(changed).intercept(Chain(changed, attributes))
            attributes.statusBarStateController.mIsFullscreen = false
            installed.getValue(changed).intercept(Chain(changed, attributes))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, Settings.Global.getInt(app.contentResolver, "tv.withaibuild.customiuizer.r14.foreground.fullscreen", -1))
        } finally {
            MainModule.mPrefs.replaceSnapshot(preferences)
            Various.variousConfig = config
            XposedHelpers.moduleInst = previous
        }
    }

    private class Chain(val target: Executable, val owner: Any, val arguments: List<Any?> = emptyList()) : XposedInterface.Chain {
        private var calls = 0
        override fun getExecutable() = target
        override fun getThisObject() = owner
        override fun getArgs() = arguments
        override fun getArg(index: Int) = arguments[index]
        override fun proceed(): Any? { check(++calls == 1); return null }
        override fun proceed(args: Array<Any?>): Any? = proceed()
        override fun proceedWith(thisObject: Any): Any? = proceed()
        override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? = proceed()
    }
}
