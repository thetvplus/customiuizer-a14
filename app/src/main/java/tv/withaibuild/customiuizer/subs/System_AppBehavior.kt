package tv.withaibuild.customiuizer.subs

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.mods.appbehavior.AppBehaviorPolicy
import tv.withaibuild.customiuizer.prefs.SeekBarPreference
import tv.withaibuild.customiuizer.utils.AppHelper

class System_AppBehavior : SubFragment() {
    private val prefix = "pref_key_system_appbehavior_"
    private var editing: String? = null

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        for (name in listOf("rotation", "volume", "brightness", "block"))
            findPreference<Preference>(prefix + name + "_apps")?.onPreferenceClickListener = openAppsEdit
        findPreference<ListPreference>(prefix + "edit_app")?.setOnPreferenceChangeListener { _, value ->
            editing = value.toString()
            showLevel()
            true
        }
        findPreference<SeekBarPreference>(prefix + "edit_level")?.setOnPreferenceChangeListener { _, value ->
            val pkg = editing ?: return@setOnPreferenceChangeListener false
            val prefs = AppHelper.appPrefs ?: return@setOnPreferenceChangeListener false
            val levels = AppBehaviorPolicy.decodeLevels(prefs.getString(prefix + "brightness_values", "").orEmpty()).toMutableMap()
            val level = (value as Number).toInt().coerceIn(0, 100)
            if (level == 0) levels.remove(pkg) else levels[pkg] = level
            prefs.edit().putString(prefix + "brightness_values", levels.entries.take(256).joinToString(";") { "${it.key}=${it.value}" }).apply()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        val apps = AppHelper.appPrefs?.getStringSet(prefix + "brightness_apps", emptySet()).orEmpty().sorted().take(256)
        findPreference<ListPreference>(prefix + "edit_app")?.apply {
            // The existing selector supplies app names/icons; this small editor uses unambiguous package IDs.
            entries = apps.toTypedArray()
            entryValues = apps.toTypedArray()
            if (editing !in apps) editing = apps.firstOrNull()
            value = editing
            isEnabled = apps.isNotEmpty()
        }
        showLevel()
    }

    private fun showLevel() {
        val levels = AppBehaviorPolicy.decodeLevels(AppHelper.appPrefs?.getString(prefix + "brightness_values", "").orEmpty())
        findPreference<SeekBarPreference>(prefix + "edit_level")?.apply {
            isEnabled = editing != null
            setValue(levels[editing] ?: 0)
        }
    }
}
