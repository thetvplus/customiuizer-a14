package tv.withaibuild.customiuizer.mods

/**
 * First-enable lifecycle audit for [tv.withaibuild.customiuizer.mods.utils.feature.SystemServerFeatures].
 *
 * Metadata only. Install still goes through FeatureSpec gates; this table must not become
 * a second installer.
 */
enum class FirstEnableClassification {
    A_STABLE_TRIGGER,
    INFRA_ALWAYS,
    B_KEEP_LAZY,
    C_REBOOT_REQUIRED,
}

enum class TriggerHookInstallMode {
    ALWAYS,
    PREF_GATED_AT_START,
}

data class SystemServerFirstEnableRow(
    val featureIdName: String,
    val preferenceKey: String?,
    val triggerHook: String,
    val uiPage: String,
    val uiDynamic: Boolean,
    val restartMask: String,
    val installMode: TriggerHookInstallMode,
    val canEnableAfterBoot: Boolean,
    val firstEnableWorksWithoutReboot: Boolean,
    val classification: FirstEnableClassification,
)

object SystemServerFirstEnableContract {

    val rows: List<SystemServerFirstEnableRow> = listOf(
        infra("animation_scale_bridge", null, "GlobalActionSystemServerHooks.setupAnimationScaleBridge", "n/a"),
        infra("updater_services_bridge", null, "GlobalActionSystemServerHooks.setupUpdaterServicesBridge", "n/a"),
        lazyB("temp_hide_overlay_app", "system_screenshot_overlay", "SystemWindowHooks.TempHideOverlayAppHook", "prefs_system_screenshot", "NONE"),
        lazyB("open_app_in_free_form", "system_notify_openinfw", "SystemWindowHooks.OpenAppInFreeFormHook", "prefs_system_floatingwindows", "LAUNCHER|SYSTEMUI"),
        triggerA("nav_bar_actions", "controls_backlong_action", "Controls.NavBarActionsHook", "prefs_controls_navbar", "LAUNCHER|SYSTEMUI"),
        triggerA("power_double_tap_action", "controls_powerdt_action", "Controls.PowerDoubleTapActionHook", "prefs_controls_power", "NONE"),
        lazyB("screen_anim", "system_screenanim_duration", "SystemDisplayHooks.ScreenAnimHook", "prefs_system_screen", "SYSTEMUI"),
        lazyB("app_lock_timeout", "system_applock_timeout", "SystemLockScreenHooks.AppLockTimeoutHook", "prefs_system_applock", "SECURITY_CENTER"),
        lazyB("screen_dim_time", "system_dimtime", "SystemDisplayHooks.ScreenDimTimeHook", "prefs_system_screen", "SYSTEMUI"),
        lazyB("toast_time", "system_toasttime", "SystemNotificationHooks.ToastTimeHook", "prefs_system_toasts", "NONE"),
        lazyB("remove_secure", "system_removesecure", "SystemWindowHooks.RemoveSecureHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("remove_act_start_confirm", "system_remove_startactconfirm", "SystemWindowHooks.RemoveActStartConfirmHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("enhanced_security", "system_securelock", "SystemSecurityHooks.EnhancedSecurityHook", "prefs_system_lockscreen", "SYSTEMUI"),
        lazyB("no_version_check", "system_downgrade", "SystemSecurityHooks.NoVersionCheckHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("orientation_lock", "system_orientationlock", "SystemDisplayHooks.OrientationLockHook", "prefs_system_screen", "SYSTEMUI"),
        lazyB("no_ducking", "system_noducking", "SystemAudioHooks.NoDuckingHook", "prefs_system_audio", "SYSTEMUI"),
        lazyB("clean_share_menu_service", "system_cleanshare", "SystemShareMenuHooks.CleanShareMenuServiceHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("clean_open_with_menu_service", "system_cleanopenwith", "SystemShareMenuHooks.CleanOpenWithMenuServiceHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("auto_brightness_range", "system_autobrightness", "SystemDisplayHooks.AutoBrightnessRangeHook", "prefs_system_autobrightness", "NONE"),
        lazyB("auto_brightness_after_screen_off", "system_autobrightness_reset_when_screenoff", "SystemDisplayHooks.AutoBrightnessAfterScreenOffHook", "prefs_system_autobrightness", "NONE"),
        lazyB("disable72h_strong_auth", "system_lockscreen_disable_strongauth_72h", "SystemLockScreenHooks.Disable72hStrongAuthHook", "prefs_system_lockscreen", "SYSTEMUI"),
        lazyB("app_lock", "system_applock", "SystemLockScreenHooks.AppLockHook", "prefs_system_applock", "SECURITY_CENTER"),
        lazyB("skip_app_lock", "system_applock_skip", "SystemLockScreenHooks.SkipAppLockHook", "prefs_system_applock", "SECURITY_CENTER"),
        lazyB("alarm_compat_service", "various_alarmcompat", "SystemLockScreenHooks.AlarmCompatServiceHook", "prefs_system_alarmonlock", "SYSTEMUI"),
        lazyB("no_call_interruption", "system_ignorecalls", "SystemAudioHooks.NoCallInterruptionHook", "prefs_various_calls", "SYSTEMUI"),
        lazyB("force_close", "system_forceclose", "SystemWindowHooks.ForceCloseHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("hide_proximity_warning", "system_hideproxywarn", "SystemDisplayHooks.HideProximityWarningHook", "prefs_system_screen", "SYSTEMUI"),
        lazyB("first_volume_press", "system_firstpress", "SystemAudioHooks.FirstVolumePressHook", "prefs_system_audio", "SYSTEMUI"),
        lazyB("no_signature_verify_service", "system_apksign", "SystemSecurityHooks.NoSignatureVerifyServiceHook", "prefs_various_package_installer", "NONE"),
        lazyB("disable_system_integrity", "system_disableintegrity", "SystemSecurityHooks.DisableSystemIntegrityHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("muffled_vibration", "system_vibration_amp", "SystemAudioHooks.MuffledVibrationHook", "prefs_system_vibration_amp", "NONE"),
        lazyB("clear_all_tasks", "system_clearalltasks", "SystemWindowHooks.ClearAllTasksHook", "prefs_system_recents", "LAUNCHER"),
        lazyB("force_dark_all_apps", "system_force_darken_allapps", "SystemDisplayHooks.ForceDarkAllAppsHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("set_lockscreen_wallpaper", "system_lswallpaper", "SystemLockScreenHooks.SetLockscreenWallpaperHook", "prefs_system_lockscreen", "SYSTEMUI"),
        lazyB("power_key", "controls_powerflash", "Controls.PowerKeyHook", "prefs_controls_power", "NONE"),
        lazyB("fingerprint_haptic_failure", "controls_fingerprintfailure", "Controls.FingerprintHapticFailureHook", "prefs_controls_fingerprint", "NONE"),
        lazyB("fingerprint_screen_on", "controls_fingerprintscreen", "Controls.FingerprintScreenOnHook", "prefs_controls_fingerprint", "NONE"),
        lazyB("no_fingerprint_wake", "controls_fingerprintwake", "Controls.NoFingerprintWakeHook", "prefs_controls_fingerprint", "NONE"),
        lazyB("apps_disable_service", "various_disableapp", "Various.AppsDisableServiceHook", "prefs_various_general", "NONE"),
        lazyB("disable_any_notification_block", "system_disableanynotif", "SystemNotificationHooks.DisableAnyNotificationBlockHook", "prefs_system_notifications", "SYSTEMUI"),
        lazyB("all_rotations", "system_allrotations2", "SystemDisplayHooks.AllRotationsHook", "prefs_system_screen", "SYSTEMUI"),
        lazyB("no_light_up_on_charge", "system_nolightuponcharges", "SystemDisplayHooks.NoLightUpOnChargeHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("selective_vibration", "system_vibration", "SystemAudioHooks.SelectiveVibrationHook", "prefs_system_vibration", "SYSTEMUI"),
        lazyB("selective_toasts", "system_blocktoasts", "SystemNotificationHooks.SelectiveToastsHook", "prefs_system_toasts", "NONE"),
        lazyB("fingerprint_haptic_success", "controls_fingerprintsuccess", "Controls.FingerprintHapticSuccessHook", "prefs_controls_fingerprint", "NONE"),
        lazyB("volume_media_buttons", "controls_volumemedia_up", "Controls.VolumeMediaButtonsHook", "prefs_controls_volume", "SYSTEMUI"),
        lazyB("multi_window_plus", "system_fw_splitscreen", "SystemWindowHooks.MultiWindowPlusHook", "prefs_system_floatingwindows", "LAUNCHER|SYSTEMUI"),
        lazyB("no_floating_window_blacklist", "system_fw_noblacklist", "SystemWindowHooks.NoFloatingWindowBlacklistHook", "prefs_system_floatingwindows", "LAUNCHER|SYSTEMUI"),
        lazyB("no_access_device_logs_request", "various_disable_access_devicelogs", "SystemSecurityHooks.NoAccessDeviceLogsRequestHook", "prefs_various_exclusive", "NONE"),
        lazyB("wallpaper_scale_level", "system_other_wallpaper_scale", "SystemDisplayHooks.WallpaperScaleLevelHook", "prefs_system_other", "SYSTEMUI|SECURITY_CENTER"),
        lazyB("allow_untrusted_touch", "various_allow_untrusted_touch", "SystemWindowHooks.AllowUntrustedTouchHook", "prefs_various_exclusive", "NONE"),
        lazyB("status_bar_height_insets", "system_statusbarheight", "SystemStatusBarInsetsHooks.StatusBarHeightInsetsHook", "prefs_system_statusbar", "SYSTEMUI"),
        infra("disable_window_blurs", "system_disable_window_blurs", "SystemDisplayHooks.DisableWindowBlursHook", "prefs_system_other"),
        infra("system_usb_default_function", "system_usb_default_function", "SystemUsbDefaultHooks.hook", "prefs_system_other"),
    )

    fun row(featureIdName: String): SystemServerFirstEnableRow? =
        rows.firstOrNull { it.featureIdName == featureIdName }

    fun names(): Set<String> = rows.map { it.featureIdName }.toSet()

    private fun triggerA(
        name: String,
        key: String,
        hook: String,
        page: String,
        restart: String,
    ) = SystemServerFirstEnableRow(
        featureIdName = name,
        preferenceKey = key,
        triggerHook = hook,
        uiPage = page,
        uiDynamic = true,
        restartMask = restart,
        installMode = TriggerHookInstallMode.ALWAYS,
        canEnableAfterBoot = true,
        firstEnableWorksWithoutReboot = true,
        classification = FirstEnableClassification.A_STABLE_TRIGGER,
    )

    private fun infra(
        name: String,
        key: String?,
        hook: String,
        page: String,
    ) = SystemServerFirstEnableRow(
        featureIdName = name,
        preferenceKey = key,
        triggerHook = hook,
        uiPage = page,
        uiDynamic = key != null,
        restartMask = "NONE",
        installMode = TriggerHookInstallMode.ALWAYS,
        canEnableAfterBoot = true,
        firstEnableWorksWithoutReboot = true,
        classification = FirstEnableClassification.INFRA_ALWAYS,
    )

    private fun lazyB(
        name: String,
        key: String,
        hook: String,
        page: String,
        restart: String,
    ) = SystemServerFirstEnableRow(
        featureIdName = name,
        preferenceKey = key,
        triggerHook = hook,
        uiPage = page,
        uiDynamic = true,
        restartMask = restart,
        installMode = TriggerHookInstallMode.PREF_GATED_AT_START,
        canEnableAfterBoot = true,
        firstEnableWorksWithoutReboot = false,
        classification = FirstEnableClassification.B_KEEP_LAZY,
    )
}
