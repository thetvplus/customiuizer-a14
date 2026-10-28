package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LauncherReturnTimingTest {
    class Track(var force: Float = 900f) {
        var fail = false
        fun getStiffness() = force
        fun setStiffness(value: Float) {
            force = value
            if (fail) { fail = false; throw IllegalStateException("native setter failed after mutation") }
        }
    }
    class NativeSpring {
        var durationRatio = 1f
        val tracks = arrayOf("centerX", "centerY", "Width", "Ratio", "Radius", "Alpha", "SurfaceExtAlpha")
            .associateWith { Track() }
        fun getSpringBundle(key: String) = tracks[key]
        fun getModifyResponse(response: Float) = response * durationRatio
    }

    @Test fun onlyTheAcceptedSpringSlowsAndRestorationPreservesNewNativeOwnership() {
        val timing = LauncherReturnTiming(NativeSpring::class.java)
        val current = NativeSpring(); val unrelated = NativeSpring()
        assertTrue(timing.apply(current))
        val oldLease = timing.lease(current)
        assertTrue(timing.apply(current))
        timing.restore(current, oldLease)
        assertTrue("An old completion cannot restore a successor adjustment", current.tracks.getValue("Width").force < 900f)
        for (track in current.tracks.values) assertEquals(227.68701f, track.force, .01f)
        for (track in unrelated.tracks.values) assertEquals(900f, track.force, 0f)
        timing.restore(unrelated)
        assertTrue(current.tracks.getValue("Width").force < 900f)
        current.tracks.getValue("Width").force = 1200f // native retarget already replaced this force
        timing.restore(current)
        assertEquals(1200f, current.tracks.getValue("Width").force, 0f)
        for ((key, track) in current.tracks) if (key != "Width") assertEquals(900f, track.force, 0f)
        timing.restore(current)
        assertEquals(1200f, current.tracks.getValue("Width").force, 0f)
    }

    @Test fun partialNativeFailureRollsBackEveryAttemptedTrackAndCannotLeaveAnAdjustmentLease() {
        val timing = LauncherReturnTiming(NativeSpring::class.java)
        val current = NativeSpring().apply { tracks.getValue("Ratio").fail = true }
        assertFalse(timing.apply(current))
        current.tracks.values.forEach { assertEquals(900f, it.force, 0f) }
        timing.restore()
        assertTrue(timing.apply(current))
        timing.restore()
        current.tracks.values.forEach { assertEquals(900f, it.force, 0f) }
    }

    @Test fun acceptedSlowAndFastNativeSpringsShareABoundedWidthResponseAndRetainTrackRatios() {
        for (absorption in listOf(false, true)) for (strength in listOf(0f, .5f, 1f)) {
            val timing = LauncherReturnTiming(NativeSpring::class.java)
            var minimum = Float.MAX_VALUE; var maximum = 0f
            for (force in listOf(100f, 300f, 900f)) {
                val spring = NativeSpring()
                spring.tracks.values.forEach { it.force = force }
                spring.tracks.getValue("centerY").force = force * .75f
                assertTrue(timing.apply(spring, strength, absorption))
                val normalized = spring.tracks.getValue("Width").force
                minimum = minOf(minimum, normalized); maximum = maxOf(maximum, normalized)
                assertEquals(.75f, spring.tracks.getValue("centerY").force / normalized, .0001f)
                assertTrue(normalized / force in .25f..4f)
                timing.restore()
                assertEquals(force, spring.tracks.getValue("Width").force, 0f)
                assertEquals(force * .75f, spring.tracks.getValue("centerY").force, 0f)
            }
            assertTrue("Widely different native tempos converge without replacing their springs", maximum / minimum < 1.6f)
        }
    }

    @Test fun nativeDurationRatioIsAppliedOnceAndAnInvalidRatioCannotPartiallyAdjustTheSpring() {
        val timing = LauncherReturnTiming(NativeSpring::class.java)
        var baseline = 0f
        for (ratio in listOf(1f, .5f, 2f)) {
            val spring = NativeSpring().apply { durationRatio = ratio }
            spring.tracks.values.forEach { it.force = 300f / (ratio * ratio) }
            assertTrue(timing.apply(spring, .5f, false))
            val force = spring.tracks.getValue("Width").force
            if (ratio == 1f) baseline = force
            assertEquals(baseline / (ratio * ratio), force, .01f)
            timing.restore()
        }
        val invalid = NativeSpring().apply { durationRatio = Float.NaN }
        assertFalse(timing.apply(invalid))
        invalid.tracks.values.forEach { assertEquals(900f, it.force, 0f) }
        assertNull(timing.lease(invalid))
    }
}
