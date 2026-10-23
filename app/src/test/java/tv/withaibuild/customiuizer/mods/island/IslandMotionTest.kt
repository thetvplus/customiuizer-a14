package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandMotionTest {
    @Test fun musicReturnArrivesBeforeOneSmallCatchAndHasExactEndpoints() {
        assertEquals(0f, IslandMotion.mediaReturnTravel(0f), 0f)
        assertEquals(1f, IslandMotion.mediaReturnTravel(1f), 0f)
        assertEquals(0f, IslandMotion.mediaReturnCatch(0f), 0f)
        assertEquals(0f, IslandMotion.mediaReturnCatch(1f), 0f)
        var previous = 0f
        for (i in 0..1000) {
            val p = i / 1000f
            val travel = IslandMotion.mediaReturnTravel(p)
            assertTrue(travel.isFinite() && travel in previous..1f)
            assertTrue(IslandMotion.mediaReturnCatch(p) in 0f.. .6f)
            previous = travel
        }
        assertEquals(1f, IslandMotion.mediaReturnTravel(.72f), 0f)
        assertTrue(IslandMotion.mediaReturnCatch(.72f) > 0f)
        assertTrue(IslandMotion.MEDIA_RETURN_DELAY < IslandMotion.MEDIA_DELAY)
        assertTrue(IslandMotion.MEDIA_RETURN < 1000)
    }

    @Test fun dampedTravelArrivesPromptlyAndSettlesWithOnlyOneSmallOvershoot() {
        assertEquals(0f, IslandMotion.progress(0f), .0001f)
        assertEquals(1f, IslandMotion.progress(1f), .0001f)
        assertTrue(IslandMotion.progress(.25f) in .65f.. .85f)
        assertTrue(IslandMotion.progress(.45f) > .97f)
        var turns = 0
        var falling = false
        var previous = 0f
        var peak = 0f
        for (step in 1..1000) {
            val next = IslandMotion.progress(step / 1000f)
            if (!falling && next < previous) { falling = true; turns++ }
            peak = maxOf(peak, next)
            assertTrue(next >= 0f && next < 1.02f)
            previous = next
        }
        assertTrue(peak > 1f)
        assertEquals(1, turns)
        assertTrue(IslandMotion.ENTER in 300L..400L)
        assertTrue(IslandMotion.MORPH in 250L..350L)
        assertEquals(750L, IslandMotion.MEDIA_DELAY)
        assertEquals(2000L, IslandMotion.PAUSE_LIFETIME)
    }
}
