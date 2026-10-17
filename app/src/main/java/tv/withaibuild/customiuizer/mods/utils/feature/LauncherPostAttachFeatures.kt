package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.Launcher
import tv.withaibuild.customiuizer.mods.LauncherAnimationHooks
import tv.withaibuild.customiuizer.mods.LauncherFolderHooks
import tv.withaibuild.customiuizer.mods.LauncherGestureHooks
import tv.withaibuild.customiuizer.mods.LauncherIconHooks
import tv.withaibuild.customiuizer.mods.LauncherLayoutHooks
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object LauncherPostAttachFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = LauncherHomescreenSwipesFeatureId,
            name = "Launcher Homescreen Swipes",
            preferenceKey = "launcher_swipedown_action",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHomescreenSwipesFeature.evaluateEnabled(prefs) },
            factory = { LauncherHomescreenSwipesFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHotSeatSwipesFeatureId,
            name = "Launcher Hot Seat Swipes",
            preferenceKey = "launcher_swipeleft_action",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHotSeatSwipesFeature.evaluateEnabled(prefs) },
            factory = { LauncherHotSeatSwipesFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherShakeFeatureId,
            name = "Launcher Shake",
            preferenceKey = "launcher_shake_action",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherShakeFeature.evaluateEnabled(prefs) },
            factory = { LauncherShakeFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherDoubleTapFeatureId,
            name = "Launcher Double Tap",
            preferenceKey = "launcher_doubletap_action",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherDoubleTapFeature.evaluateEnabled(prefs) },
            factory = { LauncherDoubleTapFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherPinchFeatureId,
            name = "Launcher Pinch",
            preferenceKey = "launcher_pinch_action",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherPinchFeature.evaluateEnabled(prefs) },
            factory = { LauncherPinchFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherFolderColumnsFeatureId,
            name = "Launcher Folder Columns",
            preferenceKey = "launcher_folder_cols",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherFolderColumnsFeature.evaluateEnabled(prefs) },
            factory = { LauncherFolderColumnsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherIconScaleFeatureId,
            name = "Launcher Icon Scale",
            preferenceKey = "launcher_iconscale",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherIconScaleFeature.evaluateEnabled(prefs) },
            factory = { LauncherIconScaleFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherTitleFontSizeFeatureId,
            name = "Launcher Title Font Size",
            preferenceKey = "launcher_titlefontsize",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherTitleFontSizeFeature.evaluateEnabled(prefs) },
            factory = { LauncherTitleFontSizeFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherTitleTopMarginFeatureId,
            name = "Launcher Title Top Margin",
            preferenceKey = "launcher_titletopmargin",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherTitleTopMarginFeature.evaluateEnabled(prefs) },
            factory = { LauncherTitleTopMarginFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherNoClockHideFeatureId,
            name = "Launcher No Clock Hide",
            preferenceKey = "launcher_noclockhide",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherNoClockHideFeature.evaluateEnabled(prefs) },
            factory = { LauncherNoClockHideFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherRenameShortcutsFeatureId,
            name = "Launcher Rename Shortcuts",
            preferenceKey = "launcher_renameapps",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherRenameShortcutsFeature.evaluateEnabled(prefs) },
            factory = { LauncherRenameShortcutsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherTitleShadowFeatureId,
            name = "Launcher Title Shadow",
            preferenceKey = "launcher_darkershadow",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherTitleShadowFeature.evaluateEnabled(prefs) },
            factory = { LauncherTitleShadowFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHideNavBarFeatureId,
            name = "Launcher Hide Nav Bar",
            preferenceKey = "controls_nonavbar",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHideNavBarFeature.evaluateEnabled(prefs) },
            factory = { LauncherHideNavBarFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherInfiniteScrollFeatureId,
            name = "Launcher Infinite Scroll",
            preferenceKey = "launcher_infinitescroll",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherInfiniteScrollFeature.evaluateEnabled(prefs) },
            factory = { LauncherInfiniteScrollFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHideTitlesFeatureId,
            name = "Launcher Hide Titles",
            preferenceKey = "launcher_hidetitles",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHideTitlesFeature.evaluateEnabled(prefs) },
            factory = { LauncherHideTitlesFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherFixAppInfoLaunchFeatureId,
            name = "Launcher Fix App Info Launch",
            preferenceKey = "launcher_fixlaunch",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherFixAppInfoLaunchFeature.evaluateEnabled(prefs) },
            factory = { LauncherFixAppInfoLaunchFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherNoWidgetOnlyFeatureId,
            name = "Launcher No Widget Only",
            preferenceKey = "launcher_nowidgetonly",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherNoWidgetOnlyFeature.evaluateEnabled(prefs) },
            factory = { LauncherNoWidgetOnlyFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherReversePortraitFeatureId,
            name = "Launcher Reverse Portrait",
            preferenceKey = "launcher_sensorportrait",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherReversePortraitFeature.evaluateEnabled(prefs) },
            factory = { LauncherReversePortraitFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherMaxHotseatIconsFeatureId,
            name = "Launcher Max Hotseat Icons",
            preferenceKey = "launcher_unlockhotseat",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherMaxHotseatIconsFeature.evaluateEnabled(prefs) },
            factory = { LauncherMaxHotseatIconsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherCloseFolderOnLaunchFeatureId,
            name = "Launcher Close Folder On Launch",
            preferenceKey = "launcher_closefolders",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherCloseFolderOnLaunchFeature.evaluateEnabled(prefs) },
            factory = { LauncherCloseFolderOnLaunchFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherRecentsBlurFeatureId,
            name = "Launcher Recents Blur",
            preferenceKey = "system_recents_blur",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherRecentsBlurFeature.evaluateEnabled(prefs) },
            factory = { LauncherRecentsBlurFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherBackGestureAreaHeightFeatureId,
            name = "Launcher Back Gesture Area Height",
            preferenceKey = "controls_fsg_coverage",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherBackGestureAreaHeightFeature.evaluateEnabled(prefs) },
            factory = { LauncherBackGestureAreaHeightFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherBackGestureAreaWidthFeatureId,
            name = "Launcher Back Gesture Area Width",
            preferenceKey = "controls_fsg_width",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherBackGestureAreaWidthFeature.evaluateEnabled(prefs) },
            factory = { LauncherBackGestureAreaWidthFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherFsgesturesFeatureId,
            name = "Launcher Fsgestures",
            preferenceKey = "controls_fsg_horiz",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherFsgesturesFeature.evaluateEnabled(prefs) },
            factory = { LauncherFsgesturesFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHideMemoryCleanFeatureId,
            name = "Launcher Hide Memory Clean",
            preferenceKey = "system_removecleaner",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHideMemoryCleanFeature.evaluateEnabled(prefs) },
            factory = { LauncherHideMemoryCleanFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherDisableWallpaperScaleFeatureId,
            name = "Launcher Disable Wallpaper Scale",
            preferenceKey = "system_recents_disable_wallpaperscale",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherDisableWallpaperScaleFeature.evaluateEnabled(prefs) },
            factory = { LauncherDisableWallpaperScaleFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHideStatusBarInRecentsFeatureId,
            name = "Launcher Hide Status Bar In Recents",
            preferenceKey = "system_recents_hide_statusbar",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHideStatusBarInRecentsFeature.evaluateEnabled(prefs) },
            factory = { LauncherHideStatusBarInRecentsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherRecentsCardStyleFeatureId,
            name = "Launcher Recents Card Style",
            preferenceKey = "system_recents_card_style",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherRecentsCardStyleFeature.evaluateEnabled(prefs) },
            factory = { LauncherRecentsCardStyleFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherMultiWindowPlusFeatureId,
            name = "Launcher Multi Window Plus",
            preferenceKey = "system_fw_splitscreen",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherMultiWindowPlusFeature.evaluateEnabled(prefs) },
            factory = { LauncherMultiWindowPlusFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherFixAnimFeatureId,
            name = "Launcher Fix Anim",
            preferenceKey = "launcher_fixanim",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherFixAnimFeature.evaluateEnabled(prefs) },
            factory = { LauncherFixAnimFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHideSeekPointsFeatureId,
            name = "Launcher Hide Seek Points",
            preferenceKey = "launcher_hideseekpoints",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHideSeekPointsFeature.evaluateEnabled(prefs) },
            factory = { LauncherHideSeekPointsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherPrivacyFolderFeatureId,
            name = "Launcher Privacy Folder",
            preferenceKey = "launcher_privacyapps_gest",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherPrivacyFolderFeature.evaluateEnabled(prefs) },
            factory = { LauncherPrivacyFolderFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHideFromRecentsFeatureId,
            name = "Launcher Hide From Recents",
            preferenceKey = "system_hidefromrecents",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHideFromRecentsFeature.evaluateEnabled(prefs) },
            factory = { LauncherHideFromRecentsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherFolderBlurFeatureId,
            name = "Launcher Folder Blur",
            preferenceKey = "launcher_folderblur_opacity",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherFolderBlurFeature.evaluateEnabled(prefs) },
            factory = { LauncherFolderBlurFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherNoUnlockAnimationFeatureId,
            name = "Launcher No Unlock Animation",
            preferenceKey = "launcher_nounlockanim",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherNoUnlockAnimationFeature.evaluateEnabled(prefs) },
            factory = { LauncherNoUnlockAnimationFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherNoZoomAnimationFeatureId,
            name = "Launcher No Zoom Animation",
            preferenceKey = "launcher_nozoomanim",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherNoZoomAnimationFeature.evaluateEnabled(prefs) },
            factory = { LauncherNoZoomAnimationFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherUseOldLaunchAnimationFeatureId,
            name = "Launcher Use Old Launch Animation",
            preferenceKey = "launcher_oldlaunchanim",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherUseOldLaunchAnimationFeature.evaluateEnabled(prefs) },
            factory = { LauncherUseOldLaunchAnimationFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherCloseDrawerOnLaunchFeatureId,
            name = "Launcher Close Drawer On Launch",
            preferenceKey = "launcher_closedrawer",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherCloseDrawerOnLaunchFeature.evaluateEnabled(prefs) },
            factory = { LauncherCloseDrawerOnLaunchFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHorizontalWidgetSpacingFeatureId,
            name = "Launcher Horizontal Widget Spacing",
            preferenceKey = "launcher_horizwidgetmargin",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherHorizontalWidgetSpacingFeature.evaluateEnabled(prefs) },
            factory = { LauncherHorizontalWidgetSpacingFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherAssistGestureActionFeatureId,
            name = "Launcher Assist Gesture Action",
            preferenceKey = "controls_fsg_assist_left_action",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherAssistGestureActionFeature.evaluateEnabled(prefs) },
            factory = { LauncherAssistGestureActionFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherSwipeAndStopActionFeatureId,
            name = "Launcher Swipe And Stop Action",
            preferenceKey = "controls_fsg_swipeandstop_action",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherSwipeAndStopActionFeature.evaluateEnabled(prefs) },
            factory = { LauncherSwipeAndStopActionFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherCloseOnLaunchFeatureId,
            name = "Launcher Close On Launch",
            preferenceKey = "launcher_closefolders",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherCloseOnLaunchFeature.evaluateEnabled(prefs) },
            factory = { LauncherCloseOnLaunchFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherResizableWidgetsFeatureId,
            name = "Launcher Resizable Widgets",
            preferenceKey = "system_resizablewidgets",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherResizableWidgetsFeature.evaluateEnabled(prefs) },
            factory = { LauncherResizableWidgetsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherWallpaperColorModeFeatureId,
            name = "Launcher Wallpaper Color Mode",
            preferenceKey = "launcher_wallpaper_colormode",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.APPLICATION_ATTACHED,
            enabled = { prefs -> LauncherWallpaperColorModeFeature.evaluateEnabled(prefs) },
            factory = { LauncherWallpaperColorModeFeature(lpparam, mPrefs) },
        ),
    )
}

internal class LauncherHomescreenSwipesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHomescreenSwipesFeatureId,
    "Launcher Homescreen Swipes",
    "launcher_swipedown_action",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_swipedown_action", 1) != 1 || prefs.getInt("launcher_swipeup_action", 1) != 1 || prefs.getInt("launcher_swipedown2_action", 1) != 1 || prefs.getInt("launcher_swipeup2_action", 1) != 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherGestureHooks.HomescreenSwipesHook(lpparam)
}

internal class LauncherHotSeatSwipesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHotSeatSwipesFeatureId,
    "Launcher Hot Seat Swipes",
    "launcher_swipeleft_action",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_swipeleft_action", 1) != 1 || prefs.getInt("launcher_swiperight_action", 1) != 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherGestureHooks.HotSeatSwipesHook(lpparam)
}

internal class LauncherShakeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherShakeFeatureId,
    "Launcher Shake",
    "launcher_shake_action",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_shake_action", 1) != 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherGestureHooks.ShakeHook(lpparam)
}

internal class LauncherDoubleTapFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherDoubleTapFeatureId,
    "Launcher Double Tap",
    "launcher_doubletap_action",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_doubletap_action", 1) != 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherGestureHooks.LauncherDoubleTapHook(lpparam)
}

internal class LauncherPinchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherPinchFeatureId,
    "Launcher Pinch",
    "launcher_pinch_action",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_pinch_action", 1) != 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherGestureHooks.LauncherPinchHook(lpparam)
}

