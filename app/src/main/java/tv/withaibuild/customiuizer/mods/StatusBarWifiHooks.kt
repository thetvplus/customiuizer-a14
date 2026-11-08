package tv.withaibuild.customiuizer.mods

import android.view.View
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Two independent patches at one native update. No copied Wi-Fi state or owned View lifecycle. */
internal object StatusBarWifiHooks {
    private const val KEY="a14.wifi.native.update"

    fun install(loader: ClassLoader,standard: Boolean): FeatureInstallResult = try {
        val view=Class.forName("com.android.systemui.statusbar.StatusBarWifiView",false,loader)
        val state=Class.forName("com.android.systemui.statusbar.phone.StatusBarSignalPolicy\$WifiIconState",false,view.classLoader)
        install(view.getDeclaredMethod("applyWifiState",state),standard)
    } catch (error: Throwable) { unavailable(error) }

    internal fun install(method: Method,standard: Boolean,
        hook: (Method,MethodHook)->CustomMethodUnhooker?=ModuleHelper::hookMethod): FeatureInstallResult = try {
        val owner=method.declaringClass
        synchronized(owner) {
            require(method.name=="applyWifiState" && method.parameterCount==1 &&
                method.returnType==Void.TYPE && !Modifier.isStatic(method.modifiers))
            val patch=(XposedHelpers.getAdditionalStaticField(owner,KEY) as? Patch)
                ?: Patch(method,PreparedHookBatch(hook)).also { XposedHelpers.setAdditionalStaticField(owner,KEY,it) }
            require(patch.method==method)
            if (standard && patch.standard!=null || !standard && patch.activity!=null)
                return FeatureInstallResult.ALREADY_INSTALLED
            // Prepare only this consumer. Missing standard fields must not disable
            // activity hiding, and a missing activity View must not disable standard rendering.
            val fields=if (standard) Standard(
                field(method.parameterTypes[0],"wifiStandard",Int::class.javaPrimitiveType!!),
                field(method.parameterTypes[0],"showWifiStandard",Boolean::class.javaPrimitiveType!!)) else null
            val activity=if (!standard) field(owner,"mWifiActivityView",View::class.java) else null
            val result=patch.batch.install(listOf(method to patch))
            if (result.isActive) {
                if (standard) patch.standard=fields else patch.activity=activity
                FeatureInstallResult.INSTALLED
            } else result
        }
    } catch (error: Throwable) { unavailable(error) }

    private fun field(owner: Class<*>,name: String,type: Class<*>): Field = XposedHelpers.findField(owner,name).also {
        require(type.isAssignableFrom(it.type) && !Modifier.isStatic(it.modifiers))
    }
    private fun unavailable(error: Throwable): FeatureInstallResult {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        XposedHelpers.log("Wi-Fi native update contract unavailable",error)
        return FeatureInstallResult.FAILED_PERMANENT
    }
    private class Standard(val code: Field,val shown: Field)

    private class Patch(val method: Method,val batch: PreparedHookBatch): MethodHook() {
        @Volatile var standard: Standard?=null
        @Volatile var activity: Field?=null

        override fun intercept(chain: XposedInterface.Chain): Any? {
            standard?.let { fields -> ModuleHelper.guarded {
                val mode=SystemStatusBarIconHooks.wifiStandard
                if (mode!=1) chain.getArg(0)?.let { state ->
                    fields.shown.setBoolean(state,mode==2 && fields.code.getInt(state)>0)
                }
            } }
            val result=try {
                chain.proceed()
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                // The existing activity patch also runs after an ordinary native
                // failure. Preserve that phase and rethrow the original unchanged.
                activity?.let { field -> hideActivity(field,chain) }
                throw error
            }
            activity?.let { field -> hideActivity(field,chain) }
            return result
        }

        private fun hideActivity(field: Field,chain: XposedInterface.Chain) = ModuleHelper.guarded {
            val activity=field.get(chain.thisObject) as View
            if (activity.visibility!=View.INVISIBLE) activity.visibility=View.INVISIBLE
        }
    }
}
