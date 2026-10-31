package tv.withaibuild.customiuizer.subs

import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.preference.Preference
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers

class System_NoScreenLock : SubFragment() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val noScreenLock = AppHelper.getStringOfAppPrefs("pref_key_system_noscreenlock", "1")
        findPreference<Preference>("pref_key_system_noscreenlock_wifi")?.isEnabled = noScreenLock == "3"
        findPreference<Preference>("pref_key_system_noscreenlock_bt")?.isEnabled = noScreenLock == "3"
        findPreference<Preference>("pref_key_system_noscreenlock")?.setOnPreferenceChangeListener { preference, newValue ->
            val selected = syncPendingListSelection(preference, newValue)
            findPreference<Preference>("pref_key_system_noscreenlock_wifi")?.isEnabled = selected == "3"
            findPreference<Preference>("pref_key_system_noscreenlock_bt")?.isEnabled = selected == "3"
            true
        }

        findPreference<Preference>("pref_key_system_noscreenlock_wifi")?.setOnPreferenceClickListener {
            if (requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), Helpers.REQUEST_PERMISSIONS_WIFI)
                return@setOnPreferenceClickListener true
            }
            openWifiNetworks()
            true
        }

        findPreference<Preference>("pref_key_system_noscreenlock_bt")?.setOnPreferenceClickListener {
            if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), Helpers.REQUEST_PERMISSIONS_BLUETOOTH)
                return@setOnPreferenceClickListener true
            }
            openBtNetworks()
            true
        }

        if (Helpers.isDeviceEncrypted(context)) {
            val req = findPreference<ListPreferenceEx>("pref_key_system_noscreenlock_req")
            req?.setValue("3")
            req?.isEnabled = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!isAdded || parentFragmentManager.isStateSaved) return
        val permission = when (requestCode) {
            Helpers.REQUEST_PERMISSIONS_WIFI -> Manifest.permission.ACCESS_FINE_LOCATION
            Helpers.REQUEST_PERMISSIONS_BLUETOOTH -> Manifest.permission.BLUETOOTH_CONNECT
            else -> return
        }
        if (requireContext().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == Helpers.REQUEST_PERMISSIONS_WIFI) openWifiNetworks() else openBtNetworks()
        } else android.widget.Toast.makeText(requireContext(), R.string.permission_scan, android.widget.Toast.LENGTH_LONG).show()
    }

    fun openWifiNetworks() {
        val args = Bundle().apply { putString("key", "pref_key_system_noscreenlock_wifi") }
        openSubFragment(WiFiList(), args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.wifi_networks, R.id.prefs_wifi_networks)
    }

    fun openBtNetworks() {
        val args = Bundle().apply { putString("key", "pref_key_system_noscreenlock_bt") }
        openSubFragment(BTList(), args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.bt_devices, R.id.prefs_bt_networks)
    }
}
