package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedModuleInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks
import tv.withaibuild.customiuizer.mods.PackagePermissions
import tv.withaibuild.customiuizer.mods.utils.feature.PackagePermissionsFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.SystemServerFeatures
import tv.withaibuild.customiuizer.utils.PrefMap
/**
 * Installer for hooks that must run in `system_server`.
 *
 * This keeps [MainModule] focused on module-level lifecycle and delegates the long list of
 * per-preference system-server hooks to a dedicated, stateless object.  Each hook is still guarded
 * by the same preference check; nothing is installed unless the user has enabled it.
 */
object SystemServerInstaller {

    @JvmStatic
    @JvmOverloads
    fun install(lpparam: XposedModuleInterface.SystemServerStartingParam, prefReady: Boolean = true) {
        val mPrefs = MainModule.mPrefs

        // Base system_server hook: not preference-controlled, always installed.
        val registry = FeatureInstallRegistry()
        val catalogStartNanos = FeatureInstallMetrics.nowNanos()
        val catalogStartBytes = FeatureInstallMetrics.allocatedBytes()
        val packagePermissionsFeature = PackagePermissionsFeature(lpparam, mPrefs)
        val features = SystemServerFeatures.all(lpparam)
        val catalogEndNanos = FeatureInstallMetrics.nowNanos()
        val catalogEndBytes = FeatureInstallMetrics.allocatedBytes()
        val registerStartNanos = FeatureInstallMetrics.nowNanos()
        val registerStartBytes = FeatureInstallMetrics.allocatedBytes()
        registry.register(packagePermissionsFeature)

        // All preference-guarded system_server features.
        for (feature in features) {
            registry.register(feature)
        }

        val registerEndNanos = FeatureInstallMetrics.nowNanos()
        val registerEndBytes = FeatureInstallMetrics.allocatedBytes()
        FeatureInstallMetrics.recordCatalog(
            label = "system-server/starting",
            specCount = features.size + 1,
            catalogStartNanos = catalogStartNanos,
            catalogEndNanos = catalogEndNanos,
            catalogStartBytes = catalogStartBytes,
            catalogEndBytes = catalogEndBytes,
            registerStartNanos = registerStartNanos,
            registerEndNanos = registerEndNanos,
            registerStartBytes = registerStartBytes,
            registerEndBytes = registerEndBytes,
        )

        // Global actions are still checked eagerly because they depend on a cached map that must
        // not be built before preferences are ready.
        if (prefReady && hasConfiguredGlobalActions()) {
            GlobalActionSystemServerHooks.setupGlobalActions(lpparam)
        }

        registry.installAll(FeatureTarget.SYSTEM_SERVER, InstallPhase.SYSTEM_SERVER_STARTING, mPrefs)
    }
}

internal class PackagePermissionsFeature(
    private val lpparam: XposedModuleInterface.SystemServerStartingParam,
    private val mPrefs: PrefMap
) : FeatureDefinition {
    override val id = PackagePermissionsFeatureId
    override val name = "Package permissions"
    override val preferenceKey: String? = null
    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING
    override fun isEnabled(prefs: PrefMap) = true

    override fun install(): FeatureInstallResult {
        PackagePermissions.hook(lpparam)
        return FeatureInstallResult.INSTALLED
    }
}
