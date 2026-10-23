package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences

/** Settings/restore cold path. Existing island users retain their former system feedback. */
internal object IslandPreferenceDefaults {
    val feedbackKeys = listOf("dnd", "ringer", "dark", "airplane", "nfc", "hotspot", "torch", "wifi", "data", "bluetooth")
        .map { "pref_key_system_island_feedback_$it" }

    fun migrate(entries: MutableMap<String, Any?>) {
        val legacyEnabled = entries["pref_key_system_strong_toast_mode"]?.toString() == "3"
        // A partial new-format backup must never re-enable omitted categories.
        val legacy = legacyEnabled && feedbackKeys.none(entries::containsKey)
        for (key in feedbackKeys) if (key !in entries) entries[key] = legacy
    }

    fun initialize(prefs: SharedPreferences) {
        if (feedbackKeys.all(prefs::contains)) return
        val entries = prefs.all.toMutableMap()
        migrate(entries)
        val editor = prefs.edit()
        for (key in feedbackKeys) if (!prefs.contains(key)) editor.putBoolean(key, entries[key] == true)
        if (!editor.commit()) android.util.Log.e("CustoMIUIzer", "Could not save island feedback migration; will retry on next launch")
    }
}
