package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.graphics.Outline
import android.media.session.PlaybackState
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.MotionEvent
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandPresentationTest {
    @Test fun playbackReentryCanInterruptCameraExitWithoutLeavingAnInvisibleWindow() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(400, 80))
        shell.geometry(400, 80, 80); shell.layout(0, 0, 400, 80)
        shell.cameraAnchor(26f, 20f)
        val main = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        main.idle()
        assertTrue(shell.isAttachedToWindow)
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        var exited = 0
        shell.exit { exited++ }
        val exit = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(shell, "motion")
        assertEquals(IslandMotion.EXIT, exit.duration)
        exit.currentPlayTime = 200L
        assertEquals(0, exited)
        shell.resumeEntry()
        shell.transition(400, 80, 400, 80, true)
        val entrance = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(shell, "motion")
        assertNotSame(exit, entrance)
        assertEquals(IslandMotion.ENTER, entrance.duration)
        exit.end() // A stale animation cannot apply its exit values to the successor.
        assertEquals(0, exited)
        entrance.end()
        assertEquals(0, exited)
        assertEquals(1f, shell.alpha, .001f)
        assertEquals(1f, shell.scaleX, .001f)
        assertEquals(1f, shell.scaleY, .001f)
        activity.finish()
        org.robolectric.shadows.ShadowChoreographer.setPaused(false)
    }
    @Test fun upwardGestureDismissesOnceAndDownwardOrLongPressNeverOpensAPanel() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity).apply { simplified = true }
        activity.setContentView(shell)
        shell.bind("Track", null, null, true, true, true, 0, true)
        shell.geometry(400, 40, 40); shell.layout(0, 0, 400, 40)
        var dismissed = 0; shell.onDismiss = { dismissed++ }
        assertFalse(shell.performLongClick())
        fun swipe(to: Float) {
            for ((action,y) in listOf(MotionEvent.ACTION_DOWN to 20f, MotionEvent.ACTION_MOVE to to, MotionEvent.ACTION_UP to to)) {
                val event = MotionEvent.obtain(0, 10, action, 200f, y, 0)
                shell.dispatchTouchEvent(event); event.recycle()
            }
        }
        swipe(180f); assertEquals(0, dismissed)
        assertEquals(40, shell.height); assertEquals(3, shell.childCount)
        swipe(-100f); assertEquals(1, dismissed)
        activity.finish()
    }

    @Test fun publicNotificationKeepsAccessibleDetailsWithoutACardOrControlPanel() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity).apply { simplified = true }
        activity.setContentView(shell)
        shell.bind("Sender", "Message supplied by notification", null, false, false, false, 0L, true)
        shell.geometry(240, 40, 40); shell.layout(0, 0, 240, 40)
        assertFalse(shell.performLongClick())
        assertEquals(3, shell.childCount)
        assertEquals(1, (shell.getChildAt(1) as android.widget.LinearLayout).childCount)
        assertTrue(shell.contentDescription.toString().contains("Message supplied by notification"))
        activity.finish()
    }

    @Test fun shortAndLongContentLeaveThePhysicalCameraGapEmptyAtEveryConfiguredHeight() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity).apply { simplified = true }
        activity.setContentView(shell)
        shell.bind("Public sender", "Message", null, false, false, false, 0, false)
        for (width in listOf(184,240,280)) for (height in 38..44) {
            shell.geometry(width,height,40)
            shell.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY))
            shell.layout(0,0,width,height)
            assertTrue(shell.getChildAt(0).right <= (width-40)/2)
            assertTrue(shell.getChildAt(1).left >= (width+40)/2)
            assertEquals(height,shell.height)
        }
        activity.finish()
    }

    @Test fun widthChangesKeepTheCameraAnchorAtTheSamePhysicalPoint() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell)
        shell.geometry(280,40,40); shell.layout(0,0,280,40)
        for (fraction in listOf(0f,.25f,.5f,.75f,1f)) {
            shell.scaleX=184f/280+(1-184f/280)*fraction
            val anchor=floatArrayOf(140f,0f); shell.matrix.mapPoints(anchor)
            assertEquals(140f,anchor[0],.01f); assertEquals(0f,anchor[1],.01f)
        }
        activity.finish()
    }

    @Test fun shapeClipsItsFullBoundsAndInvalidatesOnlyForNewDimensions() {
        val shape = IslandShape()
        shape.resize(288, 44)
        val old = shape.path.generationId
        shape.resize(288, 44)
        assertEquals(old, shape.path.generationId)
        assertFalse(shape.contains(0f, 0f))
        assertTrue(shape.contains(144f, 1f))
        assertTrue(shape.contains(144f, 43f))
        assertTrue(shape.contains(1f, 22f))
        shape.resize(328, 96)
        assertNotEquals(old, shape.path.generationId)
        val view = android.view.View(org.robolectric.RuntimeEnvironment.getApplication())
        view.layout(0, 0, 328, 96)
        val outline = Outline()
        shape.getOutline(view, outline)
        assertTrue(outline.canClip())
        assertFalse(outline.isEmpty)
    }

    @Test fun fullCapsuleEndsAreRoundThroughTheirMidpoint() {
        val shape = IslandShape()
        shape.resize(600, 200)
        assertTrue(shape.contains(2f, 100f))
        assertTrue(shape.contains(597f, 100f))
        assertFalse(shape.contains(598f, 70f))
        assertFalse(shape.contains(1f, 70f))
    }

    @Test fun bothLengthsUseTheSameEndCapAndNoIndependentCornerModel() {
        val shape=IslandShape()
        for (width in listOf(184,240,280)) {
            shape.resize(width,40)
            assertTrue(shape.contains(1f,20f)); assertFalse(shape.contains(1f,1f))
            val generation=shape.path.generationId
            shape.resize(width,40)
            assertEquals(generation,shape.path.generationId)
        }
    }

    @Test fun draggingStretchesOnlyTheLatchedAxisAndNeverMovesTheCameraCover() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity).apply { simplified = true }
        activity.setContentView(shell)
        shell.geometry(400, 80, 80, 448); shell.layout(0, 0, 400, 80)
        fun touch(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 10, action, x, y, 0)
            shell.dispatchTouchEvent(event); event.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, 200f, 40f)
        touch(MotionEvent.ACTION_MOVE, 340f, 45f)
        assertTrue(shell.scaleX > 1f); assertEquals(1f, shell.scaleY)
        touch(MotionEvent.ACTION_MOVE, 220f, 190f)
        assertEquals(1f, shell.scaleY)
        assertEquals(0f, shell.translationX); assertEquals(0f, shell.translationY)
        touch(MotionEvent.ACTION_CANCEL, 220f, 190f)
        touch(MotionEvent.ACTION_DOWN, 200f, 40f)
        touch(MotionEvent.ACTION_MOVE, 204f, 160f)
        assertEquals(1f, shell.scaleX); assertTrue(shell.scaleY > 1f)
        assertEquals(0f, shell.translationX); assertEquals(0f, shell.translationY)
        touch(MotionEvent.ACTION_CANCEL, 204f, 160f)
        assertEquals(1f, shell.scaleY)
        // At the actual icon/screen clearance, even a horizontal drag cannot cross it.
        shell.geometry(400, 80, 80, 400)
        touch(MotionEvent.ACTION_DOWN, 200f, 40f)
        touch(MotionEvent.ACTION_MOVE, 340f, 45f)
        assertEquals(1f, shell.scaleX)
        assertEquals(400f, shell.width * shell.scaleX)
        touch(MotionEvent.ACTION_CANCEL, 340f, 45f)
        activity.finish()
    }

    @Test fun centreKeepsItsFinalHeightAndVerticalPositionThroughoutEntryAndExit() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity).apply { entranceStyle = IslandEntranceStyle.CENTER }
        activity.setContentView(shell, FrameLayout.LayoutParams(400, 80))
        shell.geometry(400, 80, 80); shell.layout(0, 0, 400, 80)
        shell.cameraAnchor(26f, 20f)
        shell.prepareEntry()
        assertEquals(.2f, shell.scaleX, .0001f)
        assertEquals(1f, shell.scaleY, .0001f)
        fun centre(): Float {
            val anchor = floatArrayOf(200f, 40f)
            shell.matrix.mapPoints(anchor)
            assertEquals(200f, anchor[0], .001f)
            assertEquals(80f, shell.height * shell.scaleY, .001f)
            return anchor[1]
        }
        assertEquals(40f, centre(), .001f)
        shell.transition(400, 80, 400, 80, true)
        org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(shell, "motion").end()
        assertEquals(40f, centre(), .001f)
        var completed = 0
        shell.exit { completed++ }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
        assertEquals(40f, centre(), .001f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800))
        assertEquals(1, completed)
        assertEquals(40f, centre(), .001f)
        assertEquals(80f, shell.width * shell.scaleX, .001f)
        assertEquals(0f, shell.alpha, .001f)
        activity.finish()
    }

    @Test fun lengtheningPreservesSquareArtworkWhileTheShellChangesAspectRatio() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(840, 120))
        shell.bind("Track", "Artist", null, true, true, true, PlaybackState.ACTION_PAUSE, true)
        assertFalse(shell.performLongClick())
        shell.bind("Track", "Artist", null, true, true, true, PlaybackState.ACTION_PAUSE, true)
        shell.geometry(840, 120, 120); shell.layout(0, 0, 840, 120)
        shell.transition(552, 120, 840, 120, false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(180))
        val icon = (shell.getChildAt(0) as android.widget.LinearLayout).getChildAt(0)
        assertEquals(shell.scaleX * icon.scaleX, shell.scaleY * icon.scaleY, .001f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1200))
        assertEquals(1f, icon.scaleX); assertEquals(1f, icon.scaleY)
        activity.finish()
    }

    @Test fun horizontalSwipesRespectDirectionAndAdvertisedControls() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity).apply { simplified = true }
        activity.setContentView(shell, FrameLayout.LayoutParams(288, 44))
        shell.bind("Track", "Artist", null, true, true, true,
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS, true, PlaybackState.STATE_PLAYING)
        shell.geometry(288, 44, 40); shell.layout(0, 0, 288, 44)
        val actions = mutableListOf<Long>()
        shell.onControl = { actions.add(it) }
        fun swipe(from: Float, to: Float) {
            for ((action, x) in listOf(MotionEvent.ACTION_DOWN to from,
                MotionEvent.ACTION_MOVE to to, MotionEvent.ACTION_UP to to)) {
                val event = MotionEvent.obtain(0, 10, action, x, 22f, 0)
                shell.dispatchTouchEvent(event); event.recycle()
            }
        }
        swipe(200f, 100f); swipe(100f, 200f)
        assertEquals(listOf(PlaybackState.ACTION_SKIP_TO_NEXT, PlaybackState.ACTION_SKIP_TO_PREVIOUS), actions)
        shell.bind("Track", null, null, true, true, true, 0L, false)
        swipe(200f, 100f)
        assertEquals(2, actions.size)
        activity.finish()
    }

    @Test fun fingerReleaseDoesNotCancelTheDismissCompletion() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(288, 44))
        shell.geometry(288, 44, 40); shell.layout(0, 0, 288, 44)
        var completed = 0
        shell.onDismiss = { shell.exit { completed++ } }
        for ((action, y) in listOf(MotionEvent.ACTION_DOWN to 22f,
            MotionEvent.ACTION_MOVE to -80f, MotionEvent.ACTION_UP to -80f)) {
            val event = MotionEvent.obtain(0, 10, action, 144f, y, 0)
            shell.dispatchTouchEvent(event); event.recycle()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800))
        assertEquals(1, completed)
        activity.finish()
    }

    @Test fun ordinaryTextIsNeverReinterpretedBySubstringOrFormatting() {
        assertEquals("已复制", IslandToastText.resolve("复制成功", true))
        for (text in listOf("复制成功？", "没有保存成功", "复制成功但上传失败", "正在删除", "Saved draft")) {
            assertEquals(text, IslandToastText.resolve(text, true))
        }
        assertEquals("复制成功", IslandToastText.resolve("复制成功", false))
        assertNull(IslandToastText.resolve("two\nlines", true))
        assertNull(IslandToastText.resolve("x".repeat(161), true))
        val styled = SpannableString("Saved").apply { setSpan(StyleSpan(Typeface.BOLD), 0, 5, 0) }
        assertNull(IslandToastText.resolve(styled, true))
    }

    @Test fun controlsOnlyOfferTheAdvertisedActionAndStoppedSessionsAreInactive() {
        assertFalse(IslandMediaPolicy.canSkip(PlaybackState.ACTION_PLAY_PAUSE, -1L))
        assertFalse(IslandMediaPolicy.canSkip(PlaybackState.ACTION_SKIP_TO_NEXT,0))
        assertTrue(IslandMediaPolicy.canSkip(PlaybackState.ACTION_SKIP_TO_NEXT,PlaybackState.ACTION_SKIP_TO_NEXT))
        assertTrue(IslandMediaPolicy.canSkip(PlaybackState.ACTION_SKIP_TO_PREVIOUS,PlaybackState.ACTION_SKIP_TO_PREVIOUS))
        for (value in intArrayOf(PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR, PlaybackState.STATE_NONE))
            assertFalse(IslandMediaPolicy.active(value))
        assertTrue(IslandMediaPolicy.active(PlaybackState.STATE_PAUSED))
        assertFalse(IslandMediaPolicy.playing(PlaybackState.STATE_BUFFERING))
    }

    @Test fun dragCancelPointerChangeAndReverseNeverTriggerAStaleRecall() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity).apply { simplified = true }
        activity.setContentView(shell, FrameLayout.LayoutParams(288, 44))
        shell.layout(0, 0, 288, 44)
        shell.geometry(288, 44, 40)
        var dismiss = 0
        shell.onDismiss = { dismiss++ }
        fun touch(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 10, action, x, y, 0)
            shell.dispatchTouchEvent(event)
            event.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, 144f, 22f)
        touch(MotionEvent.ACTION_MOVE, 144f, -80f)
        touch(MotionEvent.ACTION_CANCEL, 144f, -80f)
        assertEquals(0, dismiss)
        assertEquals(0f, shell.translationY)
        assertEquals(1f, shell.scaleY)
        touch(MotionEvent.ACTION_DOWN, 144f, 22f)
        touch(MotionEvent.ACTION_MOVE, 144f, -80f)
        touch(MotionEvent.ACTION_MOVE, 144f, 22f)
        touch(MotionEvent.ACTION_UP, 144f, 22f)
        assertEquals(0, dismiss)
        touch(MotionEvent.ACTION_DOWN, 144f, 22f)
        touch(MotionEvent.ACTION_MOVE, 144f, -80f)
        touch(MotionEvent.ACTION_UP, 144f, -80f)
        assertEquals(1, dismiss)
        touch(MotionEvent.ACTION_DOWN, 144f, 22f)
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = Array(2) { MotionEvent.PointerCoords().apply { x = 144f; y = 22f; pressure = 1f; size = 1f } }
        val pointerDown = MotionEvent.obtain(0, 20,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        shell.dispatchTouchEvent(pointerDown)
        pointerDown.recycle()
        touch(MotionEvent.ACTION_MOVE, 144f, -80f)
        touch(MotionEvent.ACTION_UP, 144f, -80f)
        assertEquals(1, dismiss)
        activity.finish()
    }
}
