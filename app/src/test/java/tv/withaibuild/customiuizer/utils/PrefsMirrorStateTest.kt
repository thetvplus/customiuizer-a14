package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The orderings the mirror has to survive: a service that binds, dies and binds again while
 * a pass, a delayed retry and a change event from the previous connection are all still in
 * flight. Each is reproduced here directly, because none of them is reachable from a test
 * that only drives a live bind.
 */
class PrefsMirrorStateTest {

    @Test
    fun anUnboundStateOffersNoGenerationToQuote() {
        val state = PrefsMirrorState()

        assertEquals(PrefsMirrorState.NO_GENERATION, state.currentGeneration)
        assertFalse(state.isCurrent(0L))
        assertFalse(state.beginPass(0L))
    }

    @Test
    fun aChangeAfterTheSnapshotWasReadOwesOneFollowUpPass() {
        // The window the follow-up exists for: the pass has read local, the user changes a
        // value, the pass commits the older snapshot over it.
        val state = PrefsMirrorState()
        val generation = state.onBind()

        assertTrue(state.beginPass(generation))
        assertTrue("a change during a pass must be reported as deferred", state.onLocalChange())

        assertTrue(state.claimFollowUpPass(generation))
        assertTrue(state.endPass(generation))
    }

    @Test
    fun aChangeDuringTheFollowUpDoesNotStartAThirdPass() {
        val state = PrefsMirrorState()
        val generation = state.onBind()

        state.beginPass(generation)
        state.onLocalChange()
        assertTrue(state.claimFollowUpPass(generation))

        // Landed during the follow-up.
        state.onLocalChange()

        assertFalse("at most one follow-up", state.claimFollowUpPass(generation))
        assertFalse("the pass must report itself incomplete instead", state.endPass(generation))
    }

    @Test
    fun aKeyRemovedDuringAPassIsCarriedByTheFollowUp() {
        // A removal reaches the listener the same way a write does; the mirror must not
        // treat "no value" as "nothing happened".
        val state = PrefsMirrorState()
        val generation = state.onBind()

        state.beginPass(generation)
        assertTrue(state.onLocalChange())
        assertTrue(state.claimFollowUpPass(generation))
    }

    @Test
    fun aRetryFromADeadServiceDoesNothingWhenItArrives() {
        val state = PrefsMirrorState()
        val generation = state.onBind()
        assertTrue(state.claimRetry(generation))

        state.onUnbind()

        // The delayed body runs now.
        assertFalse(state.isCurrent(generation))
        assertFalse(state.beginPass(generation))
    }

    @Test
    fun aSecondServiceGetsItsOwnGenerationAndItsOwnRetry() {
        val state = PrefsMirrorState()
        val first = state.onBind()
        assertTrue(state.claimRetry(first))
        assertFalse("one retry per bind, not per failure", state.claimRetry(first))

        state.onUnbind()
        val second = state.onBind()

        assertNotEquals(first, second)
        assertTrue("a new connection is allowed its own single retry", state.claimRetry(second))
        assertFalse(state.claimRetry(second))
    }

    @Test
    fun aPassFromTheOldServiceCannotClearTheNewOnesFlag() {
        // The failure this prevents: the settings app tells the user everything arrived,
        // on the word of a pass belonging to a connection that no longer exists.
        val state = PrefsMirrorState()
        val first = state.onBind()
        state.onUnbind()

        state.markUndelivered()
        val second = state.onBind()

        assertFalse(state.clearUndelivered(first))
        assertTrue("the flag must survive a stale clear", state.hasUndeliveredChanges)

        assertTrue(state.clearUndelivered(second))
        assertFalse(state.hasUndeliveredChanges)
    }

    @Test
    fun undeliveredIsMarkedOnceAndSurvivesAnIncompletePass() {
        val state = PrefsMirrorState()

        assertTrue("first change reports the transition", state.markUndelivered())
        assertFalse("later changes must not log again", state.markUndelivered())

        val generation = state.onBind()
        state.beginPass(generation)
        state.onLocalChange()
        state.claimFollowUpPass(generation)
        state.onLocalChange()

        assertFalse(state.endPass(generation))
        assertTrue("an unsettled pass is not a delivery", state.hasUndeliveredChanges)
    }

    @Test
    fun rebindingResetsPassStateSoAStaleRunnerCannotBlockTheNewOne() {
        // A pass that was in flight when the service died leaves passRunning set. If a bind
        // did not clear it, the new connection could never start its own pass.
        val state = PrefsMirrorState()
        val first = state.onBind()
        state.beginPass(first)

        state.onUnbind()
        val second = state.onBind()

        assertTrue(state.beginPass(second))
    }

    @Test
    fun onlyOnePassRunsAtATime() {
        val state = PrefsMirrorState()
        val generation = state.onBind()

        assertTrue(state.beginPass(generation))
        assertFalse("a queued duplicate must not start a second pass", state.beginPass(generation))

        state.endPass(generation)
        assertTrue(state.beginPass(generation))
    }

    @Test
    fun aQuietPassOwesNoFollowUpAndSettlesCleanly() {
        val state = PrefsMirrorState()
        val generation = state.onBind()

        state.beginPass(generation)
        assertFalse(state.claimFollowUpPass(generation))
        assertTrue(state.endPass(generation))
    }

    @Test
    fun aChangeWithNoPassRunningIsNotDeferred() {
        // The ordinary case: the listener's own write is the delivery, so no follow-up is
        // owed and nothing extra is queued.
        val state = PrefsMirrorState()
        val generation = state.onBind()

        assertFalse(state.onLocalChange())
        assertFalse(state.claimFollowUpPass(generation))
    }
}
