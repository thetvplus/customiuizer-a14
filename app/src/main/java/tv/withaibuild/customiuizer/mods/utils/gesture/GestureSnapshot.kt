package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Immutable state of one gesture machine tick.
 *
 * The [state] is the high-level phase; the [session] holds the per-gesture data
 * needed for double-tap, long-press and brightness calculations.
 */
data class GestureSnapshot(
    val state: GestureState = GestureState.IDLE,
    val session: GestureSession = GestureSession(),
)
