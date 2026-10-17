package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Side-effect-free command emitted by the gesture state machine.
 *
 * The consumer is responsible for translating each command into the actual Android-side
 * action (brightness, volume, global action, or letting the system continue).
 */
sealed class GestureCommand {
    object PassThrough : GestureCommand()
    object BeginTracking : GestureCommand()
    data class ApplyTemporaryBrightness(val ratio: Float) : GestureCommand()
    data class CommitBrightness(val ratio: Float) : GestureCommand()
    data class AdjustVolume(val raise: Boolean) : GestureCommand()
    data class TriggerDoubleTap(val position: DoubleTapPosition, val actionId: Int) : GestureCommand()
    data class TriggerLongPress(val actionId: Int) : GestureCommand()
    object Reset : GestureCommand()
}

enum class DoubleTapPosition {
    LEFT,
    CENTER,
    RIGHT,
}
