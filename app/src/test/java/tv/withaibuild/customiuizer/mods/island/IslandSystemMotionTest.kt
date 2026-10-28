package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandSystemMotionTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var activity: ActivityController<Activity>
    private lateinit var owner: IslandActivityController
    private lateinit var shell: IslandActivityView
    private val native = Native()
    private var adds = 0
    private var removes = 0
    private var latestParams: WindowManager.LayoutParams? = null
    private val window = Proxy.newProxyInstance(WindowManager::class.java.classLoader,
        arrayOf(WindowManager::class.java)) { _, method, args ->
        when (method.name) {
            "getCurrentWindowMetrics" -> WindowMetrics(Rect(0, 0, 1080, 2400), WindowInsets.Builder().build())
            "addView" -> {
                shell = (args!![0] as IslandTouchWindow).shell
                val params = args[1] as WindowManager.LayoutParams
                latestParams = params
                activity.get().setContentView(args[0] as View, FrameLayout.LayoutParams(params.width, params.height))
                layoutShell(params)
                adds++
                null
            }
            "removeViewImmediate" -> {
                val view = args!![0] as View
                (view.parent as ViewGroup).removeView(view)
                removes++
                null
            }
            "updateViewLayout" -> {
                val params = args!![1] as WindowManager.LayoutParams
                latestParams = params
                layoutShell(params)
                null
            }
            "toString" -> "AttachedIslandTestWindow"
            else -> error("Unexpected WindowManager call: ${method.name}")
        }
    } as WindowManager
    private val context get() = object : ContextWrapper(activity.get()) {
        override fun getSystemService(name: String): Any? =
            if (name == Context.WINDOW_SERVICE) window else super.getSystemService(name)
    }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put("system_toast_capsule", true)
            put("system_island_media", false)
            put("system_island_simplified", false)
        }
        owner = IslandActivityController(context, prefs, IslandOptions.read(prefs), native, dispatcher)
        owner.connectSafety(Any())
        drain()
        assertTrue(ValueAnimator.areAnimatorsEnabled())
        ShadowChoreographer.setPaused(true)
    }

    @After fun cleanup() {
        if (::owner.isInitialized) owner.dispose()
        scheduler.runCurrent()
        ShadowChoreographer.setPaused(false)
        if (::activity.isInitialized) activity.pause().stop().destroy()
        Dispatchers.resetMain()
    }

    @Test fun aCausalBurstExitsOnceAndNeverAttachesItsConsequencesAfterTheExit() {
        val exit = startBurstAndReachExit()
        assertEquals(IslandMotion.EXIT, exit.duration)
        advance(exit.duration + 100)
        assertSame(exit, motion())
        assertEquals(1, adds)
        assertEquals(0, removes)
        exit.end()
        advance(5000)
        assertEquals(1, adds)
        assertEquals(1, removes)
        assertFalse(shell.isAttachedToWindow)
    }

    @Test fun lockDuringExitAndAnOldCompletionCannotDetachANewActivity() {
        val oldExit = startBurstAndReachExit()
        oldExit.currentPlayTime = oldExit.duration / 2
        native.allowed = false; native.changed!!(); drain()
        assertEquals(1, removes)
        native.allowed = true; native.changed!!(); drain()
        owner.systemActivity(IslandSystemActivity(IslandSystemKind.NFC, 1))
        advance(IslandSystemFeedback.COLLECT)
        assertEquals(2, adds)
        assertLabel(IslandSystemKind.NFC, 1)
        val entrance = motion()
        oldExit.end()
        assertSame(entrance, motion())
        assertEquals(1, removes)
        assertTrue(shell.isAttachedToWindow)
    }

    private fun startBurstAndReachExit(): ValueAnimator {
        owner.systemActivity(IslandSystemActivity(IslandSystemKind.AIRPLANE, 1))
        owner.systemActivity(IslandSystemActivity(IslandSystemKind.BLUETOOTH, 0))
        advance(IslandSystemFeedback.COLLECT)
        assertEquals(1, adds)
        assertTrue(shell.isAttachedToWindow)
        assertLabel(IslandSystemKind.AIRPLANE, 1)
        motion().end()
        advance(IslandMotion.ENTER + IslandSystemFeedback.DWELL)
        assertEquals(0, removes)
        return motion()
    }

    private fun assertLabel(kind: IslandSystemKind, value: Int) {
        assertTrue(shell.contentDescription.toString().contains(IslandSystemActivity(kind, value).content(context).text))
    }

    private fun motion(): ValueAnimator = getField(shell, "motion")

    private fun layoutShell(params: WindowManager.LayoutParams) {
        val root = shell.parent as IslandTouchWindow
        root.measure(View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, params.width, params.height)
    }

    private fun advance(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        scheduler.runCurrent()
    }

    private fun drain() {
        shadowOf(Looper.getMainLooper()).idle()
        scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        scheduler.runCurrent()
    }

    private class Native : IslandNativePort {
        var allowed = true
        var changed: (() -> Unit)? = null
        override fun controller(owner: Any) = null
        override fun metadata(owner: Any) = null
        override fun isAllowed(owner: Any) = allowed
        override fun isShadeBlocked(owner: Any) = false
        override fun trust(params: WindowManager.LayoutParams) = Unit
        override fun listen(owner: Any, changed: () -> Unit): Any { this.changed = changed; return Any() }
        override fun unlisten(owner: Any, listener: Any) = Unit
    }
}
