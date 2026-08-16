package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.withaibuild.customiuizer.R

class PreferenceResourceResolverTest {

    @Test
    fun everyGeneratedCategoryResolvesToItsLazyResource() {
        val expected = listOf(
            Triple("pref_key_system", "pref_key_system_cat_screen", R.xml.prefs_system_screen),
            Triple("pref_key_system", "pref_key_system_cat_audio", R.xml.prefs_system_audio),
            Triple("pref_key_system", "pref_key_system_cat_vibration", R.xml.prefs_system_vibration),
            Triple("pref_key_system", "pref_key_system_cat_toasts", R.xml.prefs_system_toasts),
            Triple("pref_key_system", "pref_key_system_cat_statusbar", R.xml.prefs_system_statusbar),
            Triple("pref_key_system", "pref_key_system_cat_drawer", R.xml.prefs_system_drawer),
            Triple("pref_key_system", "pref_key_system_cat_notifications", R.xml.prefs_system_notifications),
            Triple("pref_key_system", "pref_key_system_cat_qs", R.xml.prefs_system_qs),
            Triple("pref_key_system", "pref_key_system_cat_recents", R.xml.prefs_system_recents),
            Triple("pref_key_system", "pref_key_system_cat_betterpopups", R.xml.prefs_system_betterpopups),
            Triple("pref_key_system", "pref_key_system_cat_floatingwindows", R.xml.prefs_system_floatingwindows),
            Triple("pref_key_system", "pref_key_system_cat_applock", R.xml.prefs_system_applock),
            Triple("pref_key_system", "pref_key_system_cat_lockscreen", R.xml.prefs_system_lockscreen),
            Triple("pref_key_system", "pref_key_system_cat_other", R.xml.prefs_system_other),
            Triple("pref_key_launcher", "pref_key_launcher_cat_folders", R.xml.prefs_launcher_folders),
            Triple("pref_key_launcher", "pref_key_launcher_cat_titles", R.xml.prefs_launcher_titles),
            Triple("pref_key_launcher", "pref_key_launcher_cat_privacyapps", R.xml.prefs_launcher_privacyapps),
            Triple("pref_key_launcher", "pref_key_launcher_cat_gestures", R.xml.prefs_launcher_gestures),
            Triple("pref_key_launcher", "pref_key_launcher_cat_bugfixes", R.xml.prefs_launcher_bugfixes),
            Triple("pref_key_launcher", "pref_key_launcher_cat_other", R.xml.prefs_launcher_other),
            Triple("pref_key_controls", "pref_key_controls_cat_fingerprint", R.xml.prefs_controls_fingerprint),
            Triple("pref_key_controls", "pref_key_controls_cat_power", R.xml.prefs_controls_power),
            Triple("pref_key_controls", "pref_key_controls_cat_volume", R.xml.prefs_controls_volume),
            Triple("pref_key_controls", "pref_key_controls_cat_navbar", R.xml.prefs_controls_navbar),
            Triple("pref_key_controls", "pref_key_controls_cat_fsg", R.xml.prefs_controls_fsg),
            Triple("pref_key_various", "pref_key_various_cat_general", R.xml.prefs_various_general),
            Triple("pref_key_various", "pref_key_various_cat_package_installer", R.xml.prefs_various_package_installer),
            Triple("pref_key_various", "pref_key_various_cat_security_center", R.xml.prefs_various_security_center),
            Triple("pref_key_various", "pref_key_various_cat_calls", R.xml.prefs_various_calls),
            Triple("pref_key_various", "pref_key_various_cat_settings", R.xml.prefs_various_settings),
            Triple("pref_key_various", "pref_key_various_cat_gboard", R.xml.prefs_various_gboard),
        )

        expected.forEach { (category, sub, resource) ->
            assertEquals("$category/$sub", resource, PreferenceResourceResolver.resolve(category, sub))
        }
    }

    @Test
    fun missingOrUnknownSubFallsBackToCanonicalFullResource() {
        val fullResources = mapOf(
            "pref_key_system" to R.xml.prefs_system,
            "pref_key_launcher" to R.xml.prefs_launcher,
            "pref_key_controls" to R.xml.prefs_controls,
            "pref_key_various" to R.xml.prefs_various,
        )
        fullResources.forEach { (category, resource) ->
            assertEquals(resource, PreferenceResourceResolver.resolve(category, null))
            assertEquals(resource, PreferenceResourceResolver.resolve(category, "pref_key_unknown"))
        }
    }

    @Test
    fun selectorResourcesExistForAllFourMainCategories() {
        assertEquals(R.xml.prefs_system_cat, PreferenceResourceResolver.categorySelector("pref_key_system"))
        assertEquals(R.xml.prefs_launcher_cat, PreferenceResourceResolver.categorySelector("pref_key_launcher"))
        assertEquals(R.xml.prefs_controls_cat, PreferenceResourceResolver.categorySelector("pref_key_controls"))
        assertEquals(R.xml.prefs_various_cat, PreferenceResourceResolver.categorySelector("pref_key_various"))
        assertNull(PreferenceResourceResolver.categorySelector("pref_key_unknown"))
    }
}
