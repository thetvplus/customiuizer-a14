package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.withaibuild.customiuizer.R

class RestartPagePolicyTest {

    @Test
    fun systemUi_only_page() {
        assertEquals(RestartMask.SYSTEMUI, RestartPagePolicy.maskFor(R.xml.prefs_system_charginginfo))
    }

    @Test
    fun launcher_only_page() {
        assertEquals(RestartMask.LAUNCHER, RestartPagePolicy.maskFor(R.xml.prefs_launcher_folders))
    }

    @Test
    fun launcher_gestures_page_restarts_launcher_and_systemui() {
        val expected = RestartMask.LAUNCHER or RestartMask.SYSTEMUI
        assertEquals(expected, RestartPagePolicy.maskFor(R.xml.prefs_launcher_gestures))
        assertEquals(
            expected,
            RestartPagePolicy.maskFor(R.xml.prefs_launcher, "pref_key_launcher_cat_gestures")
        )
        assertEquals(listOf("launcher", "systemui"), RestartPagePolicy.toNameList(expected))
        assertEquals(0, expected and RestartMask.SECURITY_CENTER)
        assertEquals(
            expected,
            expected and (RestartMask.LAUNCHER or RestartMask.SYSTEMUI or RestartMask.SECURITY_CENTER)
        )
    }

    @Test
    fun other_launcher_category_pages_keep_previous_masks() {
        assertEquals(RestartMask.LAUNCHER, RestartPagePolicy.maskFor(R.xml.prefs_launcher_folders))
        assertEquals(RestartMask.LAUNCHER, RestartPagePolicy.maskFor(R.xml.prefs_launcher_titles))
        assertEquals(RestartMask.LAUNCHER, RestartPagePolicy.maskFor(R.xml.prefs_launcher_bugfixes))
        assertEquals(RestartMask.LAUNCHER, RestartPagePolicy.maskFor(R.xml.prefs_launcher_other))
        assertEquals(
            RestartMask.LAUNCHER or RestartMask.SECURITY_CENTER,
            RestartPagePolicy.maskFor(R.xml.prefs_launcher_privacyapps)
        )
        assertEquals(
            RestartMask.LAUNCHER,
            RestartPagePolicy.maskFor(R.xml.prefs_launcher, "pref_key_launcher_cat_folders")
        )
    }

    @Test
    fun securityCenter_only_page() {
        assertEquals(RestartMask.SECURITY_CENTER, RestartPagePolicy.maskFor(R.xml.prefs_various_security_center))
    }

    @Test
    fun multi_target_fsg_page() {
        assertEquals(
            RestartMask.LAUNCHER or RestartMask.SYSTEMUI,
            RestartPagePolicy.maskFor(R.xml.prefs_controls_fsg)
        )
    }

    @Test
    fun power_page_does_not_restart_system_server() {
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_controls_power))
        assertEquals(
            RestartMask.NONE,
            RestartPagePolicy.maskFor(R.xml.prefs_controls, "pref_key_controls_cat_power")
        )
    }

    @Test
    fun multi_target_navbar_page() {
        assertEquals(
            RestartMask.LAUNCHER or RestartMask.SYSTEMUI,
            RestartPagePolicy.maskFor(R.xml.prefs_controls_navbar)
        )
    }

    @Test
    fun pure_live_or_unsupported_page_is_none() {
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_system_autobrightness))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_system_screenshot))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_various_gboard))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_controls_fingerprint))
    }

    @Test
    fun root_navigation_cat_pages_are_none() {
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_system_cat))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_launcher_cat))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_controls_cat))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_various_cat))
    }

    @Test
    fun main_page_is_none() {
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_main))
    }

    @Test
    fun combined_source_respects_sub() {
        assertEquals(
            RestartMask.SYSTEMUI,
            RestartPagePolicy.maskFor(R.xml.prefs_system, "pref_key_system_cat_screen")
        )
        assertEquals(
            RestartMask.LAUNCHER or RestartMask.SYSTEMUI,
            RestartPagePolicy.maskFor(R.xml.prefs_controls, "pref_key_controls_cat_fsg")
        )
        assertEquals(
            RestartMask.LAUNCHER or RestartMask.SYSTEMUI,
            RestartPagePolicy.maskFor(R.xml.prefs_launcher, "pref_key_launcher_cat_gestures")
        )
        assertEquals(
            RestartMask.SECURITY_CENTER,
            RestartPagePolicy.maskFor(R.xml.prefs_various, "pref_key_various_cat_security_center")
        )
    }

    @Test
    fun unknown_resource_is_none() {
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(0))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(-1, "pref_key_unknown"))
    }

    @Test
    fun toNameList_order_and_filtering() {
        val list = RestartPagePolicy.toNameList(
            RestartMask.LAUNCHER or RestartMask.SYSTEMUI or RestartMask.SECURITY_CENTER
        )
        assertEquals(listOf("securitycenter", "launcher", "systemui"), list)
    }

    @Test
    fun strong_toast_and_usb_do_not_add_extra_bit() {
        // StrongToast / USB live-only keys are not in the P3 mask.
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_system_toasts))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_various_exclusive))
    }

    @Test
    fun shared_resources_fail_closed_for_missing_or_unknown_sub() {
        // Unknown or missing shared-resource sub identity must fail closed to NONE,
        // not fall back to a module-wide conservative union.
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_system, null))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_system, "pref_key_unknown"))

        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_launcher, null))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_launcher, "pref_key_unknown"))

        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_controls, null))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_controls, "pref_key_unknown"))

        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_various, null))
        assertEquals(RestartMask.NONE, RestartPagePolicy.maskFor(R.xml.prefs_various, "pref_key_unknown"))
    }

    @Test
    fun known_shared_sub_mapping_is_preserved() {
        assertEquals(
            RestartMask.SYSTEMUI,
            RestartPagePolicy.maskFor(R.xml.prefs_system, "pref_key_system_cat_screen")
        )
        assertEquals(
            RestartMask.SYSTEMUI or RestartMask.SECURITY_CENTER,
            RestartPagePolicy.maskFor(R.xml.prefs_system, "pref_key_system_cat_other")
        )
        assertEquals(
            RestartMask.LAUNCHER or RestartMask.SYSTEMUI,
            RestartPagePolicy.maskFor(R.xml.prefs_controls, "pref_key_controls_cat_fsg")
        )
        assertEquals(
            RestartMask.LAUNCHER or RestartMask.SYSTEMUI,
            RestartPagePolicy.maskFor(R.xml.prefs_launcher, "pref_key_launcher_cat_gestures")
        )
        assertEquals(
            RestartMask.SECURITY_CENTER,
            RestartPagePolicy.maskFor(R.xml.prefs_various, "pref_key_various_cat_security_center")
        )
    }
}
