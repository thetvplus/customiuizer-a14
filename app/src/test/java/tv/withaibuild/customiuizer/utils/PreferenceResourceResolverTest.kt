package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.withaibuild.customiuizer.R

class PreferenceResourceResolverTest {
    @Test
    fun movedPagesKeepTheirOriginalControllersAndFreshInstances() {
        for ((category, sub, controller, resource) in listOf(
            listOf("pref_key_system", "pref_key_system_cat_statusbar", "System", R.id.prefs_system_statusbar),
            listOf("pref_key_system", "pref_key_system_cat_drawer", "System", R.id.prefs_system_drawer),
            listOf("pref_key_controls", "pref_key_system_cat_audio", "System", R.id.prefs_system_audio),
            listOf("pref_key_launcher", "pref_key_system_cat_recents", "System", R.id.prefs_system_recents),
            listOf("pref_key_various", "pref_key_system_cat_automation", "System", R.id.prefs_system_automation),
            listOf("pref_key_various", "pref_key_system_appbehavior_cat", "System_AppBehavior", R.id.prefs_system_appbehavior),
            listOf("pref_key_various", "pref_key_system_wifiauto_cat", "System_WifiAutomatic", R.id.prefs_system_wifiauto),
            listOf("pref_key_various", "pref_key_system_vpnauto_cat", "System_VpnAutoSwitch", R.id.prefs_system_vpnauto),
            listOf("pref_key_various", "pref_key_various_cat_gboard", "Controls", R.id.prefs_controls_navbar),
            listOf("pref_key_system", "pref_key_system_cat_appearance", "System", R.id.prefs_system_appearance),
            listOf("pref_key_system", "pref_key_system_cat_capture", "System", R.id.prefs_system_capture),
        )) {
            val first = PreferenceResourceResolver.createFragment(category as String, sub as String)
            assertEquals(controller, first?.javaClass?.simpleName)
            org.junit.Assert.assertNotSame(first, PreferenceResourceResolver.createFragment(category, sub))
            assertEquals(resource, PreferenceResourceResolver.resolve(category, sub))
        }
    }

    @Test fun directStandalonePagesCarryTheirPageKindWithoutChangingCategoryRoutes() {
        for (key in listOf("pref_key_system_appbehavior_cat", "pref_key_system_wifiauto_cat", "pref_key_system_vpnauto_cat"))
            org.junit.Assert.assertTrue(PreferenceResourceResolver.isStandalonePage(key))
        for (key in listOf(null, "pref_key_system_cat_automation", "pref_key_system_cat_audio",
                "pref_key_controls_cat_navbar", "unknown"))
            org.junit.Assert.assertFalse(PreferenceResourceResolver.isStandalonePage(key))
    }

    @Test
    fun previousIosCategoryLinksOpenTheirCurrentOwningPages() {
        assertEquals(R.id.prefs_system_audio,
            PreferenceResourceResolver.resolve("pref_key_system", "pref_key_system_cat_ios_volume"))
        assertEquals(R.id.prefs_system_recents,
            PreferenceResourceResolver.resolve("pref_key_system", "pref_key_system_cat_ios_desktop"))
    }

