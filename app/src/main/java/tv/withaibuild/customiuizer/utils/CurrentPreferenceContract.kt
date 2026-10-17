package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * Classifies persisted preference keys against the current production contract.
 *
 * Valid keys are derived from preference XML and feature `preferenceKey` declarations
 * ([CurrentPreferenceCatalog]), plus a small explicit set of internal snapshots and
 * tightly scoped dynamic families. BackupRestore does not maintain its own key whitelist.
 */
internal object CurrentPreferenceContract {

    enum class Kind {
        CURRENT,
        INTERNAL,
        LEGACY_MIGRATABLE,
        DROPPED,
        NON_EXPORTABLE,
        UNKNOWN,
    }

    data class LegacyMigration(
        val targetKey: String,
        val convert: (Any?) -> Any?,
    )

    const val CONTRACT_REVISION = 1
    const val CONTRACT_REVISION_KEY = "pref_key_miuizer_preference_contract_revision"

    val INTERNAL_KEYS = setOf(
        "internal_updater_service_names",
        "internal_updater_service_states",
        "internal_miui_daemon_application_state",
    )

    /**
     * Current keys that production still persists but that are not declared in XML or
     * feature preferenceKey fields. Keep this set tiny; new XML/feature keys join automatically.
     */
    val EXTRA_CURRENT_KEYS = setOf(
        "pref_key_qs_autorotate_state",
    )

    val LEGACY_MIGRATIONS = mapOf(
        "pref_key_system_notif_disable_strong_toast" to LegacyMigration("pref_key_system_strong_toast_mode") { value ->
            if (value == true || value == "true" || value == 1 || value == "1") "2" else null
        },
    )

    private val STRUCTURAL_KEYS = setOf(
        "prefs",
        "pref_key_cat",
        "pref_key_warning",
        "pref_key_system",
        "pref_key_launcher",
        "pref_key_controls",
        "pref_key_various",
        "pref_key_miuizer",
    )

    private val DYNAMIC_SUFFIXES = arrayOf(
        "_shortcut_intent",
        "_shortcut_name",
        "_shortcut_icon",
        "_activity_user",
        "_app_user",
        "_shortcut",
        "_activity",
        "_action",
        "_toggle",
        "_packages",
        "_components",
        "_states",
        "_black",
        "_user",
        "_app",
        "start_hour",
        "start_minute",
        "end_hour",
        "end_minute",
    )

    fun classify(key: String): Kind {
        val storage = storagePreferenceKey(key) ?: return Kind.UNKNOWN
        if (storage == CONTRACT_REVISION_KEY || key == CONTRACT_REVISION_KEY) {
            return Kind.NON_EXPORTABLE
        }
        if (key in BackupRestore.NON_EXPORTABLE_KEYS || storage in BackupRestore.NON_EXPORTABLE_KEYS) {
            return Kind.NON_EXPORTABLE
        }
        if (key in LEGACY_MIGRATIONS || storage in LEGACY_MIGRATIONS) {
            return Kind.LEGACY_MIGRATABLE
        }
        if (key in BackupRestore.DROPPED_KEYS || storage in BackupRestore.DROPPED_KEYS) {
            return Kind.DROPPED
        }
        if (key in INTERNAL_KEYS || canonicalPreferenceKey(key) in INTERNAL_KEYS) {
            return Kind.INTERNAL
        }
        if (key in EXTRA_CURRENT_KEYS || storage in EXTRA_CURRENT_KEYS) {
            return Kind.CURRENT
        }
        if (isCatalogKey(key) || isAllowedDynamicFamily(storage)) {
            return Kind.CURRENT
        }
        return Kind.UNKNOWN
    }

    fun isExportable(key: String): Boolean = when (classify(key)) {
        Kind.CURRENT, Kind.INTERNAL, Kind.LEGACY_MIGRATABLE -> true
        Kind.DROPPED, Kind.NON_EXPORTABLE, Kind.UNKNOWN -> false
    }

