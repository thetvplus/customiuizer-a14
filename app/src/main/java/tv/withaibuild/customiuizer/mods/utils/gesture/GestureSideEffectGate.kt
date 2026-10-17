package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Deduplicates business side-effects of a physical touch event per owner.
 *
 * The gate is not responsible for state transitions; it only decides which commands
 * from the pure state machine are allowed to reach the side-effect executor.
 */
class GestureSideEffectGate(
    private val maxFingerprints: Int = 32,
) {

    private val order = ArrayDeque<OwnerFingerprint>(maxFingerprints)
    private val seen = LinkedHashSet<OwnerFingerprint>(maxFingerprints)

    /**
     * Entries that are allowed to update state and execute business side-effects.
     *
     * `STATUS_BAR_INTERCEPT` is intentionally absent: it may observe the event but must
     * defer state changes and side-effects to `STATUS_BAR_TOUCH`.
     */
    private val effectEntries: Set<GestureEntry> = setOf(
        GestureEntry.STATUS_BAR_TOUCH,
        GestureEntry.CONTROL_CENTER_TOUCH,
    )

    private fun isBusinessEffect(command: GestureCommand): Boolean = when (command) {
        is GestureCommand.ApplyTemporaryBrightness,
        is GestureCommand.AdjustVolume,
        is GestureCommand.CommitBrightness,
        is GestureCommand.TriggerDoubleTap,
        is GestureCommand.TriggerLongPress -> true
        else -> false
    }

    private fun fingerprint(ownerId: Int, event: GestureEvent): OwnerFingerprint =
        OwnerFingerprint(
            ownerId = ownerId,
            fingerprint = GestureEventFingerprint(
                downTime = event.downTime,
                eventTime = event.eventTime,
                actionMasked = event.actionMasked,
                pointerCount = event.pointerCount,
                deviceId = event.deviceId,
                source = event.source,
            ),
        )

    /**
     * Filters [commands] for the given [entry], [ownerId] and [event].
     *
     * Returns the commands that are allowed to execute. Business side-effects are
     * dropped for non-effect entries and deduplicated by owner and physical event identity.
     */
    fun filter(
        entry: GestureEntry,
        ownerId: Int,
        event: GestureEvent,
        commands: List<GestureCommand>,
    ): List<GestureCommand> {
        if (commands.isEmpty()) return commands
        if (entry !in effectEntries) return emptyList()

        val hasBusiness = commands.any(::isBusinessEffect)
        if (!hasBusiness) return commands

        val fp = fingerprint(ownerId, event)
        if (fp in seen) return emptyList()

        if (order.size >= maxFingerprints) {
            val oldest = order.removeFirst()
            seen.remove(oldest)
        }
        order.addLast(fp)
        seen.add(fp)

        return commands
    }

    /** Clears every fingerprint held for [ownerId], e.g. when the view is detached. */
    fun clearOwner(ownerId: Int) {
        order.removeIf { it.ownerId == ownerId }
        seen.removeIf { it.ownerId == ownerId }
    }

    /** Clears all stored fingerprints, e.g. when the ClassLoader is torn down. */
    fun clear() {
        order.clear()
        seen.clear()
    }

    /**
     * A fingerprint is scoped by [ownerId] so that detaching one owner does not remove
     * another owner's deduplication records.
     */
    data class OwnerFingerprint(
        val ownerId: Int,
        val fingerprint: GestureEventFingerprint,
    )
}
