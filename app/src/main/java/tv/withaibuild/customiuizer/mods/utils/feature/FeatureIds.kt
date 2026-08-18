package tv.withaibuild.customiuizer.mods.utils.feature

import tv.withaibuild.customiuizer.mods.utils.FeatureId

/**
 * Typed identities for all features in the module.
 *
 * Keeping feature ids together makes it easy to see the complete list and avoids accidental
 * duplicate identities across different installers.
 *
 * Feature ID range: 0..244.
 */

data object PackagePermissionsFeatureId : FeatureId {
    override val id = 0
    override val name = "package_permissions"
}
data object TempHideOverlayAppFeatureId : FeatureId {
    override val id = 1
    override val name = "temp_hide_overlay_app"
}
data object OpenAppInFreeFormFeatureId : FeatureId {
    override val id = 2
    override val name = "open_app_in_free_form"
}
data object NavBarActionsFeatureId : FeatureId {
    override val id = 3
    override val name = "nav_bar_actions"
}
data object PowerDoubleTapActionFeatureId : FeatureId {
    override val id = 4
    override val name = "power_double_tap_action"
}
data object ScreenAnimFeatureId : FeatureId {
    override val id = 5
    override val name = "screen_anim"
}
data object AppLockTimeoutFeatureId : FeatureId {
    override val id = 6
    override val name = "app_lock_timeout"
}
data object ScreenDimTimeFeatureId : FeatureId {
    override val id = 7
    override val name = "screen_dim_time"
}
data object ToastTimeFeatureId : FeatureId {
    override val id = 8
    override val name = "toast_time"
}
data object RemoveSecureFeatureId : FeatureId {
    override val id = 9
    override val name = "remove_secure"
}
data object RemoveActStartConfirmFeatureId : FeatureId {
    override val id = 10
    override val name = "remove_act_start_confirm"
}
data object EnhancedSecurityFeatureId : FeatureId {
    override val id = 11
    override val name = "enhanced_security"
}
data object NoVersionCheckFeatureId : FeatureId {
    override val id = 12
    override val name = "no_version_check"
}
data object OrientationLockFeatureId : FeatureId {
    override val id = 13
    override val name = "orientation_lock"
}
data object NoDuckingFeatureId : FeatureId {
    override val id = 14
    override val name = "no_ducking"
}
data object CleanShareMenuServiceFeatureId : FeatureId {
    override val id = 15
    override val name = "clean_share_menu_service"
}
data object CleanOpenWithMenuServiceFeatureId : FeatureId {
    override val id = 16
    override val name = "clean_open_with_menu_service"
}
data object AutoBrightnessRangeFeatureId : FeatureId {
    override val id = 17
    override val name = "auto_brightness_range"
}
data object AutoBrightnessAfterScreenOffFeatureId : FeatureId {
    override val id = 18
    override val name = "auto_brightness_after_screen_off"
}
data object Disable72hStrongAuthFeatureId : FeatureId {
    override val id = 19
    override val name = "disable72h_strong_auth"
}
data object AppLockFeatureId : FeatureId {
    override val id = 20
    override val name = "app_lock"
}
data object SkipAppLockFeatureId : FeatureId {
    override val id = 21
    override val name = "skip_app_lock"
}
data object AlarmCompatServiceFeatureId : FeatureId {
    override val id = 22
    override val name = "alarm_compat_service"
}
data object NoCallInterruptionFeatureId : FeatureId {
    override val id = 23
    override val name = "no_call_interruption"
}
data object ForceCloseFeatureId : FeatureId {
    override val id = 24
    override val name = "force_close"
}
data object HideProximityWarningFeatureId : FeatureId {
    override val id = 25
    override val name = "hide_proximity_warning"
}
data object FirstVolumePressFeatureId : FeatureId {
    override val id = 26
    override val name = "first_volume_press"
}
data object NoSignatureVerifyServiceFeatureId : FeatureId {
    override val id = 27
    override val name = "no_signature_verify_service"
}
data object DisableSystemIntegrityFeatureId : FeatureId {
    override val id = 28
    override val name = "disable_system_integrity"
}
data object MuffledVibrationFeatureId : FeatureId {
    override val id = 29
    override val name = "muffled_vibration"
}
data object ClearAllTasksFeatureId : FeatureId {
    override val id = 30
    override val name = "clear_all_tasks"
}
data object ForceDarkAllAppsFeatureId : FeatureId {
    override val id = 31
    override val name = "force_dark_all_apps"
}
data object SetLockscreenWallpaperFeatureId : FeatureId {
    override val id = 32
    override val name = "set_lockscreen_wallpaper"
}
data object PowerKeyFeatureId : FeatureId {
    override val id = 33
    override val name = "power_key"
}
data object FingerprintHapticFailureFeatureId : FeatureId {
    override val id = 34
    override val name = "fingerprint_haptic_failure"
}
data object FingerprintScreenOnFeatureId : FeatureId {
    override val id = 35
    override val name = "fingerprint_screen_on"
}
data object NoFingerprintWakeFeatureId : FeatureId {
    override val id = 36
    override val name = "no_fingerprint_wake"
}
data object AppsDisableServiceFeatureId : FeatureId {
    override val id = 37
    override val name = "apps_disable_service"
}
data object DisableAnyNotificationBlockFeatureId : FeatureId {
    override val id = 38
    override val name = "disable_any_notification_block"
}
data object AllRotationsFeatureId : FeatureId {
    override val id = 39
    override val name = "all_rotations"
}
data object NoLightUpOnChargeFeatureId : FeatureId {
    override val id = 40
    override val name = "no_light_up_on_charge"
}
data object SelectiveVibrationFeatureId : FeatureId {
    override val id = 41
    override val name = "selective_vibration"
}
data object SelectiveToastsFeatureId : FeatureId {
    override val id = 42
    override val name = "selective_toasts"
}
data object FingerprintHapticSuccessFeatureId : FeatureId {
    override val id = 43
    override val name = "fingerprint_haptic_success"
}
data object VolumeMediaButtonsFeatureId : FeatureId {
    override val id = 44
    override val name = "volume_media_buttons"
}
data object MultiWindowPlusFeatureId : FeatureId {
    override val id = 45
    override val name = "multi_window_plus"
}
data object NoFloatingWindowBlacklistFeatureId : FeatureId {
    override val id = 46
    override val name = "no_floating_window_blacklist"
}
data object NoAccessDeviceLogsRequestFeatureId : FeatureId {
    override val id = 47
    override val name = "no_access_device_logs_request"
}
data object WallpaperScaleLevelFeatureId : FeatureId {
    override val id = 48
    override val name = "wallpaper_scale_level"
}
data object AllowUntrustedTouchFeatureId : FeatureId {
    override val id = 49
    override val name = "allow_untrusted_touch"
}

