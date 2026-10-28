package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandSpringContinuityTest {
    @Test fun reversalPreservesBothPositionAndVelocityBeforeDecelerating() {
        val spring = IslandMotion.Spring()
        spring.retarget(184f, 280f, .32f, false)
        val position = spring.sample(.08f)
        val velocity = spring.velocity
        assertTrue(velocity > 0f)
        spring.retarget(position, 184f, .32f, true)
        assertEquals(position, spring.sample(0f), .0001f)
        assertEquals(velocity, spring.velocity, .001f)
        assertTrue(spring.sample(.001f) > position)
        assertTrue(spring.sample(.2f) < position)
        assertEquals(184f, spring.sample(.32f), .15f)
    }

    @Test fun sixtyAndOneHundredTwentyHzReachIdenticalPhysicalFrames() {
        val slow = IslandMotion.Spring()
        val fast = IslandMotion.Spring()
        slow.retarget(16f, 840f, .36f, false)
        fast.retarget(16f, 840f, .36f, false)
        for (frame in 0..21) {
            val time = frame / 60f
            fast.sample(maxOf(0f, time - 1f / 120f))
            assertEquals(slow.sample(time), fast.sample(time), .001f)
            assertEquals(slow.velocity, fast.velocity, .001f)
        }
    }

    @Test fun cameraAndSafeWidthBoundsProjectVelocityInsteadOfLosingThePhysicalAnchor() {
        val spring = IslandMotion.Spring()
        spring.retarget(16f, 840f, .32f, false)
        val current = spring.sample(.1f)
        spring.retarget(current, 16f, .32f, true)
        for (frame in 0..40) assertTrue(spring.sample(frame / 120f, 16f, 840f) in 16f..840f)
        spring.finish()
        assertEquals(16f, spring.value, 0f)
        assertEquals(0f, spring.velocity, 0f)
    }

    @Test fun finiteDeadlineHasLessThanAThirdPixelOfRemainingTravel() {
        for (duration in listOf(.195f, .3f, .32f, .36f, .42f, .76f)) for (elastic in listOf(false, true)) {
            val spring = IslandMotion.Spring()
            spring.retarget(16f, 840f, duration, false, elastic = elastic)
            assertEquals(840f, spring.sample(duration), .3f)
            spring.finish()
            assertEquals(840f, spring.value, 0f)
            assertEquals(0f, spring.velocity, 0f)
        }
    }
}
