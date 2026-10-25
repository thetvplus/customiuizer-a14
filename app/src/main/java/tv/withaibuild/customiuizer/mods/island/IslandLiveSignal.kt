package tv.withaibuild.customiuizer.mods.island

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Bundle
import android.os.SystemClock
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.Helpers

/** Scalar state from two opted-in native producers. Sender identity is checked before extras. */
internal object IslandLiveSignal {
    val REQUEST = Helpers.modulePkg + ".ISLAND_LIVE_REQUEST"
    val STATE = Helpers.modulePkg + ".ISLAND_LIVE_STATE"
    private const val VERSION = 1
    private const val MAX_AGE = 5000L
    private val packages = setOf(IslandLiveKind.CLOCK_PACKAGE, IslandLiveKind.RECORDER_PACKAGE)

    data class Packet(val kind: IslandLiveKind, val activity: IslandLiveActivity?, val sequence: Long,
        val nonce: Long, val owner: IBinder? = null) {
        fun acceptedBy(ownerNonce: Long, previous: Long) = sequence > previous && (nonce == 0L || nonce == ownerNonce)
    }

    fun owns(packageName: String, kind: IslandLiveKind) =
        packageName == IslandLiveKind.CLOCK_PACKAGE && kind in IslandLiveKind.clockKinds ||
            packageName == IslandLiveKind.RECORDER_PACKAGE && kind == IslandLiveKind.RECORDER

    fun encode(packageName: String, kind: IslandLiveKind, activity: IslandLiveActivity?,
        sequence: Long, nonce: Long = 0, owner: IBinder? = null): Intent = Intent(STATE).setPackage("com.android.systemui")
        .putExtra("version", VERSION).putExtra("at", SystemClock.uptimeMillis())
        .putExtra("source", packageName).putExtra("kind", kind.value)
        .putExtra("sequence", sequence).putExtra("nonce", nonce).putExtra("active", activity != null)
        .apply {
            if (owner != null) putExtras(Bundle().apply { putBinder("owner", owner) })
            activity?.let { value ->
                putExtra("session", value.session); putExtra("alert", value.alert)
                putExtra("open", value.open)
                if (kind == IslandLiveKind.RECORDER && value.meter != null)
                    putExtras(Bundle().apply { putBinder("meter", value.meter) })
                value.clock?.let { clock ->
                    putExtra("clock", true); putExtra("base", clock.base); putExtra("down", clock.countdown)
                    clock.pausedMillis?.let { putExtra("paused", it) }
                }
            }
        }

    fun decode(intent: Intent, packageName: String, uid: Int, now: Long, elapsed: Long): Packet? {
        if (intent.action != STATE || packageName !in packages || uid < 0 ||
            intent.getIntExtra("version", 0) != VERSION || intent.getStringExtra("source") != packageName) return null
        val kind = IslandLiveKind.entries.singleOrNull { it.value == intent.getStringExtra("kind") } ?: return null
        if (!owns(packageName, kind)) return null
        val at = intent.getLongExtra("at", -1)
        val sequence = intent.getLongExtra("sequence", -1)
        val nonce = intent.getLongExtra("nonce", -1)
        if (at < 0 || at > now || now - at > MAX_AGE || sequence <= 0 || nonce < 0) return null
        val owner = intent.extras?.getBinder("owner")
        if (!intent.getBooleanExtra("active", false)) return Packet(kind, null, sequence, nonce, owner)
        val session = intent.getLongExtra("session", 0)
        if (session <= 0) return null
        val clock = if (intent.getBooleanExtra("clock", false)) {
            val base = intent.getLongExtra("base", Long.MIN_VALUE)
            val distance = IslandLiveTime.difference(base, elapsed) ?: return null
            if (distance !in -IslandLiveTime.MAX_SPAN..IslandLiveTime.MAX_SPAN) return null
            val paused = if (intent.hasExtra("paused")) intent.getLongExtra("paused", -1).also {
                if (it !in 0..IslandLiveTime.MAX_SPAN) return null
            } else null
            IslandLiveClock(base, intent.getBooleanExtra("down", false), paused)
        } else null
        val open = intent.getParcelableExtra("open", PendingIntent::class.java)
        if (open != null && (!open.isActivity || open.creatorUid != uid || open.creatorPackage != packageName)) return null
        val meter = intent.extras?.getBinder("meter")
        if (meter != null && kind != IslandLiveKind.RECORDER) return null
        return Packet(kind, IslandLiveActivity(kind, packageName, uid, clock, open,
            session = session, alert = intent.getBooleanExtra("alert", false), meter = meter), sequence, nonce, owner)
    }

