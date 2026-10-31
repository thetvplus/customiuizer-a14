package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.widget.LinearLayout
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import miui.systemui.controlcenter.panel.main.MainPanelController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ControlCenterPanelHookTest {
    @Test fun nullableRomBooleanHookKeepsTheMovedTilePanelAttached() {
        val previous = XposedHelpers.moduleInst
        val preferences = MainModule.mPrefs.getAll()
        val installed = LinkedHashMap<Executable, XposedInterface.Hooker>()
        try {
            MainModule.mPrefs.replaceSnapshot(preferences + ("system_ccgridcolumns" to 5))
            XposedHelpers.moduleInst = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, call, args ->
                if (call.name != "hook") return@newProxyInstance null
                val target = args!![0] as Executable
                object : XposedInterface.HookBuilder {
                    override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
                    override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
                    override fun setId(id: String?): XposedInterface.HookBuilder = error("No new ID")
                    override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                        assertNull(installed.put(target, hooker))
                        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.HookHandle::class.java)) { _, _, _ -> null } as XposedInterface.HookHandle
                    }
                }
            } as XposedInterface
            SystemUIControlCenterHooks.SystemCCGridHookLoader(javaClass.classLoader!!)
            val target = MainPanelController::class.java.getDeclaredMethod("setUseSeparatedPanels", Boolean::class.javaObjectType)
            val hook = installed[target]
            assertNotNull("The ROM's boxed Boolean method must actually be hooked", hook)
            val owner = MainPanelController(RuntimeEnvironment.getApplication())
            var originalCalls = 0
            hook!!.intercept(object : XposedInterface.Chain {
                override fun getExecutable() = target
                override fun getThisObject() = owner
                override fun getArgs(): List<Any?> = listOf(false)
                override fun getArg(index: Int): Any? = false
                override fun proceed(): Any? { originalCalls++; owner.setUseSeparatedPanels(false); return null }
                override fun proceed(args: Array<Any?>): Any? = proceed()
                override fun proceedWith(thisObject: Any): Any? = proceed()
                override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? = proceed()
            })
            assertEquals(0, originalCalls)
            assertEquals(false, owner.useSeparatedPanels)
            assertSame(owner.horizontalMainPanel, owner.leftMainPanel.parent)
            assertEquals(LinearLayout.VERTICAL, owner.horizontalMainPanel.orientation)
            assertEquals(1, owner.horizontalMainPanel.indexOfChild(owner.leftMainPanel))
        } finally {
            MainModule.mPrefs.replaceSnapshot(preferences)
            XposedHelpers.moduleInst = previous
        }
    }
}
