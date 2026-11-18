package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Runs the production controller with a virtual Main dispatcher and real IO workers.
 * The blocking resolver getter is inside the production provider-query mutex. It
 * models synchronous provider work that coroutine cancellation cannot interrupt,
 * then throws an ordinary failure so Android's stub Cursor/TextView need no model.
 * This tests query ownership and scheduling, not provider data or rendered text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StepCounterRefreshLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val probe = QueryProbe()
    private val scopes = ArrayList<Job>()
    private val attempts = ArrayList<QueryAttempt>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        resetController()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        attempts.forEach { it.release.countDown() }
        try {
            awaitSettled(*scopes.toTypedArray())
            assertEquals("provider work must not overlap", 1.coerceAtMost(probe.calls.get()), probe.maxActive.get())
            assertTrue("no provider latch may time out", attempts.none { it.timedOut })
        } finally {
            resetController()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun bindingADetachedViewDoesNotActivateReceiversOrRefresh() {
        val context = QueryContext(probe)
        initialize(context)
        val view = AttachedTextView()
        StepCounterController.bindStepView(view)
        scheduler.runCurrent()
        assertTrue(context.receivers.isEmpty())
        assertEquals(0, activeJobs())
        assertEquals(0, probe.calls.get())
    }

    @Test
    fun secondAttachmentKeepsTheFirstRefreshDeadline() {
        val context = QueryContext(probe)
        initialize(context)
        val first = attachView()
        scheduler.runCurrent()
        scheduler.advanceTimeBy(2_500)
        val second = attachView()
        val query = blockNext(context)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(500)
        scheduler.runCurrent()

        awaitEntered(query)
        query.release.countDown()
        awaitSettled(currentJob())
        assertEquals(1, probe.calls.get())
        assertEquals(first.listeners, second.listeners)
    }

    @Test
    fun ticksReplaceTheInitialDelayAndCoalesceWhileProviderIsBlocked() {
        val context = QueryContext(probe)
        initialize(context)
        val firstView = attachView()
        val query = blockNext(context)
        tick(context)
        awaitEntered(query)
        assertEquals("A real refresh supersedes the initial delay", 1, activeJobs())

        repeat(32) {
            tick(context)
            StepCounterController.onScreenStateChanged(true)
        }
        val secondView = attachView()
        assertEquals("A second attached view needs no delayed work", 1, activeJobs())
        advanceAttachDelay()

        assertEquals("bursts must retain one refresh, not mutex waiters", 1, activeJobs())
        assertEquals(1, probe.calls.get())
        query.release.countDown()
        awaitSettled(currentJob())
        assertEquals("old events must not replay after a slow query", 1, probe.calls.get())

        // An ordinary provider failure must not leave single-flight permanently busy.
        tick(context)
        awaitSettled(currentJob())
        assertEquals(2, probe.calls.get())
        assertEquals(1, firstView.listeners.size)
        assertEquals(1, secondView.listeners.size)
    }

    @Test
    fun screenOffCancelsRunningAndDelayedWorkAndScreenOnCanRefreshAgain() {
        val context = QueryContext(probe)
        initialize(context)
        val firstView = attachView()
        val query = blockNext(context)
        advanceAttachDelay()
        awaitEntered(query)
        val secondView = attachView()
        repeat(8) { tick(context) }

        screen(false)
        scheduler.runCurrent()
        assertEquals("screen off must cancel both owned jobs", 0, activeJobs())
        query.release.countDown()
        awaitSettled(currentJob())
        scheduler.advanceTimeBy(10_000)
        tick(context)
        scheduler.runCurrent()
        assertEquals("screen off must not query or replay delayed work", 1, probe.calls.get())

        val resumed = blockNext(context)
        screen(true)
        scheduler.runCurrent()
        awaitEntered(resumed)
        resumed.release.countDown()
        awaitSettled(currentJob())
        assertEquals(2, probe.calls.get())
        assertFalse(firstView.listeners.isEmpty())
        assertFalse(secondView.listeners.isEmpty())
    }

    @Test
    fun lastDetachCancelsRefreshReleasesReceiversAndReattachCanRefresh() {
        val context = QueryContext(probe)
        initialize(context)
        val view = attachView()
        val query = blockNext(context)
        advanceAttachDelay()
        awaitEntered(query)
        repeat(8) { tick(context) }

        view.detach()
        scheduler.runCurrent()
        assertEquals("last detach must cancel the refresh", 0, activeJobs())
        assertTrue("last detach must release screen and tick receivers", context.receivers.isEmpty())
        query.release.countDown()
        awaitSettled(currentJob())
        tick(context)
        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()
        assertEquals("detached views must not retain queued queries", 1, probe.calls.get())

        val resumed = blockNext(context)
        view.attach()
        advanceAttachDelay()
        awaitEntered(resumed)
        resumed.release.countDown()
        awaitSettled(currentJob())
        assertEquals(2, probe.calls.get())
    }

    @Test
    fun replacingContextCancelsOldQueueButPreservesSerializationOfSynchronousWork() {
        val oldContext = QueryContext(probe)
        initialize(oldContext)
        val view = attachView()
        val oldQuery = blockNext(oldContext)
        advanceAttachDelay()
        awaitEntered(oldQuery)
        repeat(8) { tick(oldContext) }
        val oldJob = currentJob()

        val replacement = QueryContext(probe)
        val newQuery = blockNext(replacement)
        initialize(replacement)
        screen(true)
        scheduler.runCurrent()
        assertFalse("replacement cancels the old scope", oldJob.isActive)
        assertTrue("old context releases every receiver", oldContext.receivers.isEmpty())
        assertEquals("new query must wait for uncancellable old provider work", 1, probe.calls.get())
        assertEquals(1, activeJobs())

        oldQuery.release.countDown()
        awaitEntered(newQuery)
        newQuery.release.countDown()
        awaitSettled(oldJob, currentJob())
        assertEquals("only old in-flight and new current request may execute", 2, probe.calls.get())
        assertEquals(1, probe.maxActive.get())
        assertEquals(1, view.listeners.size)
    }

    @Test
    fun screenOffAndLastDetachCancelAnAttachDelayBeforeItQueries() {
        val context = QueryContext(probe)
        initialize(context)
        val view = attachView()
        scheduler.runCurrent()
        screen(false)
        scheduler.advanceTimeBy(3_000)
        scheduler.runCurrent()
        assertEquals(0, probe.calls.get())

        view.detach()
        // ScreenStateController resets its unknown state to on after the last listener.
        view.attach()
        scheduler.runCurrent()
        view.detach()
        scheduler.advanceTimeBy(3_000)
        scheduler.runCurrent()
        assertEquals("a cancelled attachment delay must never reach the provider", 0, probe.calls.get())
        assertEquals(0, activeJobs())
        assertTrue(context.receivers.isEmpty())
    }

    private fun initialize(context: QueryContext) {
        StepCounterController.initContext(context)
        scopes.add(currentJob())
    }

    @Test
    fun failedScreenRegistrationCanRetryTheSameViewAttachment() {
        val context = QueryContext(probe)
        initialize(context)
        context.registerFailure = IllegalStateException("screen registration unavailable")
        val view = AttachedTextView(initiallyAttached = true)
        try {
            StepCounterController.bindStepView(view)
            fail("registration must report the failure")
        } catch (_: IllegalStateException) {
            assertFalse("failed registration must not be committed",
                controllerField("screenStateRegistered").getBoolean(null))
        }
        context.registerFailure = null
        view.attach()
        assertEquals("the same view must be able to acquire screen and tick receivers", 2, context.receivers.size)
        view.detach()
        assertTrue(context.receivers.isEmpty())
    }

    private fun attachView() = AttachedTextView(initiallyAttached = true).also { StepCounterController.bindStepView(it) }

    private fun blockNext(context: QueryContext) = QueryAttempt().also {
        attempts.add(it)
        context.attempts.add(it)
    }

    private fun advanceAttachDelay() {
        scheduler.runCurrent()
        scheduler.advanceTimeBy(3_000)
        scheduler.runCurrent()
    }

    private fun screen(isOn: Boolean) {
        ScreenStateController::class.java.getDeclaredField("screenOn").apply {
            isAccessible = true
            setBoolean(null, isOn)
        }
        StepCounterController.onScreenStateChanged(isOn)
    }

    private fun tick(context: Context) {
        val receiver = controllerField("timeTickReceiver").get(null) as BroadcastReceiver
        receiver.onReceive(context, Intent())
        scheduler.runCurrent()
    }

    private fun currentJob(): Job =
        (controllerField("scope").get(null) as CoroutineScope).coroutineContext[Job]!!

    private fun activeJobs(): Int = currentJob().children.count { it.isActive }

    private fun awaitEntered(attempt: QueryAttempt) {
        assertTrue("provider must start", attempt.entered.await(5, TimeUnit.SECONDS))
    }

    private fun awaitSettled(vararg jobs: Job) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            scheduler.runCurrent()
            if (jobs.all { it.children.none() }) return
            Thread.sleep(1)
        } while (System.nanoTime() < deadline)
        assertTrue("controller jobs must finish", jobs.all { it.children.none() })
    }

    @Suppress("UNCHECKED_CAST")
    private fun resetController() {
        (controllerField("scope").get(null) as CoroutineScope).cancel()
        ScreenStateController.removeListener(StepCounterController)
        (controllerField("stepViewList").get(null) as ArrayList<WeakReference<TextView>>).clear()
        controllerField("context").set(null, null)
        controllerField("timeTickReceiver").set(null, null)
        controllerField("timeTickRegistered").setBoolean(null, false)
        controllerField("screenStateRegistered").setBoolean(null, false)
        controllerField("stepsWithGoal").set(null, null)
    }

    private fun controllerField(name: String) =
        StepCounterController::class.java.getDeclaredField(name).apply { isAccessible = true }

    private class AttachedTextView(initiallyAttached: Boolean = false) : TextView(null) {
        private var attached = initiallyAttached
        val listeners = ArrayList<View.OnAttachStateChangeListener>()
        override fun isAttachedToWindow(): Boolean = attached
        override fun addOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
            listeners.add(listener)
        }
        fun detach() {
            attached = false
            listeners.forEach { it.onViewDetachedFromWindow(this) }
        }
        fun attach() {
            attached = true
            listeners.forEach { it.onViewAttachedToWindow(this) }
        }
    }

    private class QueryAttempt {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        @Volatile var timedOut = false
    }

    private class QueryProbe {
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
    }

    private class QueryContext(private val probe: QueryProbe) : Application() {
        var registerFailure: Throwable? = null
        val attempts = ConcurrentLinkedQueue<QueryAttempt>()
        val receivers = ArrayList<BroadcastReceiver>()
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? {
            registerFailure?.let { throw it }
            if (receiver != null) receivers.add(receiver)
            return null
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            receivers.remove(receiver)
        }
        override fun getContentResolver(): ContentResolver {
            probe.calls.incrementAndGet()
            val active = probe.active.incrementAndGet()
            probe.maxActive.accumulateAndGet(active, ::maxOf)
            try {
                attempts.poll()?.let { attempt ->
                    attempt.entered.countDown()
                    attempt.timedOut = !attempt.release.await(10, TimeUnit.SECONDS)
                }
                throw IllegalStateException("test provider unavailable")
            } finally {
                probe.active.decrementAndGet()
            }
        }
    }
}
