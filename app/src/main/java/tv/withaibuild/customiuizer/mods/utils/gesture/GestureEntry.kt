package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Identifies which SystemUI entry point produced a gesture event.
 *
 * Each entry has a distinct owner; events from different entries are never assumed to be the
 * same physical event even if their timestamps coincide.
 */
enum class GestureEntry {
    STATUS_BAR_INTERCEPT,
    STATUS_BAR_TOUCH,
    CONTROL_CENTER_TOUCH,
}
