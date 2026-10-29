package tv.withaibuild.customiuizer.mods.vpn

import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings as Settings

/** Handler-owned command lifecycle. At most one retiring start; it blocks further starts until resolved. */
internal class VpnAutoSwitchCoordinator(options: Settings.Options, private val backend: Backend) {
    interface Backend {
        fun snapshot(): VpnAutoSwitchPolicy.Snapshot
        fun start(target: Settings.Target): Boolean
        fun stop(expected: VpnAutoSwitchPolicy.Session): Boolean
    }

    private data class RetiringStart(val target: Settings.Target, val until: Long)
    private var retiring: RetiringStart? = null
    var policy = VpnAutoSwitchPolicy(options)
        private set
    var status = policy.status
        private set
    val active: Boolean get() = policy.options.configured || retiring != null

    fun configure(updated: Settings.Options, now: Long) {
        if (updated.configured && policy.options.configured && updated.target == policy.options.target) {
            policy.update(updated, now)
            return
        }
        val next = policy.forOptions(updated)
        if (updated.autoClose) policy.owned?.let(backend::stop)
        if (policy.pendingStartDeadline > now) {
            check(retiring == null) { "A retiring VPN start must finish before another start" }
            retiring = RetiringStart(policy.options.target, policy.pendingStartDeadline)
        }
        policy.relinquish()
        policy = next
    }

    /** Returns one next wake-up delay, never a polling interval. */
    fun reconcile(now: Long): Long? {
        var snapshot = backend.snapshot()
        retiring?.takeIf { now <= it.until && policy.options.target == it.target && policy.hasTasks }?.let {
            // A quick off/on followed by a real trigger can reuse its still-pending request.
            policy.continuePendingStart(it.until)
            retiring = null
        }
        retiring?.let { lease ->
            val session = snapshot.session
            if (snapshot.known && session != null && now <= lease.until && session.target == lease.target) {
                if (policy.options.autoClose) backend.stop(session)
                retiring = null
                snapshot = backend.snapshot()
            } else if (now >= lease.until) {
                retiring = null
            }
            if (retiring != null) {
                status = VpnAutoSwitchPolicy.Status.CONNECTING
                return lease.until - now
            }
        }
        when (policy.evaluate(snapshot, now)) {
            VpnAutoSwitchPolicy.Action.START -> {
                if (backend.start(policy.options.target)) {
                    // Pin a synchronous system-profile session before any later configuration change.
                    snapshot = backend.snapshot()
                    policy.evaluate(snapshot, now)
                } else policy.startFailed()
            }
            VpnAutoSwitchPolicy.Action.STOP -> {
                val owned = policy.owned
                policy.relinquish()
                if (owned != null) backend.stop(owned)
                snapshot = backend.snapshot()
                policy.evaluate(snapshot, now)
            }
            VpnAutoSwitchPolicy.Action.NONE -> Unit
        }
        status = policy.status
        return policy.nextDelay(snapshot, now)
    }

    fun abandon() {
        retiring = null
        policy.relinquish()
    }
}
