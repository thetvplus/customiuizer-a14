package tv.withaibuild.customiuizer.prefs

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpinnerExFakeInputTest {
    @Test fun scrollingAndCancelledTouchesCannotOpenASelectorButTapsAndAccessibilityCan() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get().apply { setTheme(R.style.AppTheme) }
        controller.setup().visible()
        try {
            val picker = SpinnerExFake(activity, null)
            activity.setContentView(picker, FrameLayout.LayoutParams(240, 50))
            picker.layout(0, 0, 240, 50)
            var opens = 0
            picker.selectAction = { opens++ }
            fun touch(action: Int, y: Float, at: Long) {
                val event = MotionEvent.obtain(0, at, action, 100f, y, 0)
                try { picker.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, 20f, 0)
            touch(MotionEvent.ACTION_MOVE, 180f, 40)
            touch(MotionEvent.ACTION_UP, 180f, 80)
            assertEquals(0, opens)
            touch(MotionEvent.ACTION_DOWN, 20f, 200)
            touch(MotionEvent.ACTION_CANCEL, 20f, 220)
            touch(MotionEvent.ACTION_UP, 20f, 240)
            assertEquals(0, opens)
            touch(MotionEvent.ACTION_DOWN, 20f, 400)
            touch(MotionEvent.ACTION_UP, 20f, 450)
            assertEquals(1, opens)
            assertTrue(picker.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null))
            assertEquals(2, opens)
            picker.isEnabled = false
            assertFalse(picker.performClick())
            assertEquals(2, opens)
        } finally { controller.pause().stop().destroy() }
    }
}
