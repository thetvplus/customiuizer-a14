package tv.withaibuild.customiuizer.mods

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandEntranceTest {
    @Test fun centreTravelCanResumeWithoutJumpingOrRunningAnOldExit() = exercise(IslandEntranceStyle.CENTER)
    @Test fun topTravelCanResumeWithoutJumpingOrRunningAnOldExit() = exercise(IslandEntranceStyle.TOP)

    private fun exercise(style: IslandEntranceStyle) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val shell = IslandActivityView(activity).apply { entranceStyle = style; windowTop = 12 }
            activity.setContentView(shell, FrameLayout.LayoutParams(240, 40))
            shell.geometry(240, 40, 40); shell.layout(0, 0, 240, 40)
            shell.cameraAnchor(20f, 16f)
            shadowOf(Looper.getMainLooper()).idle()
            ShadowChoreographer.setPaused(true)
            try {
                shell.prepareEntry()
                if (style == IslandEntranceStyle.TOP) {
                    assertEquals(1f, shell.scaleX, 0f)
                    assertTrue(shell.translationY + shell.windowTop + shell.height < 0)
                } else {
                    assertEquals(0f, shell.translationY, 0f)
                    assertEquals(40f, shell.scaleX * 240, .01f)
                    assertEquals(40f, shell.scaleY * 40, .01f)
                }
                shell.transition(0, 0, 240, 40, true)
                val entering = getField<ValueAnimator>(shell, "motion")
                entering.currentPlayTime = 80
                assertEquals(40f, shell.height * shell.scaleY, .001f)
                val enteredScale = shell.scaleX; val enteredTravel = shell.translationY
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(0, 100, action, 120f, 20f, 0)
                    shell.dispatchTouchEvent(event); event.recycle()
                }
                assertSame(entering, getField<ValueAnimator>(shell, "motion"))
                assertEquals(enteredScale, shell.scaleX, .001f)
                assertEquals(enteredTravel, shell.translationY, .001f)
                entering.end()
                assertTrue("attached before exit $style", shell.isAttachedToWindow)
                var completed = 0
                shell.exit { completed++ }
                val exit = getField<ValueAnimator>(shell, "motion")
                exit.currentPlayTime = 90
                assertEquals(40f, shell.height * shell.scaleY, .001f)
                val x = shell.scaleX; val y = shell.scaleY; val travel = shell.translationY
                val visualTop = shell.pivotY * (1f - shell.scaleY) + shell.translationY
                // The controller refits geometry before it resumes an interrupted exit.
                shell.geometry(240, 40, 40)
                shell.cameraAnchor(20f, 16f)
                shell.resumeEntry()
                shell.transition(240, 40, 240, 40, true)
                assertEquals(x, shell.scaleX, .001f)
                assertEquals(y, shell.scaleY, .001f)
                assertEquals(travel, shell.translationY, .001f)
                assertEquals(visualTop, shell.pivotY * (1f - shell.scaleY) + shell.translationY, .001f)
                exit.end()
                assertEquals(0, completed)
                getField<ValueAnimator>(shell, "motion").end()
                assertEquals(1f, shell.scaleX, .001f)
                assertEquals(1f, shell.scaleY, .001f)
                assertEquals(0f, shell.translationY, .001f)
                assertEquals(1f, shell.alpha, .001f)
            } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }
}
