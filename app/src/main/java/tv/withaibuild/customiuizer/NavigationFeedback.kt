package tv.withaibuild.customiuizer

/**
 * Navigation press-feedback cleanup is shared by Preference pages and Edit pages.
 * Only PreferenceFragmentCompat initializes a RecyclerView list. Edit pages are a
 * legal state with no list; that must skip list cleanup instead of crashing.
 */
object NavigationFeedback {

    data class Plan(
        val clearFragmentRoot: Boolean,
        val cleanPreferenceList: Boolean,
    )

    @JvmStatic
    fun plan(hasFragmentView: Boolean, hasPreferenceList: Boolean): Plan {
        if (!hasFragmentView) {
            return Plan(clearFragmentRoot = false, cleanPreferenceList = false)
        }
        return Plan(
            clearFragmentRoot = true,
            cleanPreferenceList = hasPreferenceList,
        )
    }
}
