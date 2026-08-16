package tv.withaibuild.customiuizer.utils

import tv.withaibuild.customiuizer.R

/**
 * Static page-level restart mask. No preference-key registry, no tree resolver,
 * no Set allocation. The mask is resolved once from [contentResId] and [sub].
 */
internal object RestartMask {
    const val NONE = 0
    const val LAUNCHER = 1
    const val SYSTEMUI = 1 shl 1
    const val SECURITY_CENTER = 1 shl 2
}

internal object RestartPagePolicy {

    fun maskFor(contentResId: Int, sub: String? = null): Int = when (contentResId) {
        R.xml.mod_search_index -> RestartMask.NONE
        R.xml.prefs_controls_cat -> RestartMask.NONE
        R.xml.prefs_controls_fingerprint -> RestartMask.NONE
        R.xml.prefs_controls_fsg -> RestartMask.LAUNCHER or RestartMask.SYSTEMUI
        R.xml.prefs_controls_navbar -> RestartMask.LAUNCHER or RestartMask.SYSTEMUI
        R.xml.prefs_controls_power -> RestartMask.NONE
        R.xml.prefs_controls_volume -> RestartMask.SYSTEMUI
        R.xml.prefs_launcher_bugfixes -> RestartMask.LAUNCHER
        R.xml.prefs_launcher_cat -> RestartMask.NONE
        R.xml.prefs_launcher_folders -> RestartMask.LAUNCHER
        R.xml.prefs_launcher_gestures -> RestartMask.LAUNCHER
        R.xml.prefs_launcher_other -> RestartMask.LAUNCHER
        R.xml.prefs_launcher_privacyapps -> RestartMask.LAUNCHER or RestartMask.SECURITY_CENTER
        R.xml.prefs_launcher_titles -> RestartMask.LAUNCHER
        R.xml.prefs_main -> RestartMask.NONE
        R.xml.prefs_system_alarmonlock -> RestartMask.SYSTEMUI
        R.xml.prefs_system_albumartonlock -> RestartMask.SYSTEMUI
        R.xml.prefs_system_applock -> RestartMask.SECURITY_CENTER
        R.xml.prefs_system_audio -> RestartMask.SYSTEMUI
        R.xml.prefs_system_autobrightness -> RestartMask.NONE
        R.xml.prefs_system_batteryindicator -> RestartMask.SYSTEMUI
        R.xml.prefs_system_betterpopups -> RestartMask.SYSTEMUI
        R.xml.prefs_system_cat -> RestartMask.NONE
        R.xml.prefs_system_charginginfo -> RestartMask.SYSTEMUI
        R.xml.prefs_system_controlcenter_clock -> RestartMask.SYSTEMUI
        R.xml.prefs_system_controlcenter_themestyle -> RestartMask.SYSTEMUI
        R.xml.prefs_system_detailednetspeed -> RestartMask.SYSTEMUI
        R.xml.prefs_system_drawer -> RestartMask.SYSTEMUI
        R.xml.prefs_system_floatingwindows -> RestartMask.LAUNCHER or RestartMask.SYSTEMUI
        R.xml.prefs_system_hideicons -> RestartMask.SYSTEMUI
        R.xml.prefs_system_lockscreen -> RestartMask.SYSTEMUI
        R.xml.prefs_system_lockscreenshortcuts -> RestartMask.SYSTEMUI
        R.xml.prefs_system_noscreenlock -> RestartMask.SYSTEMUI
        R.xml.prefs_system_notifications -> RestartMask.SYSTEMUI
        R.xml.prefs_system_other -> RestartMask.SYSTEMUI or RestartMask.SECURITY_CENTER
        R.xml.prefs_system_qs -> RestartMask.SYSTEMUI
        R.xml.prefs_system_recents -> RestartMask.LAUNCHER
        R.xml.prefs_system_screen -> RestartMask.SYSTEMUI
        R.xml.prefs_system_screenshot -> RestartMask.NONE
        R.xml.prefs_system_secureqs -> RestartMask.SYSTEMUI
        R.xml.prefs_system_statusbar -> RestartMask.SYSTEMUI
        R.xml.prefs_system_statusbar_batterystyle -> RestartMask.SYSTEMUI
        R.xml.prefs_system_statusbar_batterytempandcurrent -> RestartMask.SYSTEMUI
        R.xml.prefs_system_statusbar_clock -> RestartMask.SYSTEMUI
        R.xml.prefs_system_statusbar_mobilesignal -> RestartMask.SYSTEMUI
        R.xml.prefs_system_statusbar_righticons -> RestartMask.SYSTEMUI
        R.xml.prefs_system_statusbar_showdevicetemperature -> RestartMask.NONE
        R.xml.prefs_system_statusbarcontrols -> RestartMask.SYSTEMUI
        R.xml.prefs_system_toasts -> RestartMask.NONE
        R.xml.prefs_system_vibration -> RestartMask.SYSTEMUI
        R.xml.prefs_system_vibration_amp -> RestartMask.NONE
        R.xml.prefs_system_visualizer -> RestartMask.SYSTEMUI
        R.xml.prefs_various_calls -> RestartMask.SYSTEMUI
        R.xml.prefs_various_calluibright -> RestartMask.NONE
        R.xml.prefs_various_cat -> RestartMask.NONE
        R.xml.prefs_various_exclusive -> RestartMask.NONE
        R.xml.prefs_various_gboard -> RestartMask.NONE
        R.xml.prefs_various_general -> RestartMask.NONE
        R.xml.prefs_various_hiddenfeatures -> RestartMask.NONE
        R.xml.prefs_various_package_installer -> RestartMask.NONE
        R.xml.prefs_various_security_center -> RestartMask.SECURITY_CENTER
        R.xml.prefs_various_settings -> RestartMask.SECURITY_CENTER
        R.xml.prefs_system -> when (sub) {
            "pref_key_system_cat_applock" -> RestartMask.SECURITY_CENTER
            "pref_key_system_cat_audio" -> RestartMask.SYSTEMUI
            "pref_key_system_cat_betterpopups" -> RestartMask.SYSTEMUI
            "pref_key_system_cat_drawer" -> RestartMask.SYSTEMUI
            "pref_key_system_cat_floatingwindows" -> RestartMask.LAUNCHER or RestartMask.SYSTEMUI
            "pref_key_system_cat_lockscreen" -> RestartMask.SYSTEMUI
            "pref_key_system_cat_notifications" -> RestartMask.SYSTEMUI
            "pref_key_system_cat_other" -> RestartMask.SYSTEMUI or RestartMask.SECURITY_CENTER
            "pref_key_system_cat_qs" -> RestartMask.SYSTEMUI
            "pref_key_system_cat_recents" -> RestartMask.LAUNCHER
            "pref_key_system_cat_screen" -> RestartMask.SYSTEMUI
            "pref_key_system_cat_statusbar" -> RestartMask.SYSTEMUI
            "pref_key_system_cat_toasts" -> RestartMask.NONE
            "pref_key_system_cat_vibration" -> RestartMask.SYSTEMUI
            else -> RestartMask.NONE
        }
        R.xml.prefs_launcher -> when (sub) {
            "pref_key_launcher_cat_bugfixes" -> RestartMask.LAUNCHER
            "pref_key_launcher_cat_folders" -> RestartMask.LAUNCHER
            "pref_key_launcher_cat_gestures" -> RestartMask.LAUNCHER
            "pref_key_launcher_cat_other" -> RestartMask.LAUNCHER
            "pref_key_launcher_cat_privacyapps" -> RestartMask.LAUNCHER or RestartMask.SECURITY_CENTER
            "pref_key_launcher_cat_titles" -> RestartMask.LAUNCHER
            else -> RestartMask.NONE
        }
        R.xml.prefs_controls -> when (sub) {
            "pref_key_controls_cat_fingerprint" -> RestartMask.NONE
            "pref_key_controls_cat_fsg" -> RestartMask.LAUNCHER or RestartMask.SYSTEMUI
            "pref_key_controls_cat_navbar" -> RestartMask.LAUNCHER or RestartMask.SYSTEMUI
            "pref_key_controls_cat_power" -> RestartMask.NONE
            "pref_key_controls_cat_volume" -> RestartMask.SYSTEMUI
            else -> RestartMask.NONE
        }
        R.xml.prefs_various -> when (sub) {
            "pref_key_various_cat_calls" -> RestartMask.SYSTEMUI
            "pref_key_various_cat_exclusive" -> RestartMask.NONE
            "pref_key_various_cat_gboard" -> RestartMask.NONE
            "pref_key_various_cat_general" -> RestartMask.NONE
            "pref_key_various_cat_package_installer" -> RestartMask.NONE
            "pref_key_various_cat_security_center" -> RestartMask.SECURITY_CENTER
            "pref_key_various_cat_settings" -> RestartMask.SECURITY_CENTER
            else -> RestartMask.NONE
        }
        else -> RestartMask.NONE
    }

    /** Returns the mask bits currently set, as a human-readable list (for Toast). */
    fun toNameList(mask: Int): List<String> = buildList {
        if (mask and RestartMask.SECURITY_CENTER != 0) add("securitycenter")
        if (mask and RestartMask.LAUNCHER != 0) add("launcher")
        if (mask and RestartMask.SYSTEMUI != 0) add("systemui")
    }
}
