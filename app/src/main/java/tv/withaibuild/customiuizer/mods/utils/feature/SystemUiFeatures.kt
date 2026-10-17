package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.GlobalActions
import java.util.concurrent.atomic.AtomicReference
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.SystemAudioHooks
import tv.withaibuild.customiuizer.mods.SystemClockHooks
import tv.withaibuild.customiuizer.mods.SystemColorizeNotificationHooks
import tv.withaibuild.customiuizer.mods.SystemDisplayHooks
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks
import tv.withaibuild.customiuizer.mods.SystemStatusBarIconHooks
import tv.withaibuild.customiuizer.mods.SystemUI
import tv.withaibuild.customiuizer.mods.SystemUIControlCenterHooks
import tv.withaibuild.customiuizer.mods.SystemUILockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemUINotificationHooks
import tv.withaibuild.customiuizer.mods.SystemUIScreenshotHooks
import tv.withaibuild.customiuizer.mods.StatusBarContentGeometryHooks
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureId
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.resolveDeviceInfoPlacement
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Base class for all SystemUI features registered by [SystemUiInstaller].
 */
internal abstract class BaseSystemUiFeature(
    protected val lpparam: PackageReadyParam,
    protected val mPrefs: PrefMap,
    override val id: FeatureId,
    override val name: String,
    override val preferenceKey: String?
) : FeatureDefinition {

    override val target = FeatureTarget.SYSTEM_UI
    override val phase = InstallPhase.PACKAGE_READY

    protected abstract fun isEnabledCondition(prefs: PrefMap): Boolean
    protected abstract fun installHook()

    final override fun isEnabled(prefs: PrefMap) = isEnabledCondition(prefs)

    final override fun install(): FeatureInstallResult {
        installHook()
        return FeatureInstallResult.INSTALLED
    }
}

internal class ForegroundMonitorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ForegroundMonitorFeatureId,
    "Foreground Monitor",
    "various_showcallui"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("various_showcallui", 0) > 0 || prefs.getBoolean("controls_volumecursor")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = GlobalActions.setupForegroundMonitor(lpparam)
}

internal class TempHideOverlaySystemUiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    TempHideOverlaySystemUiFeatureId,
    "Temp Hide Overlay System UI",
    "system_screenshot_overlay"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_screenshot_overlay")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIScreenshotHooks.TempHideOverlaySystemUIHook(lpparam)
}

internal class AddCustomTileFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AddCustomTileFeatureId,
    "Add Custom Tile",
    "system_fivegtile"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_fivegtile") || prefs.getBoolean("system_cc_fpstile") || prefs.getBoolean("system_cc_floatingtimetile")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.AddCustomTileHook(lpparam)
}

internal class HideStatusBarWhenCaptureFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideStatusBarWhenCaptureFeatureId,
    "Hide Status Bar When Capture",
    "system_hidestatusbar_whenscreenshot"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_hidestatusbar_whenscreenshot")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIScreenshotHooks.HideStatusBarWhenCaptureHook(lpparam)
}

internal class NetworkIndicatorWifiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NetworkIndicatorWifiFeatureId,
    "Network Indicator WiFi",
    "system_networkindicator_wifi"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_networkindicator_wifi")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.NetworkIndicatorWifi(lpparam)
}

internal class DrawerBlurRatioFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DrawerBlurRatioFeatureId,
    "Drawer Blur Ratio",
    "system_drawer_blur"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_drawer_blur", 100) < 100
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.DrawerBlurRatioHook(lpparam)
}

internal class ChargeAnimationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ChargeAnimationFeatureId,
    "Charge Animation",
    "system_chargeanimtime"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_chargeanimtime", 20) < 20
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemDisplayHooks.ChargeAnimationHook(lpparam)
}

internal class BetterPopupsHideDelayFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BetterPopupsHideDelayFeatureId,
    "Better Popups Hide Delay",
    "system_betterpopups_delay"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_betterpopups_delay", 0) > 0 && !prefs.getBoolean("system_betterpopups_nohide")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.BetterPopupsHideDelayHook(lpparam)
}

internal class AssistGestureActionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AssistGestureActionFeatureId,
    "Assist Gesture Action",
    "controls_fsg_assist_left_action"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("controls_fsg_assist_left_action", 1) > 1 || prefs.getInt("controls_fsg_assist_right_action", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.AssistGestureActionHook(lpparam)
}

internal class NavBarButtonsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NavBarButtonsFeatureId,
    "Nav Bar Buttons",
    "controls_navbarleft_action"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("controls_navbarleft_action", 1) > 1 ||
        prefs.getInt("controls_navbarleftlong_action", 1) > 1 ||
        prefs.getInt("controls_navbarright_action", 1) > 1 ||
        prefs.getInt("controls_navbarrightlong_action", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.NavBarButtonsHook(lpparam)
}

internal class ScramblePinFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ScramblePinFeatureId,
    "Scramble PIN",
    "system_scramblepin"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_scramblepin")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.ScramblePINHook(lpparam)
}

internal class DoubleTapToSleepFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DoubleTapToSleepFeatureId,
    "Double Tap To Sleep",
    "system_dttosleep"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_dttosleep")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.DoubleTapToSleepHook(lpparam)
}

internal class StatusBarClockTweakFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarClockTweakFeatureId,
    "Status Bar Clock Tweak",
    "system_statusbar_clocktweak"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbar_clocktweak") ||
        prefs.getBoolean("system_cc_clocktweak") ||
        prefs.getBoolean("system_cc_hidedate") ||
        prefs.getBoolean("system_drawer_hidedate") ||
        prefs.getBoolean("system_statusbaricons_clock") ||
        prefs.getString("system_cc_dateformat", "").isNotEmpty() ||
        prefs.getString("system_drawer_dateformat", "").isNotEmpty()
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemClockHooks.StatusBarClockTweakHook(lpparam)
}

internal class CcClockTweakFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    CcClockTweakFeatureId,
    "CC Clock Tweak",
    "system_cc_clocktweak"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cc_clocktweak") || prefs.getBoolean("system_qs_force_systemfonts")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemClockHooks.CCClockTweakHook(lpparam)
}

internal class DisableFakeClockAnimFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableFakeClockAnimFeatureId,
    "Disable Fake Clock Anim",
    "system_qs_disable_fakeclock_anim"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_qs_disable_fakeclock_anim")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.DisableFakeClockAnimHook(lpparam)
}

internal class CcClockCenterAlignFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    CcClockCenterAlignFeatureId,
    "CC Clock Center Align",
    "system_cc_clock_centeralign"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cc_clock_centeralign") ||
        (!prefs.getBoolean("system_drawer_hidedate") && prefs.getBoolean("system_drawer_date_centeralign"))
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemClockHooks.CCClockCenterAlignHook(lpparam)
}

internal class NoScreenLockFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NoScreenLockFeatureId,
    "No Screen Lock",
    "system_noscreenlock_act"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_noscreenlock_act")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.NoScreenLockHook(lpparam)
}

internal class LockScreenAlbumArtFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LockScreenAlbumArtFeatureId,
    "Lock Screen Album Art",
    "system_albumartonlock"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_albumartonlock")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUILockScreenHooks.LockScreenAlbumArtHook(lpparam)
}

internal class ExpandHeadsUpFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ExpandHeadsUpFeatureId,
    "Expand Heads Up",
    "system_expandheadups"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_expandheadups", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.ExpandHeadsUpHook(lpparam)
}

internal class BetterPopupsNoHideFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BetterPopupsNoHideFeatureId,
    "Better Popups No Hide",
    "system_betterpopups_nohide"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_betterpopups_nohide")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.BetterPopupsNoHideHook(lpparam)
}

internal class BetterPopupsCenteredFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BetterPopupsCenteredFeatureId,
    "Better Popups Centered",
    "system_betterpopups_center"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_betterpopups_center")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.BetterPopupsCenteredHook(lpparam)
}

internal class ShowNotificationsAfterUnlockFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ShowNotificationsAfterUnlockFeatureId,
    "Show Notifications After Unlock",
    "system_notifafterunlock"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_notifafterunlock")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.ShowNotificationsAfterUnlockHook(lpparam)
}

internal class NotificationRowMenuFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NotificationRowMenuFeatureId,
    "Notification Row Menu",
    "system_notifrowmenu"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_notifrowmenu")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.NotificationRowMenuHook(lpparam)
}

internal class HideDismissViewFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideDismissViewFeatureId,
    "Hide Dismiss View",
    "system_removedismiss"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_removedismiss")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUINotificationHooks.HideDismissViewHook(lpparam)
}

internal class HideNotificationAccessIconFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideNotificationAccessIconFeatureId,
    "Hide Notification Access Icon",
    "system_drawer_removeshortcut"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_drawer_removeshortcut")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUINotificationHooks.HideNoficationAccessIconHook(lpparam)
}

internal class HideNoNotificationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideNoNotificationsFeatureId,
    "Hide No Notifications",
    "system_drawer_remove_emptynotify"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_drawer_remove_emptynotify")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUINotificationHooks.HideNoNotificationsHook(lpparam)
}

internal class HideImeDismissButtonFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideImeDismissButtonFeatureId,
    "Hide IME Dismiss Button",
    "controls_hide_ime_dismiss_button"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_hide_ime_dismiss_button")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.HideImeDismissButtonHook(lpparam)
}

internal class StatusBarContentGeometryFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarContentGeometryFeatureId,
    "Status Bar Content Geometry",
    "system_statusbar_content_vertical_offset"
) {
    companion object {
        /**
         * Always installed so status-bar height live changes can re-center content
         * and the global offset can apply without reinstalling hooks.
         * The hot path is a no-op when the window already matches the view and
         * the stored offset is the auto-center sentinel.
         */
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = true
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = StatusBarContentGeometryHooks.hook(lpparam)
}

internal class HideNavBarFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideNavBarFeatureId,
    "Hide Nav Bar",
    "controls_nonavbar"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_nonavbar")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.HideNavBarHook(lpparam)
}

internal class HideNavBarBeforeScreenshotFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideNavBarBeforeScreenshotFeatureId,
    "Hide Nav Bar Before Screenshot",
    "controls_hidenavbar_whenscreenshot"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = !prefs.getBoolean("controls_nonavbar") && prefs.getBoolean("controls_hidenavbar_whenscreenshot")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIScreenshotHooks.HideNavBarBeforeScreenshotHook(lpparam)
}

internal class AudioVisualizerFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AudioVisualizerFeatureId,
    "Audio Visualizer",
    "system_visualizer"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_visualizer")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemAudioHooks.AudioVisualizerHook(lpparam)
}

internal class ControlCenterPluginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ControlCenterPluginFeatureId,
    "Control Center Plugin",
    null
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = SystemUIControlCenterHooks.hasControlCenterModifications()
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.ControlCenterPluginHook(lpparam)
}

internal class BatteryIndicatorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BatteryIndicatorFeatureId,
    "Battery Indicator",
    "system_batteryindicator"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_batteryindicator")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.BatteryIndicatorHook(lpparam)
}

internal class DisableAnyNotificationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableAnyNotificationFeatureId,
    "Disable Any Notification",
    "system_disableanynotif"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_disableanynotif")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.DisableAnyNotificationHook(lpparam)
}

internal class LockScreenShortcutFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LockScreenShortcutFeatureId,
    "Lock Screen Shortcut",
    "system_lockscreenshortcuts"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_lockscreenshortcuts")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUILockScreenHooks.LockScreenShortcutHook(lpparam)
}

internal class MobileNetworkTypeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MobileNetworkTypeFeatureId,
    "Mobile Network Type",
    "system_4gtolte"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_4gtolte") ||
        (prefs.getBoolean("system_statusbar_mobiletype_single") &&
         prefs.getString("system_statusbar_mobile_showname", "").isNotEmpty())
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.MobileNetworkTypeHook(lpparam)
}

internal class StatusBarIconsPositionAdjustFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarIconsPositionAdjustFeatureId,
    "Status Bar Icons Position Adjust",
    "system_statusbar_alarm_atright"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = computeStatusBarIconsAdjust(prefs).first
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() =
        SystemUIStatusBarHooks.StatusBarIconsPositionAdjustHook(lpparam, computeStatusBarIconsAdjust(mPrefs).second)


}

/**
 * Immutable StrongToast presentation snapshot for one process publish / one event freeze.
 *
 * Dynamic Island is TOP-only: position and bottom-offset are no longer part of runtime state.
 * Legacy preference keys may still exist on disk and are ignored / migrated at read time.
 */
internal data class StrongToastRuntimeSnapshot(
    val mode: StrongToastPresentationMode,
    /**
     * Compatibility-only value for existing event ownership code. It is always TOP; legacy
     * persisted Bottom data is deliberately not read into runtime configuration.
     */
    val position: StrongToastPosition = StrongToastPosition.TOP,
    /** Compatibility-only zero. Bottom offsets are not part of TOP-only runtime state. */
    val bottomOffsetDp: Int = 0,
    /**
     * Signed manual nudge for the Dynamic Island's vertical anchor, in dp.
     *
     * The island is anchored to the reported display cutout, but panels vary in how faithfully the
     * declared cutout matches the visible camera hole, so a user-facing correction is needed. Zero
     * means "use the anchor as reported".
     */
    val islandOffsetDp: Int = 0,
) {
    val isDynamicIsland: Boolean
        get() = mode == StrongToastPresentationMode.DYNAMIC_ISLAND
}

/**
 * Process-scoped StrongToast runtime configuration.
 *
 * - Installs exactly one preference observer per process.
 * - Publishes an immutable [StrongToastRuntimeSnapshot] through an [AtomicReference].
 * - Rebuilds the snapshot when the presentation mode or island offset changes.
 * - Keeps reflection, Binder and disk I/O out of the hot path.
 * - Never reinstalls hooks on preference change.
 */
internal class StrongToastRuntimeState private constructor(
    private val refreshSource: () -> Map<String, Any>,
) {
    val snapshotRef = AtomicReference(DEFAULT_SNAPSHOT)

    private val refreshLock = Any()
    private var observerRegistered = false

    @JvmField
    val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) = ModuleHelper.guarded {
            onPreferenceChanged(key)
        }
    }

    private fun onPreferenceChanged(key: String?) {
        // Legacy position / bottom-offset keys are ignored: TOP-only migration is unconditional.
        if (key != null && key != MODE_KEY && key != ISLAND_OFFSET_KEY) {
            return
        }
        synchronized(refreshLock) {
            refreshSnapshotLocked()
        }
    }

    internal fun initialize() {
        synchronized(refreshLock) {
            refreshSnapshotLocked()
        }
    }

    private fun refreshSnapshotLocked() {
        try {
            val source = refreshSource()
            snapshotRef.set(buildSnapshot(source))
        } catch (e: Exception) {
            FatalErrors.rethrowIfFatal(e)
            XposedHelpers.log("StrongToastRuntimeState", e)
        }
    }

    internal fun installObserver() {
        if (!observerRegistered) {
            ModuleHelper.observePreferenceChange(preferenceObserver)
            observerRegistered = true
        }
    }

    companion object {
        const val MODE_KEY = "system_strong_toast_mode"
        const val ISLAND_OFFSET_KEY = "system_strong_toast_island_offset"
        /** Legacy key retained only so observers can ignore it without reinstall. */
        const val POSITION_KEY = "system_strong_toast_position"
        /** Legacy key retained only so observers can ignore it without reinstall. */
        const val BOTTOM_OFFSET_KEY = "system_strong_toast_bottom_offset"

        /**
         * SeekBarPreference clamps its minimum to zero, so a signed range is persisted unsigned and
         * shifted by its own midpoint, matching every other signed offset in the project. This must
         * stay equal to the `negativeShift` and `defaultValue` declared for the preference.
         */
        const val ISLAND_OFFSET_SHIFT = 24

        internal val DEFAULT_SNAPSHOT = StrongToastRuntimeSnapshot(
            StrongToastPresentationMode.SYSTEM_DEFAULT,
            StrongToastPosition.TOP,
            0,
            0,
        )

        @Volatile
        internal var installed: Boolean = false
        internal var instance: StrongToastRuntimeState? = null
        private val installLock = Any()

        @JvmStatic
        internal fun buildSnapshot(source: Map<String, Any>): StrongToastRuntimeSnapshot {
            val mode = StrongToastPresentationMode.fromPreference(parsePreferenceInt(source[MODE_KEY]))
            return StrongToastRuntimeSnapshot(
                mode,
                StrongToastPosition.TOP,
                0,
                resolveIslandOffsetDp(source[ISLAND_OFFSET_KEY]),
            )
        }

        /**
         * Absent means untouched, which is the shifted midpoint rather than a raw zero: a stored 0
         * is the most negative end of the range, not the neutral position.
         */
        @JvmStatic
        internal fun resolveIslandOffsetDp(value: Any?): Int =
            (parsePreferenceInt(value, ISLAND_OFFSET_SHIFT) - ISLAND_OFFSET_SHIFT)
                .coerceIn(-ISLAND_OFFSET_SHIFT, ISLAND_OFFSET_SHIFT)

        private fun parsePreferenceInt(value: Any?, fallback: Int = 0): Int = when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        } ?: fallback

        @JvmStatic
        internal fun install(prefs: PrefMap): StrongToastRuntimeState =
            install(StrongToastRuntimeState { prefs.getAll() })

        @JvmStatic
        internal fun install(runtimeState: StrongToastRuntimeState): StrongToastRuntimeState {
            if (installed) {
                return checkNotNull(instance) { "StrongToastRuntimeState installed but instance is null" }
            }
            synchronized(installLock) {
                if (installed) {
                    return checkNotNull(instance) { "StrongToastRuntimeState installed but instance is null" }
                }
                instance = runtimeState
                runtimeState.installObserver()
                runtimeState.initialize()
                installed = true
                return runtimeState
            }
        }
    }
}

