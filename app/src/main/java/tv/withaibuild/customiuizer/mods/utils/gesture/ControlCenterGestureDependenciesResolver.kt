package tv.withaibuild.customiuizer.mods.utils.gesture

import android.content.Context
import android.media.AudioManager
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method

/**
 * Resolves the runtime dependencies for the Control Center gesture owner.
 *
 * The brightness controller is read from the `controlCenterController` field of the
 * view itself, so this resolver does not need an external ClassLoader.
 */
class ControlCenterGestureDependenciesResolver : GestureDependenciesResolver {

    override fun prepare(
        ownerId: Int,
        classLoaderIdentity: String,
        context: Any,
    ): GestureDependenciesResult {
        val thisObject = context as? View ?: return GestureDependenciesResult.NotReady

        return try {
            val controlCenterController = XposedHelpers.getObjectField(thisObject, "controlCenterController")
                ?: return GestureDependenciesResult.NotReady
            val brightnessController = XposedHelpers.callMethod(
                XposedHelpers.getObjectField(controlCenterController, "brightnessController"),
                "get",
            )
            val displayManager = XposedHelpers.getObjectField(brightnessController, "mDisplayManager")
            val displayId = XposedHelpers.getIntField(brightnessController, "mDisplayId")
            val minimum = XposedHelpers.getObjectField(brightnessController, "mMinimumBacklight") as Float
            val maximum = XposedHelpers.getObjectField(brightnessController, "mMaximumBacklight") as Float

            val ctx = thisObject.context
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val res = ctx.resources
            val statusBarHeight = res.getDimensionPixelSize(
                res.getIdentifier("status_bar_height_default", "dimen", "android"),
            )
            val metrics = res.displayMetrics
            val getBrightness = displayManager.javaClass.getMethod(
                "getBrightness",
                Int::class.java,
            )
            val setTemporaryBrightness = displayManager.javaClass.getMethod(
                "setTemporaryBrightness",
                Int::class.java,
                Float::class.java,
            )
            val setBrightness = displayManager.javaClass.getMethod(
                "setBrightness",
                Int::class.java,
                Float::class.java,
            )

            GestureDependenciesResult.Ready(
                GestureDependencies(
                    ownerId = ownerId,
                    classLoaderIdentity = classLoaderIdentity,
                    displayManager = displayManager,
                    displayId = displayId,
                    minimumBacklight = minimum,
                    maximumBacklight = maximum,
                    audioManager = audioManager,
                    statusBarHeight = statusBarHeight,
                    screenWidth = metrics.widthPixels,
                    density = metrics.density,
                    getBrightnessMethod = getBrightness,
                    setTemporaryBrightnessMethod = setTemporaryBrightness,
                    setBrightnessMethod = setBrightness,
                ),
            )
        } catch (err: Throwable) {
            when (err) {
                is OutOfMemoryError, is ThreadDeath, is VirtualMachineError -> throw err
                else -> GestureDependenciesResult.FailedTransient(err.message ?: "unknown")
            }
        }
    }
}
