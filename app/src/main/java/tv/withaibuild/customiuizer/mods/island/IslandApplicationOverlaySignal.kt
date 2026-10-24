package tv.withaibuild.customiuizer.mods.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.SystemClock
import android.os.UserHandle
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.Helpers
import java.util.concurrent.atomic.AtomicLong

/** A14 sender identity is mandatory; only presence/user IDs cross the process boundary. */
internal object IslandApplicationOverlaySignal {
    val REQUEST = Helpers.modulePkg + ".ISLAND_APP_OVERLAY_REQUEST"
    val STATE = Helpers.modulePkg + ".ISLAND_APP_OVERLAY_STATE"
    const val SYSTEM_UI = "com.android.systemui"
    const val SYSTEM = "android"
    const val MAX_USERS = 64
    const val LIFETIME = 5000L
    private val serial = AtomicLong()
    fun nextSequence() = serial.updateAndGet { maxOf(it + 1, SystemClock.elapsedRealtimeNanos()) }

    data class Snapshot(val users: Set<Int>, val known: Boolean = true) {
        fun hasWindow(user: UserHandle): Boolean = known &&
            users.any { UserHandle.getUserHandleForUid(it * 100000) == user }
    }
    data class Packet(val state: Snapshot, val sequence: Long, val nonce: Long) {
        // A fresh full-state event can initialize a new owner if the request response was
        // delayed or lost. Replies addressed to another owner must never initialize it.
        fun acceptedBy(ownerNonce: Long, previous: Long) = sequence > previous && (nonce == 0L || nonce == ownerNonce)
    }

    fun encode(state: Snapshot, sequence: Long, nonce: Long = 0): Intent = Intent(STATE)
        .setPackage(SYSTEM_UI).putExtra("at", SystemClock.uptimeMillis())
        .putExtra("sequence", sequence).putExtra("nonce", nonce).putExtra("known", state.known)
        .putExtra("users", state.users.sorted().toIntArray())

    fun decode(intent: Intent, now: Long): Packet? {
        if (intent.action != STATE) return null
        val at = intent.getLongExtra("at", -1)
        val sequence = intent.getLongExtra("sequence", -1)
        val nonce = intent.getLongExtra("nonce", -1)
        val users = intent.getIntArrayExtra("users") ?: return null
        if (at < 0 || now < at || now - at > LIFETIME || sequence <= 0 || nonce < 0 ||
            users.size > MAX_USERS || users.any { it !in 0..21473 } || users.toSet().size != users.size) return null
        return Packet(Snapshot(users.toSet(), intent.getBooleanExtra("known", false)), sequence, nonce)
    }

    /** One receiver owned by one attached island. Last snapshot only; no periodic refresh. */
    class Subscriber(private val context: Context, handler: Handler,
        send: (Intent) -> Unit, private val changed: () -> Unit) : AutoCloseable {
        private var closed = false
        private var sequence = -1L
        private val nonce = nextSequence()
        private var state = Snapshot(emptySet(), false)
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (closed || getSentFromUid() != 1000 || !ModuleHelper.isTrustedBroadcast(this, SYSTEM)) return@guarded
                val packet = decode(intent, SystemClock.uptimeMillis()) ?: return@guarded
                if (!packet.acceptedBy(nonce, sequence)) return@guarded
                sequence = packet.sequence
                if (packet.state != state) { state = packet.state; changed() }
            }
        }
        init {
            context.registerReceiver(receiver, IntentFilter(STATE), null, handler, Context.RECEIVER_EXPORTED)
            try {
                send(Intent(REQUEST).setPackage(SYSTEM).putExtra("nonce", nonce)
                    .putExtra("at", SystemClock.uptimeMillis()))
            } catch (error: Throwable) {
                close()
                throw error
            }
        }
        fun hasWindow(user: UserHandle) = state.hasWindow(user)
        override fun close() {
            if (closed) return
            closed = true
            ModuleHelper.guarded { context.unregisterReceiver(receiver) }
            state = Snapshot(emptySet(), false)
        }
    }
}