data object ForegroundMonitorFeatureId : FeatureId {
    override val id = 50
    override val name = "foreground_monitor"
}
data object TempHideOverlaySystemUiFeatureId : FeatureId {
    override val id = 51
    override val name = "temp_hide_overlay_system_ui"
}
data object AddCustomTileFeatureId : FeatureId {
    override val id = 52
    override val name = "add_custom_tile"
}
data object HideStatusBarWhenCaptureFeatureId : FeatureId {
    override val id = 53
    override val name = "hide_status_bar_when_capture"
}
data object NetworkIndicatorWifiFeatureId : FeatureId {
    override val id = 54
    override val name = "network_indicator_wifi"
}
data object DrawerBlurRatioFeatureId : FeatureId {
    override val id = 55
    override val name = "drawer_blur_ratio"
}
data object ChargeAnimationFeatureId : FeatureId {
    override val id = 56
    override val name = "charge_animation"
}
data object BetterPopupsHideDelayFeatureId : FeatureId {
    override val id = 57
    override val name = "betterpopups_hide_delay"
}
data object AssistGestureActionFeatureId : FeatureId {
    override val id = 58
    override val name = "assist_gesture_action"
}
data object NavBarButtonsFeatureId : FeatureId {
    override val id = 59
    override val name = "nav_bar_buttons"
}
data object ScramblePinFeatureId : FeatureId {
    override val id = 60
    override val name = "scramble_pin"
}
data object DoubleTapToSleepFeatureId : FeatureId {
    override val id = 61
    override val name = "double_tap_to_sleep"
}
data object StatusBarClockTweakFeatureId : FeatureId {
    override val id = 62
    override val name = "status_bar_clock_tweak"
}
data object CcClockTweakFeatureId : FeatureId {
    override val id = 63
    override val name = "cc_clock_tweak"
}
data object DisableFakeClockAnimFeatureId : FeatureId {
    override val id = 64
    override val name = "disable_fake_clock_anim"
}
data object CcClockCenterAlignFeatureId : FeatureId {
    override val id = 65
    override val name = "cc_clock_center_align"
}
data object NoScreenLockFeatureId : FeatureId {
    override val id = 66
    override val name = "no_screen_lock"
}
data object LockScreenAlbumArtFeatureId : FeatureId {
    override val id = 67
    override val name = "lock_screen_album_art"
}
data object ExpandHeadsUpFeatureId : FeatureId {
    override val id = 68
    override val name = "expand_heads_up"
}
data object BetterPopupsNoHideFeatureId : FeatureId {
    override val id = 69
    override val name = "betterpopups_no_hide"
}
data object BetterPopupsCenteredFeatureId : FeatureId {
    override val id = 70
    override val name = "betterpopups_centered"
}
data object ShowNotificationsAfterUnlockFeatureId : FeatureId {
    override val id = 71
    override val name = "show_notifications_after_unlock"
}
data object NotificationRowMenuFeatureId : FeatureId {
    override val id = 72
    override val name = "notification_row_menu"
}
data object HideDismissViewFeatureId : FeatureId {
    override val id = 73
    override val name = "hide_dismiss_view"
}
data object HideNotificationAccessIconFeatureId : FeatureId {
    override val id = 74
    override val name = "hide_notification_access_icon"
}
data object HideNoNotificationsFeatureId : FeatureId {
    override val id = 75
    override val name = "hide_no_notifications"
}
data object HideNavBarFeatureId : FeatureId {
    override val id = 76
    override val name = "hide_nav_bar"
}
data object HideNavBarBeforeScreenshotFeatureId : FeatureId {
    override val id = 77
    override val name = "hide_nav_bar_before_screenshot"
}
data object AudioVisualizerFeatureId : FeatureId {
    override val id = 78
    override val name = "audio_visualizer"
}
data object ControlCenterPluginFeatureId : FeatureId {
    override val id = 79
    override val name = "control_center_plugin"
}
data object BatteryIndicatorFeatureId : FeatureId {
    override val id = 80
    override val name = "battery_indicator"
}
data object DisableAnyNotificationFeatureId : FeatureId {
    override val id = 81
    override val name = "disable_any_notification"
}
data object LockScreenShortcutFeatureId : FeatureId {
    override val id = 82
    override val name = "lock_screen_shortcut"
}
data object MobileNetworkTypeFeatureId : FeatureId {
    override val id = 83
    override val name = "mobile_network_type"
}
data object StatusBarIconsPositionAdjustFeatureId : FeatureId {
    override val id = 84
    override val name = "status_bar_icons_position_adjust"
}
data object MonitorDeviceInfoFeatureId : FeatureId {
    override val id = 85
    override val name = "monitor_device_info"
}
data object StatusBarClockPositionFeatureId : FeatureId {
    override val id = 86
    override val name = "status_bar_clock_position"
}
data object StatusBarStyleBatteryIconFeatureId : FeatureId {
    override val id = 87
    override val name = "status_bar_style_battery_icon"
}
data object LockScreenTopMarginFeatureId : FeatureId {
    override val id = 88
    override val name = "lock_screen_top_margin"
}
data object HorizMarginFeatureId : FeatureId {
    override val id = 89
    override val name = "horiz_margin"
}
data object BrightnessPctFeatureId : FeatureId {
    override val id = 90
    override val name = "brightness_pct"
}
data object HideLockScreenStatusBarFeatureId : FeatureId {
    override val id = 91
    override val name = "hide_lock_screen_status_bar"
}
data object HideLockScreenClockFeatureId : FeatureId {
    override val id = 92
    override val name = "hide_lock_screen_clock"
}
data object ForceClockUseSystemFontsFeatureId : FeatureId {
    override val id = 93
    override val name = "force_clock_use_system_fonts"
}
data object HideLockScreenHintFeatureId : FeatureId {
    override val id = 94
    override val name = "hide_lock_screen_hint"
}
data object AllowAllKeyguardFeatureId : FeatureId {
    override val id = 95
    override val name = "allow_all_keyguard"
}
data object AllowAllFloatFeatureId : FeatureId {
    override val id = 96
    override val name = "allow_all_float"
}
data object LockScreenAlarmFeatureId : FeatureId {
    override val id = 97
    override val name = "lock_screen_alarm"
}
data object StatusBarGesturesFeatureId : FeatureId {
    override val id = 98
    override val name = "status_bar_gestures"
}
data object NetSpeedIntervalFeatureId : FeatureId {
    override val id = 99
    override val name = "net_speed_interval"
}
data object DetailedNetSpeedFeatureId : FeatureId {
    override val id = 100
    override val name = "detailed_net_speed"
}
data object NetSpeedStyleFeatureId : FeatureId {
    override val id = 101
    override val name = "net_speed_style"
}
data object TapToUnlockFeatureId : FeatureId {
    override val id = 102
    override val name = "tap_to_unlock"
}
data object NoSOSFeatureId : FeatureId {
    override val id = 103
    override val name = "no_sos"
}
data object RemovePackageNotificationsLimitFeatureId : FeatureId {
    override val id = 104
    override val name = "remove_package_notifications_limit"
}
data object DisableFoldNotificationsFeatureId : FeatureId {
    override val id = 105
    override val name = "disable_fold_notifications"
}
data object ChargingInfoFeatureId : FeatureId {
    override val id = 107
    override val name = "charging_info"
}
data object SecureQSTilesFeatureId : FeatureId {
    override val id = 108
    override val name = "secure_qs_tiles"
}
data object MuteVisibleNotificationsFeatureId : FeatureId {
    override val id = 109
    override val name = "mute_visible_notifications"
}
data object HideIconsBattery1FeatureId : FeatureId {
    override val id = 110
    override val name = "hide_icons_battery1"
}
data object HideIconsBattery2FeatureId : FeatureId {
    override val id = 111
    override val name = "hide_icons_battery2"
}
data object DisplayWifiStandardFeatureId : FeatureId {
    override val id = 112
    override val name = "display_wifi_standard"
}
data object HidePrivacyIndicatorFeatureId : FeatureId {
    override val id = 113
    override val name = "hide_privacy_indicator"
}
data object HideIconsSignalFeatureId : FeatureId {
    override val id = 114
    override val name = "hide_icons_signal"
}
data object HideIconsVoWiFiFeatureId : FeatureId {
    override val id = 115
    override val name = "hide_icons_vowifi"
}
data object HideIconsSelectiveAlarmFeatureId : FeatureId {
    override val id = 116
    override val name = "hide_icons_selective_alarm"
}
data object ReplaceShortcutAppFeatureId : FeatureId {
    override val id = 117
    override val name = "replace_shortcut_app"
}
data object QSHapticFeatureId : FeatureId {
    override val id = 118
    override val name = "qs_haptic"
}
data object CollapseCCAfterClickFeatureId : FeatureId {
    override val id = 119
    override val name = "collapse_cc_after_click"
}
data object LongClickTileOpenInFreeFormFeatureId : FeatureId {
    override val id = 120
    override val name = "long_click_tile_open_in_free_form"
}
data object SwitchCCAndNotificationFeatureId : FeatureId {
    override val id = 121
    override val name = "switch_cc_and_notification"
}
data object ExpandNotificationsFeatureId : FeatureId {
    override val id = 122
    override val name = "expand_notifications"
}
data object HideMobileNetworkIndicatorFeatureId : FeatureId {
    override val id = 123
    override val name = "hide_mobile_network_indicator"
}
data object ExtendedPowerMenuFeatureId : FeatureId {
    override val id = 124
    override val name = "extended_power_menu"
}
data object HideIconsFeatureId : FeatureId {
    override val id = 125
    override val name = "hide_icons"
}
data object HideIconsFromSystemManagerFeatureId : FeatureId {
    override val id = 126
    override val name = "hide_icons_from_system_manager"
}
data object BetterPopupsAllowFloatFeatureId : FeatureId {
    override val id = 127
    override val name = "betterpopups_allow_float"
}
data object AutoDismissExpandedPopupsFeatureId : FeatureId {
    override val id = 128
    override val name = "auto_dismiss_expanded_popups"
}
data object DisableHeadsUpWhenMuteFeatureId : FeatureId {
    override val id = 129
    override val name = "disable_heads_up_when_mute"
}
data object MinimalNotificationViewFeatureId : FeatureId {
    override val id = 130
    override val name = "minimal_notification_view"
}
data object NotificationChannelSettingsFeatureId : FeatureId {
    override val id = 131
    override val name = "notification_channel_settings"
}
data object MaxNotificationIconsFeatureId : FeatureId {
    override val id = 132
    override val name = "max_notification_icons"
}
data object MobileTypeSingleFeatureId : FeatureId {
    override val id = 133
    override val name = "mobile_type_single"
}
data object StatusBarDigitalSignalFeatureId : FeatureId {
    override val id = 134
    override val name = "status_bar_digital_signal"
}
data object DualRowSignalFeatureId : FeatureId {
    override val id = 135
    override val name = "dual_row_signal"
}
data object DualRowsStatusbarFeatureId : FeatureId {
    override val id = 136
    override val name = "dual_rows_statusbar"
}
data object ColorizeNotificationCardFeatureId : FeatureId {
    override val id = 137
    override val name = "colorize_notification_card"
}
data object OpenNotifyInFloatingWindowFeatureId : FeatureId {
    override val id = 138
    override val name = "open_notify_in_floating_window"
}
data object DisableSideBarSuggestionFeatureId : FeatureId {
    override val id = 139
    override val name = "disable_side_bar_suggestion"
}
data object HideSafeVolumeDlgFeatureId : FeatureId {
    override val id = 140
    override val name = "hide_safe_volume_dlg"
}
data object HideLockscreenZenModeFeatureId : FeatureId {
    override val id = 141
    override val name = "hide_lockscreen_zen_mode"
}
data object DisableKeyguardEditorFeatureId : FeatureId {
    override val id = 142
    override val name = "disable_keyguard_editor"
}
data object NoPasswordFeatureId : FeatureId {
    override val id = 143
    override val name = "no_password"
}
data object NotificationImportanceFeatureId : FeatureId {
    override val id = 144
    override val name = "notification_importance"
}
data object NoLightUpOnChargeSystemUiFeatureId : FeatureId {
    override val id = 145
    override val name = "no_light_up_on_charge_system_ui"
}

