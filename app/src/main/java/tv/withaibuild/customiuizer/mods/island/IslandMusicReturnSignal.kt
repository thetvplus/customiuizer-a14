package tv.withaibuild.customiuizer.mods.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RectF
import android.os.Handler
import android.os.SystemClock
import android.os.UserHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import java.util.concurrent.atomic.AtomicLong

/** Explicit, identity-checked A14 messages. No title, artwork, audio or window content. */
internal object IslandMusicReturnSignal {
    const val REQUEST = "tv.withaibuild.customiuizer.r14.ISLAND_MUSIC_RETURN_REQUEST"
    const val STATE = "tv.withaibuild.customiuizer.r14.ISLAND_MUSIC_RETURN_STATE"
    const val SYSTEM_UI = "com.android.systemui"
    const val LAUNCHER = "com.miui.home"
    const val LIFETIME = 5000L
    private val serial = AtomicLong()
    private fun nextSequence() = serial.updateAndGet { maxOf(it + 1, SystemClock.elapsedRealtimeNanos()) }

    data class Frame(val packageName: String, val user: UserHandle,
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val screenWidth: Int, val screenHeight: Int) {
        fun valid(): Boolean = packageName.length in 1..255 &&
            screenWidth in 1..10000 && screenHeight in 1..20000 && screenWidth < screenHeight &&
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            left >= 0 && top >= 0 && right > left && bottom > top &&
            right <= screenWidth && bottom <= screenHeight
        fun rectangle() = RectF(left, top, right, bottom)
    }

    data class Snapshot(val frame: Frame?, val issuedAt: Long, val sequence: Long) {
        fun fresh(now: Long) = now >= issuedAt && now - issuedAt <= LIFETIME
    }

    fun encode(frame: Frame?, now: Long = SystemClock.uptimeMillis(), sequence: Long = nextSequence()): Intent =
        Intent(STATE).setPackage(LAUNCHER).putExtra("at", now).putExtra("sequence", sequence)
            .putExtra("active", frame != null).apply {
                if (frame != null) {
                    putExtra("source", frame.packageName); putExtra("user", frame.user)
                    putExtra("left", frame.left); putExtra("top", frame.top)
                    putExtra("right", frame.right); putExtra("bottom", frame.bottom)
                    putExtra("width", frame.screenWidth); putExtra("height", frame.screenHeight)
                }
            }

    fun decode(intent: Intent, now: Long): Snapshot? {
        if (intent.action != STATE) return null
        val at = intent.getLongExtra("at", -1)
        val sequence = intent.getLongExtra("sequence", -1)
        if (at < 0 || sequence < 0 || now < at || now - at > LIFETIME) return null
        if (!intent.getBooleanExtra("active", false)) return Snapshot(null, at, sequence)
        val frame = Frame(intent.getStringExtra("source") ?: return null,
            intent.getParcelableExtra("user", UserHandle::class.java) ?: return null,
            intent.getFloatExtra("left", Float.NaN), intent.getFloatExtra("top", Float.NaN),
            intent.getFloatExtra("right", Float.NaN), intent.getFloatExtra("bottom", Float.NaN),
            intent.getIntExtra("width", 0), intent.getIntExtra("height", 0))
        return if (frame.valid()) Snapshot(frame, at, sequence) else null
    }

    /** Owned by one attached SystemUI island controller; capacity one queued send. */
    class Publisher(private val context: Context, private val handler: Handler,
        private val scope: CoroutineScope, private val dispatcher: CoroutineDispatcher,
        private val onCommit: (Frame, Long) -> Unit = { _, _ -> },
        private val onImpulse: (Float) -> Unit = {},
        private val current: () -> Frame?) : AutoCloseable {
        private var closed = false
        private var sent = false
        private var last: Frame? = null
        private var job: Job? = null
        private var committedGesture = -1L
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (closed || intent.action != REQUEST || !ModuleHelper.isTrustedBroadcast(this, LAUNCHER)) return@guarded
                if (intent.getBooleanExtra("committed", false)) {
                    val packet = decode(Intent(intent).setAction(STATE), SystemClock.uptimeMillis()) ?: return@guarded
                    val frame = current() ?: return@guarded
                    val gesture = intent.getLongExtra("gesture", -1)
                    val at = intent.getLongExtra("committedAt", -1)
                    val now = SystemClock.uptimeMillis()
                    if (packet.frame == frame && gesture > committedGesture && at >= 0 && now >= at && now - at <= 1000) {
                        committedGesture = gesture
                        onImpulse(intent.getFloatExtra("impulse", 0f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
                        onCommit(frame, at)
                    }
                } else publish(force = true)
            }
        }

        init { context.registerReceiver(receiver, IntentFilter(REQUEST), null, handler, Context.RECEIVER_EXPORTED) }

        fun publish(force: Boolean = false) {
            if (closed) return
            val frame = current()?.takeIf { it.valid() }
            if (!force && sent && frame == last) return
            last = frame; sent = true
            val message = encode(frame)
            job?.cancel()
            job = scope.launch(dispatcher) { ModuleHelper.guarded { ModuleHelper.sendBroadcastWithIdentity(context, message) } }
        }

        override fun close() {
            if (closed) return
            closed = true
            job?.cancel(); job = null
            try {
                // Final invalidation is a cold lifecycle operation, completed before owner disposal.
                if (sent) ModuleHelper.guarded { ModuleHelper.sendBroadcastWithIdentity(context, encode(null)) }
            } finally { ModuleHelper.guarded { context.unregisterReceiver(receiver) } }
            last = null
        }
    }
}
