package tv.withaibuild.customiuizer.mods

/**
 * Workspace vertical-gesture key mapping used by [LauncherGestureHooks.HomescreenSwipesHook].
 *
 * Direction integers 10/11 are the module's assumed Workspace ABI. This resolver only
 * proves mapping; it does not prove the current HyperOS [com.miui.home] still uses those
 * codes or still delivers a two-pointer [android.view.MotionEvent] into onVerticalGesture.
 */
object LauncherVerticalGesture {

    const val DIRECTION_UP = 10
    const val DIRECTION_DOWN = 11

    @JvmStatic
    fun resolveKey(direction: Int, pointerCount: Int): String? {
        return when (direction) {
            DIRECTION_DOWN -> when (pointerCount) {
                1 -> "launcher_swipedown"
                2 -> "launcher_swipedown2"
                else -> null
            }
            DIRECTION_UP -> when (pointerCount) {
                1 -> "launcher_swipeup"
                2 -> "launcher_swipeup2"
                else -> null
            }
            else -> null
        }
    }
}
