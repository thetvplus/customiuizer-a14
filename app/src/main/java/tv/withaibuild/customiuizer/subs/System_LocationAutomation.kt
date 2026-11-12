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
        findPreference<Preference>(prefix + "apps")?.onPreferenceClickListener = openAppsEdit
        findPreference<Preference>(prefix + "mode")?.setOnPreferenceChangeListener { preference, value ->
            val mode = syncPendingListSelection(preference, value)
            close?.isVisible = mode == LocationAutomationOptions.ALLOW
            true
        }
        close?.isVisible = AppHelper.getStringOfAppPrefs(prefix + "mode", LocationAutomationOptions.ALLOW) ==
            LocationAutomationOptions.ALLOW
    }
}
