package tv.withaibuild.customiuizer.mods.utils

import android.content.res.Resources
import android.util.DisplayMetrics
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-scoped, immutable configuration snapshot for the status bar height feature.
 *
 * The current configuration is published as a single immutable [State] behind a `@Volatile`
 * reference.  The immutable snapshot allows Architecture C hot paths to read one consistent State
 * per callback.  Compatibility getters remain during the C1 migration and each reads the currently
 * published snapshot.
 *
 * Enabled semantics:
 * - `system_statusbarheight == 11` (DEFAULT_SENTINEL) → disabled, behaves like stock.
 * - any other value → enabled, `configuredDp` equals the raw value (12, 27, 28, ...).
 *
 * Note: 27 is the default visual height, but a user that explicitly sets 27 is still
 * "enabled" and should have their 27dp respected.
 *
 * Density handling:
 * - `Resources.getSystem()` returns the global system density (e.g. ro.sf.lcd_density).
 * - The authoritative display may have a different logical density (e.g. per-display
 *   scaling on HyperOS). The hot path uses `configuredPx` which is computed from the
 *   display density that the status bar window is actually rendered with.
 *
 * Generation:
 * - Bumped only when the effective state (preference, enabled, configuredDp,
 *   configuredPx, or the density that drives them) changes.
 * - Identical preference and identical density do not increase the generation.
 */
object StatusBarHeightConfig {

    const val PREF_KEY = "system_statusbarheight"
    const val DEFAULT_SENTINEL = 11
    const val DEFAULT_DP = 27

    /** Immutable configuration snapshot.  Replaced, never mutated. */
    data class State(
        val rawPreferenceDp: Int,
        val enabled: Boolean,
        val configuredDp: Int,
        val configuredPx: Int,
        val densityDpi: Int,
        val density: Float,
    )

    /** Result of a reconfiguration attempt. */
    data class ReconfigureResult(
        val changed: Boolean,
        val previous: State,
        val current: State,
    )

    /** Monotonically increasing generation, bumped only on effective changes. */
    val generation = AtomicLong(0L)

    /** Current published configuration.  A single volatile reference to an immutable [State]. */
    @Volatile
    internal var state = State(
        rawPreferenceDp = DEFAULT_SENTINEL,
        enabled = false,
        configuredDp = DEFAULT_DP,
        configuredPx = -1,
        densityDpi = -1,
        density = -1.0f,
    )

    /** Backward-compatible hot-path getters.  They read the single snapshot, not separate volatiles. */
    @JvmStatic
    val enabled: Boolean get() = state.enabled

    @JvmStatic
    val rawPreferenceDp: Int get() = state.rawPreferenceDp

    @JvmStatic
    val configuredDp: Int get() = state.configuredDp

    @JvmStatic
    val configuredPx: Int get() = state.configuredPx

    @JvmStatic
    val densityDpi: Int get() = state.densityDpi

    @JvmStatic
    val density: Float get() = state.density

    /** Returns the current immutable configuration snapshot.  Same reference while unchanged. */
    @JvmStatic
    fun currentState(): State = state

    /**
     * Pure dp to px conversion for the supplied [Resources].
     *
     * Uses rounding to match `TypedValue.complexToDimensionPixelSize`.
     */
    @JvmStatic
    fun dpToPx(dp: Int, resources: Resources?): Int {
        return dpToPx(dp, resources?.displayMetrics?.densityDpi ?: 160)
    }

    /**
     * Pure dp to px conversion for the supplied [DisplayMetrics].
     */
    @JvmStatic
    fun dpToPx(dp: Int, metrics: DisplayMetrics?): Int {
        return dpToPx(dp, metrics?.densityDpi ?: 160)
    }

    /**
     * Pure dp to px conversion for the supplied density.
     */
    @JvmStatic
    fun dpToPx(dp: Int, densityDpi: Int): Int {
        return Math.round(dp * densityDpi / 160f)
    }

    /**
     * Pure dp to px conversion using the cached display density.
     */
    @JvmStatic
    fun dpToPx(dp: Int): Int {
        return dpToPx(dp, state.densityDpi)
    }

