package tv.withaibuild.customiuizer.mods.utils.gesture

import android.app.Application
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.GlobalActions

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GestureActionContextTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val view = View(application)
    private val config = GestureConfig(
        doubleTapLeftAction = 2, doubleTapAction = 3, doubleTapRightAction = 4,
        longPressAction = 5,
    )

    private fun machine(executor: GestureEffectExecutor = StatusBarGestureEffectExecutor()): GestureMachine {
        val deps = GestureDependencies(1, "context-test", Any(), 0, 0f, 1f, null, 80, 1000, 3f)
        return GestureMachine(
            "context-test", { config },
            object : GestureDependenciesResolver {
                override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any) =
                    GestureDependenciesResult.Ready(deps)
            },
            executor,
        ).also { it.prepare(1, view) }
    }

    private fun event(entry: GestureEntry, action: Int, down: Long, time: Long, x: Float) =
        GestureEvent(entry, action, down, time, x, 10f, 1, ownerId = 1)

    @Test fun statusBarAndControlCenterActionsReachTheRealBroadcastLauncherOnce() {
        for (entry in listOf(GestureEntry.STATUS_BAR_TOUCH, GestureEntry.CONTROL_CENTER_TOUCH)) {
            val machine = machine()
            val before = shadowOf(application).broadcastIntents.size
            for ((index, x) in listOf(100f, 500f, 900f).withIndex()) {
                val start = 1000L + index * 1000L
                for (down in listOf(start, start + 140L)) {
                    machine.dispatch(event(entry, GestureAction.DOWN, down, down, x), view)
                    machine.dispatch(event(entry, GestureAction.UP, down, down + 40L, x), view)
                }
            }
            val down = event(entry, GestureAction.DOWN, 5000, 5000, 500f)
            val up = event(entry, GestureAction.UP, 5000, 5800, 500f)
            machine.dispatch(down, view)
            machine.dispatch(up, view)
            machine.dispatch(up, view) // Duplicate dispatch must not repeat the action.
            assertEquals(
                listOf("ExpandNotifications", "ExpandSettings", "LockDevice", "GoToSleep")
                    .map { GlobalActions.ACTION_PREFIX + it },
                shadowOf(application).broadcastIntents.drop(before).map { it.action },
            )
        }
    }

    @Test fun executorReceivesOwnerAndNormalizesItsContextForTheLauncher() {
        val contexts = mutableListOf<Any?>()
        val launcher = GestureActionLauncher { context, _, _, _ -> contexts.add(context); true }
        val delegate = StatusBarGestureEffectExecutor(launcher)
        val machine = machine(object : GestureEffectExecutor {
            override fun execute(commands: List<GestureCommand>, dependencies: GestureDependencies,
                                 config: GestureConfig, context: Any?) {
                assertSame(view, context)
                delegate.execute(commands, dependencies, config, context)
            }
        })
        machine.dispatch(event(GestureEntry.STATUS_BAR_TOUCH, GestureAction.DOWN, 1000, 1000, 500f), view)
        machine.dispatch(event(GestureEntry.STATUS_BAR_TOUCH, GestureAction.UP, 1000, 1800, 500f), view)
        assertEquals(1, contexts.size)
        assertSame(application, contexts.single())
    }

    @Test fun cancelAndDetachedOwnerCannotLaunchAnAction() {
        val machine = machine()
        val before = shadowOf(application).broadcastIntents.size
        machine.dispatch(event(GestureEntry.STATUS_BAR_TOUCH, GestureAction.DOWN, 1000, 1000, 500f), view)
        machine.dispatch(event(GestureEntry.STATUS_BAR_TOUCH, GestureAction.CANCEL, 1000, 1700, 500f), view)
        machine.dispatch(event(GestureEntry.STATUS_BAR_TOUCH, GestureAction.UP, 1000, 1800, 500f), view)
        machine.dispatch(event(GestureEntry.STATUS_BAR_TOUCH, GestureAction.DOWN, 2000, 2000, 500f), view)
        machine.clear(1)
        machine.dispatch(event(GestureEntry.STATUS_BAR_TOUCH, GestureAction.UP, 2000, 2800, 500f), view)
        assertEquals(before, shadowOf(application).broadcastIntents.size)
    }
}
