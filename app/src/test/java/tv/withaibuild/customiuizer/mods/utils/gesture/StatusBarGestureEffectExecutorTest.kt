package tv.withaibuild.customiuizer.mods.utils.gesture

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StatusBarGestureEffectExecutorTest {

    private val expectedFlags = (1 shl 12) or
        AudioManager.FLAG_SHOW_UI or
        AudioManager.FLAG_ALLOW_RINGER_MODES or
        AudioManager.FLAG_PLAY_SOUND or
        AudioManager.FLAG_VIBRATE

    @Test
    fun legacyVolumeFlagsPreserved() {
        assertEquals(expectedFlags, StatusBarGestureEffectExecutor.VOLUME_FLAGS)
    }

    @Test(expected = OutOfMemoryError::class)
    fun invocationTargetWrappingOomPropagatesCauseOnTemporaryBrightness() {
        val executor = StatusBarGestureEffectExecutor()
        val deps = throwingDependencies(OutOfMemoryError("simulated setTemporaryBrightness OOM"))
        executor.execute(
            listOf(GestureCommand.ApplyTemporaryBrightness(0.5f)),
            deps,
            GestureConfig(),
            Any(),
        )
    }

    @Test(expected = OutOfMemoryError::class)
    fun invocationTargetWrappingOomPropagatesCauseOnBrightnessCommit() {
        val executor = StatusBarGestureEffectExecutor()
        val deps = throwingDependencies(OutOfMemoryError("simulated setBrightness OOM"))
        executor.execute(
            listOf(GestureCommand.CommitBrightness(0.5f)),
            deps,
            GestureConfig(),
            Any(),
        )
    }

    @Test(expected = ThreadDeath::class)
    fun invocationTargetWrappingThreadDeathPropagatesCause() {
        val executor = StatusBarGestureEffectExecutor()
        val deps = throwingDependencies(ThreadDeath())
        executor.execute(
            listOf(GestureCommand.ApplyTemporaryBrightness(0.5f)),
            deps,
            GestureConfig(),
            Any(),
        )
    }

    @Test(expected = InternalError::class)
    fun invocationTargetWrappingVirtualMachineErrorPropagatesCause() {
        val executor = StatusBarGestureEffectExecutor()
        val deps = throwingDependencies(InternalError("simulated VM error"))
        executor.execute(
            listOf(GestureCommand.ApplyTemporaryBrightness(0.5f)),
            deps,
            GestureConfig(),
            Any(),
        )
    }

    @Test
    fun disabledResolvedActionDoesNothing() {
        val launcher = RecordingActionLauncher()
        val executor = StatusBarGestureEffectExecutor(actionLauncher = launcher)

        executor.execute(
            listOf(
                GestureCommand.TriggerDoubleTap(DoubleTapPosition.CENTER, 1),
                GestureCommand.TriggerLongPress(1),
            ),
            noOpDependencies(),
            GestureConfig(doubleTapAction = 1, longPressAction = 1),
            Any(),
        )

        assertEquals(0, launcher.calls.size)
    }

    @Test
    fun doubleTapLaunchesWithPositionKeyAndAction() {
        val launcher = RecordingActionLauncher()
        val executor = StatusBarGestureEffectExecutor(actionLauncher = launcher)

        executor.execute(
            listOf(GestureCommand.TriggerDoubleTap(DoubleTapPosition.RIGHT, 42)),
            noOpDependencies(),
            GestureConfig(),
            Any(),
        )

        assertEquals(1, launcher.calls.size)
        val call = launcher.calls[0]
        assertEquals("system_statusbarcontrols_dt_right", call.key)
        assertEquals(42, call.action)
        assertFalse(call.skipLock)
    }

    @Test
    fun longPressLaunchesWithKeyAndAction() {
        val launcher = RecordingActionLauncher()
        val executor = StatusBarGestureEffectExecutor(actionLauncher = launcher)

        executor.execute(
            listOf(GestureCommand.TriggerLongPress(24)),
            noOpDependencies(),
            GestureConfig(),
            Any(),
        )

        assertEquals(1, launcher.calls.size)
        val call = launcher.calls[0]
        assertEquals("system_statusbarcontrols_longpress", call.key)
        assertEquals(24, call.action)
        assertFalse(call.skipLock)
    }

    private class RecordingActionLauncher : GestureActionLauncher {
        data class Call(
            val context: Any?,
            val key: String,
            val action: Int,
            val skipLock: Boolean,
        )

        val calls = mutableListOf<Call>()

        override fun launch(context: Any?, key: String, action: Int, skipLock: Boolean): Boolean {
            calls.add(Call(context, key, action, skipLock))
            return true
        }
    }

    private class ThrowingDisplay(stub: Throwable) {
        val error = stub
        fun setTemporaryBrightness(displayId: Int, value: Float) { throw error }
        fun setBrightness(displayId: Int, value: Float) { throw error }
        fun getBrightness(displayId: Int): Float = 0.5f
    }

    private fun throwingDependencies(error: Throwable): GestureDependencies {
        val display = ThrowingDisplay(error)
        return GestureDependencies(
            ownerId = 1,
            classLoaderIdentity = "test",
            displayManager = display,
            displayId = 0,
            minimumBacklight = 0.0f,
            maximumBacklight = 1.0f,
            audioManager = null,
            statusBarHeight = 80,
            screenWidth = 1080,
            density = 3.0f,
            setTemporaryBrightnessMethod = ThrowingDisplay::class.java
                .getMethod("setTemporaryBrightness", Int::class.java, Float::class.java),
            setBrightnessMethod = ThrowingDisplay::class.java
                .getMethod("setBrightness", Int::class.java, Float::class.java),
            getBrightnessMethod = ThrowingDisplay::class.java
                .getMethod("getBrightness", Int::class.java),
        )
    }

    private fun noOpDependencies(): GestureDependencies {
        val stub = BrightnessDisplayStub()
        return GestureDependencies(
            ownerId = 1,
            classLoaderIdentity = "test",
            displayManager = stub,
            displayId = 0,
            minimumBacklight = 0.0f,
            maximumBacklight = 1.0f,
            audioManager = Any(),
            statusBarHeight = 80,
            screenWidth = 1080,
            density = 3.0f,
        )
    }
}
