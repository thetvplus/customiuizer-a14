package tv.withaibuild.customiuizer.installers

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallMetrics
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.feature.SystemUiFeatures
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Installer for hooks that run in the SystemUI process.
 *
 * This keeps [tv.withaibuild.customiuizer.MainModule] focused on module-level lifecycle
 * and delegates the long list of package-specific SystemUI hooks to a dedicated, stateless class.
 * Base hooks (SystemUIInitializer.init, fast-reboot receiver, status-bar setup and the 10-second
 * restart guard) stay in MainModule so the installer receives an already-validated load point.
 */
object SystemUiInstaller {

    @JvmStatic
    fun install(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        val registry = FeatureInstallRegistry()
        val catalogStartNanos = FeatureInstallMetrics.nowNanos()
        val catalogStartBytes = FeatureInstallMetrics.allocatedBytes()
        val features = SystemUiFeatures.all(lpparam, mPrefs)
        val catalogEndNanos = FeatureInstallMetrics.nowNanos()
        val catalogEndBytes = FeatureInstallMetrics.allocatedBytes()
        val registerStartNanos = FeatureInstallMetrics.nowNanos()
        val registerStartBytes = FeatureInstallMetrics.allocatedBytes()

        for (feature: FeatureSpec in features) {
            registry.register(feature)
        }

        val registerEndNanos = FeatureInstallMetrics.nowNanos()
        val registerEndBytes = FeatureInstallMetrics.allocatedBytes()
        FeatureInstallMetrics.recordCatalog(
            label = "systemui/package-ready",
            specCount = features.size,
            catalogStartNanos = catalogStartNanos,
            catalogEndNanos = catalogEndNanos,
            catalogStartBytes = catalogStartBytes,
            catalogEndBytes = catalogEndBytes,
            registerStartNanos = registerStartNanos,
            registerEndNanos = registerEndNanos,
            registerStartBytes = registerStartBytes,
            registerEndBytes = registerEndBytes,
        )

        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, mPrefs)
    }
}
