package tv.withaibuild.customiuizer.mods.notificationautoexpand

import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped runtime state for the Notification Auto-Expand feature.
 *
 * Owns the published [NotificationAutoExpandSnapshot] through an [AtomicReference], a private
 * [refreshLock], and a process-scoped preference observer. It does not retain [View], [Context],
 * [Activity], or `ExpandableNotificationRow` instances.
 */
internal class NotificationAutoExpandRuntimeState @JvmOverloads internal constructor(
    private val refreshSource: () -> Map<String, Any> = { MainModule.mPrefs.getAll() },
) {

    val snapshotRef: AtomicReference<NotificationAutoExpandSnapshot?> = AtomicReference(null)

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
        if (key != null && key != MODE_KEY && key != APPS_KEY) {
            return
        }
        synchronized(refreshLock) {
            refreshSnapshotLocked()
        }
    }

    /**
     * Builds and publishes a new snapshot from the current [PrefMap] generation.
     *
     * The source [Map] is captured after [refreshLock] is acquired so an observer waiting behind
     * an in-progress refresh reads the latest generation.
     */
    private fun refreshSnapshotLocked() {
        try {
            val source = refreshSource()
            val modeRaw = source[MODE_KEY] as? String ?: "1"

            val rawApps = source[APPS_KEY]
            val selectedApps = if (rawApps is Set<*>) {
                Collections.unmodifiableSet(HashSet(rawApps.filterIsInstance<String>()))
            } else {
                Collections.unmodifiableSet(HashSet<String>())
            }

            snapshotRef.set(NotificationAutoExpandSnapshot(modeRaw, selectedApps))
        } catch (t: Throwable) {
            snapshotRef.set(null)
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
        }
    }

    /**
     * Performs the initial refresh outside the hooked `setFeedbackIcon` callback.
     */
    fun initialize() {
        synchronized(refreshLock) {
            refreshSnapshotLocked()
        }
    }

    companion object {

        private const val MODE_KEY = "system_expandnotifs"
        private const val APPS_KEY = "system_expandnotifs_apps"

        @Volatile
        internal var installed: Boolean = false
        internal var instance: NotificationAutoExpandRuntimeState? = null
        private val installLock = Any()

        /**
         * Creates and initializes the process-scoped runtime state at most once.
         *
         * Registers the process-scoped observer, then performs the initial refresh outside the
         * hooked `setFeedbackIcon` callback.
         *
         * Publication invariant: `installed` is set to `true` only after the unique instance is
         * non-null, the observer has been registered, and the initial refresh has completed. A
         * caller that observes `installed == true` is guaranteed to receive the unique,
         * initialized process instance.
         */
        @JvmStatic
        fun install(): NotificationAutoExpandRuntimeState = install(NotificationAutoExpandRuntimeState())

        /**
         * Installs a provided [NotificationAutoExpandRuntimeState] using the same
         * publication invariants as [install].
         */
        @JvmStatic
        internal fun install(runtimeState: NotificationAutoExpandRuntimeState): NotificationAutoExpandRuntimeState {
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
