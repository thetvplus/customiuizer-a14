package tv.withaibuild.customiuizer.tasker

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

class FakeSharedPreferences : SharedPreferences {

    private val store = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = store.toMap()
    override fun getString(key: String?, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = store[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = store[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) { listener?.let { listeners.add(it) } }
    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) { listeners.remove(listener) }

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { if (key != null) pending[key] = values }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) removes.add(key) }
        override fun clear(): SharedPreferences.Editor = apply { store.clear() }
        override fun commit(): Boolean = applyChanges().let { true }
        override fun apply() { applyChanges() }

        private fun applyChanges() {
            removes.forEach { store.remove(it) }
            pending.forEach { (k, v) ->
                if (v == null) store.remove(k) else store[k] = v
            }
        }
    }
}