internal class StrongToastPresentationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StrongToastPresentationFeatureId,
    "Strong Toast Presentation",
    "system_strong_toast_mode"
) {
    companion object {
        @JvmStatic
        fun resolveMode(prefs: PrefMap): StrongToastPresentationMode =
            StrongToastPresentationMode.fromPreference(
                prefs.getStringAsInt("system_strong_toast_mode", 0)
            )

        /**
         * Always installed for HyperOS 1 A14 P0.
         *
         * The StrongToast hooks own a fast passthrough for [StrongToastPresentationMode.SYSTEM_DEFAULT]
         * inside the event callbacks, so the hooks must be present even in the default mode.  This
         * lets mode changes reflect on the next SystemUI event without reinstalling hooks.
         */
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean =
            true
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() {
        val runtimeState = StrongToastRuntimeState.install(mPrefs)
        SystemUIStrongToastHooks.install(lpparam, runtimeState.snapshotRef)
    }
}

internal class MonitorDeviceInfoFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MonitorDeviceInfoFeatureId,
    "Monitor Device Info",
    "system_statusbar_batterytempandcurrent"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbar_batterytempandcurrent") ||
        prefs.getBoolean("system_statusbar_showdevicetemperature")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.MonitorDeviceInfoHook(lpparam, mPrefs)
}

internal class StatusBarClockPositionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarClockPositionFeatureId,
    "Status Bar Clock Position",
    "system_statusbar_clock_position"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_statusbar_clock_position", 1) > 1 &&
        !prefs.getBoolean("system_statusbar_dualrows")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.StatusBarClockPositionHook(lpparam)
}

internal class StatusBarStyleBatteryIconFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarStyleBatteryIconFeatureId,
    "Status Bar Style Battery Icon",
    "system_statusbar_batterystyle"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbar_batterystyle")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.StatusBarStyleBatteryIconHook(lpparam)
}

internal class LockScreenTopMarginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LockScreenTopMarginFeatureId,
    "Lock Screen Top Margin",
    "system_statusbar_topmargin"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbar_topmargin") &&
        prefs.getBoolean("system_statusbar_topmargin_unset_lockscreen")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUILockScreenHooks.LockScreenTopMarginHook(lpparam)
}

internal class HorizMarginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HorizMarginFeatureId,
    "Horiz Margin",
    "system_statusbar_horizmargin"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbar_horizmargin")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.HorizMarginHook(lpparam)
}

internal class BrightnessPctFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BrightnessPctFeatureId,
    "Brightness Pct",
    "system_showpct"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_showpct")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.BrightnessPctHook(lpparam)
}

internal class HideLockScreenStatusBarFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideLockScreenStatusBarFeatureId,
    "Hide Lock Screen Status Bar",
    "system_hidelsstatusbar"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_hidelsstatusbar")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.HideLockScreenStatusBarHook(lpparam)
}

internal class HideLockScreenClockFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideLockScreenClockFeatureId,
    "Hide Lock Screen Clock",
    "system_hidelsclock"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_hidelsclock")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.HideLockScreenClockHook(lpparam)
}

internal class ForceClockUseSystemFontsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ForceClockUseSystemFontsFeatureId,
    "Force Clock Use System Fonts",
    "system_ls_force_systemfonts"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_ls_force_systemfonts")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.ForceClockUseSystemFontsHook(lpparam)
}

internal class HideLockScreenHintFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideLockScreenHintFeatureId,
    "Hide Lock Screen Hint",
    "system_hidelshint"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_hidelshint")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.HideLockScreenHintHook(lpparam)
}

internal class AllowAllKeyguardFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AllowAllKeyguardFeatureId,
    "Allow All Keyguard",
    "system_allownotifonkeyguard"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_allownotifonkeyguard")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.AllowAllKeyguardHook(lpparam)
}

internal class AllowAllFloatFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AllowAllFloatFeatureId,
    "Allow All Float",
    "system_allownotiffloat"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_allownotiffloat")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.AllowAllFloatHook(lpparam)
}

internal class LockScreenAlarmFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LockScreenAlarmFeatureId,
    "Lock Screen Alarm",
    "system_lsalarm"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_lsalarm")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.LockScreenAlarmHook(lpparam)
}

internal class StatusBarGesturesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarGesturesFeatureId,
    "Status Bar Gestures",
    "system_statusbarcontrols"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbarcontrols")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.StatusBarGesturesHook(lpparam)
}

internal class NetSpeedIntervalFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NetSpeedIntervalFeatureId,
    "Net Speed Interval",
    "system_netspeedinterval"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_netspeedinterval", 4) != 4
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.NetSpeedIntervalHook(lpparam)
}

internal class DetailedNetSpeedFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DetailedNetSpeedFeatureId,
    "Detailed Net Speed",
    "system_detailednetspeed_style"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_detailednetspeed_style", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.DetailedNetSpeedHook(lpparam)
}

internal class NetSpeedStyleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NetSpeedStyleFeatureId,
    "Net Speed Style",
    "system_detailednetspeed_style"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_detailednetspeed_style", 1) > 1 ||
        prefs.getBoolean("system_netspeed_boldfont") ||
        prefs.getBoolean("system_netspeed_use_clock_style") ||
        prefs.getInt("system_netspeed_fontsize", 13) > 13 ||
        prefs.getInt("system_netspeed_fixedcontent_width", 10) > 10 ||
        prefs.getInt("system_netspeed_leftmargin", 0) > 0 ||
        prefs.getInt("system_netspeed_rightmargin", 0) > 0 ||
        prefs.getInt("system_netspeed_verticaloffset", 8) != 8 ||
        prefs.getStringAsInt("system_detailednetspeed_align", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.NetSpeedStyleHook(lpparam)
}

internal class TapToUnlockFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    TapToUnlockFeatureId,
    "Tap To Unlock",
    "system_taptounlock"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_taptounlock")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.TapToUnlockHook(lpparam)
}

