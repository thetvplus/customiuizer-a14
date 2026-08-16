package tv.withaibuild.customiuizer.mods.utils

import android.content.SharedPreferences
import tv.withaibuild.customiuizer.utils.PrefMap
import tv.withaibuild.customiuizer.utils.canonicalPreferenceKey

/**
 * Thread-safe, idempotent bootstrap for the process-local preference snapshot.
 *
 * The class manages the full lifecycle of [RemotePreferences] as a single transaction:
 *
 *     1. Obtain the remote [SharedPreferences].
 *     2. First [SharedPreferences.getAll] for an initial snapshot.
 *     3. Register a single [OnSharedPreferenceChangeListener].
 *     4. Second [SharedPreferences.getAll] to cover changes made during the
 *        listener registration window.
 *     5. Atomically publish the final snapshot to the [PrefMap].
 *     6. Transition to [State.LOADED] or [State.VALID_EMPTY].
 *
 * The transition to [State.LOADED] / [State.VALID_EMPTY] only happens after the listener is
 * successfully registered and a second snapshot has been read.  No caller may use the snapshot for
 * hook installation decisions before [isReady] returns true.
 *
 * The initial snapshot is published silently: [changeDispatcher] is only invoked for changes that
 * arrive through the registered [OnSharedPreferenceChangeListener], not for the initial first or
 * second snapshot.  This means an owner-bound observer registered during the bootstrap window will
 * only re-apply state when a later change arrives; it cannot be relied on to close an initial-value
 * gap on its own.
 */
