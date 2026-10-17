package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStateMachineTest {

    @Test
    fun filterIsAllowedInIdleAndSearching() {
        assertTrue(SearchStateMachine.canFilter(SearchStateMachine.STATE_IDLE))
        assertTrue(SearchStateMachine.canFilter(SearchStateMachine.STATE_SEARCHING))
    }

    @Test
    fun filterIsBlockedAfterNavigation() {
        assertFalse(SearchStateMachine.canFilter(SearchStateMachine.STATE_NAVIGATED))
    }

    @Test
    fun clearOnReturnOnlyWhenNavigated() {
        assertFalse(SearchStateMachine.shouldClearOnReturn(SearchStateMachine.STATE_IDLE))
        assertFalse(SearchStateMachine.shouldClearOnReturn(SearchStateMachine.STATE_SEARCHING))
        assertTrue(SearchStateMachine.shouldClearOnReturn(SearchStateMachine.STATE_NAVIGATED))
    }

    @Test
    fun queryInIdleTransitionsToSearching() {
        assertEquals(SearchStateMachine.STATE_SEARCHING, SearchStateMachine.transitionOnQuery(SearchStateMachine.STATE_IDLE, "foo"))
    }

    @Test
    fun emptyQueryKeepsIdleState() {
        assertEquals(SearchStateMachine.STATE_IDLE, SearchStateMachine.transitionOnQuery(SearchStateMachine.STATE_IDLE, ""))
    }

    @Test
    fun queryWhenNavigatedDoesNotChangeState() {
        assertEquals(SearchStateMachine.STATE_NAVIGATED, SearchStateMachine.transitionOnQuery(SearchStateMachine.STATE_NAVIGATED, "foo"))
    }

    @Test
    fun successfulSelectionTransitionsToNavigated() {
        assertEquals(SearchStateMachine.STATE_NAVIGATED, SearchStateMachine.transitionOnSelect(SearchStateMachine.STATE_SEARCHING, true))
    }

    @Test
    fun failedSelectionKeepsState() {
        assertEquals(SearchStateMachine.STATE_SEARCHING, SearchStateMachine.transitionOnSelect(SearchStateMachine.STATE_SEARCHING, false))
    }

    @Test
    fun returnFromNavigatedGoesToIdle() {
        assertEquals(SearchStateMachine.STATE_IDLE, SearchStateMachine.transitionOnReturn(SearchStateMachine.STATE_NAVIGATED))
    }

    @Test
    fun returnFromIdleOrSearchingDoesNotChangeState() {
        assertEquals(SearchStateMachine.STATE_IDLE, SearchStateMachine.transitionOnReturn(SearchStateMachine.STATE_IDLE))
        assertEquals(SearchStateMachine.STATE_SEARCHING, SearchStateMachine.transitionOnReturn(SearchStateMachine.STATE_SEARCHING))
    }
}
