package tv.withaibuild.customiuizer.mods.island

import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandRecorderEnvelopeRecoveryTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val frames = mutableListOf<FloatArray?>()
    private var raw = 32767
    private var reads = 0
    private val producer = IslandRecorderMeter.producer(1000) { reads++; raw }
    private val consumer = IslandRecorderMeter.Consumer(scope, dispatcher) { frames += it?.copyOf() }

    @Before fun setup() { ShadowBinder.setCallingUid(1000) }
    @After fun close() { consumer.stop(); scope.cancel(); ShadowBinder.reset() }

    private fun samples(count: Int) {
        repeat(count) {
            val interval = IslandRecorderMeter.SAMPLE_INTERVAL_MS
            ShadowSystemClock.advanceBy(Duration.ofMillis(interval))
            scheduler.advanceTimeBy(interval)
            scheduler.runCurrent()
        }
    }

    private fun fullVoice() {
        consumer.start(producer)
        scheduler.runCurrent()
        samples(4)
        assertArrayEquals(FloatArray(5) { 1f }, frames.last(), 0f)
    }

    @Test fun weakTailBelowTheDisplayDeltaStillFlushesToExactSilenceThenStopsOutputWork() {
        fullVoice()
        raw = 420 // valid weak signal: (420 - 256) / 32511, between .004 and .006
        samples(5)
        val weak = IslandRecorderMeter.level(raw)
        assertTrue(weak in .004f.. .006f)
        assertArrayEquals(FloatArray(5) { weak }, frames.last(), 0f)
        val beforeSilence = frames.size
        raw = 0
        samples(5)
        assertArrayEquals("the zero edge must bypass only the delta filter", FloatArray(5), frames.last(), 0f)
        assertEquals("retain the native five-sample decay", 5, frames.size - beforeSilence)
        val settled = frames.size
        val sampled = reads
        samples(100)
        println("recorder silence: flush frames=${frames.size - beforeSilence}, stable extra frames=${frames.size - settled}, ongoing source reads=${reads - sampled}")
        assertEquals(100, reads - sampled)
        assertEquals(settled, frames.size)
    }

    @Test fun sameOwnerAtTheSamePeakCanResumeAfterAnUnavailableRead() {
        fullVoice()
        raw = -1
        samples(1)
        assertArrayEquals(FloatArray(5), frames.last(), 0f)
        val unavailableAt = reads
        samples(20)
        assertEquals("failure does not start a retry poller", unavailableAt, reads)
        raw = 32767
        consumer.start(producer)
        scheduler.runCurrent()
        assertEquals(unavailableAt + 1, reads)
        assertTrue("a stable original level must replace the cleared failure frame", frames.last()!!.any { it > 0f })
        samples(4)
        assertArrayEquals(FloatArray(5) { 1f }, frames.last(), 0f)
    }

    @Test fun ordinaryOcclusionStillRetainsTheEnvelopeAndResumesWithoutRepeatingIt() {
        fullVoice()
        val previous = frames.last()!!.copyOf()
        val visibleFrames = frames.size
        val visibleReads = reads
        repeat(100) { consumer.pauseKeepingFrame() }
        samples(30)
        assertEquals(visibleReads, reads)
        assertEquals(visibleFrames, frames.size)
        consumer.start(producer)
        consumer.start(producer)
        scheduler.runCurrent()
        assertEquals(visibleReads + 1, reads)
        assertEquals(visibleFrames, frames.size)
        assertArrayEquals(previous, frames.last(), 0f)
    }

    @Test fun explicitStopAfterFailureStillClearsOnceAndLeavesNoSampling() {
        fullVoice()
        raw = -1
        samples(1)
        assertArrayEquals(FloatArray(5), frames.last(), 0f)
        val count = frames.size
        val stoppedAt = reads
        consumer.stop()
        consumer.stop()
        assertEquals(count + 1, frames.size)
        assertNull(frames.last())
        samples(100)
        assertEquals(stoppedAt, reads)
        assertEquals(count + 1, frames.size)
    }
}
