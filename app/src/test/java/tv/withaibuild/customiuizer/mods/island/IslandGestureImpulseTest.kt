package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandGestureImpulseTest {
    @Test fun fasterUpwardSwipeAddsOnlyABoundedImpulseAndDownwardMotionDoesNot() {
        fun swipe(duration: Long): Float = IslandGestureImpulse().run {
            begin(1000f, 100)
            move(200f, 100 + duration, 2f)
            strength
        }
        assertTrue(swipe(100) > swipe(600))
        assertTrue(swipe(1) in 0f..1f)
        val sample = IslandGestureImpulse()
        sample.begin(200f, 100); sample.move(1000f, 200, 2f)
        assertEquals(0f, sample.strength, 0f)
    }
    @Test fun cancellationAndNewGestureDoNotReuseThePreviousVelocity() {
        val sample = IslandGestureImpulse()
        sample.begin(1000f, 100); sample.move(200f, 200, 1f)
        assertTrue(sample.strength > 0f)
        sample.cancel(); assertEquals(0f, sample.strength, 0f)
        sample.move(0f, 210, 1f); assertEquals(0f, sample.strength, 0f)
        sample.begin(1000f, 220); assertEquals(0f, sample.strength, 0f)
        sample.move(Float.NaN, 230, 1f); assertEquals(0f, sample.strength, 0f)
    }
}
