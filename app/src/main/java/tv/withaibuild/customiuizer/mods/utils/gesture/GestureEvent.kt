package tv.withaibuild.customiuizer.mods.utils.gesture

import android.view.MotionEvent

/**
 * Pure data representation of a touch event passed to the gesture state machine.
 *
 * The [entry] and [ownerId] allow the machine to keep per-owner state; the physical
 * identity is described by [downTime], [deviceId] and [source].  The coordinates and
 * timing are kept as primitives to avoid Android object coupling.
 *
 * [pointerCount] is the raw count reported by the [MotionEvent] at the time of dispatch.
 * [activePointerCount] is the normalized post-action active pointer count: for
 * [MotionEvent.ACTION_UP] it is `0`, for [MotionEvent.ACTION_POINTER_UP] it is
 * `pointerCount - 1`, and for all other actions it equals [pointerCount].  This makes
 * the state machine independent of the raw `pointerCount` ambiguity.
 */
data class GestureEvent(
    val entry: GestureEntry,
    val actionMasked: Int,
    val downTime: Long,
    val eventTime: Long,
    val x: Float,
    val y: Float,
    val pointerCount: Int,
    val activePointerCount: Int = activePointerCountOf(actionMasked, pointerCount),
    val ownerId: Int,
    val deviceId: Int = 0,
    val source: Int = 0,
) {
    companion object {
        @JvmStatic
        fun activePointerCountOf(actionMasked: Int, pointerCount: Int): Int = when (actionMasked) {
            MotionEvent.ACTION_UP -> 0
            MotionEvent.ACTION_POINTER_UP -> (pointerCount - 1).coerceAtLeast(0)
            else -> pointerCount
        }
    }
}
