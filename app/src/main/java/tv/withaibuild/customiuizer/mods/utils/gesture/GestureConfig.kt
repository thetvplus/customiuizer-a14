package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Immutable snapshot of all preferences that can influence a single gesture.
 *
 * This object is read once at [android.view.MotionEvent.ACTION_DOWN] and used
 * unchanged until the gesture completes.  No preference access is permitted inside
 * [GestureStateMachine.process] or the [android.view.MotionEvent.ACTION_MOVE] path.
 */
data class GestureConfig(
    val singleAction: Int = 1,
    val dualAction: Int = 1,
    val brightnessSensitivityFactor: Float = 0.618f,
    val volumeSensitivityFactor: Float = 1.0f,
    val doubleTapAction: Int = 1,
    val doubleTapLeftAction: Int = 1,
    val doubleTapRightAction: Int = 1,
    val longPressAction: Int = 1,
    val longPressVibrate: Boolean = false,
    val ignoreVibrateOff: Boolean = false,
) {
    companion object {
        @JvmField
        val DEFAULT = GestureConfig()
    }
}
