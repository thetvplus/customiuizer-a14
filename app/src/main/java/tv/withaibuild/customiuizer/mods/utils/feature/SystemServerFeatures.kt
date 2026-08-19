package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.SystemAudioHooks
import tv.withaibuild.customiuizer.mods.SystemUsbDefaultHooks
import tv.withaibuild.customiuizer.mods.SystemDisplayHooks
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks
import tv.withaibuild.customiuizer.mods.SystemSecurityHooks
import tv.withaibuild.customiuizer.mods.SystemShareMenuHooks
import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureId
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Base class for all system_server features registered by [SystemServerInstaller].
 */
internal abstract class BaseSystemServerFeature(
    protected val lpparam: XposedModuleInterface.SystemServerStartingParam,
    override val id: FeatureId,
    override val name: String,
    override val preferenceKey: String?
) : FeatureDefinition {

    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING

    protected abstract fun isEnabledCondition(prefs: PrefMap): Boolean
    protected abstract fun installHook()

    final override fun isEnabled(prefs: PrefMap): Boolean = isEnabledCondition(prefs)

    final override fun install(): FeatureInstallResult {
        installHook()
        return FeatureInstallResult.INSTALLED
    }
}

internal class TempHideOverlayAppFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    TempHideOverlayAppFeatureId,
    "Temp Hide Overlay App",
    "system_screenshot_overlay"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_screenshot_overlay")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.TempHideOverlayAppHook(lpparam)
}

internal class OpenAppInFreeFormFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    OpenAppInFreeFormFeatureId,
    "Open App In Free Form",
    "system_notify_openinfw"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_notify_openinfw") || prefs.getBoolean("system_fw_forcein_actionsend") || prefs.getBoolean("system_betterpopups_allowfloat") || prefs.getBoolean("system_cc_freeform_when_longclick")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.OpenAppInFreeFormHook(lpparam)
}

internal class NavBarActionsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NavBarActionsFeatureId,
    "Nav Bar Actions",
    "controls_backlong_action"
) {
    companion object {
        /** Trigger hook is cheap; behavior is gated by [Controls.controlsConfig] at runtime. */
        @JvmStatic
        fun evaluateEnabled(@Suppress("UNUSED_PARAMETER") prefs: PrefMap): Boolean = true
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.NavBarActionsHook(lpparam)
}

internal class PowerDoubleTapActionFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    PowerDoubleTapActionFeatureId,
    "Power Double Tap Action",
    "controls_powerdt_action"
) {
    companion object {
        /** Trigger hook is cheap; behavior is gated by [Controls.controlsConfig] at runtime. */
        @JvmStatic
        fun evaluateEnabled(@Suppress("UNUSED_PARAMETER") prefs: PrefMap): Boolean = true
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.PowerDoubleTapActionHook(lpparam)
}

internal class ScreenAnimFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ScreenAnimFeatureId,
    "Screen Anim",
    "system_screenanim_duration"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_screenanim_duration", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.ScreenAnimHook(lpparam)
}

internal class AppLockTimeoutFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AppLockTimeoutFeatureId,
    "App Lock Timeout",
    "system_applock_timeout"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_applock_timeout", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.AppLockTimeoutHook(lpparam)
}

internal class ScreenDimTimeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ScreenDimTimeFeatureId,
    "Screen Dim Time",
    "system_dimtime"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_dimtime", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.ScreenDimTimeHook(lpparam)
}

internal class ToastTimeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ToastTimeFeatureId,
    "Toast Time",
    "system_toasttime"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_toasttime", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.ToastTimeHook(lpparam)
}

internal class RemoveSecureFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    RemoveSecureFeatureId,
    "Remove Secure",
    "system_removesecure"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_removesecure")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemSecurityHooks.RemoveSecureHook(lpparam)
}

internal class RemoveActStartConfirmFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    RemoveActStartConfirmFeatureId,
    "Remove Act Start Confirm",
    "system_remove_startactconfirm"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_remove_startactconfirm")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemSecurityHooks.RemoveActStartConfirmHook(lpparam)
}

