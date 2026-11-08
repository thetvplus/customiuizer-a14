package tv.withaibuild.customiuizer.utils

/**
 * Pure route resolver for search navigation.
 * Kept free of Android UI dependencies so the navigation logic can be unit tested.
 */

data class SearchRoute(
    val category: String,
    val key: String,
    val sub: String? = null
) {
    fun isCategorySelector(): Boolean = sub == null && category in CATEGORY_SELECTOR_CATEGORIES

    private companion object {
        private val CATEGORY_SELECTOR_CATEGORIES = setOf(
            "pref_key_system",
            "pref_key_launcher",
            "pref_key_controls",
            "pref_key_various"
        )
    }
}

object SearchRouteResolver {

    private val KNOWN_CATEGORIES = setOf(
        "pref_key_system",
        "pref_key_launcher",
        "pref_key_controls",
        "pref_key_various"
    )

    /**
     * Collapses null, empty or all-whitespace `sub` values to `null` while keeping real
     * sub-category keys intact.
     */
    fun normalizeSub(sub: String?): String? = when (sub?.takeIf { it.isNotBlank() }) {
        "pref_key_system_cat_ios_volume" -> "pref_key_system_cat_audio"
        "pref_key_system_cat_ios_desktop" -> "pref_key_system_cat_recents"
        "pref_key_various_cat_gboard" -> "pref_key_controls_cat_navbar"
        else -> sub?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns a [SearchRoute] for known categories, `null` for anything else.
     */
    fun resolve(category: String, sub: String?, key: String): SearchRoute? {
        if (category !in KNOWN_CATEGORIES) return null
        return SearchRoute(category, key, normalizeSub(sub))
    }
}
