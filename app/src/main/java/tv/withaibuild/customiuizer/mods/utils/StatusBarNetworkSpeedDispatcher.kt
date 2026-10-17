package tv.withaibuild.customiuizer.mods.utils

import java.util.concurrent.atomic.AtomicLong

/**
 * Main-thread dispatcher for the network speed second-row update.
 *
 * The hook that receives network speed payloads may run on a background handler. This dispatcher
 * takes an immutable payload and a sequence number, drops the update if a newer payload has already
 * been applied, and applies the payload to a snapshot of the registry on the caller's thread. The
 * caller must post the [dispatch] call to the SystemUI main looper; this object does not touch
 * Android handlers and is fully testable on the JVM.
 */
object StatusBarNetworkSpeedDispatcher {

    /**
     * Immutable network speed payload. The values are whatever the SystemUI network speed state
     * object exposes; they are not interpreted on the background thread.
     */
    data class NetworkSpeedPayload(
        val number: Any?,
        val unit: Any?,
        val visible: Any?,
    )

    /**
     * Apply [payload] to the current registry snapshot.
     *
     * [seq] is a monotonically increasing sequence number generated on the background thread.
     * [lastApplied] is the last sequence number that reached the main thread. If [seq] is older
     * than [lastApplied], the payload is dropped. Otherwise [lastApplied] is updated and the
     * [applier] is called for every state in the current registry snapshot.
     */
    fun <O : Any, R : Any> dispatch(
        payload: NetworkSpeedPayload,
        seq: Long,
        lastApplied: AtomicLong,
        registry: StatusBarDisplayRegistry<O, R>,
        applier: (StatusBarDisplayState<O, R>, NetworkSpeedPayload) -> Unit,
    ) {
        if (seq < lastApplied.get()) return
        lastApplied.set(seq)
        for (state in registry.allStatesSnapshot()) {
            applier(state, payload)
        }
    }
}
