package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSideEffectGateTest {

    private val gate = GestureSideEffectGate()

    private fun event(
        action: Int,
        x: Float = 0f,
        y: Float = 0f,
        eventTime: Long = 0L,
        downTime: Long = 0L,
        pointerCount: Int = 1,
        ownerId: Int = 1,
        entry: GestureEntry = GestureEntry.STATUS_BAR_TOUCH,
        deviceId: Int = 0,
        source: Int = 0,
    ) = GestureEvent(
        entry = entry,
        actionMasked = action,
        downTime = downTime,
        eventTime = eventTime,
        x = x,
        y = y,
        pointerCount = pointerCount,
        ownerId = ownerId,
        deviceId = deviceId,
        source = source,
    )

    @Test
    fun statusBarTouch_allowsBusinessEffects() {
        val e = event(GestureAction.UP, eventTime = 100L)
        val commands = listOf<GestureCommand>(
            GestureCommand.TriggerDoubleTap(DoubleTapPosition.CENTER, 2),
            GestureCommand.Reset,
        )
        val result = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e.ownerId, e, commands)
        assertEquals(commands, result)
    }

    @Test
    fun controlCenterTouch_allowsBusinessEffects() {
        val e = event(GestureAction.MOVE, eventTime = 50L)
        val commands = listOf<GestureCommand>(GestureCommand.AdjustVolume(true))
        val result = gate.filter(GestureEntry.CONTROL_CENTER_TOUCH, e.ownerId, e, commands)
        assertEquals(commands, result)
    }

    @Test
    fun statusBarIntercept_dropsAllBusinessAndState() {
        val e = event(GestureAction.DOWN, eventTime = 0L)
        val commands = listOf<GestureCommand>(
            GestureCommand.BeginTracking,
            GestureCommand.TriggerDoubleTap(DoubleTapPosition.CENTER, 2),
        )
        val result = gate.filter(GestureEntry.STATUS_BAR_INTERCEPT, e.ownerId, e, commands)
        assertTrue(result.isEmpty())
    }

    @Test
    fun sameEventFromTouchThenTouch_deduped() {
        val e = event(GestureAction.MOVE, eventTime = 50L)
        val commands = listOf<GestureCommand>(GestureCommand.ApplyTemporaryBrightness(0.5f))
        val first = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e.ownerId, e, commands)
        val second = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e.ownerId, e, commands)
        assertEquals(commands, first)
        assertTrue(second.isEmpty())
    }

    @Test
    fun sameEventFromInterceptThenTouch_allows() {
        val e = event(GestureAction.DOWN, eventTime = 0L)
        val commands = listOf<GestureCommand>(GestureCommand.BeginTracking)
        // Intercept does not record a fingerprint because it has no business effect here.
        val interceptResult = gate.filter(GestureEntry.STATUS_BAR_INTERCEPT, e.ownerId, e, commands)
        assertTrue(interceptResult.isEmpty())
        val touchResult = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e.ownerId, e, commands)
        assertEquals(commands, touchResult)
    }

    @Test
    fun samePhysicalEvent_crossOwner_isIndependent() {
        val e1 = event(GestureAction.UP, eventTime = 100L, ownerId = 1, deviceId = 1, source = 256)
        val e2 = event(GestureAction.UP, eventTime = 100L, ownerId = 2, deviceId = 1, source = 256)
        val commands = listOf<GestureCommand>(GestureCommand.TriggerDoubleTap(DoubleTapPosition.CENTER, 2))
        val first = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e1.ownerId, e1, commands)
        val second = gate.filter(GestureEntry.CONTROL_CENTER_TOUCH, e2.ownerId, e2, commands)
        assertEquals(commands, first)
        assertEquals(commands, second)
    }

    @Test
    fun differentPhysicalDevice_sameTimestamp_isNotDeduped() {
        val e1 = event(GestureAction.UP, eventTime = 100L, ownerId = 1, deviceId = 1, source = 256)
        val e2 = event(GestureAction.UP, eventTime = 100L, ownerId = 1, deviceId = 2, source = 256)
        val commands = listOf<GestureCommand>(GestureCommand.TriggerDoubleTap(DoubleTapPosition.CENTER, 2))
        val first = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e1.ownerId, e1, commands)
        val second = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e2.ownerId, e2, commands)
        assertEquals(commands, first)
        assertEquals(commands, second)
    }

    @Test
    fun nonBusinessCommands_allowedWithoutDedup() {
        val e = event(GestureAction.CANCEL, eventTime = 100L)
        val commands = listOf<GestureCommand>(GestureCommand.Reset)
        val first = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e.ownerId, e, commands)
        val second = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e.ownerId, e, commands)
        assertEquals(commands, first)
        assertEquals(commands, second)
    }

    @Test
    fun capacity_evictsOldest() {
        val smallGate = GestureSideEffectGate(maxFingerprints = 2)
        val commands = listOf<GestureCommand>(GestureCommand.AdjustVolume(true))
        val e1 = event(GestureAction.MOVE, eventTime = 10L)
        val e2 = event(GestureAction.MOVE, eventTime = 20L)
        val e3 = event(GestureAction.MOVE, eventTime = 30L)
        smallGate.filter(GestureEntry.STATUS_BAR_TOUCH, e1.ownerId, e1, commands)
        smallGate.filter(GestureEntry.STATUS_BAR_TOUCH, e2.ownerId, e2, commands)
        smallGate.filter(GestureEntry.STATUS_BAR_TOUCH, e3.ownerId, e3, commands)
        // e1 should have been evicted.
        val again = smallGate.filter(GestureEntry.STATUS_BAR_TOUCH, e1.ownerId, e1, commands)
        assertEquals(commands, again)
    }

    @Test
    fun clear_removesAllFingerprints() {
        val e = event(GestureAction.MOVE, eventTime = 50L)
        val commands = listOf<GestureCommand>(GestureCommand.ApplyTemporaryBrightness(0.5f))
        gate.filter(GestureEntry.STATUS_BAR_TOUCH, e.ownerId, e, commands)
        gate.clear()
        val again = gate.filter(GestureEntry.STATUS_BAR_TOUCH, e.ownerId, e, commands)
        assertEquals(commands, again)
    }

    @Test
    fun clearOwner_removesOnlyMatchingOwner() {
        val e1 = event(GestureAction.MOVE, eventTime = 50L, ownerId = 1)
        val e2 = event(GestureAction.MOVE, eventTime = 50L, ownerId = 2)
        val commands = listOf<GestureCommand>(GestureCommand.ApplyTemporaryBrightness(0.5f))

        gate.filter(GestureEntry.STATUS_BAR_TOUCH, e1.ownerId, e1, commands)
        gate.filter(GestureEntry.STATUS_BAR_TOUCH, e2.ownerId, e2, commands)

        gate.clearOwner(1)

        // Owner 1's record was removed, so this event is allowed again.
        assertEquals(commands, gate.filter(GestureEntry.STATUS_BAR_TOUCH, e1.ownerId, e1, commands))
        // Owner 2's record remains, so the same physical event is still deduped.
        assertTrue(gate.filter(GestureEntry.STATUS_BAR_TOUCH, e2.ownerId, e2, commands).isEmpty())
    }
}
