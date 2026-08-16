package tv.withaibuild.customiuizer.utils

/**
 * Bind-generation bookkeeping for the preference mirror.
 *
 * The mirror has to survive a service that binds, dies and binds again while work for the
 * previous connection is still queued: a delayed retry, a posted reconcile, a change event
 * that arrived a moment too late. Each of those carries the generation it was created for,
 * and every method here refuses to act on a generation that is no longer current. Without
 * that, a retry belonging to a dead service could clear the "not delivered" flag that the
 * live one had just set, and the user would be told their settings had arrived when they
 * had not.
 *
 * Kept free of Android types and of the service itself so the ordering rules can be tested
 * directly rather than inferred from a live bind.
 */
class PrefsMirrorState {

    private val lock = Any()

    private var generation = 0L
    private var bound = false
    private var passRunning = false
    private var dirty = false

    /** Whether this run has already spent its one follow-up. Reset by [beginPass]. */
    private var followUpClaimed = false

    /** The generation whose one allowed retry has been used. */
    private var retryUsedFor = NO_GENERATION

    private var undelivered = false

    /**
     * Whether a change the user made has not reached the module.
     *
     * Deliberately not cleared by a successful single-key write: one key arriving says
     * nothing about the keys that were dropped earlier. Only a complete, confirmed pass on
     * the current generation clears it.
     */
    val hasUndeliveredChanges: Boolean
        get() = synchronized(lock) { undelivered }

    /** The generation a caller must quote to do anything, or [NO_GENERATION] when unbound. */
    val currentGeneration: Long
        get() = synchronized(lock) { if (bound) generation else NO_GENERATION }

    /**
     * Starts a new generation for a freshly bound service and returns it.
     *
     * Everything outstanding from the previous one is invalidated by the bump alone - there
     * is nothing to cancel, because every queued body re-checks its generation before it
     * touches anything.
     */
    fun onBind(): Long = synchronized(lock) {
        generation++
        bound = true
        passRunning = false
        dirty = false
        retryUsedFor = NO_GENERATION
        generation
    }

    /**
     * Ends the current generation.
     *
     * [undelivered] is left as it is: settings that never made it are still undelivered, and
     * losing the service is not what delivers them.
     */
    fun onUnbind() = synchronized(lock) {
        generation++
        bound = false
        passRunning = false
        followUpClaimed = false
        retryUsedFor = NO_GENERATION
    }

    fun isCurrent(quoted: Long): Boolean = synchronized(lock) { bound && quoted == generation }

    /**
     * Records a local preference change.
     *
     * Returns true when a pass is in flight, i.e. when the change may not be in the snapshot
     * that pass is about to write, so the caller must let the follow-up pass carry it rather
     * than assume its own write is the last word.
     */
    fun onLocalChange(): Boolean = synchronized(lock) {
        if (passRunning) dirty = true
        passRunning
    }

    /** Claims the right to run a pass. False means stale generation, or one already running. */
    fun beginPass(quoted: Long): Boolean = synchronized(lock) {
        if (!bound || quoted != generation || passRunning) return@synchronized false
        passRunning = true
        dirty = false
        followUpClaimed = false
        true
    }

    /**
     * Whether one more pass is owed, and claims it.
     *
     * At most one: a change that lands during the follow-up leaves [dirty] set, and
     * [endPass] then reports the pass as incomplete instead of starting a third. Looping
     * until the snapshot stops moving is how a mirror turns into a spin.
     */
    fun claimFollowUpPass(quoted: Long): Boolean = synchronized(lock) {
        if (!bound || quoted != generation || followUpClaimed || !dirty) return@synchronized false
        followUpClaimed = true
        dirty = false
        true
    }

    /**
     * Ends the pass. Returns true when the mirror is known to be complete, i.e. the
     * generation is still current and no change was left unaccounted for.
     */
    fun endPass(quoted: Long): Boolean = synchronized(lock) {
        passRunning = false
        bound && quoted == generation && !dirty
    }

    /** Claims this generation's single retry. False once it has been used, or when stale. */
    fun claimRetry(quoted: Long): Boolean = synchronized(lock) {
        if (!bound || quoted != generation || retryUsedFor == quoted) return@synchronized false
        retryUsedFor = quoted
        true
    }

    /** Returns true if this is the first change to go undelivered since the last clear. */
    fun markUndelivered(): Boolean = synchronized(lock) {
        if (undelivered) return@synchronized false
        undelivered = true
        true
    }

    /** Clears the flag, but only for the generation that actually completed the sync. */
    fun clearUndelivered(quoted: Long): Boolean = synchronized(lock) {
        if (!bound || quoted != generation) return@synchronized false
        undelivered = false
        true
    }

    companion object {
        const val NO_GENERATION = -1L
    }
}
