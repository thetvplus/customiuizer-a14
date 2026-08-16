package tv.withaibuild.customiuizer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

import tv.withaibuild.customiuizer.utils.AppHelper

/**
 * Tests the SubFragment menu-capability policy and the structural contract that
 * all ordinary preference pages are routed through [SubFragment] with
 * [AppHelper.SettingsType.Preference] / [AppHelper.ActionBarType.HomeUp].
 *
 * This is an instrumentation-free structural contract test: it only loads class
 * metadata (no Android runtime, no lifecycle).
 */
class SubFragmentMenuPolicyTest {

    @Test
    fun preference_pages_enable_toolbar() {
        assertTrue(shouldEnablePreferenceToolbar(AppHelper.SettingsType.Preference, false))
    }

    @Test
    fun edit_pages_without_custom_action_bar_do_not_enable_toolbar() {
        assertFalse(shouldEnablePreferenceToolbar(AppHelper.SettingsType.Edit, false))
    }

    @Test
    fun edit_pages_with_custom_action_bar_enable_toolbar() {
        assertTrue(shouldEnablePreferenceToolbar(AppHelper.SettingsType.Edit, true))
    }

    @Test
    fun main_fragment_is_not_a_sub_fragment() {
        val main = loadClass("tv.withaibuild.customiuizer.MainFragment")
        assertFalse(SubFragment::class.java.isAssignableFrom(main))
    }

    @Test
    fun all_secondary_preference_pages_are_sub_fragments() {
        for (name in PREFERENCE_PAGE_CLASSES) {
            val cls = loadClass(name)
            assertTrue(
                "$name must extend SubFragment to inherit the central menu policy",
                SubFragment::class.java.isAssignableFrom(cls)
            )
        }
    }

    @Test
    fun bare_sub_fragment_class_is_the_base_for_standalone_pages() {
        val sub = loadClass("tv.withaibuild.customiuizer.SubFragment")
        assertSame(SubFragment::class.java, sub)
    }

    @Test
    fun edit_pages_do_not_enable_matched_restart_toolbar() {
        // AppSelector and SortableList are opened with Edit + HomeUp.
        for (name in EDIT_PAGE_CLASSES) {
            val cls = loadClass(name)
            assertTrue(
                "$name must extend SubFragment",
                SubFragment::class.java.isAssignableFrom(cls)
            )
        }
        assertFalse(shouldEnablePreferenceToolbar(AppHelper.SettingsType.Edit, false))
    }

    private fun loadClass(name: String): Class<*> {
        return Class.forName(name, false, javaClass.classLoader)
    }

    companion object {
        private val PREFERENCE_PAGE_CLASSES = listOf(
            "tv.withaibuild.customiuizer.subs.Launcher",
            "tv.withaibuild.customiuizer.subs.Controls",
            "tv.withaibuild.customiuizer.subs.Various",
            "tv.withaibuild.customiuizer.subs.System",
            "tv.withaibuild.customiuizer.subs.CategorySelector",
            "tv.withaibuild.customiuizer.subs.System_Visualizer",
            "tv.withaibuild.customiuizer.subs.System_BatteryIndicator",
            "tv.withaibuild.customiuizer.subs.System_NoScreenLock",
            "tv.withaibuild.customiuizer.subs.System_AutoBrightness",
            "tv.withaibuild.customiuizer.subs.System_ScreenshotConfig",
        )

        private val EDIT_PAGE_CLASSES = listOf(
            "tv.withaibuild.customiuizer.subs.AppSelector",
            "tv.withaibuild.customiuizer.subs.SortableList",
        )
    }
}
