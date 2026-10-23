package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.media.session.PlaybackState
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandMusicGestureTest {
    private lateinit var host: Activity
    private lateinit var shell: IslandActivityView
    private val gestures = mutableListOf<IslandMusicGesture>()
    private var opened = 0
    private var dismissed = 0
    private var downTime = 0L
    private val identity = Any()
    private val capabilities = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS

    @Before fun setup() {
        host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        shell = IslandActivityView(host).apply { simplified = true }
        host.setContentView(shell, FrameLayout.LayoutParams(280, 40))
        shell.bind("Song", null, null, true, true, false, capabilities, true, PlaybackState.STATE_PLAYING)
        shell.configureGestures(identity, IslandMusicActions(), true)
        shell.geometry(280, 40, 40)
        shell.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        shell.layout(0, 0, 280, 40)
        shell.onGesture = { gestures.add(it) }; shell.onOpen = { opened++ }; shell.onDismiss = { dismissed++ }
    }
    @After fun cleanup() { host.finish() }
    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun touch(action: Int, x: Float = 140f, y: Float = 20f) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try { shell.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun tap() { touch(MotionEvent.ACTION_DOWN); advance(20); touch(MotionEvent.ACTION_UP) }
    private fun swipe(x: Float, y: Float = 20f) {
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, x, y); touch(MotionEvent.ACTION_UP, x, y)
    }

    @Test fun defaultsHaveImmediateSingleClickAndOnlyHorizontalTrackActions() {
        tap(); assertEquals(1, opened)
        swipe(10f); swipe(270f); swipe(140f, 100f)
        assertEquals(listOf(IslandMusicGesture.LEFT, IslandMusicGesture.RIGHT), gestures)
        swipe(140f, -100f); assertEquals(1, dismissed)
        assertEquals(1, opened)
    }

    @Test fun platformDoubleTapExcludesBothSingleClicksButConfirmedSingleStillOpens() {
        shell.configureGestures(identity, IslandMusicActions(doubleTap = 85), true)
        tap(); assertEquals(0, opened)
        advance(80); tap()
        advance(ViewConfiguration.getDoubleTapTimeout().toLong()+1)
        assertEquals(listOf(IslandMusicGesture.DOUBLE_TAP), gestures)
        assertEquals(0, opened)
        advance(400); tap(); advance(ViewConfiguration.getDoubleTapTimeout().toLong()+1)
        assertEquals(1, opened)
    }

    @Test fun longPressAndDragAreExclusiveAndDownCanRunOneConfiguredAction() {
        shell.configureGestures(identity, IslandMusicActions(down = 18, longPress = 19), true)
        touch(MotionEvent.ACTION_DOWN)
        advance(ViewConfiguration.getLongPressTimeout().toLong()+100)
        touch(MotionEvent.ACTION_MOVE, 10f); touch(MotionEvent.ACTION_UP, 10f)
        assertEquals(listOf(IslandMusicGesture.LONG_PRESS), gestures)
        assertEquals(0, opened)
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, 140f, 100f)
        advance(ViewConfiguration.getLongPressTimeout().toLong()+100)
        touch(MotionEvent.ACTION_UP, 140f, 100f)
        assertEquals(listOf(IslandMusicGesture.LONG_PRESS, IslandMusicGesture.DOWN), gestures)
    }

    @Test fun disabledTrackSwitchKeepsNonTrackActionsAndNoneKeepsFixedDismissal() {
        shell.configureGestures(identity, IslandMusicActions(left = 18, right = 19), false)
        swipe(10f); swipe(270f)
        assertEquals(listOf(IslandMusicGesture.LEFT, IslandMusicGesture.RIGHT), gestures)
        shell.configureGestures(identity, IslandMusicActions(1,1,1,1,1), false)
        swipe(10f); swipe(270f); swipe(140f, 100f)
        assertEquals(2, gestures.size)
        tap(); assertEquals(1, opened)
        swipe(140f,-100f); assertEquals(1, dismissed)
    }

    @Test fun canceledDiagonalChangedIdentityAndExitCannotCommitPendingInput() {
        shell.configureGestures(identity, IslandMusicActions(doubleTap = 85, longPress = 18), true)
        tap()
        shell.configureGestures(Any(), IslandMusicActions(doubleTap = 85, longPress = 18), true)
        advance(1000); assertEquals(0, opened)
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE,10f)
        touch(MotionEvent.ACTION_CANCEL); touch(MotionEvent.ACTION_UP,10f)
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE,220f,100f); touch(MotionEvent.ACTION_UP,220f,100f)
        assertTrue(gestures.isEmpty())
        touch(MotionEvent.ACTION_DOWN); shell.exit {}; advance(1000); touch(MotionEvent.ACTION_UP)
        assertTrue(gestures.isEmpty()); assertEquals(0, opened)
    }

    @Test fun accessibilityKeepsClickAndExposesOnlyConfiguredActions() {
        shell.configureGestures(identity, IslandMusicActions(left=1,right=1,down=18), true)
        val node = shell.createAccessibilityNodeInfo()
        assertTrue(node.isClickable)
        val custom = node.actionList.filter { it.label != null }
        assertEquals(1,custom.size)
        assertTrue(shell.performAccessibilityAction(custom.single().id,null))
        assertEquals(listOf(IslandMusicGesture.DOWN),gestures)
        assertTrue(shell.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null))
        assertEquals(1,opened)
        assertTrue(shell.performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS,null))
        assertEquals(1,dismissed)
    }
}
