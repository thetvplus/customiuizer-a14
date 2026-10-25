package tv.withaibuild.customiuizer.installers

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.feature.SecurityCenterFeatures
import tv.withaibuild.customiuizer.mods.utils.feature.SecurityCenterNoLowBatteryWarningFeature
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Installer for hooks that run in the SecurityCenter process.
 *
 * This keeps [tv.withaibuild.customiuizer.MainModule] focused on module-level lifecycle
 * and delegates the package-specific hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
object SecurityCenterInstaller {

    /** HyperOS owns its power warning in this exact auxiliary process. It receives only
     * the existing warning policy, never the main-process UI or generic-app features. */
    @JvmStatic
    fun installPowerWarning(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        val registry = FeatureInstallRegistry()
        registry.register(SecurityCenterNoLowBatteryWarningFeature(lpparam, mPrefs))
        registry.installAll(FeatureTarget.SYSTEM_PACKAGE, InstallPhase.PACKAGE_READY, mPrefs)
    }

    @JvmStatic
    fun install(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        val registry = FeatureInstallRegistry()

        for (feature: FeatureSpec in SecurityCenterFeatures.all(lpparam, mPrefs)) {
            registry.register(feature)
        }

        registry.installAll(FeatureTarget.SYSTEM_PACKAGE, InstallPhase.PACKAGE_READY, mPrefs)
    }
}
