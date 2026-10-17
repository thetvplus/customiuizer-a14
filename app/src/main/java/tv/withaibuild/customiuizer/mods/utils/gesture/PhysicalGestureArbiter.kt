package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Enforces a single authoritative owner for each physical touch gesture.
 *
 * Status Bar and Control Center share one arbiter so the same physical stream cannot
 * produce duplicated brightness, volume, double-tap or long-press side effects.
 *
 * Tokens are only created for a valid [GestureAction.DOWN]. All other events must use
 * an already-held token via [isOwner].
 */
class PhysicalGestureArbiter {

    data class Token(
        val downTime: Long,
        val deviceId: Int,
        val source: Int,
    )

    /**
     * Hard upper bound on the number of physical gesture tokens held at once.
     *
     * Exceeding this is treated as a stale/corrupted state and triggers cleanup
     * before any further DOWN is accepted.
     */
    val maxHeldTokens: Int = MAX_HELD_TOKENS

    private val owners = mutableMapOf<Token, Int>()

    /**
     * Try to claim authority over the physical [event] for [ownerId] on a DOWN.
     *
     * Returns `true` if this owner is the first to acquire the physical token, or if it
     * already owns it. Returns `false` if another owner has already claimed it.
     *
     * This method must only be called for a DOWN that the state machine has already
     * validated as the start of a trackable gesture.
     */
    fun tryAcquireOnDown(ownerId: Int, event: GestureEvent): Boolean {
        val token = tokenOf(event)
        val existing = owners[token]
        if (existing == null) {
            // Safety net for missing UP/CANCEL: a new DOWN implies a new gesture,
            // so any token from a much older downTime is stale.
            reapStaleTokens(event.downTime)

            if (owners.size >= maxHeldTokens) {
                return false
            }

            owners[token] = ownerId
            return true
        }
        return existing == ownerId
    }

    /**
     * Returns `true` if [ownerId] is the current owner of the physical [event].
     *
     * Non-DOWN events must use this check; they are never allowed to create a token.
     */
    fun isOwner(ownerId: Int, event: GestureEvent): Boolean {
        return owners[tokenOf(event)] == ownerId
    }

    /**
     * Release the specific token held by [ownerId] for [event], if any.
     */
    fun release(ownerId: Int, event: GestureEvent) {
        val token = tokenOf(event)
        if (owners[token] == ownerId) {
            owners.remove(token)
        }
    }

    /**
     * Release all tokens held by [ownerId]. Called when the owner is detached or reset.
     */
    fun releaseOwner(ownerId: Int) {
        val iterator = owners.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value == ownerId) {
                iterator.remove()
            }
        }
    }

    /**
     * Remove tokens that are older than [STALE_TOKEN_AGE_MS] relative to [nowDownTime].
     *
     * This is the safety net for missing UP/CANCEL events: a new physical gesture
     * should never share the same downTime as an old one, so any token old enough
     * to outlive a reasonable gesture is considered stale.
     */
    fun reapStaleTokens(nowDownTime: Long) {
        val cutoff = nowDownTime - STALE_TOKEN_AGE_MS
        val iterator = owners.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.downTime < cutoff) {
                iterator.remove()
            }
        }
    }

    /** Drop every held token, e.g. when the ClassLoader is torn down. */
    fun releaseAll() {
        owners.clear()
    }

    /** Diagnostic only: the owner for a held token, or `null` if none. */
    internal fun ownerOf(token: Token): Int? = owners[token]

    /** Diagnostic only: the total number of held physical tokens. */
    internal fun heldTokenCount(): Int = owners.size

    /** Diagnostic only: the number of tokens held by the given owner. */
    internal fun tokensForOwner(ownerId: Int): Int = owners.count { it.value == ownerId }

    /** Diagnostic only: a snapshot of the current token map. */
    internal fun heldTokens(): Map<Token, Int> = owners.toMap()

    private fun tokenOf(event: GestureEvent): Token =
        Token(event.downTime, event.deviceId, event.source)

    companion object {
        const val MAX_HELD_TOKENS = 16
        const val STALE_TOKEN_AGE_MS = 10_000L
    }
}
