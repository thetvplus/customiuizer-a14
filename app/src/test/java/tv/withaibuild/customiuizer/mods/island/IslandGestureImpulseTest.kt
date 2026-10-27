package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandGestureImpulseTest {
    @Test fun recentReleaseVelocityHasTheSameTempoAfterAShortOrLongPrecedingPath() {
        fun gesture(start: Float): IslandGestureImpulse = IslandGestureImpulse().apply {
            begin(start, 0)
            move(500f, 300, 1f)
            for (step in 1..12) move(500f - step * 20f, 300L + step * 10, 1f)
        }
        val short = gesture(600f); val long = gesture(1800f)
        assertEquals(short.strength, long.strength, .02f)
        assertEquals(IslandMotion.returnDuration(short.strength, false).toFloat(),
            IslandMotion.returnDuration(long.strength, false).toFloat(), 1f)
        for (absorption in listOf(false, true)) {
            val slow = IslandMotion.returnDuration(0f, absorption)
            val fast = IslandMotion.returnDuration(1f, absorption)
            assertTrue(slow - fast <= 60L)
            assertTrue(slow > fast)
            assertTrue(fast >= 500L && slow <= 810L)
        }
    }

    @Test fun pauseAndReversalRemoveTheOldImpulseInsteadOfSpeedingTheNextReturn() {
        val sample = IslandGestureImpulse()
        sample.begin(1000f, 0); sample.move(700f, 30, 1f)
        val fast = sample.strength
        sample.move(700f, 330, 1f)
        assertTrue(sample.strength < fast * .25f)
        sample.move(900f, 350, 1f)
        assertEquals(0f, sample.strength, 0f)
    }

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
        assertEquals(sample.strength, sample.recentStrength(250), 0f)
        assertEquals(0f, sample.recentStrength(500), 0f)
        sample.cancel(); assertEquals(0f, sample.strength, 0f)
        sample.move(0f, 210, 1f); assertEquals(0f, sample.strength, 0f)
        sample.begin(1000f, 220); assertEquals(0f, sample.strength, 0f)
        sample.move(Float.NaN, 230, 1f); assertEquals(0f, sample.strength, 0f)
    }
}
