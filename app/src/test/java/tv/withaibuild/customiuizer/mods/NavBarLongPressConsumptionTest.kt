package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

class NavBarLongPressConsumptionTest {
    @Test
    fun handledPressConsumesDelayedDownRepeatAndUpThenClears() {
        val state = NavBarLongPressConsumption()
        state.markHandled(4, 123L)
        for (action in listOf(0, 0, 1)) {
            val chain = FakeChain()
            assertEquals(-1L, state.dispatch(chain, 4, 123L, action))
            assertEquals(0, chain.proceedCount)
        }
        val next = FakeChain()
        assertEquals(0L, state.dispatch(next, 4, 124L, 0))
        assertEquals(1, next.proceedCount)
        assertFalse(state.consume(4, 123L, false))
    }

    @Test
    fun unmatchedKeyTimeAndNonKeyActionPassExactlyOnceWithoutClearingTheMarker() {
        val state = NavBarLongPressConsumption()
        state.markHandled(3, 234L)
        for (event in listOf(Triple(4, 234L, 1), Triple(3, 235L, 1), Triple(3, 234L, 2), Triple(187, 234L, 1))) {
            val chain = FakeChain()
            assertEquals(0L, state.dispatch(chain, event.first, event.second, event.third))
            assertEquals(1, chain.proceedCount)
        }
        assertTrue(state.consume(3, 234L, true))
    }

    @Test
    fun zeroAndMaximumDownTimesDoNotCollideWithTheEmptySlot() {
        val state = NavBarLongPressConsumption()
        for (time in listOf(0L, NavBarLongPressConsumption.MAX_DOWN_TIME)) {
            for (key in listOf(3, 4, 82)) {
                assertTrue(state.canTrack(key, time))
                state.markHandled(key, time)
                assertTrue(state.consume(key, time, true))
                assertFalse(state.consume(key, time, true))
            }
        }
    }

    @Test
    fun invalidKeyOrDownTimeDoesNotReplaceAValidHandledPress() {
        val state = NavBarLongPressConsumption()
        state.markHandled(82, 345L)
        for ((key, time) in listOf(187 to 345L, 4 to -1L, 3 to (NavBarLongPressConsumption.MAX_DOWN_TIME + 1L))) {
            assertFalse(state.canTrack(key, time))
            state.markHandled(key, time)
            assertFalse(state.consume(key, time, true))
        }
        assertTrue(state.consume(82, 345L, true))
    }

    @Test
    fun newHandledPressReplacesTheBoundedOldMarker() {
        val state = NavBarLongPressConsumption()
        state.markHandled(4, 456L)
        state.markHandled(3, 457L)
        assertFalse(state.consume(4, 456L, true))
        assertTrue(state.consume(3, 457L, true))
    }

    @Test
    fun staleMarkerDoesNotConsumeNewShortPressOrUnrelatedKeys() {
        val state = NavBarLongPressConsumption()
        state.markHandled(4, 567L) // Its key-up was lost when an action turned the screen off.
        for (key in listOf(4, 24, 25, 26, 187)) {
            val chain = FakeChain()
            assertEquals(0L, state.dispatch(chain, key, 1000L, 1))
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun unhandledOriginalFailurePropagatesExactlyOnce() {
        val state = NavBarLongPressConsumption()
        val failure = IllegalStateException("native dispatch failure")
        val chain = FakeChain(failure)
        try {
            state.dispatch(chain, 4, 678L, 1)
            throw AssertionError("native failure was swallowed")
        } catch (actual: Throwable) {
            assertSame(failure, actual)
        }
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun noMarkerDispatchDoesNotReadEventMetadata() {
        val state = NavBarLongPressConsumption()
        val chain = FakeChain(metadataFailure = AssertionError("unhandled dispatch read event"))
        assertEquals(0L, Controls.interceptNavBarKeyDispatch(chain, state))
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun matchingMenuUpCancelsPreloadExactlyOnceWithoutCallingTheOriginalUp() {
        var cleanups = 0
        val state = NavBarLongPressConsumption { cleanups++ }
        state.markHandled(82, 789L)
        val down = FakeChain()
        assertEquals(-1L, state.dispatch(down, 82, 789L, 0))
        assertEquals(0, cleanups)
        val up = FakeChain()
        assertEquals(-1L, state.dispatch(up, 82, 789L, 1))
        assertEquals(1, cleanups)
        assertEquals(0, up.proceedCount)
        assertFalse(state.hasHandledPress())
        val duplicate = FakeChain()
        assertEquals(0L, state.dispatch(duplicate, 82, 789L, 1))
        assertEquals(1, cleanups)
        assertEquals(1, duplicate.proceedCount)
    }

    @Test
    fun unmatchedMenuUpAndOtherMatchedKeysDoNotCancelMenuPreload() {
        var cleanups = 0
        val state = NavBarLongPressConsumption { cleanups++ }
        state.markHandled(82, 890L)
        val unmatched = FakeChain()
        assertEquals(0L, state.dispatch(unmatched, 82, 891L, 1))
        assertEquals(0, cleanups)
        for (key in listOf(3, 4)) {
            state.markHandled(key, 892L)
            assertEquals(-1L, state.dispatch(FakeChain(), key, 892L, 1))
        }
        assertEquals(0, cleanups)
    }

    @Test
    fun ordinaryMenuCleanupFailureDoesNotReplayTheSuccessfulOriginalAction() {
        var cleanups = 0
        val state = NavBarLongPressConsumption {
            cleanups++
            throw InvocationTargetException(IllegalStateException("cancel failed"))
        }
        state.markHandled(82, 901L)
        val up = FakeChain()
        assertEquals(-1L, state.dispatch(up, 82, 901L, 1))
        assertEquals(0, up.proceedCount)
        assertEquals(1, cleanups)
        assertFalse(state.hasHandledPress())
    }

    @Test
    fun wrappedFatalMenuCleanupFailurePropagatesWithoutOriginalReplay() {
        for (fatal in listOf(OutOfMemoryError("oom"), ThreadDeath(), InternalError("vm"))) {
            for (wrapper in listOf(InvocationTargetException(fatal), ExecutionException(fatal))) {
                val state = NavBarLongPressConsumption { throw wrapper }
                state.markHandled(82, 912L)
                val up = FakeChain()
                try {
                    state.dispatch(up, 82, 912L, 1)
                    throw AssertionError("fatal cleanup error was swallowed")
                } catch (actual: Throwable) {
                    assertSame(fatal, actual)
                }
                assertEquals(0, up.proceedCount)
                assertFalse(state.hasHandledPress())
            }
        }
    }

    private class FakeChain(
        private val failure: Throwable? = null,
        private val metadataFailure: Throwable? = null,
    ) : XposedInterface.Chain {
        var proceedCount = 0
        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? { metadataFailure?.let { throw it }; return null }
        override fun proceed(): Any? { proceedCount++; failure?.let { throw it }; return 0L }
        override fun proceed(args: Array<Any?>): Any? = error("not used")
        override fun proceedWith(p0: Any): Any? = error("not used")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used")
    }
}