internal class EnhancedSecurityFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    EnhancedSecurityFeatureId,
    "Enhanced Security",
    "system_securelock"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_securelock")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.EnhancedSecurityHook(lpparam)
}

internal class NoVersionCheckFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoVersionCheckFeatureId,
    "No Version Check",
    "system_downgrade"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_downgrade")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemSecurityHooks.NoVersionCheckHook(lpparam)
}

internal class OrientationLockFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    OrientationLockFeatureId,
    "Orientation Lock",
    "system_orientationlock"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_orientationlock")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.OrientationLockHook(lpparam)
}

internal class NoDuckingFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoDuckingFeatureId,
    "No Ducking",
    "system_noducking"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_noducking")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemAudioHooks.NoDuckingHook(lpparam)
}

internal class CleanShareMenuServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    CleanShareMenuServiceFeatureId,
    "Clean Share Menu Service",
    "system_cleanshare"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cleanshare")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemShareMenuHooks.CleanShareMenuServiceHook(lpparam)
}

internal class CleanOpenWithMenuServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    CleanOpenWithMenuServiceFeatureId,
    "Clean Open With Menu Service",
    "system_cleanopenwith"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cleanopenwith")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemShareMenuHooks.CleanOpenWithMenuServiceHook(lpparam)
}

internal class AutoBrightnessRangeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AutoBrightnessRangeFeatureId,
    "Auto Brightness Range",
    "system_autobrightness"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_autobrightness")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.AutoBrightnessRangeHook(lpparam)
}

internal class AutoBrightnessAfterScreenOffFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AutoBrightnessAfterScreenOffFeatureId,
    "Auto Brightness After Screen Off",
    "system_autobrightness_reset_when_screenoff"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_autobrightness_reset_when_screenoff")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.AutoBrightnessAfterScreenOffHook(lpparam)
}

internal class Disable72hStrongAuthFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    Disable72hStrongAuthFeatureId,
    "Disable72h Strong Auth",
    "system_lockscreen_disable_strongauth_72h"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_lockscreen_disable_strongauth_72h")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.Disable72hStrongAuthHook(lpparam)
}

internal class AppLockFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AppLockFeatureId,
    "App Lock",
    "system_applock"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_applock")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.AppLockHook(lpparam)
}

internal class SkipAppLockFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    SkipAppLockFeatureId,
    "Skip App Lock",
    "system_applock_skip"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_applock_skip")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.SkipAppLockHook(lpparam)
}

internal class AlarmCompatServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AlarmCompatServiceFeatureId,
    "Alarm Compat Service",
    "various_alarmcompat"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_alarmcompat")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AlarmCompatServiceHook(lpparam)
}

internal class NoCallInterruptionFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoCallInterruptionFeatureId,
    "No Call Interruption",
    "system_ignorecalls"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_ignorecalls")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemAudioHooks.NoCallInterruptionHook(lpparam)
}

internal class ForceCloseFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ForceCloseFeatureId,
    "Force Close",
    "system_forceclose"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_forceclose")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.ForceCloseHook(lpparam)
}

internal class HideProximityWarningFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    HideProximityWarningFeatureId,
    "Hide Proximity Warning",
    "system_hideproxywarn"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_hideproxywarn")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.HideProximityWarningHook(lpparam)
}

internal class FirstVolumePressFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    FirstVolumePressFeatureId,
    "First Volume Press",
    "system_firstpress"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_firstpress")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemAudioHooks.FirstVolumePressHook(lpparam)
}

internal class NoSignatureVerifyServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoSignatureVerifyServiceFeatureId,
    "No Signature Verify Service",
    "system_apksign"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_apksign")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemSecurityHooks.NoSignatureVerifyServiceHook(lpparam)
}

internal class DisableSystemIntegrityFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    DisableSystemIntegrityFeatureId,
    "Disable System Integrity",
    "system_disableintegrity"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_disableintegrity")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemSecurityHooks.DisableSystemIntegrityHook(lpparam)
}

internal class MuffledVibrationFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    MuffledVibrationFeatureId,
    "Muffled Vibration",
    "system_vibration_amp"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_vibration_amp")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemAudioHooks.MuffledVibrationHook(lpparam)
}