    fun applyMigrations(entries: MutableMap<String, Any?>): Int {
        var migrated = 0
        for ((oldKey, migration) in LEGACY_MIGRATIONS) {
            val present = when {
                oldKey in entries -> oldKey
                storagePreferenceKey(oldKey) in entries -> storagePreferenceKey(oldKey)
                else -> null
            } ?: continue
            val oldValue = entries.remove(present)
            val converted = migration.convert(oldValue) ?: continue
            if (migration.targetKey !in entries) {
                entries[migration.targetKey] = converted
            }
            migrated++
        }
        return migrated
    }

    /**
     * One-shot upgrade-path cleanup of orphan keys. Skips when [CONTRACT_REVISION]
     * is already durable. Must not run on SystemUI or other hook hot paths.
     *
     * @return true if a commit was attempted (revision was stale).
     */
    fun pruneOrphanPreferences(prefs: SharedPreferences): Boolean {
        if (prefs.getInt(CONTRACT_REVISION_KEY, 0) >= CONTRACT_REVISION) return false
        val working = LinkedHashMap<String, Any?>()
        for ((key, value) in prefs.all) {
            working[key] = when (value) {
                is Set<*> -> LinkedHashSet<String>(value.size).apply {
                    @Suppress("UNCHECKED_CAST")
                    addAll(value as Set<String>)
                }
                else -> value
            }
        }
        applyMigrations(working)
        val kept = LinkedHashMap<String, Any?>(working.size)
        for ((key, value) in working) {
            when (classify(key)) {
                Kind.CURRENT, Kind.INTERNAL, Kind.NON_EXPORTABLE -> {
                    if (key != CONTRACT_REVISION_KEY) kept[key] = value
                }
                Kind.LEGACY_MIGRATABLE, Kind.DROPPED, Kind.UNKNOWN -> Unit
            }
        }
        val editor = prefs.edit()
        editor.clear()
        BackupRestore.putSupportedPreferenceEntries(editor, kept)
        editor.putInt(CONTRACT_REVISION_KEY, CONTRACT_REVISION)
        editor.commit()
        return true
    }

    private fun isCatalogKey(key: String): Boolean {
        if (key in CurrentPreferenceCatalog.STORAGE_KEYS) return true
        val storage = storagePreferenceKey(key) ?: return false
        return storage in CurrentPreferenceCatalog.STORAGE_KEYS
    }

    private fun isAllowedDynamicFamily(storageKey: String): Boolean {
        val colon = storageKey.indexOf(':')
        if (colon > 0) {
            return isFamilyBase(storageKey.substring(0, colon))
        }
        if ('|' in storageKey) {
            val prefix = storageKey.substring(0, storageKey.indexOf('|'))
            var cut = prefix.lastIndexOf('_')
            while (cut > 0) {
                if (isFamilyBase(prefix.substring(0, cut))) return true
                cut = prefix.lastIndexOf('_', cut - 1)
            }
        }
        for (suffix in DYNAMIC_SUFFIXES) {
            if (storageKey.endsWith(suffix) && storageKey.length > suffix.length) {
                val stem = storageKey.substring(0, storageKey.length - suffix.length)
                if (isFamilyBase(stem) || isUuidInstance(stem)) return true
            }
        }
        return isUuidInstance(storageKey)
    }

    private fun isUuidInstance(key: String): Boolean {
        val idx = key.lastIndexOf('_')
        if (idx <= 0) return false
        val tail = key.substring(idx + 1)
        if (tail.length != 32) return false
        for (ch in tail) {
            val hex = ch in '0'..'9' || ch in 'a'..'f'
            if (!hex) return false
        }
        return isFamilyBase(key.substring(0, idx))
    }

    private fun isFamilyBase(key: String): Boolean {
        if (isStructuralKey(key)) return false
        return isCatalogKey(key)
    }

    private fun isStructuralKey(key: String): Boolean =
        key in STRUCTURAL_KEYS || "_cat" in key
}
