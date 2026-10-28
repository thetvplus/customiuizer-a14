package tv.withaibuild.customiuizer.mods.island

import android.media.AudioAttributes
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandAudioSpectrumTest {
    @Test fun multipleTracksKeepTheCurrentSelectionAcrossOrderChangesAndChooseAgainOnlyWhenItStops() {
        fun track(session: Int, uid: Int = 10001) = IslandAudioSpectrum.Track(uid, session, true, AudioAttributes.USAGE_MEDIA)
        assertEquals(42, IslandAudioSpectrum.sessionFor(10001, listOf(track(99), track(42))))
        assertEquals(99, IslandAudioSpectrum.sessionFor(10001, listOf(track(42), track(99)), retained = 99))
        assertEquals(99, IslandAudioSpectrum.sessionFor(10001, listOf(track(1, 20001), track(42), track(99)), retained = 99))
        assertEquals(42, IslandAudioSpectrum.sessionFor(10001, listOf(track(42)), retained = 99))
        assertNull(IslandAudioSpectrum.sessionFor(10001, listOf(track(99, 20001)), retained = 99))
    }

    @Test fun changingTheAudioTrackKeepsTheLastMeasuredFrameUntilTheReplacementIsReady() {
        start()
        backend.listener!!(ByteArray(512) { 100 }, 48_000_000); drain()
        val previous = frames.last()!!.copyOf()
        backend.session = 99; backend.changed!!(); drain()
        assertArrayEquals(previous, frames.last()!!, 0f)
        assertEquals(listOf(42), backend.closed)
        backend.listener!!(ByteArray(512) { 50 }, 48_000_000); drain()
        assertNotNull(frames.last())
    }

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = TestScope(dispatcher)
    private val backend = FakeBackend()
    private val frames = mutableListOf<FloatArray?>()
    private lateinit var source: IslandAudioSpectrum
    private fun player(tag: String) = MediaController(RuntimeEnvironment.getApplication(),
        MediaSession(RuntimeEnvironment.getApplication(), tag).sessionToken)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        source = IslandAudioSpectrum(scope, dispatcher, Handler(Looper.getMainLooper()), backend,
            IslandDiagnostics()) { frames.add(it?.copyOf()) }
    }
    @After fun cleanup() { source.stop(); drain(); Dispatchers.resetMain() }
    private fun drain() { scheduler.runCurrent(); shadowOf(Looper.getMainLooper()).idle() }
    private fun start(tag: String = "one") { source.start(player(tag), Process.myUserHandle()); drain() }

    @Test fun permissionFailureCreatesNoSubscriptionOrCapture() {
        backend.permission = false
        start()
        assertEquals(0, backend.subscriptions)
        assertEquals(0, backend.opened.size)
        assertTrue(frames.all { it == null })
    }

    @Test fun realFftProducesFiveBandsAndStopImmediatelyInvalidatesOldCallbacks() {
        start()
        val listener = backend.listener!!
        listener(ByteArray(512) { 100 }, 48_000_000); drain()
        assertTrue(frames.last()!!.all { it.isFinite() && it in 0f..1f })
        val count = frames.size
        source.stop()
        listener(ByteArray(512) { 100 }, 48_000_000); drain()
        assertEquals(count + 1, frames.size)
        assertNull(frames.last())
        assertEquals(listOf(42), backend.closed)
        assertEquals(0, backend.subscriptions)
    }

    @Test fun switchingAudioOrMediaSessionsReleasesOldNativeObjectsAndObservers() {
        start()
        val stale = backend.listener!!
        backend.session = 99; backend.changed!!(); drain()
        assertEquals(listOf(42), backend.closed)
        assertEquals(listOf(42, 99), backend.opened)
        val beforeOldRoute = frames.size
        stale(ByteArray(512) { 100 }, 48_000_000); drain()
        assertEquals(beforeOldRoute, frames.size)
        source.start(player("two"), Process.myUserHandle()); drain()
        assertEquals(listOf(42, 99), backend.closed)
        assertEquals(1, backend.subscriptions)
        val count = frames.size
        stale(ByteArray(512) { 100 }, 48_000_000); drain()
        assertEquals(count, frames.size)
    }

    @Test fun aQuietFirstFrameAfterTrackReplacementClearsThePreviousSongsLevels() {
        start()
        backend.listener!!(ByteArray(512) { 100 }, 48_000_000); drain()
        assertTrue(frames.last()!!.any { it > .1f })
        val old = backend.listener!!
        backend.session = 99; backend.changed!!(); drain()
        backend.listener!!(ByteArray(1024), 192_000_000); drain()
        assertArrayEquals(FloatArray(5), frames.last(), 0f)
        val count = frames.size
        old(ByteArray(512) { 127 }, 48_000_000); drain()
        assertEquals(count, frames.size)
        assertEquals(listOf(42), backend.closed)
    }

    @Test fun callbackSampleRateChangesUseCurrentFrequencyMappingWithoutRestartingCapture() {
        start()
        for (rate in listOf(44_100_000, 48_000_000, 96_000_000, 192_000_000)) {
            val fft = ByteArray(1024)
            val bin = kotlin.math.round(1000f * fft.size / (rate / 1000f)).toInt()
            fft[bin * 2] = 64
            backend.listener!!(fft, rate); drain()
            assertTrue("rate=$rate", frames.last()!![2] > .1f)
            for (index in listOf(0, 1, 3, 4)) assertEquals(0f, frames.last()!![index], 0f)
        }
        assertEquals(listOf(42), backend.opened)
        assertEquals(1, backend.subscriptions)
    }

    @Test fun invalidDataOrAbsentCallbacksReleaseAndFallbackWithoutRetryPolling() {
        start()
        backend.listener!!(ByteArray(7), 0); drain()
        assertEquals(listOf(42), backend.closed)
        backend.changed!!(); drain()
        assertEquals(1, backend.opened.size)
        source.stop(); drain(); start("fresh")
        scheduler.advanceTimeBy(1001); drain()
        assertEquals(2, backend.closed.size)
        assertTrue(frames.all { it == null })
    }

    @Test fun anonymizedForeignAndInactiveAudioCannotSelectTheOutputMix() {
        fun track(uid: Int = 10001, session: Int = 42, active: Boolean = true,
            usage: Int = AudioAttributes.USAGE_MEDIA) = IslandAudioSpectrum.Track(uid, session, active, usage)
        assertEquals(42, IslandAudioSpectrum.sessionFor(10001, listOf(track())))
        assertNull(IslandAudioSpectrum.sessionFor(10001, listOf(track(uid = -1))))
        assertNull(IslandAudioSpectrum.sessionFor(10001, listOf(track(session = 0))))
        assertEquals(42, IslandAudioSpectrum.sessionFor(10001, listOf(track(), track(session = 99))))
        assertNull(IslandAudioSpectrum.sessionFor(10001, listOf(track(active = false))))
        assertNull(IslandAudioSpectrum.sessionFor(10001, listOf(track(usage = AudioAttributes.USAGE_VOICE_COMMUNICATION))))
        assertNull(IslandAudioSpectrum.sessionFor(10001, listOf(track(uid = 1010001))))
    }

    private class FakeBackend : IslandAudioSpectrum.Backend {
        var permission = true
        var session: Int? = 42
        var subscriptions = 0
        var changed: (() -> Unit)? = null
        var listener: ((ByteArray, Int) -> Unit)? = null
        val opened = mutableListOf<Int>()
        val closed = mutableListOf<Int>()
        override fun permitted() = permission
        override fun subscribe(changed: () -> Unit): AutoCloseable {
            this.changed = changed; subscriptions++
            return AutoCloseable { subscriptions--; this.changed = null }
        }
        override fun session(player: MediaController, user: android.os.UserHandle?) = session
        override fun capture(session: Int, fft: (ByteArray, Int) -> Unit): AutoCloseable {
            opened.add(session); listener = fft
            return AutoCloseable { closed.add(session) }
        }
    }
}
