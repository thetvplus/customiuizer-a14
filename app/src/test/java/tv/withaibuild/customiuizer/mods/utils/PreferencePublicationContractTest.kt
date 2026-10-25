package tv.withaibuild.customiuizer.mods.utils

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.FakeSharedPreferences
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Exercise the publication boundary seen by installers and preference observers. */
class PreferencePublicationContractTest {
    private val key = "pref_key_system_statusbarheight"

    private class PausedSnapshot(values: Map<String, Any>) : Map<String, Any> by values {
        private val original = values
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override val entries: Set<Map.Entry<String, Any>>
            get() {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "Snapshot publication was not released" }
                return original.entries
            }
    }

    @Test
    fun installersCannotObserveLoadedBeforeTheSnapshotIsPublished() {
        val paused = PausedSnapshot(mapOf(key to 46))
        var reads = 0
        val remote = object : SharedPreferences by FakeSharedPreferences() {
            override fun getAll(): Map<String, *> = if (++reads == 2) paused else mapOf(key to 46)
        }
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs, { remote }) {}
        val result = FutureTask { bootstrap.bootstrap() }
        val worker = Thread(result).apply { start() }
        var readyDuringPublication = true
        var catchUpDuringPublication = true
        try {
            assertTrue(paused.entered.await(5, TimeUnit.SECONDS))
            readyDuringPublication = bootstrap.isReady()
            catchUpDuringPublication = SystemServerPrefLifecycle.shouldRunCatchUp(false, bootstrap.getState())
        } finally {
            paused.release.countDown()
            worker.join(5000)
        }
        assertTrue(result.get(5, TimeUnit.SECONDS))
        assertFalse("Installers must not use a snapshot still being copied", readyDuringPublication)
        assertFalse("Catch-up must remain available until publication finishes", catchUpDuringPublication)
        assertEquals(46, prefs.getInt("system_statusbarheight", 11))
        assertTrue(SystemServerPrefLifecycle.shouldRunCatchUp(false, bootstrap.getState()))
    }

    @Test
    fun aListenerUpdateDuringBootstrapCannotBeOverwrittenByTheCapturedSnapshot() {
        refreshDuringSnapshot(initial = true)
    }

    @Test
    fun aListenerUpdateDuringBulkRefreshCannotBeOverwrittenByTheCapturedSnapshot() {
        refreshDuringSnapshot(initial = false)
    }

    @Test
    fun acknowledgedHeightStillOverridesAStaleBootstrapSnapshot() {
        refreshDuringSnapshot(initial = true, authoritative = true)
    }

    @Test
    fun aTemporaryTypedReadFailureRetainsTheLastPublishedValueWithoutDispatch() {
        var failing = false
        val values = FakeSharedPreferences().apply { put(key, 46) }
        val remote = object : SharedPreferences by values {
            override fun getInt(key: String, defValue: Int): Int {
                check(!failing) { "Remote cache temporarily unavailable" }
                return values.getInt(key, defValue)
            }
        }
        val prefs = PrefMap()
        val observations = mutableListOf<Int>()
        val bootstrap = PreferenceBootstrap.create(prefs, { remote }) {
            observations.add(prefs.getInt("system_statusbarheight", 11))
        }
        assertTrue(bootstrap.bootstrap())
        failing = true
        bootstrap.refreshRemoteKey(key)
        assertEquals("An unreadable value is not a deleted preference", 46,
            prefs.getInt("system_statusbarheight", 11))
        assertTrue("No replacement value was published", observations.isEmpty())
        // The device height workaround is authoritative even when the framework cache fails.
        bootstrap.refreshRemoteKey(key, mapOf(key to 11))
        assertEquals(11, prefs.getInt("system_statusbarheight", 46))
        assertEquals(listOf(11), observations)
        observations.clear()
        failing = false
        values.put(key, 43)
        bootstrap.refreshRemoteKey(key)
        assertEquals(listOf(43), observations)
    }

    private fun refreshDuringSnapshot(initial: Boolean, authoritative: Boolean = false) {
        val paused = PausedSnapshot(mapOf(key to 46))
        val value = AtomicInteger(46)
        val reads = AtomicInteger()
        lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener
        val remote = object : SharedPreferences by FakeSharedPreferences() {
            override fun getAll(): Map<String, *> =
                if (reads.incrementAndGet() == if (initial) 2 else 3) paused else mapOf(key to value.get())
            override fun contains(key: String) = true
            override fun getInt(key: String, defValue: Int) = value.get()
            override fun registerOnSharedPreferenceChangeListener(next: SharedPreferences.OnSharedPreferenceChangeListener) {
                listener = next
            }
        }
        val prefs = PrefMap()
        val observations = Collections.synchronizedList(mutableListOf<Int>())
        val bootstrap = PreferenceBootstrap.create(prefs, { remote }) {
            observations.add(prefs.getInt("system_statusbarheight", 11))
        }
        if (!initial) assertTrue(bootstrap.bootstrap())
        val publication = FutureTask { if (initial) bootstrap.bootstrap() else bootstrap.refreshRemoteKey(null) }
        val publisher = Thread(publication).apply { start() }
        var update: FutureTask<Unit>? = null
        var callback: Thread? = null
        try {
            assertTrue(paused.entered.await(5, TimeUnit.SECONDS))
            if (!authoritative) value.set(43)
            val pending = FutureTask {
                if (authoritative) bootstrap.refreshRemoteKey(key, mapOf(key to 43))
                else listener.onSharedPreferenceChanged(remote, key)
            }
            update = pending
            val running = Thread(pending).apply { start() }
            callback = running
            // Both implementations reach a monitor: the old one after writing PrefMap,
            // the corrected one before reading and publishing the changed value.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (running.state != Thread.State.BLOCKED && running.isAlive && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertTrue("Callback must reach the blocked publication or complete", running.state == Thread.State.BLOCKED || pending.isDone)
        } finally {
            paused.release.countDown()
            publisher.join(5000)
            callback?.join(5000)
        }
        publication.get(5, TimeUnit.SECONDS)
        requireNotNull(update).get(5, TimeUnit.SECONDS)
        assertEquals("The latest remote value must survive either publication path", 43,
            prefs.getInt("system_statusbarheight", 11))
        assertEquals("Observers must eventually receive the surviving value", 43, observations.last())
        assertTrue(bootstrap.isReady())
    }
}
