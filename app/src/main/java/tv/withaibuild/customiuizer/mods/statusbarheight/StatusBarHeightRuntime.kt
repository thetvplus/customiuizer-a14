package tv.withaibuild.customiuizer.mods.statusbarheight

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-scoped runtime state for the status bar height feature.
 *
 * The class is explicit about ownership: the facade (e.g. [SystemStatusBarInsetsHooks]) holds the
 * process instance.  No strong Android owner references are retained here.  Identity matching uses
 * a bounded `@Volatile` array of [WeakReference]s.
 */
internal class StatusBarHeightRuntime {

    /** Bounded identity snapshot.  Always a fixed-length array of length [MAX_TRACKED]. */
    @Volatile
    internal var knownOwners = arrayOfNulls<WeakReference<Any>?>(MAX_TRACKED)

    /** Weak reference to the most recently laid-out status bar WindowState for traversal. */
    @Volatile
    var latestKnownStatusBar: WeakReference<Any>? = null

    /** True once `LayoutParams.type == TYPE_STATUS_BAR` has been observed on this ROM. */
    @Volatile
    var typeMatchObserved: Boolean = false

    /** Remaining budget for the packageName/toString fallback probe. */
    val fallbackProbeBudget: AtomicInteger = AtomicInteger(MAX_FALLBACK_PROBES)

    /** Last generation for which a traversal was requested, to coalesce duplicates. */
    val lastRefreshGeneration: AtomicLong = AtomicLong(-1L)

    /**
     * Check whether [owner] is already known as a status bar.
     *
     * Steady-state fast path:
     * - single volatile acquire
     * - bounded linear scan (at most [MAX_TRACKED] iterations)
     * - no lock
     * - no allocation
     */
    fun isKnownStatusBar(owner: Any): Boolean {
        val snapshot = knownOwners
        for (i in 0 until MAX_TRACKED) {
            if (snapshot[i]?.get() === owner) return true
        }
        return false
    }

    /**
     * If [owner] is already known, mark it as the most recently laid-out status bar without
     * creating a new [WeakReference].  Returns `true` when the owner was known and the latest
     * ref was updated (or already pointed to the same reference).
     *
     * Fast path:
     * - single volatile snapshot acquire
     * - bounded identity scan
     * - no lock
     * - no allocation
     */
    fun markLatestIfKnown(owner: Any): Boolean {
        val snapshot = knownOwners
        for (i in 0 until MAX_TRACKED) {
            val ref = snapshot[i]
            if (ref?.get() === owner) {
                if (latestKnownStatusBar !== ref) {
                    latestKnownStatusBar = ref
                }
                return true
            }
        }
        return false
    }

    /**
     * Remember that [owner] is a status bar.
     *
     * Rare discovery path.  It allocates a new [WeakReference] only for a genuinely new owner and
     * publishes the new snapshot with a single volatile assignment.  The operation is synchronized
     * so readers can read the array without a lock.
     *
     * @return the [WeakReference] for this owner (existing or newly created).
     */
    @Synchronized
    fun rememberStatusBar(owner: Any): WeakReference<Any> {
        val current = knownOwners

        // Reuse an existing live entry.
        for (i in 0 until MAX_TRACKED) {
            val ref = current[i]
            if (ref?.get() === owner) {
                latestKnownStatusBar = ref
                return ref
            }
        }

        // Compact dead/null refs and find insertion slot.
        val next = arrayOfNulls<WeakReference<Any>>(MAX_TRACKED)
        var j = 0
        for (i in 0 until MAX_TRACKED) {
            val ref = current[i]
            if (ref != null && ref.get() != null) {
                next[j++] = ref
            }
        }

        // If at capacity, evict the oldest (slot 0) and shift.
        if (j >= MAX_TRACKED) {
            for (i in 1 until MAX_TRACKED) {
                next[i - 1] = next[i]
            }
            j = MAX_TRACKED - 1
            next[j] = null
        }

        val newRef = WeakReference(owner)
        next[j] = newRef
        knownOwners = next
        latestKnownStatusBar = newRef
        return newRef
    }

    companion object {
        /** Upper bound for the identity fast path and for per-display diagnostic stamps. */
        const val MAX_TRACKED = 4

        /** Upper bound for the expensive packageName/toString fallback probe. */
        const val MAX_FALLBACK_PROBES = 4096

        /** `WindowManager.LayoutParams.TYPE_STATUS_BAR` = 2000. */
        const val TYPE_STATUS_BAR = 2000
    }
}
