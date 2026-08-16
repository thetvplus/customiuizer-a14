package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeExpansionTrackerTest {

    private val tracker = ShadeExpansionTracker(0.33f)

    @Test
    fun staysBelowWhenUnderThreshold() {
        assertFalse(tracker.update(0.1f))
        assertFalse(tracker.update(0.2f))
        assertFalse(tracker.update(0.32f))
        assertEquals(ShadeExpansionTracker.State.BELOW, tracker.currentState())
    }

    @Test
    fun reportsCrossingOnceOnExpansion() {
        assertFalse(tracker.update(0.2f))
        assertTrue(tracker.update(0.34f))
        assertFalse(tracker.update(0.5f))
        assertEquals(ShadeExpansionTracker.State.ABOVE, tracker.currentState())
    }

    @Test
    fun reportsCrossingOnceOnCollapse() {
        tracker.update(0.5f)
        tracker.update(0.6f)
        assertTrue(tracker.update(0.1f))
        assertFalse(tracker.update(0.05f))
        assertEquals(ShadeExpansionTracker.State.BELOW, tracker.currentState())
    }

    @Test
    fun avoidsRepeatedResetsInsideSameState() {
        assertTrue(tracker.update(0.4f))
        assertFalse(tracker.update(0.6f))
        assertFalse(tracker.update(0.8f))
        assertEquals(ShadeExpansionTracker.State.ABOVE, tracker.currentState())
    }

    @Test
    fun crossingExactlyAtThreshold() {
        assertFalse(tracker.update(0.33f))
        assertTrue(tracker.update(0.3301f))
    }
}
