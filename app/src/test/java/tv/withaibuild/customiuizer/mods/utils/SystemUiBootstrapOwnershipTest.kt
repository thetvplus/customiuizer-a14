package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.Context
import com.android.systemui.SystemUIInitializer
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import java.util.function.Supplier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.installers.GenericAppInstaller
import tv.withaibuild.customiuizer.utils.PrefMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SystemUiBootstrapOwnershipTest {
    @Test fun repeatedPackageReadyAndDelegatingLoadersShareTheActualInitializerOwner() {
        val previous = XposedHelpers.moduleInst
        val prefs = MainModule.mPrefs.getAll()
        val targets = mutableListOf<Executable>()
        try {
            MainModule.mPrefs.replaceSnapshot(emptyMap<String, Any?>())
            XposedHelpers.moduleInst = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, call, args ->
                if (call.name != "hook") return@newProxyInstance null
                val target = args!![0] as Executable
                object : XposedInterface.HookBuilder {
                    var id: String? = null
                    override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
                    override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
                    override fun setId(id: String?): XposedInterface.HookBuilder { this.id = id; return this }
                    override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                        targets.add(target)
                        val assignedId = id
                        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.HookHandle::class.java)) { _, method, _ ->
                            if (method.name == "getId") assignedId else null
                        } as XposedInterface.HookHandle
                    }
                }
            } as XposedInterface
            repeat(5) {
                val loader = if (it % 2 == 0) javaClass.classLoader else object : ClassLoader(javaClass.classLoader) {}
                val param = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PackageReadyParam::class.java)) { _, call, _ ->
                    when (call.name) { "getClassLoader" -> loader; "getPackageName" -> "com.android.systemui"; else -> null }
                } as PackageReadyParam
                SystemUiBootstrapCoordinator.install(param, PrefMap(), Supplier { false }, false)
                GenericAppInstaller.installPostAttach(param, PrefMap(), false, false, false, false)
            }
            val init = SystemUIInitializer::class.java.getDeclaredMethod("init", Boolean::class.javaPrimitiveType)
            assertEquals("One hook per resolved native method, even across delegating loaders", 1, targets.count { it == init })
            val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java)
            assertEquals("The boot-class method is shared by all package loaders", 1, targets.count { it == attach })
        } finally {
            MainModule.mPrefs.replaceSnapshot(prefs)
            XposedHelpers.moduleInst = previous
        }
    }
}
