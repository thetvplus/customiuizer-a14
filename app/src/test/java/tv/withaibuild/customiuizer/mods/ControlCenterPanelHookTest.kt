package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.LinearLayout
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import miui.systemui.controlcenter.panel.main.MainPanelAnimController
import miui.systemui.controlcenter.panel.main.MainPanelController
import miui.systemui.controlcenter.qs.tileview.QSTileItemView
import miui.systemui.util.CommonUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ControlCenterPanelHookTest {
    @Test fun nullableRomBooleanHookKeepsTheMovedTilePanelAttached() = withGridHooks { installed ->
        val target = MainPanelController::class.java.getDeclaredMethod("setUseSeparatedPanels", Boolean::class.javaObjectType)
        val owner = MainPanelController(RuntimeEnvironment.getApplication())
        for (value in listOf(null, false, false)) {
            installed.invoke(target, owner, value) { error("The grid hook owns panel placement") }
        }
        assertEquals(false, owner.useSeparatedPanels)
        assertSame(owner.horizontalMainPanel, owner.leftMainPanel.parent)
        assertEquals(LinearLayout.VERTICAL, owner.horizontalMainPanel.orientation)
        assertEquals(1, owner.horizontalMainPanel.indexOfChild(owner.leftMainPanel))
    }

    @Test fun nativeTileInflateAndResizeUseTheSameCustomGeometry() = withGridHooks { installed ->
        val type = QSTileItemView::class.java
        val inflate = type.getDeclaredMethod("onFinishInflate")
        val updateHeight = type.getDeclaredMethod("updateContainerHeight").apply { isAccessible = true }
        val updateSize = type.getDeclaredMethod("updateSize").apply { isAccessible = true }
        val context = RuntimeEnvironment.getApplication()

        for ((density, iconSize, height) in listOf(Triple(240, 81, 103), Triple(320, 108, 138))) {
            val tile = QSTileItemView(context.createConfigurationContext(Configuration().apply { densityDpi = density }))
            fun updateNativeHeight() = installed.invoke(updateHeight, tile) { updateHeight.invoke(tile) }
            installed.invoke(inflate, tile) {
                tile.onFinishInflate()
                updateNativeHeight()
            }
            assertEquals(1, tile.expansions)
            assertEquals(height, tile.appliedHeight)
            assertEquals(iconSize, tile.iconFrame.layoutParams.width)
            installed.invoke(updateSize, tile) {
                updateNativeHeight()
                updateSize.invoke(tile)
            }
            assertEquals(2, tile.expansions)
            assertEquals(22, tile.labelHeight)
            assertEquals(height, tile.appliedHeight)
            assertEquals(iconSize, tile.iconFrame.layoutParams.width)
            assertEquals(iconSize, tile.iconFrame.layoutParams.height)
        }
    }

    @Test fun panelAnimationMirrorsOnlyChangedAlphaInNativeVerticalMode() = withGridHooks { installed ->
        val config = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        val context = RuntimeEnvironment.getApplication().createConfigurationContext(config)
        val owner = MainPanelAnimController(context)
        val animUpdate = MainPanelAnimController::class.java.getDeclaredMethod("onAnimUpdate")
        val configChanged = MainPanelAnimController::class.java.getDeclaredMethod("onConfigurationChanged", Int::class.javaPrimitiveType)
        fun animate() = installed.invoke(animUpdate, owner, proceed = owner::onAnimUpdate)
        try {
            owner.panelAlpha = 0.4f
            animate()
            assertEquals(1f, owner.leftMainPanel.alpha, 0f)

            CommonUtils.forceVertical = true
            animate()
            assertEquals(0.4f, owner.leftMainPanel.alpha, 0f)

            CommonUtils.modeReads = 0
            animate()
            assertEquals(1, CommonUtils.modeReads)

            CommonUtils.forceVertical = false
            installed.invoke(configChanged, owner, ActivityInfo.CONFIG_ORIENTATION) {
                owner.onConfigurationChanged(ActivityInfo.CONFIG_ORIENTATION)
            }
            assertEquals(1f, owner.leftMainPanel.alpha, 0f)
        } finally {
            CommonUtils.forceVertical = false
            CommonUtils.modeReads = 0
        }
    }

    private fun withGridHooks(test: (Map<Executable, XposedInterface.Hooker>) -> Unit) {
        val previous = XposedHelpers.moduleInst
        val installed = LinkedHashMap<Executable, XposedInterface.Hooker>()
        try {
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
            SystemUIControlCenterHooks.SystemCCGridHookLoader(javaClass.classLoader!!, 5)
            test(installed)
        } finally {
            XposedHelpers.moduleInst = previous
        }
    }

    private fun Map<Executable, XposedInterface.Hooker>.invoke(
        member: Executable,
        owner: Any,
        vararg args: Any?,
        proceed: () -> Unit,
    ) {
        getValue(member).intercept(object : XposedInterface.Chain {
            override fun getExecutable() = member
            override fun getThisObject() = owner
            override fun getArgs(): List<Any?> = error("Read only the arguments used by the hook")
            override fun getArg(index: Int): Any? = args[index]
            override fun proceed(): Any? {
                proceed.invoke()
                return null
            }
            override fun proceed(args: Array<Any?>): Any? = error("The hook must preserve the native arguments")
            override fun proceedWith(thisObject: Any): Any? = error("The hook must preserve the native owner")
            override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? = error("The hook must preserve the native call")
        })
    }
}
