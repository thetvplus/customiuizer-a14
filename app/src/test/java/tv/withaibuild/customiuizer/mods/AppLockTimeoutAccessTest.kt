package tv.withaibuild.customiuizer.mods

import android.os.Handler
import android.os.Looper
import android.util.ArrayMap
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AppLockTimeoutAccessTest {
    class State { @JvmField val mAccessControlLastCheck = ArrayMap<String, Long>() }
    class Service {
        @JvmField val mUserStateLock = Any()
        val states = mapOf(0 to State(), 10 to State())
        fun getUserStateLocked(user: Int): State = states.getValue(user)
    }
    class Impl(@JvmField val mService: Service) {
        @JvmField val mHandler = Handler(Looper.getMainLooper())
        fun scheduleForMaskObserver(pkg: String, user: Int) = Unit
    }
    private fun access(minutes: () -> Int) = requireNotNull(AppLockTimeoutAccess.prepare(Impl::class.java, Service::class.java, State::class.java, minutes))
    @Test fun extendsOnlyTheFreshNativeEntryAndItsMatchingNativeTaskForTheActualUser() {
        val host = Service(); val owner = Impl(host); val access = access { 5 }
        host.states.getValue(0).mAccessControlLastCheck["app"] = 500
        val current = host.states.getValue(10).mAccessControlLastCheck
        current["app"] = 1000; current["other"] = 2000
        val callback = Runnable {}
        val post = Chain(owner.mHandler, listOf(callback, "app_10", 60000L), true) { args ->
            assertNotNull(args); assertSame(callback, args!![0]); assertEquals("app_10", args[1]); assertEquals(300000L, args[2])
            assertEquals(241000L, current["app"])
        }
        val schedule = Chain(owner, listOf("app", 10)) { args ->
            assertNull(args)
            assertEquals(true, access.handlerDelayHook().intercept(post))
            java.util.concurrent.CompletableFuture.runAsync {
                val otherThread = Chain(owner.mHandler, listOf(callback, "app_10", 60000L)) { assertNull(it) }
                access.handlerDelayHook().intercept(otherThread)
                assertEquals(0, otherThread.ownerReads); assertEquals(0, otherThread.copies)
            }.join()
            // Unrelated work on the same thread, including the same handler, stays native.
            for ((handler, token, delay) in listOf(Triple(owner.mHandler, "other_10", 60000L), Triple(owner.mHandler, "app_10", 1000L), Triple(Handler(Looper.getMainLooper()), "app_10", 60000L))) {
                val other = Chain(handler, listOf(callback, token, delay)) { assertNull(it) }
                access.handlerDelayHook().intercept(other); assertEquals(0, other.copies)
                val failure = IllegalStateException("unrelated native scheduler")
                val rejected = Chain(handler, listOf(callback, token, delay)) { throw failure }
                assertSame(failure, assertThrows(IllegalStateException::class.java) { access.handlerDelayHook().intercept(rejected) })
                assertEquals(1, rejected.calls)
            }
        }
        synchronized(host.mUserStateLock) { access.scheduleHook().intercept(schedule) }
        assertEquals(1, schedule.calls); assertEquals(1, post.calls); assertEquals(1, post.copies)
        assertEquals(500L, host.states.getValue(0).mAccessControlLastCheck["app"]); assertEquals(2000L, current["other"])
        val later = Chain(owner.mHandler, listOf(callback, "app_10", 60000L)) { assertNull(it) }
        access.handlerDelayHook().intercept(later); assertEquals(0, later.ownerReads); assertEquals(0, later.copies)
    }
    @Test fun defaultAndCallsOutsideTheNativeLockKeepNativeValuesAndWork() {
        var minutes = 1; val access = access { minutes }; val host = Service(); val owner = Impl(host)
        host.states.getValue(10).mAccessControlLastCheck["app"] = 1000
        val default = Chain { assertNull(it) }
        access.scheduleHook().intercept(default); assertEquals(0, default.argReads); assertEquals(0, default.ownerReads)
        minutes = 5
        val unlocked = Chain(owner, listOf("app", 10)) { assertNull(it) }
        access.scheduleHook().intercept(unlocked)
        assertEquals(1000L, host.states.getValue(10).mAccessControlLastCheck["app"]); assertEquals(1, unlocked.calls)
    }
    @Test fun nativeFailureRollsBackOnlyTheLocalAdjustmentAndAlwaysClearsTheDelayScope() {
        val access = access { 3 }; val host = Service(); val owner = Impl(host)
        val map = host.states.getValue(0).mAccessControlLastCheck
        for (failure in listOf(IllegalStateException("native"), OutOfMemoryError("native fatal"))) {
            map["app"] = 1000
            val chain = Chain(owner, listOf("app", 0)) { throw failure }
            synchronized(host.mUserStateLock) {
                assertSame(failure, assertThrows(Throwable::class.java) { access.scheduleHook().intercept(chain) })
            }
            assertEquals(1, chain.calls); assertEquals(1000L, map["app"])
            val later = Chain { assertNull(it) }; access.handlerDelayHook().intercept(later)
            assertEquals(0, later.ownerReads)
        }
    }
    @Test fun malformedUserAndAbsentEntriesDoNotCreateUnlockRecordsOrChangeNativeResults() {
        val access = access { 60 }; val host = Service(); val owner = Impl(host)
        for (user in listOf(-1, 0, 10)) {
            val chain = Chain(owner, listOf("missing", user), false) { assertNull(it) }
            synchronized(host.mUserStateLock) { assertEquals(false, access.scheduleHook().intercept(chain)) }
            assertEquals(1, chain.calls)
        }
        assertTrue(host.states.values.all { it.mAccessControlLastCheck.isEmpty() })
    }
    private class Chain(private val owner: Any? = null, private val args: List<Any?> = emptyList(), val result: Any? = null, val native: (Array<Any?>?) -> Unit) : XposedInterface.Chain {
        var calls = 0; var copies = 0; var ownerReads = 0; var argReads = 0
        override fun getExecutable(): Executable = error("metadata must be cold")
        override fun getThisObject(): Any { ownerReads++; return owner ?: error("scope must not access owner") }
        override fun getArgs(): List<Any?> { copies++; return args }
        override fun getArg(index: Int): Any? { argReads++; return args[index] }
        override fun proceed(): Any? { calls++; native(null); return result }
        override fun proceed(args: Array<Any?>): Any? { calls++; native(args); return result }
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
