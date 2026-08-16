package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.libxposed.service.RemotePreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single application-level owner for the LSPosed/Vector service connection.
 *
 * UI code must not treat a provisional state as "not active": see [State].
 * [AppHelper.moduleActive] is kept in sync for existing call sites.
 */
object XposedServiceManager {

    /**
     * - [UNKNOWN]: registration attempted, no callback yet.
     * - [BOUND]: the service is connected.
     * - [TIMED_OUT]: we stopped waiting. **This is not proof of anything.** The service can
     *   still bind afterwards, and on a device that has just restarted this process it often
     *   does — a captured log showed LSPosed service transactions taking up to 1.7 s while
     *   the settings app was being killed and relaunched repeatedly.
     * - [DISCONNECTED]: `onServiceDied`, or registration threw. A proven negative.
     *
     * Only [DISCONNECTED] justifies telling the user the module is not active without
     * further waiting.
     */
    enum class State {
        UNKNOWN, BOUND, TIMED_OUT, DISCONNECTED;

        /**
         * True while the state is still only provisional, i.e. the bind may yet succeed.
         *
         * [TIMED_OUT] is provisional for the same reason [UNKNOWN] is: nothing has been
         * observed. A caller that waits only while the state is [UNKNOWN] stops the moment
         * the timeout fires and then reads [TIMED_OUT] as a negative — the exact
         * misjudgement that made "module not active" appear after a language change
         * restarted this process.
         */
        val isProvisional: Boolean
            get() = this == UNKNOWN || this == TIMED_OUT
    }

    /**
     * Whether the module may be reported as not connected to the user.
     *
     * Only [State.DISCONNECTED] is a proven negative. Time passing does not add evidence:
     * [State.TIMED_OUT] can still become [State.BOUND], especially while this process is
     * being killed and relaunched quickly.
     */
    @JvmStatic
    @JvmOverloads
    fun shouldReportInactive(current: State = state): Boolean = current == State.DISCONNECTED

    /**
     * Whether a startup caller should stop waiting for [shouldReportInactive].
     *
     * A caller that is about to act on the state should wait for this, not for the state
     * to stop being [State.isProvisional] - a bind that never arrives leaves the state
     * provisional forever. Expiring the wait budget ends UI polling but deliberately does
     * not turn a timeout into evidence that the module is inactive.
     */
    @JvmStatic
    fun isDecided(): Boolean = !state.isProvisional || decisionBudgetElapsed()

    /**
     * Whether a setting the user changed has not reached the module.
     *
     * The mirror is the only way a setting gets to the module, so while this is true the
     * settings screen is showing values the module is not running on. It is the part of an
     * unbound service the user can actually observe - a toggle that appears to do nothing -
     * so it is worth saying out loud rather than leaving them to conclude the feature is
     * broken.
     *
     * Cleared by the next successful reconcile, which is why the flag needs no persistence:
     * a restart re-mirrors everything from local anyway.
     */
    @JvmStatic
    fun hasUndeliveredChanges(): Boolean = mirrorState.hasUndeliveredChanges

    /**
     * Whether enough time has passed since [init] that a still-pending bind counts against
     * the module.
     *
     * Measured from the registration attempt with [SystemClock.elapsedRealtime], so it is
     * unaffected by the wall clock and by how long any particular screen has been open.
     * Before [init] runs there is nothing to time, and nothing has been observed either,
     * so the budget has not elapsed.
     */
    @JvmStatic
    fun decisionBudgetElapsed(): Boolean {
        val started = initElapsedRealtime
        if (started == NOT_STARTED) return false
        return SystemClock.elapsedRealtime() - started >= FULL_DECISION_BUDGET_MS
    }

    @JvmField
    @Volatile
    var state: State = State.UNKNOWN

    @JvmField
    @Volatile
    var service: XposedService? = null

    @JvmField
    @Volatile
    var remotePrefs: RemotePreferences? = null

