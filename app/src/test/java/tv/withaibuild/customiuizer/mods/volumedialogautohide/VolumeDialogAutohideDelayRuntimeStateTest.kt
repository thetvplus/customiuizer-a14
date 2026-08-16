package tv.withaibuild.customiuizer.mods.volumedialogautohide

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Component tests for [VolumeDialogAutohideDelayRuntimeState].
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class VolumeDialogAutohideDelayRuntimeStateTest {

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
        VolumeDialogAutohideDelayRuntimeState.installed = false
        VolumeDialogAutohideDelayRuntimeState.instance = null
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        VolumeDialogAutohideDelayRuntimeState.installed = false
        VolumeDialogAutohideDelayRuntimeState.instance = null
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
    }

    @Test
    fun initialSnapshot_isNullBeforeInitialize() {
        val state = VolumeDialogAutohideDelayRuntimeState()
        assertNull(state.snapshotRef.get())
    }

    @Test
    fun initialize_buildsFromOneSource() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 100,
                "system_volumedialogdelay_collapsed" to 50,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        val snapshot = state.snapshotRef.get()
        assertNotNull(snapshot)
        assertEquals(100, snapshot!!.expanded)
        assertEquals(50, snapshot.collapsed)
    }

    @Test
    fun initialize_typeMismatchValue_returnsDefaultForThatKey() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to "not an int",
                "system_volumedialogdelay_collapsed" to 50,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        val snapshot = state.snapshotRef.get()!!
        assertEquals(0, snapshot.expanded)
        assertEquals(50, snapshot.collapsed)
    }

    @Test
    fun onPreferenceChanged_relevantExpandedKey_rebuilds() {
        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 200,
                "system_volumedialogdelay_collapsed" to 100,
            ),
        )
        state.onPreferenceChanged("system_volumedialogdelay_expanded")

        val snapshot = state.snapshotRef.get()!!
        assertEquals(200, snapshot.expanded)
    }

    @Test
    fun onPreferenceChanged_relevantCollapsedKey_rebuilds() {
        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 200,
                "system_volumedialogdelay_collapsed" to 100,
            ),
        )
        state.onPreferenceChanged("system_volumedialogdelay_collapsed")

        val snapshot = state.snapshotRef.get()!!
        assertEquals(100, snapshot.collapsed)
    }

    @Test
    fun onPreferenceChanged_nullKey_rebuilds() {
        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 999,
                "system_volumedialogdelay_collapsed" to 111,
            ),
        )
        state.onPreferenceChanged(null)

        val snapshot = state.snapshotRef.get()!!
        assertEquals(999, snapshot.expanded)
        assertEquals(111, snapshot.collapsed)
    }

    @Test
    fun onPreferenceChanged_irrelevantKey_doesNotRebuild() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 100,
                "system_volumedialogdelay_collapsed" to 50,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 999,
                "system_volumedialogdelay_collapsed" to 111,
            ),
        )
        state.onPreferenceChanged("system_volumeblur_collapsed")

        val snapshot = state.snapshotRef.get()!!
        assertEquals(100, snapshot.expanded)
        assertEquals(50, snapshot.collapsed)
    }

    @Test
    fun noCallbackTimeLazyBuild() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 100,
                "system_volumedialogdelay_collapsed" to 50,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        // Do not call initialize() — simulate callback racing with initial refresh.
        val snapshot = state.snapshotRef.get()
        assertNull(snapshot)
    }

    @Test
    fun install_returnsSameInstanceOnSequentialCalls() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 111,
                "system_volumedialogdelay_collapsed" to 222,
            ),
        )

        val first = VolumeDialogAutohideDelayRuntimeState.install()
        val second = VolumeDialogAutohideDelayRuntimeState.install()

        assertSame("install must return the same process singleton", first, second)

        val snapshot = first.snapshotRef.get()
        assertNotNull(snapshot)
        assertEquals(111, snapshot!!.expanded)
        assertEquals(222, snapshot.collapsed)
    }

    @Test
    fun install_concurrentCallersAllReceiveSameInitializedInstance() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 111,
                "system_volumedialogdelay_collapsed" to 222,
            ),
        )

        val threadCount = 8
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)

        val threads = (0 until threadCount).map { _ ->
            object : Thread() {
                var returned: VolumeDialogAutohideDelayRuntimeState? = null
                override fun run() {
                    ready.countDown()
                    assertTrue(
                        "all workers must reach the start barrier",
                        start.await(2, TimeUnit.SECONDS),
                    )
                    returned = VolumeDialogAutohideDelayRuntimeState.install()
                }
            }
        }

        threads.forEach { it.start() }

        assertTrue(
            "all install workers must arrive at the start barrier",
            ready.await(2, TimeUnit.SECONDS),
        )
        start.countDown()

        threads.forEach { it.join(2_000) }
        threads.forEach { assertTrue("all install threads must complete", !it.isAlive) }

        val first = threads[0].returned!!
        threads.forEach {
            assertSame("concurrent install must return the same instance", first, it.returned)
        }

        val snapshot = first.snapshotRef.get()!!
        assertEquals(111, snapshot.expanded)
        assertEquals(222, snapshot.collapsed)

        val observerCount = processScopedObserverCount()
        assertEquals("observer must be registered exactly once", 1, observerCount)

        assertTrue(
            "installed flag must be published after successful init",
            VolumeDialogAutohideDelayRuntimeState.installed,
        )
    }

    @Test
    fun install_initialRefreshFatalThenRetryReusesSameStateAndDoesNotDuplicateObserver() {
        val refreshCallCount = AtomicInteger(0)
        val values = mapOf(
            "system_volumedialogdelay_expanded" to 111,
            "system_volumedialogdelay_collapsed" to 222,
        )

        val runtimeState = VolumeDialogAutohideDelayRuntimeState {
            val call = refreshCallCount.incrementAndGet()
            if (call == 1) throw OutOfMemoryError("simulated fatal initial refresh")
            values
        }

        try {
            VolumeDialogAutohideDelayRuntimeState.install(runtimeState)
            fail("Expected OutOfMemoryError from initial refresh")
        } catch (e: OutOfMemoryError) {
            assertNull("snapshot must be cleared after fatal refresh", runtimeState.snapshotRef.get())
            assertEquals("observer must be registered before fatal is rethrown", 1, processScopedObserverCount())
            assertFalse(VolumeDialogAutohideDelayRuntimeState.installed)
        }

        val installed = VolumeDialogAutohideDelayRuntimeState.install(runtimeState)

        assertSame("retry must return the same state", runtimeState, installed)
        assertNotNull("snapshot must be published after retry", installed.snapshotRef.get())
        assertEquals(111, installed.snapshotRef.get()!!.expanded)
        assertEquals(222, installed.snapshotRef.get()!!.collapsed)

        val observerCount = processScopedObserverCount()
        assertEquals("observer must still be registered exactly once", 1, observerCount)
        assertEquals(2, refreshCallCount.get())
    }

    @Test
    fun install_publishedInstanceIsCompleteBeforeVolatileFlag() {
        val runtimeState = VolumeDialogAutohideDelayRuntimeState {
            mapOf(
                "system_volumedialogdelay_expanded" to 777,
                "system_volumedialogdelay_collapsed" to 888,
            )
        }

        val installed = VolumeDialogAutohideDelayRuntimeState.install(runtimeState)

        assertTrue(VolumeDialogAutohideDelayRuntimeState.installed)
        assertSame(runtimeState, installed)
        assertEquals(1, processScopedObserverCount())
        assertEquals(777, installed.snapshotRef.get()!!.expanded)
        assertEquals(888, installed.snapshotRef.get()!!.collapsed)
    }

    @Test
    fun initialize_ordinaryRefreshFailure_clearsPreviousSnapshot() {
        val state = VolumeDialogAutohideDelayRuntimeState {
            throw RuntimeException("simulated refresh failure")
        }
        state.snapshotRef.set(VolumeDialogAutohideDelaySnapshot(100, 50))
        state.initialize()

        assertNull("snapshot must be cleared after ordinary refresh failure", state.snapshotRef.get())
    }

    @Test(expected = OutOfMemoryError::class)
    fun initialize_fatalRefreshFailure_clearsThenRethrows() {
        val state = VolumeDialogAutohideDelayRuntimeState {
            throw OutOfMemoryError("simulated fatal refresh failure")
        }
        state.snapshotRef.set(VolumeDialogAutohideDelaySnapshot(100, 50))

        try {
            state.initialize()
        } catch (e: OutOfMemoryError) {
            assertNull("snapshot must be cleared before fatal rethrow", state.snapshotRef.get())
            throw e
        }
    }

    @Test
    fun refreshWaitingBehindLockCapturesLatestGeneration() {
        val mapA = mapOf(
            "system_volumedialogdelay_expanded" to 1,
            "system_volumedialogdelay_collapsed" to 2,
        )
        val mapB = mapOf(
            "system_volumedialogdelay_expanded" to 3,
            "system_volumedialogdelay_collapsed" to 4,
        )

        val blockingMapA = BlockingMap(mapA)

        var callCount = 0
        val state = VolumeDialogAutohideDelayRuntimeState {
            if (++callCount == 1) {
                blockingMapA
            } else {
                MainModule.mPrefs.getAll()
            }
        }

        val secondStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        val secondCompleted = CountDownLatch(1)

        val firstRefresh = Thread {
            state.initialize()
        }

        // 1. Start Refresh A.
        firstRefresh.start()

        // 2. Refresh A acquires refreshLock, then blocks inside source Map.get.
        assertTrue(
            "Refresh A must be inside Map.get before we continue",
            blockingMapA.awaitEntered(),
        )

        // 3. Update the backing PrefMap to generation B.
        MainModule.mPrefs.replaceSnapshot(mapB)

        // 4. Start Refresh B. It must park on refreshLock, not complete.
        val secondRefresh = Thread {
            secondStarted.set(true)
            state.onPreferenceChanged("system_volumedialogdelay_expanded")
            secondCompleted.countDown()
        }
        secondRefresh.start()

        // Wait for B to actually start, then prove it cannot finish while A is blocked.
        assertTrue(
            "Refresh B must have started",
            spinUntilTrue { secondStarted.get() },
        )
        assertFalse(
            "Refresh B must be waiting behind refreshLock while A is blocked",
            secondCompleted.await(100, TimeUnit.MILLISECONDS),
        )

        // 5. Release A. It publishes generation A and exits the lock.
        blockingMapA.release()

        // 6. B acquires refreshLock, then invokes refreshSource() and sees generation B.
        assertTrue(
            "Refresh B must complete",
            secondCompleted.await(2, TimeUnit.SECONDS),
        )

        // 7. Final snapshot must reflect the latest generation captured by B.
        val snapshot = state.snapshotRef.get()!!
        assertEquals("final snapshot must reflect generation B", 3, snapshot.expanded)
        assertEquals(4, snapshot.collapsed)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the number of process-scoped observers currently registered.
     */
    private fun processScopedObserverCount(): Int {
        val field = PreferenceObserverRegistry::class.java.getDeclaredField("observers")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val observers = field.get(PreferenceObserverRegistry) as java.util.Set<*>
        return observers.size
    }

    private fun spinUntilTrue(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 2_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.yield()
        }
        return true
    }

    /**
     * A [Map] whose first [get] call blocks until the test releases it, while still
     * holding the [VolumeDialogAutohideDelayRuntimeState.refreshLock].
     *
     * This creates a deterministic point inside `synchronized(refreshLock)` where a second
     * refresh can be shown to be waiting and the backing preference source can be changed.
     */
    private class BlockingMap(
        private val map: Map<String, Any>,
    ) : AbstractMap<String, Any>() {

        private val entered = CountDownLatch(1)
        private val release = CountDownLatch(1)
        private val blockOnce = java.util.concurrent.atomic.AtomicBoolean(true)

        override val entries: Set<Map.Entry<String, Any>> = map.entries

        override fun get(key: String): Any? {
            if (blockOnce.compareAndSet(true, false)) {
                entered.countDown()
                check(
                    release.await(2, TimeUnit.SECONDS),
                ) { "test did not release BlockingMap in time" }
            }
            return map[key]
        }

        fun awaitEntered(): Boolean =
            entered.await(2, TimeUnit.SECONDS)

        fun release() =
            release.countDown()
    }
}
