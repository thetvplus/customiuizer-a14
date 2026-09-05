package tv.withaibuild.customiuizer.mods.utils

import android.content.Context
import android.os.Handler
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
 * per-preference system-server hooks to a dedicated object.  Each hook is still guarded
 * by the same preference check; nothing is installed unless the user has enabled it.
 *
 * Preference-gated features are not decided against a torn/empty snapshot. When
 * [prefReady] is false they stay [FeatureState.NOT_INSTALLED]. The first
 * [PreferenceBootstrap.State.LOADED] snapshot runs [installAll] again for those
 * features only — already-installed hooks are never reinstalled.
 */
object SystemServerInstaller {

    private val lock = Any()
    private var registry: FeatureInstallRegistry? = null

    @Volatile
    private var catchUpDone = false

    @JvmStatic
    @JvmOverloads
    fun install(lpparam: XposedModuleInterface.SystemServerStartingParam, prefReady: Boolean = true) {
        val mPrefs = MainModule.mPrefs

        synchronized(lock) {
            val activeRegistry = registry ?: run {
                val created = FeatureInstallRegistry()
                val catalogStartNanos = FeatureInstallMetrics.nowNanos()
                val catalogStartBytes = FeatureInstallMetrics.allocatedBytes()
                val packagePermissionsFeature = PackagePermissionsFeature(lpparam, mPrefs)
                val features = SystemServerFeatures.all(lpparam)
                val catalogEndNanos = FeatureInstallMetrics.nowNanos()
                val catalogEndBytes = FeatureInstallMetrics.allocatedBytes()
                val registerStartNanos = FeatureInstallMetrics.nowNanos()
                val registerStartBytes = FeatureInstallMetrics.allocatedBytes()
                created.register(packagePermissionsFeature)

                // All preference-guarded system_server features.
                for (feature in features) {
                    created.register(feature)
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

                // PhoneWindowManager GlobalAction receiver is a cheap always-on transport.
                // It must be installed at system_server start so later none → configured
                // changes do not require restarting system_server. ROM-specific optional
                // hooks remain lazy inside setupStatusBar.
                GlobalActionSystemServerHooks.setupGlobalActions(lpparam)
                registry = created
                created
            }

            // Unready snapshots must not decide business features. Always-on specs
            // (preferenceKey null / isEnabled on an empty map) still install.
            val installPrefs = if (prefReady) mPrefs else PrefMap()
            activeRegistry.installAll(FeatureTarget.SYSTEM_SERVER, InstallPhase.SYSTEM_SERVER_STARTING, installPrefs)
            if (SystemServerPrefLifecycle.shouldMarkCatchUpComplete(prefReady, currentBootstrapState())) {
                catchUpDone = true
            }
        }
    }

    /**
     * First [PreferenceBootstrap.State.LOADED] only: install features still
     * [FeatureState.NOT_INSTALLED]. No-op once catch-up has run or when the snapshot
     * is still empty/unavailable.
     */
    @JvmStatic
    fun installPendingIfPrefsLoaded() {
        synchronized(lock) {
            if (!SystemServerPrefLifecycle.shouldRunCatchUp(catchUpDone, currentBootstrapState())) {
                return
            }
            val activeRegistry = registry ?: return
            activeRegistry.installAll(
                FeatureTarget.SYSTEM_SERVER,
                InstallPhase.SYSTEM_SERVER_STARTING,
                MainModule.mPrefs,
            )
            catchUpDone = true
        }
    }

    /**
     * Posts [installPendingIfPrefsLoaded] onto [context]'s main looper.
     *
     * Preference invalidation arrives on a binder thread; hook installation must not
     * run there.
     */
    @JvmStatic
    fun scheduleCatchUp(context: Context) {
        if (catchUpDone) return
        val looper = context.mainLooper ?: return
        Handler(looper).post { installPendingIfPrefsLoaded() }
    }

    private fun currentBootstrapState(): PreferenceBootstrap.State? =
        MainModule.sPreferenceBootstrap?.getState()
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
