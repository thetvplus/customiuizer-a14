package tv.withaibuild.customiuizer.mods.utils

import java.lang.ref.WeakReference

/**
 * Per-display state for a status bar generation.
 *
 * @param O the owner type (typically the status bar view)
 * @param R the second-row container type
 *
 * This is intentionally a regular class. The instance identity matters: it is used as the live
 * state object and must not be re-created by [copy]/data-class equality. The weak fields are
 * mutable by design, but the object itself is the identity token.
 */
class StatusBarDisplayState<O : Any, R : Any>(
    var generation: WeakReference<O>?,
    var secondRow: WeakReference<R>? = null,
    val registrations: OwnedRegistrations<O> = OwnedRegistrations(),
)

/**
 * Per-display state for SystemUI status bar generations.
 *
 * SystemUI may host multiple status bars on different displays. Each display has its own
 * generation (the current status bar view), its own second-row container for network speed
 * and its own [OwnedRegistrations]. A view whose display is not yet known is kept in a
 * temporary identity-scoped pending bucket so two null-display views do not clean each other.
 *
 * The pending bucket uses a [WeakIdentityMap] so equal but distinct owners do not share state
 * and so a never-bound view can still be garbage collected. The map's value strongly holds the
 * [StatusBarDisplayState] until it is bound, detached, expunged or explicitly released.
 *
 * All mutation and lookup is expected to run on the SystemUI main thread.
 */
class StatusBarDisplayRegistry<O : Any, R : Any>(
    /**
     * Called whenever the pending-state count changes from or to zero. The receiver is responsible
     * for scheduling and cancelling a bounded prune trigger (for example a single
     * [android.os.Handler] post to the main looper). This keeps the registry itself free of
     * Android framework dependencies and unit-testable on the JVM.
     */
    private val onPendingChanged: (hasPending: Boolean) -> Unit = {},
) {

    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()

    /**
     * Return the number of pending states whose owners are still reachable. This does not drain
     * the reference queue.
     */
    val pendingCount: Int get() = pendingByOwner.size

    /**
     * Return or create the pending state for [owner] when the display is not yet known.
     * Multiple calls for the same owner instance return the same state; two different owners,
     * including two that compare equal with [equals], never share a pending bucket.
     */
    fun getOrCreatePending(owner: O): StatusBarDisplayState<O, R> {
        val existing = pendingByOwner[owner]
        if (existing != null) {
            // Refresh the weak reference so the entry tracks the current owner instance.
            existing.generation = WeakReference(owner)
            return existing
        }
        val created = StatusBarDisplayState<O, R>(WeakReference(owner))
        pendingByOwner.put(owner, created)
        onPendingChanged(true)
        return created
    }

    /**
     * Bind [owner] to [displayId], migrating any pending state. If the display already has a
     * state owned by a different (dead) generation, that state is fully cleaned before the
     * display bucket is replaced.
     */
    fun bind(owner: O, displayId: Int): StatusBarDisplayState<O, R> {
        val pending = pendingByOwner.remove(owner)
        if (pending != null && pendingByOwner.size == 0) {
            // The owner migrated from pending to bound and no pending states remain.
            onPendingChanged(false)
        }
        val existing = byDisplay[displayId]
        if (existing != null && existing.generation?.get() === owner) {
            // Same view re-attached; keep its state and registrations.
            return existing
        }
        if (existing != null) {
            // Old generation for this display is being replaced; release everything it owned.
            existing.registrations.cleanupAll()
        }
        val state = pending ?: StatusBarDisplayState<O, R>(WeakReference(owner))
        state.generation = WeakReference(owner)
        if (pending != null && existing != null && pending !== existing) {
            // The pending state carries the row created at onFinishInflate; prefer it.
            if (state.secondRow == null) state.secondRow = pending.secondRow ?: existing.secondRow
        }
        byDisplay[displayId] = state
        return state
    }

    /**
     * Explicitly detach [owner] from this registry.
     *
     * This is the primary lifecycle release path. It is called when the status bar view is
     * detached from its window. It removes the owner from the pending bucket (if still pending),
     * releases the display bucket only if it is still owned by this exact [owner] instance, and
     * runs every registration cleanup for that state.
     *
     * A delayed detach (for example a posted runnable that fires after a new generation has
     * already taken over the display) must not release the new generation. The identity check
     * `state.generation?.get() === owner` guarantees that.
     */
    fun detach(owner: O) {
        // Remove from pending first, by identity.
        val pendingState = pendingByOwner.remove(owner)
        if (pendingState != null) {
            releaseState(pendingState, owner)
            if (pendingByOwner.size == 0) onPendingChanged(false)
        }

        // Find the display currently owned by this exact owner instance.
        val toRemove = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            if (state.generation?.get() === owner) {
                toRemove.add(displayId)
                releaseState(state, owner)
            }
        }
        for (displayId in toRemove) {
            byDisplay.remove(displayId)
        }
    }

    /**
     * Release all registrations for [state] when [owner] is the current generation, then clear the
     * state's references so it cannot be used again.
     *
     * The cleanup is exact-once per [state]: even if this function is called re-entrantly, the
     * [OwnedRegistrations.cleanupAll] machinery runs each registration at most once. A fatal error
     * during a single cleanup does not propagate outward because [OwnedRegistrations] isolates
     * failures; however, the generation is cleared here so the state is not bound again.
     */
    private fun releaseState(state: StatusBarDisplayState<O, R>, owner: O) {
        if (state.generation?.get() !== owner) return
        state.generation = null
        state.secondRow = null
        state.registrations.cleanupAll()
    }

    /**
     * Return the existing state for [displayId], or null if none has been bound.
     */
    fun get(displayId: Int): StatusBarDisplayState<O, R>? = byDisplay[displayId]

    /**
     * Remove every state whose owner is gone and whose registration list is empty.
     *
     * Before a state is removed, all its registrations are cleaned. If the cleanup callbacks
     * re-enter and add new registrations, the state is kept. Pending states whose keys have been
     * garbage collected are expunged from the weak identity map and cleaned.
     */
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                // The status bar view for this display is gone; release every registration.
                // A reentrant callback might register again, in which case the state is kept.
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) {
            byDisplay.remove(displayId)
        }

        // Expunge any cleared pending keys and run their registration cleanup.
        val clearedStates = pendingByOwner.expunge()
        for (state in clearedStates) {
            state.registrations.cleanupAll()
        }

        if (pendingByOwner.size == 0) {
            onPendingChanged(false)
        } else {
            onPendingChanged(true)
        }
    }

    /**
     * All current display states.
     */
    fun allDisplayStates(): Collection<StatusBarDisplayState<O, R>> = byDisplay.values

    /**
     * A snapshot of all display and pending states. The snapshot is taken on the caller's thread
     * and is safe to iterate without concurrent modification as long as all registry mutation is
     * also single-threaded.
     */
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> =
        ArrayList<StatusBarDisplayState<O, R>>(byDisplay.size + pendingByOwner.size).apply {
            addAll(byDisplay.values)
            addAll(pendingByOwner.allValuesSnapshot())
        }

    /**
     * @deprecated use [allStatesSnapshot] on the main thread.
     */
    @Suppress("unused")
    fun allStates(): Collection<StatusBarDisplayState<O, R>> = allStatesSnapshot()
}
