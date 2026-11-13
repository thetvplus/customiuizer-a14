package tv.withaibuild.customiuizer.mods.island

import android.os.Binder
import android.os.SystemClock
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import org.robolectric.shadows.ShadowSystemClock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandRecorderMeterTest {
    @Test fun occlusionStopsBinderReadsAndResumesTheSameEnvelopeWithoutClearingIt() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var reads = 0
        val frames = mutableListOf<FloatArray?>()
        val consumer = IslandRecorderMeter.Consumer(scope, dispatcher) { frames.add(it?.copyOf()) }
        try {
            ShadowBinder.setCallingUid(1000)
            val producer = IslandRecorderMeter.producer(1000) { reads++; 32767 }
            consumer.start(producer); scheduler.runCurrent()
            scheduler.advanceTimeBy(5 * IslandRecorderMeter.SAMPLE_INTERVAL_MS); scheduler.runCurrent()
            val previous = frames.last()!!.copyOf()
            val count = frames.size; val sampled = reads
            repeat(120) { consumer.pauseKeepingFrame() }
            scheduler.advanceTimeBy(3000); scheduler.runCurrent()
            assertEquals(sampled, reads); assertEquals(count, frames.size)
            consumer.start(producer); consumer.start(producer); scheduler.runCurrent()
            assertEquals(sampled + 1, reads)
            assertArrayEquals(previous, frames.last(), 0f)
            assertEquals(count, frames.size)
            val quietSuccessor = IslandRecorderMeter.producer(1000) { 0 }
            consumer.start(quietSuccessor); scheduler.runCurrent()
            assertArrayEquals("A quiet successor cannot inherit another recorder's levels", FloatArray(5), frames.last(), 0f)
            consumer.stop(); assertNull(frames.last())
        } finally { consumer.stop(); scope.cancel(); ShadowBinder.reset() }
    }
    @Test fun sustainedVisibleRecordingBoundsIpcAndRecoversFromSilenceWithoutASecondLoop() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var reads = 0; var amplitude = 0
        val frames = mutableListOf<FloatArray?>()
        val consumer = IslandRecorderMeter.Consumer(scope, dispatcher) { frames.add(it?.copyOf()) }
        try {
            ShadowBinder.setCallingUid(1000)
            val producer = IslandRecorderMeter.producer(1000) { reads++; amplitude }
            consumer.start(producer); scheduler.runCurrent()
            repeat(100) { consumer.start(producer) }
            scheduler.advanceTimeBy(10_000); scheduler.runCurrent()
            assertTrue("Visible sampling must stay at or below ten reads per second", reads in 100..101)
            val quietFrames = frames.size
            scheduler.advanceTimeBy(1000); scheduler.runCurrent()
            assertEquals("Sampling silence must not request display frames", quietFrames, frames.size)
            amplitude = 16384
            scheduler.advanceTimeBy(100); scheduler.runCurrent()
            assertTrue("Speech must resume within one source interval", frames.last()!!.last() > .49f)
            consumer.pauseKeepingFrame()
            val pausedReads = reads
            scheduler.advanceTimeBy(10_000); scheduler.runCurrent()
            assertEquals(pausedReads, reads)
        } finally { consumer.stop(); scope.cancel(); ShadowBinder.reset() }
    }

    @Test fun unavailableRecorderCannotTurnPreDrawIntoBinderSampling() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var reads = 0
        var successorReads = 0
        var amplitude = -1
        val frames = mutableListOf<FloatArray?>()
        val consumer = IslandRecorderMeter.Consumer(scope, dispatcher) { frames.add(it?.copyOf()) }
        try {
            ShadowBinder.setCallingUid(1000)
            val producer = IslandRecorderMeter.producer(1000) { reads++; amplitude }
            consumer.start(producer); scheduler.runCurrent()
            val quietFrames = frames.size
            repeat(11) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(8))
                consumer.start(producer); scheduler.runCurrent()
            }
            assertEquals("Re-presenting a failed source must not retry before 100 ms", 1, reads)
            assertEquals(quietFrames, frames.size)

            amplitude = 16384
            ShadowSystemClock.advanceBy(Duration.ofMillis(12))
            consumer.start(producer); scheduler.runCurrent()
            assertEquals(2, reads)
            assertTrue(frames.last()!!.last() > .49f)

            amplitude = -1
            ShadowSystemClock.advanceBy(Duration.ofMillis(100))
            scheduler.advanceTimeBy(100); scheduler.runCurrent()
            assertEquals(3, reads)
            val successor = IslandRecorderMeter.producer(1000) { successorReads++; 8192 }
            consumer.start(successor); scheduler.runCurrent()
            assertEquals("A new source must start immediately", 1, successorReads)
            assertTrue(frames.last()!!.last() > .24f)
        } finally { consumer.stop(); scope.cancel(); ShadowBinder.reset() }
    }

    @Test fun peakMappingKeepsSilenceQuietAndLeavesHeadroomForOrdinarySpeechAndClipping() {
        assertEquals(0f, IslandRecorderMeter.level(256), 0f)
        assertTrue(IslandRecorderMeter.level(8192) in .24f.. .25f)
        assertTrue(IslandRecorderMeter.level(16384) in .49f.. .50f)
        assertTrue("Quiet speech must survive the display gate", IslandRecorderMeter.level(1024) > .02f)
        assertEquals(1f, IslandRecorderMeter.level(32767), .00001f)
        var previous = 0f
        for (raw in 0..32768 step 64) {
            val value = IslandRecorderMeter.level(raw)
            assertTrue(value in previous..1f)
            previous = value
        }
    }
    @Test fun scalarCapabilityChecksTheCallerBeforeReadingAndRejectsInactiveOrForeignPayloads() {
        var reads = 0
        val meter = IslandRecorderMeter.producer(1000) { reads++; 12345 }
        try {
            ShadowBinder.setCallingUid(10300)
            assertThrows(SecurityException::class.java) { IslandRecorderMeter.read(meter) }
            assertEquals(0, reads)
            ShadowBinder.setCallingUid(1000)
            assertEquals(12345, IslandRecorderMeter.read(meter))
            assertEquals(1, reads)
            assertNull(IslandRecorderMeter.read(IslandRecorderMeter.producer(1000) { -1 }))
            val activity = IslandLiveActivity(IslandLiveKind.RECORDER, IslandLiveKind.RECORDER_PACKAGE, 10300,
                session = 1, meter = meter)
            val encoded = IslandLiveSignal.encode(activity.packageName, activity.kind, activity, 2)
            assertSame(meter, IslandLiveSignal.decode(encoded, activity.packageName, activity.uid,
                SystemClock.uptimeMillis(), SystemClock.elapsedRealtime())!!.activity!!.meter)
            val bad = IslandLiveSignal.encode(IslandLiveKind.CLOCK_PACKAGE, IslandLiveKind.TIMER,
                IslandLiveActivity(IslandLiveKind.TIMER, IslandLiveKind.CLOCK_PACKAGE, 10300, session = 1), 3)
                .putExtras(android.os.Bundle().apply { putBinder("meter", Binder()) })
            assertNull(IslandLiveSignal.decode(bad, IslandLiveKind.CLOCK_PACKAGE, 10300,
                SystemClock.uptimeMillis(), SystemClock.elapsedRealtime()))
        } finally { ShadowBinder.reset() }
    }

    @Test fun oneVisibleConsumerSmoothsActualLevelsThenFlushesSilenceAndStopsAllSampling() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val frames = mutableListOf<FloatArray?>()
        var reads = 0; var amplitude = 32767
        val consumer = IslandRecorderMeter.Consumer(scope, dispatcher) { frames.add(it?.copyOf()) }
        try {
            ShadowBinder.setCallingUid(1000)
            val producer = IslandRecorderMeter.producer(1000) { reads++; amplitude }
            consumer.start(producer); consumer.start(producer)
            scheduler.runCurrent()
            scheduler.advanceTimeBy(5 * IslandRecorderMeter.SAMPLE_INTERVAL_MS); scheduler.runCurrent()
            assertTrue(reads in 5..6)
            assertArrayEquals(FloatArray(5) { 1f }, frames.last(), .00001f)
            amplitude = 0
            scheduler.advanceTimeBy(6 * IslandRecorderMeter.SAMPLE_INTERVAL_MS); scheduler.runCurrent()
            assertArrayEquals(FloatArray(5), frames.last(), 0f)
            val frameCount = frames.size
            scheduler.advanceTimeBy(6 * IslandRecorderMeter.SAMPLE_INTERVAL_MS); scheduler.runCurrent()
            assertEquals("Stable silence must not invalidate the waveform", frameCount, frames.size)
            consumer.stop(); consumer.stop()
            val stoppedAt = reads
            scheduler.advanceTimeBy(3000); scheduler.runCurrent()
            assertEquals(stoppedAt, reads)
            assertNull(frames.last())
            amplitude = -1
            consumer.start(producer); scheduler.runCurrent()
            val unavailableAt = reads
            ShadowSystemClock.advanceBy(Duration.ofMillis(1000))
            scheduler.advanceTimeBy(1000); scheduler.runCurrent()
            assertEquals(unavailableAt, reads)
            amplitude = 8192
            consumer.start(producer); scheduler.runCurrent()
            assertEquals(unavailableAt + 1, reads)
            consumer.stop()
        } finally { consumer.stop(); scope.cancel(); ShadowBinder.reset() }
    }
}
