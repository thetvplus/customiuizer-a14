package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessDisplayStub(var currentBrightness: Float = 0.5f) {
    fun getBrightness(displayId: Int): Float = currentBrightness
    fun setTemporaryBrightness(displayId: Int, value: Float) {}
    fun setBrightness(displayId: Int, value: Float) {}
}

class GestureMachineTest {

    private val geometry = GestureGeometry(
        screenWidth = 1080,
        density = 3.0f,
        statusBarHeight = 80,
        minBacklight = 0.0f,
        maxBacklight = 1.0f,
    )

    private val displayStub = BrightnessDisplayStub()

    private val deps = GestureDependencies(
        ownerId = 1,
        classLoaderIdentity = "cl-1",
        displayManager = displayStub,
        displayId = 0,
        minimumBacklight = 0.0f,
        maximumBacklight = 1.0f,
        audioManager = Any(),
        statusBarHeight = 80,
        screenWidth = 1080,
        density = 3.0f,
        getBrightnessMethod = BrightnessDisplayStub::class.java.getMethod("getBrightness", Int::class.java),
    )

    private val config = GestureConfig(
        singleAction = 2,
        brightnessSensitivityFactor = 0.618f,
        volumeSensitivityFactor = 1.0f,
    )

    private val dummyContext = Any()

