package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Launcher
import tv.withaibuild.customiuizer.mods.LauncherFolderHooks
import tv.withaibuild.customiuizer.mods.LauncherIconHooks
import tv.withaibuild.customiuizer.mods.LauncherLayoutHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object LauncherPackageReadyFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = LauncherFolderColumnsResFeatureId,
            name = "Launcher Folder Columns Res",
            preferenceKey = "launcher_folder_cols",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherFolderColumnsResFeature.evaluateEnabled(prefs) },
            factory = { LauncherFolderColumnsResFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherHorizontalSpacingFeatureId,
            name = "Launcher Horizontal Spacing",
            preferenceKey = "launcher_horizmargin",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherHorizontalSpacingFeature.evaluateEnabled(prefs) },
            factory = { LauncherHorizontalSpacingFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherIndicatorHeightFeatureId,
            name = "Launcher Indicator Height",
            preferenceKey = "launcher_indicatorheight",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherIndicatorHeightFeature.evaluateEnabled(prefs) },
            factory = { LauncherIndicatorHeightFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherIndicatorMarginTopFeatureId,
            name = "Launcher Indicator Margin Top",
            preferenceKey = "launcher_indicator_topmargin",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherIndicatorMarginTopFeature.evaluateEnabled(prefs) },
            factory = { LauncherIndicatorMarginTopFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherUnlockGridsFeatureId,
            name = "Launcher Unlock Grids",
            preferenceKey = "launcher_unlockgrids",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherUnlockGridsFeature.evaluateEnabled(prefs) },
            factory = { LauncherUnlockGridsFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherDockTitlesFeatureId,
            name = "Launcher Dock Titles",
            preferenceKey = "launcher_docktitles",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherDockTitlesFeature.evaluateEnabled(prefs) },
            factory = { LauncherDockTitlesFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherDisableLogFeatureId,
            name = "Launcher Disable Log",
            preferenceKey = "launcher_disable_log",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherDisableLogFeature.evaluateEnabled(prefs) },
            factory = { LauncherDisableLogFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherWorkspaceCellPaddingTopFeatureId,
            name = "Launcher Workspace Cell Padding Top",
            preferenceKey = "launcher_topmargin",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherWorkspaceCellPaddingTopFeature.evaluateEnabled(prefs) },
            factory = { LauncherWorkspaceCellPaddingTopFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherDockMarginTopFeatureId,
            name = "Launcher Dock Margin Top",
            preferenceKey = "launcher_dock_topmargin",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherDockMarginTopFeature.evaluateEnabled(prefs) },
            factory = { LauncherDockMarginTopFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherDockMarginBottomFeatureId,
            name = "Launcher Dock Margin Bottom",
            preferenceKey = "launcher_dock_bottommargin",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherDockMarginBottomFeature.evaluateEnabled(prefs) },
            factory = { LauncherDockMarginBottomFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherDockHeightFeatureId,
            name = "Launcher Dock Height",
            preferenceKey = "launcher_dock_height",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherDockHeightFeature.evaluateEnabled(prefs) },
            factory = { LauncherDockHeightFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = LauncherPrivacyAppsGestFeatureId,
            name = "Launcher Privacy Apps Gest",
            preferenceKey = "launcher_privacyapps_gest",
            target = FeatureTarget.LAUNCHER,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> LauncherPrivacyAppsGestFeature.evaluateEnabled(prefs) },
            factory = { LauncherPrivacyAppsGestFeature(lpparam, mPrefs) },
        ),
    )
}

internal class LauncherFolderColumnsResFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherFolderColumnsResFeatureId,
    "Launcher Folder Columns Res",
    "launcher_folder_cols",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_folder_cols", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherFolderHooks.FolderColumnsRes(mPrefs.getInt("launcher_folder_cols", 1))
}

internal class LauncherHorizontalSpacingFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherHorizontalSpacingFeatureId,
    "Launcher Horizontal Spacing",
    "launcher_horizmargin",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_horizmargin", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.HorizontalSpacingRes()
}

internal class LauncherIndicatorHeightFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherIndicatorHeightFeatureId,
    "Launcher Indicator Height",
    "launcher_indicatorheight",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_indicatorheight", 9) > 9
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.IndicatorHeightRes()
}

internal class LauncherIndicatorMarginTopFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherIndicatorMarginTopFeatureId,
    "Launcher Indicator Margin Top",
    "launcher_indicator_topmargin",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_indicator_topmargin", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.IndicatorMarginTopHook(lpparam)
}

internal class LauncherUnlockGridsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherUnlockGridsFeatureId,
    "Launcher Unlock Grids",
    "launcher_unlockgrids",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_unlockgrids")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun install(): FeatureInstallResult {
        LauncherLayoutHooks.UnlockGridsRes()
        LauncherLayoutHooks.UnlockGridsHook(lpparam)
        return FeatureInstallResult.INSTALLED
    }
}

internal class LauncherDockTitlesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDockTitlesFeatureId,
    "Launcher Dock Titles",
    "launcher_docktitles",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_docktitles")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherIconHooks.ShowHotseatTitlesHook(lpparam)
}

internal class LauncherDisableLogFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDisableLogFeatureId,
    "Launcher Disable Log",
    "launcher_disable_log",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_disable_log")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.DisableLauncherLogHook(lpparam)
}

internal class LauncherWorkspaceCellPaddingTopFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherWorkspaceCellPaddingTopFeatureId,
    "Launcher Workspace Cell Padding Top",
    "launcher_topmargin",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_topmargin", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.WorkspaceCellPaddingTopHook(lpparam)
}

internal class LauncherDockMarginTopFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDockMarginTopFeatureId,
    "Launcher Dock Margin Top",
    "launcher_dock_topmargin",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_dock_topmargin", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.DockMarginTopHook(lpparam)
}

internal class LauncherDockMarginBottomFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDockMarginBottomFeatureId,
    "Launcher Dock Margin Bottom",
    "launcher_dock_bottommargin",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_dock_bottommargin", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.DockMarginBottomHook(lpparam)
}

internal class LauncherDockHeightFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDockHeightFeatureId,
    "Launcher Dock Height",
    "launcher_dock_height",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("launcher_dock_height", 60) > 60
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = LauncherLayoutHooks.DockHeightHook(lpparam)
}

internal class LauncherPrivacyAppsGestFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherPrivacyAppsGestFeatureId,
    "Launcher Privacy Apps Gest",
    "launcher_privacyapps_gest",
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("launcher_privacyapps_gest")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Launcher.setupLauncher(lpparam)
}
