package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureId
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

internal abstract class BasePackageReadyFeature(
    protected val lpparam: PackageReadyParam,
    protected val mPrefs: PrefMap,
    override val id: FeatureId,
    override val name: String,
    override val preferenceKey: String?,
    override val target: FeatureTarget
) : FeatureDefinition {

    override val phase = InstallPhase.PACKAGE_READY
    protected val packageName: String by lazy { lpparam.packageName.orEmpty() }

    protected abstract fun isEnabledCondition(prefs: PrefMap): Boolean
    protected open fun installHook() {}

    final override fun isEnabled(prefs: PrefMap) = isEnabledCondition(prefs)

    open override fun install(): FeatureInstallResult {
        installHook()
        return FeatureInstallResult.INSTALLED
    }
}

internal abstract class BaseApplicationAttachedFeature(
    protected val lpparam: PackageReadyParam,
    protected val mPrefs: PrefMap,
    override val id: FeatureId,
    override val name: String,
    override val preferenceKey: String?,
    override val target: FeatureTarget
) : FeatureDefinition {

    override val phase = InstallPhase.APPLICATION_ATTACHED
    protected val packageName: String by lazy { lpparam.packageName.orEmpty() }

    protected abstract fun isEnabledCondition(prefs: PrefMap): Boolean
    protected open fun installHook() {}

    final override fun isEnabled(prefs: PrefMap) = isEnabledCondition(prefs)

    open override fun install(): FeatureInstallResult {
        installHook()
        return FeatureInstallResult.INSTALLED
    }
}