// Common package-ready (MainModule onPackageReady) features
data object StatusBarHeightFeatureId : FeatureId {
    override val id = 146
    override val name = "status_bar_height"
}
data object AlarmCompatFeatureId : FeatureId {
    override val id = 147
    override val name = "alarm_compat"
}

// Launcher package-ready features
data object LauncherFolderColumnsResFeatureId : FeatureId {
    override val id = 148
    override val name = "launcher_folder_columns_res"
}
data object LauncherHorizontalSpacingFeatureId : FeatureId {
    override val id = 149
    override val name = "launcher_horizontal_spacing"
}
data object LauncherIndicatorHeightFeatureId : FeatureId {
    override val id = 150
    override val name = "launcher_indicator_height"
}
data object LauncherIndicatorMarginTopFeatureId : FeatureId {
    override val id = 151
    override val name = "launcher_indicator_margin_top"
}
data object LauncherUnlockGridsFeatureId : FeatureId {
    override val id = 152
    override val name = "launcher_unlock_grids"
}
data object LauncherDockTitlesFeatureId : FeatureId {
    override val id = 153
    override val name = "launcher_dock_titles"
}
data object LauncherDisableLogFeatureId : FeatureId {
    override val id = 154
    override val name = "launcher_disable_log"
}
data object LauncherWorkspaceCellPaddingTopFeatureId : FeatureId {
    override val id = 155
    override val name = "launcher_workspace_cell_padding_top"
}
data object LauncherDockMarginTopFeatureId : FeatureId {
    override val id = 156
    override val name = "launcher_dock_margin_top"
}
data object LauncherDockMarginBottomFeatureId : FeatureId {
    override val id = 157
    override val name = "launcher_dock_margin_bottom"
}
data object LauncherDockHeightFeatureId : FeatureId {
    override val id = 158
    override val name = "launcher_dock_height"
}
data object LauncherPrivacyAppsGestFeatureId : FeatureId {
    override val id = 159
    override val name = "launcher_privacy_apps_gest"
}

