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
        if (player === selected && user == foreground) return
        stop()
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
                if (!backend.permitted()) { diagnostics.report("spectrum-permission-unavailable"); return@withLock }
                subscription = backend.subscribe { changes.trySend(Unit) }
                changes.trySend(Unit)
                for (ignored in changes) {
                    ensureActive()
                    val next = backend.session(selected, foreground)?.takeIf { it > 0 }
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
                    withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        if (epoch == generation) display(null)
                    }
                    if (next == null || next == failedSession) continue
                    failedSession = null
                    audioSession = next
                    val scratch = FloatArray(5)
                    val received = AtomicBoolean()
                    capture = try {
                        backend.capture(next) { fft, rate ->
                            if (epoch == generation && captureEpoch == captureGeneration.get()) {
                                if (AudioFftBands.five(fft, rate, scratch)) {
                                    received.set(true)
                                    synchronized(frameLock) {
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
                    if (capture != null) timeout = launch {
                        delay(1000) // One first-frame deadline, never a background polling loop.
                        if (!received.get()) { invalid.set(true); changes.trySend(Unit) }
                    }
                }
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                if (error is kotlinx.coroutines.CancellationException) throw error
                diagnostics.report("spectrum-session-unavailable")
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

    fun stop() {
        captureGeneration.incrementAndGet()
        generation++ // Old callbacks become inert before asynchronous native release.
        player = null; user = null
        job?.cancel(); job = null
        handler.removeCallbacks(deliver); posted.set(false)
        display(null)
    }

    companion object {
        fun sessionFor(uid: Int, tracks: List<Track>): Int? {
            var selected: Int? = null
            for (track in tracks) if (track.active && track.uid == uid && track.usage == AudioAttributes.USAGE_MEDIA) {
                if (track.session <= 0 || selected != null && selected != track.session) return null
                selected = track.session
            }
            return selected
        }
    }

    class NativeBackend(private val context: Context, private val handler: Handler) : Backend {
        private val audio = context.getSystemService(AudioManager::class.java)
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
            // MediaController has no public audio-session ID. Require a unique local media
            // session AND a unique active MEDIA audio session for this UID. Ambiguity fails.
            val sessions = context.getSystemService(MediaSessionManager::class.java)?.getActiveSessions(null)
                ?.filter { IslandMediaIdentity.uid(it) == uid } ?: return null
            if (sessions.size != 1 || sessions[0].sessionToken != player.sessionToken) return null
            val tracks = audio?.activePlaybackConfigurations?.map { config ->
                Track(XposedHelpers.callMethod(config, "getClientUid") as Int,
                    XposedHelpers.callMethod(config, "getSessionId") as Int,
                    XposedHelpers.callMethod(config, "isActive") as Boolean, config.audioAttributes.usage)
            } ?: return null
            return sessionFor(uid, tracks)
        }

        override fun capture(session: Int, fft: (ByteArray, Int) -> Unit): AutoCloseable {
            require(session > 0) // Never capture the global output mix.
            val visualizer = Visualizer(session)
            try {
                visualizer.enabled = false
                val range = Visualizer.getCaptureSizeRange()
                visualizer.captureSize = 512.coerceIn(range[0], range[1])
                visualizer.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                val status = visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, data: ByteArray, rate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer, data: ByteArray, rate: Int) { fft(data, rate) }
                }, minOf(20_000, Visualizer.getMaxCaptureRate()), false, true)
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
