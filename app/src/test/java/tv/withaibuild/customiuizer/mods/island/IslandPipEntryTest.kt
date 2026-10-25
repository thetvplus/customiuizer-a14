package tv.withaibuild.customiuizer.mods.island

import android.app.ActivityManager
import android.content.ComponentName
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.view.SurfaceControl
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandPipEntryTest {
    class Transaction
    class Request(private val type: Int, private val task: ActivityManager.RunningTaskInfo?) {
        fun getType() = type
        fun getTriggerTask() = task
    }
    class Transition {
        fun augmentRequest(token: IBinder, request: Request, transaction: Transaction) {}
        fun onTransitionConsumed(token: IBinder, aborted: Boolean, transaction: SurfaceControl.Transaction?) {}
    }
    private val entry = IslandPipEntryAccess(IslandPipAccessTest.Organizer::class.java,
        Transition::class.java, Request::class.java, Transaction::class.java)
    private val access = IslandPipAccess(IslandPipAccessTest.Organizer::class.java,
        IslandPipAccessTest.Algorithm::class.java, entry)
    private val delivered = mutableListOf<IslandPipSnapshot>()
    private val observer = IslandPipObserver(access, delivered::add)
    private val transition = Transition()
    private val owner = IslandPipAccessTest.Organizer().apply { mPipTransitionController = transition }
    private fun task(display: Int = 0) = ActivityManager.RunningTaskInfo().apply {
        taskId = 17; topActivity = ComponentName("video.app", "Player")
        ReflectionHelpers.setField(this, "userId", 0)
        ReflectionHelpers.setField(this, "displayId", display)
    }

    @Test fun aConfirmedRequestBlocksBeforeAnyNativePipStateWithoutMutatingTheNativeTask() {
        observer.capture(owner)
        val info = task()
        observer.entryRequested(transition, Binder(), Request(10, info))
        assertTrue(observer.snapshot.pendingEntry)
        assertEquals(0, observer.snapshot.state)
        assertEquals(info.topActivity, observer.snapshot.component)
        assertEquals(17, observer.snapshot.taskId)
        assertEquals(0, owner.mPipTransitionState.mState)
        assertNull(owner.mTaskInfo)
    }

    @Test fun unrelatedTypesDisplaysOwnersAndMissingTasksNeverCreateAPendingEntry() {
        observer.capture(owner)
        val before = observer.snapshot
        for (request in listOf(Request(1, task()), Request(10, null), Request(10, task(1))))
            observer.entryRequested(transition, Binder(), request)
        observer.entryRequested(Transition(), Binder(), Request(10, task()))
        assertSame(before, observer.snapshot)
    }

    @Test fun nativeEntryReplacesThePendingLeaseAndItsOldTimerCannotClearAVisiblePip() {
        observer.capture(owner)
        observer.entryRequested(transition, Binder(), Request(10, task()))
        owner.mTaskInfo = task(); owner.mPipTransitionState.mState = 1
        observer.stateChanged(owner.mPipTransitionState)
        assertFalse(observer.snapshot.pendingEntry)
        owner.mPipTransitionState.mState = 4; observer.stateChanged(owner.mPipTransitionState)
        val visible = observer.snapshot
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandPipObserver.ENTRY_REQUEST_TIMEOUT + 1))
        assertSame(visible, observer.snapshot)
        assertEquals(4, observer.snapshot.state)
    }

    @Test fun cancellationAndADequeuedOldExpiryCannotReleaseANewerRequest() {
        observer.capture(owner)
        val old = Binder(); val current = Binder()
        observer.entryRequested(transition, old, Request(10, task()))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
        observer.entryRequested(transition, current, Request(10, task()))
        val newer = observer.snapshot
        observer.entryConsumed(transition, old)
        ReflectionHelpers.getField<Runnable>(observer, "expireEntry").run()
        assertSame(newer, observer.snapshot)
        observer.entryConsumed(transition, current)
        assertFalse(observer.snapshot.pendingEntry)
        assertNull(observer.snapshot.component)
        assertNull(ReflectionHelpers.getField<IBinder?>(observer, "pendingToken"))
    }

    @Test fun lostCancellationExpiresAndOwnerReplacementReleasesTheOnlyPendingToken() {
        observer.capture(owner)
        observer.entryRequested(transition, Binder(), Request(10, task()))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandPipObserver.ENTRY_REQUEST_TIMEOUT + 1))
        assertFalse(observer.snapshot.pendingEntry)
        assertNull(observer.snapshot.component)
        observer.entryRequested(transition, Binder(), Request(10, task()))
        observer.capture(IslandPipAccessTest.Organizer())
        val replacement = observer.snapshot
        observer.entryRequested(transition, Binder(), Request(10, task()))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandPipObserver.ENTRY_REQUEST_TIMEOUT + 1))
        assertSame(replacement, observer.snapshot)
        assertNull(ReflectionHelpers.getField<IBinder?>(observer, "pendingToken"))
    }

    @Test fun requestHookPreservesOneNativeCallAndNativeExceptions() {
        observer.capture(owner)
        var calls = 0
        fun chain(failure: Throwable? = null): XposedInterface.Chain = Proxy.newProxyInstance(
            XposedInterface.Chain::class.java.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "getThisObject" -> transition
                "getArgs" -> listOf(Binder(), Request(10, task()), Transaction())
                "proceed" -> { calls++; if (failure != null) throw failure; null }
                else -> error(method.name)
            }
        } as XposedInterface.Chain
        observer.requestHook().intercept(chain())
        assertEquals(1, calls)
        assertTrue(observer.snapshot.pendingEntry)
        val before = observer.snapshot
        for (failure in listOf(IllegalStateException("native"), OutOfMemoryError("fatal"))) {
            assertSame(failure, assertThrows(failure.javaClass) { observer.requestHook().intercept(chain(failure)) })
            assertSame(before, observer.snapshot)
        }
        assertEquals(3, calls)
    }
}