// Launcher post-attach features
data object LauncherHomescreenSwipesFeatureId : FeatureId {
    override val id = 160
    override val name = "launcher_homescreen_swipes"
}
data object LauncherHotSeatSwipesFeatureId : FeatureId {
    override val id = 161
    override val name = "launcher_hot_seat_swipes"
}
data object LauncherShakeFeatureId : FeatureId {
    override val id = 162
    override val name = "launcher_shake"
}
data object LauncherDoubleTapFeatureId : FeatureId {
    override val id = 163
    override val name = "launcher_double_tap"
}
data object LauncherPinchFeatureId : FeatureId {
    override val id = 164
    override val name = "launcher_pinch"
}
data object LauncherFolderColumnsFeatureId : FeatureId {
    override val id = 165
    override val name = "launcher_folder_columns"
}
data object LauncherIconScaleFeatureId : FeatureId {
    override val id = 166
    override val name = "launcher_icon_scale"
}
data object LauncherTitleFontSizeFeatureId : FeatureId {
    override val id = 167
    override val name = "launcher_title_font_size"
}
data object LauncherTitleTopMarginFeatureId : FeatureId {
    override val id = 168
    override val name = "launcher_title_top_margin"
}
data object LauncherNoClockHideFeatureId : FeatureId {
    override val id = 169
    override val name = "launcher_no_clock_hide"
}
data object LauncherRenameShortcutsFeatureId : FeatureId {
    override val id = 170
    override val name = "launcher_rename_shortcuts"
}
data object LauncherTitleShadowFeatureId : FeatureId {
    override val id = 171
    override val name = "launcher_title_shadow"
}
data object LauncherHideNavBarFeatureId : FeatureId {
    override val id = 172
    override val name = "launcher_hide_nav_bar"
}
data object LauncherInfiniteScrollFeatureId : FeatureId {
    override val id = 173
    override val name = "launcher_infinite_scroll"
}
data object LauncherHideTitlesFeatureId : FeatureId {
    override val id = 174
    override val name = "launcher_hide_titles"
}
data object LauncherFixAppInfoLaunchFeatureId : FeatureId {
    override val id = 175
    override val name = "launcher_fix_app_info_launch"
}
data object LauncherNoWidgetOnlyFeatureId : FeatureId {
    override val id = 176
    override val name = "launcher_no_widget_only"
}
data object LauncherReversePortraitFeatureId : FeatureId {
    override val id = 177
    override val name = "launcher_reverse_portrait"
}
data object LauncherMaxHotseatIconsFeatureId : FeatureId {
    override val id = 178
    override val name = "launcher_max_hotseat_icons"
}
data object LauncherCloseFolderOnLaunchFeatureId : FeatureId {
    override val id = 179
    override val name = "launcher_close_folder_on_launch"
}
data object LauncherRecentsBlurFeatureId : FeatureId {
    override val id = 180
    override val name = "launcher_recents_blur"
}
data object LauncherBackGestureAreaHeightFeatureId : FeatureId {
    override val id = 181
    override val name = "launcher_back_gesture_area_height"
}
data object LauncherBackGestureAreaWidthFeatureId : FeatureId {
    override val id = 182
    override val name = "launcher_back_gesture_area_width"
}
data object LauncherFsgesturesFeatureId : FeatureId {
    override val id = 183
    override val name = "launcher_fsgestures"
}
data object LauncherHideMemoryCleanFeatureId : FeatureId {
    override val id = 184
    override val name = "launcher_hide_memory_clean"
}
data object LauncherDisableWallpaperScaleFeatureId : FeatureId {
    override val id = 185
    override val name = "launcher_disable_wallpaper_scale"
}
data object LauncherHideStatusBarInRecentsFeatureId : FeatureId {
    override val id = 186
    override val name = "launcher_hide_status_bar_in_recents"
}
data object LauncherMultiWindowPlusFeatureId : FeatureId {
    override val id = 187
    override val name = "launcher_multi_window_plus"
}
data object LauncherFixAnimFeatureId : FeatureId {
    override val id = 188
    override val name = "launcher_fix_anim"
}
data object LauncherHideSeekPointsFeatureId : FeatureId {
    override val id = 189
    override val name = "launcher_hide_seek_points"
}
data object LauncherPrivacyFolderFeatureId : FeatureId {
    override val id = 190
    override val name = "launcher_privacy_folder"
}
data object LauncherHideFromRecentsFeatureId : FeatureId {
    override val id = 191
    override val name = "launcher_hide_from_recents"
}
data object LauncherFolderBlurFeatureId : FeatureId {
    override val id = 192
    override val name = "launcher_folder_blur"
}
data object LauncherNoUnlockAnimationFeatureId : FeatureId {
    override val id = 193
    override val name = "launcher_no_unlock_animation"
}
data object LauncherNoZoomAnimationFeatureId : FeatureId {
    override val id = 194
    override val name = "launcher_no_zoom_animation"
}
data object LauncherUseOldLaunchAnimationFeatureId : FeatureId {
    override val id = 195
    override val name = "launcher_use_old_launch_animation"
}
data object LauncherCloseDrawerOnLaunchFeatureId : FeatureId {
    override val id = 196
    override val name = "launcher_close_drawer_on_launch"
}
data object LauncherHorizontalWidgetSpacingFeatureId : FeatureId {
    override val id = 197
    override val name = "launcher_horizontal_widget_spacing"
}
data object LauncherAssistGestureActionFeatureId : FeatureId {
    override val id = 198
    override val name = "launcher_assist_gesture_action"
}
data object LauncherSwipeAndStopActionFeatureId : FeatureId {
    override val id = 199
    override val name = "launcher_swipe_and_stop_action"
}
data object LauncherCloseOnLaunchFeatureId : FeatureId {
    override val id = 200
    override val name = "launcher_close_on_launch"
}
data object LauncherResizableWidgetsFeatureId : FeatureId {
    override val id = 201
    override val name = "launcher_resizable_widgets"
}
data object LauncherWallpaperColorModeFeatureId : FeatureId {
    override val id = 202
    override val name = "launcher_wallpaper_color_mode"
}

