package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.SystemShareMenuHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object AndroidPackageFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = AndroidCleanShareMenuFeatureId,
            name = "Android Clean Share Menu",
            preferenceKey = "system_cleanshare",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AndroidCleanShareMenuFeature.evaluateEnabled(prefs) },
            factory = { AndroidCleanShareMenuFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AndroidCleanOpenWithMenuFeatureId,
            name = "Android Clean Open With Menu",
            preferenceKey = "system_cleanopenwith",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AndroidCleanOpenWithMenuFeature.evaluateEnabled(prefs) },
            factory = { AndroidCleanOpenWithMenuFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AndroidAllRotationsFeatureId,
            name = "Android All Rotations",
            preferenceKey = "system_allrotations2",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AndroidAllRotationsFeature.evaluateEnabled(prefs) },
            factory = { AndroidAllRotationsFeature(lpparam, mPrefs) },
        ),
    )
}

internal class AndroidCleanShareMenuFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    AndroidCleanShareMenuFeatureId,
    "Android Clean Share Menu",
    "system_cleanshare",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cleanshare")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemShareMenuHooks.CleanShareMenuHook(lpparam)
}

internal class AndroidCleanOpenWithMenuFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    AndroidCleanOpenWithMenuFeatureId,
    "Android Clean Open With Menu",
    "system_cleanopenwith",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_cleanopenwith")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemShareMenuHooks.CleanOpenWithMenuHook(lpparam)
}

internal class AndroidAllRotationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    AndroidAllRotationsFeatureId,
    "Android All Rotations",
    "system_allrotations2",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("system_allrotations2", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun install(): FeatureInstallResult {
        val allRotations = mPrefs.getStringAsInt("system_allrotations2", 1)
        MainModule.resHooks.setThemeValueReplacement("android", "bool", "config_allowAllRotations", allRotations == 2)
        return FeatureInstallResult.INSTALLED
    }
}
