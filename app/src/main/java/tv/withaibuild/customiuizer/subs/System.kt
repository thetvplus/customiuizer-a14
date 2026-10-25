package tv.withaibuild.customiuizer.subs

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import miui.os.Build
import tv.withaibuild.customiuizer.PrefsProvider
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx
import tv.withaibuild.customiuizer.prefs.SeekBarPreference
import tv.withaibuild.customiuizer.qs.AutoRotateService
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers
import kotlin.math.roundToInt

class System : SubFragment() {
    private var islandOffsetUpdate: Runnable? = null
    private var islandOffsetHandler: Handler? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (sub == "pref_key_system_cat_recents") {
            migrateLegacyRecentsCardStylePreference()
        }
        if (sub == "pref_key_system_cat_toasts") {
            val prefs = AppHelper.appPrefs
            if (prefs != null && !prefs.contains("pref_key_system_toast_position") &&
                (prefs.all["pref_key_system_toast_bottom"] as? Number)?.toInt()?.let { it > 0 } == true) {
                prefs.edit().putString("pref_key_system_toast_position", "3").apply()
            }
        }
        super.onCreatePreferences(savedInstanceState, rootKey)
        selectSub()
        if (sub == "pref_key_system_island_layout_cat") configureIslandOffsetRange()
        if (sub == "pref_key_system_cat_toasts") {
            val position = findPreference<ListPreferenceEx>("pref_key_system_toast_position")
            fun showOffset(value: Any?) {
                findPreference<Preference>("pref_key_system_toast_top")?.isVisible = value?.toString() == "1"
                findPreference<Preference>("pref_key_system_toast_center")?.isVisible = value?.toString() == "2"
                findPreference<Preference>("pref_key_system_toast_bottom")?.isVisible = value?.toString() == "3"
            }
            position?.setOnPreferenceChangeListener { preference, value ->
                syncPendingListSelection(preference, value)
                showOffset(value)
                true
            }
            showOffset(position?.value)
        }
        // Apply ROM-specific visibility before the first adapter bind. Removing this row
        // in onActivityCreated starts a list relayout during the horizontal page animation.
        if (sub == "pref_key_system_cat_qs" && Build.IS_INTERNATIONAL_BUILD) {
            findPreference<Preference>("pref_key_system_cc_switch_qsandnotification")?.isVisible = false
        }
        if (sub == "pref_key_system_detailednetspeed_cat") {
            updateDetailedNetSpeedOptions(
                findPreference<ListPreferenceEx>("pref_key_system_detailednetspeed_style")?.value
            )
        }
    }

    private fun configureIslandOffsetRange() {
        val prefs = AppHelper.appPrefs ?: return
        val offset = findPreference<SeekBarPreference>("pref_key_system_island_horizontal_offset") ?: return
        val metrics = activity?.windowManager?.currentWindowMetrics
        val hole = metrics?.windowInsets?.displayCutout?.boundingRectTop?.width() ?: 0
        val density = resources.displayMetrics.density
        val screenWidth = metrics?.bounds?.width() ?: resources.displayMetrics.widthPixels
        fun px(dp: Int) = (dp * density + .5f).toInt()
        val update = Runnable {
            if (!isAdded) return@Runnable
            val alignment = prefs.getString("pref_key_system_island_position", "1")?.toIntOrNull() ?: 1
            val centered = alignment == 1
            var limit = 80
            if (centered && hole > 0) {
                val manualHeight = prefs.getInt("pref_key_system_island_height", 40)
                val height = if (prefs.getBoolean("pref_key_system_island_height_auto", manualHeight == 40))
                    tv.withaibuild.customiuizer.mods.island.IslandSizing.automaticHeight((hole / density).toInt()) else manualHeight
                val gap = maxOf(px(40), hole + px(16))
                val sizing = tv.withaibuild.customiuizer.mods.island.IslandSizing
                val short = sizing.collapsed(density, screenWidth, screenWidth, gap, px(24).toFloat(), px(24).toFloat(),
                    shortDp = prefs.getInt("pref_key_system_island_width_short", 184),
                    longDp = prefs.getInt("pref_key_system_island_width_medium", 240),
                    manual = !prefs.getBoolean("pref_key_system_island_width_auto", true), heightDp = height)
                val smallest = if (prefs.getBoolean("pref_key_system_island_idle", false)) sizing.idleWidth(density,
                    px(height), hole, short, if (prefs.getBoolean("pref_key_system_island_idle_width_auto", true)) 0
                    else prefs.getInt("pref_key_system_island_idle_width", 100)) else short
                limit = (sizing.horizontalOffsetLimit(short, smallest, gap, px(sizing.EDGE_DP + 24)) / density).toInt().coerceIn(0, 80)
            }
            offset.setValueRange(if (alignment == 0) 80 else 80 - limit,
                if (alignment == 2) 80 else maxOf(81, 80 + limit))
            if (limit == 0) offset.setValue(80)
            val autoHeight = prefs.getBoolean("pref_key_system_island_height_auto", prefs.getInt("pref_key_system_island_height", 40) == 40)
            val autoWidth = prefs.getBoolean("pref_key_system_island_width_auto", true)
            findPreference<Preference>("pref_key_system_island_height")?.isVisible = !autoHeight
            for (kind in listOf("short", "medium"))
                findPreference<Preference>("pref_key_system_island_width_$kind")?.isVisible = !autoWidth
            val idle = prefs.getBoolean("pref_key_system_island_idle", false)
            findPreference<Preference>("pref_key_system_island_idle_width_auto")?.isVisible = idle
            findPreference<Preference>("pref_key_system_island_idle_width")?.isVisible = idle && !prefs.getBoolean("pref_key_system_island_idle_width_auto", true)
            val mode = prefs.all["pref_key_system_strong_toast_mode"]?.toString()?.toIntOrNull() ?: 0
            offset.isEnabled = limit > 0 && StrongToastPresentationMode.fromPreference(mode) == StrongToastPresentationMode.DYNAMIC_ISLAND
        }
        val handler = Handler(Looper.getMainLooper())
        islandOffsetHandler = handler; islandOffsetUpdate = update
        for (suffix in listOf("position", "height_auto", "height", "width_auto", "width_short", "width_medium",
                "idle", "idle_width_auto", "idle_width")) {
            findPreference<Preference>("pref_key_system_island_$suffix")?.setOnPreferenceChangeListener { _, _ ->
                handler.removeCallbacks(update)
                handler.post(update)
                true
            }
        }
        update.run()
    }

    override fun onDestroyView() {
        islandOffsetUpdate?.let { islandOffsetHandler?.removeCallbacks(it) }
        islandOffsetUpdate = null; islandOffsetHandler = null
        super.onDestroyView()
    }

    private fun updateDetailedNetSpeedOptions(style: Any?) {
        val usesModuleFormatter = (style?.toString()?.toIntOrNull() ?: 1) > 1
        findPreference<Preference>("pref_key_system_detailednetspeed_secunit")?.isEnabled = usesModuleFormatter
        findPreference<Preference>("pref_key_system_detailednetspeed_low")?.isEnabled = usesModuleFormatter
        findPreference<Preference>("pref_key_system_detailednetspeed_lowlevel")?.isEnabled = usesModuleFormatter
    }

    private fun migrateLegacyRecentsCardStylePreference() {
        val prefs = AppHelper.appPrefs ?: return
        val raw = prefs.all?.get("pref_key_system_recents_card_style") ?: return
        val migrated = legacyRecentsCardStyleToBoolean(raw) ?: return
        val editor = prefs.edit()
        editor.remove("pref_key_system_recents_card_style")
        editor.putBoolean("pref_key_system_recents_card_style", migrated)
        val committed = editor.commit()
        if (!committed) {
            AppHelper.appPrefs?.edit()?.remove("pref_key_system_recents_card_style")?.commit()
        }
    }

    internal companion object {
        @JvmStatic
        fun legacyRecentsCardStyleToBoolean(raw: Any?): Boolean? {
            return when (raw) {
                is Boolean -> null
                is String -> when (raw.toIntOrNull()) {
                    1 -> true
                    else -> false
                }
                is Number -> when (raw.toInt()) {
                    1 -> true
                    else -> false
                }
                null -> null
                else -> false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private fun openSystemSubFragment(preference: Preference, isDynamic: Boolean = false, xmlResId: Int) {
        val args = Bundle().apply {
            putBoolean("isStandalone", true)
            if (isDynamic) putBundle("catInfo", Bundle().apply { putBoolean("isDynamic", true) })
            putString("sub", preference.key)
        }
        openSubFragment(System(), args, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, preference.title?.toString() ?: "", xmlResId)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        when (sub) {
            "pref_key_system_cat_screen" -> {
                findPreference<Preference>("pref_key_system_orientationlock")?.setOnPreferenceChangeListener { _, newValue ->
                    val act = activity ?: return@setOnPreferenceChangeListener false
                    val pm = act.packageManager
                    val state = if (newValue == true) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    pm.setComponentEnabledSetting(ComponentName(act, AutoRotateService::class.java), state, PackageManager.DONT_KILL_APP)
                    true
                }

                findPreference<Preference>("pref_key_system_autobrightness_cat")?.setOnPreferenceClickListener {
                    openSubFragment(System_AutoBrightness(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_autobrightness_title, R.xml.prefs_system_autobrightness)
                    true
                }
            }
            "pref_key_system_cat_audio" -> {
                findPreference<Preference>("pref_key_system_ignorecalls_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_visualizer_cat")?.setOnPreferenceClickListener {
                    openSubFragment(System_Visualizer(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_visualizer_title, R.xml.prefs_system_visualizer)
                    true
                }
            }
            "pref_key_system_cat_vibration" -> {
                findPreference<Preference>("pref_key_system_vibration_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_vibration_amp_cat")?.setOnPreferenceClickListener {
                    openSubFragment(System_VibrationAmp(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_vibration_amp_title, R.xml.prefs_system_vibration_amp)
                    true
                }
            }
            "pref_key_system_cat_toasts" -> findPreference<Preference>("pref_key_system_blocktoasts_apps")?.setOnPreferenceClickListener(openAppsEdit)

            "pref_key_system_cat_prompts" -> {
                val style = findPreference<ListPreferenceEx>("pref_key_system_strong_toast_mode")
                fun updateApplicationPrompt(value: Any?) {
                    val mode = StrongToastPresentationMode.fromPreference(value?.toString()?.toIntOrNull() ?: 0)
                    findPreference<Preference>("pref_key_system_toast_capsule")?.isEnabled =
                        mode == StrongToastPresentationMode.DYNAMIC_ISLAND || mode == StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT
                }
                style?.setOnPreferenceChangeListener { preference, value ->
                    syncPendingListSelection(preference, value)
                    updateApplicationPrompt(value)
                    true
                }
                updateApplicationPrompt(style?.value)
            }

            "pref_key_system_island_feedback_cat", "pref_key_system_island_music_cat" -> {
                val enabled = StrongToastPresentationMode.fromPreference(
                    AppHelper.appPrefs?.all?.get("pref_key_system_strong_toast_mode")?.toString()?.toIntOrNull() ?: 0
                ) == StrongToastPresentationMode.DYNAMIC_ISLAND
                // Native Preference dependencies own media/return enablement and propagate
                // changes through the existing screen; no mirrored checkbox state.
                preferenceScreen.isEnabled = enabled
                findPreference<Preference>("pref_key_system_island_media_return_apps")?.setOnPreferenceClickListener(openAppsEdit)
                for (gesture in tv.withaibuild.customiuizer.mods.island.IslandMusicGesture.entries) {
                    findPreference<Preference>("pref_key_${gesture.key}")?.apply {
                        onPreferenceClickListener = openMusicActions
                    }
                }
            }
            "pref_key_system_island_notifications_cat" -> {
                val prefs = AppHelper.appPrefs
                val enabled = StrongToastPresentationMode.fromPreference(prefs?.all?.get("pref_key_system_strong_toast_mode")?.toString()?.toIntOrNull() ?: 0) == StrongToastPresentationMode.DYNAMIC_ISLAND
                preferenceScreen.isEnabled = enabled
                for (suffix in listOf("notification_apps", "chat_excluded_apps"))
                    findPreference<Preference>("pref_key_system_island_$suffix")?.setOnPreferenceClickListener(openAppsEdit)
            }
            "pref_key_system_island_layout_cat" -> {
                val enabled = StrongToastPresentationMode.fromPreference(AppHelper.appPrefs?.all?.get("pref_key_system_strong_toast_mode")?.toString()?.toIntOrNull() ?: 0) == StrongToastPresentationMode.DYNAMIC_ISLAND
                preferenceScreen.isEnabled = enabled
                islandOffsetUpdate?.run()
            }
            "pref_key_system_cat_island" -> {
                findPreference<Preference>("pref_key_system_island_layout_cat")?.setOnPreferenceClickListener {
                    openSystemSubFragment(it, false, R.xml.prefs_system_island_layout); true
                }
                findPreference<Preference>("pref_key_system_island_notifications_cat")?.setOnPreferenceClickListener {
                    openSystemSubFragment(it, false, R.xml.prefs_system_island_notifications); true
                }
                findPreference<Preference>("pref_key_system_island_feedback_cat")?.setOnPreferenceClickListener {
                    openSystemSubFragment(it, false, R.xml.prefs_system_island_feedback); true
                }
                findPreference<Preference>("pref_key_system_island_music_cat")?.setOnPreferenceClickListener {
                    openSystemSubFragment(it, false, R.xml.prefs_system_island_music); true
                }
                val strongToastMode = findPreference<ListPreferenceEx>("pref_key_system_strong_toast_mode")
                val islandOffset = findPreference<Preference>("pref_key_system_strong_toast_island_offset")
                fun showIslandOffset(mode: Any?) {
                    val presentation = StrongToastPresentationMode.fromPreference(
                        mode?.toString()?.toIntOrNull() ?: 0
                    )
                    val enabled = presentation == StrongToastPresentationMode.DYNAMIC_ISLAND
                    islandOffset?.isVisible = enabled
                    for (key in listOf("feedback_cat", "music_cat", "layout_cat", "notifications_cat", "idle", "expand_down", "entrance_style", "simplified", "excluded_apps", "hide_in_floating_window")) {
                        findPreference<Preference>("pref_key_system_island_$key")?.isEnabled = enabled
                    }
                    for (gesture in tv.withaibuild.customiuizer.mods.island.IslandMusicGesture.entries)
                        findPreference<Preference>("pref_key_${gesture.key}")?.isEnabled = enabled
                    for (key in tv.withaibuild.customiuizer.utils.IslandPreferenceDefaults.feedbackKeys)
                        findPreference<Preference>(key)?.isEnabled = enabled
                    findPreference<Preference>("pref_key_system_toast_capsule")?.isEnabled =
                        enabled || presentation == StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT
                }
                strongToastMode?.setOnPreferenceChangeListener { preference, newValue ->
                    syncPendingListSelection(preference, newValue)
                    showIslandOffset(newValue)
                    true
                }
                showIslandOffset(strongToastMode?.value ?: AppHelper.appPrefs?.all?.get("pref_key_system_strong_toast_mode"))
                islandOffsetUpdate?.run()
                findPreference<Preference>("pref_key_system_island_excluded_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_island_chat_excluded_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_island_notification_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_island_media_return_apps")?.setOnPreferenceClickListener(openAppsEdit)
                for (gesture in tv.withaibuild.customiuizer.mods.island.IslandMusicGesture.entries)
                    findPreference<Preference>("pref_key_${gesture.key}")?.setOnPreferenceClickListener(openMusicActions)
            }
            "pref_key_system_cat_statusbar" -> {
                findPreference<Preference>("pref_key_system_statusbarcolor_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_detailednetspeed_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, false, R.xml.prefs_system_detailednetspeed); true }
                findPreference<Preference>("pref_key_system_statusbar_batterytempandcurrent_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_statusbar_batterytempandcurrent); true }
                findPreference<Preference>("prefs_system_statusbar_showdevicetemperature_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_statusbar_showdevicetemperature); true }
                findPreference<Preference>("pref_key_system_statusbar_batterystyle_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, false, R.xml.prefs_system_statusbar_batterystyle); true }
                findPreference<Preference>("pref_key_system_statusbar_duo_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_statusbar_duo); true }
                findPreference<Preference>("pref_key_system_statusbar_mobile_signal_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_statusbar_mobilesignal); true }
                findPreference<Preference>("pref_key_system_statusbaricons_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_hideicons); true }
                findPreference<Preference>("pref_key_system_statusbaricons_atright_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_statusbar_righticons); true }
                findPreference<Preference>("pref_key_system_statusbar_clocktweak_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_statusbar_clock); true }
                findPreference<Preference>("pref_key_system_batteryindicator_cat")?.setOnPreferenceClickListener {
                    openSubFragment(System_BatteryIndicator(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_batteryindicator_title, R.xml.prefs_system_batteryindicator)
                    true
                }
                findPreference<Preference>("pref_key_system_statusbarcontrols_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_statusbarcontrols); true }
            }
            "pref_key_system_cat_drawer" -> {
                findPreference<Preference>("pref_key_system_clock_app")?.setOnPreferenceClickListener {
                    openStandaloneApp(it, this, 1)
                    true
                }
                findPreference<Preference>("pref_key_system_calendar_app")?.setOnPreferenceClickListener {
                    openStandaloneApp(it, this, 2)
                    true
                }
                findPreference<Preference>("pref_key_system_cc_clocktweak_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_controlcenter_clock); true }
            }
            "pref_key_system_cat_notifications" -> {
                findPreference<Preference>("pref_key_system_expandnotifs_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_notify_openinfw_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_colorizenotifs_apps")?.setOnPreferenceClickListener(openAppsEdit)
            }
            "pref_key_system_cat_qs" -> {
                findPreference<Preference>("pref_key_system_cc_tile_style_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, false, R.xml.prefs_system_controlcenter_themestyle); true }
            }
            "pref_key_system_cat_recents" -> findPreference<Preference>("pref_key_system_hidefromrecents_apps")?.setOnPreferenceClickListener(openAppsEdit)
            "pref_key_system_cat_betterpopups" -> {
                findPreference<Preference>("pref_key_system_betterpopups_allowfloat_apps")?.setOnPreferenceClickListener(openAppsBWEdit)
                findPreference<Preference>("pref_key_system_expandheadups_apps")?.setOnPreferenceClickListener(openAppsEdit)
            }
            "pref_key_system_cat_floatingwindows" -> findPreference<Preference>("pref_key_system_fw_forcein_actionsend_apps")?.setOnPreferenceClickListener(openAppsEdit)
            "pref_key_system_cat_applock" -> {
                findPreference<Preference>("pref_key_system_applock_list")?.setOnPreferenceClickListener {
                    openLockedAppEdit(this, 0)
                    true
                }
                findPreference<Preference>("pref_key_system_applock_skip_activities")?.setOnPreferenceClickListener(openActivitiesList)
            }
            "pref_key_system_cat_lockscreen" -> {
                findPreference<Preference>("pref_key_system_noscreenlock_cat")?.setOnPreferenceClickListener {
                    openSubFragment(System_NoScreenLock(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_noscreenlock_title, R.xml.prefs_system_noscreenlock)
                    true
                }
                findPreference<Preference>("pref_key_system_lockscreenshortcuts_cat")?.setOnPreferenceClickListener { openSystemSubFragment(it, true, R.xml.prefs_system_lockscreenshortcuts); true }
                findPreference<Preference>("pref_key_system_albumartonlock_cat")?.setOnPreferenceClickListener {
                    openSubFragment(SubFragment(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_albumartonlock_title, R.xml.prefs_system_albumartonlock)
                    true
                }
                findPreference<Preference>("pref_key_system_charginginfo_cat")?.setOnPreferenceClickListener {
                    openSubFragment(SubFragment(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_charginginfo_title, R.xml.prefs_system_charginginfo)
                    true
                }
                findPreference<Preference>("pref_key_system_lsalarm_cat")?.setOnPreferenceClickListener {
                    openSubFragment(SubFragment(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_lsalarm_title, R.xml.prefs_system_alarmonlock)
                    true
                }
                findPreference<Preference>("pref_key_system_secureqs_cat")?.setOnPreferenceClickListener {
                    openSubFragment(SubFragment(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_secureqs_title, R.xml.prefs_system_secureqs)
                    true
                }

                findPreference<Preference>("pref_key_system_credentials")?.setOnPreferenceChangeListener { _, newValue ->
                    val act = activity ?: return@setOnPreferenceChangeListener false
                    val pm = act.packageManager
                    val state = if (newValue == true) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    pm.setComponentEnabledSetting(ComponentName(act, Helpers.CREDENTIALS_LAUNCHER_ALIAS_NAME), state, PackageManager.DONT_KILL_APP)
                    true
                }

                activity?.let { act ->
                    findPreference<CheckBoxPreferenceEx>("pref_key_system_credentials")?.isChecked = act.packageManager.getComponentEnabledSetting(ComponentName(act, Helpers.CREDENTIALS_LAUNCHER_ALIAS_NAME)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }

                if (Helpers.isDeviceEncrypted(context)) {
                    findPreference<CheckBoxPreferenceEx>("pref_key_system_nopassword")?.apply {
                        isChecked = false
                        setUnsupported(true)
                    }
                }
            }
            "pref_key_system_lockscreenshortcuts_cat" -> findPreference<Preference>("pref_key_system_lockscreenshortcuts_right")?.setOnPreferenceClickListener(openLockScreenActions)
            "pref_key_system_cat_other" -> {
                findPreference<Preference>("pref_key_system_forceclose_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_nooverscroll_apps")?.setOnPreferenceClickListener(openAppsEdit)
                findPreference<Preference>("pref_key_system_cleanshare_apps")?.setOnPreferenceClickListener(openShareEdit)
                findPreference<Preference>("pref_key_system_cleanshare_test")?.setOnPreferenceClickListener {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "CustoMIUIzer is the best!")
                        type = "*/*"
                    }
                    context?.startActivity(Intent.createChooser(sendIntent, null))
                    true
                }

                findPreference<Preference>("pref_key_system_cleanopenwith_apps")?.setOnPreferenceClickListener(openOpenWithEdit)
                findPreference<Preference>("pref_key_system_cleanopenwith_test")?.setOnPreferenceClickListener {
                    AlertDialog.Builder(requireActivity()).apply {
                        setTitle(R.string.system_cleanopenwith_testdata)
                        setSingleChoiceItems(R.array.openwithtest, -1) { dialog, which ->
                            dialog.dismiss()
                            val type = when (which) {
                                0 -> "image/*"
                                1 -> "audio/*"
                                2 -> "video/*"
                                3 -> "text/*"
                                4 -> "application/zip"
                                else -> "*/*"
                            }
                            val viewIntent = Intent().apply {
                                action = Intent.ACTION_VIEW
                                setDataAndType(Uri.parse("content://${PrefsProvider.AUTHORITY}/test/$which"), type)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(viewIntent, null))
                        }
                        setNeutralButton(android.R.string.cancel) { _, _ -> }
                        show()
                    }
                    true
                }

                findPreference<Preference>("pref_key_system_screenshot_cat")?.setOnPreferenceClickListener {
                    openSubFragment(System_ScreenshotConfig(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_screenshot_title, R.xml.prefs_system_screenshot)
                    true
                }

                AppHelper.appPrefs!!.edit().putInt("pref_key_system_animationscale_window", (Helpers.getAnimationScale(0) * 10).roundToInt()).apply()
                findPreference<SeekBarPreference>("pref_key_system_animationscale_window")?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        requestAnimationScale(0, seekBar)
                    }
                })

                AppHelper.appPrefs!!.edit().putInt("pref_key_system_animationscale_transition", (Helpers.getAnimationScale(1) * 10).roundToInt()).apply()
                findPreference<SeekBarPreference>("pref_key_system_animationscale_transition")?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        requestAnimationScale(1, seekBar)
                    }
                })

                AppHelper.appPrefs!!.edit().putInt("pref_key_system_animationscale_animator", (Helpers.getAnimationScale(2) * 10).roundToInt()).apply()
                findPreference<SeekBarPreference>("pref_key_system_animationscale_animator")?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        requestAnimationScale(2, seekBar)
                    }
                })

            }
            "pref_key_system_detailednetspeed_cat" -> {
                findPreference<ListPreferenceEx>("pref_key_system_detailednetspeed_style")?.setOnPreferenceChangeListener { preference, newValue ->
                    syncPendingListSelection(preference, newValue)
                    updateDetailedNetSpeedOptions(newValue)
                    true
                }
            }
            "pref_key_system_statusbarcontrols_cat" -> {
                findPreference<Preference>("pref_key_system_statusbarcontrols_dt")?.setOnPreferenceClickListener(openStatusbarActions)
                findPreference<Preference>("pref_key_system_statusbarcontrols_dt_left")?.setOnPreferenceClickListener(openStatusbarActions)
                findPreference<Preference>("pref_key_system_statusbarcontrols_dt_right")?.setOnPreferenceClickListener(openStatusbarActions)
                findPreference<Preference>("pref_key_system_statusbarcontrols_longpress")?.setOnPreferenceClickListener(openStatusbarActions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (sub != "pref_key_system_island_music_cat") return
        val values = resources.getIntArray(R.array.global_actions_island_music_val)
        val labels = resources.getStringArray(R.array.global_actions_island_music)
        for (gesture in tv.withaibuild.customiuizer.mods.island.IslandMusicGesture.entries) {
            val key = "pref_key_${gesture.key}"
            val action = AppHelper.getIntOfAppPrefs(key + "_action", gesture.defaultAction)
            val index = values.indexOf(action).coerceAtLeast(0)
            val label = if (action == 8 || action == 9 || action == 20)
                AppHelper.getActionNameLocal(requireContext(), key)?.let { listOf(it.first, it.second).filter(String::isNotBlank).joinToString(": ") }
                    ?: labels[index] else labels[index]
            findPreference<Preference>(key)?.summary = label
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val key = when (requestCode) {
                1 -> "pref_key_system_clock_app"
                2 -> "pref_key_system_calendar_app"
                else -> null
            }
            if (key != null) {
                AppHelper.appPrefs!!.edit()
                    .putString(key, data?.getStringExtra("app"))
                    .putInt(key + "_user", data?.getIntExtra("user", 0) ?: 0)
                    .apply()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun requestAnimationScale(type: Int, seekBar: SeekBar) {
        val context = context ?: return
        val requestedValue = seekBar.progress / 10f
        val intent = Intent(GlobalActions.SET_ANIMATION_SCALE_ACTION).apply {
            setPackage("android")
            putExtra(GlobalActions.EXTRA_ANIMATION_SCALE_TYPE, type)
            putExtra(GlobalActions.EXTRA_ANIMATION_SCALE_VALUE, requestedValue)
        }
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: Intent?) {
                if (resultCode == GlobalActions.ACTION_HANDLED) return
                seekBar.progress = (Helpers.getAnimationScale(type) * 10).roundToInt()
                val message = if (resultCode == GlobalActions.ACTION_UNHANDLED) {
                    R.string.animation_scale_bridge_unavailable
                } else {
                    R.string.animation_scale_write_failed
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
        ModuleHelper.sendOrderedBroadcastWithIdentity(
            context,
            intent,
            null,
            resultReceiver,
            Handler(Looper.getMainLooper()),
            GlobalActions.ACTION_UNHANDLED
        )
    }
}
