package tv.withaibuild.customiuizer.installers

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallMetrics
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherPackageReadyFeatures
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherPostAttachFeatures
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Installer for hooks that run in the Launcher process.
 *
 * This keeps [tv.withaibuild.customiuizer.MainModule] focused on module-level lifecycle
 * and delegates the package-specific hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
object LauncherInstaller {

    @JvmStatic
    fun install(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        val registry = FeatureInstallRegistry()
        val catalogStartNanos = FeatureInstallMetrics.nowNanos()
        val catalogStartBytes = FeatureInstallMetrics.allocatedBytes()
        val features = LauncherPackageReadyFeatures.all(lpparam, mPrefs)
        val catalogEndNanos = FeatureInstallMetrics.nowNanos()
        val catalogEndBytes = FeatureInstallMetrics.allocatedBytes()
        val registerStartNanos = FeatureInstallMetrics.nowNanos()
        val registerStartBytes = FeatureInstallMetrics.allocatedBytes()

        for (feature: FeatureSpec in features) {
            registry.register(feature)
        }

        val registerEndNanos = FeatureInstallMetrics.nowNanos()
        val registerEndBytes = FeatureInstallMetrics.allocatedBytes()
        recordCatalogMetrics(
            label = "launcher/package-ready",
            features = features,
            catalogStartNanos = catalogStartNanos,
            catalogEndNanos = catalogEndNanos,
            catalogStartBytes = catalogStartBytes,
            catalogEndBytes = catalogEndBytes,
            registerStartNanos = registerStartNanos,
            registerEndNanos = registerEndNanos,
            registerStartBytes = registerStartBytes,
            registerEndBytes = registerEndBytes,
        )

        registry.installAll(FeatureTarget.LAUNCHER, InstallPhase.PACKAGE_READY, mPrefs)
    }

    @JvmStatic
    fun handleLoadLauncher(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        val registry = FeatureInstallRegistry()
        val catalogStartNanos = FeatureInstallMetrics.nowNanos()
        val catalogStartBytes = FeatureInstallMetrics.allocatedBytes()
        val features = LauncherPostAttachFeatures.all(lpparam, mPrefs)
        val catalogEndNanos = FeatureInstallMetrics.nowNanos()
        val catalogEndBytes = FeatureInstallMetrics.allocatedBytes()
        val registerStartNanos = FeatureInstallMetrics.nowNanos()
        val registerStartBytes = FeatureInstallMetrics.allocatedBytes()

        for (feature: FeatureSpec in features) {
            registry.register(feature)
        }

        val registerEndNanos = FeatureInstallMetrics.nowNanos()
        val registerEndBytes = FeatureInstallMetrics.allocatedBytes()
        recordCatalogMetrics(
            label = "launcher/post-attach",
            features = features,
            catalogStartNanos = catalogStartNanos,
            catalogEndNanos = catalogEndNanos,
            catalogStartBytes = catalogStartBytes,
            catalogEndBytes = catalogEndBytes,
            registerStartNanos = registerStartNanos,
            registerEndNanos = registerEndNanos,
            registerStartBytes = registerStartBytes,
            registerEndBytes = registerEndBytes,
        )

        registry.installAll(FeatureTarget.LAUNCHER, InstallPhase.APPLICATION_ATTACHED, mPrefs)
    }

    private fun recordCatalogMetrics(
        label: String,
        features: List<FeatureSpec>,
        catalogStartNanos: Long,
        catalogEndNanos: Long,
        catalogStartBytes: Long,
        catalogEndBytes: Long,
        registerStartNanos: Long,
        registerEndNanos: Long,
        registerStartBytes: Long,
        registerEndBytes: Long,
    ) {
        FeatureInstallMetrics.recordCatalog(
            label = label,
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
    }
}
