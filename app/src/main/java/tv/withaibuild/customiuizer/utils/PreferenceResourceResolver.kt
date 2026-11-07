package tv.withaibuild.customiuizer.utils

import tv.withaibuild.customiuizer.R

/** Resolves main setting categories to generated selector and lazy page resources. */
internal object PreferenceResourceResolver {

    fun categorySelector(category: String): Int? = when (category) {
        "pref_key_system" -> R.id.prefs_system_cat
        "pref_key_launcher" -> R.id.prefs_launcher_cat
        "pref_key_controls" -> R.id.prefs_controls_cat
        "pref_key_various" -> R.id.prefs_various_cat
        else -> null
    }

    fun resolve(category: String, sub: String?): Int = when (category) {
        "pref_key_system" -> resolveSystem(SearchRouteResolver.normalizeSub(sub))
        "pref_key_launcher" -> resolveLauncher(sub)
        "pref_key_controls" -> resolveControls(sub)
        "pref_key_various" -> resolveVarious(sub)
        else -> 0
    }

    private fun resolveSystem(sub: String?): Int = when (sub) {
        "pref_key_system_cat_screen" -> R.id.prefs_system_screen
        "pref_key_system_cat_automation" -> R.id.prefs_system_automation
        "pref_key_system_cat_audio" -> R.id.prefs_system_audio
        "pref_key_system_cat_vibration" -> R.id.prefs_system_vibration
        "pref_key_system_cat_toasts" -> R.id.prefs_system_toasts
        "pref_key_system_cat_statusbar" -> R.id.prefs_system_statusbar
        "pref_key_system_cat_island" -> R.id.prefs_system_island
        "pref_key_system_cat_drawer" -> R.id.prefs_system_drawer
        "pref_key_system_cat_notifications" -> R.id.prefs_system_notifications
        "pref_key_system_cat_qs" -> R.id.prefs_system_qs
        "pref_key_system_cat_recents" -> R.id.prefs_system_recents
        "pref_key_system_cat_betterpopups" -> R.id.prefs_system_betterpopups
        "pref_key_system_cat_floatingwindows" -> R.id.prefs_system_floatingwindows
        "pref_key_system_cat_applock" -> R.id.prefs_system_applock
        "pref_key_system_cat_lockscreen" -> R.id.prefs_system_lockscreen
        "pref_key_system_cat_other" -> R.id.prefs_system_other
        else -> R.id.prefs_system
    }

    private fun resolveLauncher(sub: String?): Int = when (sub) {
        "pref_key_launcher_cat_folders" -> R.id.prefs_launcher_folders
        "pref_key_launcher_cat_titles" -> R.id.prefs_launcher_titles
        "pref_key_launcher_cat_privacyapps" -> R.id.prefs_launcher_privacyapps
        "pref_key_launcher_cat_gestures" -> R.id.prefs_launcher_gestures
        "pref_key_launcher_cat_bugfixes" -> R.id.prefs_launcher_bugfixes
        "pref_key_launcher_cat_dock" -> R.id.prefs_launcher_dock
        "pref_key_launcher_cat_other" -> R.id.prefs_launcher_other
        else -> R.id.prefs_launcher
    }

    private fun resolveControls(sub: String?): Int = when (sub) {
        "pref_key_controls_cat_fingerprint" -> R.id.prefs_controls_fingerprint
        "pref_key_controls_cat_power" -> R.id.prefs_controls_power
        "pref_key_controls_cat_volume" -> R.id.prefs_controls_volume
        "pref_key_controls_cat_navbar" -> R.id.prefs_controls_navbar
        "pref_key_controls_cat_fsg" -> R.id.prefs_controls_fsg
        else -> R.id.prefs_controls
    }

    private fun resolveVarious(sub: String?): Int = when (sub) {
        "pref_key_various_cat_general" -> R.id.prefs_various_general
        "pref_key_various_cat_package_installer" -> R.id.prefs_various_package_installer
        "pref_key_various_cat_security_center" -> R.id.prefs_various_security_center
        "pref_key_various_cat_calls" -> R.id.prefs_various_calls
        "pref_key_various_cat_settings" -> R.id.prefs_various_settings
        "pref_key_various_cat_exclusive" -> R.id.prefs_various_exclusive
        "pref_key_various_cat_gboard" -> R.id.prefs_various_gboard
        else -> R.id.prefs_various
    }
}