internal class LauncherFolderColumnsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFolderColumnsFeatureId,
    "Launcher Folder Columns",
    "launcher_folder_cols",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_folder_cols", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherFolderHooks.FolderColumnsHook(lpparam)
}

internal class LauncherIconScaleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherIconScaleFeatureId,
    "Launcher Icon Scale",
    "launcher_iconscale",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_iconscale", 45) > 45
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherIconHooks.IconScaleHook(lpparam)
}

internal class LauncherTitleFontSizeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherTitleFontSizeFeatureId,
    "Launcher Title Font Size",
    "launcher_titlefontsize",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_titlefontsize", 5) > 5
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherIconHooks.TitleFontSizeHook(lpparam)
}

internal class LauncherTitleTopMarginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherTitleTopMarginFeatureId,
    "Launcher Title Top Margin",
    "launcher_titletopmargin",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_titletopmargin", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherIconHooks.TitleTopMarginHook(lpparam)
}

internal class LauncherNoClockHideFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherNoClockHideFeatureId,
    "Launcher No Clock Hide",
    "launcher_noclockhide",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_noclockhide")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherIconHooks.NoClockHideHook(lpparam)
}

internal class LauncherRenameShortcutsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherRenameShortcutsFeatureId,
    "Launcher Rename Shortcuts",
    "launcher_renameapps",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_renameapps")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherIconHooks.RenameShortcutsHook(lpparam)
}

