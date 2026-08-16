package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Immutable, Android-free geometry used by the gesture state machine.
 *
 * The consumer is responsible for publishing a consistent snapshot when the screen,
 * density, status bar height or brightness bounds change.  [currentBrightness] is the
 * real display brightness at the start of the gesture and is not a fixed guess.
 */
data class GestureGeometry(
    val screenWidth: Int,
    val density: Float,
    val statusBarHeight: Int,
    val minBacklight: Float,
    val maxBacklight: Float,
    val currentBrightness: Float = -1f,
)

/** Convert runtime dependencies to the geometry snapshot used by the pure state machine. */
fun GestureDependencies.toGeometry(currentBrightness: Float = -1f): GestureGeometry =
    GestureGeometry(
        screenWidth = screenWidth,
        density = density,
        statusBarHeight = statusBarHeight,
        minBacklight = minimumBacklight,
        maxBacklight = maximumBacklight,
        currentBrightness = currentBrightness,
    )