    @Test
    fun everyGeneratedCategoryResolvesToItsLazyResource() {
        val expected = listOf(
            Triple("pref_key_system", "pref_key_system_cat_screen", R.id.prefs_system_screen),
            Triple("pref_key_system", "pref_key_system_cat_automation", R.id.prefs_system_automation),
            Triple("pref_key_system", "pref_key_system_cat_audio", R.id.prefs_system_audio),
            Triple("pref_key_system", "pref_key_system_cat_vibration", R.id.prefs_system_vibration),
            Triple("pref_key_system", "pref_key_system_cat_toasts", R.id.prefs_system_toasts),
            Triple("pref_key_system", "pref_key_system_cat_statusbar", R.id.prefs_system_statusbar),
            Triple("pref_key_system", "pref_key_system_cat_drawer", R.id.prefs_system_drawer),
            Triple("pref_key_system", "pref_key_system_cat_notifications", R.id.prefs_system_notifications),
            Triple("pref_key_system", "pref_key_system_cat_qs", R.id.prefs_system_qs),
            Triple("pref_key_system", "pref_key_system_cat_recents", R.id.prefs_system_recents),
            Triple("pref_key_system", "pref_key_system_cat_betterpopups", R.id.prefs_system_betterpopups),
            Triple("pref_key_system", "pref_key_system_cat_floatingwindows", R.id.prefs_system_floatingwindows),
            Triple("pref_key_system", "pref_key_system_cat_applock", R.id.prefs_system_applock),
            Triple("pref_key_system", "pref_key_system_cat_lockscreen", R.id.prefs_system_lockscreen),
            Triple("pref_key_system", "pref_key_system_cat_other", R.id.prefs_system_other),
            Triple("pref_key_launcher", "pref_key_launcher_cat_folders", R.id.prefs_launcher_folders),
            Triple("pref_key_launcher", "pref_key_launcher_cat_titles", R.id.prefs_launcher_titles),
            Triple("pref_key_launcher", "pref_key_launcher_cat_privacyapps", R.id.prefs_launcher_privacyapps),
            Triple("pref_key_launcher", "pref_key_launcher_cat_gestures", R.id.prefs_launcher_gestures),
            Triple("pref_key_launcher", "pref_key_launcher_cat_bugfixes", R.id.prefs_launcher_bugfixes),
            Triple("pref_key_launcher", "pref_key_launcher_cat_other", R.id.prefs_launcher_other),
            Triple("pref_key_controls", "pref_key_controls_cat_fingerprint", R.id.prefs_controls_fingerprint),
            Triple("pref_key_controls", "pref_key_controls_cat_power", R.id.prefs_controls_power),
            Triple("pref_key_controls", "pref_key_controls_cat_volume", R.id.prefs_controls_volume),
            Triple("pref_key_controls", "pref_key_controls_cat_navbar", R.id.prefs_controls_navbar),
            Triple("pref_key_controls", "pref_key_controls_cat_fsg", R.id.prefs_controls_fsg),
            Triple("pref_key_various", "pref_key_various_cat_general", R.id.prefs_various_general),
            Triple("pref_key_various", "pref_key_various_cat_package_installer", R.id.prefs_various_package_installer),
            Triple("pref_key_various", "pref_key_various_cat_security_center", R.id.prefs_various_security_center),
            Triple("pref_key_various", "pref_key_various_cat_calls", R.id.prefs_various_calls),
            Triple("pref_key_various", "pref_key_various_cat_settings", R.id.prefs_various_settings),
            Triple("pref_key_various", "pref_key_various_cat_gboard", R.id.prefs_controls_navbar),
        )

        expected.forEach { (category, sub, resource) ->
            assertEquals("$category/$sub", resource, PreferenceResourceResolver.resolve(category, sub))
        }
    }

    @Test
    fun missingOrUnknownSubFallsBackToCanonicalFullResource() {
        val fullResources = mapOf(
            "pref_key_system" to R.id.prefs_system,
            "pref_key_launcher" to R.id.prefs_launcher,
            "pref_key_controls" to R.id.prefs_controls,
            "pref_key_various" to R.id.prefs_various,
        )
        fullResources.forEach { (category, resource) ->
            assertEquals(resource, PreferenceResourceResolver.resolve(category, null))
            assertEquals(resource, PreferenceResourceResolver.resolve(category, "pref_key_unknown"))
        }
    }

    @Test
    fun selectorResourcesExistForAllFourMainCategories() {
        assertEquals(R.id.prefs_system_cat, PreferenceResourceResolver.categorySelector("pref_key_system"))
        assertEquals(R.id.prefs_launcher_cat, PreferenceResourceResolver.categorySelector("pref_key_launcher"))
        assertEquals(R.id.prefs_controls_cat, PreferenceResourceResolver.categorySelector("pref_key_controls"))
        assertEquals(R.id.prefs_various_cat, PreferenceResourceResolver.categorySelector("pref_key_various"))
        assertNull(PreferenceResourceResolver.categorySelector("pref_key_unknown"))
    }
}
