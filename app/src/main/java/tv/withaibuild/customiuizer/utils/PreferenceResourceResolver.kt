package tv.withaibuild.customiuizer.utils

import tv.withaibuild.customiuizer.R

/** Resolves main setting categories to generated selector and lazy page resources. */
internal object PreferenceResourceResolver {

    fun categorySelector(category: String): Int? = when (category) {
        "pref_key_system" -> R.xml.prefs_system_cat
        "pref_key_launcher" -> R.xml.prefs_launcher_cat
        "pref_key_controls" -> R.xml.prefs_controls_cat
        "pref_key_various" -> R.xml.prefs_various_cat
        else -> null
    }

    fun resolve(category: String, sub: String?): Int = when (category) {
        "pref_key_system" -> resolveSystem(sub)
        "pref_key_launcher" -> resolveLauncher(sub)
        "pref_key_controls" -> resolveControls(sub)
        "pref_key_various" -> resolveVarious(sub)
        else -> 0
    }

    private fun resolveSystem(sub: String?): Int = when (sub) {
        "pref_key_system_cat_screen" -> R.xml.prefs_system_screen
        "pref_key_system_cat_audio" -> R.xml.prefs_system_audio
        "pref_key_system_cat_vibration" -> R.xml.prefs_system_vibration
        "pref_key_system_cat_toasts" -> R.xml.prefs_system_toasts
        "pref_key_system_cat_statusbar" -> R.xml.prefs_system_statusbar
        "pref_key_system_cat_drawer" -> R.xml.prefs_system_drawer
        "pref_key_system_cat_notifications" -> R.xml.prefs_system_notifications
        "pref_key_system_cat_qs" -> R.xml.prefs_system_qs
        "pref_key_system_cat_recents" -> R.xml.prefs_system_recents
        "pref_key_system_cat_betterpopups" -> R.xml.prefs_system_betterpopups
        "pref_key_system_cat_floatingwindows" -> R.xml.prefs_system_floatingwindows
        "pref_key_system_cat_applock" -> R.xml.prefs_system_applock
        "pref_key_system_cat_lockscreen" -> R.xml.prefs_system_lockscreen
        "pref_key_system_cat_other" -> R.xml.prefs_system_other
        else -> R.xml.prefs_system
    }

    private fun resolveLauncher(sub: String?): Int = when (sub) {
        "pref_key_launcher_cat_folders" -> R.xml.prefs_launcher_folders
        "pref_key_launcher_cat_titles" -> R.xml.prefs_launcher_titles
        "pref_key_launcher_cat_privacyapps" -> R.xml.prefs_launcher_privacyapps
        "pref_key_launcher_cat_gestures" -> R.xml.prefs_launcher_gestures
        "pref_key_launcher_cat_bugfixes" -> R.xml.prefs_launcher_bugfixes
        "pref_key_launcher_cat_other" -> R.xml.prefs_launcher_other
        else -> R.xml.prefs_launcher
    }

    private fun resolveControls(sub: String?): Int = when (sub) {
        "pref_key_controls_cat_fingerprint" -> R.xml.prefs_controls_fingerprint
        "pref_key_controls_cat_power" -> R.xml.prefs_controls_power
        "pref_key_controls_cat_volume" -> R.xml.prefs_controls_volume
        "pref_key_controls_cat_navbar" -> R.xml.prefs_controls_navbar
        "pref_key_controls_cat_fsg" -> R.xml.prefs_controls_fsg
        else -> R.xml.prefs_controls
    }

    private fun resolveVarious(sub: String?): Int = when (sub) {
        "pref_key_various_cat_general" -> R.xml.prefs_various_general
        "pref_key_various_cat_package_installer" -> R.xml.prefs_various_package_installer
        "pref_key_various_cat_security_center" -> R.xml.prefs_various_security_center
        "pref_key_various_cat_calls" -> R.xml.prefs_various_calls
        "pref_key_various_cat_settings" -> R.xml.prefs_various_settings
        "pref_key_various_cat_exclusive" -> R.xml.prefs_various_exclusive
        "pref_key_various_cat_gboard" -> R.xml.prefs_various_gboard
        else -> R.xml.prefs_various
    }
}
