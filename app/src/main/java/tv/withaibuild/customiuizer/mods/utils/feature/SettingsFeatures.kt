package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object SettingsFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = SettingsMiuizerIconFeatureId,
            name = "Settings Miuizer Icon",
            preferenceKey = "miuizer_settingsiconpos",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SettingsMiuizerIconFeature.evaluateEnabled(prefs) },
            factory = { SettingsMiuizerIconFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SettingsDisableAnyNotificationFeatureId,
            name = "Settings Disable Any Notification",
            preferenceKey = "system_disableanynotif",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SettingsDisableAnyNotificationFeature.evaluateEnabled(prefs) },
            factory = { SettingsDisableAnyNotificationFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SettingsNotificationImportanceFeatureId,
            name = "Settings Notification Importance",
            preferenceKey = "system_notifimportance",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SettingsNotificationImportanceFeature.evaluateEnabled(prefs) },
            factory = { SettingsNotificationImportanceFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SettingsViewWifiPasswordFeatureId,
            name = "Settings View Wifi Password",
            preferenceKey = "system_wifipassword",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SettingsViewWifiPasswordFeature.evaluateEnabled(prefs) },
            factory = { SettingsViewWifiPasswordFeature(lpparam, mPrefs) },
        ),
    )
}

internal class SettingsMiuizerIconFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SettingsMiuizerIconFeatureId,
    "Settings Miuizer Icon",
    "miuizer_settingsiconpos",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("miuizer_settingsiconpos", 1) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = GlobalActions.miuizerSettingsHook(lpparam)
}

internal class SettingsDisableAnyNotificationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SettingsDisableAnyNotificationFeatureId,
    "Settings Disable Any Notification",
    "system_disableanynotif",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_disableanynotif")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun install(): FeatureInstallResult {
        SystemNotificationHooks.DisableAnyNotificationHook(lpparam)
        SystemNotificationHooks.DisableAnyNotificationBlockHook(lpparam)
        SystemNotificationHooks.UnlockSettingsNotificationControlsHook(lpparam)
        return FeatureInstallResult.INSTALLED
    }
}

internal class SettingsNotificationImportanceFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SettingsNotificationImportanceFeatureId,
    "Settings Notification Importance",
    "system_notifimportance",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_notifimportance")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemNotificationHooks.NotificationImportanceHook(lpparam)
}

internal class SettingsViewWifiPasswordFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SettingsViewWifiPasswordFeatureId,
    "Settings View Wifi Password",
    "system_wifipassword",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_wifipassword")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = ModsSystem.ViewWifiPasswordHook(lpparam)
}
