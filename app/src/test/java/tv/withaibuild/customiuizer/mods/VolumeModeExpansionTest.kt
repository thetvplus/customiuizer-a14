package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.view.View
import com.android.systemui.miui.volume.MiuiVolumeDialogView
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Executable
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class VolumeModeExpansionTest {
    @Test fun nativeFooterUpdatesCannotReopenAnEmptyShortcutContainerOrHideTheMoreButton() {
        val previous=XposedHelpers.moduleInst
        val prefs=MainModule.mPrefs.getAll()
        val hooks=LinkedHashMap<Executable,XposedInterface.Hooker>()
        try {
            MainModule.mPrefs.clear()
            XposedHelpers.moduleInst=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(XposedInterface::class.java)) { _,call,args ->
                if(call.name!="hook") return@newProxyInstance null
                val target=args!![0] as Executable
                object: XposedInterface.HookBuilder {
                    override fun setPriority(priority: Int): XposedInterface.HookBuilder=this
                    override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder=this
                    override fun setId(id: String?): XposedInterface.HookBuilder=error("No new ID")
                    override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                        hooks[target]=hooker
                        return Proxy.newProxyInstance(javaClass.classLoader,arrayOf(XposedInterface.HookHandle::class.java)) { _,_,_ -> null } as XposedInterface.HookHandle
                    }
                }
            } as XposedInterface
            SystemUIControlCenterHooks.VolumeModeButtonColorsHook(requireNotNull(javaClass.classLoader))
            val method=MiuiVolumeDialogView::class.java.getDeclaredMethod("updateFooterVisibility",Boolean::class.javaPrimitiveType)
            for(mute in listOf(false,true)) for(dnd in listOf(false,true)) {
                MainModule.mPrefs.put("system_volume_hide_mute_shortcut",mute)
                MainModule.mPrefs.put("system_volume_hide_dnd_shortcut",dnd)
                SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()
                val dialog=MiuiVolumeDialogView(RuntimeEnvironment.getApplication())
                val layout=dialog.mRingerModeLayout
                val state=SystemUIControlCenterHooks.VolumeModeSharedVisibilityState(layout)
                XposedHelpers.setAdditionalInstanceField(layout,"customiuizer_volumeModeSharedVisibilityState",state)
                for(show in listOf(true,false,true)) {
                val chain=object: XposedInterface.Chain {
                    override fun getExecutable()=method
                    override fun getThisObject()=dialog
                    override fun getArgs(): List<Any?> = listOf(show)
                    override fun getArg(index: Int): Any = show
                    override fun proceed(): Any? { dialog.updateFooterVisibility(show);return null }
                    override fun proceed(args: Array<Any?>)=proceed()
                    override fun proceedWith(thisObject: Any)=proceed()
                    override fun proceedWith(thisObject: Any,args: Array<Any?>)=proceed()
                }
                val hook=hooks[method]
                if(hook==null) chain.proceed() else hook.intercept(chain)
                assertEquals("hideMute=$mute hideDnd=$dnd show=$show",if(!show || mute && dnd) View.GONE else View.VISIBLE,layout.visibility)
                assertEquals("The native more entry has separate visibility",if(show) View.VISIBLE else View.GONE,dialog.more.visibility)
                }
            }
        } finally {
            XposedHelpers.moduleInst=previous
            MainModule.mPrefs.replaceSnapshot(prefs)
            SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()
        }
    }
}
