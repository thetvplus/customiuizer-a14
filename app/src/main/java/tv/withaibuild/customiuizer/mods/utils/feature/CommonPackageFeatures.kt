package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object CommonPackageFeatures {
    @JvmStatic
    fun hasEnabledFeature(prefs: PrefMap, packageName: String): Boolean =
        StatusBarHeightFeature.evaluateEnabled(prefs) ||
            AlarmCompatFeature.evaluateEnabled(prefs, packageName)

    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = StatusBarHeightFeatureId,
            name = "Status Bar Height",
            preferenceKey = "system_statusbarheight",
            target = FeatureTarget.ANY,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> StatusBarHeightFeature.evaluateEnabled(prefs) },
            factory = { StatusBarHeightFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = AlarmCompatFeatureId,
            name = "Alarm Compat",
            preferenceKey = "various_alarmcompat",
            target = FeatureTarget.ANY,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> AlarmCompatFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
            factory = { AlarmCompatFeature(lpparam, mPrefs) },
        ),
    )
}

internal class StatusBarHeightFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    StatusBarHeightFeatureId,
    "Status Bar Height",
    "system_statusbarheight",
    FeatureTarget.ANY,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getInt("system_statusbarheight", 11) > 11
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.StatusBarHeightHook(lpparam)
}

internal class AlarmCompatFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    AlarmCompatFeatureId,
    "Alarm Compat",
    "various_alarmcompat",
    FeatureTarget.ANY,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean =
            prefs.getBoolean("various_alarmcompat") && prefs.getStringSet("various_alarmcompat_apps").contains(packageName)
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun installHook() = Various.AlarmCompatHook()
}
