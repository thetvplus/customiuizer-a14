package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.installers.LauncherInstaller
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.SystemStatusBarBackgroundHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object GenericAppFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> =
        selected(lpparam, mPrefs, true, true, true, true)

    @JvmStatic
    fun selected(
        lpparam: PackageReadyParam,
        mPrefs: PrefMap,
        includeLauncher: Boolean,
        includeStatusBarBackground: Boolean,
        includeNoOverscroll: Boolean,
        includeVolumeMediaPlayer: Boolean,
    ): List<FeatureSpec> {
        val count = (if (includeLauncher) 1 else 0) +
            (if (includeStatusBarBackground) 1 else 0) +
            (if (includeNoOverscroll) 1 else 0) +
            (if (includeVolumeMediaPlayer) 1 else 0)
        if (count == 0) return emptyList()

        val features = ArrayList<FeatureSpec>(count)
        if (includeLauncher) {
            features.add(
                LazyFeatureSpec(
                    id = LauncherPostAttachFeatureId,
                    name = "Launcher Post Attach",
                    preferenceKey = null,
                    target = FeatureTarget.LAUNCHER,
                    phase = InstallPhase.APPLICATION_ATTACHED,
                    enabled = { prefs -> LauncherPostAttachFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
                    factory = { LauncherPostAttachFeature(lpparam, mPrefs) },
                )
            )
        }
        if (includeStatusBarBackground) {
            features.add(
                LazyFeatureSpec(
                    id = GenericAppStatusBarBackgroundFeatureId,
                    name = "Generic App Status Bar Background",
                    preferenceKey = "system_statusbarcolor",
                    target = FeatureTarget.ANY,
                    phase = InstallPhase.APPLICATION_ATTACHED,
                    enabled = { prefs -> GenericAppStatusBarBackgroundFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
                    factory = { GenericAppStatusBarBackgroundFeature(lpparam, mPrefs) },
                )
            )
        }
        if (includeNoOverscroll) {
            features.add(
                LazyFeatureSpec(
                    id = GenericAppNoOverscrollFeatureId,
                    name = "Generic App No Overscroll",
                    preferenceKey = "system_nooverscroll",
                    target = FeatureTarget.ANY,
                    phase = InstallPhase.APPLICATION_ATTACHED,
                    enabled = { prefs -> GenericAppNoOverscrollFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
                    factory = { GenericAppNoOverscrollFeature(lpparam, mPrefs) },
                )
            )
        }
        if (includeVolumeMediaPlayer) {
            features.add(
                LazyFeatureSpec(
                    id = GenericAppVolumeMediaPlayerFeatureId,
                    name = "Generic App Volume Media Player",
                    preferenceKey = "controls_volumemedia_up",
                    target = FeatureTarget.ANY,
                    phase = InstallPhase.APPLICATION_ATTACHED,
                    enabled = { prefs -> GenericAppVolumeMediaPlayerFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
                    factory = { GenericAppVolumeMediaPlayerFeature(lpparam, mPrefs) },
                )
            )
        }
        return features
    }
}

internal class LauncherPostAttachFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherPostAttachFeatureId,
    "Launcher Post Attach",
    null,
    FeatureTarget.LAUNCHER,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean = packageName == "com.miui.home"
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun installHook() = LauncherInstaller.handleLoadLauncher(lpparam, mPrefs)
}

internal class GenericAppStatusBarBackgroundFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    GenericAppStatusBarBackgroundFeatureId,
    "Generic App Status Bar Background",
    "system_statusbarcolor",
    FeatureTarget.ANY,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean = prefs.getBoolean("system_statusbarcolor") && prefs.getStringSet("system_statusbarcolor_apps").contains(packageName)
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun install(): FeatureInstallResult {
        SystemStatusBarBackgroundHooks.StatusBarBackgroundCompatHook(lpparam)
        SystemStatusBarBackgroundHooks.StatusBarBackgroundHook(lpparam)
        return FeatureInstallResult.INSTALLED
    }
}

internal class GenericAppNoOverscrollFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    GenericAppNoOverscrollFeatureId,
    "Generic App No Overscroll",
    "system_nooverscroll",
    FeatureTarget.ANY,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean = prefs.getBoolean("system_nooverscroll") && prefs.getStringSet("system_nooverscroll_apps").contains(packageName)
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun installHook() = SystemWindowHooks.NoOverscrollAppHook(lpparam)
}

internal class GenericAppVolumeMediaPlayerFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    GenericAppVolumeMediaPlayerFeatureId,
    "Generic App Volume Media Player",
    "controls_volumemedia_up",
    FeatureTarget.ANY,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean = (prefs.getStringAsInt("controls_volumemedia_up", 0) > 0 || prefs.getStringAsInt("controls_volumemedia_down", 0) > 0) && prefs.getStringSet("controls_mediaplayer_apps").contains(packageName)
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun installHook() = Controls.VolumeMediaPlayerHook(lpparam)
}
