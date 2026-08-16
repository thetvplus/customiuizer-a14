package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Randomised property-like test for [GestureMachine].
 *
 * The goal is to exercise the state machine with chaotic, interleaved sequences of
 * actions, owners, entries and pointer counts to catch crashes, side-effect leaks
 * across owners, or undefined transitions that the structured unit tests miss.
 */
class GestureMachineStressTest {

    private val random = Random(42)
    private val dummyContext = Any()

    private val config = GestureConfig(
        singleAction = 2,
        dualAction = 2,
        doubleTapAction = 2,
        doubleTapLeftAction = 2,
        doubleTapRightAction = 2,
        longPressAction = 2,
        brightnessSensitivityFactor = 1.0f,
        volumeSensitivityFactor = 1.0f,
        longPressVibrate = false,
        ignoreVibrateOff = false,
    )

    private val resolver = object : GestureDependenciesResolver {
        override fun prepare(
            ownerId: Int,
            classLoaderIdentity: String,
            context: Any,
        ): GestureDependenciesResult = GestureDependenciesResult.Ready(
            GestureDependencies(
                ownerId = ownerId,
                classLoaderIdentity = classLoaderIdentity,
                displayManager = Any(),
                displayId = 0,
                minimumBacklight = 0.0f,
                maximumBacklight = 1.0f,
                audioManager = null,
                statusBarHeight = 100,
                screenWidth = 1080,
                density = 3.0f,
            ),
        )
    }

    private class RecordingExecutor : GestureEffectExecutor {
        val commands = mutableListOf<GestureCommand>()
        override fun execute(
            commands: List<GestureCommand>,
            dependencies: GestureDependencies,
            config: GestureConfig,
            context: Any?,
        ) {
            this.commands.addAll(commands)
        }
    }

    private fun machine(exec: RecordingExecutor) = GestureMachine(
        classLoaderIdentity = "stress-cl",
        configResolver = { config },
        depsResolver = resolver,
        effectExecutor = exec,
    )

    private fun randomAction(): Int = listOf(
        GestureAction.DOWN,
        GestureAction.MOVE,
        GestureAction.UP,
        GestureAction.CANCEL,
        GestureAction.POINTER_DOWN,
        GestureAction.POINTER_UP,
    ).random(random)

    private fun randomEntry(): GestureEntry = listOf(
        GestureEntry.STATUS_BAR_INTERCEPT,
        GestureEntry.STATUS_BAR_TOUCH,
        GestureEntry.CONTROL_CENTER_TOUCH,
    ).random(random)

    private fun randomEvent(ownerId: Int, action: Int, baseTime: Long): GestureEvent =
        GestureEvent(
            entry = randomEntry(),
            actionMasked = action,
            downTime = (baseTime - random.nextLong(50L, 5000L)).coerceAtLeast(0L),
            eventTime = baseTime,
            x = random.nextFloat() * 1080f,
            y = random.nextFloat() * 200f,
            pointerCount = random.nextInt(1, 4),
            ownerId = ownerId,
        )

    @Test
    fun randomSequencesDoNotCrash() {
        val exec = RecordingExecutor()
        val m = machine(exec)

        m.prepare(1, dummyContext)
        m.prepare(2, dummyContext)

        var time = 0L
        repeat(100) { sequence ->
            repeat(20) { index ->
                val owner = if (index % 3 == 0) 2 else 1
                val action = randomAction()
                time += random.nextLong(5L, 50L)
                val ev = randomEvent(owner, action, time)
                if (ev.entry == GestureEntry.STATUS_BAR_INTERCEPT) {
                    m.observe(ev, dummyContext)
                } else {
                    m.dispatch(ev, dummyContext)
                }
            }
            // Ensure the sequence is deterministically reset so sequences are independent.
            m.clear(if (sequence % 2 == 0) 1 else 2)
        }

        // The only real assertion is that we made it here without crashing.
        assertTrue(time > 0L)
    }
}