class PreferenceBootstrap private constructor(
    private val prefs: PrefMap,
    private val remoteSource: RemotePreferenceSource,
    private val changeDispatcher: (String?) -> Unit,
) {

    /**
     * Source of the remote [SharedPreferences].  This is a function because
     * [XposedModule.getRemotePreferences] is a protected method and must be supplied by the
     * module instance.
     */
    fun interface RemotePreferenceSource {
        fun get(name: String): SharedPreferences?
    }

    enum class State {
        UNINITIALIZED,
        UNAVAILABLE,
        SNAPSHOT_PENDING_LISTENER,
        EMPTY_PENDING,
        VALID_EMPTY,
        LOADED,
    }

    private val lock = Any()
    private val remoteName = ModuleHelper.prefsName + "_remote"

    @Volatile
    private var currentState = State.UNINITIALIZED

    private var remotePrefs: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Volatile
    private var listenerRegistered = false

    private var initAttempts = 0
    private var emptyPendingAttempts = 0

    private var unavailableReported = false
    private var emptyPendingReported = false
    private var validEmptyReported = false

    companion object {
        const val MAX_PREF_INIT_ATTEMPTS = 5
        const val MAX_EMPTY_PENDING_ATTEMPTS = 3

        @JvmStatic
        fun create(prefs: PrefMap, source: RemotePreferenceSource): PreferenceBootstrap {
            return PreferenceBootstrap(prefs, source, ModuleHelper::handlePreferenceChanged)
        }

        @JvmStatic
        fun create(
            prefs: PrefMap,
            source: RemotePreferenceSource,
            changeDispatcher: (String?) -> Unit,
        ): PreferenceBootstrap {
            return PreferenceBootstrap(prefs, source, changeDispatcher)
        }
    }

    /** The current bootstrap state. */
    fun getState(): State = currentState

    /** Whether the snapshot is safe to use for feature installation decisions. */
    fun isReady(): Boolean {
        val s = currentState
        return s == State.LOADED || s == State.VALID_EMPTY
    }

    /** Whether the live listener is registered. */
    fun isListenerRegistered(): Boolean = listenerRegistered

    /**
     * Single transaction entry.  Returns [isReady] after attempting to obtain the remote
     * preferences, register a listener, and publish a stable snapshot.
     *
     * Safe to call repeatedly from any thread.  If the bootstrap has already reached a ready state,
     * this returns immediately without re-registering.
     */
    fun bootstrap(): Boolean {
        synchronized(lock) {
            val s = currentState
            if (s == State.LOADED || s == State.VALID_EMPTY) return true

            if (s == State.UNAVAILABLE && initAttempts >= MAX_PREF_INIT_ATTEMPTS) return false

            if (s == State.EMPTY_PENDING) {
                if (emptyPendingAttempts >= MAX_EMPTY_PENDING_ATTEMPTS && !listenerRegistered) {
                    if (!emptyPendingReported) {
                        emptyPendingReported = true
                        XposedHelpers.log("Remote preferences empty-pending: retry limit reached, continuing without final state")
                    }
                    return false
                }
                emptyPendingAttempts++
            }

            if (s == State.UNINITIALIZED || s == State.UNAVAILABLE) {
                initAttempts++
                if (remotePrefs == null) {
                    try {
                        remotePrefs = remoteSource.get(remoteName)
                    } catch (vm: VirtualMachineError) {
                        currentState = State.UNAVAILABLE
                        throw vm
                    } catch (td: ThreadDeath) {
                        currentState = State.UNAVAILABLE
                        throw td
                    } catch (t: Throwable) {
                        currentState = State.UNAVAILABLE
                        HookDiagnostics.recordPreferencesUnavailable(t.javaClass.name, "getRemotePreferences")
                        return false
                    }
                }
                if (remotePrefs == null) {
                    currentState = State.UNAVAILABLE
                    HookDiagnostics.recordPreferencesUnavailable("", "getRemotePreferences returned null")
                    return false
                }
            }

            return doBootstrapLocked()
        }
    }

    /**
     * Old public entry points kept for binary compatibility, but [MainModule] should not call them
     * directly.  They delegate to [bootstrap].
     */
    @Deprecated("Use bootstrap() instead.", ReplaceWith("bootstrap()"))
    fun init() {
        bootstrap()
    }

    @Deprecated("Use bootstrap() instead.", ReplaceWith("bootstrap()"))
    fun installListener(): Boolean = bootstrap()

    /**
     * Performs the stable snapshot sequence under [lock].
     *
     * The snapshot is not published until after the listener is registered, and the second
     * [getAll] has completed.  This closes the window where a preference change would be lost.
     */
    private fun doBootstrapLocked(): Boolean {
        val remote = remotePrefs ?: return false

        val first = getAllOrFail(remote) ?: return false

        currentState = if (first.isEmpty()) {
            State.EMPTY_PENDING
        } else {
            State.SNAPSHOT_PENDING_LISTENER
        }

        if (!listenerRegistered) {
            val newListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                ModuleHelper.guarded { onPreferenceChanged(key) }
            }
            listener = newListener

            return try {
                remote.registerOnSharedPreferenceChangeListener(newListener)
                listenerRegistered = true
                initAttempts = 0
                emptyPendingAttempts = 0
                // A live watcher is in place. Take the second snapshot and publish.
                publishSecondSnapshotLocked(remote)
            } catch (vm: VirtualMachineError) {
                listener = null
                currentState = State.UNAVAILABLE
                throw vm
            } catch (td: ThreadDeath) {
                listener = null
                currentState = State.UNAVAILABLE
                throw td
            } catch (t: Throwable) {
                listener = null
                currentState = State.UNAVAILABLE
                HookDiagnostics.recordPreferencesUnavailable(t.javaClass.name, "registerOnSharedPreferenceChangeListener")
                false
            }
        } else {
            // Listener already registered; this is a retry. Take a fresh snapshot.
            return publishSecondSnapshotLocked(remote)
        }
    }

    private fun publishSecondSnapshotLocked(remote: SharedPreferences): Boolean {
        val second = getAllOrFail(remote) ?: return false

        if (second.isEmpty()) {
            currentState = State.VALID_EMPTY
            prefs.clear()
            if (!validEmptyReported) {
                validEmptyReported = true
                XposedHelpers.log("Remote preferences are valid but empty (watcher confirmed)")
            }
            return true
        }

        currentState = State.LOADED
        prefs.replaceSnapshot(second)
        return true
    }

    private fun getAllOrFail(remote: SharedPreferences): Map<String, *>? {
        return try {
            val all = remote.all
            if (all == null) {
                currentState = State.UNAVAILABLE
                HookDiagnostics.recordPreferencesUnavailable("", "getAll returned null")
                null
            } else {
                all
            }
        } catch (vm: VirtualMachineError) {
            currentState = State.UNAVAILABLE
            throw vm
        } catch (td: ThreadDeath) {
            currentState = State.UNAVAILABLE
            throw td
        } catch (t: Throwable) {
            currentState = State.UNAVAILABLE
            HookDiagnostics.recordPreferencesUnavailable(t.javaClass.name, "getAll")
            null
        }
    }

    /**
     * Listener callback. Runs on the remote-preference binder thread, so it must be fast and
     * must not install hooks or perform reflection.  The snapshot is updated atomically and the
     * state is kept in sync.
     */
    private fun onPreferenceChanged(key: String?) {
        if (key == null) return

        try {
            val remote = remotePrefs ?: return

            val rawValue = if (remote.contains(key)) {
                remote.all?.get(key)
            } else {
                null
            }

            if (rawValue == null) {
                prefs.remove(key)
            } else {
                prefs.put(key, rawValue)
            }

            synchronizeState()

            val canonicalKey = canonicalPreferenceKey(key)
            if (canonicalKey != "systemui_restart_time") {
                changeDispatcher(canonicalKey)
            }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
        }
    }

    /**
     * Keep the bootstrap state in sync with the snapshot after a single-key update.
     *
     * This is called from the listener thread.  It uses a quick in-memory check; no remote
     * SharedPreferences access here.
     */
    private fun synchronizeState() {
        synchronized(lock) {
            val s = currentState
            val empty = prefs.size() == 0

            when {
                (s == State.LOADED || s == State.VALID_EMPTY) && empty -> {
                    currentState = State.VALID_EMPTY
                }
                (s == State.LOADED || s == State.VALID_EMPTY) && !empty -> {
                    currentState = State.LOADED
                }
            }
        }
    }
}
