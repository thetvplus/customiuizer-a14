package tv.withaibuild.customiuizer.subs

import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings as Settings
import java.lang.ref.WeakReference

class System_VpnAutoSwitch : SubFragment() {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var timeout: Runnable? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        for (key in listOf("client", "profile")) findPreference<ListPreference>("pref_key_system_vpnauto_$key")?.apply {
            entries = emptyArray<CharSequence>()
            entryValues = emptyArray<CharSequence>()
            isEnabled = false
        }
        findPreference<Preference>("pref_key_system_vpnauto_apps")?.onPreferenceClickListener = openAppsEdit
        findPreference<Preference>("pref_key_system_vpnauto_kind")?.setOnPreferenceChangeListener { preference, value ->
            visibility(syncPendingListSelection(preference, value))
            true
        }
        visibility(AppHelper.getStringOfAppPrefs("pref_key_system_vpnauto_kind", "app"))
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val clients = withContext(Dispatchers.IO) {
                try {
                    val pm = ctx.packageManager
                    pm.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), PackageManager.GET_META_DATA)
                    .mapNotNull { it.serviceInfo }.filter { it.enabled && it.permission == "android.permission.BIND_VPN_SERVICE" }
                    .distinctBy { it.packageName }.take(128)
                    .map { it.packageName to it.applicationInfo.loadLabel(pm).toString().take(120) }
                        .sortedBy { it.second.lowercase() }
                } catch (error: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(error)
                    if (error is CancellationException) throw error
                    error.printStackTrace()
                    emptyList()
                }
            }
            val labelCounts = clients.groupingBy { it.second }.eachCount()
            findPreference<ListPreference>("pref_key_system_vpnauto_client")?.apply {
                entryValues = clients.map { it.first }.toTypedArray()
                entries = clients.map { (pkg, label) -> if (labelCounts[label] == 1) label else "$label ($pkg)" }.toTypedArray()
                isEnabled = clients.isNotEmpty()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestOptions()
    }

    override fun onPause() {
        generation++
        timeout?.let(handler::removeCallbacks)
        timeout = null
        super.onPause()
    }

    private fun visibility(kind: String?) {
        findPreference<Preference>("pref_key_system_vpnauto_client")?.isVisible = kind != "system"
        findPreference<Preference>("pref_key_system_vpnauto_profile")?.isVisible = kind == "system"
    }

    private fun requestOptions() {
        val ctx = context ?: return
        val expected = ++generation
        timeout?.let(handler::removeCallbacks)
        val weak = WeakReference(this)
        timeout = Runnable { weak.get()?.takeIf { it.generation == expected && it.view != null }?.showStatus("UNAVAILABLE") }
            .also { handler.postDelayed(it, 4_000) }
        val result = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val fragment = weak.get()?.takeIf { it.generation == expected && it.view != null } ?: return
                fragment.timeout?.let(fragment.handler::removeCallbacks)
                fragment.timeout = null
                if (resultCode != -1) { fragment.showStatus("UNAVAILABLE"); return }
                val data = getResultExtras(false)
                val keys = data?.getStringArrayList("keys").orEmpty().take(128)
                val names = data?.getStringArrayList("names").orEmpty().take(128)
                fragment.findPreference<ListPreference>("pref_key_system_vpnauto_profile")?.apply {
                    entryValues = keys.toTypedArray()
                    entries = keys.indices.map { names.getOrElse(it) { keys[it] } }.toTypedArray()
                    isEnabled = keys.isNotEmpty()
                    summary = fragment.getString(if (keys.isEmpty()) R.string.system_vpnauto_no_profiles else R.string.system_vpnauto_profile_summary)
                }
                fragment.showStatus(data?.getString("status") ?: "UNAVAILABLE")
            }
        }
        val identity = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        try {
            ctx.sendOrderedBroadcast(Intent(Settings.OPTIONS).setPackage("android"), null, identity, result, handler, 0, null, null)
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            error.printStackTrace()
            timeout?.let(handler::removeCallbacks)
            timeout = null
            showStatus("UNAVAILABLE")
        }
    }

    private fun showStatus(value: String) {
        val resource = when (value) {
            "READY" -> R.string.system_vpnauto_ready
            "CONNECTING" -> R.string.system_vpnauto_connecting
            "OWNED" -> R.string.system_vpnauto_owned
            "EXISTING" -> R.string.system_vpnauto_existing
            "PROTECTED" -> R.string.system_vpnauto_protected
            "PAUSED" -> R.string.system_vpnauto_paused
            "FAILED" -> R.string.system_vpnauto_failed
            "DISABLED" -> R.string.system_vpnauto_disabled
            "CONFIGURE" -> R.string.system_vpnauto_configure
            else -> R.string.system_vpnauto_unavailable
        }
        findPreference<Preference>("pref_key_system_vpnauto_status")?.setSummary(resource)
    }

    override fun onDestroyView() {
        generation++
        timeout?.let(handler::removeCallbacks)
        timeout = null
        super.onDestroyView()
    }
}
