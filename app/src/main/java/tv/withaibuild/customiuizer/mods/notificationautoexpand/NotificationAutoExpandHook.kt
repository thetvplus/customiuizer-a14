package tv.withaibuild.customiuizer.mods.notificationautoexpand

import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

internal object NotificationAutoExpandHook {

    private const val TARGET_CLASS = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"
    private const val HOOK_METHOD = "setFeedbackIcon"

    @JvmStatic
    fun install(classLoader: ClassLoader?) {
        val resolutionRootClass = try {
            XposedHelpers.findClassIfExists(TARGET_CLASS, classLoader)
        } catch (t: Throwable) {
            val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("NotificationAutoExpandHook: target class probe failed for $TARGET_CLASS")
            XposedHelpers.log(toReport)
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.ALL_METHODS,
                TARGET_CLASS,
                HOOK_METHOD,
                "",
                HookDiagnostics.Status.INSTALL_FAILED,
                toReport.javaClass.simpleName,
            )
            return
        }

        if (resolutionRootClass == null) {
            XposedHelpers.log("Failed to hook $HOOK_METHOD in $TARGET_CLASS (class not found)")
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.ALL_METHODS,
                TARGET_CLASS,
                HOOK_METHOD,
                "",
                HookDiagnostics.Status.TARGET_CLASS_MISSING,
            )
            return
        }

        val abi = NotificationAutoExpandResolver.resolve(resolutionRootClass)
        val effect = NotificationAutoExpandEffect(abi, MainModule.mPrefs)

        ModuleHelper.hookAllMethods(resolutionRootClass, HOOK_METHOD, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return effect.intercept(chain)
            }
        })
    }
}
