package tv.withaibuild.customiuizer.subs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.prefs.PreferenceEx
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.WifiAutomaticSettings as Config
import java.util.Locale
import java.util.UUID

class System_WifiAutomatic : SubFragment() {
    private val prefix = "pref_key_system_wifiauto_"
    private var dialog: AlertDialog? = null
    private var permissionContinuation: (() -> Unit)? = null
    private var pendingCurrentAdd = false
    private fun rules() = Config.decode(AppHelper.appPrefs?.getString(prefix + "rules", "").orEmpty())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCurrentAdd = savedInstanceState?.getBoolean("wifi.pendingCurrentAdd") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("wifi.pendingCurrentAdd", pendingCurrentAdd)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        findPreference<Preference>(prefix + "add")?.setOnPreferenceClickListener { edit(null); true }
        findPreference<Preference>(prefix + "current")?.setOnPreferenceClickListener {
            addCurrent()
            true
        }
        findPreference<Preference>(prefix + "dnd_access")?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); true
        }
        findPreference<Preference>(prefix + "location")?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + requireContext().packageName))); true
        }
        findPreference<Preference>(prefix + "vpn_target")?.setOnPreferenceClickListener {
            openSubFragment(System_VpnAutoSwitch(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp,
                R.string.system_vpnauto_title, R.id.prefs_system_vpnauto)
            true
        }
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        if (AppHelper.appPrefs?.getBoolean(prefix + "enabled", false) == true)
            ModuleHelper.guarded { ModuleHelper.sendBroadcastWithIdentity(requireContext(), Intent(Config.REFRESH).setPackage("android")) }
    }

    private fun rebuild() {
        val values = rules()
        val category = findPreference<PreferenceCategory>(prefix + "list") ?: return
        category.removeAll()
        for (rule in values) category.addPreference(PreferenceEx(requireContext(), null).apply {
            title = rule.name
            summary = listOf(rule.ssid, rule.bssid.takeIf { it.isNotEmpty() },
                getString(R.string.wifiauto_priority) + ": " + rule.priority,
                getString(if (rule.enabled) R.string.system_island_state_enabled else R.string.array_disable)).filterNotNull().joinToString(" · ")
            isPersistent = false
            setOnPreferenceClickListener { edit(rule); true }
        })
        for (key in listOf("add", "current")) findPreference<Preference>(prefix + key)?.isEnabled = values.size < Config.MAX_RULES
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun current(onGranted: () -> Unit): Config.Identity? {
        val ctx = requireContext()
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            permissionContinuation = onGranted
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES), 771)
            return null
        }
        if (!ctx.getSystemService(LocationManager::class.java).isLocationEnabled) { message(); return null }
        val info = try { ctx.getSystemService(WifiManager::class.java).connectionInfo } catch (_: SecurityException) { null }
        val identity = Config.identity(info?.ssid, info?.bssid)
        if (identity == null) message()
        return identity
    }

    private fun addCurrent() {
        pendingCurrentAdd = false
        current(::addCurrent)?.let { edit(Config.Rule(UUID.randomUUID().toString(), it.ssid, it.ssid)) }
        pendingCurrentAdd = permissionContinuation != null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 771) return
        val continuation = permissionContinuation
        val addCurrentAfterGrant = pendingCurrentAdd
        permissionContinuation = null
        pendingCurrentAdd = false
        if (!isAdded || view == null || parentFragmentManager.isStateSaved) return
        val ctx = requireContext()
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED) {
            if (addCurrentAfterGrant) addCurrent() else continuation?.invoke()
        }
        else message()
    }

    private fun message() { AlertDialog.Builder(requireContext()).setMessage(R.string.wifiauto_unavailable).setPositiveButton(android.R.string.ok, null).show() }

    private fun edit(existing: Config.Rule?) {
        val original = existing ?: Config.Rule(UUID.randomUUID().toString(), "", "")
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad)
        }
        fun text(label: Int, value: String, tagName: String, limit: Int = 80) = EditText(ctx).apply {
            layout.addView(TextView(ctx).apply { text = getString(label) })
            setText(value); tag = tagName; setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(limit))
            layout.addView(this)
        }
        val name = text(R.string.wifiauto_name, original.name, "name")
        val ssid = text(R.string.wifiauto_ssid, original.ssid, "ssid", 64)
        val bssid = text(R.string.wifiauto_bssid, original.bssid, "bssid", 17)
        layout.addView(Button(ctx).apply {
            setText(R.string.wifi_networks)
            fun choose() {
                val identity = current { choose() }
                if (permissionContinuation != null) return
                val networks = (rules().map { it.ssid } + listOfNotNull(identity?.ssid)).distinct()
                if (networks.isNotEmpty()) AlertDialog.Builder(ctx).setTitle(R.string.wifi_networks)
                    .setItems(networks.toTypedArray()) { _, which -> ssid.setText(networks[which]) }.show()
            }
            setOnClickListener { choose() }
        })
        val priority = text(R.string.wifiauto_priority, original.priority.toString(), "priority", 3).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val enabled = CheckBox(ctx).apply { setText(R.string.system_island_state_enabled); isChecked = original.enabled; layout.addView(this) }
        fun choice(title: Int, labels: IntArray, values: IntArray, selected: Int): Pair<Spinner, IntArray> {
            layout.addView(TextView(ctx).apply { text = getString(title) })
            val spinner = Spinner(ctx).apply {
                adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, labels.map(::getString))
                setSelection(values.indexOf(selected).coerceAtLeast(0))
                layout.addView(this)
            }
            return spinner to values
        }
        val dnd = choice(R.string.system_statusbaricons_dnd_title,
            intArrayOf(R.string.wifiauto_keep, R.string.array_disable, R.string.wifiauto_priority_only, R.string.wifiauto_alarms, R.string.wifiauto_silence), intArrayOf(-1, 1, 2, 4, 3), original.dnd)
        val ringer = choice(R.string.system_statusbaricons_sound_title,
            intArrayOf(R.string.wifiauto_keep, R.string.toggle_sound_normal, R.string.toggle_sound_vibrate, R.string.toggle_sound_silent), intArrayOf(-1, 2, 1, 0), original.ringer)
        val onoff = intArrayOf(R.string.wifiauto_keep, R.string.array_enable, R.string.array_disable)
        val rotation = choice(R.string.system_mods_rotation, onoff, intArrayOf(-1, 1, 0), original.rotation)
        val vpn = choice(R.string.system_statusbaricons_vpn_title, onoff, intArrayOf(-1, 1, 0), original.vpn)
        val builder = AlertDialog.Builder(ctx).setTitle(R.string.wifiauto_edit)
            .setView(ScrollView(ctx).apply { addView(layout) }).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
        if (existing != null) builder.setNeutralButton(R.string.wifiauto_delete) { _, _ -> save(rules().filter { it.id != existing.id }) }
        dialog?.dismiss()
        dialog = builder.create().also { value ->
            value.setOnShowListener {
                value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val rule = original.copy(name = name.text.toString().trim(), ssid = ssid.text.toString(),
                        bssid = bssid.text.toString().trim().lowercase(Locale.ROOT), priority = priority.text.toString().toIntOrNull() ?: -1,
                        enabled = enabled.isChecked, dnd = dnd.second[dnd.first.selectedItemPosition], ringer = ringer.second[ringer.first.selectedItemPosition],
                        rotation = rotation.second[rotation.first.selectedItemPosition], vpn = vpn.second[vpn.first.selectedItemPosition])
                    val error = getString(R.string.wifiauto_invalid)
                    when {
                        rule.name.isBlank() -> name.error = error
                        !Config.validSsid(rule.ssid) -> ssid.error = error
                        rule.bssid.isNotEmpty() && !Config.validBssid(rule.bssid) -> bssid.error = error
                        rule.priority !in 0..999 -> priority.error = error
                        else -> {
                            val list = rules().toMutableList()
                            val index = list.indexOfFirst { it.id == rule.id }
                            if (index >= 0) list[index] = rule else if (list.size < Config.MAX_RULES) list.add(rule)
                            save(list); value.dismiss()
                        }
                    }
                }
            }
            value.show()
        }
    }

    private fun save(rules: List<Config.Rule>) { AppHelper.appPrefs?.edit()?.putString(prefix + "rules", Config.encode(rules))?.apply(); rebuild() }
    override fun onDestroyView() {
        permissionContinuation = null
        if (isRemoving) pendingCurrentAdd = false
        dialog?.dismiss(); dialog = null
        super.onDestroyView()
    }
}
