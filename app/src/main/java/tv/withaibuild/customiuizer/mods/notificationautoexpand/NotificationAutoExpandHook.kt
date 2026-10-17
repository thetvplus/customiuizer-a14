package tv.withaibuild.customiuizer.mods.notificationautoexpand

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Thin hook installer for the Notification Auto-Expand feature.
 *
 * Performs a fail-safe target class probe before resolving the frozen ABI or creating
 * process-scoped runtime state. If the target class is missing or the probe fails with an
 * ordinary non-fatal error, installation is isolated with ModuleHelper-compatible
 * diagnostics and no runtime state, observer, or callback is created.
 *
 * If the target class is found, it resolves the frozen ABI, installs the
 * [NotificationAutoExpandRuntimeState] singleton, builds the
 * [NotificationAutoExpandEffect], and wires the [MethodHook] to all `setFeedbackIcon` methods
 * on `ExpandableNotificationRow` via [ModuleHelper.hookAllMethods].
 */
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
            // Preserve the legacy ModuleHelper diagnostics for a missing hook target class:
            // log and record TARGET_CLASS_MISSING, then return without installing a callback,
            // without building runtime state, and without entering FAST or COMPLETE_LEGACY dispatch.
            ModuleHelper.hookAllMethods(TARGET_CLASS, classLoader, HOOK_METHOD, object : MethodHook() {})
            return
        }

        val abi = NotificationAutoExpandResolver.resolve(resolutionRootClass)
        val runtimeState = NotificationAutoExpandRuntimeState.install()
        val effect = NotificationAutoExpandEffect(abi, runtimeState.snapshotRef)

        val hook = object : MethodHook() {
            override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = effect.intercept(chain)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

        ModuleHelper.hookAllMethods(resolutionRootClass, HOOK_METHOD, hook)
    }
}
