package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandMotionTest {
    @Test fun elasticOverscrollCrossesTheBoundaryOnceAndSettlesWithinTheFiniteClock() {
        val spring = IslandMotion.Spring()
        val seconds = IslandMotion.REBOUND / 1000f
        spring.retarget(110f, 100f, seconds, false, elastic = true)
        assertEquals(110f, spring.sample(0f), .0001f)
        assertTrue(spring.sample(seconds * .4f) < 100f)
        assertEquals(100f, spring.sample(seconds), .05f)
        spring.finish()
        assertEquals(0f, spring.velocity, 0f)
    }

    @Test fun elasticReleaseMovesInTheFirstFrameAndCarriesItsMeasuredVelocityOnReversal() {
        val spring = IslandMotion.Spring()
        val seconds = IslandMotion.REBOUND / 1000f
        spring.retarget(110f, 100f, seconds, false, elastic = true)
        // The longer elastic tail retains visible first-frame travel. The same physical
        // undershoot is reached later on the shared clock, rather than by changing damping.
        assertTrue("Release responds in the first frame without rushing through its whole travel", spring.sample(1f / 60f) in 109f..109.5f)
        assertTrue(spring.sample(seconds * .4f) in 98.8f..99.3f)
        val current = spring.sample(.065f)
        val velocity = spring.velocity
        spring.retarget(current, 108f, seconds, true, elastic = true)
        assertEquals(current, spring.sample(0f), .0001f)
        assertEquals(velocity, spring.velocity, .001f)
        spring.finish()
        assertEquals(108f, spring.value, 0f)
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
        assertTrue(IslandMotion.ENTER in 400L..480L)
        assertTrue(IslandMotion.MORPH in 360L..440L)
        assertEquals(750L, IslandMotion.MEDIA_DELAY)
        assertEquals(2000L, IslandMotion.PAUSE_LIFETIME)
    }
}
