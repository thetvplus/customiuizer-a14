package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.*
import org.junit.Test

class PhysicalGestureArbiterTest {

    private fun event(
        downTime: Long = 1000L,
        eventTime: Long = downTime,
        actionMasked: Int = GestureAction.DOWN,
        ownerId: Int = 1,
        pointerCount: Int = 1,
    ) = GestureEvent(
        entry = GestureEntry.STATUS_BAR_TOUCH,
        actionMasked = actionMasked,
        downTime = downTime,
        eventTime = eventTime,
        x = 0f,
        y = 0f,
        pointerCount = pointerCount,
        ownerId = ownerId,
        deviceId = 0,
        source = 0,
    )

    @Test
    fun firstDownAcquiresToken() {
        val arbiter = PhysicalGestureArbiter()
        val event = event()
        assertTrue(arbiter.tryAcquireOnDown(1, event))
        assertTrue(arbiter.isOwner(1, event))
    }

    @Test
    fun sameDownReacquiresForSameOwner() {
        val arbiter = PhysicalGestureArbiter()
        val event = event()
        assertTrue(arbiter.tryAcquireOnDown(1, event))
        assertTrue(arbiter.tryAcquireOnDown(1, event))
        assertFalse(arbiter.tryAcquireOnDown(2, event))
    }

    @Test
    fun releaseRemovesToken() {
        val arbiter = PhysicalGestureArbiter()
        val e = event()
        arbiter.tryAcquireOnDown(1, e)
        arbiter.release(1, e)
        assertFalse(arbiter.isOwner(1, e))
    }

    @Test
    fun releaseOwnerRemovesAllOwnerTokens() {
        val arbiter = PhysicalGestureArbiter()
        arbiter.tryAcquireOnDown(1, event(downTime = 1000L))
        arbiter.tryAcquireOnDown(1, event(downTime = 2000L))
        assertEquals(2, arbiter.heldTokenCount())
        arbiter.releaseOwner(1)
        assertEquals(0, arbiter.heldTokenCount())
    }

    @Test
    fun maxHeldTokensRejectsFurtherDowns() {
        val arbiter = PhysicalGestureArbiter()
        repeat(PhysicalGestureArbiter.MAX_HELD_TOKENS) { index ->
            assertTrue(arbiter.tryAcquireOnDown(index, event(downTime = 1000L + index)))
        }
        assertFalse(arbiter.tryAcquireOnDown(99, event(downTime = 10_000L)))
    }

    @Test
    fun staleTokensAreReapedOnNewDown() {
        val arbiter = PhysicalGestureArbiter()
        val oldTime = 1000L
        val newTime = oldTime + PhysicalGestureArbiter.STALE_TOKEN_AGE_MS + 1
        arbiter.tryAcquireOnDown(1, event(downTime = oldTime))
        assertEquals(1, arbiter.heldTokenCount())
        assertTrue(arbiter.tryAcquireOnDown(2, event(downTime = newTime)))
        assertEquals(1, arbiter.heldTokenCount())
        assertFalse(arbiter.isOwner(1, event(downTime = oldTime)))
    }

    @Test
    fun nonStaleTokensSurviveReap() {
        val arbiter = PhysicalGestureArbiter()
        val t1 = 1000L
        val t2 = t1 + PhysicalGestureArbiter.STALE_TOKEN_AGE_MS - 1
        arbiter.tryAcquireOnDown(1, event(downTime = t1))
        assertTrue(arbiter.tryAcquireOnDown(2, event(downTime = t2)))
        assertEquals(2, arbiter.heldTokenCount())
    }
}
