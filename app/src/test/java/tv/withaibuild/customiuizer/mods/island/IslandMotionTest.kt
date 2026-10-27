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

    @Test fun nativeIntakePrecedesOneBoundedCatchWithExactEndpoints() {
        assertEquals(0f, IslandMotion.mediaReturnCatch(0f), 0f)
        assertEquals(0f, IslandMotion.mediaReturnCatch(1f), 0f)
        var previous = 0f
        var descending = false
        for (i in 0..1000) {
            val p = i / 1000f
            val catchValue = IslandMotion.mediaReturnCatch(p)
            assertTrue(catchValue.isFinite() && catchValue in -.2001f.. .6f)
            if (p >= .5f) {
                if (catchValue < previous) descending = true
                if (descending) assertTrue(catchValue <= previous)
            } else assertTrue(catchValue <= 0f)
            previous = catchValue
        }
        assertTrue(descending)
        assertEquals(-.2f, IslandMotion.mediaReturnCatch(.325f), .0001f)
        assertEquals(0f, IslandMotion.mediaReturnCatch(.5f), 0f)
        assertTrue(IslandMotion.mediaReturnCatch(.72f) > 0f)
        assertTrue(IslandMotion.MEDIA_RETURN_DELAY < IslandMotion.MEDIA_DELAY)
        assertTrue(IslandMotion.MEDIA_RETURN < 1000)
    }

    @Test fun theCatchPeaksEarlyAndScalesIntoTheAvailableSpaceWithoutAClippedPlateau() {
        val peak = (500..900).maxBy { IslandMotion.mediaReturnCatch(it / 1000f) } / 1000f
        assertTrue(peak in .59f.. .61f)
        assertEquals(0f, IslandMotion.mediaReturnCatch(.9f), .00001f)
        assertTrue(IslandMotion.mediaReturnCatch(.86f) < .006f)
        for (width in listOf(140f, 160f, 280f, 400f)) {
            val maximum = width + 8f
            val weight = IslandMotion.boundedCatchWeight(width, maximum, .1f)
            var cappedFrames = 0
            for (step in 500..900) {
                val breadth = width + width * weight * IslandMotion.mediaReturnCatch(step / 1000f)
                assertTrue(breadth <= maximum + .0001f)
                if (breadth >= maximum - .001f) cappedFrames++
            }
            assertTrue("There must be a peak, not several frames stuck at the cap", cappedFrames < 5)
        }
    }

    @Test fun elasticReleaseMovesInTheFirstFrameAndCarriesItsMeasuredVelocityOnReversal() {
        val spring = IslandMotion.Spring()
        val seconds = IslandMotion.REBOUND / 1000f
        spring.retarget(110f, 100f, seconds, false, elastic = true)
        // The longer elastic tail retains visible first-frame travel. The same physical
        // undershoot is reached later on the shared clock, rather than by changing damping.
        assertTrue(spring.sample(1f / 60f) in 107f..108f)
        assertTrue(spring.sample(seconds * .3125f) in 98.8f..99.3f)
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
        assertTrue(IslandMotion.ENTER in 300L..400L)
        assertTrue(IslandMotion.MORPH in 250L..350L)
        assertEquals(750L, IslandMotion.MEDIA_DELAY)
        assertEquals(2000L, IslandMotion.PAUSE_LIFETIME)
    }
}
