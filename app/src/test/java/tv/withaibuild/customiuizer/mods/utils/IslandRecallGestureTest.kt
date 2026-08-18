package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandRecallGestureTest {

    private val config = IslandRecallGesture.configFromSlop(16)

    @Test
    fun shortUpwardTriggers() {
        var snap = IslandRecallGesture.onDown(0, 50f, 80f)
        snap = IslandRecallGesture.onMove(snap, 0, 50f, 80f - config.minUpDistancePx, config)
        assertTrue(IslandRecallGesture.isTriggered(snap))
    }

    @Test
    fun mediumUpwardTriggers() {
        var snap = IslandRecallGesture.onDown(0, 50f, 120f)
        snap = IslandRecallGesture.onMove(snap, 0, 52f, 40f, config)
        assertTrue(IslandRecallGesture.isTriggered(snap))
    }

    @Test
    fun tapDoesNotTrigger() {
        var snap = IslandRecallGesture.onDown(0, 50f, 80f)
        snap = IslandRecallGesture.onMove(snap, 0, 51f, 79f, config)
        assertFalse(IslandRecallGesture.isTriggered(snap))
    }

    @Test
    fun horizontalDoesNotTrigger() {
        var snap = IslandRecallGesture.onDown(0, 50f, 80f)
        snap = IslandRecallGesture.onMove(snap, 0, 200f, 70f, config)
        assertFalse(IslandRecallGesture.isTriggered(snap))
    }

    @Test
    fun downwardDoesNotTrigger() {
        var snap = IslandRecallGesture.onDown(0, 50f, 80f)
        snap = IslandRecallGesture.onMove(snap, 0, 50f, 160f, config)
        assertFalse(IslandRecallGesture.isTriggered(snap))
    }

    @Test
    fun jitterBelowSlopDoesNotTrigger() {
        var snap = IslandRecallGesture.onDown(0, 50f, 80f)
        snap = IslandRecallGesture.onMove(snap, 0, 50f, 80f - (config.scaledTouchSlopPx - 1), config)
        assertFalse(IslandRecallGesture.isTriggered(snap))
    }

    @Test
    fun diagonalUpwardDominantTriggers() {
        var snap = IslandRecallGesture.onDown(0, 50f, 120f)
        snap = IslandRecallGesture.onMove(snap, 0, 70f, 40f, config)
        assertTrue(IslandRecallGesture.isTriggered(snap))
    }

    @Test
    fun cancelResets() {
        var snap = IslandRecallGesture.onDown(0, 50f, 80f)
        snap = IslandRecallGesture.onMove(snap, 0, 50f, 20f, config)
        assertTrue(IslandRecallGesture.isTriggered(snap))
        snap = IslandRecallGesture.onCancel()
        assertEquals(IslandRecallGesture.STATE_IDLE, snap.state)
        assertFalse(IslandRecallGesture.isTriggered(snap))
    }

    @Test
    fun secondMoveAfterTriggerDoesNotRetrigger() {
        var snap = IslandRecallGesture.onDown(0, 50f, 120f)
        snap = IslandRecallGesture.onMove(snap, 0, 50f, 40f, config)
        assertTrue(IslandRecallGesture.isTriggered(snap))
        val after = IslandRecallGesture.onMove(snap, 0, 50f, 10f, config)
        assertEquals(IslandRecallGesture.STATE_TRIGGERED, after.state)
        assertEquals(snap.pointerId, after.pointerId)
    }

    @Test
    fun newDownStartsFreshGesture() {
        val first = IslandRecallGesture.onMove(
            IslandRecallGesture.onDown(0, 50f, 120f),
            0,
            50f,
            40f,
            config,
        )
        assertTrue(IslandRecallGesture.isTriggered(first))
        val second = IslandRecallGesture.onDown(1, 60f, 90f)
        assertEquals(IslandRecallGesture.STATE_TRACKING, second.state)
        assertFalse(IslandRecallGesture.isTriggered(second))
    }
}
