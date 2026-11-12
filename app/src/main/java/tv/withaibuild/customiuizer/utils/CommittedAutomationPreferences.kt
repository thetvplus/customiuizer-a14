package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences
import android.os.Bundle
import tv.withaibuild.customiuizer.mods.locationauto.LocationAutomationOptions

/** Fixed cold bridge for system_server frameworks that acknowledge writes but cache old values. */
internal object CommittedAutomationPreferences {
    private const val APP = "pref_key_system_appbehavior_"
    private const val WIFI = "pref_key_system_wifiauto_"
    private const val LOCATION = "pref_key_" + LocationAutomationOptions.PREFIX
    private val defaults: Map<String, Any> = buildMap {
        for (name in listOf("rotation", "volume", "brightness", "block")) {
            put(APP + name, false)
            put(APP + name + "_apps", emptySet<String>())
        }
        put(APP + "brightness_values", "")
        put(APP + "exceptions", "")
        put(APP + "block_limit", 0)
        put(WIFI + "enabled", false)
        put(WIFI + "rules", "")
        put(LOCATION + "enabled", false)
        put(LOCATION + "mode", LocationAutomationOptions.ALLOW)
        put(LOCATION + "apps", emptySet<String>())
        put(LOCATION + "close", false)
    }

    fun owns(key: String) = key in defaults

    fun read(remote: SharedPreferences, key: String?): Bundle {
        require(key == null || owns(key))
        // Bulk publication needs one atomic snapshot; a single change only reads its typed value.
        val snapshot = if (key == null) remote.all else null
        return Bundle().apply {
            for ((name, default) in defaults) {
                if (key != null && key != name) continue
                val value = if (snapshot != null) snapshot[name] ?: default else when (default) {
                    is Boolean -> remote.getBoolean(name, default)
                    is Int -> remote.getInt(name, default)
                    is String -> remote.getString(name, default) ?: default
                    else -> remote.getStringSet(name, emptySet()) ?: emptySet<String>()
                }
                when (default) {
                    is Boolean -> putBoolean(name, value as Boolean)
                    is Int -> putInt(name, value as Int)
                    is String -> putString(name, (value as String).take(65537))
                    else -> putStringArrayList(name, ArrayList((value as Set<*>).take(256).filterIsInstance<String>().filter { it.length <= 255 }))
                }
            }
        }
    }

    fun values(bundle: Bundle): Map<String, Any> = buildMap {
        for ((name, default) in defaults) if (bundle.containsKey(name)) {
            put(name, when (default) {
                is Boolean -> bundle.getBoolean(name)
                is Int -> bundle.getInt(name)
                is String -> checkNotNull(bundle.getString(name))
                else -> checkNotNull(bundle.getStringArrayList(name)).toSet()
            })
        }
    }
}
