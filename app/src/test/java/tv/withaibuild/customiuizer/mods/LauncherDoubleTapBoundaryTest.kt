package tv.withaibuild.customiuizer.mods

import android.view.MotionEvent
import android.view.ViewConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LauncherDoubleTapBoundaryTest {
    private val detector = LauncherGestureHooks.DoubleTapController(
        RuntimeEnvironment.getApplication(), "launcher_doubletap"
    )

    private fun event(action: Int, down: Long, time: Long, x: Float = 50f): Boolean {
        val event = MotionEvent.obtain(down, time, action, x, 50f, 0)
        return try { detector.isDoubleTapEvent(event) } finally { event.recycle() }
    }

    private fun tap(time: Long, x: Float = 50f): Boolean {
        assertFalse(event(MotionEvent.ACTION_DOWN, time, time, x))
        return event(MotionEvent.ACTION_UP, time, time + 20, x)
    }

    @Test
    fun twoNearbyTapsTriggerOnceAndAnExpiredTapStartsANewPair() {
        assertFalse(tap(1000))
        assertTrue(tap(1100))
        assertFalse(tap(1200))
        assertFalse(tap(2000))
        assertTrue(tap(2100))
    }

    @Test
    fun cancellationBreaksThePairAndDoesNotAcceptAnOrphanUp() {
        assertFalse(tap(1000))
        event(MotionEvent.ACTION_DOWN, 1050, 1050)
        event(MotionEvent.ACTION_CANCEL, 1050, 1060)
        assertFalse(event(MotionEvent.ACTION_UP, 1050, 1070))
        assertFalse(tap(1100))
        assertTrue(tap(1200))
    }

    @Test
    fun aSecondPointerBreaksThePairEvenIfTheLastPointerFinishesNearby() {
        assertFalse(tap(1000))
        event(MotionEvent.ACTION_DOWN, 1050, 1050)
        event(MotionEvent.ACTION_POINTER_DOWN, 1050, 1060)
        event(MotionEvent.ACTION_POINTER_UP, 1050, 1070)
        assertFalse(event(MotionEvent.ACTION_UP, 1050, 1080))
        assertFalse(tap(1100))
    }

    @Test
    fun draggingOutAndBackCannotCountAsTheSecondTap() {
        assertFalse(tap(1000))
        event(MotionEvent.ACTION_DOWN, 1050, 1050)
        event(MotionEvent.ACTION_MOVE, 1050, 1060, 500f)
        event(MotionEvent.ACTION_MOVE, 1050, 1070)
        assertFalse(event(MotionEvent.ACTION_UP, 1050, 1080))
        assertFalse(tap(1100))
    }

    @Test
    fun aLongPressDoesNotBecomeTheFirstHalfOfADoubleTap() {
        val release = 1000L + ViewConfiguration.getLongPressTimeout()
        event(MotionEvent.ACTION_DOWN, 1000, 1000)
        assertFalse(event(MotionEvent.ACTION_UP, 1000, release))
        assertFalse(tap(release + 50))
    }

    @Test
    fun distantTapsInEitherDirectionStartAFreshPair() {
        assertFalse(tap(1000, 500f))
        assertFalse(tap(1100, 50f))
        assertTrue(tap(1200, 50f))
        assertFalse(tap(1300, 50f))
        assertFalse(tap(1400, 500f))
        assertTrue(tap(1500, 500f))
    }
}