internal class LauncherTitleShadowFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherTitleShadowFeatureId,
    "Launcher Title Shadow",
    "launcher_darkershadow",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_darkershadow")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherIconHooks.TitleShadowHook(lpparam)
}

internal class LauncherHideNavBarFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideNavBarFeatureId,
    "Launcher Hide Nav Bar",
    "controls_nonavbar",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_nonavbar")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.HideNavBarHook(lpparam)
}

internal class LauncherInfiniteScrollFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherInfiniteScrollFeatureId,
    "Launcher Infinite Scroll",
    "launcher_infinitescroll",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_infinitescroll")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.InfiniteScrollHook(lpparam)
}

internal class LauncherHideTitlesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideTitlesFeatureId,
    "Launcher Hide Titles",
    "launcher_hidetitles",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_hidetitles")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherIconHooks.HideTitlesHook(lpparam)
}

internal class LauncherFixAppInfoLaunchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFixAppInfoLaunchFeatureId,
    "Launcher Fix App Info Launch",
    "launcher_fixlaunch",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_fixlaunch")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.FixAppInfoLaunchHook(lpparam)
}

internal class LauncherNoWidgetOnlyFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherNoWidgetOnlyFeatureId,
    "Launcher No Widget Only",
    "launcher_nowidgetonly",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_nowidgetonly")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.NoWidgetOnlyHook(lpparam)
}

