package tv.withaibuild.customiuizer.mods.utils

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks registrations this module creates inside a hooked system process (for example
 * DarkIconDispatcher dark receivers or StatusBarIconController icon groups), keyed by the
 * owner object whose lifetime controls them.
 *
 * SystemUI re-inflates the status bar on theme, density, display and fold changes. The system
 * singleton on the other side of each registration holds a strong reference to whatever was
 * registered, so a dead generation of module Views stays reachable for the whole process
 * lifetime unless the registration is explicitly removed. Callers run [cleanupWhere] at a
 * generation boundary (a new inflation or attach) with a predicate that identifies stale
 * owners.
 *
 * This registry does not hold strong references to historical owners. A registration handle is
 * exact-once: [cleanupNow] runs the cleanup at most once and returns true only on the first
 * call. Cleanup is two-phase (snapshot then run) so reentrant calls and cleanup callbacks that
 * themselves register or clean cannot corrupt the live list or duplicate a cleanup. All access
 * is expected to be on the SystemUI main thread.
 *
 * The cleanup action takes no arguments and must capture the objects it needs to release
 * (for example the dispatcher and the registered View). It must not depend on [owner] still
 * being alive: once the owner is garbage collected the entry is treated as stale and the
 * cleanup action is still executed.
 */
class OwnedRegistrations<V : Any> {

    private class Entry<V>(
        owner: V,
        var cleanup: (() -> Unit)?,
    ) {
        val ownerRef = WeakReference(owner)
        val consumed = AtomicBoolean(false)
    }

    private val entries = ArrayList<Entry<V>>(4)

    val size: Int get() = entries.size

    /**
     * Opaque handle returned by [register]. [cleanupNow] removes the registration and runs the
     * cleanup at most once. The first call returns true; subsequent calls return false.
     */
    interface RegistrationHandle {
        fun cleanupNow(): Boolean
    }

    private inner class Handle(private val entry: Entry<V>) : RegistrationHandle {
        override fun cleanupNow(): Boolean {
            if (!entry.consumed.compareAndSet(false, true)) return false
            // The entry may already have been removed by an outer cleanupWhere/cleanupAll;
            // this is idempotent.
            entries.remove(entry)
            val callback = entry.cleanup
            entry.cleanup = null
            if (callback != null) {
                try {
                    callback()
                } catch (t: Throwable) {
                    val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(toReport)
                }
            }
            return true
        }
    }

    /**
     * Register a [cleanup] to run when [owner] is identified as stale.
     *
     * The returned handle can be used for an explicit early cleanup. The same cleanup is also
     * executed by [cleanupWhere] when the owner is identified as stale.
     */
    fun register(owner: V, cleanup: () -> Unit): RegistrationHandle {
        val entry = Entry(owner, cleanup)
        entries.add(entry)
        return Handle(entry)
    }

    /**
     * Remove every entry whose owner is stale according to [isStale], then run each cleanup.
     *
     * Reentrant calls from inside a cleanup callback only see the live list as it exists after
     * the snapshot, so a cleanup cannot be duplicated. A single failing cleanup does not block
     * the others, but fatal JVM errors always propagate.
     */
    fun cleanupWhere(isStale: (V) -> Boolean) {
        val toRemove = ArrayList<Entry<V>>(entries.size)
        for (entry in entries) {
            val owner = entry.ownerRef.get()
            if (owner == null || isStale(owner)) {
                toRemove.add(entry)
            }
        }
        if (toRemove.isEmpty()) return

        // Remove from the live list before running any cleanup so reentrant register/cleanup
        // calls operate on the correct set.
        entries.removeAll(toRemove)

        for (entry in toRemove) {
            runCleanupOnce(entry)
        }
    }

    /**
     * Remove and run every registered cleanup.
     *
     * This is used when a whole display generation is being replaced and every registration
     * tied to the previous generation must be released.
     */
    fun cleanupAll() {
        if (entries.isEmpty()) return

        val toRemove = entries.toList()
        entries.clear()

        for (entry in toRemove) {
            runCleanupOnce(entry)
        }
    }

    private fun runCleanupOnce(entry: Entry<V>) {
        if (!entry.consumed.compareAndSet(false, true)) return
        val callback = entry.cleanup
        entry.cleanup = null
        if (callback != null) {
            try {
                callback()
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(toReport)
            }
        }
    }
}
