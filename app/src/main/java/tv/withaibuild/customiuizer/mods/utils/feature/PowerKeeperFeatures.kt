package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object PowerKeeperFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = PowerKeeperAppsRestrictFeatureId,
            name = "Power Keeper Apps Restrict",
            preferenceKey = "various_restrictapp",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> PowerKeeperAppsRestrictFeature.evaluateEnabled(prefs) },
            factory = { PowerKeeperAppsRestrictFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = PowerKeeperPersistBatteryOptimizationFeatureId,
            name = "Power Keeper Persist Battery Optimization",
            preferenceKey = "various_persist_batteryoptimization",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> PowerKeeperPersistBatteryOptimizationFeature.evaluateEnabled(prefs) },
            factory = { PowerKeeperPersistBatteryOptimizationFeature(lpparam, mPrefs) },
        ),
    )
}

internal class PowerKeeperAppsRestrictFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PowerKeeperAppsRestrictFeatureId,
    "Power Keeper Apps Restrict",
    "various_restrictapp",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_restrictapp")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AppsRestrictPowerHook(lpparam)
}

internal class PowerKeeperPersistBatteryOptimizationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PowerKeeperPersistBatteryOptimizationFeatureId,
    "Power Keeper Persist Battery Optimization",
    "various_persist_batteryoptimization",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_persist_batteryoptimization")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.PersistBatteryOptimizationHook(lpparam)
}