// Input method features
data object InputMethodVolumeCursorFeatureId : FeatureId {
    override val id = 203
    override val name = "input_method_volume_cursor"
}
data object InputMethodFixBottomMarginFeatureId : FeatureId {
    override val id = 204
    override val name = "input_method_fix_bottom_margin"
}
data object InputMethodGboardPaddingFeatureId : FeatureId {
    override val id = 205
    override val name = "input_method_gboard_padding"
}

// Settings features
data object SettingsMiuizerIconFeatureId : FeatureId {
    override val id = 206
    override val name = "settings_miuizer_icon"
}
data object SettingsDisableAnyNotificationFeatureId : FeatureId {
    override val id = 207
    override val name = "settings_disable_any_notification"
}
data object SettingsNotificationImportanceFeatureId : FeatureId {
    override val id = 208
    override val name = "settings_notification_importance"
}
data object SettingsViewWifiPasswordFeatureId : FeatureId {
    override val id = 209
    override val name = "settings_view_wifi_password"
}

// Security center features
data object SecurityCenterAppInfoFeatureId : FeatureId {
    override val id = 210
    override val name = "security_center_app_info"
}
data object SecurityCenterAppsDisableFeatureId : FeatureId {
    override val id = 211
    override val name = "security_center_apps_disable"
}
data object SecurityCenterAppsRestrictFeatureId : FeatureId {
    override val id = 212
    override val name = "security_center_apps_restrict"
}
data object SecurityCenterHideReportButtonFeatureId : FeatureId {
    override val id = 213
    override val name = "security_center_hide_report_button"
}
data object SecurityCenterScrambleAppLockPinFeatureId : FeatureId {
    override val id = 214
    override val name = "security_center_scramble_app_lock_pin"
}
data object SecurityCenterAppsDefaultSortFeatureId : FeatureId {
    override val id = 215
    override val name = "security_center_apps_default_sort"
}
data object SecurityCenterInterceptPermFeatureId : FeatureId {
    override val id = 216
    override val name = "security_center_intercept_perm"
}
data object SecurityCenterOpenByDefaultFeatureId : FeatureId {
    override val id = 217
    override val name = "security_center_open_by_default"
}
data object SecurityCenterSkipSecurityScanFeatureId : FeatureId {
    override val id = 218
    override val name = "security_center_skip_security_scan"
}
data object SecurityCenterShowTempInBatteryFeatureId : FeatureId {
    override val id = 219
    override val name = "security_center_show_temp_in_battery"
}
data object SecurityCenterDisableSideBarSuggestionFeatureId : FeatureId {
    override val id = 220
    override val name = "security_center_disable_side_bar_suggestion"
}
data object SecurityCenterDisableDockSuggestFeatureId : FeatureId {
    override val id = 221
    override val name = "security_center_disable_dock_suggest"
}
data object SecurityCenterAddSideBarExpandReceiverFeatureId : FeatureId {
    override val id = 222
    override val name = "security_center_add_side_bar_expand_receiver"
}
data object SecurityCenterNoLowBatteryWarningFeatureId : FeatureId {
    override val id = 223
    override val name = "security_center_no_low_battery_warning"
}
data object SecurityCenterPrivacyAppsLayoutFeatureId : FeatureId {
    override val id = 224
    override val name = "security_center_privacy_apps_layout"
}
data object SecurityCenterPersistPrivacyThumbnailBlurFeatureId : FeatureId {
    override val id = 225
    override val name = "security_center_persist_privacy_thumbnail_blur"
}

