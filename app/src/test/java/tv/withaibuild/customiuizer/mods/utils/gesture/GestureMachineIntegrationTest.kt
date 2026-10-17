package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayManagerStub(var currentBrightness: Float = 0.5f) {
    val temporaryCalls = mutableListOf<Pair<Int, Float>>()
    val commitCalls = mutableListOf<Pair<Int, Float>>()

    fun getBrightness(displayId: Int): Float = currentBrightness
    fun setTemporaryBrightness(displayId: Int, value: Float) { temporaryCalls.add(displayId to value) }
    fun setBrightness(displayId: Int, value: Float) { commitCalls.add(displayId to value) }
}

class GestureMachineIntegrationTest {

    private val dummyContext = Any()

    private val config = GestureConfig(
        singleAction = 2,
        brightnessSensitivityFactor = 0.618f,
        volumeSensitivityFactor = 1.0f,
    )

    private fun machineWith(stub: DisplayManagerStub): Pair<GestureMachine, DisplayManagerStub> {
        val deps = GestureDependencies(
            ownerId = 1,
            classLoaderIdentity = "integration",
            displayManager = stub,
            displayId = 0,
            minimumBacklight = 0.0f,
            maximumBacklight = 1.0f,
            audioManager = null,
            statusBarHeight = 80,
            screenWidth = 1080,
            density = 3.0f,
            getBrightnessMethod = DisplayManagerStub::class.java.getMethod("getBrightness", Int::class.java),
            setTemporaryBrightnessMethod = DisplayManagerStub::class.java.getMethod(
                "setTemporaryBrightness",
                Int::class.java,
                Float::class.javaPrimitiveType,
            ),
            setBrightnessMethod = DisplayManagerStub::class.java.getMethod(
                "setBrightness",
                Int::class.java,
                Float::class.javaPrimitiveType,
            ),
        )

        val m = GestureMachine(
            classLoaderIdentity = "integration",
            configResolver = { config },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(
                    ownerId: Int,
                    classLoaderIdentity: String,
                    context: Any,
                ): GestureDependenciesResult = GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = StatusBarGestureEffectExecutor(),
        )
        return m to stub
    }

    private fun event(
        action: Int,
        x: Float,
        eventTime: Long,
        downTime: Long = 0L,
    ) = GestureEvent(
        entry = GestureEntry.STATUS_BAR_TOUCH,
        actionMasked = action,
        downTime = downTime,
        eventTime = eventTime,
        x = x,
        y = 10f,
        pointerCount = 1,
        ownerId = 1,
    )

    @Test
    fun duplicateBrightnessValueIsSkipped() {
        val stub = DisplayManagerStub(0.5f)
        val (m, _) = machineWith(stub)

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, eventTime = 50L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, eventTime = 60L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, eventTime = 100L), dummyContext)

        assertEquals(1, stub.temporaryCalls.size)
        assertEquals(1, stub.commitCalls.size)
    }

    @Test
    fun realExecutorCommitsLatestBrightness() {
        val stub = DisplayManagerStub(0.5f)
        val (m, _) = machineWith(stub)

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, eventTime = 50L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 500f, eventTime = 60L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 500f, eventTime = 100L), dummyContext)

        val lastTemp = stub.temporaryCalls.last().second
        val commit = stub.commitCalls.single().second
        assertEquals(lastTemp, commit, 0.0001f)
    }
}
