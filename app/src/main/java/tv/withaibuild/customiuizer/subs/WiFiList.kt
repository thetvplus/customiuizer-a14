package tv.withaibuild.customiuizer.subs

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import tv.withaibuild.customiuizer.ui.NetworkSelectionRow
import tv.withaibuild.customiuizer.ui.SettingsNetworkList
import tv.withaibuild.customiuizer.ui.settingsView
import android.widget.Toast
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.utils.AppHelper

class WiFiList : SubFragment() {

    private var key: String? = null
    private var rows by mutableStateOf<List<NetworkSelectionRow>>(emptyList())
    private var loading by mutableStateOf(false)
    private lateinit var wifiManager: WifiManager
    private var wifiList: List<ScanResult> = emptyList()
    private var bssids = LinkedHashSet<String>()

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private val scanTimeout = Runnable { loading = false }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!receiverRegistered) return
            when (intent.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    stopWaiting()
                    readResults()
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> refreshRows()
                WifiManager.WIFI_STATE_CHANGED_ACTION, LocationManager.MODE_CHANGED_ACTION -> {
                    readResults()
                    if (!canScan()) stopWaiting()
                    else if (!hasRecentResults()) requestScan()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        key = requireArguments().getString("key")
        bssids = LinkedHashSet(AppHelper.getStringSetOfAppPrefs(key, LinkedHashSet()))
        wifiManager = requireContext().applicationContext.getSystemService(WifiManager::class.java)
    }

    private fun canScan(): Boolean = wifiManager.isWifiEnabled &&
        requireContext().getSystemService(LocationManager::class.java).isLocationEnabled &&
        requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasRecentResults(): Boolean {
        val newest = wifiList.maxOfOrNull { it.timestamp } ?: return false
        return SystemClock.elapsedRealtime() - newest / 1000 in 0L..30_000L
    }

    @SuppressLint("MissingPermission") // A14 results require precise location, checked above.
    private fun readResults() {
        wifiList = if (canScan()) {
            try { wifiManager.scanResults } catch (_: SecurityException) { emptyList() }
        } else emptyList()
        refreshRows()
    }

    private fun stopWaiting() {
        handler.removeCallbacks(scanTimeout)
        loading = false
    }

    @SuppressLint("MissingPermission")
    private fun requestScan() {
        if (!receiverRegistered || loading) return
        if (!canScan()) {
            Toast.makeText(requireContext(), if (wifiManager.isWifiEnabled) R.string.request_location else R.string.request_wifi,
                Toast.LENGTH_LONG).show()
            return
        }
        // A14 has no public replacement for an on-demand scan; other updates are passive.
        @Suppress("DEPRECATION")
        val accepted = try { wifiManager.startScan() } catch (_: SecurityException) { false }
        loading = accepted
        if (accepted) handler.postDelayed(scanTimeout, 12_000L)
        else Toast.makeText(requireContext(), R.string.wifi_scan_unavailable, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        if (receiverRegistered) {
            receiverRegistered = false
            requireContext().unregisterReceiver(wifiReceiver)
        }
        stopWaiting()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            requireContext().registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        readResults()
        if (!hasRecentResults()) requestScan()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = settingsView {
        SettingsNetworkList(getString(R.string.wifi_location_title),
            getString(R.string.wifi_location_summ), { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            getString(R.string.wifi_category1), getString(R.string.wifi_category2), rows, loading, ::toggleNetwork,
            refresh = ::requestScan)
    }

    @SuppressLint("MissingPermission")
    private fun refreshRows() {
        // One-shot identity read; a separate NetworkCallback would duplicate the broadcast owner.
        @Suppress("DEPRECATION")
        val current = try { wifiManager.connectionInfo?.bssid } catch (_: SecurityException) { null }
        val selectedRows = bssids.map {
            val parts = it.split("|", limit = 2)
            NetworkSelectionRow(parts[0], parts.getOrElse(1) { "" }, selected = true, current = parts[0] == current)
        }
        val known = selectedRows.mapTo(HashSet()) { it.id }
        rows = selectedRows + buildList {
            for (network in wifiList) {
                if (known.add(network.BSSID)) {
                    add(NetworkSelectionRow(network.BSSID, network.wifiSsid?.toString().orEmpty().removeSurrounding("\""),
                        current = network.BSSID == current))
                }
            }
        }
    }

    private fun toggleNetwork(row: NetworkSelectionRow) {
        bssids = LinkedHashSet(AppHelper.getStringSetOfAppPrefs(key, LinkedHashSet()))
        if (row.selected) AppHelper.removeStringPair(bssids, row.id)
        else AppHelper.addStringPair(bssids, row.id, row.name)
        AppHelper.appPrefs?.edit()?.putStringSet(key, bssids.takeIf { it.isNotEmpty() })?.apply()
        refreshRows()
    }
}
