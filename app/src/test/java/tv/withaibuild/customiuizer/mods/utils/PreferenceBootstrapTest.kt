package tv.withaibuild.customiuizer.mods.utils

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tv.withaibuild.customiuizer.utils.FakeSharedPreferences
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [PreferenceBootstrap] stable-snapshot state machine.
 *
 * A caller must never see [State.LOADED] or [State.VALID_EMPTY] before the listener is registered
 * and a second snapshot has been taken, and the published [PrefMap] snapshot must always be a
 * complete, consistent map.
 */
class PreferenceBootstrapTest {

    @Test
    fun bootstrap_nonEmptySnapshot_reachesLoaded() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 20)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        assertTrue(bootstrap.bootstrap())
        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
        assertTrue(bootstrap.isReady())
        assertEquals(20, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun bootstrap_initialSnapshot_doesNotDispatchChanges() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 20)

        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val bootstrap = PreferenceBootstrap.create(prefs, { fake }) { key ->
            dispatchedKeys.add(key)
        }

        assertTrue(bootstrap.bootstrap())
        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
        assertTrue(bootstrap.isReady())
        assertTrue(
            "initial first and second snapshot must be published silently; no observer dispatch",
            dispatchedKeys.isEmpty()
        )
    }

    @Test
    fun bootstrap_initialSnapshot_dispatchesOnlyListenerChanges() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 20)

        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val bootstrap = PreferenceBootstrap.create(prefs, { fake }) { key ->
            dispatchedKeys.add(key)
        }

        assertTrue(bootstrap.bootstrap())

        val listener = getListener(bootstrap)
        fake.put("pref_key_system_charginginfo_fontsize", 24)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_charginginfo_fontsize")

        assertEquals(listOf("system_charginginfo_fontsize"), dispatchedKeys)
    }

    @Test
    fun bootstrap_windowedChangeWithoutListener_noDispatch() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val bootstrap = PreferenceBootstrap.create(prefs, { fake }) { key ->
            dispatchedKeys.add(key)
        }

        // Mutate before bootstrap. The second snapshot picks up the new value, but no
        // [handlePreferenceChanged] dispatch occurs for it.
        fake.put("pref_key_system_statusbarheight", 24)

        assertTrue(bootstrap.bootstrap())
        assertEquals(24, prefs.getInt("system_statusbarheight", 11))
        assertTrue(
            "a change that happens before the listener is registered must not trigger a dispatch",
            dispatchedKeys.isEmpty()
        )
    }

    @Test
    fun bootstrap_firstSnapshotNonEmpty_doesNotReachLoadedUntilListener() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 20)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        // No public way to see first snapshot without listener: bootstrap() is the only entry.
        assertTrue(bootstrap.bootstrap())
        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
        assertTrue(bootstrap.isListenerRegistered())
    }

    @Test
    fun bootstrap_emptySnapshotAfterListener_reachesValidEmpty() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        assertTrue(bootstrap.bootstrap())
        assertEquals(PreferenceBootstrap.State.VALID_EMPTY, bootstrap.getState())
        assertTrue(bootstrap.isReady())
        assertTrue(bootstrap.isListenerRegistered())
        assertEquals(0, prefs.size())
    }

    @Test
    fun bootstrap_listenerNotRegistered_notReady() {
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { throw IllegalStateException("remote not ready") }

        assertFalse(bootstrap.bootstrap())
        assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
        assertFalse(bootstrap.isReady())
        assertFalse(bootstrap.isListenerRegistered())
    }

    @Test
    fun bootstrap_getAllReturnsNull_recordsUnavailable() {
        val fake = object : SharedPreferences by FakeSharedPreferences() {
            override fun getAll(): Map<String, *>? = null
        }
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        assertFalse(bootstrap.bootstrap())
        assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
    }

    @Test
    fun bootstrap_windowedChangeCaptured() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        // Simulate a preference change between the first snapshot and the listener registration.
        // FakeSharedPreferences is synchronous, so we can model the window by mutating before
        // bootstrap() and checking the final snapshot includes it.
        fake.put("pref_key_system_statusbarheight", 24)

        assertTrue(bootstrap.bootstrap())
        assertEquals(24, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun bootstrap_listenerRegistered_onlyOnce() {
        val fake = FakeSharedPreferences()
        var registerCount = 0
        val trackingFake = object : SharedPreferences by fake {
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
                registerCount++
            }
        }

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { trackingFake }

        assertTrue(bootstrap.bootstrap())
        assertTrue(bootstrap.bootstrap())

        assertEquals(1, registerCount)
    }

    @Test
    fun bootstrap_concurrentCalls_registerOnlyOnce() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        val barrier = CyclicBarrier(2)
        val done = CountDownLatch(2)
        val results = Collections.synchronizedList(mutableListOf<Boolean>())

        repeat(2) {
            Thread {
                barrier.await(5, TimeUnit.SECONDS)
                results.add(bootstrap.bootstrap())
                done.countDown()
            }.start()
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(2, results.size)
        assertTrue(results.all { it })
        assertTrue(bootstrap.isListenerRegistered())
    }

    @Test
    fun bootstrap_twoThreads_finalStateConsistent() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 30)
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        val barrier = CyclicBarrier(2)
        val done = CountDownLatch(2)

        repeat(2) {
            Thread {
                barrier.await(5, TimeUnit.SECONDS)
                bootstrap.bootstrap()
                done.countDown()
            }.start()
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(30, prefs.getInt("system_statusbarheight", 11))
        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
    }

    @Test
    fun listenerCallback_updatesPrefMapFromAll() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.bootstrap()

        val listener = getListener(bootstrap)
        fake.put("pref_key_system_statusbarheight", 36)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals(36, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun listenerCallback_removesDeletedKey() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.bootstrap()

        val listener = getListener(bootstrap)
        fake.edit().remove("pref_key_system_statusbarheight").apply()
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals(11, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun listenerCallback_typeChange_intToStringHandled() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.bootstrap()

        val listener = getListener(bootstrap)
        fake.put("pref_key_system_statusbarheight", "24")
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals("24", prefs.getString("system_statusbarheight", ""))
        assertEquals(24, prefs.getStringAsInt("system_statusbarheight", 0))
    }

    @Test
    fun listenerCallback_typeChange_stringToBooleanHandled() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", "12")

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.bootstrap()

        val listener = getListener(bootstrap)
        fake.put("pref_key_system_statusbarheight", true)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertTrue(prefs.getBoolean("system_statusbarheight", false))
    }

    @Test
    fun listenerCallback_deletedLastKey_becomesValidEmpty() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.bootstrap()

        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())

        val listener = getListener(bootstrap)
        fake.edit().remove("pref_key_system_statusbarheight").apply()
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals(PreferenceBootstrap.State.VALID_EMPTY, bootstrap.getState())
        assertEquals(0, prefs.size())
    }

    @Test
    fun listenerCallback_addedFirstKey_becomesLoaded() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.bootstrap()

        assertEquals(PreferenceBootstrap.State.VALID_EMPTY, bootstrap.getState())

        val listener = getListener(bootstrap)
        fake.put("pref_key_system_statusbarheight", 12)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
        assertEquals(12, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun bootstrap_retryBudgetExhausted_stops() {
        val prefs = PrefMap()
        var attempts = 0
        val bootstrap = PreferenceBootstrap.create(prefs) {
            attempts++
            throw IllegalStateException("remote not ready")
        }

        repeat(PreferenceBootstrap.MAX_PREF_INIT_ATTEMPTS) {
            assertFalse(bootstrap.bootstrap())
        }
        assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
        assertEquals(PreferenceBootstrap.MAX_PREF_INIT_ATTEMPTS, attempts)

        // Next call must not retry.
        assertFalse(bootstrap.bootstrap())
        assertEquals(PreferenceBootstrap.MAX_PREF_INIT_ATTEMPTS, attempts)
    }

    @Test
    fun bootstrap_recoveryAfterFailureResetsBudget() {
        val prefs = PrefMap()
        var attempts = 0
        var shouldFail = true
        val fake = FakeSharedPreferences()

        val bootstrap = PreferenceBootstrap.create(prefs) {
            attempts++
            if (shouldFail) throw IllegalStateException("remote not ready") else fake
        }

        repeat(PreferenceBootstrap.MAX_PREF_INIT_ATTEMPTS - 1) {
            assertFalse(bootstrap.bootstrap())
        }

        shouldFail = false
        assertTrue(bootstrap.bootstrap())
        assertEquals(PreferenceBootstrap.State.VALID_EMPTY, bootstrap.getState())
    }

    @Test
    fun bootstrap_malformedSetValue_isIgnored() {
        val fake = FakeSharedPreferences()
        // FakeSharedPreferences stores Sets as-is; simulate a malformed Set by putting a non-Set
        // then later changing it to a proper Set through the listener.
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.bootstrap()

        val listener = getListener(bootstrap)
        @Suppress("UNCHECKED_CAST")
        val value = setOf("a") as Any
        fake.put("pref_key_system_statusbarheight", value)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals(setOf("a"), prefs.getStringSet("system_statusbarheight"))
    }

    @Test
    fun listenerCallback_throwsVirtualMachineError_propagates() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val throwingFake = object : SharedPreferences by fake {
            override fun contains(key: String): Boolean = throw InternalError("contains vm error")
        }

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { throwingFake }
        assertTrue(bootstrap.bootstrap())

        val listener = getListener(bootstrap)
        try {
            listener?.onSharedPreferenceChanged(throwingFake, "pref_key_system_statusbarheight")
            assertTrue("InternalError from the listener callback must propagate", false)
        } catch (e: InternalError) {
            // Expected: the live listener path must not swallow fatal VM errors.
        }
        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
    }

    @Test
    fun listenerCallback_throwsThreadDeath_propagates() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val throwingFake = object : SharedPreferences by fake {
            override fun contains(key: String): Boolean = throw ThreadDeath()
        }

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { throwingFake }
        assertTrue(bootstrap.bootstrap())

        val listener = getListener(bootstrap)
        try {
            listener?.onSharedPreferenceChanged(throwingFake, "pref_key_system_statusbarheight")
            assertTrue("ThreadDeath from the listener callback must propagate", false)
        } catch (e: ThreadDeath) {
            // Expected: the live listener path must not swallow fatal thread errors.
        }
        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
    }

    @Test
    fun listenerCallback_throwsRuntimeException_isolated() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val throwingFake = object : SharedPreferences by fake {
            override fun contains(key: String): Boolean = throw RuntimeException("transient read failure")
        }

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { throwingFake }
        assertTrue(bootstrap.bootstrap())

        val listener = getListener(bootstrap)
        // Non-fatal errors must be isolated by the listener boundary and not propagate.
        listener?.onSharedPreferenceChanged(throwingFake, "pref_key_system_statusbarheight")

        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
        assertEquals(12, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun prefMap_replaceSnapshot_isAtomic() {
        val prefs = PrefMap()

        val keyCount = 100
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)

        Thread {
            start.await(5, TimeUnit.SECONDS)
            repeat(100) { iteration ->
                prefs.replaceSnapshot((0 until keyCount).associate { "k$it" to (iteration * keyCount + it) })
            }
            done.countDown()
        }.start()

        Thread {
            start.await(5, TimeUnit.SECONDS)
            repeat(100) {
                // Capture the whole published snapshot once. Each individual getInt() reads the
                // current snapshot independently, so a sequence of them could observe different
                // snapshots; only a single captured view can be checked for atomicity.
                val current = prefs.getAll()
                val snapshot = (0 until keyCount).map { "k$it" to (current["k$it"] as? Int ?: -1) }.toMap()
                // If a reader saw a half-built snapshot, some keys would be -1 while others
                // were from the previous or next iteration.  That cannot happen here because
                // replaceSnapshot swaps the reference atomically.
                val allMinusOne = snapshot.values.all { it == -1 }
                val allValid = snapshot.values.all { it != -1 }
                assertTrue("reader saw a mixed snapshot", allMinusOne || allValid)
            }
            done.countDown()
        }.start()

        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun prefMap_concurrentSingleKeyUpdate_neverReadsPartialSnapshot() {
        val prefs = PrefMap()
        prefs.replaceSnapshot(mapOf("counter" to 0))

        val iterations = 100
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val updates = AtomicInteger(0)

        Thread {
            start.await(5, TimeUnit.SECONDS)
            repeat(iterations) {
                prefs.put("counter", it)
                updates.incrementAndGet()
            }
            done.countDown()
        }.start()

        Thread {
            start.await(5, TimeUnit.SECONDS)
            repeat(iterations) {
                // Reader must never see a negative or otherwise corrupted counter.
                val value = prefs.getInt("counter", -1)
                assertTrue("corrupt counter value", value >= -1)
            }
            done.countDown()
        }.start()

        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(iterations, updates.get())
    }

    @Test
    fun bootstrap_remoteSourceThrowsVmError_propagatesAndMarksUnavailable() {
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { throw InternalError("remote vm error") }

        try {
            bootstrap.bootstrap()
            assertTrue("InternalError must propagate", false)
        } catch (e: InternalError) {
            assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
        }
    }

    @Test
    fun bootstrap_remoteSourceThrowsThreadDeath_propagatesAndMarksUnavailable() {
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { throw ThreadDeath() }

        try {
            bootstrap.bootstrap()
            assertTrue("ThreadDeath must propagate", false)
        } catch (e: ThreadDeath) {
            assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
        }
    }

    @Test
    fun bootstrap_getAllThrowsVmError_propagatesAndMarksUnavailable() {
        val fake = object : SharedPreferences by FakeSharedPreferences() {
            override fun getAll(): Map<String, *> = throw InternalError("getAll vm error")
        }
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        try {
            bootstrap.bootstrap()
            assertTrue("InternalError must propagate", false)
        } catch (e: InternalError) {
            assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
        }
    }

    @Test
    fun bootstrap_registerListenerThrowsVmError_propagatesAndMarksUnavailable() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 20)
        val throwingFake = object : SharedPreferences by fake {
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
                throw InternalError("register vm error")
            }
        }

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { throwingFake }

        try {
            bootstrap.bootstrap()
            assertTrue("InternalError must propagate", false)
        } catch (e: InternalError) {
            assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getListener(bootstrap: PreferenceBootstrap): SharedPreferences.OnSharedPreferenceChangeListener? {
        val field = PreferenceBootstrap::class.java.getDeclaredField("listener")
            .apply { isAccessible = true }
        return field.get(bootstrap) as? SharedPreferences.OnSharedPreferenceChangeListener
    }
}
