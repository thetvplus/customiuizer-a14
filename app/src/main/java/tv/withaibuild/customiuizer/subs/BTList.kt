package tv.withaibuild.customiuizer.subs

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Pair
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
import android.bluetooth.BluetoothManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.AppHelper

class BTList : SubFragment() {

    private var key: String? = null
    private var rows by mutableStateOf<List<NetworkSelectionRow>>(emptyList())
    private var loading by mutableStateOf(false)
    private var addresses = LinkedHashSet<String>()

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private val timeout = Runnable { loading = false }
    private val adapter: BluetoothAdapter?
        get() = requireContext().getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    private fun isBluetoothEnabled(): Boolean =
        requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            adapter?.isEnabled == true

    private val devicesReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (!receiverRegistered) return
            if (intent.action != GlobalActions.EVENT_PREFIX + "CACHEDDEVICESUPDATE") {
                refreshRows()
                if (isBluetoothEnabled()) fetchCachedDevices() else {
                    btList.clear()
                    lastRequest = null
                    stopWaiting()
                    refreshRows()
                }
                return
            }
            if (!ModuleHelper.isTrustedBroadcast(this, "com.android.systemui", rejectionResultCode = GlobalActions.ACTION_FAILED)) return
            val deviceList = intent.getParcelableArrayListExtra("device_list", BluetoothDevice::class.java)
            btList.clear()
            if (isBluetoothEnabled()) deviceList?.forEach { btList.add(Pair(it.address, it.name)) }
            stopWaiting()
            refreshRows()
            if (isOrderedBroadcast) setResultCode(GlobalActions.ACTION_HANDLED)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        key = requireArguments().getString("key")
        addresses = LinkedHashSet(AppHelper.getStringSetOfAppPrefs(key, LinkedHashSet()))

        if (!isBluetoothEnabled()) {
            btList.clear()
            Toast.makeText(requireActivity(), R.string.request_bt, Toast.LENGTH_SHORT).show()
        }
        refreshRows()
    }

    @SuppressLint("MissingPermission")
    private fun fetchCachedDevices() {
        if (!receiverRegistered || loading || !isBluetoothEnabled()) return
        val now = SystemClock.elapsedRealtime()
        if (lastRequest?.let { now - it in 0 until 10_000L } == true) return
        lastRequest = now
        val intent = Intent(GlobalActions.ACTION_PREFIX + "FetchCachedDevices").setPackage("com.android.systemui")
        loading = true
        handler.postDelayed(timeout, 12_000L)
        ModuleHelper.sendBroadcastWithIdentity(getValidContext(), intent)
    }

    private fun stopWaiting() {
        handler.removeCallbacks(timeout)
        loading = false
    }

    override fun onPause() {
        if (receiverRegistered) {
            receiverRegistered = false
            requireContext().unregisterReceiver(devicesReceiver)
        }
        stopWaiting()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            val filter = IntentFilter(GlobalActions.EVENT_PREFIX + "CACHEDDEVICESUPDATE").apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            // The module response comes from SystemUI, guarded by sender identity;
            // Bluetooth state broadcasts are protected system actions.
            requireContext().registerReceiver(devicesReceiver, filter, Context.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        refreshRows()
        fetchCachedDevices()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = settingsView {
        SettingsNetworkList(getString(R.string.bt_fetch_devices_title),
            getString(R.string.bt_fetch_devices_summ), { fetchCachedDevices() },
            getString(R.string.bt_category1), getString(R.string.bt_category2), rows, loading, ::toggleNetwork)
    }

    @SuppressLint("MissingPermission")
    private fun refreshRows() {
        val current = try { adapter?.bondedDevices?.map { it.address }?.toSet() ?: emptySet() } catch (_: SecurityException) { emptySet() }
        val selectedRows = addresses.map {
            val parts = it.split("|", limit = 2)
            NetworkSelectionRow(parts[0], parts.getOrElse(1) { "" }, selected = true, current = parts[0] in current)
        }
        val known = selectedRows.mapTo(HashSet()) { it.id }
        rows = selectedRows + btList.distinctBy { it.first }.filter { it.first !in known }.map {
            NetworkSelectionRow(it.first, it.second.orEmpty(), current = it.first in current)
        }
    }

    companion object {
        private var lastRequest: Long? = null
        // Reuse the last system cache response during cross-page cooldown. One
        // snapshot of address/name values only; no device, Context or View owners.
        private val btList = mutableListOf<Pair<String, String>>()
    }

    private fun toggleNetwork(row: NetworkSelectionRow) {
        addresses = LinkedHashSet(AppHelper.getStringSetOfAppPrefs(key, LinkedHashSet()))
        if (row.selected) AppHelper.removeStringPair(addresses, row.id)
        else AppHelper.addStringPair(addresses, row.id, row.name)
        AppHelper.appPrefs?.edit()?.putStringSet(key, addresses.takeIf { it.isNotEmpty() })?.apply()
        refreshRows()
    }
}