    private fun machine(
        exec: FakeGestureEffectExecutor = FakeGestureEffectExecutor(),
        testConfig: GestureConfig = config,
        testDeps: GestureDependencies = deps,
        testArbiter: PhysicalGestureArbiter? = null,
    ): Pair<GestureMachine, FakeGestureEffectExecutor> {
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { testConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(
                    ownerId: Int,
                    classLoaderIdentity: String,
                    context: Any,
                ): GestureDependenciesResult = GestureDependenciesResult.Ready(testDeps)
            },
            effectExecutor = exec,
            arbiter = testArbiter,
        )
        return m to exec
    }

    private fun event(
        action: Int,
        x: Float = 0f,
        y: Float = 0f,
        eventTime: Long = 0L,
        downTime: Long = 0L,
        pointerCount: Int = 1,
        entry: GestureEntry = GestureEntry.STATUS_BAR_TOUCH,
        ownerId: Int = 1,
        deviceId: Int = 0,
        source: Int = 0,
    ) = GestureEvent(
        entry = entry,
        actionMasked = action,
        downTime = downTime,
        eventTime = eventTime,
        x = x,
        y = y,
        pointerCount = pointerCount,
        ownerId = ownerId,
        deviceId = deviceId,
        source = source,
    )

    @Test
    fun statusBarTouch_brightnessCycle() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
        assertEquals(1, exec.brightnessCommitted.size)
        assertEquals(1, exec.resets)
    }

    @Test
    fun interceptObservesButDoesNotExecute() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.observe(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.observe(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.observe(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)

        assertTrue(exec.brightnessApplied.isEmpty())
        assertTrue(exec.brightnessCommitted.isEmpty())
    }

    @Test
    fun sameEventInterceptThenTouch_allowsOnce() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.observe(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1, entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)

        val e = event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, ownerId = 1)
        m.observe(e.copy(entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
    }

    @Test
    fun interceptDown_thenTouchDown_stateOnlyChangesOnce() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.observe(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
    }

    @Test
    fun interceptMove_thenTouchMove_volumeExecutesOnce() {
        val (m, exec) = machine(testConfig = config.copy(singleAction = 3))

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.observe(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)

        assertEquals(1, exec.volumeAdjusted.size)
    }

    @Test
    fun interceptUp_thenTouchUp_brightnessCommitsOnce() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.observe(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertEquals(1, exec.brightnessCommitted.size)
    }

    @Test
    fun dependencyNotReady_returnsPassThrough() {
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { config },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(
                    ownerId: Int,
                    classLoaderIdentity: String,
                    context: Any,
                ): GestureDependenciesResult = GestureDependenciesResult.NotReady
            },
            effectExecutor = exec,
        )
        assertTrue(!m.prepare(1, dummyContext))
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        assertTrue(exec.brightnessApplied.isEmpty())
    }

    @Test
    fun ownerChange_isIndependent() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.prepare(2, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 2), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, ownerId = 2), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 60L, downTime = 0L, ownerId = 1), dummyContext)

        assertEquals(2, exec.brightnessApplied.size)
    }

    @Test
    fun brightnessStartsFromCurrentValue() {
        for (current in listOf(0.1f, 0.5f, 0.9f)) {
            val stub = BrightnessDisplayStub(current)
            val (m, exec) = machine(testDeps = deps.copy(displayManager = stub))

            m.prepare(1, dummyContext)
            m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
            m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
            m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

            val expected = (current + 200f / 1080f * config.brightnessSensitivityFactor)
                .coerceIn(0.0f, 1.0f)

            assertEquals("current=$current applied", 1, exec.brightnessApplied.size)
            assertEquals("current=$current", expected, exec.brightnessApplied[0], 0.0001f)
            assertEquals("current=$current committed", expected, exec.brightnessCommitted[0], 0.0001f)
        }
    }

    @Test
    fun brightnessReadFailure_passesThrough() {
        val (m, exec) = machine(testDeps = deps.copy(getBrightnessMethod = null))

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertTrue(exec.brightnessApplied.isEmpty())
        assertTrue(exec.brightnessCommitted.isEmpty())
        assertEquals(1, exec.resets)
    }

    @Test
    fun brightnessFirstMoveDoesNotJump() {
        val stub = BrightnessDisplayStub(0.1f)
        val (m, exec) = machine(testDeps = deps.copy(displayManager = stub))

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)

        val expected = 0.1f + 200f / 1080f * config.brightnessSensitivityFactor
        assertEquals(1, exec.brightnessApplied.size)
        assertEquals(expected, exec.brightnessApplied[0], 0.0001f)
    }

    @Test
    fun statusBarAndControlCenter_sharePhysicalOwner() {
        val arbiter = PhysicalGestureArbiter()
        val exec = FakeGestureEffectExecutor()
        val (statusBar, _) = machine(exec = exec, testArbiter = arbiter)
        val (controlCenter, _) = machine(exec = exec, testArbiter = arbiter)

        statusBar.prepare(1, dummyContext)
        controlCenter.prepare(2, dummyContext)

        val token = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 3, source = 0x1002)
        statusBar.dispatch(token, dummyContext)
        statusBar.dispatch(token.copy(actionMasked = GestureAction.MOVE, eventTime = 50L, x = 300f), dummyContext)

        val sharedUp = token.copy(actionMasked = GestureAction.UP, eventTime = 100L, x = 300f)
        controlCenter.dispatch(sharedUp.copy(ownerId = 2, entry = GestureEntry.CONTROL_CENTER_TOUCH), dummyContext)
        controlCenter.dispatch(sharedUp.copy(ownerId = 2, entry = GestureEntry.CONTROL_CENTER_TOUCH, actionMasked = GestureAction.MOVE, eventTime = 60L), dummyContext)
        statusBar.dispatch(sharedUp, dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
        assertEquals(1, exec.brightnessCommitted.size)
    }

    @Test
    fun cancelReleasesPhysicalOwner() {
        val arbiter = PhysicalGestureArbiter()
        val exec = FakeGestureEffectExecutor()
        val (m1, _) = machine(exec = exec, testArbiter = arbiter)
        val (m2, _) = machine(exec = exec, testArbiter = arbiter)

        m1.prepare(1, dummyContext)
        m2.prepare(2, dummyContext)

        val token = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 4, source = 0x1002)
        m1.dispatch(token, dummyContext)
        m1.dispatch(token.copy(actionMasked = GestureAction.CANCEL, eventTime = 50L), dummyContext)

        val sameToken = token.copy(ownerId = 2, entry = GestureEntry.CONTROL_CENTER_TOUCH)
        m2.dispatch(sameToken, dummyContext)
        m2.dispatch(sameToken.copy(actionMasked = GestureAction.MOVE, eventTime = 50L, x = 300f), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
    }

    @Test
    fun sameTimeDifferentDevice_isDifferentToken() {
        val arbiter = PhysicalGestureArbiter()
        val exec = FakeGestureEffectExecutor()
        val (m1, _) = machine(exec = exec, testArbiter = arbiter)
        val (m2, _) = machine(exec = exec, testArbiter = arbiter)

        m1.prepare(1, dummyContext)
        m2.prepare(2, dummyContext)

        m1.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 5, source = 0x1002, ownerId = 1), dummyContext)
        m1.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, deviceId = 5, source = 0x1002, ownerId = 1), dummyContext)
        m2.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 6, source = 0x1002, ownerId = 2, entry = GestureEntry.CONTROL_CENTER_TOUCH), dummyContext)
        m2.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, deviceId = 6, source = 0x1002, ownerId = 2, entry = GestureEntry.CONTROL_CENTER_TOUCH), dummyContext)

        assertEquals(2, exec.brightnessApplied.size)
    }

    @Test
    fun configChangeAppliesNextGestureOnly() {
        var currentConfig = config
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { currentConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(
                    ownerId: Int,
                    classLoaderIdentity: String,
                    context: Any,
                ): GestureDependenciesResult = GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = exec,
        )

        m.prepare(1, dummyContext)

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        currentConfig = config.copy(singleAction = 3)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 200L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 250L, downTime = 200L), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
        assertEquals(1, exec.volumeAdjusted.size)
    }

    @Test
    fun detachClearsOwnerState() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)

        m.clear(1)

        m.dispatch(event(GestureAction.MOVE, x = 400f, y = 10f, eventTime = 60L, downTime = 0L, ownerId = 1), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 100L, ownerId = 1), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 150L, downTime = 100L, ownerId = 1), dummyContext)

        assertEquals(2, exec.brightnessApplied.size)
    }

    @Test
    fun repeatedAttachDetach_doesNotGrowMaps() {
        val (m, exec) = machine()

        repeat(1000) { index ->
            m.prepare(1, dummyContext)
            m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = index.toLong(), ownerId = 1), dummyContext)
            m.clear(1)
        }

        assertTrue(exec.brightnessApplied.isEmpty())

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 1001L, ownerId = 1), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 1051L, downTime = 1001L, ownerId = 1), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
    }

    @Test
    fun upWithoutDown_doesNotTriggerDoubleTapOrLongPress() {
        val (m, exec) = machine()
        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.UP, x = 100f, y = 10f, eventTime = 100L), dummyContext)

        assertTrue(exec.doubleTaps.isEmpty())
        assertEquals(0, exec.longPresses)
        assertTrue(exec.brightnessApplied.isEmpty())
    }

    @Test
    fun cancelThenUp_doesNotCommitOrTriggerTap() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.CANCEL, x = 300f, y = 10f, eventTime = 60L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertEquals(0, exec.brightnessCommitted.size)
        assertTrue(exec.doubleTaps.isEmpty())
        assertEquals(0, exec.longPresses)
    }

    @Test
    fun brightnessSlideUp_onlyCommitsNoDoubleTapOrLongPress() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertEquals(1, exec.brightnessCommitted.size)
        assertTrue(exec.doubleTaps.isEmpty())
        assertEquals(0, exec.longPresses)
    }

    @Test
    fun volumeSlideUp_doesNotDoubleTapOrLongPress() {
        val (m, exec) = machine(testConfig = config.copy(singleAction = 3))

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertEquals(1, exec.volumeAdjusted.size)
        assertTrue(exec.doubleTaps.isEmpty())
        assertEquals(0, exec.longPresses)
    }

    @Test
    fun newDownAbortsOldSession() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)

        m.dispatch(event(GestureAction.DOWN, x = 500f, y = 10f, eventTime = 100L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 500f, y = 10f, eventTime = 150L, downTime = 100L), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
        assertEquals(0, exec.brightnessCommitted.size)
    }

    // --------------------------------------------------------------------------------------------
    // PhysicalGestureArbiter invariants
    // --------------------------------------------------------------------------------------------

    @Test
    fun idleMoveWithFreshToken_doesNotAcquire() {
        val arbiter = PhysicalGestureArbiter()
        val (m, _) = machine(testArbiter = arbiter)

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, deviceId = 7, source = 0x1002), dummyContext)

        assertEquals(0, arbiter.heldTokenCount())
        assertEquals(GestureState.IDLE, m.snapshot(1).state)
    }

    @Test
    fun validDown_acquiresExactlyOneToken() {
        val arbiter = PhysicalGestureArbiter()
        val (m, _) = machine(testArbiter = arbiter)

        m.prepare(1, dummyContext)
        val e = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 7, source = 0x1002)
        m.dispatch(e, dummyContext)

        assertEquals(1, arbiter.heldTokenCount())
        assertEquals(1, arbiter.tokensForOwner(1))
        val token = PhysicalGestureArbiter.Token(e.downTime, e.deviceId, e.source)
        assertEquals(1, arbiter.ownerOf(token))
    }

    @Test
    fun nonOwnerMove_cannotStealToken() {
        val arbiter = PhysicalGestureArbiter()
        val (m1, _) = machine(testArbiter = arbiter)
        val (m2, _) = machine(testArbiter = arbiter)

        m1.prepare(1, dummyContext)
        m2.prepare(2, dummyContext)

        val token = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 8, source = 0x1002)
        m1.dispatch(token, dummyContext)

        val before = m2.snapshot(2)
        m2.dispatch(token.copy(ownerId = 2, actionMasked = GestureAction.MOVE, eventTime = 50L, x = 300f, entry = GestureEntry.CONTROL_CENTER_TOUCH), dummyContext)

        assertEquals(1, arbiter.heldTokenCount())
        assertEquals(1, arbiter.tokensForOwner(1))
        assertEquals(0, arbiter.tokensForOwner(2))
        assertEquals(before, m2.snapshot(2))
    }

    @Test
    fun nonOwnerUp_cannotReleaseToken() {
        val arbiter = PhysicalGestureArbiter()
        val (m1, _) = machine(testArbiter = arbiter)
        val (m2, _) = machine(testArbiter = arbiter)

        m1.prepare(1, dummyContext)
        m2.prepare(2, dummyContext)

        val token = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 9, source = 0x1002)
        m1.dispatch(token, dummyContext)

        m2.dispatch(token.copy(ownerId = 2, actionMasked = GestureAction.UP, eventTime = 100L, entry = GestureEntry.CONTROL_CENTER_TOUCH), dummyContext)

        assertEquals(1, arbiter.heldTokenCount())
        assertEquals(1, arbiter.tokensForOwner(1))
        assertEquals(0, arbiter.tokensForOwner(2))
    }

    @Test
    fun ownerCancel_releasesToken() {
        val arbiter = PhysicalGestureArbiter()
        val (m, _) = machine(testArbiter = arbiter)

        m.prepare(1, dummyContext)
        val token = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 10, source = 0x1002)
        m.dispatch(token, dummyContext)
        m.dispatch(token.copy(actionMasked = GestureAction.CANCEL, eventTime = 50L), dummyContext)

        assertEquals(0, arbiter.heldTokenCount())
        assertEquals(GestureState.IDLE, m.snapshot(1).state)
    }

    @Test
    fun ownerUp_releasesToken() {
        val arbiter = PhysicalGestureArbiter()
        val (m, _) = machine(testArbiter = arbiter)

        m.prepare(1, dummyContext)
        val token = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 11, source = 0x1002)
        m.dispatch(token, dummyContext)
        m.dispatch(token.copy(actionMasked = GestureAction.UP, eventTime = 100L), dummyContext)

        assertEquals(0, arbiter.heldTokenCount())
        assertEquals(GestureState.IDLE, m.snapshot(1).state)
    }

    @Test
    fun newValidDown_releasesPreviousOwnerToken() {
        val arbiter = PhysicalGestureArbiter()
        val (m, _) = machine(testArbiter = arbiter)

        m.prepare(1, dummyContext)
        val tokenA = event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, deviceId = 12, source = 0x1002)
        m.dispatch(tokenA, dummyContext)

        val tokenB = event(GestureAction.DOWN, x = 500f, y = 10f, eventTime = 100L, deviceId = 13, source = 0x1002)
        m.dispatch(tokenB, dummyContext)

        assertEquals(1, arbiter.heldTokenCount())
        assertEquals(1, arbiter.tokensForOwner(1))
        val activeToken = PhysicalGestureArbiter.Token(tokenB.downTime, tokenB.deviceId, tokenB.source)
        assertEquals(1, arbiter.ownerOf(activeToken))
        val oldToken = PhysicalGestureArbiter.Token(tokenA.downTime, tokenA.deviceId, tokenA.source)
        assertNull(arbiter.ownerOf(oldToken))
    }

    @Test
    fun controlCenterDownOutsideStatusBar_doesNotAcquire() {
        val arbiter = PhysicalGestureArbiter()
        val (m, exec) = machine(testArbiter = arbiter)

        m.prepare(1, dummyContext)
        m.dispatch(
            event(
                GestureAction.DOWN,
                x = 100f,
                y = 200f,
                eventTime = 0L,
                deviceId = 14,
                source = 0x1002,
                entry = GestureEntry.CONTROL_CENTER_TOUCH,
            ),
            dummyContext,
        )

        assertEquals(0, arbiter.heldTokenCount())
        assertEquals(GestureState.IDLE, m.snapshot(1).state)
        assertTrue(exec.brightnessApplied.isEmpty())
    }

    @Test
    fun invalidControlCenterDown_doesNotBlockStatusBarOwner() {
        val arbiter = PhysicalGestureArbiter()
        val (statusBar, _) = machine(testArbiter = arbiter)
        val (controlCenter, _) = machine(testArbiter = arbiter)

        statusBar.prepare(1, dummyContext)
        controlCenter.prepare(2, dummyContext)

        val token = event(GestureAction.DOWN, x = 100f, y = 200f, eventTime = 0L, deviceId = 15, source = 0x1002, entry = GestureEntry.CONTROL_CENTER_TOUCH, ownerId = 2)
        controlCenter.dispatch(token, dummyContext)

        val statusBarDown = token.copy(ownerId = 1, y = 10f, entry = GestureEntry.STATUS_BAR_TOUCH)
        statusBar.dispatch(statusBarDown, dummyContext)

        assertEquals(1, arbiter.heldTokenCount())
        assertEquals(1, arbiter.tokensForOwner(1))
        assertEquals(0, arbiter.tokensForOwner(2))
    }

    @Test
    fun invalidDown_doesNotMutateAuthoritativeSnapshot() {
        val arbiter = PhysicalGestureArbiter()
        val (m, _) = machine(testArbiter = arbiter)

        m.prepare(1, dummyContext)
        val before = m.snapshot(1)
        m.dispatch(
            event(
                GestureAction.DOWN,
                x = 100f,
                y = 200f,
                eventTime = 0L,
                deviceId = 16,
                source = 0x1002,
                entry = GestureEntry.CONTROL_CENTER_TOUCH,
            ),
            dummyContext,
        )

        assertEquals(before, m.snapshot(1))
        assertEquals(0, arbiter.heldTokenCount())
    }

    @Test
    fun idlePointerDownWithFreshToken_doesNotAcquire() {
        val arbiter = PhysicalGestureArbiter()
        val (m, _) = machine(testArbiter = arbiter)

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.POINTER_DOWN, x = 100f, y = 10f, eventTime = 50L, downTime = 0L, pointerCount = 2, deviceId = 17, source = 0x1002), dummyContext)

        assertEquals(0, arbiter.heldTokenCount())
        assertEquals(GestureState.IDLE, m.snapshot(1).state)
    }

    // --------------------------------------------------------------------------------------------
    // Double-tap and long-press must use the action resolved at DOWN time
    // --------------------------------------------------------------------------------------------

    @Test
    fun doubleTapActionChangeMidGesture_usesDownSnapshot() {
        var currentConfig = config.copy(doubleTapAction = 2)
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { currentConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult =
                    GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = exec,
        )

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 0L), dummyContext)
        val up1 = event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L)
        m.dispatch(up1, dummyContext)

        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 200L, downTime = 200L), dummyContext)
        currentConfig = config.copy(doubleTapAction = 3)
        val up2 = event(GestureAction.UP, x = 300f, y = 10f, eventTime = 250L, downTime = 200L)
        m.dispatch(up2, dummyContext)

        assertEquals(1, exec.doubleTaps.size)
        assertEquals(2, exec.doubleTapActions[0])
    }

    @Test
    fun doubleTapLeftActionChangeMidGesture_usesDownSnapshot() {
        var currentConfig = config.copy(doubleTapLeftAction = 5)
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { currentConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult =
                    GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = exec,
        )

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        val up1 = event(GestureAction.UP, x = 100f, y = 10f, eventTime = 100L, downTime = 0L)
        m.dispatch(up1, dummyContext)

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 200L, downTime = 200L), dummyContext)
        currentConfig = config.copy(doubleTapLeftAction = 6)
        val up2 = event(GestureAction.UP, x = 100f, y = 10f, eventTime = 250L, downTime = 200L)
        m.dispatch(up2, dummyContext)

        assertEquals(1, exec.doubleTaps.size)
        assertEquals(DoubleTapPosition.LEFT, exec.doubleTaps[0])
        assertEquals(5, exec.doubleTapActions[0])
    }

    @Test
    fun doubleTapRightActionChangeMidGesture_usesDownSnapshot() {
        var currentConfig = config.copy(doubleTapRightAction = 7)
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { currentConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult =
                    GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = exec,
        )

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 900f, y = 10f, eventTime = 0L), dummyContext)
        val up1 = event(GestureAction.UP, x = 900f, y = 10f, eventTime = 100L, downTime = 0L)
        m.dispatch(up1, dummyContext)

        m.dispatch(event(GestureAction.DOWN, x = 900f, y = 10f, eventTime = 200L, downTime = 200L), dummyContext)
        currentConfig = config.copy(doubleTapRightAction = 8)
        val up2 = event(GestureAction.UP, x = 900f, y = 10f, eventTime = 250L, downTime = 200L)
        m.dispatch(up2, dummyContext)

        assertEquals(1, exec.doubleTaps.size)
        assertEquals(DoubleTapPosition.RIGHT, exec.doubleTaps[0])
        assertEquals(7, exec.doubleTapActions[0])
    }

    @Test
    fun longPressActionChangeMidGesture_usesDownSnapshot() {
        var currentConfig = config.copy(longPressAction = 4)
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { currentConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult =
                    GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = exec,
        )

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 0L), dummyContext)
        currentConfig = config.copy(longPressAction = 5)
        val up = event(GestureAction.UP, x = 300f, y = 10f, eventTime = 2000L, downTime = 0L)
        m.dispatch(up, dummyContext)

        assertEquals(1, exec.longPresses)
        assertEquals(4, exec.longPressActions[0])
    }

    @Test
    fun nextDoubleTapUsesRepublishedAction() {
        var currentConfig = config.copy(doubleTapAction = 2)
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { currentConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult =
                    GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = exec,
        )

        m.prepare(1, dummyContext)

        // First double-tap with action 2.
        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 200L, downTime = 200L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 250L, downTime = 200L), dummyContext)

        currentConfig = config.copy(doubleTapAction = 3)

        // Second double-tap with action 3.
        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 1000L, downTime = 1000L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 1100L, downTime = 1000L), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 1200L, downTime = 1200L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 1250L, downTime = 1200L), dummyContext)

        assertEquals(2, exec.doubleTaps.size)
        assertEquals(2, exec.doubleTapActions[0])
        assertEquals(3, exec.doubleTapActions[1])
    }

    @Test
    fun nextLongPressUsesRepublishedAction() {
        var currentConfig = config.copy(longPressAction = 4)
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { currentConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult =
                    GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = exec,
        )

        m.prepare(1, dummyContext)

        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 2000L, downTime = 0L), dummyContext)

        currentConfig = config.copy(longPressAction = 5)

        m.dispatch(event(GestureAction.DOWN, x = 300f, y = 10f, eventTime = 3000L, downTime = 3000L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 5000L, downTime = 3000L), dummyContext)

        assertEquals(2, exec.longPresses)
        assertEquals(4, exec.longPressActions[0])
        assertEquals(5, exec.longPressActions[1])
    }
}
