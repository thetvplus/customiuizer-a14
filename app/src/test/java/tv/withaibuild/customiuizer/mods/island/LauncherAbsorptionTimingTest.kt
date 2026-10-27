package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LauncherAbsorptionTimingTest {
    class Track(var force: Float = 900f) {
        var fail = false
        fun getStiffness() = force
        fun setStiffness(value: Float) {
            force = value
            if (fail) { fail = false; throw IllegalStateException("native setter failed after mutation") }
        }
    }
    class NativeSpring {
        val tracks = arrayOf("centerX", "centerY", "Width", "Ratio", "Radius", "Alpha", "SurfaceExtAlpha")
            .associateWith { Track() }
        fun getSpringBundle(key: String) = tracks[key]
    }

    @Test fun onlyTheAcceptedSpringSlowsAndRestorationPreservesNewNativeOwnership() {
        val timing = LauncherAbsorptionTiming(NativeSpring::class.java)
        val current = NativeSpring(); val unrelated = NativeSpring()
        assertTrue(timing.apply(current))
        val oldLease = timing.lease(current)
        assertTrue(timing.apply(current))
        timing.restore(current, oldLease)
        assertTrue("An old completion cannot restore a successor adjustment", current.tracks.getValue("Width").force < 900f)
        for (track in current.tracks.values) assertTrue(track.force in 600f..700f)
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
        val timing = LauncherAbsorptionTiming(NativeSpring::class.java)
        val current = NativeSpring().apply { tracks.getValue("Ratio").fail = true }
        assertFalse(timing.apply(current))
        current.tracks.values.forEach { assertEquals(900f, it.force, 0f) }
        timing.restore()
        assertTrue(timing.apply(current))
        timing.restore()
        current.tracks.values.forEach { assertEquals(900f, it.force, 0f) }
    }
}
