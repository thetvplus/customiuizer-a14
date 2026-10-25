package tv.withaibuild.customiuizer.mods.island

import android.os.Binder
import android.os.SystemClock
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandRecorderMeterTest {
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
            scheduler.advanceTimeBy(335); scheduler.runCurrent()
            assertTrue(reads in 5..6)
            assertArrayEquals(FloatArray(5) { 1f }, frames.last(), 0f)
            amplitude = 0
            scheduler.advanceTimeBy(402); scheduler.runCurrent()
            assertArrayEquals(FloatArray(5), frames.last(), 0f)
            val frameCount = frames.size
            scheduler.advanceTimeBy(402); scheduler.runCurrent()
            assertEquals("Stable silence must not invalidate the waveform", frameCount, frames.size)
            consumer.stop(); consumer.stop()
            val stoppedAt = reads
            scheduler.advanceTimeBy(3000); scheduler.runCurrent()
            assertEquals(stoppedAt, reads)
            assertNull(frames.last())
            amplitude = -1
            consumer.start(producer); scheduler.runCurrent()
            val unavailableAt = reads
            scheduler.advanceTimeBy(1000); scheduler.runCurrent()
            assertEquals(unavailableAt, reads)
            amplitude = 8192
            consumer.start(producer); scheduler.runCurrent()
            assertEquals(unavailableAt + 1, reads)
            consumer.stop()
        } finally { consumer.stop(); scope.cancel(); ShadowBinder.reset() }
    }
}
