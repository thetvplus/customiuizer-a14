package tv.withaibuild.customiuizer.mods.utils

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/**
 * A weak-keyed map that uses object identity rather than [equals]/[hashCode].
 *
 * This implementation is intentionally narrow: it supports the exact operations the status bar
 * display registry needs and no more. Keys are held through [WeakReference] so a transient owner
 * that is never bound can still be garbage collected. The reference queue lets callers expunge
 * cleared keys and receive the associated values so they can run registration cleanup.
 *
 * Identity is defined by [System.identityHashCode] for bucketing and by referent identity (`===`)
 * for equality. Two distinct objects with the same [hashCode]/[equals] but different identities are
 * stored as separate entries; two objects whose [identityHashCode] collides are still disambiguated
 * by referent identity inside the bucket.
 *
 * All operations are expected to run on a single thread (the SystemUI main thread in production).
 * The queue is only drained when [expunge] or [pollCleared] is called; there is no background
 * reaper thread.
 */
class WeakIdentityMap<K : Any, V : Any> {

    /**
     * A [WeakReference] that also remembers the identity hash of its referent, so the bucket it
     * belongs to can be found even after the referent has been collected.
     */
    private class IdentityKey<K : Any>(
        referent: K,
        queue: ReferenceQueue<in K>,
        val identityHash: Int,
    ) : WeakReference<K>(referent, queue) {
        /**
         * Returns true if this key and [other] refer to the exact same object instance.
         * After the referent has been cleared this returns false because there is no object to
         * compare.
         */
        fun refersToSame(other: K): Boolean = this.get() === other
    }

    private class Entry<K : Any, V : Any>(
        val key: IdentityKey<K>,
        var value: V,
    )

    private val queue = ReferenceQueue<K>()
    private val buckets = HashMap<Int, ArrayList<Entry<K, V>>>()

    val size: Int
        get() {
            var count = 0
            for (bucket in buckets.values) {
                for (entry in bucket) {
                    if (entry.key.get() != null) count++
                }
            }
            return count
        }

    /**
     * Return the value associated with [key] by identity, or null if none exists.
     *
     * The lookup does not create a long-lived strong reference to [key] and does not allocate
     * beyond the short-lived iteration state.
     */
    operator fun get(key: K): V? {
        val hash = System.identityHashCode(key)
        val bucket = buckets[hash] ?: return null
        for (entry in bucket) {
            if (entry.key.refersToSame(key)) {
                return entry.value
            }
        }
        return null
    }

    /**
     * Associate [key] with [value] by identity. Returns the previous value for this identity or
     * null. If an entry for the same identity already exists, its value is replaced and the new
     * reference is refreshed to track the current [key] instance.
     */
    fun put(key: K, value: V): V? {
        val hash = System.identityHashCode(key)
        val bucket = buckets.getOrPut(hash) { ArrayList(2) }
        for (entry in bucket) {
            if (entry.key.refersToSame(key)) {
                val old = entry.value
                entry.value = value
                return old
            }
        }
        bucket.add(Entry(IdentityKey(key, queue, hash), value))
        return null
    }

    /**
     * Remove the entry for [key] by identity and return its value, or null if absent.
     */
    fun remove(key: K): V? {
        val hash = System.identityHashCode(key)
        val bucket = buckets[hash] ?: return null
        val iterator = bucket.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.refersToSame(key)) {
                iterator.remove()
                if (bucket.isEmpty()) buckets.remove(hash)
                return entry.value
            }
        }
        return null
    }

    /**
     * Drain the reference queue of keys whose referents have been garbage collected, remove each
     * from the backing map, and return the associated values so the caller can run cleanup.
     *
     * The returned values are still strongly referenced by this list until the caller drops them.
     */
    fun expunge(): List<V> {
        val cleared = mutableListOf<V>()
        while (true) {
            @Suppress("UNCHECKED_CAST")
            val key = queue.poll() as? IdentityKey<K> ?: break
            val bucket = buckets[key.identityHash] ?: continue
            val iterator = bucket.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key === key) {
                    iterator.remove()
                    cleared.add(entry.value)
                    break
                }
            }
            if (bucket.isEmpty()) buckets.remove(key.identityHash)
        }
        return cleared
    }

    /**
     * Return a snapshot of all values whose keys are still reachable. This does not remove any
     * entry and does not drain the queue.
     */
    fun valuesSnapshot(): List<V> {
        val result = ArrayList<V>()
        for (bucket in buckets.values) {
            for (entry in bucket) {
                if (entry.key.get() != null) {
                    result.add(entry.value)
                }
            }
        }
        return result
    }

    /**
     * Return a snapshot of all values, including those whose keys have already been cleared but
     * have not yet been expunged. This is useful when a caller must enumerate every state that may
     * still need cleanup.
     */
    fun allValuesSnapshot(): List<V> {
        val result = ArrayList<V>()
        for (bucket in buckets.values) {
            for (entry in bucket) {
                result.add(entry.value)
            }
        }
        return result
    }
}
