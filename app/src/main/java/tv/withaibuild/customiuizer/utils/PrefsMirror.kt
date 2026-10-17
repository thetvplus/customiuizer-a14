package tv.withaibuild.customiuizer.utils

/**
 * Plans the writes that make the module's remote preference snapshot equal to the settings
 * app's local one.
 *
 * The mirror used to be incremental only: [XposedServiceManager] forwarded each change as it
 * happened and dropped it whenever the service was not bound. A change made while unbound is
 * therefore lost for good - not deferred, lost - and a captured log shows the bind can stay
 * missing for the whole life of the process. The module then keeps running on a snapshot that
 * predates the change, and because it decides at load time which hooks to install from that
 * snapshot, the feature behind the setting simply never turns on. Nothing in either log says
 * why: the write succeeded locally and the drop was silent.
 *
 * A full reconcile at bind time closes that hole, because the answer to "what should the
 * remote snapshot contain" is always "whatever is local now", regardless of how many changes
 * were missed or in which order.
 *
 * Planning is kept separate from writing so the decision is testable without a service.
 */
object PrefsMirror {

    /**
     * The writes that bring the remote snapshot in line.
     *
     * [puts] are keys whose remote value is missing or differs; [removes] are keys the remote
     * side still has and the local side no longer does.
     */
    data class Plan(val puts: Map<String, Any>, val removes: Set<String>) {
        val isEmpty: Boolean
            get() = puts.isEmpty() && removes.isEmpty()

        val size: Int
            get() = puts.size + removes.size
    }

    /**
     * Computes the difference from [remote] to [local], ignoring [ignoredKeys].
     *
     * [ignoredKeys] are settings-app-only keys that the module has no business seeing. They
     * are skipped in *both* directions: never pushed, and never removed if some earlier build
     * left one behind, because this mirror does not own them and guessing would be a second
     * silent write.
     *
     * A local key whose value is `null` counts as absent - `SharedPreferences.getAll` cannot
     * normally produce one, but the incremental path already treated null as "removed" and
     * disagreeing here would make a reconcile undo what a change event just did.
     */
    @JvmStatic
    fun plan(
        local: Map<String, Any?>,
        remote: Map<String, Any?>,
        ignoredKeys: Set<String>
    ): Plan {
        val puts = LinkedHashMap<String, Any>()
        for ((key, value) in local) {
            if (key in ignoredKeys || value == null) continue
            // Set equality is by content, so a string set whose members are unchanged is not
            // rewritten just because the iteration order differs - otherwise every app-list
            // preference would be pushed again on every single bind.
            if (remote[key] == value) continue
            // `SharedPreferences.getAll` hands out the set it stores. Copying here keeps a
            // later local edit from mutating a set this plan has already handed onward.
            puts[key] = if (value is Set<*>) LinkedHashSet(value) else value
        }

        val removes = LinkedHashSet<String>()
        for (key in remote.keys) {
            if (key in ignoredKeys) continue
            if (local[key] == null) removes.add(key)
        }

        return Plan(puts, removes)
    }
}