internal class LauncherReversePortraitFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherReversePortraitFeatureId,
    "Launcher Reverse Portrait",
    "launcher_sensorportrait",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_sensorportrait")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.ReverseLauncherPortraitHook(lpparam)
}

internal class LauncherMaxHotseatIconsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherMaxHotseatIconsFeatureId,
    "Launcher Max Hotseat Icons",
    "launcher_unlockhotseat",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_unlockhotseat")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.MaxHotseatIconsCountHook(lpparam)
}

internal class LauncherCloseFolderOnLaunchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherCloseFolderOnLaunchFeatureId,
    "Launcher Close Folder On Launch",
    "launcher_closefolders",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("launcher_closefolders", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherFolderHooks.CloseFolderOnLaunchHook(lpparam)
}

internal class LauncherRecentsBlurFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherRecentsBlurFeatureId,
    "Launcher Recents Blur",
    "system_recents_blur",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_recents_blur", 100) < 100
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.RecentsBlurRatioHook(lpparam)
}

internal class LauncherBackGestureAreaHeightFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherBackGestureAreaHeightFeatureId,
    "Launcher Back Gesture Area Height",
    "controls_fsg_coverage",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("controls_fsg_coverage", 60) != 60
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.BackGestureAreaHeightHook(lpparam)
}

internal class LauncherBackGestureAreaWidthFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherBackGestureAreaWidthFeatureId,
    "Launcher Back Gesture Area Width",
    "controls_fsg_width",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("controls_fsg_width", 100) > 100
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.BackGestureAreaWidthHook(lpparam)
}

internal class LauncherFsgesturesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFsgesturesFeatureId,
    "Launcher Fsgestures",
    "controls_fsg_horiz",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_fsg_horiz")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherGestureHooks.FSGesturesHook(lpparam)
}

internal class LauncherHideMemoryCleanFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideMemoryCleanFeatureId,
    "Launcher Hide Memory Clean",
    "system_removecleaner",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_removecleaner")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.HideMemoryCleanHook(lpparam, true)
}

internal class LauncherDisableWallpaperScaleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherDisableWallpaperScaleFeatureId,
    "Launcher Disable Wallpaper Scale",
    "system_recents_disable_wallpaperscale",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_recents_disable_wallpaperscale") || prefs.getBoolean("launcher_disable_wallpaperscale")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherAnimationHooks.DisableLauncherWallpaperScale(lpparam)
}

internal class LauncherHideStatusBarInRecentsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideStatusBarInRecentsFeatureId,
    "Launcher Hide Status Bar In Recents",
    "system_recents_hide_statusbar",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_recents_hide_statusbar")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.HideStatusBarInRecentsHook(lpparam)
}

internal class LauncherRecentsCardStyleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherRecentsCardStyleFeatureId,
    "Launcher Recents Hide App Name",
    "system_recents_card_style",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean =
            Launcher.isRecentsHideAppNameEnabled(prefs)
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.RecentsHideAppNameHook(lpparam)
}

internal class LauncherMultiWindowPlusFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherMultiWindowPlusFeatureId,
    "Launcher Multi Window Plus",
    "system_fw_splitscreen",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_fw_splitscreen")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.MultiWindowPlusHook(lpparam)
}

internal class LauncherFixAnimFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFixAnimFeatureId,
    "Launcher Fix Anim",
    "launcher_fixanim",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_fixanim")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherAnimationHooks.FixAnimHook(lpparam)
}

