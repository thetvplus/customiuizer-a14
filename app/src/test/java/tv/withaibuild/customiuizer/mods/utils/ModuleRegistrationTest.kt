package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Regression tests for [ReceiverRegistry.replaceModuleRegistration].
 */
class ModuleRegistrationTest {

    @After
    fun tearDown() {
        getModuleRegistrationsMap().clear()
        getStaleModuleRegistrationsMap().clear()
    }

    @Test
    fun replaceModuleRegistration_replacesOldCleanup() {
        val oldRan = AtomicInteger(0)
        val newRan = AtomicInteger(0)

        ReceiverRegistry.replaceModuleRegistration("regKey") { oldRan.incrementAndGet() }
        assertEquals("first cleanup must not run yet", 0, oldRan.get())

        ReceiverRegistry.replaceModuleRegistration("regKey") { newRan.incrementAndGet() }
        assertEquals("old cleanup must have run once", 1, oldRan.get())
        assertEquals("new cleanup must not have run yet", 0, newRan.get())
    }

    @Test
    fun replaceModuleRegistration_staleCleanupRetriesOnNextCall() {
        val attempts = AtomicInteger(0)
        val cleanup = Runnable {
            attempts.incrementAndGet()
            if (attempts.get() == 1) throw IllegalStateException("simulated cleanup failure")
        }

        ReceiverRegistry.replaceModuleRegistration("staleRegKey", cleanup)
        assertEquals("first cleanup must not run on first install", 0, attempts.get())

        ReceiverRegistry.replaceModuleRegistration("staleRegKey") {}
        assertEquals("first cleanup must run once when replaced", 1, attempts.get())
        assertTrue("stale queue must contain the failed cleanup", getStaleModuleRegistrationsMap().containsKey("staleRegKey"))

        ReceiverRegistry.replaceModuleRegistration("staleRegKey") {}
        assertEquals("cleanup must be retried exactly once", 2, attempts.get())
        assertFalse("stale queue must be empty after successful retry", getStaleModuleRegistrationsMap().containsKey("staleRegKey"))
    }

    @Test
    fun replaceModuleRegistration_staleQueueIsBounded() {
        val attempts = AtomicInteger(0)
        val failing = Runnable {
            attempts.incrementAndGet()
            throw IllegalStateException("simulated cleanup failure")
        }

        repeat(5) {
            ReceiverRegistry.replaceModuleRegistration("boundedRegKey", failing)
        }

        val staleMap = getStaleModuleRegistrationsMap()
        val staleQueue = staleMap["boundedRegKey"] as? Collection<*>
        assertNotNull("stale queue must exist", staleQueue)
        assertTrue("stale queue must be bounded", staleQueue!!.size <= 3)
    }

    @Test
    fun staleCleanupReentryDoesNotRetryItsOwnInFlightCleanup() {
        val attempts = AtomicInteger()
        val nestedCleanup = AtomicInteger()
        ReceiverRegistry.replaceModuleRegistration("reentry") {
            when (attempts.incrementAndGet()) {
                1 -> throw IllegalStateException("retryable")
                2 -> ReceiverRegistry.replaceModuleRegistration("reentry") { nestedCleanup.incrementAndGet() }
                else -> throw AssertionError("recursive cleanup")
            }
        }
        ReceiverRegistry.replaceModuleRegistration("reentry") {}
        ReceiverRegistry.replaceModuleRegistration("reentry") {}
        assertEquals("a retry must detach its batch before calling user cleanup", 2, attempts.get())
        assertEquals(1, nestedCleanup.get())
        assertTrue(getStaleModuleRegistrationsMap().isEmpty())
    }

    @Test
    fun blockedStaleCleanupDoesNotHoldTheRegistryMapLock() {
        val attempts = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        ReceiverRegistry.replaceModuleRegistration("blocked") {
            if (attempts.incrementAndGet() == 1) throw IllegalStateException("retryable")
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        ReceiverRegistry.replaceModuleRegistration("blocked") {}
        val retry = Thread {
            try {
                ReceiverRegistry.replaceModuleRegistration("blocked") {}
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        val replacement = Thread {
            try {
                ReceiverRegistry.replaceModuleRegistration("blocked") {}
            } catch (t: Throwable) {
                failure.set(t)
            } finally {
                completed.countDown()
            }
        }
        try {
            retry.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            replacement.start()
            assertTrue("replacement must finish while stale cleanup is blocked", completed.await(1, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            retry.join(5000)
            replacement.join(5000)
        }
        assertFalse(retry.isAlive)
        assertFalse(replacement.isAlive)
        assertEquals(null, failure.get())
    }

    @Suppress("UNCHECKED_CAST")
    private fun getModuleRegistrationsMap(): ConcurrentHashMap<String, Any> {
        val field = ReceiverRegistry::class.java.getDeclaredField("moduleRegistrations")
            .apply { isAccessible = true }
        return field.get(null) as ConcurrentHashMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun getStaleModuleRegistrationsMap(): ConcurrentHashMap<String, Any> {
        val field = ReceiverRegistry::class.java.getDeclaredField("staleModuleRegistrations")
            .apply { isAccessible = true }
        return field.get(null) as ConcurrentHashMap<String, Any>
    }
}
