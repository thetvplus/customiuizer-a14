package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {

    private val values = HashMap<String, Any?>()

    /** Set to false to simulate a failing [commit]/[apply]. */
    var commitShouldSucceed = true

    /** Optional sequence of commit results. If set, each call to [commit] consumes one value. */
    var commitSequence: Iterator<Boolean>? = null

    /** If true, [edit] throws the configured exception for ordinary failure tests. */
    var editShouldThrow = false
    var editThrowable: Throwable? = RuntimeException("edit failure")

    /** If true, [FakeEditor.apply] throws the configured exception for ordinary failure tests. */
    var applyShouldThrow = false
    var applyThrowable: Throwable? = RuntimeException("apply failure")

    /**
     * Defensive snapshots of the in-memory preference map taken immediately after each
     * commit/apply, regardless of whether the durability result is true or false.
     *
     * This mirrors Android `SharedPreferences` semantics where `commit()` first updates the
     * in-memory map and then returns the disk-write status.
     */
    val commitSnapshots = mutableListOf<Map<String, Any?>>()

    var getAllCount = 0

    fun put(key: String, value: Any?) {
        values[key] = value
    }

    override fun getAll(): Map<String, *> {
        getAllCount++
        return HashMap(values)
    }

    override fun getString(key: String, defValue: String?): String? {
        val v = values[key] ?: return defValue
        return v as? String ?: throw ClassCastException("$key is ${v.javaClass.name}")
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val v = values[key] ?: return defValues
        @Suppress("UNCHECKED_CAST")
        return v as? Set<String> ?: throw ClassCastException("$key is ${v.javaClass.name}")
    }

    override fun getInt(key: String, defValue: Int): Int {
        val v = values[key] ?: return defValue
        return v as? Int ?: throw ClassCastException("$key is ${v.javaClass.name}")
    }

    override fun getLong(key: String, defValue: Long): Long {
        val v = values[key] ?: return defValue
        return v as? Long ?: throw ClassCastException("$key is ${v.javaClass.name}")
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val v = values[key] ?: return defValue
        return v as? Float ?: throw ClassCastException("$key is ${v.javaClass.name}")
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val v = values[key] ?: return defValue
        return v as? Boolean ?: throw ClassCastException("$key is ${v.javaClass.name}")
    }

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor {
        if (editShouldThrow) throw editThrowable ?: RuntimeException("edit failure")
        return FakeEditor(values)
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    /** Returns a defensive copy of the snapshot taken after the [index]-th commit/apply. */
    fun commitSnapshot(index: Int): Map<String, Any?> = commitSnapshots[index]

    /** Returns the number of recorded commit/apply snapshots. */
    fun commitSnapshotCount(): Int = commitSnapshots.size

    inner class FakeEditor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {

        private val staging = HashMap<String, Any?>()
        private var clearOnApply = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) {
                staging[key] = RemoveMarker
            } else {
                staging[key] = value
            }
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            if (values == null) {
                staging[key] = RemoveMarker
            } else {
                staging[key] = values
            }
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            staging[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            staging[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            staging[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            staging[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            staging[key] = RemoveMarker
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearOnApply = true
            staging.clear()
            return this
        }

        override fun commit(): Boolean {
            applyStaged()
            val sequence = this@FakeSharedPreferences.commitSequence
            return if (sequence != null && sequence.hasNext()) {
                sequence.next()
            } else {
                this@FakeSharedPreferences.commitShouldSucceed
            }
        }

        override fun apply() {
            if (this@FakeSharedPreferences.applyShouldThrow) {
                throw this@FakeSharedPreferences.applyThrowable ?: RuntimeException("apply failure")
            }
            applyStaged()
        }

        private fun applyStaged() {
            if (clearOnApply) values.clear()
            for ((key, value) in staging) {
                if (value === RemoveMarker) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
            staging.clear()
            clearOnApply = false
            recordSnapshot()
        }

        private fun recordSnapshot() {
            val snapshot = HashMap<String, Any?>(values.size * 4 / 3 + 1)
            for ((key, value) in values) {
                snapshot[key] = when (value) {
                    is Set<*> -> HashSet<String>(value.size).apply {
                        @Suppress("UNCHECKED_CAST")
                        addAll(value as Set<String>)
                    }
                    else -> value
                }
            }
            commitSnapshots.add(snapshot)
        }
    }

    private companion object {
        private val RemoveMarker = Any()
    }
}