internal class LauncherHideSeekPointsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideSeekPointsFeatureId,
    "Launcher Hide Seek Points",
    "launcher_hideseekpoints",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_hideseekpoints")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.HideSeekPointsHook(lpparam)
}

internal class LauncherPrivacyFolderFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherPrivacyFolderFeatureId,
    "Launcher Privacy Folder",
    "launcher_privacyapps_gest",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_privacyapps_gest") || prefs.getInt("launcher_spread_action", 1) != 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherFolderHooks.PrivacyFolderHook(lpparam)
}

internal class LauncherHideFromRecentsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideFromRecentsFeatureId,
    "Launcher Hide From Recents",
    "system_hidefromrecents",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_hidefromrecents")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.HideFromRecentsHook(lpparam)
}

internal class LauncherFolderBlurFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFolderBlurFeatureId,
    "Launcher Folder Blur",
    "launcher_folderblur_opacity",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean =
            prefs.getBoolean("launcher_folderblur_disable") ||
                prefs.getInt("launcher_folderblur_opacity", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherFolderHooks.FolderBlurHook(lpparam)
}

internal class LauncherNoUnlockAnimationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherNoUnlockAnimationFeatureId,
    "Launcher No Unlock Animation",
    "launcher_nounlockanim",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_nounlockanim")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherAnimationHooks.NoUnlockAnimationHook(lpparam)
}

internal class LauncherNoZoomAnimationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherNoZoomAnimationFeatureId,
    "Launcher No Zoom Animation",
    "launcher_nozoomanim",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_nozoomanim")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherAnimationHooks.NoZoomAnimationHook(lpparam)
}

internal class LauncherUseOldLaunchAnimationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherUseOldLaunchAnimationFeatureId,
    "Launcher Use Old Launch Animation",
    "launcher_oldlaunchanim",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_oldlaunchanim")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherAnimationHooks.UseOldLaunchAnimationHook(lpparam)
}

internal class LauncherCloseDrawerOnLaunchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherCloseDrawerOnLaunchFeatureId,
    "Launcher Close Drawer On Launch",
    "launcher_closedrawer",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_closedrawer")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherFolderHooks.CloseDrawerOnLaunchHook(lpparam)
}

internal class LauncherHorizontalWidgetSpacingFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHorizontalWidgetSpacingFeatureId,
    "Launcher Horizontal Widget Spacing",
    "launcher_horizwidgetmargin",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_horizwidgetmargin", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.HorizontalWidgetSpacingHook(lpparam)
}

internal class LauncherAssistGestureActionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherAssistGestureActionFeatureId,
    "Launcher Assist Gesture Action",
    "controls_fsg_assist_left_action",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("controls_fsg_assist_left_action", 1) > 1 || prefs.getInt("controls_fsg_assist_right_action", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherGestureHooks.AssistGestureActionHook(lpparam)
}

internal class LauncherSwipeAndStopActionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherSwipeAndStopActionFeatureId,
    "Launcher Swipe And Stop Action",
    "controls_fsg_swipeandstop_action",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("controls_fsg_swipeandstop_action", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherGestureHooks.SwipeAndStopActionHook(lpparam)
}

internal class LauncherCloseOnLaunchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherCloseOnLaunchFeatureId,
    "Launcher Close On Launch",
    "launcher_closefolders",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("launcher_closefolders", 1) > 1 || prefs.getBoolean("launcher_closedrawer")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherFolderHooks.CloseFolderOrDrawerOnLaunchShortcutMenuHook(lpparam)
}

internal class LauncherResizableWidgetsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherResizableWidgetsFeatureId,
    "Launcher Resizable Widgets",
    "system_resizablewidgets",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_resizablewidgets")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.ResizableWidgetsHook(lpparam)
}

internal class LauncherWallpaperColorModeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherWallpaperColorModeFeatureId,
    "Launcher Wallpaper Color Mode",
    "launcher_wallpaper_colormode",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("launcher_wallpaper_colormode", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherAnimationHooks.WallpaperColorModeHook(lpparam)
}
