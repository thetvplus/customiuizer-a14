package tv.withaibuild.customiuizer.subs

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.mods.locationauto.LocationAutomationOptions
import tv.withaibuild.customiuizer.utils.AppHelper

class System_LocationAutomation : SubFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefix = "pref_key_system_locationauto_"
        val close = findPreference<Preference>(prefix + "close")
        val delay = findPreference<Preference>(prefix + "close_delay")
        var mode = AppHelper.getStringOfAppPrefs(prefix + "mode", LocationAutomationOptions.ALLOW)
        var autoClose = AppHelper.getBooleanOfAppPrefs(prefix + "close", false)
        fun refresh() {
            val allow = mode == LocationAutomationOptions.ALLOW
            close?.isVisible = allow
            delay?.isVisible = mode == LocationAutomationOptions.BLOCK || allow && autoClose
        }
        findPreference<Preference>(prefix + "apps")?.onPreferenceClickListener = openAppsEdit
        findPreference<Preference>(prefix + "mode")?.setOnPreferenceChangeListener { preference, value ->
            mode = syncPendingListSelection(preference, value)
            refresh()
            true
        }
        close?.setOnPreferenceChangeListener { _, value ->
            autoClose = value == true
            refresh()
            true
        }
        refresh()
    }
}