internal class NoSOSFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NoSOSFeatureId,
    "No SOS",
    "system_nosos"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_nosos")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.NoSOSHook(lpparam)
}

internal class RemovePackageNotificationsLimitFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    RemovePackageNotificationsLimitFeatureId,
    "Remove Package Notifications Limit",
    "system_morenotif"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_morenotif")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUINotificationHooks.RemovePackageNotificationsLimitHook(lpparam)
}

internal class DisableFoldNotificationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableFoldNotificationsFeatureId,
    "Disable Fold Notifications",
    "system_notif_disable_fold"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_notif_disable_fold")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUINotificationHooks.DisableFoldNotificationsHook(lpparam)
}

internal class ChargingInfoFeature(
    private val lpparam: PackageReadyParam
) : FeatureDefinition {

    override val id = ChargingInfoFeatureId
    override val name = "Charging Info"
    override val preferenceKey = "system_charginginfo"
    override val target = FeatureTarget.SYSTEM_UI
    override val phase = InstallPhase.PACKAGE_READY

    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_charginginfo")
    }

    override fun isEnabled(prefs: PrefMap) = Companion.evaluateEnabled(prefs)

    override fun install(): FeatureInstallResult = SystemLockScreenHooks.ChargingInfoHook(lpparam)
}

internal class SecureQSTilesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    SecureQSTilesFeatureId,
    "Secure QS Tiles",
    "system_secureqs"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_secureqs")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.SecureQSTilesHook(lpparam)
}

internal class MuteVisibleNotificationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MuteVisibleNotificationsFeatureId,
    "Mute Visible Notifications",
    "system_mutevisiblenotif"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_mutevisiblenotif")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.MuteVisibleNotificationsHook(lpparam)
}

internal class HideIconsBattery1Feature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsBattery1FeatureId,
    "Hide Icons Battery1",
    "system_statusbaricons_battery1"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbaricons_battery1")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemStatusBarIconHooks.HideIconsBattery1Hook(lpparam)
}

internal class HideIconsBattery2Feature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsBattery2FeatureId,
    "Hide Icons Battery2",
    "system_statusbaricons_battery3"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbaricons_battery3") ||
        prefs.getBoolean("system_statusbaricons_battery4") ||
        prefs.getBoolean("system_statusbaricons_battery2")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemStatusBarIconHooks.HideIconsBattery2Hook(lpparam)
}

internal class DisplayWifiStandardFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisplayWifiStandardFeatureId,
    "Display WiFi Standard",
    "system_statusbaricons_wifistandard"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_statusbaricons_wifistandard", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemStatusBarIconHooks.DisplayWifiStandardHook(lpparam)
}

internal class HidePrivacyIndicatorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HidePrivacyIndicatorFeatureId,
    "Hide Privacy Indicator",
    "system_statusbaricons_privacy_prompt"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbaricons_privacy_prompt")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.HidePrivacyIndicatorHook(lpparam)
}

internal class HideIconsSignalFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsSignalFeatureId,
    "Hide Icons Signal",
    "system_statusbaricons_signal"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbaricons_signal") ||
        prefs.getBoolean("system_statusbaricons_sim1") ||
        prefs.getBoolean("system_statusbaricons_sim2") ||
        prefs.getBoolean("system_statusbaricons_sim_nodata") ||
        prefs.getBoolean("system_statusbaricons_roaming") ||
        prefs.getBoolean("system_statusbaricons_volte")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.HideIconsSignalHook(lpparam)
}

internal class HideIconsVoWiFiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsVoWiFiFeatureId,
    "Hide Icons VoWiFi",
    "system_statusbaricons_vowifi"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbaricons_vowifi")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.HideIconsVoWiFiHook(lpparam)
}

internal class HideIconsSelectiveAlarmFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsSelectiveAlarmFeatureId,
    "Hide Icons Selective Alarm",
    "system_statusbaricons_alarm"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = !prefs.getBoolean("system_statusbaricons_alarm") &&
        prefs.getInt("system_statusbaricons_alarmn", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemStatusBarIconHooks.HideIconsSelectiveAlarmHook(lpparam)
}

internal class ReplaceShortcutAppFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ReplaceShortcutAppFeatureId,
    "Replace Shortcut App",
    "system_shortcut_app"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getString("system_shortcut_app", "").isNotEmpty() ||
        prefs.getString("system_calendar_app", "").isNotEmpty() ||
        prefs.getString("system_clock_app", "").isNotEmpty()
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUILockScreenHooks.ReplaceShortcutAppHook(lpparam)
}

internal class QSHapticFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    QSHapticFeatureId,
    "QS Haptic",
    "system_qshaptics"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_qshaptics", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemAudioHooks.QSHapticHook(lpparam)
}

internal class CollapseCCAfterClickFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    CollapseCCAfterClickFeatureId,
    "Collapse CC After Click",
    "system_cc_collapse_after_clicked"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cc_collapse_after_clicked")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.CollapseCCAfterClickHook(lpparam)
}

internal class LongClickTileOpenInFreeFormFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LongClickTileOpenInFreeFormFeatureId,
    "Long Click Tile Open In Free Form",
    "system_cc_freeform_when_longclick"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cc_freeform_when_longclick")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.LongClickTileOpenInFreeFormHook(lpparam)
}

internal class SwitchCCAndNotificationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    SwitchCCAndNotificationFeatureId,
    "Switch CC And Notification",
    "system_cc_switch_qsandnotification"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cc_switch_qsandnotification")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.SwitchCCAndNotificationHook(lpparam)
}

internal class ExpandNotificationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ExpandNotificationsFeatureId,
    "Expand Notifications",
    "system_expandnotifs"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_expandnotifs", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.ExpandNotificationsHook(lpparam)
}

internal class HideMobileNetworkIndicatorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideMobileNetworkIndicatorFeatureId,
    "Hide Mobile Network Indicator",
    "system_mobiletypeicon"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_mobiletypeicon", 1) > 1 ||
        prefs.getBoolean("system_networkindicator_mobile") ||
        prefs.getBoolean("system_statusbar_mobiletype_show_wificonnected")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.HideMobileNetworkIndicatorHook(lpparam)
}

internal class ExtendedPowerMenuFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ExtendedPowerMenuFeatureId,
    "Extended Power Menu",
    "system_epm"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_epm")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUI.ExtendedPowerMenuHook(lpparam)
}

internal class HideIconsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsFeatureId,
    "Hide Icons",
    "system_statusbaricons_wifi"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbaricons_wifi") ||
        prefs.getBoolean("system_statusbaricons_dualwifi") ||
        prefs.getBoolean("system_statusbaricons_alarm") ||
        prefs.getBoolean("system_statusbaricons_profile") ||
        prefs.getBoolean("system_statusbaricons_sound") ||
        prefs.getBoolean("system_statusbaricons_dnd") ||
        prefs.getBoolean("system_statusbaricons_secondspace") ||
        prefs.getBoolean("system_statusbaricons_headset") ||
        prefs.getBoolean("system_statusbaricons_nfc") ||
        prefs.getBoolean("system_statusbaricons_vpn") ||
        prefs.getBoolean("system_statusbaricons_airplane") ||
        prefs.getBoolean("system_statusbaricons_hotspot") ||
        prefs.getBoolean("system_statusbaricons_nosims") ||
        prefs.getBoolean("system_statusbaricons_gps") ||
        prefs.getBoolean("system_statusbaricons_btbattery") ||
        prefs.getBoolean("system_statusbaricons_ble_unlock") ||
        prefs.getBoolean("system_statusbaricons_bluetoothicn") ||
        prefs.getBoolean("system_statusbaricons_volte")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.HideIconsHook(lpparam)
}

internal class HideIconsFromSystemManagerFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsFromSystemManagerFeatureId,
    "Hide Icons From System Manager",
    "system_statusbaricons_privacy"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbaricons_privacy") ||
        prefs.getBoolean("system_statusbaricons_mute") ||
        prefs.getBoolean("system_statusbaricons_speaker") ||
        prefs.getBoolean("system_statusbaricons_record") ||
        prefs.getBoolean("system_statusbaricons_wireless_headset")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.HideIconsFromSystemManager(lpparam)
}

internal class BetterPopupsAllowFloatFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BetterPopupsAllowFloatFeatureId,
    "Better Popups Allow Float",
    "system_betterpopups_allowfloat"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_betterpopups_allowfloat")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.BetterPopupsAllowFloatHook(lpparam)
}

internal class AutoDismissExpandedPopupsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AutoDismissExpandedPopupsFeatureId,
    "Auto Dismiss Expanded Popups",
    "system_betterpopups_autoclose_expanded"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_betterpopups_autoclose_expanded")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.AutoDismissExpandedPopupsHook(lpparam)
}

internal class DisableHeadsUpWhenMuteFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableHeadsUpWhenMuteFeatureId,
    "Disable Heads Up When Mute",
    "system_betterpopups_disablewhenmute"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_betterpopups_disablewhenmute")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUINotificationHooks.DisableHeadsUpWhenMuteHook(lpparam)
}

internal class MinimalNotificationViewFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MinimalNotificationViewFeatureId,
    "Minimal Notification View",
    "system_minimalnotifview"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_minimalnotifview")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.MinimalNotificationViewHook(lpparam)
}

internal class NotificationChannelSettingsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NotificationChannelSettingsFeatureId,
    "Notification Channel Settings",
    "system_notifchannelsettings"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_notifchannelsettings")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.NotificationChannelSettingsHook(lpparam)
}

internal class MaxNotificationIconsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MaxNotificationIconsFeatureId,
    "Max Notification Icons",
    "system_maxsbicons"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_maxsbicons", 0) != 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.MaxNotificationIconsHook(lpparam)
}

internal class MobileTypeSingleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MobileTypeSingleFeatureId,
    "Mobile Type Single",
    "system_statusbar_mobiletype_single"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbar_mobiletype_single")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.MobileTypeSingleHook(lpparam)
}

internal class StatusBarDigitalSignalFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarDigitalSignalFeatureId,
    "Status Bar Digital Signal",
    "system_statusbar_mobile_digital_signal"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbar_mobile_digital_signal")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.StatusBarDigitalSignalHook(lpparam)
}

internal class DualRowSignalFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DualRowSignalFeatureId,
    "Dual Row Signal",
    "system_statusbar_dualsimin2rows"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = !prefs.getBoolean("system_statusbar_mobile_digital_signal") &&
        prefs.getBoolean("system_statusbar_dualsimin2rows")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.DualRowSignalHook(lpparam)
}

internal class DualRowsStatusbarFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DualRowsStatusbarFeatureId,
    "Dual Rows Statusbar",
    "system_statusbar_dualrows"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_statusbar_dualrows")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIStatusBarHooks.DualRowsStatusbarHook(lpparam)
}

internal class ColorizeNotificationCardFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ColorizeNotificationCardFeatureId,
    "Colorize Notification Card",
    "system_colorizenotifs"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_colorizenotifs", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemColorizeNotificationHooks.ColorizeNotificationCardHook(lpparam)
}

internal class OpenNotifyInFloatingWindowFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    OpenNotifyInFloatingWindowFeatureId,
    "Open Notify In Floating Window",
    "system_notify_openinfw"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_notify_openinfw")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUINotificationHooks.OpenNotifyInFloatingWindowHook(lpparam)
}

internal class DisableSideBarSuggestionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableSideBarSuggestionFeatureId,
    "Disable Side Bar Suggestion",
    "system_fw_noblacklist"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_fw_noblacklist")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.DisableSideBarSuggestionHook(lpparam)
}

internal class HideSafeVolumeDlgFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideSafeVolumeDlgFeatureId,
    "Hide Safe Volume Dlg",
    "system_nosafevolume"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_nosafevolume")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUIControlCenterHooks.HideSafeVolumeDlgHook(lpparam)
}

internal class HideLockscreenZenModeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideLockscreenZenModeFeatureId,
    "Hide Lockscreen Zen Mode",
    "system_lockscreen_hidezenmode"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_lockscreen_hidezenmode")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUILockScreenHooks.HideLockscreenZenModeHook(lpparam)
}

internal class DisableKeyguardEditorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableKeyguardEditorFeatureId,
    "Disable Keyguard Editor",
    "system_lockscreen_disable_edit"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_lockscreen_disable_edit")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUILockScreenHooks.DisableKeyguardEditorHook(lpparam)
}

internal class NoPasswordFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NoPasswordFeatureId,
    "No Password",
    "system_nopassword"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_nopassword")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.NoPasswordHook(lpparam)
}

internal class NotificationImportanceFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NotificationImportanceFeatureId,
    "Notification Importance",
    "system_notifimportance"
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_notifimportance")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUINotificationHooks.NotificationImportanceHook(lpparam)
}

internal class NoLightUpOnChargeSystemUiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NoLightUpOnChargeSystemUiFeatureId,
    "No Light Up On Charge System UI",
    "system_nolightuponcharges"
) {
    companion object {
        // Option 2 and 3 both suppress the charge animation. Option 3 still wakes natively.
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_nolightuponcharges", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemUI.NoLightUpOnChargeHook(lpparam)
}

/**
 * All preference-guarded features that belong in the SystemUI process.
 */
object SystemUiFeatures {
    @JvmStatic
    fun all(
        lpparam: PackageReadyParam,
        mPrefs: PrefMap
    ): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = ForegroundMonitorFeatureId,
            name = "Foreground Monitor",
            preferenceKey = "various_showcallui",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ForegroundMonitorFeature.evaluateEnabled(prefs) },
            factory = { ForegroundMonitorFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = TempHideOverlaySystemUiFeatureId,
            name = "Temp Hide Overlay System UI",
            preferenceKey = "system_screenshot_overlay",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> TempHideOverlaySystemUiFeature.evaluateEnabled(prefs) },
            factory = { TempHideOverlaySystemUiFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AddCustomTileFeatureId,
            name = "Add Custom Tile",
            preferenceKey = "system_fivegtile",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AddCustomTileFeature.evaluateEnabled(prefs) },
            factory = { AddCustomTileFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideStatusBarWhenCaptureFeatureId,
            name = "Hide Status Bar When Capture",
            preferenceKey = "system_hidestatusbar_whenscreenshot",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideStatusBarWhenCaptureFeature.evaluateEnabled(prefs) },
            factory = { HideStatusBarWhenCaptureFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NetworkIndicatorWifiFeatureId,
            name = "Network Indicator WiFi",
            preferenceKey = "system_networkindicator_wifi",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NetworkIndicatorWifiFeature.evaluateEnabled(prefs) },
            factory = { NetworkIndicatorWifiFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DrawerBlurRatioFeatureId,
            name = "Drawer Blur Ratio",
            preferenceKey = "system_drawer_blur",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DrawerBlurRatioFeature.evaluateEnabled(prefs) },
            factory = { DrawerBlurRatioFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ChargeAnimationFeatureId,
            name = "Charge Animation",
            preferenceKey = "system_chargeanimtime",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ChargeAnimationFeature.evaluateEnabled(prefs) },
            factory = { ChargeAnimationFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = BetterPopupsHideDelayFeatureId,
            name = "Better Popups Hide Delay",
            preferenceKey = "system_betterpopups_delay",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> BetterPopupsHideDelayFeature.evaluateEnabled(prefs) },
            factory = { BetterPopupsHideDelayFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AssistGestureActionFeatureId,
            name = "Assist Gesture Action",
            preferenceKey = "controls_fsg_assist_left_action",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AssistGestureActionFeature.evaluateEnabled(prefs) },
            factory = { AssistGestureActionFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NavBarButtonsFeatureId,
            name = "Nav Bar Buttons",
            preferenceKey = "controls_navbarleft_action",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NavBarButtonsFeature.evaluateEnabled(prefs) },
            factory = { NavBarButtonsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ScramblePinFeatureId,
            name = "Scramble PIN",
            preferenceKey = "system_scramblepin",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ScramblePinFeature.evaluateEnabled(prefs) },
            factory = { ScramblePinFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DoubleTapToSleepFeatureId,
            name = "Double Tap To Sleep",
            preferenceKey = "system_dttosleep",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DoubleTapToSleepFeature.evaluateEnabled(prefs) },
            factory = { DoubleTapToSleepFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = StatusBarClockTweakFeatureId,
            name = "Status Bar Clock Tweak",
            preferenceKey = "system_statusbar_clocktweak",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StatusBarClockTweakFeature.evaluateEnabled(prefs) },
            factory = { StatusBarClockTweakFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = CcClockTweakFeatureId,
            name = "CC Clock Tweak",
            preferenceKey = "system_cc_clocktweak",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> CcClockTweakFeature.evaluateEnabled(prefs) },
            factory = { CcClockTweakFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DisableFakeClockAnimFeatureId,
            name = "Disable Fake Clock Anim",
            preferenceKey = "system_qs_disable_fakeclock_anim",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DisableFakeClockAnimFeature.evaluateEnabled(prefs) },
            factory = { DisableFakeClockAnimFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = CcClockCenterAlignFeatureId,
            name = "CC Clock Center Align",
            preferenceKey = "system_cc_clock_centeralign",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> CcClockCenterAlignFeature.evaluateEnabled(prefs) },
            factory = { CcClockCenterAlignFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NoScreenLockFeatureId,
            name = "No Screen Lock",
            preferenceKey = "system_noscreenlock_act",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NoScreenLockFeature.evaluateEnabled(prefs) },
            factory = { NoScreenLockFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LockScreenAlbumArtFeatureId,
            name = "Lock Screen Album Art",
            preferenceKey = "system_albumartonlock",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LockScreenAlbumArtFeature.evaluateEnabled(prefs) },
            factory = { LockScreenAlbumArtFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ExpandHeadsUpFeatureId,
            name = "Expand Heads Up",
            preferenceKey = "system_expandheadups",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ExpandHeadsUpFeature.evaluateEnabled(prefs) },
            factory = { ExpandHeadsUpFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = BetterPopupsNoHideFeatureId,
            name = "Better Popups No Hide",
            preferenceKey = "system_betterpopups_nohide",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> BetterPopupsNoHideFeature.evaluateEnabled(prefs) },
            factory = { BetterPopupsNoHideFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = BetterPopupsCenteredFeatureId,
            name = "Better Popups Centered",
            preferenceKey = "system_betterpopups_center",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> BetterPopupsCenteredFeature.evaluateEnabled(prefs) },
            factory = { BetterPopupsCenteredFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ShowNotificationsAfterUnlockFeatureId,
            name = "Show Notifications After Unlock",
            preferenceKey = "system_notifafterunlock",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ShowNotificationsAfterUnlockFeature.evaluateEnabled(prefs) },
            factory = { ShowNotificationsAfterUnlockFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NotificationRowMenuFeatureId,
            name = "Notification Row Menu",
            preferenceKey = "system_notifrowmenu",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NotificationRowMenuFeature.evaluateEnabled(prefs) },
            factory = { NotificationRowMenuFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideDismissViewFeatureId,
            name = "Hide Dismiss View",
            preferenceKey = "system_removedismiss",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideDismissViewFeature.evaluateEnabled(prefs) },
            factory = { HideDismissViewFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideNotificationAccessIconFeatureId,
            name = "Hide Notification Access Icon",
            preferenceKey = "system_drawer_removeshortcut",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideNotificationAccessIconFeature.evaluateEnabled(prefs) },
            factory = { HideNotificationAccessIconFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideNoNotificationsFeatureId,
            name = "Hide No Notifications",
            preferenceKey = "system_drawer_remove_emptynotify",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideNoNotificationsFeature.evaluateEnabled(prefs) },
            factory = { HideNoNotificationsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideImeDismissButtonFeatureId,
            name = "Hide IME Dismiss Button",
            preferenceKey = "controls_hide_ime_dismiss_button",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideImeDismissButtonFeature.evaluateEnabled(prefs) },
            factory = { HideImeDismissButtonFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = StatusBarContentGeometryFeatureId,
            name = "Status Bar Content Geometry",
            preferenceKey = "system_statusbar_content_vertical_offset",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StatusBarContentGeometryFeature.evaluateEnabled(prefs) },
            factory = { StatusBarContentGeometryFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideNavBarFeatureId,
            name = "Hide Nav Bar",
            preferenceKey = "controls_nonavbar",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideNavBarFeature.evaluateEnabled(prefs) },
            factory = { HideNavBarFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideNavBarBeforeScreenshotFeatureId,
            name = "Hide Nav Bar Before Screenshot",
            preferenceKey = "controls_hidenavbar_whenscreenshot",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideNavBarBeforeScreenshotFeature.evaluateEnabled(prefs) },
            factory = { HideNavBarBeforeScreenshotFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AudioVisualizerFeatureId,
            name = "Audio Visualizer",
            preferenceKey = "system_visualizer",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AudioVisualizerFeature.evaluateEnabled(prefs) },
            factory = { AudioVisualizerFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ControlCenterPluginFeatureId,
            name = "Control Center Plugin",
            preferenceKey = null,
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ControlCenterPluginFeature.evaluateEnabled(prefs) },
            factory = { ControlCenterPluginFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = BatteryIndicatorFeatureId,
            name = "Battery Indicator",
            preferenceKey = "system_batteryindicator",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> BatteryIndicatorFeature.evaluateEnabled(prefs) },
            factory = { BatteryIndicatorFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DisableAnyNotificationFeatureId,
            name = "Disable Any Notification",
            preferenceKey = "system_disableanynotif",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DisableAnyNotificationFeature.evaluateEnabled(prefs) },
            factory = { DisableAnyNotificationFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LockScreenShortcutFeatureId,
            name = "Lock Screen Shortcut",
            preferenceKey = "system_lockscreenshortcuts",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LockScreenShortcutFeature.evaluateEnabled(prefs) },
            factory = { LockScreenShortcutFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = MobileNetworkTypeFeatureId,
            name = "Mobile Network Type",
            preferenceKey = "system_4gtolte",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MobileNetworkTypeFeature.evaluateEnabled(prefs) },
            factory = { MobileNetworkTypeFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = StatusBarIconsPositionAdjustFeatureId,
            name = "Status Bar Icons Position Adjust",
            preferenceKey = "system_statusbar_alarm_atright",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StatusBarIconsPositionAdjustFeature.evaluateEnabled(prefs) },
            factory = { StatusBarIconsPositionAdjustFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = StrongToastPresentationFeatureId,
            name = "Strong Toast Presentation",
            preferenceKey = "system_strong_toast_mode",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StrongToastPresentationFeature.evaluateEnabled(prefs) },
            factory = { StrongToastPresentationFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = MonitorDeviceInfoFeatureId,
            name = "Monitor Device Info",
            preferenceKey = "system_statusbar_batterytempandcurrent",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MonitorDeviceInfoFeature.evaluateEnabled(prefs) },
            factory = { MonitorDeviceInfoFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = StatusBarClockPositionFeatureId,
            name = "Status Bar Clock Position",
            preferenceKey = "system_statusbar_clock_position",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StatusBarClockPositionFeature.evaluateEnabled(prefs) },
            factory = { StatusBarClockPositionFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = StatusBarStyleBatteryIconFeatureId,
            name = "Status Bar Style Battery Icon",
            preferenceKey = "system_statusbar_batterystyle",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StatusBarStyleBatteryIconFeature.evaluateEnabled(prefs) },
            factory = { StatusBarStyleBatteryIconFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LockScreenTopMarginFeatureId,
            name = "Lock Screen Top Margin",
            preferenceKey = "system_statusbar_topmargin",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LockScreenTopMarginFeature.evaluateEnabled(prefs) },
            factory = { LockScreenTopMarginFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HorizMarginFeatureId,
            name = "Horiz Margin",
            preferenceKey = "system_statusbar_horizmargin",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HorizMarginFeature.evaluateEnabled(prefs) },
            factory = { HorizMarginFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = BrightnessPctFeatureId,
            name = "Brightness Pct",
            preferenceKey = "system_showpct",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> BrightnessPctFeature.evaluateEnabled(prefs) },
            factory = { BrightnessPctFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideLockScreenStatusBarFeatureId,
            name = "Hide Lock Screen Status Bar",
            preferenceKey = "system_hidelsstatusbar",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideLockScreenStatusBarFeature.evaluateEnabled(prefs) },
            factory = { HideLockScreenStatusBarFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideLockScreenClockFeatureId,
            name = "Hide Lock Screen Clock",
            preferenceKey = "system_hidelsclock",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideLockScreenClockFeature.evaluateEnabled(prefs) },
            factory = { HideLockScreenClockFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ForceClockUseSystemFontsFeatureId,
            name = "Force Clock Use System Fonts",
            preferenceKey = "system_ls_force_systemfonts",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ForceClockUseSystemFontsFeature.evaluateEnabled(prefs) },
            factory = { ForceClockUseSystemFontsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideLockScreenHintFeatureId,
            name = "Hide Lock Screen Hint",
            preferenceKey = "system_hidelshint",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideLockScreenHintFeature.evaluateEnabled(prefs) },
            factory = { HideLockScreenHintFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AllowAllKeyguardFeatureId,
            name = "Allow All Keyguard",
            preferenceKey = "system_allownotifonkeyguard",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AllowAllKeyguardFeature.evaluateEnabled(prefs) },
            factory = { AllowAllKeyguardFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AllowAllFloatFeatureId,
            name = "Allow All Float",
            preferenceKey = "system_allownotiffloat",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AllowAllFloatFeature.evaluateEnabled(prefs) },
            factory = { AllowAllFloatFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LockScreenAlarmFeatureId,
            name = "Lock Screen Alarm",
            preferenceKey = "system_lsalarm",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LockScreenAlarmFeature.evaluateEnabled(prefs) },
            factory = { LockScreenAlarmFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = StatusBarGesturesFeatureId,
            name = "Status Bar Gestures",
            preferenceKey = "system_statusbarcontrols",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StatusBarGesturesFeature.evaluateEnabled(prefs) },
            factory = { StatusBarGesturesFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NetSpeedIntervalFeatureId,
            name = "Net Speed Interval",
            preferenceKey = "system_netspeedinterval",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NetSpeedIntervalFeature.evaluateEnabled(prefs) },
            factory = { NetSpeedIntervalFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DetailedNetSpeedFeatureId,
            name = "Detailed Net Speed",
            preferenceKey = "system_detailednetspeed_style",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DetailedNetSpeedFeature.evaluateEnabled(prefs) },
            factory = { DetailedNetSpeedFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NetSpeedStyleFeatureId,
            name = "Net Speed Style",
            preferenceKey = "system_detailednetspeed_style",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NetSpeedStyleFeature.evaluateEnabled(prefs) },
            factory = { NetSpeedStyleFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = TapToUnlockFeatureId,
            name = "Tap To Unlock",
            preferenceKey = "system_taptounlock",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> TapToUnlockFeature.evaluateEnabled(prefs) },
            factory = { TapToUnlockFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NoSOSFeatureId,
            name = "No SOS",
            preferenceKey = "system_nosos",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NoSOSFeature.evaluateEnabled(prefs) },
            factory = { NoSOSFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = RemovePackageNotificationsLimitFeatureId,
            name = "Remove Package Notifications Limit",
            preferenceKey = "system_morenotif",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> RemovePackageNotificationsLimitFeature.evaluateEnabled(prefs) },
            factory = { RemovePackageNotificationsLimitFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DisableFoldNotificationsFeatureId,
            name = "Disable Fold Notifications",
            preferenceKey = "system_notif_disable_fold",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DisableFoldNotificationsFeature.evaluateEnabled(prefs) },
            factory = { DisableFoldNotificationsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ChargingInfoFeatureId,
            name = "Charging Info",
            preferenceKey = "system_charginginfo",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ChargingInfoFeature.evaluateEnabled(prefs) },
            factory = { ChargingInfoFeature(lpparam) },
        ),
        LazyFeatureSpec(
            id = SecureQSTilesFeatureId,
            name = "Secure QS Tiles",
            preferenceKey = "system_secureqs",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecureQSTilesFeature.evaluateEnabled(prefs) },
            factory = { SecureQSTilesFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = MuteVisibleNotificationsFeatureId,
            name = "Mute Visible Notifications",
            preferenceKey = "system_mutevisiblenotif",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MuteVisibleNotificationsFeature.evaluateEnabled(prefs) },
            factory = { MuteVisibleNotificationsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideIconsBattery1FeatureId,
            name = "Hide Icons Battery1",
            preferenceKey = "system_statusbaricons_battery1",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideIconsBattery1Feature.evaluateEnabled(prefs) },
            factory = { HideIconsBattery1Feature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideIconsBattery2FeatureId,
            name = "Hide Icons Battery2",
            preferenceKey = "system_statusbaricons_battery3",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideIconsBattery2Feature.evaluateEnabled(prefs) },
            factory = { HideIconsBattery2Feature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DisplayWifiStandardFeatureId,
            name = "Display WiFi Standard",
            preferenceKey = "system_statusbaricons_wifistandard",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DisplayWifiStandardFeature.evaluateEnabled(prefs) },
            factory = { DisplayWifiStandardFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HidePrivacyIndicatorFeatureId,
            name = "Hide Privacy Indicator",
            preferenceKey = "system_statusbaricons_privacy_prompt",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HidePrivacyIndicatorFeature.evaluateEnabled(prefs) },
            factory = { HidePrivacyIndicatorFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideIconsSignalFeatureId,
            name = "Hide Icons Signal",
            preferenceKey = "system_statusbaricons_signal",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideIconsSignalFeature.evaluateEnabled(prefs) },
            factory = { HideIconsSignalFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideIconsVoWiFiFeatureId,
            name = "Hide Icons VoWiFi",
            preferenceKey = "system_statusbaricons_vowifi",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideIconsVoWiFiFeature.evaluateEnabled(prefs) },
            factory = { HideIconsVoWiFiFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideIconsSelectiveAlarmFeatureId,
            name = "Hide Icons Selective Alarm",
            preferenceKey = "system_statusbaricons_alarm",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideIconsSelectiveAlarmFeature.evaluateEnabled(prefs) },
            factory = { HideIconsSelectiveAlarmFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ReplaceShortcutAppFeatureId,
            name = "Replace Shortcut App",
            preferenceKey = "system_shortcut_app",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ReplaceShortcutAppFeature.evaluateEnabled(prefs) },
            factory = { ReplaceShortcutAppFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = QSHapticFeatureId,
            name = "QS Haptic",
            preferenceKey = "system_qshaptics",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> QSHapticFeature.evaluateEnabled(prefs) },
            factory = { QSHapticFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = CollapseCCAfterClickFeatureId,
            name = "Collapse CC After Click",
            preferenceKey = "system_cc_collapse_after_clicked",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> CollapseCCAfterClickFeature.evaluateEnabled(prefs) },
            factory = { CollapseCCAfterClickFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LongClickTileOpenInFreeFormFeatureId,
            name = "Long Click Tile Open In Free Form",
            preferenceKey = "system_cc_freeform_when_longclick",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LongClickTileOpenInFreeFormFeature.evaluateEnabled(prefs) },
            factory = { LongClickTileOpenInFreeFormFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SwitchCCAndNotificationFeatureId,
            name = "Switch CC And Notification",
            preferenceKey = "system_cc_switch_qsandnotification",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SwitchCCAndNotificationFeature.evaluateEnabled(prefs) },
            factory = { SwitchCCAndNotificationFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ExpandNotificationsFeatureId,
            name = "Expand Notifications",
            preferenceKey = "system_expandnotifs",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ExpandNotificationsFeature.evaluateEnabled(prefs) },
            factory = { ExpandNotificationsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideMobileNetworkIndicatorFeatureId,
            name = "Hide Mobile Network Indicator",
            preferenceKey = "system_mobiletypeicon",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideMobileNetworkIndicatorFeature.evaluateEnabled(prefs) },
            factory = { HideMobileNetworkIndicatorFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ExtendedPowerMenuFeatureId,
            name = "Extended Power Menu",
            preferenceKey = "system_epm",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ExtendedPowerMenuFeature.evaluateEnabled(prefs) },
            factory = { ExtendedPowerMenuFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideIconsFeatureId,
            name = "Hide Icons",
            preferenceKey = "system_statusbaricons_wifi",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideIconsFeature.evaluateEnabled(prefs) },
            factory = { HideIconsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideIconsFromSystemManagerFeatureId,
            name = "Hide Icons From System Manager",
            preferenceKey = "system_statusbaricons_privacy",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideIconsFromSystemManagerFeature.evaluateEnabled(prefs) },
            factory = { HideIconsFromSystemManagerFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = BetterPopupsAllowFloatFeatureId,
            name = "Better Popups Allow Float",
            preferenceKey = "system_betterpopups_allowfloat",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> BetterPopupsAllowFloatFeature.evaluateEnabled(prefs) },
            factory = { BetterPopupsAllowFloatFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AutoDismissExpandedPopupsFeatureId,
            name = "Auto Dismiss Expanded Popups",
            preferenceKey = "system_betterpopups_autoclose_expanded",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AutoDismissExpandedPopupsFeature.evaluateEnabled(prefs) },
            factory = { AutoDismissExpandedPopupsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DisableHeadsUpWhenMuteFeatureId,
            name = "Disable Heads Up When Mute",
            preferenceKey = "system_betterpopups_disablewhenmute",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DisableHeadsUpWhenMuteFeature.evaluateEnabled(prefs) },
            factory = { DisableHeadsUpWhenMuteFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = MinimalNotificationViewFeatureId,
            name = "Minimal Notification View",
            preferenceKey = "system_minimalnotifview",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MinimalNotificationViewFeature.evaluateEnabled(prefs) },
            factory = { MinimalNotificationViewFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NotificationChannelSettingsFeatureId,
            name = "Notification Channel Settings",
            preferenceKey = "system_notifchannelsettings",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NotificationChannelSettingsFeature.evaluateEnabled(prefs) },
            factory = { NotificationChannelSettingsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = MaxNotificationIconsFeatureId,
            name = "Max Notification Icons",
            preferenceKey = "system_maxsbicons",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MaxNotificationIconsFeature.evaluateEnabled(prefs) },
            factory = { MaxNotificationIconsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = MobileTypeSingleFeatureId,
            name = "Mobile Type Single",
            preferenceKey = "system_statusbar_mobiletype_single",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MobileTypeSingleFeature.evaluateEnabled(prefs) },
            factory = { MobileTypeSingleFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = StatusBarDigitalSignalFeatureId,
            name = "Status Bar Digital Signal",
            preferenceKey = "system_statusbar_mobile_digital_signal",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StatusBarDigitalSignalFeature.evaluateEnabled(prefs) },
            factory = { StatusBarDigitalSignalFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DualRowSignalFeatureId,
            name = "Dual Row Signal",
            preferenceKey = "system_statusbar_dualsimin2rows",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DualRowSignalFeature.evaluateEnabled(prefs) },
            factory = { DualRowSignalFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DualRowsStatusbarFeatureId,
            name = "Dual Rows Statusbar",
            preferenceKey = "system_statusbar_dualrows",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DualRowsStatusbarFeature.evaluateEnabled(prefs) },
            factory = { DualRowsStatusbarFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = ColorizeNotificationCardFeatureId,
            name = "Colorize Notification Card",
            preferenceKey = "system_colorizenotifs",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> ColorizeNotificationCardFeature.evaluateEnabled(prefs) },
            factory = { ColorizeNotificationCardFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = OpenNotifyInFloatingWindowFeatureId,
            name = "Open Notify In Floating Window",
            preferenceKey = "system_notify_openinfw",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> OpenNotifyInFloatingWindowFeature.evaluateEnabled(prefs) },
            factory = { OpenNotifyInFloatingWindowFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DisableSideBarSuggestionFeatureId,
            name = "Disable Side Bar Suggestion",
            preferenceKey = "system_fw_noblacklist",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DisableSideBarSuggestionFeature.evaluateEnabled(prefs) },
            factory = { DisableSideBarSuggestionFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideSafeVolumeDlgFeatureId,
            name = "Hide Safe Volume Dlg",
            preferenceKey = "system_nosafevolume",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideSafeVolumeDlgFeature.evaluateEnabled(prefs) },
            factory = { HideSafeVolumeDlgFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = HideLockscreenZenModeFeatureId,
            name = "Hide Lockscreen Zen Mode",
            preferenceKey = "system_lockscreen_hidezenmode",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> HideLockscreenZenModeFeature.evaluateEnabled(prefs) },
            factory = { HideLockscreenZenModeFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = DisableKeyguardEditorFeatureId,
            name = "Disable Keyguard Editor",
            preferenceKey = "system_lockscreen_disable_edit",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> DisableKeyguardEditorFeature.evaluateEnabled(prefs) },
            factory = { DisableKeyguardEditorFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NoPasswordFeatureId,
            name = "No Password",
            preferenceKey = "system_nopassword",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NoPasswordFeature.evaluateEnabled(prefs) },
            factory = { NoPasswordFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NotificationImportanceFeatureId,
            name = "Notification Importance",
            preferenceKey = "system_notifimportance",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NotificationImportanceFeature.evaluateEnabled(prefs) },
            factory = { NotificationImportanceFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = NoLightUpOnChargeSystemUiFeatureId,
            name = "No Light Up On Charge System UI",
            preferenceKey = "system_nolightuponcharges",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> NoLightUpOnChargeSystemUiFeature.evaluateEnabled(prefs) },
            factory = { NoLightUpOnChargeSystemUiFeature(lpparam, mPrefs) },
        ),
    )
}