// Phone features
data object PhoneShowCallUiFeatureId : FeatureId {
    override val id = 226
    override val name = "phone_show_call_ui"
}
data object PhoneInCallBrightnessFeatureId : FeatureId {
    override val id = 227
    override val name = "phone_in_call_brightness"
}
data object PhoneAnswerCallInHeadUpFeatureId : FeatureId {
    override val id = 228
    override val name = "phone_answer_call_in_head_up"
}

// Power keeper features
data object PowerKeeperAppsRestrictFeatureId : FeatureId {
    override val id = 229
    override val name = "power_keeper_apps_restrict"
}
data object PowerKeeperPersistBatteryOptimizationFeatureId : FeatureId {
    override val id = 230
    override val name = "power_keeper_persist_battery_optimization"
}

// Guard provider features
data object GuardProviderDisableDefraudAppsFeatureId : FeatureId {
    override val id = 231
    override val name = "guard_provider_disable_defraud_apps"
}

// Package installer features
data object PackageInstallerMiuiPackageFeatureId : FeatureId {
    override val id = 232
    override val name = "package_installer_miui_package"
}
data object PackageInstallerAppInfoFeatureId : FeatureId {
    override val id = 233
    override val name = "package_installer_app_info"
}
data object PackageInstallerPurifyFeatureId : FeatureId {
    override val id = 234
    override val name = "package_installer_purify"
}

