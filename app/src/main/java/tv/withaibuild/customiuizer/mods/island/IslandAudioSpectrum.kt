package tv.withaibuild.customiuizer.mods.island

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.Visualizer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.UserHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.AudioFftBands
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Experimental, selected local-session capture. Uses the controller's scope; no thread or polling. */
internal class IslandAudioSpectrum(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val handler: Handler,
    private val backend: Backend,
    private val diagnostics: IslandDiagnostics,
    private val display: (FloatArray?) -> Unit,
) {
    interface Backend {
        fun permitted(): Boolean
        fun subscribe(changed: () -> Unit): AutoCloseable
        fun session(player: MediaController, user: UserHandle?): Int?
        fun capture(session: Int, fft: (ByteArray, Int) -> Unit): AutoCloseable
    }
    data class Track(val uid: Int, val session: Int, val active: Boolean, val usage: Int)
    private var player: MediaController? = null
    private var user: UserHandle? = null
    private var job: Job? = null
    @Volatile private var generation = 0L
    private val frameLock = Any()
    private val lifecycle = Mutex()
    private val levels = FloatArray(5)
    private val posted = AtomicBoolean()
    private var pendingGeneration = 0L
    private var pendingCapture = 0L
    private val captureGeneration = AtomicLong()
    private val deliver = Runnable { ModuleHelper.guarded {
        posted.set(false)
        synchronized(frameLock) {
            if (pendingGeneration == generation && pendingCapture == captureGeneration.get() && player != null) display(levels)
        }
    } }

    fun start(selected: MediaController, foreground: UserHandle?) {
        if (player?.sessionToken == selected.sessionToken && user == foreground) return
        val keepFrame = player?.packageName == selected.packageName && foreground != null && user == foreground
        stop(clearFrame = !keepFrame)
        player = selected; user = foreground
        val epoch = generation
        job = scope.launch(io) { lifecycle.withLock {
            val changes = Channel<Unit>(Channel.CONFLATED)
            var subscription: AutoCloseable? = null
            var capture: AutoCloseable? = null
            var timeout: Job? = null
            var audioSession: Int? = null
            var failedSession: Int? = null
            val invalid = AtomicBoolean()
            try {
                if (!backend.permitted()) {
                    diagnostics.report("spectrum-permission-unavailable")
                    withContext(Dispatchers.Main.immediate) { if (epoch == generation) display(null) }
                    return@withLock
                }
                subscription = backend.subscribe { changes.trySend(Unit) }
                changes.trySend(Unit)
                for (ignored in changes) {
                    ensureActive()
                    val next = backend.session(selected, foreground)?.takeIf { it > 0 }
                    if (next == null) diagnostics.report("spectrum-no-eligible-media-track")
                    ensureActive()
                    if (epoch != generation) break
                    if (invalid.getAndSet(false)) {
                        failedSession = audioSession
                        diagnostics.report("spectrum-data-unavailable")
                    }
                    if (next == audioSession && next != failedSession) continue
                    val captureEpoch = captureGeneration.incrementAndGet()
                    timeout?.cancel(); timeout = null
                    close(capture); capture = null; audioSession = null
                    if (next == null || next == failedSession) {
                        withContext(Dispatchers.Main.immediate) { if (epoch == generation) display(null) }
                        continue
                    }
                    failedSession = null
                    audioSession = next
                    val scratch = FloatArray(5)
                    val received = AtomicBoolean()
                    var lastFftAt = 0L
                    capture = try {
                        backend.capture(next) { fft, rate ->
                            if (epoch == generation && captureEpoch == captureGeneration.get()) {
                                val now = android.os.SystemClock.elapsedRealtimeNanos()
                                val dt = if (lastFftAt == 0L) .05 else ((now - lastFftAt) / 1e9).coerceIn(.016, .15)
                                lastFftAt = now
                                // The ROM may cap capture at 20 Hz; keep the same time response
                                // at every supported capture rate. Display interpolation is local.
                                val attack = (1.0 - kotlin.math.exp(-dt / .030)).toFloat()
                                val release = (1.0 - kotlin.math.exp(-dt / .083)).toFloat()
                                if (AudioFftBands.five(fft, rate, scratch, attack, release)) {
                                    var changed = !received.getAndSet(true)
                                    synchronized(frameLock) {
                                        for (index in 0..4) if (kotlin.math.abs(scratch[index] - levels[index]) >= .006f ||
                                            scratch[index] == 0f && levels[index] != 0f) changed = true
                                        if (!changed) return@capture
                                        System.arraycopy(scratch, 0, levels, 0, 5)
                                        pendingGeneration = epoch
                                        pendingCapture = captureEpoch
                                    }
                                    if (posted.compareAndSet(false, true)) handler.post(deliver)
                                } else { invalid.set(true); changes.trySend(Unit) }
                            }
                        }
                    } catch (error: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(error)
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        failedSession = next
                        diagnostics.report("spectrum-capture-unavailable")
                        null
                    }
                    if (capture == null) withContext(Dispatchers.Main.immediate) { if (epoch == generation) display(null) }
                    else timeout = launch {
                        delay(1000) // One first-frame deadline, never a background polling loop.
                        if (!received.get()) { invalid.set(true); changes.trySend(Unit) }
                    }
                }
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                if (error is kotlinx.coroutines.CancellationException) throw error
                diagnostics.report("spectrum-session-unavailable")
                withContext(Dispatchers.Main.immediate) { if (epoch == generation) display(null) }
            } finally {
                withContext(NonCancellable) {
                    timeout?.cancel()
                    close(subscription)
                    close(capture)
                    changes.close()
                }
            }
        } }
    }

    private fun close(resource: AutoCloseable?) {
        try { resource?.close() } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            diagnostics.report("spectrum-release-unavailable")
        }
    }

    fun stop() = stop(clearFrame = true)
    fun pauseKeepingFrame() = stop(clearFrame = false)

    private fun stop(clearFrame: Boolean) {
        captureGeneration.incrementAndGet()
        generation++ // Old callbacks become inert before asynchronous native release.
        player = null; user = null
        job?.cancel(); job = null
        handler.removeCallbacks(deliver); posted.set(false)
        if (clearFrame) display(null)
    }

    companion object {
        fun sessionFor(uid: Int, tracks: List<Track>, retained: Int? = null): Int? {
            var selected: Int? = null
            for (track in tracks) if (track.active && track.uid == uid &&
                track.usage == AudioAttributes.USAGE_MEDIA && track.session > 0) {
                if (track.session == retained) return retained
                if (selected == null || track.session < selected) selected = track.session
            }
            return selected
        }
    }

    class NativeBackend(private val context: Context, private val handler: Handler) : Backend {
        private val audio = context.getSystemService(AudioManager::class.java)
        private var retainedUid = -1
        private var retainedSession: Int? = null
        override fun permitted() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission("android.permission.MODIFY_AUDIO_ROUTING") == PackageManager.PERMISSION_GRANTED

        override fun subscribe(changed: () -> Unit): AutoCloseable {
            val manager = audio ?: error("No audio manager")
            val callback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) { changed() }
            }
            manager.registerAudioPlaybackCallback(callback, handler)
            return AutoCloseable { manager.unregisterAudioPlaybackCallback(callback) }
        }

        override fun session(player: MediaController, user: UserHandle?): Int? {
            val uid = IslandMediaIdentity.uid(player) ?: return null
            if (!IslandMediaIdentity.sameUser(uid, user) ||
                player.playbackInfo?.playbackType != MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL) return null
            if (context.packageManager.getApplicationInfo(player.packageName, 0).uid != uid ||
                context.packageManager.getPackagesForUid(uid)?.singleOrNull() != player.packageName) return null
            // HyperOS mirrors MediaSessions. The native selection remains authoritative;
            // its token, package, UID and user must agree. Session count is not audio count.
            val sessions = context.getSystemService(MediaSessionManager::class.java)?.getActiveSessions(null)
                ?.filter { IslandMediaIdentity.uid(it) == uid } ?: return null
            if (sessions.none { it.sessionToken == player.sessionToken }) return null
            val tracks = audio?.activePlaybackConfigurations?.map { config ->
                Track(XposedHelpers.callMethod(config, "getClientUid") as Int,
                    XposedHelpers.callMethod(config, "getSessionId") as Int,
                    XposedHelpers.callMethod(config, "isActive") as Boolean, config.audioAttributes.usage)
            } ?: return null
            if (retainedUid != uid) { retainedUid = uid; retainedSession = null }
            // Pin one of this app's active MEDIA tracks until it stops. Callback order,
            // mirrored sessions and another app starting audio cannot make it oscillate.
            return sessionFor(uid, tracks, retainedSession).also { retainedSession = it }
        }

        override fun capture(session: Int, fft: (ByteArray, Int) -> Unit): AutoCloseable {
            require(session > 0) // Never capture the global output mix.
            val visualizer = Visualizer(session)
            try {
                visualizer.enabled = false
                val range = Visualizer.getCaptureSizeRange()
                // The callback is already signed 8-bit FFT, irrespective of the file's
                // codec, bitrate or decoded PCM depth. Use the native effect's rate.
                // Above 102.4 kHz, a 512-point capture cannot resolve our first 200 Hz band.
                val size = if (visualizer.samplingRate > 200_000 * 512) 1024 else 512
                check(visualizer.setCaptureSize(size.coerceIn(range[0], range[1])) == Visualizer.SUCCESS)
                // AS_PLAYED quantizes low-volume playback to an all-zero 8-bit FFT on
                // fuxi. Native normalization preserves a useful display signal before
                // quantization. User gain never changes the actual playback volume.
                check(visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED) == Visualizer.SUCCESS)
                check(visualizer.scalingMode == Visualizer.SCALING_MODE_NORMALIZED)
                val status = visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, data: ByteArray, rate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer, data: ByteArray, rate: Int) { fft(data, rate) }
                }, minOf(30_000, Visualizer.getMaxCaptureRate()), false, true)
                check(status == Visualizer.SUCCESS)
                visualizer.enabled = true
                check(visualizer.enabled)
            } catch (error: Throwable) {
                try { visualizer.release() } finally { FatalErrors.unwrapAndRethrowIfFatal(error) }
                throw error
            }
            return AutoCloseable {
                try { visualizer.enabled = false } finally { visualizer.release() }
            }
        }
    }
}
