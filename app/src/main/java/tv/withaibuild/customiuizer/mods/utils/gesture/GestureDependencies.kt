package tv.withaibuild.customiuizer.mods.utils.gesture

import java.lang.reflect.Method

/**
 * Immutable snapshot of the runtime objects and geometry needed to execute a gesture.
 *
 * This object does not hold a [View], [Activity], or [Context]; it only keeps the
 * system service objects and screen geometry resolved ahead of time.  The two
 * display-manager [Method] handles are resolved once during preparation so that
 * [ACTION_MOVE] does not have to perform a string-based method lookup.
 */
data class GestureDependencies(
    val ownerId: Int,
    val classLoaderIdentity: String,
    val displayManager: Any,
    val displayId: Int,
    val minimumBacklight: Float,
    val maximumBacklight: Float,
    val audioManager: Any?,
    val statusBarHeight: Int,
    val screenWidth: Int,
    val density: Float,
    val getBrightnessMethod: Method? = null,
    val setTemporaryBrightnessMethod: Method? = null,
    val setBrightnessMethod: Method? = null,
)
