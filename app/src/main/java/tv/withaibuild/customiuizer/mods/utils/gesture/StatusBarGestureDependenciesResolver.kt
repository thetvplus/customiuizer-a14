package tv.withaibuild.customiuizer.mods.utils.gesture

import android.content.Context
import android.media.AudioManager
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method

/**
 * Resolves the runtime dependencies for the status bar (PhoneStatusBarView) gesture owner.
 *
 * This still performs reflection at call time; the long-term goal is to move this work
 * to a safe lifecycle hook and let [GestureMachine] use the prepared snapshot only.
 */
class StatusBarGestureDependenciesResolver(
    private val classLoader: ClassLoader,
) : GestureDependenciesResolver {

    override fun prepare(
        ownerId: Int,
        classLoaderIdentity: String,
        context: Any,
    ): GestureDependenciesResult {
        val ctx = when (context) {
            is View -> context.context
            is Context -> context
            else -> return GestureDependenciesResult.NotReady
        }

        return try {
            val controller = ModuleHelper.getDepInstance(
                classLoader,
                "com.android.systemui.controlcenter.policy.ControlCenterControllerImpl",
            )
            val brightnessControllerRef = XposedHelpers.getObjectField(controller, "brightnessController")
            val brightnessController = XposedHelpers.callMethod(brightnessControllerRef, "get")
            val displayManager = XposedHelpers.getObjectField(brightnessController, "mDisplayManager")
            val displayId = XposedHelpers.getIntField(brightnessController, "mDisplayId")
            val minimum = XposedHelpers.getObjectField(brightnessController, "mMinimumBacklight") as Float
            val maximum = XposedHelpers.getObjectField(brightnessController, "mMaximumBacklight") as Float
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
