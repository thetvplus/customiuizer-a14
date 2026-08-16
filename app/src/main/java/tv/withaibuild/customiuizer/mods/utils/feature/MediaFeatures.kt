package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.LauncherAnimationHooks
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object MediaFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = MediaDisableUnlockWallpaperScaleFeatureId,
            name = "Media Disable Unlock Wallpaper Scale",
            preferenceKey = "launcher_disable_wallpaperscale",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MediaDisableUnlockWallpaperScaleFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
            factory = { MediaDisableUnlockWallpaperScaleFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = MediaScreenshotConfigFeatureId,
            name = "Media Screenshot Config",
            preferenceKey = "system_screenshot",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MediaScreenshotConfigFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
            factory = { MediaScreenshotConfigFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = MediaGalleryScreenshotPathFeatureId,
            name = "Media Gallery Screenshot Path",
            preferenceKey = "system_gallery_screenshots_path",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> MediaGalleryScreenshotPathFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
            factory = { MediaGalleryScreenshotPathFeature(lpparam, mPrefs) },
        ),
    )
}

internal class MediaDisableUnlockWallpaperScaleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    MediaDisableUnlockWallpaperScaleFeatureId,
    "Media Disable Unlock Wallpaper Scale",
    "launcher_disable_wallpaperscale",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean = packageName == "com.miui.miwallpaper" && prefs.getBoolean("launcher_disable_wallpaperscale")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun installHook() = LauncherAnimationHooks.DisableUnlockWallpaperScale(lpparam)
}

internal class MediaScreenshotConfigFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    MediaScreenshotConfigFeatureId,
    "Media Screenshot Config",
    "system_screenshot",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean = packageName == "com.miui.screenshot" && prefs.getBoolean("system_screenshot")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun install(): FeatureInstallResult {
        try {
            MainModule.loadDexKit()
            XposedHelpers.createBridge(lpparam.applicationInfo.sourceDir)
            ModsSystem.ScreenshotConfigHook(lpparam)
            return FeatureInstallResult.INSTALLED
        } finally {
            XposedHelpers.closeBridge()
        }
    }
}

internal class MediaGalleryScreenshotPathFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    MediaGalleryScreenshotPathFeatureId,
    "Media Gallery Screenshot Path",
    "system_gallery_screenshots_path",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean = packageName == "com.miui.gallery" && prefs.getStringAsInt("system_gallery_screenshots_path", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun installHook() = ModsSystem.GalleryScreenshotPathHook(lpparam)
}
