package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherVerticalGestureTest {

    @Test
    fun downAndUpMapOneAndTwoFingersToCanonicalKeys() {
        assertEquals(
            "launcher_swipedown",
            LauncherVerticalGesture.resolveKey(LauncherVerticalGesture.DIRECTION_DOWN, 1),
        )
        assertEquals(
            "launcher_swipedown2",
            LauncherVerticalGesture.resolveKey(LauncherVerticalGesture.DIRECTION_DOWN, 2),
        )
        assertEquals(
            "launcher_swipeup",
            LauncherVerticalGesture.resolveKey(LauncherVerticalGesture.DIRECTION_UP, 1),
        )
        assertEquals(
            "launcher_swipeup2",
            LauncherVerticalGesture.resolveKey(LauncherVerticalGesture.DIRECTION_UP, 2),
        )
    }

    @Test
    fun otherDirectionsOrFingerCountsDoNotResolveACustomKey() {
        assertNull(LauncherVerticalGesture.resolveKey(9, 1))
        assertNull(LauncherVerticalGesture.resolveKey(12, 1))
        assertNull(LauncherVerticalGesture.resolveKey(LauncherVerticalGesture.DIRECTION_DOWN, 0))
        assertNull(LauncherVerticalGesture.resolveKey(LauncherVerticalGesture.DIRECTION_DOWN, 3))
        assertNull(LauncherVerticalGesture.resolveKey(LauncherVerticalGesture.DIRECTION_UP, 3))
    }
}
