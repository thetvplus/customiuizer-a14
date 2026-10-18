package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/** Blocking provider acquisition models synchronous I/O that cancellation cannot interrupt. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeatherRefreshLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val calls = AtomicInteger()
    private val attempts = ArrayList<QueryAttempt>()
    private val scopes = ArrayList<Job>()
    private val clock = Clock()

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
            assertTrue("provider work must not time out", attempts.none { it.timedOut })
        } finally {
            resetController()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun slowProviderCoalescesTicksAndForcedUpdatesAndCanRefreshAgainAfterFailure() {
        val context = QueryContext()
        val query = blockNext(context)
        initialize(context)
        advanceInitialDelay()
        awaitEntered(query)

        repeat(32) { WeatherDataController.refreshWeatherData(false) }
        repeat(8) { WeatherDataController.refreshWeatherData(true) }
        scheduler.runCurrent()
        assertEquals("one request may run; ticks must not become mutex waiters", 1,
            currentJob().children.count { it.isActive })
        assertEquals(1, calls.get())

        query.release.countDown()
        awaitSettled(currentJob())
        assertEquals("old tick events must not replay after a slow provider", 1, calls.get())
        assertEquals("forced updates must be coalesced without being lost", 1, clock.updates)

        WeatherDataController.refreshWeatherData(false)
        awaitSettled(currentJob())
        assertEquals("ordinary failures must allow the next refresh", 2, calls.get())
    }

    @Test
    fun screenOffCancelsTheQueryAndRetainsTheForcedUpdateForScreenOn() {
        val context = QueryContext()
        val query = blockNext(context)
        initialize(context)
        advanceInitialDelay()
        awaitEntered(query)
        val oldJob = currentJob()

        screen(false)
        query.release.countDown()
        awaitSettled(oldJob)
        assertEquals("cancelled work must not update a clock", 0, clock.updates)
        WeatherDataController.refreshWeatherData(false)
        scheduler.runCurrent()
        assertEquals(1, calls.get())

        screen(true)
        awaitSettled(currentJob())
        assertEquals("screen on must finish the cancelled force refresh", 2, calls.get())
        assertEquals(1, clock.updates)
    }

    @Test
    fun replacingTheContextCancelsOldWorkAndQueriesOnlyTheNewOwner() {
        val oldContext = QueryContext()
        val oldQuery = blockNext(oldContext)
        initialize(oldContext)
        advanceInitialDelay()
        awaitEntered(oldQuery)
        repeat(8) { WeatherDataController.refreshWeatherData(false) }
        val oldJob = currentJob()

        val replacement = QueryContext()
        val newQuery = blockNext(replacement)
        initialize(replacement)
        advanceInitialDelay()
        assertFalse(oldJob.isActive)
        assertTrue("replacement must release the old tick receiver", oldContext.ticks.isEmpty())
        assertEquals("new work must wait for the uncancellable old query", 1, calls.get())

        oldQuery.release.countDown()
        awaitEntered(newQuery)
        newQuery.release.countDown()
        awaitSettled(oldJob, currentJob())
        assertEquals(2, calls.get())
        assertEquals("only the current owner may update the clock", 1, clock.updates)
    }

    @Test
    fun cancelledProviderResultIsClosedWithoutPublishingStaleWeather() {
        val app = RuntimeEnvironment.getApplication()
        val provider = WeatherProvider()
        provider.attachInfo(app, ProviderInfo().apply { authority = "weather" })
        ShadowContentResolver.registerProviderInternal("weather", provider)
        val context = QueryContext().apply { resolver = app.contentResolver }
        val query = blockNext(context)
        initialize(context)
        WeatherDataController.weatherInfo = "current weather"
        advanceInitialDelay()
        awaitEntered(query)
        val oldJob = currentJob()

        screen(false)
        query.release.countDown()
        awaitSettled(oldJob)

        assertEquals("cancelled I/O must not overwrite current state", "current weather", WeatherDataController.weatherInfo)
        assertEquals(0, clock.updates)
        assertTrue("cancelled synchronous queries must still close their cursor", provider.cursor?.isClosed == true)
    }

    @Test
    fun rateLimitedDiagnosticsMustStillPropagateFatalProviderErrors() {
        val query = WeatherDataController::class.java.getDeclaredMethod("queryWeather", Context::class.java).apply {
            isAccessible = true
        }
        val oom = OutOfMemoryError("injected")
        for ((failure, fatal) in listOf(
            ThreadDeath().let { it to it },
            InternalError("injected").let { it to it },
            ExecutionException(oom) to oom,
        )) {
            field("queryFailureLogged").setBoolean(null, true)
            val context = QueryContext().apply { providerFailure = failure }
            try {
                query.invoke(WeatherDataController, context)
                fail("log throttling must not hide a fatal error")
            } catch (wrapped: InvocationTargetException) {
                assertSame(fatal, wrapped.cause)
            }
        }
    }

    private fun initialize(context: QueryContext) {
        WeatherDataController.initContext(context, clock)
        scopes.add(currentJob())
    }

    private fun advanceInitialDelay() {
        scheduler.runCurrent()
        scheduler.advanceTimeBy(1800)
        scheduler.runCurrent()
    }

    private fun screen(isOn: Boolean) {
        ScreenStateController::class.java.getDeclaredField("screenOn").apply {
            isAccessible = true
            setBoolean(null, isOn)
        }
        WeatherDataController.onScreenStateChanged(isOn)
        scopes.add(currentJob())
    }

    private fun blockNext(context: QueryContext) = QueryAttempt().also {
        attempts.add(it)
        context.attempts.add(it)
    }

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
        assertTrue("controller work must finish", jobs.all { it.children.none() })
    }

    private fun currentJob(): Job =
        (field("controllerScope").get(null) as CoroutineScope).coroutineContext[Job]!!

    private fun resetController() {
        (field("controllerScope").get(null) as CoroutineScope).cancel()
        ScreenStateController.removeListener(WeatherDataController)
        for (name in listOf("context", "updateTarget", "timeTickReceiver")) field(name).set(null, null)
        for (name in listOf("timeTickRegistered", "pendingForceRefresh", "queryFailureLogged")) {
            field(name).setBoolean(null, false)
        }
        WeatherDataController.weatherInfo = ""
    }

    private fun field(name: String) = WeatherDataController::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    class Clock {
        var updates = 0
        fun updateTime() { updates++ }
    }

    private class QueryAttempt {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        @Volatile var timedOut = false
    }

    private inner class QueryContext : Application() {
        var resolver: ContentResolver? = null
        var providerFailure: Throwable? = null
        val attempts = ConcurrentLinkedQueue<QueryAttempt>()
        val ticks = ArrayList<BroadcastReceiver>()
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? {
            if (receiver != null && filter?.hasAction(Intent.ACTION_TIME_TICK) == true) ticks.add(receiver)
            return null
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) { ticks.remove(receiver) }
        override fun getContentResolver(): ContentResolver {
            calls.incrementAndGet()
            attempts.poll()?.let {
                it.entered.countDown()
                it.timedOut = !it.release.await(10, TimeUnit.SECONDS)
            }
            providerFailure?.let { throw it }
            resolver?.let { return it }
            throw IllegalStateException("test provider unavailable")
        }
    }

    private class WeatherProvider : ContentProvider() {
        var cursor: Cursor? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf("description", "temperature")).apply {
                addRow(arrayOf("stale weather", "10"))
                cursor = this
            }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
