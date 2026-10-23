package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandMotionTest {
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
