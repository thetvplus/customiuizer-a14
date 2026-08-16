# A14 Process Matrix

This matrix is generated from source (`tools/extract_process_matrix.py`). It records every `FeatureSpec` discovered in `mods/utils/feature/`.

## LSPosed scope list

- `android`
- `com.android.incallui`
- `com.android.permissioncontroller`
- `com.android.settings`
- `com.android.systemui`
- `com.google.android.permissioncontroller`
- `com.miui.gallery`
- `com.miui.guardprovider`
- `com.miui.home`
- `com.miui.miwallpaper`
- `com.miui.packageinstaller`
- `com.miui.powerkeeper`
- `com.miui.screenshot`
- `com.miui.securitycenter`
- `system`

## Package -> Installer routing (MainModule.java)

| Package | Installer | Notes |
|---|---|---|
| `android` | `AndroidPackageInstaller` |  |
| `com.android.incallui` | `PhoneInstaller` |  |
| `com.android.packageinstaller` | `PackageInstallerRouter` |  |
| `com.android.permissioncontroller` | `PermissionControllerInstaller` |  |
| `com.android.settings` | `SettingsInstaller` | Explicitly denies `com.android.settings:remote` |
| `com.android.systemui` | `SystemUiInstaller` | ReflectionCache + SystemUIInitializer hook; post-init prefs |
| `com.baidu.input` | `InputMethodInstaller` |  |
| `com.baidu.input_mi` | `InputMethodInstaller` |  |
| `com.google.android.inputmethod` | `InputMethodInstaller` |  |
| `com.google.android.permissioncontroller` | `PermissionControllerInstaller` |  |
| `com.iflytek.inputmethod` | `InputMethodInstaller` |  |
| `com.iflytek.inputmethod.miui` | `InputMethodInstaller` |  |
| `com.miui.gallery` | `MediaInstaller` |  |
| `com.miui.guardprovider` | `GuardProviderInstaller` |  |
| `com.miui.home` | `LauncherInstaller` | ReflectionCache; may also trigger GenericAppInstaller post-attach |
| `com.miui.miwallpaper` | `MediaInstaller` |  |
| `com.miui.packageinstaller` | `PackageInstallerRouter` | PackageInstallerRouter handles both MIUI and AOSP installer |
| `com.miui.powerkeeper` | `PowerKeeperInstaller` |  |
| `com.miui.screenshot` | `MediaInstaller` |  |
| `com.miui.securitycenter` | `SecurityCenterInstaller` | Explicitly denies `com.miui.securitycenter.bootaware` |
| `com.sohu.inputmethod.sogou` | `InputMethodInstaller` |  |
| `com.sohu.inputmethod.sogou.xiaomi` | `InputMethodInstaller` |  |
| `com.tencent.wetype` | `InputMethodInstaller` |  |
| `com.touchtype.swiftkey` | `InputMethodInstaller` |  |

## Feature matrix (CSV: `A14_PROCESS_MATRIX.csv`)