internal class ClearAllTasksFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ClearAllTasksFeatureId,
    "Clear All Tasks",
    "system_clearalltasks"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_clearalltasks")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.ClearAllTasksHook(lpparam)
}

internal class ForceDarkAllAppsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ForceDarkAllAppsFeatureId,
    "Force Dark All Apps",
    "system_force_darken_allapps"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_force_darken_allapps")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.ForceDarkAllAppsHook(lpparam)
}

internal class SetLockscreenWallpaperFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    SetLockscreenWallpaperFeatureId,
    "Set Lockscreen Wallpaper",
    "system_lswallpaper"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_lswallpaper")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.SetLockscreenWallpaperHook(lpparam)
}

internal class PowerKeyFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    PowerKeyFeatureId,
    "Power Key",
    "controls_powerflash"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_powerflash")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.PowerKeyHook(lpparam)
}

internal class FingerprintHapticFailureFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    FingerprintHapticFailureFeatureId,
    "Fingerprint Haptic Failure",
    "controls_fingerprintfailure"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_fingerprintfailure")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.FingerprintHapticFailureHook(lpparam)
}

internal class FingerprintScreenOnFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    FingerprintScreenOnFeatureId,
    "Fingerprint Screen On",
    "controls_fingerprintscreen"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_fingerprintscreen")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.FingerprintScreenOnHook(lpparam)
}

internal class NoFingerprintWakeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoFingerprintWakeFeatureId,
    "No Fingerprint Wake",
    "controls_fingerprintwake"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_fingerprintwake")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.NoFingerprintWakeHook(lpparam)
}

internal class AppsDisableServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AppsDisableServiceFeatureId,
    "Apps Disable Service",
    "various_disableapp"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_disableapp")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AppsDisableServiceHook(lpparam)
}

internal class DisableAnyNotificationBlockFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    DisableAnyNotificationBlockFeatureId,
    "Disable Any Notification Block",
    "system_disableanynotif"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_disableanynotif")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.DisableAnyNotificationBlockHook(lpparam)
}

internal class AllRotationsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AllRotationsFeatureId,
    "All Rotations",
    "system_allrotations2"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_allrotations2", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.AllRotationsHook(lpparam)
}

internal class NoLightUpOnChargeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoLightUpOnChargeFeatureId,
    "No Light Up On Charge",
    "system_nolightuponcharges"
) {
    companion object {
        // Option 2 only. Option 3 keeps native wake and must not install this hook.
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean =
            prefs.getStringAsInt("system_nolightuponcharges", 1) == 2
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.NoLightUpOnChargeHook(lpparam)
}

internal class SelectiveVibrationFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    SelectiveVibrationFeatureId,
    "Selective Vibration",
    "system_vibration"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_vibration", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemAudioHooks.SelectiveVibrationHook(lpparam)
}

internal class SelectiveToastsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    SelectiveToastsFeatureId,
    "Selective Toasts",
    "system_blocktoasts"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_blocktoasts", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.SelectiveToastsHook(lpparam)
}

internal class FingerprintHapticSuccessFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    FingerprintHapticSuccessFeatureId,
    "Fingerprint Haptic Success",
    "controls_fingerprintsuccess"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("controls_fingerprintsuccess", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.FingerprintHapticSuccessHook(lpparam)
}

internal class VolumeMediaButtonsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    VolumeMediaButtonsFeatureId,
    "Volume Media Buttons",
    "controls_volumemedia_up"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("controls_volumemedia_up", 0) > 0 || prefs.getStringAsInt("controls_volumemedia_down", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.VolumeMediaButtonsHook(lpparam)
}

internal class MultiWindowPlusFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    MultiWindowPlusFeatureId,
    "Multi Window Plus",
    "system_fw_splitscreen"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_fw_splitscreen")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.MultiWindowPlusHook(lpparam)
}

internal class NoFloatingWindowBlacklistFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoFloatingWindowBlacklistFeatureId,
    "No Floating Window Blacklist",
    "system_fw_noblacklist"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_fw_noblacklist")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.NoFloatingWindowBlacklistHook(lpparam)
}

internal class NoAccessDeviceLogsRequestFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoAccessDeviceLogsRequestFeatureId,
    "No Access Device Logs Request",
    "various_disable_access_devicelogs"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_disable_access_devicelogs")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemSecurityHooks.NoAccessDeviceLogsRequest(lpparam)
}

internal class WallpaperScaleLevelFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    WallpaperScaleLevelFeatureId,
    "Wallpaper Scale Level",
    "system_other_wallpaper_scale"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_other_wallpaper_scale", 6) > 6
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.WallpaperScaleLevelHook(lpparam)
}

internal class AllowUntrustedTouchFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AllowUntrustedTouchFeatureId,
    "Allow Untrusted Touch",
    "various_allow_untrusted_touch"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_allow_untrusted_touch")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.AllowUntrustedTouchHook(lpparam)
}
internal class StatusBarHeightInsetsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    StatusBarHeightInsetsFeatureId,
    "Status Bar Height Insets",
    "system_statusbarheight"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = StatusBarHeightConfig.isEnabled(prefs)
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemStatusBarInsetsHooks.StatusBarInsetsHeightHook(lpparam)
}

/** Always installed because the default-off switch must still be available after a restart. */
internal class DisableWindowBlursFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    DisableWindowBlursFeatureId,
    "Disable Window Blurs",
    "system_disable_window_blurs"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(@Suppress("UNUSED_PARAMETER") prefs: PrefMap): Boolean = true
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.DisableWindowBlursHook(lpparam)
}


/**
 * All preference-guarded features that belong in system_server.
 *
 * This keeps [SystemServerInstaller.install] a thin registration loop while still
 * making every feature individually testable and traceable.
 */
