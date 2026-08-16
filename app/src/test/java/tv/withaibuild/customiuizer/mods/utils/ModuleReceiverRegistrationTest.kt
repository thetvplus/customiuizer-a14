package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

/**
 * Regression tests for process-scoped module receiver registration.
 *
 * The key invariant: for any given key, at most one receiver is both registered with the
 * Android framework and tracked in the module's registry. Replacements must be atomic, and a
 * registration that loses a race after the framework call must self-unregister.
 */
class ModuleReceiverRegistrationTest {

    /**
     * A stub [Application] that lets tests observe unregistrations and control the timing of
     * framework [registerReceiver] calls. The 5-arg overload is the one [ReceiverRegistry] uses.
     */
    private class TrackableContext : Application() {
        val unregisteredReceivers = java.util.concurrent.CopyOnWriteArrayList<BroadcastReceiver>()
        val registeredReceivers = java.util.concurrent.CopyOnWriteArrayList<BroadcastReceiver>()
        var failNextRegister = false
        var oomNextRegister = false
        var failNextUnregister = false

        // Latches for deterministic race tests. The first 5-arg registerReceiver call will count
        // the latch down and then wait, allowing another thread to finish first.
        var firstRegisterEntered: CountDownLatch? = null
        var firstRegisterRelease: CountDownLatch? = null

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent {
            return Intent("stub")
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            broadcastPermission: String?,
            scheduler: Handler?,
            flags: Int
        ): Intent {
            if (oomNextRegister) {
                oomNextRegister = false
                throw OutOfMemoryError("simulated OOM during registerReceiver")
            }
            if (failNextRegister) {
                failNextRegister = false
                throw IllegalStateException("simulated register failure")
            }
            if (receiver != null) registeredReceivers.add(receiver)

            val entered = firstRegisterEntered
            if (entered != null) {
                firstRegisterEntered = null
                entered.countDown()
                firstRegisterRelease?.await(5, TimeUnit.SECONDS)
            }
            return Intent("stub")
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            if (failNextUnregister) {
                failNextUnregister = false
                throw IllegalStateException("simulated unregister failure")
            }
            if (receiver != null) {
                unregisteredReceivers.add(receiver)
                registeredReceivers.remove(receiver)
            }
        }
    }

    @After
    fun tearDown() {
        // Isolate tests that exercise the process-scoped singleton.
        getModuleReceiversMap().clear()
        getStaleModuleReceiversMap().clear()
    }

    @Test
    fun moduleReceiver_registerAndTrackSingleReceiver() {
        val context = TrackableContext()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        val ok = ReceiverRegistry.registerModuleReceiver(
            context, "testKey", receiver, filter, Context.RECEIVER_NOT_EXPORTED
        )

        assertTrue("register must succeed", ok)
        assertTrue("receiver must be registered with framework", context.registeredReceivers.contains(receiver))

        val map = getModuleReceiversMap()
        assertTrue("map must contain the key", map.containsKey("testKey"))
        assertSame("tracked receiver must be the one registered", receiver, getReceiverFromRegistration(map["testKey"]))
    }

    @Test
    fun moduleReceiver_sameReceiverIsIdempotent() {
        val context = TrackableContext()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        assertTrue(
            ReceiverRegistry.registerModuleReceiver(
                context, "testKey", receiver, filter, Context.RECEIVER_NOT_EXPORTED
            )
        )

        val ok = ReceiverRegistry.registerModuleReceiver(
            context, "testKey", receiver, filter, Context.RECEIVER_NOT_EXPORTED
        )

        assertTrue("second register with same receiver must succeed", ok)
        assertEquals("framework register must be called exactly once", 1, context.registeredReceivers.size)
        assertEquals("framework unregister must not be called", 0, context.unregisteredReceivers.size)

        val map = getModuleReceiversMap()
        assertSame(receiver, getReceiverFromRegistration(map["testKey"]))
    }

    @Test
    fun moduleReceiver_sameKeyReplacesOldReceiver() {
        val context = TrackableContext()
        val oldReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        ReceiverRegistry.registerModuleReceiver(
            context, "testKey", oldReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )
        ReceiverRegistry.registerModuleReceiver(
            context, "testKey", newReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )

        assertTrue("old receiver must be unregistered", context.unregisteredReceivers.contains(oldReceiver))
        assertTrue("new receiver must be registered", context.registeredReceivers.contains(newReceiver))

        val map = getModuleReceiversMap()
        assertEquals("map must contain exactly one registration", 1, map.size)
        assertSame("tracked receiver must be the new one", newReceiver, getReceiverFromRegistration(map["testKey"]))
    }

