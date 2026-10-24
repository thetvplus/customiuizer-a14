package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class GestureOwnershipLifecycleTest {

    private val context = Any()
    private val config = GestureConfig(singleAction = 2)

    private fun machine(
        identity: String,
        arbiter: PhysicalGestureArbiter,
        executor: GestureEffectExecutor,
    ): GestureMachine = GestureMachine(
        classLoaderIdentity = identity,
        configResolver = { config },
        depsResolver = object : GestureDependenciesResolver {
            override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any) =
                GestureDependenciesResult.Ready(
                    GestureDependencies(
                        ownerId = ownerId,
                        classLoaderIdentity = classLoaderIdentity,
                        displayManager = BrightnessDisplayStub(),
                        displayId = 0,
                        minimumBacklight = 0f,
                        maximumBacklight = 1f,
                        audioManager = null,
                        statusBarHeight = 80,
                        screenWidth = 1080,
                        density = 3f,
                        getBrightnessMethod = BrightnessDisplayStub::class.java.getMethod(
                            "getBrightness", Int::class.java,
                        ),
                    ),
                )
        },
        effectExecutor = executor,
        arbiter = arbiter,
    )

    private fun down(owner: Int, entry: GestureEntry) = GestureEvent(
        entry = entry,
        actionMasked = GestureAction.DOWN,
        downTime = 1000L + owner,
        eventTime = 1000L + owner,
        x = 100f,
        y = 10f,
        pointerCount = 1,
        ownerId = owner,
        deviceId = 7,
        source = 0x1002,
    )

    @Test
    fun clearingControlCenterPreservesStatusBarBrightnessCommit() {
        val arbiter = PhysicalGestureArbiter()
        val statusBarEffects = FakeGestureEffectExecutor()
        val controlCenterEffects = FakeGestureEffectExecutor()
        val statusBar = machine("status-bar", arbiter, statusBarEffects)
        val controlCenter = machine("plugin", arbiter, controlCenterEffects)
        val statusBarDown = down(1, GestureEntry.STATUS_BAR_TOUCH)
        val controlCenterDown = down(2, GestureEntry.CONTROL_CENTER_TOUCH)
        val otherControlCenterDown = down(3, GestureEntry.CONTROL_CENTER_TOUCH)
        statusBar.prepare(1, context)
        controlCenter.prepare(2, context)
        controlCenter.prepare(3, context)
        statusBar.dispatch(statusBarDown, context)
        controlCenter.dispatch(controlCenterDown, context)
        controlCenter.dispatch(otherControlCenterDown, context)
        statusBar.dispatch(statusBarDown.copy(actionMasked = GestureAction.MOVE, eventTime = 1100L, x = 400f), context)
        controlCenter.dispatch(controlCenterDown.copy(actionMasked = GestureAction.MOVE, eventTime = 1101L, x = 400f), context)
        assertEquals(3, arbiter.heldTokenCount())

        controlCenter.clear()
        controlCenter.clear()

        assertEquals(1, arbiter.tokensForOwner(1))
        assertEquals(0, arbiter.tokensForOwner(2))
        assertEquals(0, arbiter.tokensForOwner(3))
        assertNull(controlCenter.resolvedConfig(2))
        assertNull(controlCenter.resolvedConfig(3))
        assertEquals(GestureState.IDLE, controlCenter.snapshot(2).state)
        statusBar.dispatch(statusBarDown.copy(actionMasked = GestureAction.UP, eventTime = 1200L, x = 400f), context)
        assertEquals(statusBarEffects.brightnessApplied.single(), statusBarEffects.brightnessCommitted.single(), 0.0001f)
        controlCenter.dispatch(controlCenterDown.copy(actionMasked = GestureAction.UP, eventTime = 1201L, x = 400f), context)
        controlCenter.dispatch(otherControlCenterDown.copy(actionMasked = GestureAction.UP, eventTime = 1202L), context)
        assertEquals(0, controlCenterEffects.brightnessCommitted.size)
        assertEquals(0, arbiter.heldTokenCount())
    }

    @Test
    fun clearingUnpreparedMachineDoesNotReleaseOtherMachineToken() {
        val arbiter = PhysicalGestureArbiter()
        val statusBar = machine("status-bar", arbiter, FakeGestureEffectExecutor())
        val emptyPlugin = machine("plugin", arbiter, FakeGestureEffectExecutor())
        val event = down(1, GestureEntry.STATUS_BAR_TOUCH)
        statusBar.prepare(1, context)
        statusBar.dispatch(event, context)

        emptyPlugin.clear()

        assertEquals(1, arbiter.tokensForOwner(1))
        statusBar.dispatch(event.copy(actionMasked = GestureAction.CANCEL, eventTime = 1100L), context)
        assertEquals(0, arbiter.heldTokenCount())
    }

    @Test
    fun failedTerminalEffectStillReleasesOnlyItsOwnToken() {
        for (action in listOf(GestureAction.UP, GestureAction.CANCEL)) {
            for (failure in listOf(IllegalStateException("effect failed"), InternalError("fatal effect"))) {
                val arbiter = PhysicalGestureArbiter()
                val other = machine("status-bar", arbiter, FakeGestureEffectExecutor())
                val machine = machine("plugin", arbiter, GestureEffectExecutor { commands, _, _, _ ->
                    if (GestureCommand.Reset in commands) throw failure
                })
                other.prepare(1, context)
                machine.prepare(2, context)
                other.dispatch(down(1, GestureEntry.STATUS_BAR_TOUCH), context)
                val event = down(2, GestureEntry.CONTROL_CENTER_TOUCH)
                machine.dispatch(event, context)

                assertSame(failure, assertThrows(failure.javaClass) {
                    machine.dispatch(event.copy(actionMasked = action, eventTime = 1100L), context)
                })

                assertEquals(GestureState.IDLE, machine.snapshot(2).state)
                assertEquals(0, arbiter.tokensForOwner(2))
                assertEquals(1, arbiter.tokensForOwner(1))
            }
        }
    }
}
