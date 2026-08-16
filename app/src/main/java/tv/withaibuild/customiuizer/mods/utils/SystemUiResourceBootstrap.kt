package tv.withaibuild.customiuizer.mods.utils

import android.content.Context
import android.provider.Settings
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R

internal var statusbarTextIconLayoutResId = 0
    private set

internal fun setupSystemUiResources(mContext: Context) {
    statusbarTextIconLayoutResId = MainModule.resHooks.addFakeResource("statusbar_text_icon", R.layout.statusbar_text_icon, "layout")
    if (MainModule.mPrefs.getBoolean("system_statusbar_topmargin")) {
        val topMargin = MainModule.mPrefs.getInt("system_statusbar_topmargin_val", 1)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_padding_top", topMargin)
    }
    if (MainModule.mPrefs.getBoolean("system_statusbar_horizmargin")) {
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_padding_start", 0)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_padding_end", 0)
    }
    if (MainModule.mPrefs.getBoolean("system_cc_enable_style_switch")) {
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "integer", "force_use_control_panel", 0)
    }
    if (MainModule.mPrefs.getBoolean("system_volumetimer")) {
        val module_volume_timer_segments = intArrayOf(0, 1800, 3600, 7200, 10800, 14400, 18000, 21600, 28800, 36000, 43200)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "integer-array", "miui_volume_timer_segments", module_volume_timer_segments)
    }
    val iconSize = MainModule.mPrefs.getInt("system_statusbar_iconsize", 6)
    if (iconSize > 6) {
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_icon_size", iconSize)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_clock_size", iconSize + 0.4f)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_icon_drawing_size", iconSize)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_icon_drawing_size_dark", iconSize)
        val notifyPadding = 2.5f * iconSize / 13
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_notification_icon_padding", notifyPadding)
        val iconHeight = 20.5f * iconSize / 13
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_icon_height", iconHeight)
    }
    if (MainModule.mPrefs.getBoolean("system_cc_show_stepcount")) {
        StepCounterController.initContext(mContext)
    }
    if (!MainModule.mPrefs.getBoolean("system_drawer_hidedate")) {
        val drawerDateSize = MainModule.mPrefs.getInt("system_drawer_date_fontsize", 12)
        if (drawerDateSize > 12) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "qs_control_header_date_size", drawerDateSize)
        }
    }
    if (MainModule.mPrefs.getBoolean("system_taptounlock")) {
        MainModule.resHooks.setResReplacement("com.android.systemui", "string", "default_lockscreen_unlock_hint_text", R.string.system_taptounlock_title)
    }
    val userActivityTimeout = MainModule.mPrefs.getInt("system_lstimeout", 3)
    if (userActivityTimeout > 3) {
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "integer", "config_lockScreenDisplayTimeout", userActivityTimeout * 1000)
    }
    Settings.System.putLong(mContext.contentResolver, "systemui_restart_time", java.lang.System.currentTimeMillis())
}