    @Test
    fun moduleReceiver_registrationFailureRollsBackMap() {
        val context = TrackableContext()
        context.failNextRegister = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        val ok = ReceiverRegistry.registerModuleReceiver(
            context, "testKey", receiver, filter, Context.RECEIVER_NOT_EXPORTED
        )

        assertFalse("register must fail", ok)
        assertEquals("map must not contain a dead registration", 0, getModuleReceiversMap().size)
    }

    @Test
    fun moduleReceiver_oldUnregisterFailureDoesNotBlockNewRegistration() {
        val context = TrackableContext()
        val oldReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        ReceiverRegistry.registerModuleReceiver(
            context, "testKey", oldReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )
        context.failNextUnregister = true

        val ok = ReceiverRegistry.registerModuleReceiver(
            context, "testKey", newReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )

        assertTrue("new registration must succeed", ok)
        assertTrue("new receiver must be in framework", context.registeredReceivers.contains(newReceiver))

        val map = getModuleReceiversMap()
        assertSame("new receiver must be tracked", newReceiver, getReceiverFromRegistration(map["testKey"]))
    }

    @Test
    fun moduleReceiver_raceLoserUnregistersAfterFrameworkRegister() {
        val context = TrackableContext()
        val receiverA = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val receiverB = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        // Thread A will enter registerReceiver and wait. Thread B will complete first.
        val aEntered = CountDownLatch(1)
        val aRelease = CountDownLatch(1)
        context.firstRegisterEntered = aEntered
        context.firstRegisterRelease = aRelease

        var resultA = false
        var resultB = false

        val threadA = Thread {
            resultA = ReceiverRegistry.registerModuleReceiver(
                context, "raceKey", receiverA, filter, Context.RECEIVER_NOT_EXPORTED
            )
        }

        val threadB = Thread {
            assertTrue("thread A must enter registerReceiver", aEntered.await(5, TimeUnit.SECONDS))
            resultB = ReceiverRegistry.registerModuleReceiver(
                context, "raceKey", receiverB, filter, Context.RECEIVER_NOT_EXPORTED
            )
            aRelease.countDown()
        }

        threadA.start()
        threadB.start()
        threadA.join(10_000)
        threadB.join(10_000)

        // Exactly one call returns true. A is the loser because B registered first.
        assertFalse("losing registration must return false", resultA)
        assertTrue("winning registration must return true", resultB)

        assertTrue("loser must be unregistered", context.unregisteredReceivers.contains(receiverA))
        assertFalse("winner must not be unregistered", context.unregisteredReceivers.contains(receiverB))

        val map = getModuleReceiversMap()
        assertEquals("exactly one registration must remain", 1, map.size)
        val tracked = getReceiverFromRegistration(map["raceKey"])
        assertSame("winner must be tracked", receiverB, tracked)
        assertNotNull("there must be a tracked receiver", tracked)
    }

    @Test
    fun moduleReceiver_unregisterAndRegisterRaceDoesNotRemoveNewReceiver() {
        val context = TrackableContext()
        val oldReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        ReceiverRegistry.registerModuleReceiver(
            context, "raceKey", oldReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )

        val barrier = CyclicBarrier(2)
        val unregisterDone = CountDownLatch(1)
        val registerDone = CountDownLatch(1)

        val unregisterThread = Thread {
            barrier.await(5, TimeUnit.SECONDS)
            // Pass the old receiver so a concurrent register does not accidentally remove the new one.
            ReceiverRegistry.unregisterModuleReceiver("raceKey", oldReceiver)
            unregisterDone.countDown()
        }

        val registerThread = Thread {
            barrier.await(5, TimeUnit.SECONDS)
            ReceiverRegistry.registerModuleReceiver(
                context, "raceKey", newReceiver, filter, Context.RECEIVER_NOT_EXPORTED
            )
            registerDone.countDown()
        }

        unregisterThread.start()
        registerThread.start()
        assertTrue(unregisterDone.await(5, TimeUnit.SECONDS))
        assertTrue(registerDone.await(5, TimeUnit.SECONDS))

        val map = getModuleReceiversMap()
        assertEquals("map must contain exactly one registration after the race", 1, map.size)
        assertSame("the new receiver must be the one tracked", newReceiver, getReceiverFromRegistration(map["raceKey"]))
    }

    @Test
    fun moduleReceiver_repeatedRaceLoop() {
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        for (i in 0 until 100) {
            val context = TrackableContext()
            val oldReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {}
            }
            val newReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {}
            }

            // First establish an old registration.
            ReceiverRegistry.registerModuleReceiver(
                context, "loopKey", oldReceiver, filter, Context.RECEIVER_NOT_EXPORTED
            )

