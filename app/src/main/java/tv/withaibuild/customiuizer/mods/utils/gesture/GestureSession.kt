package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Per-gesture session data carried from one event to the next.
 *
 * This is kept separate from [GestureState] so the state machine can be a pure
 * function of `(snapshot, event, config, geometry)`.
 */
data class GestureSession(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val startTime: Long = 0L,
    val startPointerCount: Int = 0,
    val startBrightnessRatio: Float = 0.5f,
    val lastTouchX: Float = 0f,
    val lastTouchTime: Long = 0L,
    val currentBrightnessRatio: Float = -1f,
)