// Media features
data object MediaDisableUnlockWallpaperScaleFeatureId : FeatureId {
    override val id = 235
    override val name = "media_disable_unlock_wallpaper_scale"
}
data object MediaScreenshotConfigFeatureId : FeatureId {
    override val id = 236
    override val name = "media_screenshot_config"
}
data object MediaGalleryScreenshotPathFeatureId : FeatureId {
    override val id = 237
    override val name = "media_gallery_screenshot_path"
}

// Android package features
data object AndroidCleanShareMenuFeatureId : FeatureId {
    override val id = 238
    override val name = "android_clean_share_menu"
}
data object AndroidCleanOpenWithMenuFeatureId : FeatureId {
    override val id = 239
    override val name = "android_clean_open_with_menu"
}
data object AndroidAllRotationsFeatureId : FeatureId {
    override val id = 240
    override val name = "android_all_rotations"
}

// Generic app post-attach features
data object LauncherPostAttachFeatureId : FeatureId {
    override val id = 241
    override val name = "launcher_post_attach"
}
data object GenericAppStatusBarBackgroundFeatureId : FeatureId {
    override val id = 242
    override val name = "generic_app_status_bar_background"
}
data object GenericAppNoOverscrollFeatureId : FeatureId {
    override val id = 243
    override val name = "generic_app_no_overscroll"
}
data object GenericAppVolumeMediaPlayerFeatureId : FeatureId {
    override val id = 244
    override val name = "generic_app_volume_media_player"
}

