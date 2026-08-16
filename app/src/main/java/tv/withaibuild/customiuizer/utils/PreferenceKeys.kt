package tv.withaibuild.customiuizer.utils

/**
 * Canonical preference key normalization.
 *
 * Remote preference storage uses the `pref_key_` prefix, while source-level
 * feature and observer code uses short keys. This is the single implementation
 * for converting a storage or already-short key into its canonical short form:
 *
 * - `"pref_key_system_charginginfo_fontsize"` -> `"system_charginginfo_fontsize"`
 * - `"system_charginginfo_fontsize"` -> `"system_charginginfo_fontsize"`
 * - `null` -> `null`
 */
internal fun canonicalPreferenceKey(key: String?): String? {
    return if (key == null) null
    else if (key.startsWith("pref_key_")) key.substring("pref_key_".length)
    else key
}

/**
 * Storage form used by the settings app SharedPreferences and backup files.
 *
 * Feature code speaks the canonical short key; XML and backup speak `pref_key_*`.
 */
internal fun storagePreferenceKey(key: String?): String? {
    val canonical = canonicalPreferenceKey(key) ?: return null
    return if (canonical.startsWith("pref_key_")) canonical else "pref_key_$canonical"
}
