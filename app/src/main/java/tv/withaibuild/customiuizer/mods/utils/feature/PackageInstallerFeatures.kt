package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object PackageInstallerFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = PackageInstallerMiuiPackageFeatureId,
            name = "Package Installer Miui Package",
            preferenceKey = "various_miuiinstaller",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> PackageInstallerMiuiPackageFeature.evaluateEnabled(prefs) },
            factory = { PackageInstallerMiuiPackageFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = PackageInstallerAppInfoFeatureId,
            name = "Package Installer App Info",
            preferenceKey = "various_installappinfo",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> PackageInstallerAppInfoFeature.evaluateEnabled(prefs) },
            factory = { PackageInstallerAppInfoFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = PackageInstallerPurifyFeatureId,
            name = "Package Installer Purify",
            preferenceKey = "various_installer_purify",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> PackageInstallerPurifyFeature.evaluateEnabled(prefs) },
            factory = { PackageInstallerPurifyFeature(lpparam, mPrefs) },
        ),
    )
}

internal class PackageInstallerMiuiPackageFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PackageInstallerMiuiPackageFeatureId,
    "Package Installer Miui Package",
    "various_miuiinstaller",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_miuiinstaller")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.MiuiPackageInstallerHook(lpparam)
}

internal class PackageInstallerAppInfoFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PackageInstallerAppInfoFeatureId,
    "Package Installer App Info",
    "various_installappinfo",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_installappinfo")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AppInfoDuringMiuiInstallHook(lpparam)
}

internal class PackageInstallerPurifyFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PackageInstallerPurifyFeatureId,
    "Package Installer Purify",
    "various_installer_purify",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_installer_purify")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.PurePackageInstallerHook(lpparam)
}