    /** One attached SystemUI owner, with no retry timer. Package/UID resolution is supplied cold. */
    class Subscriber(private val context: Context, handler: Handler, private val identities: Map<String, Int>,
        private val changed: (IslandLiveKind, IslandLiveActivity?) -> Unit) : AutoCloseable {
        @Volatile private var closed = false
        private val nonce = IslandApplicationOverlaySignal.nextSequence()
        private val sequences = HashMap<IslandLiveKind, Long>()
        private val owners = IslandLiveOwners(post = { task -> handler.post { ModuleHelper.guarded { task() } } }) { pkg ->
            for (kind in IslandLiveKind.entries) if (owns(pkg, kind)) changed(kind, null)
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = ModuleHelper.guarded {
                val packageName = getSentFromPackage() ?: return@guarded
                val uid = identities[packageName] ?: return@guarded
                if (closed || getSentFromUid() != uid || !ModuleHelper.isTrustedBroadcast(this, packageName)) return@guarded
                val packet = decode(intent, packageName, uid, SystemClock.uptimeMillis(), SystemClock.elapsedRealtime()) ?: return@guarded
                if (!packet.acceptedBy(nonce, sequences[packet.kind] ?: -1)) return@guarded
                if (!owners.accept(packageName, packet.owner ?: return@guarded)) return@guarded
                sequences[packet.kind] = packet.sequence
                changed(packet.kind, packet.activity)
            }
        }
        init { context.registerReceiver(receiver, IntentFilter(STATE), null, handler, Context.RECEIVER_EXPORTED) }
        fun request() {
            if (closed) return
            for (packageName in identities.keys) ModuleHelper.sendBroadcastWithIdentity(context,
                Intent(REQUEST).setPackage(packageName).putExtra("at", SystemClock.uptimeMillis()).putExtra("nonce", nonce))
        }
        override fun close() {
            if (closed) return
            closed = true
            owners.close()
            ModuleHelper.guarded { context.unregisterReceiver(receiver) }
            sequences.clear()
        }
    }
}

/** Two anonymous liveness tokens. Kernel death replaces a poll and cannot clear a newer owner. */
internal class IslandLiveOwners(private val post: (() -> Unit) -> Unit,
    private val ended: (String) -> Unit) : AutoCloseable {
    private class Owner(val binder: IBinder, val death: IBinder.DeathRecipient)
    private val owners = HashMap<String, Owner>(2)
    private var closed = false
    fun accept(pkg: String, binder: IBinder): Boolean {
        if (pkg != IslandLiveKind.CLOCK_PACKAGE && pkg != IslandLiveKind.RECORDER_PACKAGE) return false
        if (closed || !binder.isBinderAlive) return false
        if (owners[pkg]?.binder == binder) return true
        var next: Owner? = null
        val death = IBinder.DeathRecipient { post { ModuleHelper.guarded {
            if (!closed && owners[pkg] === next) {
                owners.remove(pkg)
                ended(pkg)
            }
        } } }
        next = Owner(binder, death)
        owners.remove(pkg)?.let { old -> ModuleHelper.guarded { old.binder.unlinkToDeath(old.death, 0) } }
        owners[pkg] = next
        try { binder.linkToDeath(death, 0) } catch (_: android.os.RemoteException) {
            if (owners[pkg] === next) { owners.remove(pkg); ended(pkg) }
            return false
        }
        return true
    }
    override fun close() {
        if (closed) return
        closed = true
        for (owner in owners.values) ModuleHelper.guarded { owner.binder.unlinkToDeath(owner.death, 0) }
        owners.clear()
    }
}