| ID | Feature | Pref key | Target | Phase | Installer | Install hook | Allowed process | Denied process |
|---|---|---|---|---|---|---|---|---|
| 1 | Temp Hide Overlay App | `system_screenshot_overlay` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemWindowHooks.TempHideOverlayAppHook(lpparam)` | system_server |  |
| 2 | Open App In Free Form | `system_notify_openinfw` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemWindowHooks.OpenAppInFreeFormHook(lpparam)` | system_server |  |
| 3 | Nav Bar Actions | `controls_backlong_action` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Controls.NavBarActionsHook(lpparam)` | system_server |  |
| 4 | Power Double Tap Action | `controls_powerdt_action` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Controls.PowerDoubleTapActionHook(lpparam)` | system_server |  |
| 5 | Screen Anim | `system_screenanim_duration` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemDisplayHooks.ScreenAnimHook(lpparam)` | system_server |  |
| 6 | App Lock Timeout | `system_applock_timeout` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemLockScreenHooks.AppLockTimeoutHook(lpparam)` | system_server |  |
| 7 | Screen Dim Time | `system_dimtime` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemDisplayHooks.ScreenDimTimeHook(lpparam)` | system_server |  |
| 8 | Toast Time | `system_toasttime` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `ModsSystem.ToastTimeHook(lpparam)` | system_server |  |
| 9 | Remove Secure | `system_removesecure` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemSecurityHooks.RemoveSecureHook(lpparam)` | system_server |  |
| 10 | Remove Act Start Confirm | `system_remove_startactconfirm` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemSecurityHooks.RemoveActStartConfirmHook(lpparam)` | system_server |  |
| 11 | Enhanced Security | `system_securelock` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemLockScreenHooks.EnhancedSecurityHook(lpparam)` | system_server |  |
| 12 | No Version Check | `system_downgrade` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemSecurityHooks.NoVersionCheckHook(lpparam)` | system_server |  |
| 13 | Orientation Lock | `system_orientationlock` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemWindowHooks.OrientationLockHook(lpparam)` | system_server |  |
| 14 | No Ducking | `system_noducking` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemAudioHooks.NoDuckingHook(lpparam)` | system_server |  |
| 15 | Clean Share Menu Service | `system_cleanshare` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemShareMenuHooks.CleanShareMenuServiceHook(lpparam)` | system_server |  |
| 16 | Clean Open With Menu Service | `system_cleanopenwith` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemShareMenuHooks.CleanOpenWithMenuServiceHook(lppar...` | system_server |  |
| 17 | Auto Brightness Range | `system_autobrightness` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemDisplayHooks.AutoBrightnessRangeHook(lpparam)` | system_server |  |
| 18 | Auto Brightness After Screen Off | `system_autobrightness_reset_when_screenoff` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemDisplayHooks.AutoBrightnessAfterScreenOffHook(lpp...` | system_server |  |
| 19 | Disable72h Strong Auth | `system_lockscreen_disable_strongauth_72h` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemLockScreenHooks.Disable72hStrongAuthHook(lpparam)` | system_server |  |
| 20 | App Lock | `system_applock` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemLockScreenHooks.AppLockHook(lpparam)` | system_server |  |
| 21 | Skip App Lock | `system_applock_skip` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemLockScreenHooks.SkipAppLockHook(lpparam)` | system_server |  |
| 22 | Alarm Compat Service | `various_alarmcompat` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Various.AlarmCompatServiceHook(lpparam)` | system_server |  |
| 23 | No Call Interruption | `system_ignorecalls` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemAudioHooks.NoCallInterruptionHook(lpparam)` | system_server |  |
| 24 | Force Close | `system_forceclose` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `ModsSystem.ForceCloseHook(lpparam)` | system_server |  |
| 25 | Hide Proximity Warning | `system_hideproxywarn` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `ModsSystem.HideProximityWarningHook(lpparam)` | system_server |  |
| 26 | First Volume Press | `system_firstpress` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemAudioHooks.FirstVolumePressHook(lpparam)` | system_server |  |
| 27 | No Signature Verify Service | `system_apksign` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemSecurityHooks.NoSignatureVerifyServiceHook(lppara...` | system_server |  |
| 28 | Disable System Integrity | `system_disableintegrity` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemSecurityHooks.DisableSystemIntegrityHook(lpparam)` | system_server |  |
| 29 | Muffled Vibration | `system_vibration_amp` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemAudioHooks.MuffledVibrationHook(lpparam)` | system_server |  |
| 30 | Clear All Tasks | `system_clearalltasks` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `ModsSystem.ClearAllTasksHook(lpparam)` | system_server |  |
| 31 | Force Dark All Apps | `system_force_darken_allapps` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemDisplayHooks.ForceDarkAllAppsHook(lpparam)` | system_server |  |
| 32 | Set Lockscreen Wallpaper | `system_lswallpaper` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemLockScreenHooks.SetLockscreenWallpaperHook(lppara...` | system_server |  |
| 33 | Power Key | `controls_powerflash` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Controls.PowerKeyHook(lpparam)` | system_server |  |
| 34 | Fingerprint Haptic Failure | `controls_fingerprintfailure` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Controls.FingerprintHapticFailureHook(lpparam)` | system_server |  |
| 35 | Fingerprint Screen On | `controls_fingerprintscreen` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Controls.FingerprintScreenOnHook(lpparam)` | system_server |  |
| 36 | No Fingerprint Wake | `controls_fingerprintwake` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Controls.NoFingerprintWakeHook(lpparam)` | system_server |  |
| 37 | Apps Disable Service | `various_disableapp` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Various.AppsDisableServiceHook(lpparam)` | system_server |  |
| 38 | Disable Any Notification Block | `system_disableanynotif` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemNotificationHooks.DisableAnyNotificationBlockHook...` | system_server |  |
| 39 | All Rotations | `system_allrotations2` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemWindowHooks.AllRotationsHook(lpparam)` | system_server |  |
| 40 | No Light Up On Charge | `system_nolightuponcharges` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemDisplayHooks.NoLightUpOnChargeHook(lpparam)` | system_server |  |
| 41 | Selective Vibration | `system_vibration` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemAudioHooks.SelectiveVibrationHook(lpparam)` | system_server |  |
| 42 | Selective Toasts | `system_blocktoasts` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `ModsSystem.SelectiveToastsHook(lpparam)` | system_server |  |
| 43 | Fingerprint Haptic Success | `controls_fingerprintsuccess` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Controls.FingerprintHapticSuccessHook(lpparam)` | system_server |  |
| 44 | Volume Media Buttons | `controls_volumemedia_up` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `Controls.VolumeMediaButtonsHook(lpparam)` | system_server |  |
| 45 | Multi Window Plus | `system_fw_splitscreen` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemWindowHooks.MultiWindowPlusHook(lpparam)` | system_server |  |
| 46 | No Floating Window Blacklist | `system_fw_noblacklist` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemWindowHooks.NoFloatingWindowBlacklistHook(lpparam...` | system_server |  |
| 47 | No Access Device Logs Request | `various_disable_access_devicelogs` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemSecurityHooks.NoAccessDeviceLogsRequest(lpparam)` | system_server |  |
| 48 | Wallpaper Scale Level | `system_other_wallpaper_scale` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemDisplayHooks.WallpaperScaleLevelHook(lpparam)` | system_server |  |
| 49 | Allow Untrusted Touch | `various_allow_untrusted_touch` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemWindowHooks.AllowUntrustedTouchHook(lpparam)` | system_server |  |
| 50 | Foreground Monitor | `various_showcallui` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `GlobalActions.setupForegroundMonitor(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 51 | Temp Hide Overlay System UI | `system_screenshot_overlay` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIScreenshotHooks.TempHideOverlaySystemUIHook(lpp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 52 | Add Custom Tile | `system_fivegtile` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.AddCustomTileHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 53 | Hide Status Bar When Capture | `system_hidestatusbar_whenscreenshot` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIScreenshotHooks.HideStatusBarWhenCaptureHook(lp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 54 | Network Indicator WiFi | `system_networkindicator_wifi` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `ModsSystem.NetworkIndicatorWifi(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 55 | Drawer Blur Ratio | `system_drawer_blur` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemDisplayHooks.DrawerBlurRatioHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 56 | Charge Animation | `system_chargeanimtime` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemDisplayHooks.ChargeAnimationHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 57 | Better Popups Hide Delay | `system_betterpopups_delay` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.BetterPopupsHideDelayHook(lppar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 58 | Assist Gesture Action | `controls_fsg_assist_left_action` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `Controls.AssistGestureActionHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 59 | Nav Bar Buttons | `controls_navbarleft_action` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `Controls.NavBarButtonsHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 60 | Scramble PIN | `system_scramblepin` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.ScramblePINHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 61 | Double Tap To Sleep | `system_dttosleep` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.DoubleTapToSleepHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 62 | Status Bar Clock Tweak | `system_statusbar_clocktweak` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemClockHooks.StatusBarClockTweakHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 63 | CC Clock Tweak | `system_cc_clocktweak` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemClockHooks.CCClockTweakHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 64 | Disable Fake Clock Anim | `system_qs_disable_fakeclock_anim` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.DisableFakeClockAnimHook(lpparam...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 65 | CC Clock Center Align | `system_cc_clock_centeralign` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemClockHooks.CCClockCenterAlignHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 66 | No Screen Lock | `system_noscreenlock_act` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.NoScreenLockHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 67 | Lock Screen Album Art | `system_albumartonlock` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUILockScreenHooks.LockScreenAlbumArtHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 68 | Expand Heads Up | `system_expandheadups` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.ExpandHeadsUpHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 69 | Better Popups No Hide | `system_betterpopups_nohide` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.BetterPopupsNoHideHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 70 | Better Popups Centered | `system_betterpopups_center` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.BetterPopupsCenteredHook(lppara...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 71 | Show Notifications After Unlock | `system_notifafterunlock` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.ShowNotificationsAfterUnlockHook(...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 72 | Notification Row Menu | `system_notifrowmenu` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.NotificationRowMenuHook(lpparam...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 73 | Hide Dismiss View | `system_removedismiss` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUINotificationHooks.HideDismissViewHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 74 | Hide Notification Access Icon | `system_drawer_removeshortcut` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUINotificationHooks.HideNoficationAccessIconHook(...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 75 | Hide No Notifications | `system_drawer_remove_emptynotify` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUINotificationHooks.HideNoNotificationsHook(lppar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 76 | Hide Nav Bar | `controls_nonavbar` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `Controls.HideNavBarHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 77 | Hide Nav Bar Before Screenshot | `controls_hidenavbar_whenscreenshot` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIScreenshotHooks.HideNavBarBeforeScreenshotHook(...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 78 | Audio Visualizer | `system_visualizer` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemAudioHooks.AudioVisualizerHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 79 | Control Center Plugin | `null` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.ControlCenterPluginHook(lppa...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 80 | Battery Indicator | `system_batteryindicator` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.BatteryIndicatorHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 81 | Disable Any Notification | `system_disableanynotif` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.DisableAnyNotificationHook(lppa...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 82 | Lock Screen Shortcut | `system_lockscreenshortcuts` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUILockScreenHooks.LockScreenShortcutHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 83 | Mobile Network Type | `system_4gtolte` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.MobileNetworkTypeHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 84 | Status Bar Icons Position Adjust | `system_statusbar_alarm_atright` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.StatusBarIconsPositionAdjustHook...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 85 | Monitor Device Info | `system_statusbar_batterytempandcurrent` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.MonitorDeviceInfoHook(lpparam, m...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 86 | Status Bar Clock Position | `system_statusbar_clock_position` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.StatusBarClockPositionHook(lppar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 87 | Status Bar Style Battery Icon | `system_statusbar_batterystyle` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.StatusBarStyleBatteryIconHook(lp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 88 | Lock Screen Top Margin | `system_statusbar_topmargin` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUILockScreenHooks.LockScreenTopMarginHook(lpparam...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 89 | Horiz Margin | `system_statusbar_horizmargin` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.HorizMarginHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 90 | Brightness Pct | `system_showpct` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.BrightnessPctHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 91 | Hide Lock Screen Status Bar | `system_hidelsstatusbar` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.HideLockScreenStatusBarHook(lppar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 92 | Hide Lock Screen Clock | `system_hidelsclock` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.HideLockScreenClockHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 93 | Force Clock Use System Fonts | `system_ls_force_systemfonts` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.ForceClockUseSystemFontsHook(lpp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 94 | Hide Lock Screen Hint | `system_hidelshint` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.HideLockScreenHintHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 95 | Allow All Keyguard | `system_allownotifonkeyguard` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.AllowAllKeyguardHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 96 | Allow All Float | `system_allownotiffloat` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemWindowHooks.AllowAllFloatHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 97 | Lock Screen Alarm | `system_lsalarm` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.LockScreenAlarmHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 98 | Status Bar Gestures | `system_statusbarcontrols` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.StatusBarGesturesHook(lppara...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 99 | Net Speed Interval | `system_netspeedinterval` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.NetSpeedIntervalHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 100 | Detailed Net Speed | `system_detailednetspeed_style` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.DetailedNetSpeedHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 101 | Net Speed Style | `system_detailednetspeed_style` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.NetSpeedStyleHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 102 | Tap To Unlock | `system_taptounlock` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.TapToUnlockHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 103 | No SOS | `system_nosos` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.NoSOSHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 104 | Remove Package Notifications Limit | `system_morenotif` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUINotificationHooks.RemovePackageNotificationsLim...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 105 | Disable Fold Notifications | `system_notif_disable_fold` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUINotificationHooks.DisableFoldNotificationsHook(...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 107 | Charging Info | `system_charginginfo` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `(default base install)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 108 | Secure QS Tiles | `system_secureqs` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.SecureQSTilesHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 109 | Mute Visible Notifications | `system_mutevisiblenotif` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.MuteVisibleNotificationsHook(lp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 110 | Hide Icons Battery1 | `system_statusbaricons_battery1` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemStatusBarIconHooks.HideIconsBattery1Hook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 111 | Hide Icons Battery2 | `system_statusbaricons_battery3` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemStatusBarIconHooks.HideIconsBattery2Hook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 112 | Display WiFi Standard | `system_statusbaricons_wifistandard` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemStatusBarIconHooks.DisplayWifiStandardHook(lppara...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 113 | Hide Privacy Indicator | `system_statusbaricons_privacy_prompt` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.HidePrivacyIndicatorHook(lpparam...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 114 | Hide Icons Signal | `system_statusbaricons_signal` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.HideIconsSignalHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 115 | Hide Icons VoWiFi | `system_statusbaricons_vowifi` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.HideIconsVoWiFiHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 116 | Hide Icons Selective Alarm | `system_statusbaricons_alarm` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemStatusBarIconHooks.HideIconsSelectiveAlarmHook(lp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 117 | Replace Shortcut App | `system_shortcut_app` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUILockScreenHooks.ReplaceShortcutAppHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 118 | QS Haptic | `system_qshaptics` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemAudioHooks.QSHapticHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 119 | Collapse CC After Click | `system_cc_collapse_after_clicked` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.CollapseCCAfterClickHook(lpp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 120 | Long Click Tile Open In Free Form | `system_cc_freeform_when_longclick` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.LongClickTileOpenInFreeFormH...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 121 | Switch CC And Notification | `system_cc_switch_qsandnotification` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.SwitchCCAndNotificationHook(...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 122 | Expand Notifications | `system_expandnotifs` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.ExpandNotificationsHook(lpparam...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 123 | Hide Mobile Network Indicator | `system_mobiletypeicon` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.HideMobileNetworkIndicatorHook(l...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 124 | Extended Power Menu | `system_epm` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUI.ExtendedPowerMenuHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 125 | Hide Icons | `system_statusbaricons_wifi` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.HideIconsHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 126 | Hide Icons From System Manager | `system_statusbaricons_privacy` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.HideIconsFromSystemManager(lppar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 127 | Better Popups Allow Float | `system_betterpopups_allowfloat` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemWindowHooks.BetterPopupsAllowFloatHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 128 | Auto Dismiss Expanded Popups | `system_betterpopups_autoclose_expanded` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.AutoDismissExpandedPopupsHook(l...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 129 | Disable Heads Up When Mute | `system_betterpopups_disablewhenmute` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUINotificationHooks.DisableHeadsUpWhenMuteHook(lp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 130 | Minimal Notification View | `system_minimalnotifview` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.MinimalNotificationViewHook(lpp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 131 | Notification Channel Settings | `system_notifchannelsettings` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.NotificationChannelSettingsHook...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 132 | Max Notification Icons | `system_maxsbicons` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemNotificationHooks.MaxNotificationIconsHook(lppara...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 133 | Mobile Type Single | `system_statusbar_mobiletype_single` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.MobileTypeSingleHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 134 | Status Bar Digital Signal | `system_statusbar_mobile_digital_signal` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.StatusBarDigitalSignalHook(lppar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 135 | Dual Row Signal | `system_statusbar_dualsimin2rows` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.DualRowSignalHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 136 | Dual Rows Statusbar | `system_statusbar_dualrows` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIStatusBarHooks.DualRowsStatusbarHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 137 | Colorize Notification Card | `system_colorizenotifs` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemColorizeNotificationHooks.ColorizeNotificationCar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 138 | Open Notify In Floating Window | `system_notify_openinfw` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUINotificationHooks.OpenNotifyInFloatingWindowHoo...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 139 | Disable Side Bar Suggestion | `system_fw_noblacklist` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemWindowHooks.DisableSideBarSuggestionHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 140 | Hide Safe Volume Dlg | `system_nosafevolume` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUIControlCenterHooks.HideSafeVolumeDlgHook(lppara...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 141 | Hide Lockscreen Zen Mode | `system_lockscreen_hidezenmode` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUILockScreenHooks.HideLockscreenZenModeHook(lppar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 142 | Disable Keyguard Editor | `system_lockscreen_disable_edit` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUILockScreenHooks.DisableKeyguardEditorHook(lppar...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 143 | No Password | `system_nopassword` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemLockScreenHooks.NoPasswordHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 144 | Notification Importance | `system_notifimportance` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUINotificationHooks.NotificationImportanceHook(lp...` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 145 | No Light Up On Charge System UI | `system_nolightuponcharges` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `SystemUI.NoLightUpOnChargeHook(lpparam)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 146 | Status Bar Height | `system_statusbarheight` | ANY | PACKAGE_READY | CommonPackageFeatures (MainModule) | `ModsSystem.StatusBarHeightHook(lpparam)` | any scoped package where hasEnabledFeature() is true |  |
| 147 | Alarm Compat | `various_alarmcompat` | ANY | PACKAGE_READY | CommonPackageFeatures (MainModule) | `Various.AlarmCompatHook()` | any scoped package where hasEnabledFeature() is true |  |
| 148 | Launcher Folder Columns Res | `launcher_folder_cols` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherFolderHooks.FolderColumnsRes(mPrefs.getInt("lau...` | com.miui.home | third-party launchers (unless selected app sets) |
| 149 | Launcher Horizontal Spacing | `launcher_horizmargin` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherLayoutHooks.HorizontalSpacingRes()` | com.miui.home | third-party launchers (unless selected app sets) |
| 150 | Launcher Indicator Height | `launcher_indicatorheight` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherLayoutHooks.IndicatorHeightRes()` | com.miui.home | third-party launchers (unless selected app sets) |
| 151 | Launcher Indicator Margin Top | `launcher_indicator_topmargin` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherLayoutHooks.IndicatorMarginTopHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 152 | Launcher Unlock Grids | `launcher_unlockgrids` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `(default base install)` | com.miui.home | third-party launchers (unless selected app sets) |
| 153 | Launcher Dock Titles | `launcher_docktitles` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherIconHooks.ShowHotseatTitlesHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 154 | Launcher Disable Log | `launcher_disable_log` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `Launcher.DisableLauncherLogHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 155 | Launcher Workspace Cell Padding Top | `launcher_topmargin` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherLayoutHooks.WorkspaceCellPaddingTopHook(lpparam...` | com.miui.home | third-party launchers (unless selected app sets) |
| 156 | Launcher Dock Margin Top | `launcher_dock_topmargin` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherLayoutHooks.DockMarginTopHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 157 | Launcher Dock Margin Bottom | `launcher_dock_bottommargin` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherLayoutHooks.DockMarginBottomHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 158 | Launcher Dock Height | `launcher_dock_height` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `LauncherLayoutHooks.DockHeightHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 159 | Launcher Privacy Apps Gest | `launcher_privacyapps_gest` | LAUNCHER | PACKAGE_READY | LauncherPackageReadyFeatures | `Launcher.setupLauncher(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 160 | Launcher Homescreen Swipes | `launcher_swipedown_action` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherGestureHooks.HomescreenSwipesHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 161 | Launcher Hot Seat Swipes | `launcher_swipeleft_action` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherGestureHooks.HotSeatSwipesHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 162 | Launcher Shake | `launcher_shake_action` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherGestureHooks.ShakeHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 163 | Launcher Double Tap | `launcher_doubletap_action` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherGestureHooks.LauncherDoubleTapHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 164 | Launcher Pinch | `launcher_pinch_action` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherGestureHooks.LauncherPinchHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 165 | Launcher Folder Columns | `launcher_folder_cols` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherFolderHooks.FolderColumnsHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 166 | Launcher Icon Scale | `launcher_iconscale` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherIconHooks.IconScaleHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 167 | Launcher Title Font Size | `launcher_titlefontsize` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherIconHooks.TitleFontSizeHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 168 | Launcher Title Top Margin | `launcher_titletopmargin` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherIconHooks.TitleTopMarginHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 169 | Launcher No Clock Hide | `launcher_noclockhide` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherIconHooks.NoClockHideHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 170 | Launcher Rename Shortcuts | `launcher_renameapps` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherIconHooks.RenameShortcutsHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 171 | Launcher Title Shadow | `launcher_darkershadow` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherIconHooks.TitleShadowHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 172 | Launcher Hide Nav Bar | `controls_nonavbar` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Launcher.HideNavBarHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 173 | Launcher Infinite Scroll | `launcher_infinitescroll` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherLayoutHooks.InfiniteScrollHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 174 | Launcher Hide Titles | `launcher_hidetitles` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherIconHooks.HideTitlesHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 175 | Launcher Fix App Info Launch | `launcher_fixlaunch` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Launcher.FixAppInfoLaunchHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 176 | Launcher No Widget Only | `launcher_nowidgetonly` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherLayoutHooks.NoWidgetOnlyHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 177 | Launcher Reverse Portrait | `launcher_sensorportrait` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Launcher.ReverseLauncherPortraitHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 178 | Launcher Max Hotseat Icons | `launcher_unlockhotseat` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherLayoutHooks.MaxHotseatIconsCountHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 179 | Launcher Close Folder On Launch | `launcher_closefolders` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherFolderHooks.CloseFolderOnLaunchHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 180 | Launcher Recents Blur | `system_recents_blur` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Launcher.RecentsBlurRatioHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 181 | Launcher Back Gesture Area Height | `controls_fsg_coverage` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Controls.BackGestureAreaHeightHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 182 | Launcher Back Gesture Area Width | `controls_fsg_width` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Controls.BackGestureAreaWidthHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 183 | Launcher Fsgestures | `controls_fsg_horiz` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherGestureHooks.FSGesturesHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 184 | Launcher Hide Memory Clean | `system_removecleaner` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `ModsSystem.HideMemoryCleanHook(lpparam, true)` | com.miui.home | third-party launchers (unless selected app sets) |
| 185 | Launcher Disable Wallpaper Scale | `system_recents_disable_wallpaperscale` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherAnimationHooks.DisableLauncherWallpaperScale(lp...` | com.miui.home | third-party launchers (unless selected app sets) |
| 186 | Launcher Hide Status Bar In Recents | `system_recents_hide_statusbar` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Launcher.HideStatusBarInRecentsHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 187 | Launcher Multi Window Plus | `system_fw_splitscreen` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `SystemWindowHooks.MultiWindowPlusHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 188 | Launcher Fix Anim | `launcher_fixanim` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherAnimationHooks.FixAnimHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 189 | Launcher Hide Seek Points | `launcher_hideseekpoints` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherLayoutHooks.HideSeekPointsHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 190 | Launcher Privacy Folder | `launcher_privacyapps_gest` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherFolderHooks.PrivacyFolderHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 191 | Launcher Hide From Recents | `system_hidefromrecents` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Launcher.HideFromRecentsHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 192 | Launcher Folder Blur | `launcher_folderblur_opacity` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherFolderHooks.FolderBlurHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 193 | Launcher No Unlock Animation | `launcher_nounlockanim` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherAnimationHooks.NoUnlockAnimationHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 194 | Launcher No Zoom Animation | `launcher_nozoomanim` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherAnimationHooks.NoZoomAnimationHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 195 | Launcher Use Old Launch Animation | `launcher_oldlaunchanim` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherAnimationHooks.UseOldLaunchAnimationHook(lppara...` | com.miui.home | third-party launchers (unless selected app sets) |
| 196 | Launcher Close Drawer On Launch | `launcher_closedrawer` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherFolderHooks.CloseDrawerOnLaunchHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 197 | Launcher Horizontal Widget Spacing | `launcher_horizwidgetmargin` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherLayoutHooks.HorizontalWidgetSpacingHook(lpparam...` | com.miui.home | third-party launchers (unless selected app sets) |
| 198 | Launcher Assist Gesture Action | `controls_fsg_assist_left_action` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherGestureHooks.AssistGestureActionHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 199 | Launcher Swipe And Stop Action | `controls_fsg_swipeandstop_action` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherGestureHooks.SwipeAndStopActionHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 200 | Launcher Close On Launch | `launcher_closefolders` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherFolderHooks.CloseFolderOrDrawerOnLaunchShortcut...` | com.miui.home | third-party launchers (unless selected app sets) |
| 201 | Launcher Resizable Widgets | `system_resizablewidgets` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherLayoutHooks.ResizableWidgetsHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 202 | Launcher Wallpaper Color Mode | `launcher_wallpaper_colormode` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `LauncherAnimationHooks.WallpaperColorModeHook(lpparam)` | com.miui.home | third-party launchers (unless selected app sets) |
| 203 | Input Method Volume Cursor | `controls_volumecursor` | ANY | PACKAGE_READY | InputMethodFeatures | `Controls.VolumeCursorHook(lpparam)` | com.baidu.input; com.baidu.input_mi; com.iflytek.inputmethod; com.iflytek.inputmethod.miui; com.sohu.inputmethod.sogou; com.sohu.inputmethod.sogou.xiaomi; com.google.android.inputmethod*; com.touchtype.swiftkey; com.tencent.wetype | not in scope.list |
| 204 | Input Method Fix Bottom Margin | `controls_nonavbar_fix_inputmethod` | ANY | PACKAGE_READY | InputMethodFeatures | `Various.FixInputMethodBottomMarginHook(lpparam)` | com.baidu.input; com.baidu.input_mi; com.iflytek.inputmethod; com.iflytek.inputmethod.miui; com.sohu.inputmethod.sogou; com.sohu.inputmethod.sogou.xiaomi; com.google.android.inputmethod*; com.touchtype.swiftkey; com.tencent.wetype | not in scope.list |
| 205 | Input Method Gboard Padding | `various_gboardpadding_port` | ANY | PACKAGE_READY | InputMethodFeatures | `Various.GboardPaddingHook(lpparam)` | com.baidu.input; com.baidu.input_mi; com.iflytek.inputmethod; com.iflytek.inputmethod.miui; com.sohu.inputmethod.sogou; com.sohu.inputmethod.sogou.xiaomi; com.google.android.inputmethod*; com.touchtype.swiftkey; com.tencent.wetype | not in scope.list |
| 206 | Settings Miuizer Icon | `miuizer_settingsiconpos` | SYSTEM_PACKAGE | PACKAGE_READY | SettingsFeatures | `GlobalActions.miuizerSettingsHook(lpparam)` | com.android.settings | com.android.settings:remote |
| 207 | Settings Disable Any Notification | `system_disableanynotif` | SYSTEM_PACKAGE | PACKAGE_READY | SettingsFeatures | `(default base install)` | com.android.settings | com.android.settings:remote |
| 208 | Settings Notification Importance | `system_notifimportance` | SYSTEM_PACKAGE | PACKAGE_READY | SettingsFeatures | `SystemNotificationHooks.NotificationImportanceHook(lppa...` | com.android.settings | com.android.settings:remote |
| 209 | Settings View Wifi Password | `system_wifipassword` | SYSTEM_PACKAGE | PACKAGE_READY | SettingsFeatures | `ModsSystem.ViewWifiPasswordHook(lpparam)` | com.android.settings | com.android.settings:remote |
| 210 | Security Center App Info | `various_appdetails` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.AppInfoHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 211 | Security Center Apps Disable | `various_disableapp` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.AppsDisableHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 212 | Security Center Apps Restrict | `various_restrictapp` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.AppsRestrictHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 213 | Security Center Hide Report Button | `various_hide_report_ondetails` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.HideReportButtonHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 214 | Security Center Scramble App Lock Pin | `system_applock_scramblepin` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `SystemLockScreenHooks.ScrambleAppLockPINHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 215 | Security Center Apps Default Sort | `various_appsort` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.AppsDefaultSortHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 216 | Security Center Intercept Perm | `various_skip_interceptperm` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.InterceptPermHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 217 | Security Center Open By Default | `various_replace_defaultopen_with_openbydefault` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.OpenByDefaultHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 218 | Security Center Skip Security Scan | `various_skip_securityscan` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.SkipSecurityScanHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 219 | Security Center Show Temp In Battery | `various_show_battery_temperature` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.ShowTempInBatteryHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 220 | Security Center Disable Side Bar Suggestion | `various_disable_freeform_suggest_blacklist` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `SystemWindowHooks.DisableSideBarSuggestionHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 221 | Security Center Disable Dock Suggest | `various_disable_dock_suggest` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.DisableDockSuggestHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 222 | Security Center Add Side Bar Expand Receiver | `various_enable_expand_sidebar` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.AddSideBarExpandReceiverHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 223 | Security Center No Low Battery Warning | `system_hidelowbatwarn` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.NoLowBatteryWarningHook()` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 224 | Security Center Privacy Apps Layout | `various_privacyapps_column_nums4` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.PrivacyAppsLayoutHook(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 225 | Security Center Persist Privacy Thumbnail Blur | `various_disable_reset_recents_privacy_blur` | SYSTEM_PACKAGE | PACKAGE_READY | SecurityCenterFeatures | `Various.PersistPrivacyThumbnailBlur(lpparam)` | com.miui.securitycenter | com.miui.securitycenter.bootaware |
| 226 | Phone Show Call Ui | `various_showcallui` | SYSTEM_PACKAGE | PACKAGE_READY | PhoneFeatures | `Various.ShowCallUIHook(lpparam)` | com.android.incallui |  |
| 227 | Phone In Call Brightness | `various_calluibright` | SYSTEM_PACKAGE | PACKAGE_READY | PhoneFeatures | `Various.InCallBrightnessHook(lpparam)` | com.android.incallui |  |
| 228 | Phone Answer Call In Head Up | `various_answerinheadup` | SYSTEM_PACKAGE | PACKAGE_READY | PhoneFeatures | `Various.AnswerCallInHeadUpHook(lpparam)` | com.android.incallui |  |
| 229 | Power Keeper Apps Restrict | `various_restrictapp` | SYSTEM_PACKAGE | PACKAGE_READY | PowerKeeperFeatures | `Various.AppsRestrictPowerHook(lpparam)` | com.miui.powerkeeper |  |
| 230 | Power Keeper Persist Battery Optimization | `various_persist_batteryoptimization` | SYSTEM_PACKAGE | PACKAGE_READY | PowerKeeperFeatures | `Various.PersistBatteryOptimizationHook(lpparam)` | com.miui.powerkeeper |  |
| 231 | Guard Provider Disable Defraud Apps | `various_disable_defraud_apps_detect` | SYSTEM_PACKAGE | PACKAGE_READY | GuardProviderFeatures | `(default base install)` | com.miui.guardprovider |  |
| 232 | Package Installer Miui Package | `various_miuiinstaller` | SYSTEM_PACKAGE | PACKAGE_READY | PackageInstallerFeatures | `Various.MiuiPackageInstallerHook(lpparam)` | com.miui.packageinstaller; com.android.packageinstaller |  |
| 233 | Package Installer App Info | `various_installappinfo` | SYSTEM_PACKAGE | PACKAGE_READY | PackageInstallerFeatures | `Various.AppInfoDuringMiuiInstallHook(lpparam)` | com.miui.packageinstaller; com.android.packageinstaller |  |
| 234 | Package Installer Purify | `various_installer_purify` | SYSTEM_PACKAGE | PACKAGE_READY | PackageInstallerFeatures | `Various.PurePackageInstallerHook(lpparam)` | com.miui.packageinstaller; com.android.packageinstaller |  |
| 235 | Media Disable Unlock Wallpaper Scale | `launcher_disable_wallpaperscale` | SYSTEM_PACKAGE | PACKAGE_READY | MediaFeatures | `LauncherAnimationHooks.DisableUnlockWallpaperScale(lppa...` | android |  |
| 236 | Media Screenshot Config | `system_screenshot` | SYSTEM_PACKAGE | PACKAGE_READY | MediaFeatures | `(default base install)` | android |  |
| 237 | Media Gallery Screenshot Path | `system_gallery_screenshots_path` | SYSTEM_PACKAGE | PACKAGE_READY | MediaFeatures | `ModsSystem.GalleryScreenshotPathHook(lpparam)` | android |  |
| 238 | Android Clean Share Menu | `system_cleanshare` | SYSTEM_PACKAGE | PACKAGE_READY | AndroidPackageFeatures | `SystemShareMenuHooks.CleanShareMenuHook(lpparam)` | android |  |
| 239 | Android Clean Open With Menu | `system_cleanopenwith` | SYSTEM_PACKAGE | PACKAGE_READY | AndroidPackageFeatures | `SystemShareMenuHooks.CleanOpenWithMenuHook(lpparam)` | android |  |
| 240 | Android All Rotations | `system_allrotations2` | SYSTEM_PACKAGE | PACKAGE_READY | AndroidPackageFeatures | `(default base install)` | android |  |
| 241 | Launcher Post Attach | `null` | LAUNCHER | APPLICATION_ATTACHED | GenericAppFeatures | `LauncherInstaller.handleLoadLauncher(lpparam, mPrefs)` | com.miui.home | third-party launchers (unless selected app sets) |
| 242 | Generic App Status Bar Background | `system_statusbarcolor` | ANY | APPLICATION_ATTACHED | GenericAppFeatures | `(default base install)` | com.miui.home + selected packages |  |
| 243 | Generic App No Overscroll | `system_nooverscroll` | ANY | APPLICATION_ATTACHED | GenericAppFeatures | `SystemWindowHooks.NoOverscrollAppHook(lpparam)` | com.miui.home + selected packages |  |
| 244 | Generic App Volume Media Player | `controls_volumemedia_up` | ANY | APPLICATION_ATTACHED | GenericAppFeatures | `Controls.VolumeMediaPlayerHook(lpparam)` | com.miui.home + selected packages |  |
| 245 | Status Bar Height Insets | `system_statusbarheight` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemStatusBarInsetsHooks.StatusBarInsetsHeightHook(lp...` | system_server |  |
| 246 | Strong Toast Presentation | `system_strong_toast_mode` | SYSTEM_UI | PACKAGE_READY | SystemUiFeatures | `(default base install)` | com.android.systemui | miui.systemui.plugin (ClassLoader extracted at runtime) |
| 247 | Disable Window Blurs | `system_disable_window_blurs` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `SystemDisplayHooks.DisableWindowBlursHook(lpparam)` | system_server |  |
| 248 | Launcher Recents Card Style | `system_recents_card_style` | LAUNCHER | APPLICATION_ATTACHED | LauncherPostAttachFeatures | `Launcher.RecentsCardStyleHook( lpparam, mPrefs.getStrin...` | com.miui.home | third-party launchers (unless selected app sets) |
| 249 | Animation Scale Bridge | `null` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `GlobalActionSystemServerHooks.setupAnimationScaleBridge...` | system_server |  |
| 250 | Updater Services Bridge | `null` | SYSTEM_SERVER | SYSTEM_SERVER_STARTING | SystemServerFeatures | `GlobalActionSystemServerHooks.setupUpdaterServicesBridg...` | system_server |  |
