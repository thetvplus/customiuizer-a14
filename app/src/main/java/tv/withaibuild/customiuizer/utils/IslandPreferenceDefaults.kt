package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode

/** Settings/restore cold path. Existing island users retain their former system feedback. */
internal object IslandPreferenceDefaults {
    val feedbackKeys = listOf("dnd", "ringer", "dark", "airplane", "nfc", "hotspot", "torch", "wifi", "data", "bluetooth")
        .map { "pref_key_system_island_feedback_$it" }

    const val waveformModeKey = "pref_key_system_island_waveform_mode"
    const val heightAutoKey = "pref_key_system_island_height_auto"
    private const val liveKindsKey = "pref_key_system_island_live_kinds"
    private val retiredMessageKinds = setOf("sms", "calendar")
    private val initializedKeys get() = feedbackKeys + waveformModeKey + heightAutoKey
    private val waveformModes = setOf("0", "1", "2")

    fun migrate(entries: MutableMap<String, Any?>) {
        (entries[liveKindsKey] as? Set<*>)?.let { selected ->
            if (selected.any { it in retiredMessageKinds })
                entries[liveKindsKey] = selected.filterIsInstance<String>().filterTo(linkedSetOf()) { it !in retiredMessageKinds }
        }
        if (heightAutoKey !in entries) entries[heightAutoKey] =
            (entries["pref_key_system_island_height"]?.toString()?.toIntOrNull() ?: 40) == 40
        val legacyEnabled = StrongToastPresentationMode.fromPreference(
            entries["pref_key_system_strong_toast_mode"]?.toString()?.toIntOrNull() ?: 0
        ) == StrongToastPresentationMode.DYNAMIC_ISLAND
        // A partial new-format backup must never re-enable omitted categories.
        val legacy = legacyEnabled && feedbackKeys.none(entries::containsKey)
        for (key in feedbackKeys) if (key !in entries) entries[key] = legacy
        entries[waveformModeKey] = if (waveformModeKey !in entries)
            if (entries["pref_key_system_island_waveform"] == false) "0" else "1"
        else entries[waveformModeKey]?.toString()?.takeIf { it in waveformModes } ?: "1"
    }

    fun initialize(prefs: SharedPreferences) {
        val validMode = try { prefs.getString(waveformModeKey, null) in waveformModes }
            catch (_: ClassCastException) { false }
        val oldKinds = prefs.all[liveKindsKey] as? Set<*>
        if (initializedKeys.all(prefs::contains) && validMode && oldKinds?.any { it in retiredMessageKinds } != true) return
        val entries = prefs.all.toMutableMap()
        val storedMode = entries[waveformModeKey]
        migrate(entries)
        val editor = prefs.edit()
        for (key in feedbackKeys) if (!prefs.contains(key)) editor.putBoolean(key, entries[key] == true)
        if (!prefs.contains(heightAutoKey)) editor.putBoolean(heightAutoKey, entries[heightAutoKey] == true)
        if (storedMode != entries[waveformModeKey]) editor.putString(waveformModeKey, entries[waveformModeKey] as String)
        if (oldKinds != entries[liveKindsKey]) {
            @Suppress("UNCHECKED_CAST")
            editor.putStringSet(liveKindsKey, entries[liveKindsKey] as Set<String>)
        }
        if (!editor.commit()) android.util.Log.e("CustoMIUIzer", "Could not save island preference migration; will retry on next launch")
    }
}
