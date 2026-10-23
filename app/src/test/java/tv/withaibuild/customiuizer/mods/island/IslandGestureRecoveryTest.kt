package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.media.session.PlaybackState
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
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
class IslandGestureRecoveryTest {
    private lateinit var activity: Activity
    private lateinit var shell: IslandActivityView

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(240, 40))
        shell.bind("Sender", "Public message", null, false, false, false, 0, false)
        shell.geometry(240, 40, 40)
        layout(240, 40)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)

    }

    @After fun cleanup() {
        ShadowChoreographer.setPaused(false)
        activity.finish()
    }

    @Test fun touchingAnExitingIslandCannotCancelWindowRemoval() {
        var completions = 0
        shell.exit { completions++ }
        val exit = motion()!!
        exit.currentPlayTime = 60
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pointers = 2)
        touch(MotionEvent.ACTION_MOVE, y = 50f, pointers = 2)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pointers = 2)
        touch(MotionEvent.ACTION_UP)
        assertSame("The exit owns its completion until removal or explicit re-entry", exit, motion())
        exit.end()
        assertEquals(1, completions)
    }

    @Test fun aCommittedRecallFinishesAtTheCameraWithoutReleaseRebound() {
        var completions = 0
        shell.cameraAnchor(20f, 16f)
        shell.onDismiss = { shell.exit { completions++ } }
        touch(MotionEvent.ACTION_DOWN, y = 30f)
        touch(MotionEvent.ACTION_MOVE, y = -80f)
        touch(MotionEvent.ACTION_UP, y = -80f)
        val exit = motion()!!
        var lastHeight = 40f * 1.14f
        for (step in 0..100) {
            exit.currentPlayTime = exit.duration * step / 100
            val visibleHeight = shell.height * shell.scaleY
            assertTrue("monotonic retreat", visibleHeight <= lastHeight + .01f)
            assertTrue("camera remains covered", visibleHeight >= 16f - .01f)
            lastHeight = visibleHeight
        }
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_CANCEL)
        assertSame(exit, motion())
        exit.end()
        assertEquals(1, completions)
    }

    @Test fun repeatedCanceledAndMultiPointerGesturesStillAllowOneDismissal() {
        var dismissed = 0
        shell.onDismiss = { dismissed++ }
        repeat(120) {
            touch(MotionEvent.ACTION_DOWN)
            touch(MotionEvent.ACTION_MOVE, x = 130f, y = 27f)
            touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pointers = 2)
            touch(MotionEvent.ACTION_MOVE, x = 90f, y = 0f, pointers = 2)
            touch(MotionEvent.ACTION_CANCEL)
            motion()?.end()
        }
        assertEquals(0, dismissed)
        touch(MotionEvent.ACTION_DOWN, y = 30f)
        touch(MotionEvent.ACTION_MOVE, y = -80f)
        touch(MotionEvent.ACTION_UP, y = -80f)
        assertEquals(1, dismissed)
    }

    @Test fun disablingTrackSwipesBlocksBothDirectionsAndKeepsTapAndUpwardDismissal() {
        val actions = PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        val controls = mutableListOf<Long>()
        var opened = 0; var dismissed = 0
        shell.onControl = { controls.add(it) }
        shell.onOpen = { opened++ }
        shell.onDismiss = { dismissed++ }
        fun bind(enabled: Boolean) {
            shell.bind("Song", null, null, true, true, false, actions, enabled)
            layout(240, 40)
        }
        fun swipe(endX: Float) {
            touch(MotionEvent.ACTION_DOWN)
            touch(MotionEvent.ACTION_MOVE, x = endX)
            touch(MotionEvent.ACTION_UP, x = endX)
            motion()?.end()
        }
        bind(true)
        swipe(-80f); swipe(320f)
        assertEquals(listOf(PlaybackState.ACTION_SKIP_TO_NEXT, PlaybackState.ACTION_SKIP_TO_PREVIOUS), controls)
        bind(false)
        swipe(-80f); swipe(320f)
        assertEquals(2, controls.size)
        assertTrue(shell.performClick())
        assertEquals(1, opened)
        touch(MotionEvent.ACTION_DOWN, y = 30f)
        touch(MotionEvent.ACTION_MOVE, y = -80f)
        touch(MotionEvent.ACTION_UP, y = -80f)
        assertEquals(1, dismissed)
    }

    @Test fun multiplePointersDuringLengtheningCannotCancelContentAndContourCompletion() {
        shell.geometry(280, 40, 40); layout(280, 40)
        shell.transition(240, 40, 280, 40, false)
        val morph = motion()!!
        morph.currentPlayTime = morph.duration / 4
        val visibleWidth = shell.width * shell.scaleX
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pointers = 2)
        touch(MotionEvent.ACTION_MOVE, y = 40f, pointers = 2)
        touch(MotionEvent.ACTION_CANCEL)
        assertSame(morph, motion())
        assertEquals(visibleWidth, shell.width * shell.scaleX, .001f)
        morph.end()
        assertEquals(1f, shell.scaleX, .001f)
        assertEquals(1f, shell.getChildAt(1).alpha, .001f)
        assertEquals(40, shell.height)
    }

    private fun motion(): ValueAnimator? = getField(shell, "motion")
    private fun layout(width: Int, height: Int) {
        shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        shell.layout(0, 0, width, height)
    }

    private fun touch(action: Int, x: Float = 120f, y: Float = 20f, pointers: Int = 1) {
        val properties = Array(pointers) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = Array(pointers) { MotionEvent.PointerCoords().apply { this.x = x + it * 8; this.y = y; pressure = 1f; size = 1f } }
        val event = MotionEvent.obtain(0, 100, action, pointers, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
        try { shell.dispatchTouchEvent(event) } finally { event.recycle() }
    }
}
