package tv.withaibuild.customiuizer.utils

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-local, atomically published preference snapshot used by hook callbacks.
 *
 * The snapshot is stored as an immutable [Map] behind an [AtomicReference].  A full snapshot is
 * replaced with a single reference swap, so a reader on a hot path can never see a half-written or
 * temporarily empty map.  Single-key updates use a CAS loop on the same reference.
 *
 * Remote preferences use `pref_key_` storage names. They are normalized once when inserted so hot
 * hook paths can read their short, source-level keys without allocating a prefixed String on every
 * invocation. [PreferenceBootstrap] dispatches the same short key to [PreferenceObserver] callbacks.
 */
class PrefMap {

    /** A parse result. [value] is null when [raw] could not be read as an integer. */
    private data class CachedInt(val raw: String, val value: Int?)

    private val parsedIntCache = ConcurrentHashMap<String, CachedInt>()

    private val snapshot = AtomicReference<Map<String, Any>>(emptyMap())

    private fun currentSnapshot(): Map<String, Any> = snapshot.get()

    private fun getValue(key: String): Any? {
        return currentSnapshot()[canonicalPreferenceKey(key) ?: key]
    }

    /**
     * Atomically replace the entire published snapshot with [values].
     *
     * Null values are skipped. All keys are normalized. The swap itself is a single
     * [AtomicReference.set], so readers see either the previous complete snapshot or the new
     * complete snapshot — never a partially built map.
     */
    fun replaceSnapshot(values: Map<String, *>) {
        val normalized = HashMap<String, Any>(values.size)
        for ((key, value) in values) {
            if (value != null) {
                normalized[canonicalPreferenceKey(key) ?: key] = value
            }
        }
        snapshot.set(normalized)
        parsedIntCache.clear()
    }

    /**
     * Atomically update a single key.
     *
     * The update uses a CAS loop: the current snapshot is copied, one key is changed, and the
     * reference is swapped only if the snapshot has not changed in the meantime.  This guarantees
     * that a reader sees a consistent snapshot and never a mixed old/new state.
     */
    fun put(key: String, value: Any) {
        val normalized = canonicalPreferenceKey(key) ?: key
        parsedIntCache.remove(normalized)

        while (true) {
            val old = snapshot.get()
            val new = HashMap<String, Any>(old)
            new[normalized] = value
            if (snapshot.compareAndSet(old, new)) break
        }
    }

    /**
     * Atomically remove a single key.
     */
    fun remove(key: String) {
        val normalized = canonicalPreferenceKey(key) ?: key
        parsedIntCache.remove(normalized)

        while (true) {
            val old = snapshot.get()
            val new = HashMap<String, Any>(old)
            new.remove(normalized)
            if (snapshot.compareAndSet(old, new)) break
        }
    }

    /** Clear the entire snapshot. */
    fun clear() {
        snapshot.set(emptyMap())
        parsedIntCache.clear()
    }

    /**
     * Merge the contents of [from] into the snapshot, replacing existing keys.
     *
     * Note: this is **not** atomic as a whole; each entry is inserted with an individual CAS loop.
     * Callers that need an atomic full snapshot should use [replaceSnapshot].
     */
    fun putAll(from: Map<out String, Any>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    /** Snapshot size. */
    fun size(): Int = currentSnapshot().size

    /** Whether the snapshot contains [key] exactly as given (not normalized). */
    fun containsKey(key: String): Boolean = currentSnapshot().containsKey(key)

    operator fun contains(key: String): Boolean = currentSnapshot().containsKey(key)

    /**
     * Returns the current published snapshot as an unmodifiable map.
     *
     * Callers that need a consistent view of multiple keys should read the snapshot once with
     * this method, rather than call the typed getters repeatedly. The typed getters each read
     * the current snapshot independently, so a sequence of them may observe different snapshots.
     */
    fun getAll(): Map<String, Any> = snapshot.get().let { Collections.unmodifiableMap(it) }

    fun getInt(key: String, defaultValue: Int): Int {
        val value = getValue(key)
        return value as? Int ?: defaultValue
    }

    fun getLong(key: String, defaultValue: Long): Long {
        val value = getValue(key)
        return value as? Long ?: defaultValue
    }

    fun getString(key: String, defaultValue: String): String {
        val value = getValue(key)
        return value as? String ?: defaultValue
    }

    /**
     * Reads a list preference, which the framework stores as a String.
     *
     * Every caller of this runs inside a hook, most of them in SystemUI or system_server,
     * and most of them at process start while deciding which hooks to install. It therefore
     * must not throw for any stored value: a restored backup from an older build, a key
     * whose type changed between releases, or a String that is simply not a number used to
     * escape as ClassCastException or NumberFormatException and take the host process with
     * it. Anything unreadable is the caller's [defaultValue] instead.
     *
     * A failed parse is cached like a successful one so a bad value costs one parse rather
     * than one per read on a two-second ticker.
     */
    fun getStringAsInt(key: String, defaultValue: Int): Int {
        val value = getValue(key) ?: return defaultValue
        if (value is Number) return value.toInt()
        if (value !is String) return defaultValue

        val normalized = canonicalPreferenceKey(key) ?: key
        val cached = parsedIntCache[normalized]
        if (cached != null && cached.raw == value) return cached.value ?: defaultValue

        val parsed = value.toIntOrNull()
        parsedIntCache[normalized] = CachedInt(value, parsed)
        return parsed ?: defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String): Set<String> {
        val value = getValue(key)
        return value as? Set<String> ?: Collections.emptySet()
    }

    @JvmOverloads
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        val value = getValue(key)
        return value as? Boolean ?: defaultValue
    }
}