    private const val NOT_STARTED = 0L

    private const val LOG_TAG = "XposedService"

    @JvmStatic
    fun requestApi102Scope(
        packages: List<String>,
        callback: (Boolean, String?) -> Unit,
    ): Boolean {
        val boundService = service ?: return false
        if (boundService.apiVersion < XposedService.API_102) return false
        Api102ScopeRequester.request(boundService, packages, callback)
        return true
    }

    private var initialized = false

    /** [SystemClock.elapsedRealtime] at [init], or [NOT_STARTED]. */
    @Volatile
    private var initElapsedRealtime = NOT_STARTED

    /** Bind-generation bookkeeping for the mirror; see [PrefsMirrorState]. */
    private val mirrorState = PrefsMirrorState()

    /**
     * UI deadlines and delayed retry triggers live on the main looper. Preference snapshots,
     * map copies and RemotePreferences editor updates run on [mirrorScope] instead.
     *
     * libxposed 102 `RemotePreferences.Editor.apply()` sends Binder work through its own
     * executor, but it first clones and updates the complete in-memory map on the caller.
     * Doing that from SharedPreferences' main-thread listener delays the switch rebind and
     * makes a successful tap look ignored. A shared Default worker avoids a dedicated thread;
     * limited parallelism preserves editor order without locks around remote calls.
     */
    private val handler = Handler(Looper.getMainLooper())
    private val mirrorFailureHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is OutOfMemoryError) throw throwable
        AppHelper.log(LOG_TAG, "mirror worker failed: $throwable")
    }
    private val mirrorScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(1) + mirrorFailureHandler
    )

    /**
     * Upper bound on how long [state] may stay [State.UNKNOWN].
     *
     * A caller that needs a decided state must not give up before this. It used to pick
     * its own, shorter deadline, so it stopped waiting while the state was still UNKNOWN
     * and drew no conclusion; the "module not active" dialog then appeared at whatever
     * arbitrary later moment the screen was next entered — most visibly right after the
     * cold restart that a language change forces, which is exactly when binding is
     * slowest.
     */
    const val BIND_DECISION_TIMEOUT_MS = 3500L

    /**
     * Total time from [init] before a startup caller stops waiting for a still-pending bind.
     * Two bind windows plus a margin: the first covers an ordinary start, the
     * second covers a bind still in flight after a process restart - which is what a
     * language change forces, and when binding is slowest.
     *
     * This lives here, not in a screen, so every caller has the same bounded wait regardless
     * of when it happened to open. It never changes the meaning of [State.TIMED_OUT].
     */
    const val FULL_DECISION_BUDGET_MS = BIND_DECISION_TIMEOUT_MS * 2 + 500L

    /**
     * Delay before the single re-mirror attempt that follows a failed remote write.
     *
     * One shot, scheduled only after a failure, on the handler this object already owns - so
     * a device where nothing ever fails posts nothing. Long enough that a service being
     * replaced underneath us has finished, short enough that the user is plausibly still on
     * the screen where they made the change.
     */
    private const val MIRROR_RETRY_DELAY_MS = 2000L

    private val timeoutRunnable = Runnable {
        if (state == State.UNKNOWN) {
            // Give up waiting, but do not claim the module is inactive: the listener stays
            // registered and a later bind still promotes this to BOUND.
            state = State.TIMED_OUT
            AppHelper.moduleActive = false
            AppHelper.log(
                LOG_TAG,
                "no bind within ${BIND_DECISION_TIMEOUT_MS}ms; still waiting for the service"
            )
        }
    }

    private val IGNORE_KEYS = setOf(
        "pref_key_miuizer_locale",
        "pref_key_miuizer_locale_applied",
        "pref_key_miuizer_launchericon",
        "pref_key_miuizer_synced_from_lsposed",
        CurrentPreferenceContract.CONTRACT_REVISION_KEY,
    )

    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key != null && IGNORE_KEYS.contains(key)) return@OnSharedPreferenceChangeListener

        val generation = mirrorState.currentGeneration
        val remote = remotePrefs
        if (remote == null || generation == PrefsMirrorState.NO_GENERATION) {
            // The change is not lost - it is in the local preferences, and the pass that
            // runs on the next bind pushes whatever is there. Recording it is what lets the
            // UI say the module is not running on what the screen shows, instead of the
            // user finding out by toggling something that does nothing.
            markUndelivered("service not bound")
            return@OnSharedPreferenceChangeListener
        }

        // True while a pass is in flight, in which case that pass may be about to write a
        // snapshot taken before this change. The state machine has recorded it and will
        // owe one follow-up pass, so this write does not have to be the last word.
        mirrorState.onLocalChange()

        if (key == null) {
            // A whole-file change (a restore, or a clear) cannot be mirrored key by key.
            // It also must not be re-planned here: this listener runs on whichever thread
            // committed the restore. Hand it to the pass, which owns the planning.
            requestMirrorPass(generation, "bulk preference change")
            return@OnSharedPreferenceChangeListener
        }

        requestPreferenceWrite(sharedPreferences, key, generation)
    }

    /** Leaves the SharedPreferences callback after a constant-time enqueue. */
    private fun requestPreferenceWrite(
        sharedPreferences: SharedPreferences,
        key: String,
        generation: Long
    ) {
        mirrorScope.launch {
            if (!mirrorState.isCurrent(generation)) {
                markUndelivered("generation changed before remote write")
                return@launch
            }
            val remote = remotePrefs
            if (remote == null) {
                markUndelivered("remote preferences disappeared before write")
                return@launch
            }
            val written = try {
                // getAll() copies the local map; keep it off the input frame as well.
                val value = sharedPreferences.all[key]
                val edit = remote.edit()
                if (value == null) edit.remove(key) else putValue(edit, key, value)
                edit.apply()
                true
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                AppHelper.log(LOG_TAG, "remote write for '$key' failed: $t")
                false
            }

            if (!written) {
                markUndelivered("remote write failed")
                scheduleMirrorRetry(generation)
            }
        }
    }

    /**
     * Writes [value] under [key].
     *
     * String sets are copied. `SharedPreferences.getAll` hands out the set it stores, and
     * both this and [PrefsMirror.plan] would otherwise put a live reference to it into the
     * remote store, where a later local edit could mutate it behind the module's back.
     */
    private fun putValue(edit: SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is Float -> edit.putFloat(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, LinkedHashSet(value as Set<String>))
        }
    }

    private fun markUndelivered(reason: String) {
        // Logged once per stretch: a user working through a settings screen with no service
        // would otherwise fill the log with identical lines.
        if (mirrorState.markUndelivered()) {
            AppHelper.log(LOG_TAG, "settings change not mirrored to the module ($reason)")
        }
    }

    /** Queues a pass off the caller's thread. Stale generations are dropped by [runMirror]. */
    private fun requestMirrorPass(generation: Long, reason: String) {
        mirrorScope.launch { runMirror(generation, reason) }
    }

    /**
     * Re-mirrors once, a short while after a failed write.
     *
     * One attempt per bind: [PrefsMirrorState.claimRetry] refuses a second for the same
     * generation, so a write that fails for good leaves the flag set instead of turning into
     * a two-second poll. A new bind is a new generation and gets its own single retry.
     */
    private fun scheduleMirrorRetry(generation: Long) {
        if (!mirrorState.claimRetry(generation)) return
        handler.postDelayed({
            if (mirrorState.isCurrent(generation)) {
                requestMirrorPass(generation, "retry after a failed write")
            }
        }, MIRROR_RETRY_DELAY_MS)
    }

    /**
     * Brings the remote snapshot in line with the local preferences.
     *
     * One pass, plus at most one more if a change landed while the first was reading. Any
     * change that arrives after that leaves the mirror reported as incomplete rather than
     * starting a third pass - the flag is recoverable, an unbounded loop is not.
     */
    private fun runMirror(generation: Long, reason: String) {
        if (!mirrorState.beginPass(generation)) return

        var complete = mirrorPass(generation, reason)
        if (mirrorState.claimFollowUpPass(generation)) {
            complete = mirrorPass(generation, "$reason, changed while mirroring") && complete
        }

        val settled = mirrorState.endPass(generation)
        if (complete && settled) {
            mirrorState.clearUndelivered(generation)
        }
    }

    /** One pass. Returns true only if a plan was built and committed without throwing. */
    private fun mirrorPass(generation: Long, reason: String): Boolean {
        if (!mirrorState.isCurrent(generation)) return false
        val remote = remotePrefs ?: return false
        val local = AppHelper.appPrefs?.all ?: return false
        return try {
            val plan = PrefsMirror.plan(local, remote.all, IGNORE_KEYS)
            if (!plan.isEmpty) {
                // One editor for the whole plan: a per-key commit would be a binder call and
                // a change callback in every hooked process for every single key.
                val edit = remote.edit()
                for ((planKey, planValue) in plan.puts) putValue(edit, planKey, planValue)
                for (planKey in plan.removes) edit.remove(planKey)
                edit.apply()
                AppHelper.log(LOG_TAG, "mirrored ${plan.size} setting(s) to the module ($reason)")
            }
            true
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            AppHelper.log(LOG_TAG, "mirror pass failed ($reason): $t")
            markUndelivered("mirror pass failed")
            scheduleMirrorRetry(generation)
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun init(appPrefs: SharedPreferences?) {
        if (initialized) return
        initialized = true
        initElapsedRealtime = SystemClock.elapsedRealtime()

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, BIND_DECISION_TIMEOUT_MS)

        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(s: XposedService) {
                    handler.removeCallbacks(timeoutRunnable)
                    state = State.BOUND
                    service = s
                    remotePrefs = s.getRemotePreferences(AppHelper.prefsName + "_remote") as RemotePreferences
                    AppHelper.remotePrefs = remotePrefs
                    AppHelper.moduleActive = true

                    // Order matters. The listener goes on before the generation opens, so a
                    // change made between the two cannot fall into a gap where neither the
                    // listener nor the first pass would carry it. Re-registering the same
                    // instance is a no-op in SharedPreferencesImpl, so a second bind does
                    // not double up.
                    appPrefs?.registerOnSharedPreferenceChangeListener(prefsChanged)
                    val generation = mirrorState.onBind()

                    AppHelper.log(
                        LOG_TAG,
                        "service bound ${SystemClock.elapsedRealtime() - initElapsedRealtime}ms " +
                            "after registration (generation $generation)"
                    )
                    // Whatever the incremental mirror missed while unbound is pushed by this
                    // pass. Posted, never run inline: this callback arrives on the daemon's
                    // binder thread. See handler.
                    requestMirrorPass(generation, "service bound")
                }

                override fun onServiceDied(s: XposedService) {
                    handler.removeCallbacks(timeoutRunnable)
                    state = State.DISCONNECTED
                    service = null
                    remotePrefs = null
                    AppHelper.remotePrefs = null
                    AppHelper.moduleActive = false
                    // Ends the generation, which is what makes every queued pass and the
                    // pending retry from this connection no-ops when they run.
                    mirrorState.onUnbind()
                    AppHelper.log(LOG_TAG, "service died")
                    try {
                        appPrefs?.unregisterOnSharedPreferenceChangeListener(prefsChanged)
                    } catch (_: Throwable) {
                    }
                }
            })
        } catch (t: Throwable) {
            handler.removeCallbacks(timeoutRunnable)
            state = State.DISCONNECTED
            AppHelper.moduleActive = false
            AppHelper.log(LOG_TAG, "registerListener threw, giving up: $t")
        }
    }
}