private fun computeStatusBarIconsAdjust(prefs: PrefMap): Pair<Boolean, Boolean> {
    val dualRows = prefs.getBoolean("system_statusbar_dualrows")
    val netspeedAtRow2 = dualRows && prefs.getBoolean("system_statusbar_netspeed_atsecondrow")
    val placement = resolveDeviceInfoPlacement(
        showBatteryDetail = prefs.getBoolean("system_statusbar_batterytempandcurrent"),
        showDeviceTemp = prefs.getBoolean("system_statusbar_showdevicetemperature"),
        dualRows = dualRows,
        batteryAtRightPref = prefs.getBoolean("system_statusbar_batterytempandcurrent_atright"),
        tempAtRightPref = prefs.getBoolean("system_statusbar_showdevicetemperature_atright"),
    )

    val alwaysShowAtRight = prefs.getBoolean("system_statusbar_alarm_atright") ||
        prefs.getBoolean("system_statusbar_nfc_atright") ||
        prefs.getBoolean("system_statusbar_btbattery_atright") ||
        prefs.getBoolean("system_statusbar_headset_atright") ||
        prefs.getBoolean("system_statusbar_vpn_atright") ||
        placement.batteryAtRight || placement.tempAtRight
    val moveLeft = prefs.getBoolean("system_statusbar_alarm_atleft") ||
        prefs.getBoolean("system_statusbar_sound_atleft") ||
        prefs.getBoolean("system_statusbar_netspeed_atleft") ||
        prefs.getBoolean("system_statusbar_dnd_atleft") ||
        prefs.getBoolean("system_statusbar_gps_atleft") ||
        prefs.getBoolean("system_statusbaricons_wifi_mobile_atleft") ||
        placement.batteryAtLeft || placement.tempAtLeft
    val needsAdjust = alwaysShowAtRight || moveLeft || netspeedAtRow2 ||
        prefs.getBoolean("system_statusbaricons_swap_wifi_mobile")
    return needsAdjust to moveLeft
}
