package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightRuntime

/**
 * Test-side helpers for resetting the production status-bar insets state.
 *
 * These are intentionally located under `app/src/test` and are not shipped in the APK.
 */
object StatusBarInsetsTestAccess {

    /** Reset the diagnostic counters and log stamps used by the status-bar insets hooks. */
    fun resetDiagnostics() {
        SystemStatusBarInsetsHooks.loggedCritical.clear()
        SystemStatusBarInsetsHooks.loggedRejection.clear()
        SystemStatusBarInsetsHooks.loggedLiveKeys.clear()
        SystemStatusBarInsetsHooks.rejectionLoggingExhausted = false
        clearStamps(SystemStatusBarInsetsHooks.layoutLogStamps)
        clearStamps(SystemStatusBarInsetsHooks.windowFrameLogStamps)
        clearStamps(SystemStatusBarInsetsHooks.clientFrameLogStamps)
        SystemStatusBarInsetsHooks.statusSourceLogStamp.set(0L)
        SystemStatusBarInsetsHooks.reflectionFailureLogStamp.set(0L)
        SystemStatusBarInsetsHooks.invalidShapeLogStamp.set(0L)
    }

    /** Reset the install/effect/runtime state of the status-bar insets hooks. */
    fun resetState() {
        SystemStatusBarInsetsHooks.hookInstalled = false
        SystemStatusBarInsetsHooks.statusBarHeightAbi = null
        SystemStatusBarInsetsHooks.statusBarHeightEffect = null
        resetRuntime(SystemStatusBarInsetsHooks.statusBarHeightRuntime)
        resetDiagnostics()
    }

    /** Reset [StatusBarHeightConfig] to its default state. */
    fun resetConfig() {
        StatusBarHeightConfig.generation.set(0L)
        StatusBarHeightConfig.state = StatusBarHeightConfig.State(
            rawPreferenceDp = StatusBarHeightConfig.DEFAULT_SENTINEL,
            enabled = false,
            configuredDp = StatusBarHeightConfig.DEFAULT_DP,
            configuredPx = -1,
            densityDpi = -1,
            density = -1.0f,
        )
    }

    /** Reset the bounded runtime owner state to its initial values. */
    private fun resetRuntime(runtime: StatusBarHeightRuntime) {
        runtime.knownOwners = arrayOfNulls(StatusBarHeightRuntime.MAX_TRACKED)
        runtime.latestKnownStatusBar = null
        runtime.typeMatchObserved = false
        runtime.fallbackProbeBudget.set(StatusBarHeightRuntime.MAX_FALLBACK_PROBES)
        runtime.lastRefreshGeneration.set(-1L)
    }

    private fun clearStamps(stamps: java.util.concurrent.atomic.AtomicLongArray) {
        for (i in 0 until stamps.length()) stamps.set(i, 0L)
    }
}
