package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object GuardProviderFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = GuardProviderDisableDefraudAppsFeatureId,
            name = "Guard Provider Disable Defraud Apps",
            preferenceKey = "various_disable_defraud_apps_detect",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> GuardProviderDisableDefraudAppsFeature.evaluateEnabled(prefs) },
            factory = { GuardProviderDisableDefraudAppsFeature(lpparam, mPrefs) },
        ),
    )
}

internal class GuardProviderDisableDefraudAppsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    GuardProviderDisableDefraudAppsFeatureId,
    "Guard Provider Disable Defraud Apps",
    "various_disable_defraud_apps_detect",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_disable_defraud_apps_detect")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun install(): FeatureInstallResult {
        try {
            MainModule.loadDexKit()
            XposedHelpers.createBridge(lpparam.applicationInfo.sourceDir)
            Various.DisableDefraudAppsCheck(lpparam)
            return FeatureInstallResult.INSTALLED
        } finally {
            XposedHelpers.closeBridge()
        }
    }
}
