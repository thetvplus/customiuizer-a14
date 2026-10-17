package tv.withaibuild.customiuizer.mods.utils.gesture

import android.content.Context
import android.media.AudioManager
import android.view.View
import android.util.Log
import tv.withaibuild.customiuizer.utils.HookUtils
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Launcher for the action chosen at gesture DOWN time.
 *
 * This keeps [StatusBarGestureEffectExecutor] testable on the JVM: the production
 * launcher is injected in the constructor, while tests can inject a recorder.
 */
fun interface GestureActionLauncher {
    fun launch(context: Any?, key: String, action: Int, skipLock: Boolean): Boolean
}

/**
 * Executes the side-effect commands for status bar gestures.
 *
 * This class is deliberately stateless between events; all per-gesture state is held
 * by [GestureMachine].  It only performs the actual Android calls.
 */
private const val TAG = "StatusBarGestureEffectExecutor"

class StatusBarGestureEffectExecutor(
    private val actionLauncher: GestureActionLauncher = DefaultGestureActionLauncher(),
) : GestureEffectExecutor {

    /**
     * Volume flags mirror the legacy GlobalActions implementation:
     * FLAG_FROM_KEY, SHOW_UI, ALLOW_RINGER_MODES, PLAY_SOUND and VIBRATE.
     */
    internal companion object {
        const val VOLUME_FLAGS = (1 shl 12) or
            AudioManager.FLAG_SHOW_UI or
            AudioManager.FLAG_ALLOW_RINGER_MODES or
            AudioManager.FLAG_PLAY_SOUND or
            AudioManager.FLAG_VIBRATE
    }

    private var lastSentBrightnessRatio = -1f

    override fun execute(
        commands: List<GestureCommand>,
        dependencies: GestureDependencies,
        config: GestureConfig,
        context: Any?,
    ) {
        val ctx = when (context) {
            is View -> context.context
            is Context -> context
            else -> null
        }

        val audioManager = dependencies.audioManager as? AudioManager

        for (command in commands) {
            when (command) {
                is GestureCommand.ApplyTemporaryBrightness -> {
                    if (kotlin.math.abs(command.ratio - lastSentBrightnessRatio) >= 0.0001f) {
                        invokePrepared(
                            dependencies.setTemporaryBrightnessMethod,
                            dependencies.displayManager,
                            dependencies.displayId,
                            command.ratio,
                        )
                        lastSentBrightnessRatio = command.ratio
                    }
                }
                is GestureCommand.CommitBrightness -> {
                    invokePrepared(
                        dependencies.setBrightnessMethod,
                        dependencies.displayManager,
                        dependencies.displayId,
                        command.ratio,
                    )
                    lastSentBrightnessRatio = -1f
                }
                is GestureCommand.AdjustVolume -> {
                    if (audioManager != null) {
                        val direction = if (command.raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                        @Suppress("WrongConstant")
                        audioManager.adjustVolume(direction, VOLUME_FLAGS)
                    }
                }
                is GestureCommand.TriggerDoubleTap -> {
                    val actionId = command.actionId
                    if (actionId > 1) {
                        val key = when (command.position) {
                            DoubleTapPosition.LEFT -> "system_statusbarcontrols_dt_left"
                            DoubleTapPosition.CENTER -> "system_statusbarcontrols_dt"
                            DoubleTapPosition.RIGHT -> "system_statusbarcontrols_dt_right"
                        }
                        actionLauncher.launch(context, key, actionId, false)
                    }
                }
                is GestureCommand.TriggerLongPress -> {
                    if (ctx != null && config.longPressVibrate) {
                        HookUtils.performStrongVibration(ctx, config.ignoreVibrateOff)
                    }
                    val actionId = command.actionId
                    if (actionId > 1) {
                        actionLauncher.launch(context, "system_statusbarcontrols_longpress", actionId, false)
                    }
                }
                GestureCommand.Reset,
                GestureCommand.BeginTracking -> { lastSentBrightnessRatio = -1f }
                else -> { /* PassThrough has no side effect here */ }
            }
        }
    }

    /**
     * Calls a pre-resolved [Method] and re-throws any fatal error, including fatal errors
     * wrapped in [InvocationTargetException]. Non-fatal reflection failures are logged and
     * treated as a no-op so the gesture session is not torn down by a ROM method that
     * throws a recoverable error.
     */
    private fun invokePrepared(method: Method?, receiver: Any?, vararg args: Any?): Any? {
        if (method == null) return null
        return try {
            method.invoke(receiver, *args)
        } catch (err: Throwable) {
            when (err) {
                is OutOfMemoryError, is ThreadDeath, is VirtualMachineError -> throw err
                is InvocationTargetException -> when (val cause = err.targetException) {
                    is OutOfMemoryError, is ThreadDeath, is VirtualMachineError -> throw cause
                    else -> {
                        Log.e(TAG, "invokePrepared failed", err)
                        null
                    }
                }
                else -> {
                    Log.e(TAG, "invokePrepared failed", err)
                    null
                }
            }
        }
    }
}