// system_server status bar Insets synchronization
data object StatusBarHeightInsetsFeatureId : FeatureId {
    override val id = 245
    override val name = "status_bar_height_insets"
}

// SystemUI HyperOS 1 StrongToast presentation
data object StrongToastPresentationFeatureId : FeatureId {
    override val id = 246
    override val name = "strong_toast_presentation"
}

data object DisableWindowBlursFeatureId : FeatureId {
    override val id = 247
    override val name = "disable_window_blurs"
}

data object LauncherRecentsCardStyleFeatureId : FeatureId {
    override val id = 248
    override val name = "launcher_recents_card_style"
}

data object AnimationScaleBridgeFeatureId : FeatureId {
    override val id = 249
    override val name = "animation_scale_bridge"
}

data object UpdaterServicesBridgeFeatureId : FeatureId {
    override val id = 250
    override val name = "updater_services_bridge"
}

data object UsbDefaultFunctionFeatureId : FeatureId {
    override val id = 251
    override val name = "system_usb_default_function"
}

data object HideImeDismissButtonFeatureId : FeatureId {
    override val id = 252
    override val name = "systemui_hide_ime_dismiss_button"
}

data object StatusBarContentGeometryFeatureId : FeatureId {
    override val id = 253
    override val name = "systemui_statusbar_content_geometry"
}
