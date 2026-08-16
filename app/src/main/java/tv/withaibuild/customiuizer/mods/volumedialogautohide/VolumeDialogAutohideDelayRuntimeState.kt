package tv.withaibuild.customiuizer.mods.volumedialogautohide

import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped runtime state for the VolumeDialogAutohideDelay feature.
 *
 * Owns the published [VolumeDialogAutohideDelaySnapshot] through an
 * [AtomicReference], a private [refreshLock], and a process-scoped preference
 * observer. It does not retain [View], [Context], [Activity], or
 * [MiuiVolumeDialogImpl] instances.
 */
internal class VolumeDialogAutohideDelayRuntimeState @JvmOverloads internal constructor(
    private val refreshSource: () -> Map<String, Any> = { MainModule.mPrefs.getAll() },
) {

    val snapshotRef: AtomicReference<VolumeDialogAutohideDelaySnapshot?> = AtomicReference(null)

    private val refreshLock = Any()
    @JvmField
    internal val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) = ModuleHelper.guarded {
            onPreferenceChanged(key)
        }
    }

    private var observerRegistered: Boolean = false

    /**
     * Registers the process-scoped preference observer exactly once for this state.
     *
     * Must be called under [installLock].
     */
    internal fun installObserver() {
        if (!observerRegistered) {
            ModuleHelper.observePreferenceChange(preferenceObserver)
            observerRegistered = true
        }
    }

    /**
     * Called from [ModuleHelper.guarded] on a preference change.
     */
    internal fun onPreferenceChanged(key: String?) {
        if (key != null && key != EXPANDED_KEY && key != COLLAPSED_KEY) {
            return
        }
        synchronized(refreshLock) {
            refreshSnapshotLocked()
        }
    }

    /**
     * Builds and publishes a new snapshot from the current [PrefMap] generation.
     *
     * The source [Map] is captured after [refreshLock] is acquired so an observer
     * waiting behind an in-progress refresh reads the latest generation.
     */
    private fun refreshSnapshotLocked() {
        return try {
            val source = refreshSource()
            val snapshot = VolumeDialogAutohideDelaySnapshot(
                expanded = source[EXPANDED_KEY] as? Int ?: 0,
                collapsed = source[COLLAPSED_KEY] as? Int ?: 0,
            )
            snapshotRef.set(snapshot)
        } catch (t: Throwable) {
            snapshotRef.set(null)
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
        }
    }

    /**
     * Performs the initial refresh outside the hooked [computeTimeoutH] callback.
     */
    fun initialize() {
        synchronized(refreshLock) {
            refreshSnapshotLocked()
        }
    }

    companion object {

        private const val EXPANDED_KEY = "system_volumedialogdelay_expanded"
        private const val COLLAPSED_KEY = "system_volumedialogdelay_collapsed"

        @Volatile
        internal var installed: Boolean = false
        internal var instance: VolumeDialogAutohideDelayRuntimeState? = null
        private val installLock = Any()

        /**
         * Creates and initializes the process-scoped runtime state at most once.
         *
         * Registers the process-scoped observer, then performs the initial refresh
         * outside the hooked [computeTimeoutH] callback.
         *
         * Publication invariant: `installed` is set to `true` only after the unique
         * instance is non-null, the observer has been registered, and the initial
         * refresh has completed. A caller that observes `installed == true` is
         * guaranteed to receive the unique, initialized process instance.
         */
        @JvmStatic
        fun install(): VolumeDialogAutohideDelayRuntimeState = install(VolumeDialogAutohideDelayRuntimeState())

        /**
         * Installs a provided [VolumeDialogAutohideDelayRuntimeState] using the same
         * publication invariants as [install].
         */
        @JvmStatic
        internal fun install(runtimeState: VolumeDialogAutohideDelayRuntimeState): VolumeDialogAutohideDelayRuntimeState {
            if (installed) {
                return checkNotNull(instance) { "installed=true but instance is null" }
            }

            synchronized(installLock) {
                if (installed) {
                    return checkNotNull(instance) { "installed=true but instance is null" }
                }

                instance = runtimeState

                runtimeState.installObserver()
                runtimeState.initialize()

                installed = true
                return runtimeState
            }
        }
    }
}
