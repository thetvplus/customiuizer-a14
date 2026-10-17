package tv.withaibuild.customiuizer.installers

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.feature.InputMethodFeatures
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Installer for hooks that run in the InputMethod process.
 *
 * This keeps [tv.withaibuild.customiuizer.MainModule] focused on module-level lifecycle
 * and delegates the package-specific hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
object InputMethodInstaller {

    @JvmStatic
    fun install(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        val registry = FeatureInstallRegistry()

        for (feature: FeatureSpec in InputMethodFeatures.all(lpparam, mPrefs)) {
            registry.register(feature)
        }

        registry.installAll(FeatureTarget.ANY, InstallPhase.PACKAGE_READY, mPrefs)
    }
}
