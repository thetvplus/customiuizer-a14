package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.LazyFeatureSpec
import tv.withaibuild.customiuizer.utils.PrefMap

object InputMethodFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureSpec> = listOf(
        LazyFeatureSpec(
            id = InputMethodVolumeCursorFeatureId,
            name = "Input Method Volume Cursor",
            preferenceKey = "controls_volumecursor",
            target = FeatureTarget.ANY,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> InputMethodVolumeCursorFeature.evaluateEnabled(prefs) },
            factory = { InputMethodVolumeCursorFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = InputMethodFixBottomMarginFeatureId,
            name = "Input Method Fix Bottom Margin",
            preferenceKey = "controls_nonavbar_fix_inputmethod",
            target = FeatureTarget.ANY,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> InputMethodFixBottomMarginFeature.evaluateEnabled(prefs) },
            factory = { InputMethodFixBottomMarginFeature(lpparam, mPrefs) },
        ),
        LazyFeatureSpec(
            id = InputMethodGboardPaddingFeatureId,
            name = "Input Method Gboard Padding",
            preferenceKey = "various_gboardpadding_port",
            target = FeatureTarget.ANY,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { prefs -> InputMethodGboardPaddingFeature.evaluateEnabled(prefs, lpparam.packageName.orEmpty()) },
            factory = { InputMethodGboardPaddingFeature(lpparam, mPrefs) },
        ),
    )
}

internal class InputMethodVolumeCursorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    InputMethodVolumeCursorFeatureId,
    "Input Method Volume Cursor",
    "controls_volumecursor",
    FeatureTarget.ANY,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_volumecursor")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Controls.VolumeCursorHook(lpparam)
}

internal class InputMethodFixBottomMarginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    InputMethodFixBottomMarginFeatureId,
    "Input Method Fix Bottom Margin",
    "controls_nonavbar_fix_inputmethod",
    FeatureTarget.ANY,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap): Boolean = prefs.getBoolean("controls_nonavbar_fix_inputmethod") && prefs.getBoolean("controls_nonavbar")
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs)
    override fun installHook() = Various.FixInputMethodBottomMarginHook(lpparam)
}

internal class InputMethodGboardPaddingFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    InputMethodGboardPaddingFeatureId,
    "Input Method Gboard Padding",
    "various_gboardpadding_port",
    FeatureTarget.ANY,
) {
    companion object {
        @JvmStatic
        fun evaluateEnabled(prefs: PrefMap, packageName: String): Boolean = packageName.startsWith("com.google.android.inputmethod") && (prefs.getInt("various_gboardpadding_port", 0) > 0 || prefs.getInt("various_gboardpadding_land", 0) > 0)
    }

    override fun isEnabledCondition(prefs: PrefMap) = Companion.evaluateEnabled(prefs, packageName)
    override fun installHook() = Various.GboardPaddingHook(lpparam)
}
