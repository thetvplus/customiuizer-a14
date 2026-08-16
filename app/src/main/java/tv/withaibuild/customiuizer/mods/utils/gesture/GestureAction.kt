package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Mirror of the MotionEvent action constants used inside the pure state machine.
 *
 * The values match [android.view.MotionEvent] so that an adapter can map directly,
 * but the state machine itself does not depend on the Android SDK.
 */
object GestureAction {
    const val DOWN = 0
    const val UP = 1
    const val MOVE = 2
    const val CANCEL = 3
    const val POINTER_DOWN = 5
    const val POINTER_UP = 6
}
