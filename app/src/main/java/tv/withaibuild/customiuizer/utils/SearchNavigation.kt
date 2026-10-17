package tv.withaibuild.customiuizer.utils

/**
 * Pure route resolver and state constants for search navigation.
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
    fun normalizeSub(sub: String?): String? = sub?.takeIf { it.isNotBlank() }

    /**
     * Returns a [SearchRoute] for known categories, `null` for anything else.
     */
    fun resolve(category: String, sub: String?, key: String): SearchRoute? {
        if (category !in KNOWN_CATEGORIES) return null
        return SearchRoute(category, key, normalizeSub(sub))
    }
}

object SearchStateMachine {

    const val STATE_IDLE = 0
    const val STATE_SEARCHING = 1
    const val STATE_NAVIGATED = 2

    fun canFilter(state: Int): Boolean = state != STATE_NAVIGATED

    fun shouldClearOnReturn(state: Int): Boolean = state == STATE_NAVIGATED

    fun transitionOnQuery(state: Int, query: String?): Int = when {
        state == STATE_NAVIGATED -> STATE_NAVIGATED
        query.isNullOrEmpty() -> state
        else -> STATE_SEARCHING
    }

    fun transitionOnSelect(state: Int, success: Boolean): Int =
        if (success) STATE_NAVIGATED else state

    fun transitionOnReturn(state: Int): Int =
        if (state == STATE_NAVIGATED) STATE_IDLE else state
}
