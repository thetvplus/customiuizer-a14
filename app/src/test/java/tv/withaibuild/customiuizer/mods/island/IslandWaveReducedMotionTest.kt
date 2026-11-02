package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import java.time.Duration
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import tv.withaibuild.customiuizer.testsupport.advanceDisplayFrames

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class IslandWaveReducedMotionTest {
    @Before fun controlDisplayFrames() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        advanceDisplayFrames(2)
    }

    private val savedScale = ValueAnimator.getDurationScale()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
    private val wave = IslandWaveView(activity.get())

    init {
        activity.get().setContentView(wave, FrameLayout.LayoutParams(25, 24))
        wave.onVisibilityAggregated(true)
        wave.outputVisible = true
        wave.spectrum(FloatArray(5))
        wave.playing = true
    }

    @After fun close() { scale(savedScale); activity.pause().stop().destroy() }

    private fun scale(value: Float) {
        // Robolectric's setter shadow only writes the field and skips native scale listeners.
        Shadow.directlyOn<Unit, ValueAnimator>(ValueAnimator::class.java, "setDurationScale",
            ClassParameter.from(Float::class.javaPrimitiveType!!, value))
    }

    private fun assertCurrentLevels() {
        assertArrayEquals(ReflectionHelpers.getField<FloatArray>(wave, "targets"),
            ReflectionHelpers.getField<FloatArray>(wave, "energies"), 0f)
        assertFalse(ReflectionHelpers.getField(wave, "settling"))
        assertFalse(ReflectionHelpers.getField(wave, "scheduled"))
    }

    @Test fun measuredBarsRiseAndFallFasterWithoutChangingTheirGainOrPeak() {
        scale(1f)
        wave.spectrum(FloatArray(5) { 1f })
        val peak = ReflectionHelpers.getField<FloatArray>(wave, "targets")[0]
        var start = android.os.SystemClock.uptimeMillis()
        advanceDisplayFrames(6)
        var elapsed = (android.os.SystemClock.uptimeMillis() - start) / 1000.0
        val rising = ReflectionHelpers.getField<FloatArray>(wave, "energies")[0]
        val oldAttack = (peak * (1.0 - kotlin.math.exp(-elapsed / .080))).toFloat()
        assertTrue("Rising levels must beat the former 80 ms response", rising > oldAttack + peak * .01f)
        advanceDisplayFrames(90)
        assertEquals(peak, ReflectionHelpers.getField<FloatArray>(wave, "energies")[0], 0f)
        wave.spectrum(FloatArray(5))
        start = android.os.SystemClock.uptimeMillis(); advanceDisplayFrames(20)
        elapsed = (android.os.SystemClock.uptimeMillis() - start) / 1000.0
        val oldRelease = (peak * kotlin.math.exp(-elapsed / .240)).toFloat()
        assertTrue("Falling levels must beat the former 240 ms response",
            ReflectionHelpers.getField<FloatArray>(wave, "energies")[0] < oldRelease - peak * .01f)
        assertEquals(200, wave.gainPercent)
    }

    @Test fun disabledAnimationsStillCommitMeasuredLevelsGainAndSilenceImmediately() {
        scale(0f)
        wave.spectrum(FloatArray(5) { .2f })
        assertCurrentLevels()
        val before = ReflectionHelpers.getField<FloatArray>(wave, "energies")[0]
        wave.gainPercent = 800
        assertCurrentLevels()
        assertTrue(ReflectionHelpers.getField<FloatArray>(wave, "energies")[0] > before)
        wave.spectrum(FloatArray(5))
        assertCurrentLevels()
        assertArrayEquals(FloatArray(5), ReflectionHelpers.getField(wave, "energies"), 0f)
    }

    @Test fun disablingAnimationsDuringInterpolationDrainsTheLatestValueWithoutAnotherClock() {
        scale(1f)
        wave.spectrum(FloatArray(5) { 1f })
        assertTrue(ReflectionHelpers.getField(wave, "scheduled"))
        scale(0f)
        advanceDisplayFrames()
        assertCurrentLevels()
        val current = ReflectionHelpers.getField<FloatArray>(wave, "energies").clone()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertArrayEquals(current, ReflectionHelpers.getField(wave, "energies"), 0f)
    }

    @Test fun hiddenMeasuredUpdatesAndRepeatedSilenceDoNotStartAClock() {
        scale(0f)
        wave.outputVisible = false
        wave.spectrum(FloatArray(5) { 1f })
        assertCurrentLevels()
        wave.outputVisible = true
        assertCurrentLevels()
        repeat(100) { wave.spectrum(FloatArray(5)) }
        assertCurrentLevels()
        assertArrayEquals(FloatArray(5), ReflectionHelpers.getField(wave, "energies"), 0f)
        wave.spectrum(null)
        assertFalse("synthetic motion also stays stopped", ReflectionHelpers.getField(wave, "scheduled"))
    }

    @Test fun enabledAnimationsKeepMeasuredInterpolationAndStopAfterSettling() {
        scale(1f)
        wave.spectrum(FloatArray(5) { 1f })
        assertEquals(0f, ReflectionHelpers.getField<FloatArray>(wave, "energies")[0], 0f)
        advanceDisplayFrames()
        assertTrue(ReflectionHelpers.getField<FloatArray>(wave, "energies")[0] in .1f.. .99f)
        advanceDisplayFrames(90)
        assertFalse(ReflectionHelpers.getField(wave, "scheduled"))
        assertArrayEquals(ReflectionHelpers.getField<FloatArray>(wave, "targets"),
            ReflectionHelpers.getField(wave, "energies"), .006f)
    }

    @Test fun syntheticWaveResumesFromNativeScaleNotificationWithoutRebinding() {
        scale(1f)
        wave.spectrum(null)
        assertTrue(ReflectionHelpers.getField(wave, "durationScaleListening"))
        advanceDisplayFrames(3)
        scale(0f)
        assertFalse(ReflectionHelpers.getField(wave, "scheduled"))
        val stopped = ReflectionHelpers.getField<Float>(wave, "phase")
        advanceDisplayFrames(10)
        assertEquals(stopped, ReflectionHelpers.getField<Float>(wave, "phase"), 0f)
        scale(1f)
        assertTrue(ReflectionHelpers.getField(wave, "scheduled"))
        advanceDisplayFrames(3)
        assertNotEquals(stopped, ReflectionHelpers.getField<Float>(wave, "phase"))
    }

    @Test fun scaleNotificationIsOwnedOnlyByVisiblePlayingSyntheticOutput() {
        scale(0f)
        wave.spectrum(null)
        assertTrue(ReflectionHelpers.getField(wave, "durationScaleListening"))
        wave.outputVisible = false
        assertFalse(ReflectionHelpers.getField(wave, "durationScaleListening"))
        scale(1f)
        assertFalse(ReflectionHelpers.getField(wave, "scheduled"))
        wave.outputVisible = true
        wave.playing = false
        assertFalse(ReflectionHelpers.getField(wave, "durationScaleListening"))
        wave.playing = true
        wave.spectrum(FloatArray(5))
        assertFalse(ReflectionHelpers.getField(wave, "durationScaleListening"))
        wave.spectrum(null)
        activity.get().setContentView(FrameLayout(activity.get()))
        assertFalse(ReflectionHelpers.getField(wave, "durationScaleListening"))
        scale(0f)
        scale(1f)
        assertFalse(ReflectionHelpers.getField(wave, "scheduled"))
    }
}
