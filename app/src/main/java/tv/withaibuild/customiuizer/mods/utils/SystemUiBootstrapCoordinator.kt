package tv.withaibuild.customiuizer.mods.utils

import android.content.Context
import android.provider.Settings
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks
import tv.withaibuild.customiuizer.installers.SystemUiInstaller
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.function.Supplier

/**
 * SystemUI bootstrap coordinator.
 *
 * Owns the SystemUI-specific lifecycle that requires a live Context / ClassLoader:
 * initializer hook, fast-reboot receiver, status-bar setup, preference watch and the
 * 10-second restart guard.  MainModule stays focused on routing and delegates the
 * SystemUI branch to this object.
 */
object SystemUiBootstrapCoordinator {

    private enum class State {
        UNINITIALIZED,
        HOOK_INSTALLED,
        CONTEXT_READY,
        BASE_READY,
        PREFERENCE_READY,
        COMPLETE,
        FAILED_TRANSIENT
    }

    private val restartThresholdMs = 10000L

    @JvmStatic
    fun install(lpparam: PackageReadyParam, mPrefs: PrefMap, preferenceInit: Supplier<Boolean>, prefReady: Boolean) {
        var coordinatorState = State.UNINITIALIZED
        ReflectionCache.onSafeLifecycle(lpparam.classLoader)
        coordinatorState = State.HOOK_INSTALLED

        // 1. The SystemUIInitializer.init hook is always installed first. It is the only place
        // where we can safely obtain a live Context and finish context-dependent init.
        val fastRebootReceiverReady = booleanArrayOf(false)
        val statusBarSetupDone = booleanArrayOf(false)
        val preferenceWatchDone = booleanArrayOf(false)
        val globalActionStatusBarDone = booleanArrayOf(false)

        val initStatusBarHook = object : HookerClassHelper.MethodHook() {
            private var isHooked = false

            @Throws(Throwable::class)
            override fun before(param: HookerClassHelper.BeforeHookCallback) {
                if (isHooked || param.getThisObject() == null) return

                val mContextField: Any? = try {
                    XposedHelpers.getObjectField(param.getThisObject(), "mContext")
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                    return
                }

                if (mContextField !is Context) {
                    XposedHelpers.log("SystemUiBootstrapCoordinator: SystemUI mContext field is not a Context")
                    return
                }

                val context = mContextField as Context?
                if (context == null) {
                    XposedHelpers.log("SystemUiBootstrapCoordinator: SystemUI mContext is null in SystemUIInitializer.init, deferring context-dependent init")
                    return
                }

                try {
                    if (!fastRebootReceiverReady[0]) {
                        fastRebootReceiverReady[0] = setupFastRebootReceiver(context)
                    }
                    if (!statusBarSetupDone[0]) {
                        setupSystemUiResources(context)
                        statusBarSetupDone[0] = true
                    }
                    if (!preferenceWatchDone[0]) {
                        preferenceWatchDone[0] = preferenceInit.get()
                    }
                    evaluateGlobalActionStatusBarIfReady(lpparam, preferenceWatchDone[0], globalActionStatusBarDone)
                    if (fastRebootReceiverReady[0] && statusBarSetupDone[0] && preferenceWatchDone[0]) {
                        isHooked = true
                        coordinatorState = State.PREFERENCE_READY
                        coordinatorState = State.COMPLETE
                        HookDiagnostics.printSummaryForStage("post-init")
                    }
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                    // Do not set isHooked: one failed init step must not mark the whole pass as complete.
                    coordinatorState = State.FAILED_TRANSIENT
                }
            }
        }

        ModuleHelper.findAndHookMethod(
            "com.android.systemui.SystemUIInitializer",
            lpparam.classLoader,
            "init",
            Boolean::class.javaPrimitiveType,
            initStatusBarHook
        )

        // 2. Base hooks whose original install timing must never be skipped by the 10s restart check.
        val mContext = ModuleHelper.findContext(lpparam)
        if (mContext != null) {
            if (!fastRebootReceiverReady[0]) {
                fastRebootReceiverReady[0] = setupFastRebootReceiver(mContext)
            }
        } else {
            XposedHelpers.log("SystemUiBootstrapCoordinator: SystemUI context not ready at package ready, deferring FastReboot receiver")
        }
        evaluateGlobalActionStatusBarIfReady(lpparam, prefReady, globalActionStatusBarDone)

        // 3. The 10s restart check is only allowed to skip the non-essential hooks below.
        var skipNonEssential = false
        if (mContext != null) {
            try {
                val restartTime = Settings.System.getLong(mContext.contentResolver, "systemui_restart_time", 0L)
                val currentTime = java.lang.System.currentTimeMillis()
                if (currentTime - restartTime < restartThresholdMs) skipNonEssential = true
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                XposedHelpers.log(t)
            }
        }

        if (skipNonEssential) {
            HookDiagnostics.printSummaryForStage("onPackageReady")
            return
        }

        SystemUiInstaller.install(lpparam, mPrefs)
    }
}

/**
 * Pure gate helper for the SystemUI global-action status-bar setup.
 *
 * The status-bar hooks must not be evaluated before the preference snapshot is
 * stable, otherwise the one-time GlobalActionConfig cache is built from a
 * transient or empty snapshot and remains frozen for the process lifetime.
 */
internal fun shouldSetupGlobalActionStatusBar(prefReady: Boolean, alreadyDone: Boolean): Boolean =
    !alreadyDone && prefReady

/**
 * One-shot evaluator for the SystemUI global-action status-bar setup.
 *
 * Called from both [PackageReadyParam] and the deferred [SystemUIInitializer.init]
 * path.  The exact condition and call site are preserved so that the status-bar
 * hooks are installed at the same moment as before, but only after the
 * preference snapshot is stable.
 */
internal fun evaluateGlobalActionStatusBarIfReady(
    lpparam: PackageReadyParam,
    shouldEvaluate: Boolean,
    globalActionStatusBarDone: BooleanArray,
) {
    if (shouldSetupGlobalActionStatusBar(shouldEvaluate, globalActionStatusBarDone[0])) {
        // Base SystemUI receiver must exist before first runtime action selection.
        // Optional action-specific hooks remain lazy inside setupStatusBar().
        GlobalActionSystemServerHooks.setupStatusBar(lpparam)
        globalActionStatusBarDone[0] = true
    }
}