            // Race a replacement against an explicit unregister.
            val barrier = CyclicBarrier(2)
            val done = CountDownLatch(2)

            Thread {
                barrier.await(5, TimeUnit.SECONDS)
                // Pass the old receiver so a concurrent register does not remove the new one.
                ReceiverRegistry.unregisterModuleReceiver("loopKey", oldReceiver)
                done.countDown()
            }.start()

            Thread {
                barrier.await(5, TimeUnit.SECONDS)
                ReceiverRegistry.registerModuleReceiver(
                    context, "loopKey", newReceiver, filter, Context.RECEIVER_NOT_EXPORTED
                )
                done.countDown()
            }.start()

            assertTrue("iteration $i: race must finish", done.await(5, TimeUnit.SECONDS))

            val map = getModuleReceiversMap()
            val tracked = getReceiverFromRegistration(map["loopKey"])
            assertSame("iteration $i: the new receiver must be tracked", newReceiver, tracked)
            assertFalse("iteration $i: old receiver must not still be registered", context.registeredReceivers.contains(oldReceiver))
            assertTrue("iteration $i: new receiver must still be registered", context.registeredReceivers.contains(newReceiver))

            // Reset for the next iteration.
            map.clear()
        }
    }

    @Test
    fun moduleReceiver_staleReceiverIsRetriedOnNextRegister() {
        val context = TrackableContext()
        val oldReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val midReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        ReceiverRegistry.registerModuleReceiver(
            context, "staleKey", oldReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )

        context.failNextUnregister = true
        ReceiverRegistry.registerModuleReceiver(
            context, "staleKey", midReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )
        assertTrue("old receiver must still be in framework until retry", context.registeredReceivers.contains(oldReceiver))
        assertTrue("old receiver must still be tracked as stale", getStaleModuleReceiversMap().containsKey("staleKey"))

        context.failNextUnregister = false
        ReceiverRegistry.registerModuleReceiver(
            context, "staleKey", newReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )

        assertTrue("old receiver must be unregistered on retry", context.unregisteredReceivers.contains(oldReceiver))
        assertTrue("mid receiver must be unregistered by replacement", context.unregisteredReceivers.contains(midReceiver))
        assertTrue("new receiver must be registered", context.registeredReceivers.contains(newReceiver))
        assertFalse("stale queue must be empty after retry", getStaleModuleReceiversMap().containsKey("staleKey"))
    }

    @Test
    fun moduleReceiver_staleQueueIsBounded() {
        val context = TrackableContext()
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        // Fill the stale queue by repeatedly failing to unregister the previous receiver.
        val receivers = mutableListOf<BroadcastReceiver>()
        repeat(5) { index ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {}
            }
            receivers.add(receiver)
            if (index > 0) context.failNextUnregister = true
            ReceiverRegistry.registerModuleReceiver(
                context, "boundedKey", receiver, filter, Context.RECEIVER_NOT_EXPORTED
            )
        }

        val staleMap = getStaleModuleReceiversMap()
        val staleQueue = staleMap["boundedKey"] as? Collection<*>
        assertNotNull("stale queue must exist", staleQueue)
        assertTrue("stale queue must be bounded", staleQueue!!.size <= 3)
    }

    @Test
    fun moduleReceiver_registerOomDoesNotLeaveTrackedRegistration() {
        val context = TrackableContext()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("android.intent.action.TIME_TICK")

        context.oomNextRegister = true
        try {
            ReceiverRegistry.registerModuleReceiver(
                context, "oomKey", receiver, filter, Context.RECEIVER_NOT_EXPORTED
            )
            fail("OutOfMemoryError must propagate")
        } catch (oom: OutOfMemoryError) {
            assertEquals("OOM must remove the in-flight registration", 0, getModuleReceiversMap().size)
            assertTrue("OOM must not register the receiver with the framework", context.registeredReceivers.isEmpty())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getModuleReceiversMap(): ConcurrentHashMap<String, Any> {
        val field = ReceiverRegistry::class.java.getDeclaredField("moduleReceivers")
            .apply { isAccessible = true }
        return field.get(null) as ConcurrentHashMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun getStaleModuleReceiversMap(): ConcurrentHashMap<String, Any> {
        val field = ReceiverRegistry::class.java.getDeclaredField("staleModuleReceivers")
            .apply { isAccessible = true }
        return field.get(null) as ConcurrentHashMap<String, Any>
    }

    private fun getReceiverFromRegistration(registration: Any?): BroadcastReceiver? {
        if (registration == null) return null
        val field = registration.javaClass.getDeclaredField("receiver")
            .apply { isAccessible = true }
        return field.get(registration) as? BroadcastReceiver
    }
}
