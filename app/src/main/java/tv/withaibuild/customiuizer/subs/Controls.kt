package tv.withaibuild.customiuizer.subs

import android.os.Bundle
import androidx.preference.Preference
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper

class Controls : SubFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        selectSub()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        when (sub) {
            "pref_key_controls_cat_power" -> findPreference<Preference>("pref_key_controls_powerdt")?.setOnPreferenceClickListener(openLaunchActions)
            "pref_key_controls_cat_volume" -> {
                findPreference<Preference>("pref_key_controls_volumecursor_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_controls_mediaplayer_apps")?.setOnPreferenceClickListener(openAppsEdit)
            }
            "pref_key_controls_cat_navbar" -> {
                val hideNavigation = findPreference<CheckBoxPreferenceEx>("pref_key_controls_nonavbar")
                val keyboardFix = findPreference<Preference>("pref_key_controls_nonavbar_fix_inputmethod")
                // Other dependents need a visible bar; this fix requires the opposite state.
                keyboardFix?.isEnabled = hideNavigation?.isChecked == true
                hideNavigation?.setOnPreferenceChangeListener { _, value ->
                    keyboardFix?.isEnabled = value == true
                    true
                }
                findPreference<Preference>("pref_key_controls_backlong")?.setOnPreferenceClickListener(openNavbarActions)
                findPreference<Preference>("pref_key_controls_homelong")?.setOnPreferenceClickListener(openNavbarActions)
                findPreference<Preference>("pref_key_controls_menulong")?.setOnPreferenceClickListener(openNavbarActions)

                findPreference<Preference>("pref_key_controls_navbarleft")?.setOnPreferenceClickListener(openNavbarActions)
                findPreference<Preference>("pref_key_controls_navbarleftlong")?.setOnPreferenceClickListener(openNavbarActions)
                findPreference<Preference>("pref_key_controls_navbarright")?.setOnPreferenceClickListener(openNavbarActions)
                findPreference<Preference>("pref_key_controls_navbarrightlong")?.setOnPreferenceClickListener(openNavbarActions)
            }
            "pref_key_controls_cat_fingerprint" -> {
                findPreference<Preference>("pref_key_controls_fingerprintsuccess_ignore")?.isEnabled = AppHelper.getStringOfAppPrefs("pref_key_controls_fingerprintsuccess", "1") != "1"
                findPreference<Preference>("pref_key_controls_fingerprintsuccess")?.setOnPreferenceChangeListener { preference, newValue ->
                    val selected = syncPendingListSelection(preference, newValue)
                    findPreference<Preference>("pref_key_controls_fingerprintsuccess_ignore")?.isEnabled = selected != "1"
                    true
                }
            }
            "pref_key_controls_cat_fsg" -> {
                findPreference<Preference>("pref_key_controls_fsg_horiz_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_controls_fsg_assist_left")?.setOnPreferenceClickListener(openNavbarActions)
                findPreference<Preference>("pref_key_controls_fsg_assist_right")?.setOnPreferenceClickListener(openNavbarActions)
                findPreference<Preference>("pref_key_controls_fsg_swipeandstop")?.setOnPreferenceClickListener(openNavbarActions)
                val enableSwipeAndStop = AppHelper.getIntOfAppPrefs("pref_key_controls_fsg_swipeandstop_action", 1) > 1
                findPreference<Preference>("pref_key_controls_fsg_swipeandstop_disablevibrate")?.isEnabled = enableSwipeAndStop
            }
        }
    }
}
