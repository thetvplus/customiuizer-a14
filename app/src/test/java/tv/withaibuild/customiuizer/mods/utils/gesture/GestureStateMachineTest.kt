package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureStateMachineTest {

    private val geometry = GestureGeometry(
        screenWidth = 1080,
        density = 3.0f,
        statusBarHeight = 80,
        minBacklight = 0.0f,
        maxBacklight = 1.0f,
        currentBrightness = 0.5f,
    )

    private val config = GestureConfig(
        singleAction = 2,
        brightnessSensitivityFactor = 0.618f,
        volumeSensitivityFactor = 1.0f,
    )

    private val volumeConfig = GestureConfig(
        singleAction = 3,
        brightnessSensitivityFactor = 0.618f,
        volumeSensitivityFactor = 1.0f,
    )

    private fun event(
        action: Int,
        x: Float = 0f,
        y: Float = 0f,
        eventTime: Long = 0L,
        downTime: Long = 0L,
        pointerCount: Int = 1,
        entry: GestureEntry = GestureEntry.STATUS_BAR_TOUCH,
        ownerId: Int = 1,
    ) = GestureEvent(
        entry = entry,
        actionMasked = action,
        downTime = downTime,
        eventTime = eventTime,
        x = x,
        y = y,
        pointerCount = pointerCount,
        ownerId = ownerId,
    )

    @Test
    fun downMoveUp_brightnessCycle() {
        val down = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L)
        val (s1, c1) = GestureStateMachine.process(GestureSnapshot(), down, config, geometry)
        assertEquals(GestureState.TRACKING, s1.state)
        assertEquals(GestureCommand.BeginTracking, c1.single())

        val move = event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L)
        val (s2, c2) = GestureStateMachine.process(s1, move, config, geometry)
        assertEquals(GestureState.SLIDING_BRIGHTNESS, s2.state)
        assertEquals(1, c2.size)
        val ratio = (c2[0] as GestureCommand.ApplyTemporaryBrightness).ratio
        val expected = 0.5f + (200f / 1080f) * 0.618f
        assertEquals(expected, ratio, 0.0001f)

        val up = event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L)
        val (_, c3) = GestureStateMachine.process(s2, up, config, geometry)
        val commit = c3.find { it is GestureCommand.CommitBrightness } as? GestureCommand.CommitBrightness
        assertTrue("expected CommitBrightness", commit != null)
        assertEquals(ratio, commit!!.ratio, 0.0001f)
        assertTrue(c3.any { it is GestureCommand.Reset })
    }

    @Test
    fun downCancel_resetsToIdle() {
        val down = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L)
        val (s1, _) = GestureStateMachine.process(GestureSnapshot(), down, config, geometry)
        val cancel = event(GestureAction.CANCEL, x = 100f, y = 10f, eventTime = 50L)
        val (s2, c) = GestureStateMachine.process(s1, cancel, config, geometry)
        assertEquals(GestureState.IDLE, s2.state)
        assertEquals(GestureCommand.Reset, c.single())
    }

    @Test
    fun moveBelowThreshold_doesNothing() {
        val down = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L)
        val (s1, _) = GestureStateMachine.process(GestureSnapshot(), down, config, geometry)
        val move = event(GestureAction.MOVE, x = 120f, y = 10f, eventTime = 50L)
        val (s2, c) = GestureStateMachine.process(s1, move, config, geometry)
        assertEquals(GestureState.TRACKING, s2.state)
        assertTrue(c.isEmpty())
    }

    @Test
    fun volumeSlide_emitsAdjustVolume() {
        val down = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L)
        val (s1, _) = GestureStateMachine.process(GestureSnapshot(), down, volumeConfig, geometry)
        val move = event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L)
        val (s2, c) = GestureStateMachine.process(s1, move, volumeConfig, geometry)
        assertEquals(GestureState.SLIDING_VOLUME, s2.state)
        val adjust = c.single() as GestureCommand.AdjustVolume
        assertTrue(adjust.raise)
    }

    @Test
    fun pointerDownAndUp_updatesActivePointerCount() {
        val down = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, pointerCount = 1)
        val (s1, _) = GestureStateMachine.process(GestureSnapshot(), down, config, geometry)
        assertEquals(1, s1.session.startPointerCount)

        val ptrDown = event(GestureAction.POINTER_DOWN, x = 100f, y = 10f, eventTime = 50L, pointerCount = 2)
        val (s2, _) = GestureStateMachine.process(s1, ptrDown, config, geometry)
        assertEquals(2, s2.session.startPointerCount)

        // Android reports pointerCount=2 for POINTER_UP while the active count after is 1.
        val ptrUp = event(GestureAction.POINTER_UP, x = 100f, y = 10f, eventTime = 60L, pointerCount = 2)
        val (s3, _) = GestureStateMachine.process(s2, ptrUp, config, geometry)
        assertEquals(1, s3.session.startPointerCount)

        // Final UP: raw pointerCount is 1, active count after release is 0.
        val up = event(GestureAction.UP, x = 100f, y = 10f, eventTime = 70L, pointerCount = 1)
        val (s4, _) = GestureStateMachine.process(s3, up, config, geometry)
        assertEquals(0, s4.session.startPointerCount)
    }

    @Test
    fun doubleTap_triggersForTwoQuickUps() {
        // First down/up sets lastTouch state.
        val down1 = event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 0L)
        val (s1, _) = GestureStateMachine.process(GestureSnapshot(), down1, config, geometry)
        val up1 = event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L)
        val (s2, _) = GestureStateMachine.process(s1, up1, config, geometry)

        val down2 = event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 200L)
        val (s3, _) = GestureStateMachine.process(s2, down2, config, geometry)
        val up2 = event(GestureAction.UP, x = 300f, y = 10f, eventTime = 300L)
        val (_, c) = GestureStateMachine.process(s3, up2, config, geometry)
        assertTrue(c.any { it is GestureCommand.TriggerDoubleTap })
    }

    @Test
    fun longPress_triggersForSlowHold() {
        val down = event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 0L)
        val (s1, _) = GestureStateMachine.process(GestureSnapshot(), down, config, geometry)
        val up = event(GestureAction.UP, x = 300f, y = 10f, eventTime = 2000L)
        val (_, c) = GestureStateMachine.process(s1, up, config, geometry)
        assertTrue(c.any { it is GestureCommand.TriggerLongPress })
    }

    @Test
    fun newDownResetsOldGesture() {
        val down1 = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L)
        val (s1, _) = GestureStateMachine.process(GestureSnapshot(), down1, config, geometry)
        val down2 = event(GestureAction.DOWN, x = 500f, y = 10f, eventTime = 50L)
        val (s2, c) = GestureStateMachine.process(s1, down2, config, geometry)
        assertEquals(GestureState.TRACKING, s2.state)
        assertEquals(500f, s2.session.startX)
        assertEquals(GestureCommand.BeginTracking, c.single())
    }

    @Test
    fun controlCenterBelowStatusBar_doesNotTrack() {
        val down = event(
            GestureAction.DOWN,
            x = 100f,
            y = 200f,
            eventTime = 0L,
            entry = GestureEntry.CONTROL_CENTER_TOUCH,
        )
        val (s, c) = GestureStateMachine.process(GestureSnapshot(), down, config, geometry)
        assertEquals(GestureState.IDLE, s.state)
        assertEquals(GestureCommand.PassThrough, c.single())
    }

    private fun List<GestureCommand>.single(): GestureCommand {
        assertEquals(1, size)
        return first()
    }
}
