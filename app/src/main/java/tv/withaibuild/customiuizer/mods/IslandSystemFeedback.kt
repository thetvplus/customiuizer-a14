package tv.withaibuild.customiuizer.mods

/** Main-owned latest feedback slot. Collect a short causal burst, never rotate/replay it. */
internal class IslandSystemFeedback {
    data class Entry(val activity: IslandSystemActivity, val observedAt: Long, val expiresAt: Long)
    private var pending: Entry? = null
    private var ready = 0L
    private var primary: Entry? = null
    private val explicitUntil = LongArray(IslandSystemKind.entries.size)

    /** A native local mode command distinguishes an independent action from a broadcast consequence. */
    fun independent(kind: IslandSystemKind, now: Long) { explicitUntil[kind.ordinal] = now + CAUSAL_WINDOW }

    fun offer(activity: IslandSystemActivity, now: Long): Entry? {
        prune(now)
        val explicit = now < explicitUntil[activity.kind.ordinal]
        if (!explicit && primary?.let { related(it, activity, now) } == true) return null
        val entry = Entry(activity, now, now + LIFETIME)
        val displaced = pending?.takeIf { it.activity.kind != activity.kind && !related(entry, it.activity, now) }
        if (activity.kind == IslandSystemKind.DND || activity.kind == IslandSystemKind.AIRPLANE) {
            primary = entry
        }
        // Replacement has a fixed deadline: repeated broadcasts cannot defer feedback forever.
        if (pending == null) ready = now + COLLECT
        pending = entry
        return displaced
    }

    fun readyAt(now: Long): Long? { prune(now); return pending?.let { maxOf(now, ready) } }
    fun poll(now: Long): Entry? {
        if (readyAt(now)?.let { it <= now } != true) return null
        return pending.also { pending = null }
    }
    fun clear() { pending = null; primary = null; ready = 0L; explicitUntil.fill(0L) }
    private fun prune(now: Long) {
        if (pending?.let { now >= it.expiresAt } == true) pending = null
        if (primary?.let { now - it.observedAt > CAUSAL_WINDOW } == true) primary = null
    }
    private fun related(root: Entry, event: IslandSystemActivity, now: Long): Boolean {
        if (now - root.observedAt !in 0..CAUSAL_WINDOW || now < explicitUntil[event.kind.ordinal]) return false
        return when (root.activity.kind) {
            IslandSystemKind.DND -> event.kind == IslandSystemKind.RINGER
            // Only radio OFF edges are consequences of enabling airplane mode. Turning a
            // radio back on remains a separate operation, including during this window.
            IslandSystemKind.AIRPLANE -> root.activity.value == 1 && event.value == 0 &&
                event.kind in RADIOS
            else -> false
        }
    }
    companion object {
        const val COLLECT = 200L
        const val CAUSAL_WINDOW = 350L
        // Original event time is retained across a slow control-center gesture. One slot,
        // six seconds maximum; presentation never renews this deadline.
        const val LIFETIME = 6000L
        const val DWELL = 2400L
        private val RADIOS = setOf(IslandSystemKind.WIFI, IslandSystemKind.BLUETOOTH, IslandSystemKind.DATA)
    }
}
