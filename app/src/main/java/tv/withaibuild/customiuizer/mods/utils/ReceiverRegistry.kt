package tv.withaibuild.customiuizer.mods.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object ReceiverRegistry {
    private enum class RegistrationState {
        PENDING_REGISTER,
        ACTIVE,
        PENDING_UNREGISTER,
        STALE,
        RELEASED,
        REGISTER_FAILED
    }

    private class ModuleReceiverRegistration(
        val context: Context,
        val receiver: BroadcastReceiver,
        val generation: Long,
        val state: AtomicReference<RegistrationState> = AtomicReference(RegistrationState.PENDING_REGISTER)
    )

    private val moduleReceivers = ConcurrentHashMap<String, ModuleReceiverRegistration>()
    private val moduleReceiverGeneration = AtomicLong(0)

    /** Maximum stale receivers held per key while waiting for a retry. */
    private const val MAX_STALE_MODULE_RECEIVERS = 3

    /** Receivers whose framework unregister failed. Retried on the next same-key operation. */
    private val staleModuleReceivers = ConcurrentHashMap<String, ConcurrentLinkedDeque<ModuleReceiverRegistration>>()



    /**
     * Registers [receiver] under [key], replacing whatever the module last registered there.
     *
     * Hook targets are recreated while the process lives: a new `BluetoothControllerImpl`, a
     * new keyguard controller after a theme change, a new Launcher after a rotation. Every
     * recreation runs the constructor or init hook again. Cleanup keyed on the hooked instance
     * cannot see the previous registration — that instance is gone — so each recreation used
     * to leave one more live receiver behind. The module then did the same work N times per
     * broadcast and pinned N dead Contexts.
     *
     * A process-scoped key keeps exactly one live receiver per logical registration,
     * regardless of how many times the hook fires.
     *
     * The registration and replacement sequence is atomic:
     * 1. The map is updated with a new, unique [ModuleReceiverRegistration] first.
     * 2. The previous registration (if any) is unregistered outside the map lock.
     * 3. The framework is asked to register the new receiver.
     * 4. After the framework call, the map is checked again. If another thread has replaced
     *    this registration in the meantime, this thread self-unregisters so the winner is the
     *    only tracked, active receiver.
     * 5. If the framework registration throws, only this thread's own map entry is removed.
     *
     * The registration holds the [Context] strongly because it is required for safe
     * unregistration and the key is process-scoped. Only [Context.getApplicationContext] is
     * retained to avoid pinning an Activity / View context.
     */
    @JvmStatic
    @JvmOverloads
    fun registerModuleReceiver(
        context: Context,
        key: String,
        receiver: BroadcastReceiver,
        filter: IntentFilter,
        flags: Int,
        permission: String? = null
    ): Boolean {
        val appContext = context.applicationContext ?: context

        // Retry any stale receivers left over from a previous failed unregister before we
        // touch the active slot. This is the only safe retry point, not a hot path.
        retryStaleModuleReceivers(key)

        // Calling with the exact same receiver instance is a no-op. This keeps repeated init
        // from the same hook target idempotent and avoids a second framework registration.
        val current = moduleReceivers[key]
        if (current != null && current.receiver === receiver) return true

        val generation = moduleReceiverGeneration.incrementAndGet()
        val newReg = ModuleReceiverRegistration(appContext, receiver, generation)

        // Atomically install the new registration. The previous value (if any) is captured so
        // it can be unregistered outside this compute block, avoiding any framework call inside
        // a potentially blocking map operation.
        val previousRef = java.util.concurrent.atomic.AtomicReference<ModuleReceiverRegistration?>(null)
        val installed = moduleReceivers.compute(key) { _, old ->
            previousRef.set(old)
            // If the same receiver was installed concurrently between the first read above and
            // this compute, keep the existing one. This keeps the map consistent with the
            // framework, which would reject a duplicate registration of the same receiver.
            if (old?.receiver === receiver) old else newReg
        }

        // If the compute kept the previous registration because of a same-receiver race, the
        // framework already has this receiver. Do not touch it again.
        val previous = previousRef.get()
        if (previous?.receiver === receiver) return true

        // `installed` is non-null here (it is either `newReg` or a non-null old registration
        // kept because of a same-receiver race, which we just returned from above).
        if (installed == null) return false

        // Unregister the previous receiver, but do not let a failed unregister prevent the new
        // one from being registered. Failed unregistrations move to the bounded stale queue for
        // a later retry instead of being silently lost.
        if (previous != null) {
            previous.state.set(RegistrationState.PENDING_UNREGISTER)
            if (!releaseModuleRegistration(previous)) {
                recordStaleModuleReceiver(key, previous)
            } else {
                previous.state.set(RegistrationState.RELEASED)
            }
        }

        return try {
            appContext.registerReceiver(receiver, filter, permission, null, flags)

            // Another thread may have replaced this same key while we were inside
            // registerReceiver. If our registration is no longer current, we are the loser and
            // must self-unregister.
            val stillCurrent = moduleReceivers[key]
            if (stillCurrent !== installed) {
                installed.state.set(RegistrationState.PENDING_UNREGISTER)
                if (!releaseModuleRegistration(installed)) {
                    recordStaleModuleReceiver(key, installed)
                } else {
                    installed.state.set(RegistrationState.RELEASED)
                }
                return false
            }
            installed.state.compareAndSet(RegistrationState.PENDING_REGISTER, RegistrationState.ACTIVE)
        } catch (oom: OutOfMemoryError) {
            installed.state.set(RegistrationState.REGISTER_FAILED)
            moduleReceivers.remove(key, installed)
            throw oom
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            installed.state.set(RegistrationState.REGISTER_FAILED)
            // Registration failed. Roll back only our own record, leaving any newer record
            // untouched.
            moduleReceivers.computeIfPresent(key) { _, reg ->
                if (reg === installed) null else reg
            }
            false
        }
    }

    /** Unregisters the receiver held under [key], if the module still has one. */
    @JvmStatic
    @JvmOverloads
    fun unregisterModuleReceiver(key: String, expectedReceiver: BroadcastReceiver? = null) {
        // Retry any stale receivers before touching the active slot.
        retryStaleModuleReceivers(key)

        val current = moduleReceivers[key] ?: return
        // If a specific receiver was expected, only remove that exact registration. This
        // prevents a concurrent replacement from being accidentally torn down.
        if (expectedReceiver != null && current.receiver !== expectedReceiver) return
        current.state.set(RegistrationState.PENDING_UNREGISTER)
        // remove(key, value) is atomic: the entry is removed only if it is still the one
        // we observed, so a registration that has just been replaced by another thread is
        // never deleted under our feet.
        if (moduleReceivers.remove(key, current)) {
            if (!releaseModuleRegistration(current)) {
                recordStaleModuleReceiver(key, current)
            } else {
                current.state.set(RegistrationState.RELEASED)
            }
        }
    }

    /**
     * Unregisters [reg] from the framework. Returns true on success, false if the framework
     * call threw. This is the only place that calls [Context.unregisterReceiver] for a module
     * receiver, so failed unregistrations are handled by the stale queue above.
     */
    private fun releaseModuleRegistration(reg: ModuleReceiverRegistration): Boolean {
        return try {
            reg.context.unregisterReceiver(reg.receiver)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Records [reg] as stale so a future same-key operation can retry the framework unregister.
     * The queue is bounded; if it is full, the oldest stale receiver is evicted and a single
     * retry is attempted. Receivers that still cannot be unregistered after that are dropped
     * with a diagnostic record so the process is not taken down by cleanup code.
     */
    private fun recordStaleModuleReceiver(key: String, reg: ModuleReceiverRegistration) {
        reg.state.set(RegistrationState.STALE)
        staleModuleReceivers.compute(key) { _, queue ->
            val newQueue = ConcurrentLinkedDeque(queue ?: emptyList())
            if (newQueue.size >= MAX_STALE_MODULE_RECEIVERS) {
                val oldest = newQueue.pollFirst()
                if (oldest != null) {
                    if (releaseModuleRegistration(oldest)) {
                        oldest.state.set(RegistrationState.RELEASED)
                    } else {
                        // Bounded best effort: the oldest is evicted because we cannot track
                        // an unbounded number of stuck receivers. It may still be in the
                        // framework, but it is no longer our responsibility.
                        oldest.state.set(RegistrationState.RELEASED)
                        HookDiagnostics.record(
                            processName(),
                            HookDiagnostics.Kind.RECEIVER,
                            "ModuleReceiverRegistry",
                            reg.receiver.javaClass.name,
                            key,
                            HookDiagnostics.Status.RECEIVER_STALE_DROPPED,
                            "stale receiver evicted due to bounded queue",
                        )
                    }
                }
            }
            newQueue.addLast(reg)
            HookDiagnostics.record(
                processName(),
                HookDiagnostics.Kind.RECEIVER,
                "ModuleReceiverRegistry",
                reg.receiver.javaClass.name,
                key,
                HookDiagnostics.Status.RECEIVER_UNREGISTER_FAILED,
                "receiver moved to stale queue",
            )
            newQueue
        }
    }

    /**
     * Retries unregistration for every stale receiver under [key]. Receivers that still fail
     * are kept in the bounded stale queue; receivers that succeed are removed.
     */
    private fun retryStaleModuleReceivers(key: String) {
        staleModuleReceivers.compute(key) { _, queue ->
            if (queue == null) return@compute null
            val stillStale = ConcurrentLinkedDeque<ModuleReceiverRegistration>()
            for (reg in queue) {
                if (reg.state.get() == RegistrationState.RELEASED) continue
                if (releaseModuleRegistration(reg)) {
                    reg.state.set(RegistrationState.RELEASED)
                } else if (stillStale.size < MAX_STALE_MODULE_RECEIVERS) {
                    reg.state.set(RegistrationState.STALE)
                    stillStale.addLast(reg)
                } else {
                    // Bounded best effort: drop on retry if the queue is already full.
                    reg.state.set(RegistrationState.RELEASED)
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.RECEIVER,
                        "ModuleReceiverRegistry",
                        reg.receiver.javaClass.name,
                        key,
                        HookDiagnostics.Status.RECEIVER_STALE_DROPPED,
                        "stale receiver dropped on retry due to bounded queue",
                    )
                }
            }
            if (stillStale.isEmpty()) null else stillStale
        }
    }

    private class OwnedReceiver(
        val ownerRef: WeakReference<Any>,
        val contextRef: WeakReference<Context>,
        val receiver: WeakOwnerReceiver
    )

    private val ownedReceivers = ConcurrentHashMap<String, CopyOnWriteArrayList<OwnedReceiver>>()

    /**
     * Callback for [registerOwnedReceiver]. The receiver is passed so callers can call
     * [BroadcastReceiver.setResultCode] and [BroadcastReceiver.isOrderedBroadcast].
     *
     * Implementations must not close over the [owner]; use the [owner] parameter instead.
     * Closing over the owner turns the weak-reference design into a strong reference and leaks
     * the hook target.
     */
    fun interface OwnedReceiverCallback {
        fun onReceive(receiver: BroadcastReceiver, owner: Any, context: Context, intent: Intent)
    }

    /**
     * A [BroadcastReceiver] that only holds a [WeakReference] to its owner.
     *
     * When a broadcast arrives and the owner is still alive, the owner is passed to the
     * [OwnedReceiverCallback]. If the owner has been collected, the broadcast is ignored and the
     * receiver will be unregistered by the next [registerOwnedReceiver] sweep for this key.
     */
    internal class WeakOwnerReceiver(
        owner: Any,
        private val registeredKey: String? = null,
        private val callback: OwnedReceiverCallback
    ) : BroadcastReceiver() {
        private val ownerRef = WeakReference(owner)
        private val active = java.util.concurrent.atomic.AtomicBoolean(true)

        internal fun markInactive() {
            active.set(false)
        }

        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
            if (!active.get()) {
                cleanupIfOwnerGone(context)
                return@guarded
            }
            val owner = ownerRef.get()
            if (owner == null) {
                active.set(false)
                cleanupIfOwnerGone(context)
                return@guarded
            }
            callback.onReceive(this, owner, context, intent)
        }

        private fun cleanupIfOwnerGone(fallbackContext: Context) {
            // Remove this receiver from the ownedReceivers registry. Always try to unregister;
            // a previous cleanup may have failed or the registry may already be gone. Failure is
            // logged and ignored so the host process never crashes because the owner was
            // collected before the broadcast arrived.
            val receiver = this
            val registration = removeOwnedRegistration(receiver)

            // Prefer the Context that was used at registration time; fall back to the Context
            // supplied by the broadcast delivery.
            val context = registration?.contextRef?.get() ?: fallbackContext
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
                // Already unregistered or Context is gone; the next broadcast will retry.
            }
        }

        private fun removeOwnedRegistration(receiver: WeakOwnerReceiver): OwnedReceiver? {
            val key = registeredKey
            return if (key != null) {
                removeOwnedRegistrationForKey(key, receiver)
            } else {
                // Fallback for receivers created without a key (unit tests).
                removeOwnedRegistrationFallback(receiver)
            }
        }

        private fun removeOwnedRegistrationForKey(key: String, receiver: WeakOwnerReceiver): OwnedReceiver? {
            val removedRef = java.util.concurrent.atomic.AtomicReference<OwnedReceiver?>(null)
            ownedReceivers.compute(key) { _, list ->
                val newList = list?.let { CopyOnWriteArrayList(it) }
                val found = newList?.find { it.receiver === receiver }
                if (found != null) {
                    newList.remove(found)
                    removedRef.set(found)
                }
                if (newList.isNullOrEmpty()) null else newList
            }
            return removedRef.get()
        }

        private fun removeOwnedRegistrationFallback(receiver: WeakOwnerReceiver): OwnedReceiver? {
            // Tests may create WeakOwnerReceiver directly without a key. In that case we still
            // need to find and remove the registration, but the list of keys is small.
            for ((key, _) in ownedReceivers) {
                val found = removeOwnedRegistrationForKey(key, receiver)
                if (found != null) return found
            }
            return null
        }
    }

    /**
     * Registers a weakly-owned receiver for [owner] and unregisters the receivers of owners that
     * have since been collected.
     *
     * Use this instead of [registerModuleReceiver] when several hook targets can legitimately
     * be alive at once — two clock controllers, one status bar per display — so a single
     * process-wide slot would silently disable all but the newest.
     *
     * The [OwnedReceiverCallback] must not capture the owner; it receives the owner (or nothing,
     * if it has been collected) as a parameter.
     */
    @JvmStatic
    @JvmOverloads
    fun registerOwnedReceiver(
        context: Context,
        owner: Any,
        key: String,
        filter: IntentFilter,
        flags: Int,
        permission: String? = null,
        callback: OwnedReceiverCallback
    ): BroadcastReceiver {
        val receiver = WeakOwnerReceiver(owner, key, callback)
        val newReg = OwnedReceiver(WeakReference(owner), WeakReference(context), receiver)

        // Atomically replace stale / same-owner registrations and add the new one.
        // No Android framework calls are made inside the remapping function.
        val removedRef = java.util.concurrent.atomic.AtomicReference<List<OwnedReceiver>>(emptyList())
        ownedReceivers.compute(key) { _, oldList ->
            val toRemove = ArrayList<OwnedReceiver>()
            val newList = CopyOnWriteArrayList<OwnedReceiver>()
            if (oldList != null) {
                for (reg in oldList) {
                    val regOwner = reg.ownerRef.get()
                    // Keep live registrations that belong to a different owner. Remove stale
                    // owners (collected) and any previous registration for the same owner/key
                    // so the same hook target only has one receiver at a time.
                    if (regOwner != null && regOwner !== owner) {
                        newList.add(reg)
                    } else {
                        reg.receiver.markInactive()
                        toRemove.add(reg)
                    }
                }
            }
            newList.add(newReg)
            removedRef.set(toRemove)
            newList
        }

        // Unregister whatever the atomic update displaced. This is safe to do outside the
        // compute because the map already reflects the new state.
        for (reg in removedRef.get()) {
            releaseReceiver(reg.contextRef, reg.receiver)
        }

        return try {
            context.registerReceiver(receiver, filter, permission, null, flags)

            // Another thread may have replaced this same-owner registration while this thread
            // was inside registerReceiver. If newReg is no longer in the map, we are the loser
            // and must self-unregister so the winner is the only tracked receiver.
            val stillTracked = ownedReceivers[key]?.any { it === newReg } == true
            if (!stillTracked) {
                receiver.markInactive()
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Throwable) {
                    // Already unregistered or Context is gone; the winner's cleanup has already
                    // handled it.
                }
            }

            receiver
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            // Framework registration failed; undo the map entry so we do not keep a dead
            // receiver around and can retry on the next hook.
            ownedReceivers.compute(key) { _, list ->
                val newList = list?.let { CopyOnWriteArrayList(it) }
                if (newList?.remove(newReg) == true) {
                    receiver.markInactive()
                }
                if (newList.isNullOrEmpty()) null else newList
            }
            receiver
        }
    }

    /** Unregisters and removes the receiver owned by [owner] under [key], if one exists. */
    @JvmStatic
    @JvmOverloads
    fun unregisterOwnedReceiver(
        owner: Any,
        key: String,
        expectedReceiver: BroadcastReceiver? = null
    ) {
        val removedRef = java.util.concurrent.atomic.AtomicReference<OwnedReceiver?>(null)
        ownedReceivers.compute(key) { _, list ->
            if (list == null) return@compute null
            val newList = CopyOnWriteArrayList<OwnedReceiver>()
            for (reg in list) {
                val regOwner = reg.ownerRef.get()
                if (regOwner === owner && (expectedReceiver == null || reg.receiver === expectedReceiver)) {
                    reg.receiver.markInactive()
                    removedRef.set(reg)
                } else {
                    newList.add(reg)
                }
            }
            if (newList.isEmpty()) null else newList
        }
        val reg = removedRef.get() ?: return
        releaseReceiver(reg.contextRef, reg.receiver)
    }

    private class ModuleRegistration(
        val key: String,
        val cleanup: Runnable,
        val generation: Long = moduleRegistrationGeneration.incrementAndGet(),
        val state: AtomicReference<RegistrationState> = AtomicReference(RegistrationState.PENDING_REGISTER)
    )

    private val moduleRegistrations = ConcurrentHashMap<String, ModuleRegistration>()
    private val moduleRegistrationGeneration = AtomicLong(0)

    private const val MAX_STALE_MODULE_REGISTRATIONS = 3
    private val staleModuleRegistrations = ConcurrentHashMap<String, ConcurrentLinkedDeque<ModuleRegistration>>()

    /**
     * Records [cleanup] under [key] and runs whatever cleanup was recorded there before.
     *
     * The general form of [registerModuleReceiver], for registrations that are not broadcast
     * receivers — content observers, listeners added to ROM objects. Call it immediately after
     * registering, passing the action that undoes that registration.
     *
     * The same reason applies: a hook target that gets recreated cannot see the registration
     * its predecessor made, so per-instance cleanup silently accumulates live registrations.
     *
     * Replacement is two-stage: the new cleanup is installed first, the old cleanup is run,
     * and failed cleanups are tracked in a bounded stale queue for one retry on the next call.
     */
    @JvmStatic
    fun replaceModuleRegistration(key: String, cleanup: Runnable): Boolean {
        // Retry stale cleanups before touching the active slot.
        retryStaleModuleRegistrations(key)

        val newReg = ModuleRegistration(key, cleanup)

        val previous = moduleRegistrations.put(key, newReg)

        if (previous != null) {
            previous.state.set(RegistrationState.PENDING_UNREGISTER)
            if (!runModuleCleanup(previous)) {
                recordStaleModuleRegistration(key, previous)
            } else {
                previous.state.set(RegistrationState.RELEASED)
            }
        }

        // A concurrent replacement may have installed another registration while we were
        // running the previous cleanup. Only mark [newReg] active if it is still current.
        val stillCurrent = moduleRegistrations[key]
        if (stillCurrent !== newReg) {
            // The winner is responsible for [newReg] (it is the winner's previous cleanup).
            return false
        }

        newReg.state.set(RegistrationState.ACTIVE)
        return true
    }

    private fun runModuleCleanup(reg: ModuleRegistration): Boolean {
        return try {
            reg.cleanup.run()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun recordStaleModuleRegistration(key: String, reg: ModuleRegistration) {
        reg.state.set(RegistrationState.STALE)
        staleModuleRegistrations.compute(key) { _, queue ->
            val newQueue = ConcurrentLinkedDeque(queue ?: emptyList())
            if (newQueue.size >= MAX_STALE_MODULE_REGISTRATIONS) {
                val oldest = newQueue.pollFirst()
                if (oldest != null) {
                    if (runModuleCleanup(oldest)) {
                        oldest.state.set(RegistrationState.RELEASED)
                    } else {
                        oldest.state.set(RegistrationState.RELEASED)
                        HookDiagnostics.record(
                            processName(),
                            HookDiagnostics.Kind.RECEIVER,
                            "ModuleRegistrationRegistry",
                            reg.cleanup.javaClass.name,
                            key,
                            HookDiagnostics.Status.RECEIVER_STALE_DROPPED,
                            "stale cleanup evicted due to bounded queue",
                        )
                    }
                }
            }
            newQueue.addLast(reg)
            HookDiagnostics.record(
                processName(),
                HookDiagnostics.Kind.RECEIVER,
                "ModuleRegistrationRegistry",
                reg.cleanup.javaClass.name,
                key,
                HookDiagnostics.Status.RECEIVER_UNREGISTER_FAILED,
                "cleanup moved to stale queue",
            )
            newQueue
        }
    }

    private fun retryStaleModuleRegistrations(key: String) {
        staleModuleRegistrations.compute(key) { _, queue ->
            if (queue == null) return@compute null
            val stillStale = ConcurrentLinkedDeque<ModuleRegistration>()
            for (reg in queue) {
                if (reg.state.get() == RegistrationState.RELEASED) continue
                if (runModuleCleanup(reg)) {
                    reg.state.set(RegistrationState.RELEASED)
                } else if (stillStale.size < MAX_STALE_MODULE_REGISTRATIONS) {
                    reg.state.set(RegistrationState.STALE)
                    stillStale.addLast(reg)
                } else {
                    reg.state.set(RegistrationState.RELEASED)
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.RECEIVER,
                        "ModuleRegistrationRegistry",
                        reg.cleanup.javaClass.name,
                        key,
                        HookDiagnostics.Status.RECEIVER_STALE_DROPPED,
                        "stale cleanup dropped on retry due to bounded queue",
                    )
                }
            }
            if (stillStale.isEmpty()) null else stillStale
        }
    }

    private fun releaseReceiver(contextRef: WeakReference<Context>, receiver: BroadcastReceiver) {
        val context = contextRef.get() ?: return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Throwable) {
            // Already gone with its context, or never completed registration.
        }
    }
    private fun processName() = HookDiagnostics.currentProcessName
        ?: ModuleHelper.currentPackageName
        ?: Process.myPid().toString()
}
