package tv.withaibuild.customiuizer.mods.island

import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandPipAccessTest {
    private val access = IslandPipAccess(Organizer::class.java, Algorithm::class.java)
    private val delivered = ArrayList<IslandPipSnapshot>()
    private val observer = IslandPipObserver(access, delivered::add)
    private fun component(pkg: String = "video.app") = ComponentName(pkg, "$pkg.Player")
    private fun info(user: Int = 0) = ActivityInfo().apply { applicationInfo = ApplicationInfo().apply { uid = user * 100000 + 10001 } }
    private fun task(id: Int, pkg: String = "video.app", user: Int = 0) = ActivityManager.RunningTaskInfo().apply {
        taskId = id; topActivity = component(pkg); ReflectionHelpers.setField(this, "userId", user)
    }
    private fun transition(owner: Organizer, state: Int) { owner.mPipTransitionState.mState = state; observer.stateChanged(owner.mPipTransitionState) }

    @Test fun swipeIdentityPrecedesPinnedTaskAndCancelDoesNotReuseTheExitedTask() {
        val owner = Organizer()
        observer.capture(owner)
        transition(owner, 3)
        observer.entryChanged(owner.mPipBoundsState, component(), info())
        assertEquals(component(), observer.snapshot.component)
        assertNull(observer.snapshot.taskId)
        owner.mTaskInfo = task(17)
        transition(owner, 1)
        assertEquals(17, observer.snapshot.taskId)
        transition(owner, 3); transition(owner, 4); transition(owner, 5); transition(owner, 0)
        assertNull(observer.snapshot.component)
        assertNull(observer.snapshot.taskId)
        // The ROM intentionally leaves mTaskInfo populated after exit.
        transition(owner, 3)
        assertNull(observer.snapshot.component)
        observer.entryChanged(owner.mPipBoundsState, component("second.video"), info(10))
        assertEquals("second.video", observer.snapshot.component!!.packageName)
        assertEquals(android.os.UserHandle.getUserHandleForUid(1000000), observer.snapshot.user)
        assertNull(observer.snapshot.taskId)
        transition(owner, 0)
        assertEquals(0, observer.snapshot.state)
        assertNull(observer.snapshot.component)
    }

    @Test fun nativeOwnerReplacementRejectsOldStateEntryAndTaskCallbacks() {
        val old = Organizer()
        val replacement = Organizer()
        observer.capture(old); transition(old, 3)
        observer.entryChanged(old.mPipBoundsState, component(), info())
        observer.capture(replacement)
        val current = observer.snapshot
        transition(old, 4)
        observer.entryChanged(old.mPipBoundsState, component("old.video"), info())
        old.mTaskInfo = task(99); observer.taskChanged(old)
        assertSame(current, observer.snapshot)
        observer.capture(replacement) // Chained constructors must not reset the current owner.
        assertSame(current, observer.snapshot)
        assertFalse(observer.javaClass.declaredFields.any { it.isAccessible = true; it.get(observer) === old || it.get(observer) === replacement })
    }

    @Test fun taskReplacementAndDisplayChangesPublishOnlyActualIdentityChanges() {
        val owner = Organizer()
        observer.capture(owner); owner.mTaskInfo = task(17); transition(owner, 1); transition(owner, 4)
        val same = observer.snapshot
        repeat(30) { observer.taskChanged(owner); observer.stateChanged(owner.mPipTransitionState) }
        assertSame(same, observer.snapshot)
        owner.mTaskInfo = task(18, "second.video", 10)
        observer.taskChanged(owner)
        assertEquals(18, observer.snapshot.taskId)
        assertEquals("second.video", observer.snapshot.component!!.packageName)
        owner.mPipDisplayLayoutState.mDisplayId = 1
        observer.taskChanged(owner)
        assertEquals(1, observer.snapshot.displayId)
        assertTrue(delivered.zipWithNext().all { (before, after) -> after.generation > before.generation })
    }

    @Test fun visibilityAndNativeStashChangesDeduplicateAndCannotOutliveTheirOwner() {
        val native = Organizer()
        observer.capture(native); native.mTaskInfo = task(17); transition(native, 4)
        observer.visibilityChanged(native, false)
        assertFalse(observer.snapshot.visible)
        val hidden = observer.snapshot
        observer.visibilityChanged(native, false)
        assertSame(hidden, observer.snapshot)
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, _ -> when (method.name) {
                "getThisObject" -> native.mPipBoundsState
                "getArgs" -> listOf(1)
                "proceed" -> { native.mPipBoundsState.setStashed(1); null }
                else -> error(method.name)
            } } as XposedInterface.Chain
        observer.stashHook().intercept(chain)
        assertTrue(observer.snapshot.stashed)
        observer.capture(Organizer())
        val replacement = observer.snapshot
        observer.visibilityChanged(native, true)
        observer.stashHook().intercept(chain)
        assertSame(replacement, observer.snapshot)
        assertTrue(replacement.visible)
        assertFalse(replacement.stashed)
    }

    @Test fun hookCallbacksObserveSuccessfulNativeCallsOnceAndDoNotConsumeNativeFailures() {
        val owner = Organizer()
        observer.capture(owner)
        var calls = 0
        fun chain(target: Any, args: List<Any?>, native: () -> Unit): XposedInterface.Chain =
            Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
                when (method.name) {
                    "getThisObject" -> target
                    "getArgs" -> args
                    "proceed" -> { calls++; native(); null }
                    else -> error("Unexpected chain call: ${method.name}")
                }
            } as XposedInterface.Chain
        observer.stateHook().intercept(chain(owner.mPipTransitionState, listOf(3)) { owner.mPipTransitionState.mState = 3 })
        observer.entryHook().intercept(chain(owner.mPipBoundsState, listOf(component(), info(), null, Algorithm())) {})
        assertEquals(component(), observer.snapshot.component)
        assertEquals(2, calls)
        val before = observer.snapshot
        for (failure in listOf(IllegalStateException("native"), OutOfMemoryError("fatal"))) {
            assertSame(failure, assertThrows(failure.javaClass) {
                observer.stateHook().intercept(chain(owner.mPipTransitionState, listOf(0)) { throw failure })
            })
            assertSame(before, observer.snapshot)
        }
        assertEquals(4, calls)
    }

    class Algorithm
    class Transition { @JvmField var mState = 0; fun setTransitionState(value: Int) { mState = value } }
    class Bounds {
        @JvmField var mStashedState = 0
        fun setStashed(value: Int) { mStashedState = value }
        fun setBoundsStateForEntry(component: ComponentName?, info: ActivityInfo?, params: PictureInPictureParams?, algorithm: Algorithm) = Unit
    }
    class Layout { @JvmField var mDisplayId = 0 }
    class Organizer {
        @JvmField var mPipTransitionController: Any? = null
        @JvmField val mPipTransitionState = Transition()
        @JvmField val mPipBoundsState = Bounds()
        @JvmField val mPipDisplayLayoutState = Layout()
        @JvmField var mTaskInfo: ActivityManager.RunningTaskInfo? = null
        fun onTaskInfoChanged(info: ActivityManager.RunningTaskInfo) { mTaskInfo = info }
    }
}
