package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Lightweight identity of a physical touch event.
 *
 * Two events with the same [downTime], [eventTime], [actionMasked], [pointerCount],
 * [deviceId] and [source] are the same physical event even if they are delivered through
 * different [GestureEntry] points or different owners.
 *
 * Physical identity is therefore decoupled from [ownerId]; the [entry] and [ownerId] are
 * used to find the right state machine, but they do not bypass the cross-owner
 * side-effect deduplication performed by [PhysicalGestureArbiter] and [GestureSideEffectGate].
 */
data class GestureEventFingerprint(
    val downTime: Long,
    val eventTime: Long,
    val actionMasked: Int,
    val pointerCount: Int,
    val deviceId: Int,
    val source: Int,
)