object SystemServerFeatures {
    fun all(
        lpparam: XposedModuleInterface.SystemServerStartingParam
    ): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = AnimationScaleBridgeFeatureId,
            name = "Animation Scale Bridge",
            preferenceKey = null,
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { true },
            factory = { AnimationScaleBridgeFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = UpdaterServicesBridgeFeatureId,
            name = "Updater Services Bridge",
            preferenceKey = null,
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { true },
            factory = { UpdaterServicesBridgeFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = TempHideOverlayAppFeatureId,
            name = "Temp Hide Overlay App",
            preferenceKey = "system_screenshot_overlay",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> TempHideOverlayAppFeature.evaluateEnabled(prefs) },
            factory = { TempHideOverlayAppFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = OpenAppInFreeFormFeatureId,
            name = "Open App In Free Form",
            preferenceKey = "system_notify_openinfw",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> OpenAppInFreeFormFeature.evaluateEnabled(prefs) },
            factory = { OpenAppInFreeFormFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NavBarActionsFeatureId,
            name = "Nav Bar Actions",
            preferenceKey = "controls_backlong_action",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NavBarActionsFeature.evaluateEnabled(prefs) },
            factory = { NavBarActionsFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = PowerDoubleTapActionFeatureId,
            name = "Power Double Tap Action",
            preferenceKey = "controls_powerdt_action",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> PowerDoubleTapActionFeature.evaluateEnabled(prefs) },
            factory = { PowerDoubleTapActionFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = ScreenAnimFeatureId,
            name = "Screen Anim",
            preferenceKey = "system_screenanim_duration",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> ScreenAnimFeature.evaluateEnabled(prefs) },
            factory = { ScreenAnimFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = AppLockTimeoutFeatureId,
            name = "App Lock Timeout",
            preferenceKey = "system_applock_timeout",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> AppLockTimeoutFeature.evaluateEnabled(prefs) },
            factory = { AppLockTimeoutFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = ScreenDimTimeFeatureId,
            name = "Screen Dim Time",
            preferenceKey = "system_dimtime",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> ScreenDimTimeFeature.evaluateEnabled(prefs) },
            factory = { ScreenDimTimeFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = ToastTimeFeatureId,
            name = "Toast Time",
            preferenceKey = "system_toasttime",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> ToastTimeFeature.evaluateEnabled(prefs) },
            factory = { ToastTimeFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = RemoveSecureFeatureId,
            name = "Remove Secure",
            preferenceKey = "system_removesecure",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> RemoveSecureFeature.evaluateEnabled(prefs) },
            factory = { RemoveSecureFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = RemoveActStartConfirmFeatureId,
            name = "Remove Act Start Confirm",
            preferenceKey = "system_remove_startactconfirm",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> RemoveActStartConfirmFeature.evaluateEnabled(prefs) },
            factory = { RemoveActStartConfirmFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = EnhancedSecurityFeatureId,
            name = "Enhanced Security",
            preferenceKey = "system_securelock",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> EnhancedSecurityFeature.evaluateEnabled(prefs) },
            factory = { EnhancedSecurityFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NoVersionCheckFeatureId,
            name = "No Version Check",
            preferenceKey = "system_downgrade",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NoVersionCheckFeature.evaluateEnabled(prefs) },
            factory = { NoVersionCheckFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = OrientationLockFeatureId,
            name = "Orientation Lock",
            preferenceKey = "system_orientationlock",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> OrientationLockFeature.evaluateEnabled(prefs) },
            factory = { OrientationLockFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NoDuckingFeatureId,
            name = "No Ducking",
            preferenceKey = "system_noducking",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NoDuckingFeature.evaluateEnabled(prefs) },
            factory = { NoDuckingFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = CleanShareMenuServiceFeatureId,
            name = "Clean Share Menu Service",
            preferenceKey = "system_cleanshare",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> CleanShareMenuServiceFeature.evaluateEnabled(prefs) },
            factory = { CleanShareMenuServiceFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = CleanOpenWithMenuServiceFeatureId,
            name = "Clean Open With Menu Service",
            preferenceKey = "system_cleanopenwith",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> CleanOpenWithMenuServiceFeature.evaluateEnabled(prefs) },
            factory = { CleanOpenWithMenuServiceFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = AutoBrightnessRangeFeatureId,
            name = "Auto Brightness Range",
            preferenceKey = "system_autobrightness",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> AutoBrightnessRangeFeature.evaluateEnabled(prefs) },
            factory = { AutoBrightnessRangeFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = AutoBrightnessAfterScreenOffFeatureId,
            name = "Auto Brightness After Screen Off",
            preferenceKey = "system_autobrightness_reset_when_screenoff",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> AutoBrightnessAfterScreenOffFeature.evaluateEnabled(prefs) },
            factory = { AutoBrightnessAfterScreenOffFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = Disable72hStrongAuthFeatureId,
            name = "Disable72h Strong Auth",
            preferenceKey = "system_lockscreen_disable_strongauth_72h",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> Disable72hStrongAuthFeature.evaluateEnabled(prefs) },
            factory = { Disable72hStrongAuthFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = AppLockFeatureId,
            name = "App Lock",
            preferenceKey = "system_applock",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> AppLockFeature.evaluateEnabled(prefs) },
            factory = { AppLockFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = SkipAppLockFeatureId,
            name = "Skip App Lock",
            preferenceKey = "system_applock_skip",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> SkipAppLockFeature.evaluateEnabled(prefs) },
            factory = { SkipAppLockFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = AlarmCompatServiceFeatureId,
            name = "Alarm Compat Service",
            preferenceKey = "various_alarmcompat",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> AlarmCompatServiceFeature.evaluateEnabled(prefs) },
            factory = { AlarmCompatServiceFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NoCallInterruptionFeatureId,
            name = "No Call Interruption",
            preferenceKey = "system_ignorecalls",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NoCallInterruptionFeature.evaluateEnabled(prefs) },
            factory = { NoCallInterruptionFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = ForceCloseFeatureId,
            name = "Force Close",
            preferenceKey = "system_forceclose",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> ForceCloseFeature.evaluateEnabled(prefs) },
            factory = { ForceCloseFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = HideProximityWarningFeatureId,
            name = "Hide Proximity Warning",
            preferenceKey = "system_hideproxywarn",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> HideProximityWarningFeature.evaluateEnabled(prefs) },
            factory = { HideProximityWarningFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = FirstVolumePressFeatureId,
            name = "First Volume Press",
            preferenceKey = "system_firstpress",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> FirstVolumePressFeature.evaluateEnabled(prefs) },
            factory = { FirstVolumePressFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NoSignatureVerifyServiceFeatureId,
            name = "No Signature Verify Service",
            preferenceKey = "system_apksign",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NoSignatureVerifyServiceFeature.evaluateEnabled(prefs) },
            factory = { NoSignatureVerifyServiceFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = DisableSystemIntegrityFeatureId,
            name = "Disable System Integrity",
            preferenceKey = "system_disableintegrity",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> DisableSystemIntegrityFeature.evaluateEnabled(prefs) },
            factory = { DisableSystemIntegrityFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = MuffledVibrationFeatureId,
            name = "Muffled Vibration",
            preferenceKey = "system_vibration_amp",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> MuffledVibrationFeature.evaluateEnabled(prefs) },
            factory = { MuffledVibrationFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = ClearAllTasksFeatureId,
            name = "Clear All Tasks",
            preferenceKey = "system_clearalltasks",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> ClearAllTasksFeature.evaluateEnabled(prefs) },
            factory = { ClearAllTasksFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = ForceDarkAllAppsFeatureId,
            name = "Force Dark All Apps",
            preferenceKey = "system_force_darken_allapps",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> ForceDarkAllAppsFeature.evaluateEnabled(prefs) },
            factory = { ForceDarkAllAppsFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = SetLockscreenWallpaperFeatureId,
            name = "Set Lockscreen Wallpaper",
            preferenceKey = "system_lswallpaper",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> SetLockscreenWallpaperFeature.evaluateEnabled(prefs) },
            factory = { SetLockscreenWallpaperFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = PowerKeyFeatureId,
            name = "Power Key",
            preferenceKey = "controls_powerflash",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> PowerKeyFeature.evaluateEnabled(prefs) },
            factory = { PowerKeyFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = FingerprintHapticFailureFeatureId,
            name = "Fingerprint Haptic Failure",
            preferenceKey = "controls_fingerprintfailure",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> FingerprintHapticFailureFeature.evaluateEnabled(prefs) },
            factory = { FingerprintHapticFailureFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = FingerprintScreenOnFeatureId,
            name = "Fingerprint Screen On",
            preferenceKey = "controls_fingerprintscreen",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> FingerprintScreenOnFeature.evaluateEnabled(prefs) },
            factory = { FingerprintScreenOnFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NoFingerprintWakeFeatureId,
            name = "No Fingerprint Wake",
            preferenceKey = "controls_fingerprintwake",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NoFingerprintWakeFeature.evaluateEnabled(prefs) },
            factory = { NoFingerprintWakeFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = AppsDisableServiceFeatureId,
            name = "Apps Disable Service",
            preferenceKey = "various_disableapp",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> AppsDisableServiceFeature.evaluateEnabled(prefs) },
            factory = { AppsDisableServiceFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = DisableAnyNotificationBlockFeatureId,
            name = "Disable Any Notification Block",
            preferenceKey = "system_disableanynotif",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> DisableAnyNotificationBlockFeature.evaluateEnabled(prefs) },
            factory = { DisableAnyNotificationBlockFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = AllRotationsFeatureId,
            name = "All Rotations",
            preferenceKey = "system_allrotations2",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> AllRotationsFeature.evaluateEnabled(prefs) },
            factory = { AllRotationsFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NoLightUpOnChargeFeatureId,
            name = "No Light Up On Charge",
            preferenceKey = "system_nolightuponcharges",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NoLightUpOnChargeFeature.evaluateEnabled(prefs) },
            factory = { NoLightUpOnChargeFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = SelectiveVibrationFeatureId,
            name = "Selective Vibration",
            preferenceKey = "system_vibration",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> SelectiveVibrationFeature.evaluateEnabled(prefs) },
            factory = { SelectiveVibrationFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = SelectiveToastsFeatureId,
            name = "Selective Toasts",
            preferenceKey = "system_blocktoasts",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> SelectiveToastsFeature.evaluateEnabled(prefs) },
            factory = { SelectiveToastsFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = FingerprintHapticSuccessFeatureId,
            name = "Fingerprint Haptic Success",
            preferenceKey = "controls_fingerprintsuccess",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> FingerprintHapticSuccessFeature.evaluateEnabled(prefs) },
            factory = { FingerprintHapticSuccessFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = VolumeMediaButtonsFeatureId,
            name = "Volume Media Buttons",
            preferenceKey = "controls_volumemedia_up",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> VolumeMediaButtonsFeature.evaluateEnabled(prefs) },
            factory = { VolumeMediaButtonsFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = MultiWindowPlusFeatureId,
            name = "Multi Window Plus",
            preferenceKey = "system_fw_splitscreen",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> MultiWindowPlusFeature.evaluateEnabled(prefs) },
            factory = { MultiWindowPlusFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NoFloatingWindowBlacklistFeatureId,
            name = "No Floating Window Blacklist",
            preferenceKey = "system_fw_noblacklist",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NoFloatingWindowBlacklistFeature.evaluateEnabled(prefs) },
            factory = { NoFloatingWindowBlacklistFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = NoAccessDeviceLogsRequestFeatureId,
            name = "No Access Device Logs Request",
            preferenceKey = "various_disable_access_devicelogs",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> NoAccessDeviceLogsRequestFeature.evaluateEnabled(prefs) },
            factory = { NoAccessDeviceLogsRequestFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = WallpaperScaleLevelFeatureId,
            name = "Wallpaper Scale Level",
            preferenceKey = "system_other_wallpaper_scale",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> WallpaperScaleLevelFeature.evaluateEnabled(prefs) },
            factory = { WallpaperScaleLevelFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = AllowUntrustedTouchFeatureId,
            name = "Allow Untrusted Touch",
            preferenceKey = "various_allow_untrusted_touch",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> AllowUntrustedTouchFeature.evaluateEnabled(prefs) },
            factory = { AllowUntrustedTouchFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = StatusBarHeightInsetsFeatureId,
            name = "Status Bar Height Insets",
            preferenceKey = "system_statusbarheight",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> StatusBarHeightInsetsFeature.evaluateEnabled(prefs) },
            factory = { StatusBarHeightInsetsFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = DisableWindowBlursFeatureId,
            name = "Disable Window Blurs",
            preferenceKey = "system_disable_window_blurs",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { prefs -> DisableWindowBlursFeature.evaluateEnabled(prefs) },
            factory = { DisableWindowBlursFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = UsbDefaultFunctionFeatureId,
            name = "USB Default Function",
            preferenceKey = "system_usb_default_function",
            target = FeatureTarget.SYSTEM_SERVER,
            phase = InstallPhase.SYSTEM_SERVER_STARTING,
            enabled = { true },
            factory = { UsbDefaultFunctionFeature(lpparam) },
        ),
)
}

internal class AnimationScaleBridgeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AnimationScaleBridgeFeatureId,
    "Animation Scale Bridge",
    null
) {
    override fun isEnabledCondition(prefs: PrefMap): Boolean = true
    override fun installHook() = GlobalActionSystemServerHooks.setupAnimationScaleBridge(lpparam)
}

internal class UpdaterServicesBridgeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    UpdaterServicesBridgeFeatureId,
    "Updater Services Bridge",
    null
) {
    override fun isEnabledCondition(prefs: PrefMap): Boolean = true
    override fun installHook() = GlobalActionSystemServerHooks.setupUpdaterServicesBridge(lpparam)
}

internal class UsbDefaultFunctionFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    UsbDefaultFunctionFeatureId,
    "USB Default Function",
    "system_usb_default_function"
) {
    override fun isEnabledCondition(prefs: PrefMap): Boolean = true

    override fun installHook() {
        val result = SystemUsbDefaultHooks.hook(lpparam)
        if (result != FeatureInstallResult.INSTALLED) {
            throw IllegalStateException("USB default function hooks could not be installed: $result")
        }
    }
}
