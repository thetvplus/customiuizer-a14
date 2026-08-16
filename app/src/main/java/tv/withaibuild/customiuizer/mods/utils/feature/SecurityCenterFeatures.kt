package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object SecurityCenterFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = SecurityCenterAppInfoFeatureId,
            name = "Security Center App Info",
            preferenceKey = "various_appdetails",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterAppInfoFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterAppInfoFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterAppsDisableFeatureId,
            name = "Security Center Apps Disable",
            preferenceKey = "various_disableapp",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterAppsDisableFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterAppsDisableFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterAppsRestrictFeatureId,
            name = "Security Center Apps Restrict",
            preferenceKey = "various_restrictapp",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterAppsRestrictFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterAppsRestrictFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterHideReportButtonFeatureId,
            name = "Security Center Hide Report Button",
            preferenceKey = "various_hide_report_ondetails",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterHideReportButtonFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterHideReportButtonFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterScrambleAppLockPinFeatureId,
            name = "Security Center Scramble App Lock Pin",
            preferenceKey = "system_applock_scramblepin",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterScrambleAppLockPinFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterScrambleAppLockPinFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterAppsDefaultSortFeatureId,
            name = "Security Center Apps Default Sort",
            preferenceKey = "various_appsort",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterAppsDefaultSortFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterAppsDefaultSortFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterInterceptPermFeatureId,
            name = "Security Center Intercept Perm",
            preferenceKey = "various_skip_interceptperm",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterInterceptPermFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterInterceptPermFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterOpenByDefaultFeatureId,
            name = "Security Center Open By Default",
            preferenceKey = "various_replace_defaultopen_with_openbydefault",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterOpenByDefaultFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterOpenByDefaultFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterSkipSecurityScanFeatureId,
            name = "Security Center Skip Security Scan",
            preferenceKey = "various_skip_securityscan",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterSkipSecurityScanFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterSkipSecurityScanFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterShowTempInBatteryFeatureId,
            name = "Security Center Show Temp In Battery",
            preferenceKey = "various_show_battery_temperature",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterShowTempInBatteryFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterShowTempInBatteryFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterDisableSideBarSuggestionFeatureId,
            name = "Security Center Disable Side Bar Suggestion",
            preferenceKey = "various_disable_freeform_suggest_blacklist",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterDisableSideBarSuggestionFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterDisableSideBarSuggestionFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterDisableDockSuggestFeatureId,
            name = "Security Center Disable Dock Suggest",
            preferenceKey = "various_disable_dock_suggest",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterDisableDockSuggestFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterDisableDockSuggestFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterAddSideBarExpandReceiverFeatureId,
            name = "Security Center Add Side Bar Expand Receiver",
            preferenceKey = "various_enable_expand_sidebar",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterAddSideBarExpandReceiverFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterAddSideBarExpandReceiverFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterNoLowBatteryWarningFeatureId,
            name = "Security Center No Low Battery Warning",
            preferenceKey = "system_hidelowbatwarn",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterNoLowBatteryWarningFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterNoLowBatteryWarningFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterPrivacyAppsLayoutFeatureId,
            name = "Security Center Privacy Apps Layout",
            preferenceKey = "various_privacyapps_column_nums4",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterPrivacyAppsLayoutFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterPrivacyAppsLayoutFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = SecurityCenterPersistPrivacyThumbnailBlurFeatureId,
            name = "Security Center Persist Privacy Thumbnail Blur",
            preferenceKey = "various_disable_reset_recents_privacy_blur",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> SecurityCenterPersistPrivacyThumbnailBlurFeature.evaluateEnabled(prefs) },
            factory = { SecurityCenterPersistPrivacyThumbnailBlurFeature(lpparam, mPrefs) },
        ),
    )
}

internal class SecurityCenterAppInfoFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAppInfoFeatureId,
    "Security Center App Info",
    "various_appdetails",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_appdetails")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AppInfoHook(lpparam)
}

internal class SecurityCenterAppsDisableFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAppsDisableFeatureId,
    "Security Center Apps Disable",
    "various_disableapp",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_disableapp")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AppsDisableHook(lpparam)
}

internal class SecurityCenterAppsRestrictFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAppsRestrictFeatureId,
    "Security Center Apps Restrict",
    "various_restrictapp",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_restrictapp")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AppsRestrictHook(lpparam)
}

internal class SecurityCenterHideReportButtonFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterHideReportButtonFeatureId,
    "Security Center Hide Report Button",
    "various_hide_report_ondetails",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_hide_report_ondetails")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.HideReportButtonHook(lpparam)
}

internal class SecurityCenterScrambleAppLockPinFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterScrambleAppLockPinFeatureId,
    "Security Center Scramble App Lock Pin",
    "system_applock_scramblepin",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_applock_scramblepin")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemLockScreenHooks.ScrambleAppLockPINHook(lpparam)
}

internal class SecurityCenterAppsDefaultSortFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAppsDefaultSortFeatureId,
    "Security Center Apps Default Sort",
    "various_appsort",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("various_appsort", 1) > 1
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AppsDefaultSortHook(lpparam)
}

internal class SecurityCenterInterceptPermFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterInterceptPermFeatureId,
    "Security Center Intercept Perm",
    "various_skip_interceptperm",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_skip_interceptperm")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.InterceptPermHook(lpparam)
}

internal class SecurityCenterOpenByDefaultFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterOpenByDefaultFeatureId,
    "Security Center Open By Default",
    "various_replace_defaultopen_with_openbydefault",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_replace_defaultopen_with_openbydefault")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.OpenByDefaultHook(lpparam)
}

internal class SecurityCenterSkipSecurityScanFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterSkipSecurityScanFeatureId,
    "Security Center Skip Security Scan",
    "various_skip_securityscan",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_skip_securityscan")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.SkipSecurityScanHook(lpparam)
}

internal class SecurityCenterShowTempInBatteryFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterShowTempInBatteryFeatureId,
    "Security Center Show Temp In Battery",
    "various_show_battery_temperature",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_show_battery_temperature")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.ShowTempInBatteryHook(lpparam)
}

internal class SecurityCenterDisableSideBarSuggestionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterDisableSideBarSuggestionFeatureId,
    "Security Center Disable Side Bar Suggestion",
    "various_disable_freeform_suggest_blacklist",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_disable_freeform_suggest_blacklist")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = SystemWindowHooks.DisableSideBarSuggestionHook(lpparam)
}

internal class SecurityCenterDisableDockSuggestFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterDisableDockSuggestFeatureId,
    "Security Center Disable Dock Suggest",
    "various_disable_dock_suggest",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_disable_dock_suggest")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.DisableDockSuggestHook(lpparam)
}

internal class SecurityCenterAddSideBarExpandReceiverFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAddSideBarExpandReceiverFeatureId,
    "Security Center Add Side Bar Expand Receiver",
    "various_enable_expand_sidebar",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = HookDiagnostics.currentProcessName == "com.miui.securitycenter:ui" && prefs.getBoolean("various_enable_expand_sidebar")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AddSideBarExpandReceiverHook(lpparam)
}

internal class SecurityCenterNoLowBatteryWarningFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterNoLowBatteryWarningFeatureId,
    "Security Center No Low Battery Warning",
    "system_hidelowbatwarn",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("system_hidelowbatwarn")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.NoLowBatteryWarningHook()
}

internal class SecurityCenterPrivacyAppsLayoutFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterPrivacyAppsLayoutFeatureId,
    "Security Center Privacy Apps Layout",
    "various_privacyapps_column_nums4",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_privacyapps_column_nums4")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.PrivacyAppsLayoutHook(lpparam)
}

internal class SecurityCenterPersistPrivacyThumbnailBlurFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterPersistPrivacyThumbnailBlurFeatureId,
    "Security Center Persist Privacy Thumbnail Blur",
    "various_disable_reset_recents_privacy_blur",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_disable_reset_recents_privacy_blur")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.PersistPrivacyThumbnailBlur(lpparam)
}
