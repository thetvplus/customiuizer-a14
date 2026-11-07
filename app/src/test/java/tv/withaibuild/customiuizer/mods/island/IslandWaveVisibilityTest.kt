package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField
import tv.withaibuild.customiuizer.testsupport.advanceDisplayFrames
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandWaveVisibilityTest {
    @Before fun controlDisplayFrames() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @Test fun visibleWaveAdvancesOnEachDisplayFrameAtBothFrameIntervals() {
        val savedDelay = ShadowChoreographer.getFrameDelay()
        val savedPaused = ShadowChoreographer.isPaused()
        ShadowChoreographer.setPaused(true)
        try {
            for (milliseconds in listOf(16L, 8L)) {
                ShadowChoreographer.setFrameDelay(Duration.ofMillis(milliseconds))
                val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
                try {
                    val wave = IslandWaveView(activity.get())
                    activity.get().setContentView(wave, FrameLayout.LayoutParams(25, 24))
                    advanceDisplayFrames(2, milliseconds)
                    wave.onVisibilityAggregated(true)
                    wave.outputVisible = true
                    wave.playing = true
                    repeat(6) {
                        val before = getField<Float>(wave, "phase")
                        advanceDisplayFrames(milliseconds = milliseconds)
                        assertTrue("A visible wave must advance at a $milliseconds ms display cadence",
                            getField<Float>(wave, "phase") > before)
                    }
                    wave.spectrum(FloatArray(5))
                    assertFalse("Stable measured silence must release the frame callback", getField(wave, "scheduled"))
                } finally { activity.pause().stop().destroy() }
            }
        } finally {
            ShadowChoreographer.setFrameDelay(savedDelay)
            ShadowChoreographer.setPaused(savedPaused)
        }
    }

    @Test fun measuredPeaksRiseSmoothlyFallMoreSlowlyAndAlwaysRetireAtSilence() {
        val savedDelay = ShadowChoreographer.getFrameDelay()
        try {
            for (milliseconds in listOf(16L, 8L)) {
                ShadowChoreographer.setFrameDelay(Duration.ofMillis(milliseconds))
                val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
                val wave = IslandWaveView(controller.get())
                try {
                    controller.get().setContentView(wave, FrameLayout.LayoutParams(25, 24))
                    advanceDisplayFrames(2, milliseconds)
                    wave.onVisibilityAggregated(true); wave.outputVisible = true
                    wave.spectrum(FloatArray(5)); wave.playing = true
                    wave.spectrum(FloatArray(5) { 1f })
                    advanceDisplayFrames((96 / milliseconds).toInt(), milliseconds)
                    val peak = getField<FloatArray>(wave, "energies")[0]
                    val target = getField<FloatArray>(wave, "targets")[0]
                    assertTrue("Onset must bridge sparse samples at $milliseconds ms", peak > target * .6f && peak < target * .8f)
                    wave.spectrum(FloatArray(5))
                    advanceDisplayFrames((96 / milliseconds).toInt(), milliseconds)
                    val falling = getField<FloatArray>(wave, "energies")[0]
                    assertTrue("Release must be slower than onset", falling > peak * .55f && falling < peak * .8f)
                    advanceDisplayFrames((1400 / milliseconds).toInt(), milliseconds)
                    assertArrayEquals(FloatArray(5), getField(wave, "energies"), 0f)
                    assertFalse("A longer release is finite, never a silent frame loop", getField(wave, "scheduled"))
                } finally { controller.pause().stop().destroy() }
            }
        } finally { ShadowChoreographer.setFrameDelay(savedDelay) }
    }

    @Test fun hiddenWaveCancelsFramesRetainsLevelsAndResumesOnItsExistingPhaseClock() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val wave = IslandWaveView(activity)
        activity.setContentView(wave, FrameLayout.LayoutParams(25, 24))
        advanceDisplayFrames(2)
        wave.onVisibilityAggregated(true)
        wave.outputVisible = true
        wave.playing = true
        try {
            advanceDisplayFrames(6)
            val phase = getField<Float>(wave, "phase")
            assertTrue(phase > 0f)
            wave.outputVisible = false
            assertFalse(getField(wave, "scheduled"))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertEquals(phase, getField<Float>(wave, "phase"), 0f)
            wave.outputVisible = true
            advanceDisplayFrames()
            assertTrue("Resume uses elapsed time without running hidden frames", getField<Float>(wave, "phase") > phase + 2f)
            wave.spectrum(FloatArray(5) { .4f })
            val levels = getField<FloatArray>(wave, "energies").copyOf()
            wave.outputVisible = false
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertArrayEquals(levels, getField(wave, "energies"), 0f)
            assertFalse(getField(wave, "scheduled"))
            assertTrue(wave.playing)
        } finally { activity.finish() }
    }

    @Test fun presentationChecksAlphaExplicitClipsSpaceAndVisibilityWithoutLosingMediaState() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val parent = FrameLayout(activity)
        val shell = IslandActivityView(activity)
        parent.addView(shell, FrameLayout.LayoutParams(240, 40))
        activity.setContentView(parent)
        advanceDisplayFrames(2)
        shell.bind("Track", null, null, true, true, true, 0, false)
        shell.geometry(240, 40, 40)
        parent.layout(0, 0, 400, 100)
        shell.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        shell.layout(0, 0, 240, 40)
        val attach = getField<Any>(shell, "mAttachInfo")
        org.robolectric.util.ReflectionHelpers.setField(attach, "mWindowVisibility", View.VISIBLE)
        val wave = shell.contentAt(2) as IslandWaveView
        var changes = 0
        shell.onWaveformVisibilityChanged = { changes++ }
        fun refresh() = shell.refreshWaveformVisibility()
        try {
            refresh(); assertTrue(shell.waveformSampling)
            val baseline = changes
            repeat(120) { refresh() }
            assertEquals(baseline, changes)
            wave.alpha = 0f; refresh()
            assertFalse(shell.waveformSampling); assertFalse(getField(wave, "scheduled"))
            assertTrue(shell.isMedia); assertTrue(wave.playing)
            wave.alpha = 1f; refresh(); assertTrue(shell.waveformSampling)
            shell.clipBounds = Rect(0, 0, wave.left, 40); refresh()
            assertFalse("A nonempty parent clip can completely exclude the bars", shell.waveformSampling)
            shell.clipBounds = null; refresh(); assertTrue(shell.waveformSampling)
            parent.alpha = 0f; refresh(); assertFalse(shell.waveformSampling)
            parent.alpha = 1f; refresh(); assertTrue(shell.waveformSampling)
            wave.visibility = View.GONE; refresh(); assertFalse(shell.waveformSampling)
            wave.visibility = View.VISIBLE; refresh(); assertTrue(shell.waveformSampling)
            shell.transition(240, 40, 240, 40, false)
            // Apply the current occlusion after the transition prepares its own clip.
            shell.clipBounds = Rect(0, 0, 0, 0)
            refresh()
            assertTrue("A finite transition may prepare sampling before its lane appears", shell.waveformSampling)
            assertFalse(wave.outputVisible)
            getField<android.animation.ValueAnimator>(shell, "motion").end()
            shell.clipBounds = Rect(0, 0, 0, 0)
            refresh(); assertFalse("Preparation ends with the shell clock", shell.waveformSampling)
            shell.clipBounds = null; refresh()
            val oldWidth = wave.width
            wave.layout(wave.left, wave.top, wave.left, wave.bottom); refresh()
            assertFalse(shell.waveformSampling)
            wave.layout(wave.left, wave.top, wave.left + oldWidth, wave.bottom); refresh()
            assertTrue(shell.waveformSampling)
            parent.removeView(shell)
            assertFalse(wave.outputVisible); assertFalse(getField(wave, "scheduled"))
            parent.addView(shell)
            shell.layout(0, 0, 240, 40); refresh()
            assertTrue(shell.waveformSampling)
        } finally { activity.finish() }
    }
}
