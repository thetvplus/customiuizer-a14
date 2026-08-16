package tv.withaibuild.customiuizer.mods.utils

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped state for the battery / temperature monitor.
 *
 * The monitor is tied to the lifetime of a single `NetworkSpeedController` instance. When
 * SystemUI recreates the controller, a new generation starts and the previous generation's
 * handlers must stop scheduling ticks and stop posting UI updates.
 *
 * This class has no Android dependencies and is unit-testable.
 */
internal class DeviceInfoMonitorState {

    private data class IconState(val show: Boolean, val text: String)

    private val handlerIdGenerator = AtomicLong(0L)

    @Volatile
    var activeBgHandlerId: Long = -1
        private set

    @Volatile
    var activeMainHandlerId: Long = -1
        private set

    @Volatile
    var screenOn: Boolean = true

    val consecutiveFailCount = AtomicInteger(0)

    private val lastBattery = AtomicReference(IconState(false, ""))
    private val lastTemp = AtomicReference(IconState(false, ""))

    /**
     * Start a new handler generation. Returns the id that the new background and main handlers
     * must carry. Any message from a handler with a different id is stale and must be ignored.
     *
     * Resets the last published text so the first tick of the new generation is never suppressed
     * by stale de-duplication state.
     */
    fun startNewGeneration(): Long {
        val next = handlerIdGenerator.incrementAndGet()
        activeBgHandlerId = next
        activeMainHandlerId = next
        lastBattery.set(IconState(false, ""))
        lastTemp.set(IconState(false, ""))
        return next
    }

    /**
     * Returns true if [handlerId] is the current generation's id.
     */
    fun isActive(handlerId: Long): Boolean {
        return handlerId == activeBgHandlerId || handlerId == activeMainHandlerId
    }

    /**
     * Returns true if [handlerId] is the current background handler id.
     */
    fun isActiveBg(handlerId: Long): Boolean = handlerId == activeBgHandlerId

    /**
     * Returns true if [handlerId] is the current main handler id.
     */
    fun isActiveMain(handlerId: Long): Boolean = handlerId == activeMainHandlerId

    /**
     * Stop the monitor. All current handler ids become inactive; any message from an old handler
     * is dropped. The published text is also reset so a later start does not de-duplicate against
     * stale state.
     */
    fun stop() {
        activeBgHandlerId = -1
        activeMainHandlerId = -1
        lastBattery.set(IconState(false, ""))
        lastTemp.set(IconState(false, ""))
    }

    /**
     * Reset the failure back-off count.
     */
    fun resetFailCount() {
        consecutiveFailCount.set(0)
    }

    /**
     * Increment the failure back-off count.
     */
    fun bumpFailCount() {
        consecutiveFailCount.incrementAndGet()
    }

    /**
     * Compute the next tick delay based on the current failure back-off count.
     */
    fun calculateDelay(baseMs: Long, maxMs: Long): Long {
        val count = consecutiveFailCount.get()
        if (count <= 0) return baseMs
        val multiplier = 1L shl count.coerceAtMost(5)
        return (baseMs * multiplier).coerceAtMost(maxMs)
    }

    /**
     * Returns true if [handlerId] is the active main handler and the proposed update differs
     * from the last published state for [type]. Does not update the last published state; the
     * main handler calls [commitPublished] only after it accepts the message.
     */
    fun shouldPublish(handlerId: Long, type: Int, show: Boolean, text: String): Boolean {
        if (!isActiveMain(handlerId)) return false
        val new = IconState(show, text)
        val current = if (type == 91) lastBattery.get() else lastTemp.get()
        return current != new
    }

    /**
     * Records that an update has been accepted and applied by the current main handler.
     * Returns true if [handlerId] is the active main handler and the state was updated.
     */
    fun commitPublished(handlerId: Long, type: Int, show: Boolean, text: String): Boolean {
        if (!isActiveMain(handlerId)) return false
        val new = IconState(show, text)
        if (type == 91) {
            lastBattery.set(new)
        } else if (type == 92) {
            lastTemp.set(new)
        }
        return true
    }

    /**
     * Last published state for diagnostics. Returns the current pair of
     * `(show, text)` for [type].
     */
    fun getLastPublished(type: Int): Pair<Boolean, String> {
        val state = if (type == 91) lastBattery.get() else lastTemp.get()
        return state.show to state.text
    }
}
