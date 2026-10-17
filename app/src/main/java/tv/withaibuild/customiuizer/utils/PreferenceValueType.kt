package tv.withaibuild.customiuizer.utils

enum class PreferenceValueType {
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    STRING,
    STRING_SET,
}

/**
 * Resolves the persisted type of a preference key from generated catalog metadata.
 *
 * Unknown and dynamic keys return null so the caller can take a cold [SharedPreferences.getAll]
 * fallback for that key only.
 */
internal object PreferenceValueTypes {

    @JvmStatic
    fun resolve(key: String): PreferenceValueType? {
        val storage = storagePreferenceKey(key) ?: key
        CurrentPreferenceCatalog.VALUE_TYPES[storage]?.let { return it }
        CurrentPreferenceCatalog.VALUE_TYPES[key]?.let { return it }
        return inferDynamic(storage)
    }

    private fun inferDynamic(storage: String): PreferenceValueType? {
        if (storage.endsWith("_apps") || storage.endsWith("_black")) {
            return PreferenceValueType.STRING_SET
        }
        if ('|' in storage) {
            return PreferenceValueType.INT
        }
        return null
    }
}
