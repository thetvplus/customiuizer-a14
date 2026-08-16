package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object PhoneFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = PhoneShowCallUiFeatureId,
            name = "Phone Show Call Ui",
            preferenceKey = "various_showcallui",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> PhoneShowCallUiFeature.evaluateEnabled(prefs) },
            factory = { PhoneShowCallUiFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = PhoneInCallBrightnessFeatureId,
            name = "Phone In Call Brightness",
            preferenceKey = "various_calluibright",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> PhoneInCallBrightnessFeature.evaluateEnabled(prefs) },
            factory = { PhoneInCallBrightnessFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = PhoneAnswerCallInHeadUpFeatureId,
            name = "Phone Answer Call In Head Up",
            preferenceKey = "various_answerinheadup",
            target = FeatureTarget.SYSTEM_PACKAGE,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> PhoneAnswerCallInHeadUpFeature.evaluateEnabled(prefs) },
            factory = { PhoneAnswerCallInHeadUpFeature(lpparam, mPrefs) },
        ),
    )
}

internal class PhoneShowCallUiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PhoneShowCallUiFeatureId,
    "Phone Show Call Ui",
    "various_showcallui",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getStringAsInt("various_showcallui", 0) > 0
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.ShowCallUIHook(lpparam)
}

internal class PhoneInCallBrightnessFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PhoneInCallBrightnessFeatureId,
    "Phone In Call Brightness",
    "various_calluibright",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_calluibright")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.InCallBrightnessHook(lpparam)
}

internal class PhoneAnswerCallInHeadUpFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PhoneAnswerCallInHeadUpFeatureId,
    "Phone Answer Call In Head Up",
    "various_answerinheadup",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("various_answerinheadup")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.AnswerCallInHeadUpHook(lpparam)
}