    /**
     * Pure preference to dp resolution. Sentinel 11 maps to the framework default 27dp
     * so callers always get a usable height; use [isEnabled] to distinguish stock mode.
     */
    @JvmStatic
    fun resolveHeightDp(prefs: PrefMap): Int {
        val opt = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL)
        return if (opt == DEFAULT_SENTINEL) DEFAULT_DP else opt
    }

    /**
     * Returns true when the user has set a custom value (anything above the sentinel).
     */
    @JvmStatic
    fun isEnabled(prefs: PrefMap): Boolean {
        val raw = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL)
        return raw > DEFAULT_SENTINEL
    }

    /**
     * Configure this process's cache from preferences and the supplied display metrics.
     *
     * This is the single cold-path call; the Insets hook reads [currentState] afterwards.
     */
    @JvmStatic
    @JvmOverloads
    fun configure(
        prefs: PrefMap,
        resources: Resources? = null,
        metrics: DisplayMetrics? = null,
    ) {
        val raw = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL)
        val dp = if (raw == DEFAULT_SENTINEL) DEFAULT_DP else raw

        val effectiveMetrics = metrics ?: resources?.displayMetrics
        val effectiveDensityDpi = effectiveMetrics?.densityDpi ?: 160
        val effectiveDensity = effectiveMetrics?.density
            .takeIf { it != null && it > 0 } ?: (effectiveDensityDpi / 160f)

        val newState = State(
            rawPreferenceDp = raw,
            enabled = raw > DEFAULT_SENTINEL,
            configuredDp = dp,
            configuredPx = dpToPx(dp, effectiveDensityDpi),
            densityDpi = effectiveDensityDpi,
            density = effectiveDensity,
        )
        synchronized(this) { applyStateUnderLock(newState) }
    }

    /**
     * Reconfigure from preferences using the current cached density.
     * Called on the preference-change observer after the hook is already installed.
     *
     * Returns a [ReconfigureResult] describing whether the effective state changed.
     */
    @JvmStatic
    fun reconfigure(prefs: PrefMap): ReconfigureResult {
        val raw = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL)
        val dp = if (raw == DEFAULT_SENTINEL) DEFAULT_DP else raw

        return synchronized(this) {
            val previous = state
            val newState = previous.copy(
                rawPreferenceDp = raw,
                enabled = raw > DEFAULT_SENTINEL,
                configuredDp = dp,
                configuredPx = dpToPx(dp, previous.densityDpi),
            )
            applyStateUnderLock(newState)
        }
    }

    /**
     * Recompute px after a configuration/density change. This should only be called when
     * the process is notified of a display or configuration change and the hook is
     * already installed, so the InsetsSource frames can be adjusted to the new px.
     *
     * Returns a [ReconfigureResult] so hot-path callbacks can replace their local
     * [currentState] snapshot with the new state without a second volatile read.
     */
    @JvmStatic
    fun recomputePx(metrics: DisplayMetrics): ReconfigureResult {
        val effectiveDensity = metrics.density.takeIf { it > 0 } ?: (metrics.densityDpi / 160f)

        return synchronized(this) {
            val previous = state
            val newPx = if (previous.enabled) {
                dpToPx(previous.configuredDp, metrics.densityDpi)
            } else {
                previous.configuredPx
            }
            val newState = previous.copy(
                configuredPx = newPx,
                densityDpi = metrics.densityDpi,
                density = effectiveDensity,
            )
            applyStateUnderLock(newState)
        }
    }

    /**
     * Recompute px from a [Resources] after a configuration/density change.
     */
    @JvmStatic
    fun recomputePx(resources: Resources): ReconfigureResult {
        return recomputePx(resources.displayMetrics)
    }

    /**
     * Returns the configured px for a specific density without mutating the cache.
     */
    @JvmStatic
    fun configuredPxFor(dp: Int, metrics: DisplayMetrics): Int {
        return Math.round(dp * metrics.densityDpi / 160f)
    }

    /**
     * Apply [newState] if it differs from the current state and bump the generation.
     * Callers must hold the monitor of this object.
     */
    private fun applyStateUnderLock(newState: State): ReconfigureResult {
        val previous = state
        if (newState == previous) {
            return ReconfigureResult(false, previous, previous)
        }

        state = newState
        generation.incrementAndGet()

        return ReconfigureResult(true, previous, newState)
    }
}
