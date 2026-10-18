package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Fault injection into production registration/cleanup paths; no Android runtime claim. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationFatalBoundaryTest(
    @Suppress("UNUSED_PARAMETER") name: String,
    private val failure: Throwable,
    private val fatal: Throwable,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val oom = OutOfMemoryError("injected")
            val death = ThreadDeath()
            val vm = InternalError("injected")
            return listOf(
                arrayOf("oom", oom, oom),
                arrayOf("thread-death", death, death),
                arrayOf("vm-error", vm, vm),
                arrayOf("reflection", InvocationTargetException(vm), vm),
                arrayOf("proxy", UndeclaredThrowableException(oom), oom),
                arrayOf("execution", ExecutionException(death), death),
                arrayOf("xposed", XposedHelpers.InvocationTargetError(oom), oom),
            )
        }
    }

    private class FailingContext : Application() {
        var registerFailure: Throwable? = null
        var unregisterFailure: Throwable? = null
        var registerCalls = 0
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? =
            registerReceiver(receiver, filter, null, null, flags)

        override fun registerReceiver(
            receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?,
            scheduler: Handler?, flags: Int,
        ): Intent? {
            registerCalls++
            registerFailure?.let { throw it }
            return null
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            unregisterFailure?.let { throw it }
        }
    }

    private val context = FailingContext()
    private val owner = Any()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {}
    }
    private val key = "fatal-boundary"

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun clearRegistries() {
        for (name in listOf("moduleReceivers", "staleModuleReceivers", "ownedReceivers",
            "moduleRegistrations", "staleModuleRegistrations")) {
            registry(name).clear()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun moduleRegistrationFailurePropagatesAndRollsBack() {
        context.registerFailure = failure
        expectFatal { registerModule(receiver) }
        assertTrue(registry("moduleReceivers").isEmpty())
    }

    @Test
    fun moduleReplacementCleanupPropagatesWithoutTrackingUnregisteredSuccessor() {
        registerModule(receiver)
        context.unregisterFailure = failure
        expectFatal { registerModule(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }) }
        assertEquals(1, context.registerCalls)
        assertTrue(registry("moduleReceivers").isEmpty())
    }

    @Test
    fun moduleExplicitCleanupPropagates() {
        registerModule(receiver)
        context.unregisterFailure = failure
        expectFatal { ReceiverRegistry.unregisterModuleReceiver(key) }
        assertTrue(registry("moduleReceivers").isEmpty())
    }

    @Test
    fun moduleStaleRetryPropagates() {
        registerModule(receiver)
        context.unregisterFailure = IllegalStateException("retryable")
        ReceiverRegistry.unregisterModuleReceiver(key)
        assertTrue(registry("staleModuleReceivers").containsKey(key))
        context.unregisterFailure = failure
        expectFatal { registerModule(receiver) }
        assertEquals(1, context.registerCalls)
    }

    @Test
    fun ownedRegistrationFailurePropagatesAndRollsBack() {
        context.registerFailure = failure
        expectFatal { registerOwned() }
        assertTrue(registry("ownedReceivers").isEmpty())
    }

    @Test
    fun ownedReplacementCleanupPropagatesAndRollsBack() {
        registerOwned()
        context.unregisterFailure = failure
        expectFatal { registerOwned() }
        assertEquals(1, context.registerCalls)
        assertTrue(registry("ownedReceivers").isEmpty())
    }

    @Test
    fun ownedExplicitCleanupPropagates() {
        registerOwned()
        context.unregisterFailure = failure
        expectFatal { ReceiverRegistry.unregisterOwnedReceiver(owner, key) }
        assertTrue(registry("ownedReceivers").isEmpty())
    }

    @Test
    fun collectedOwnerCleanupPropagates() {
        val owned = registerOwned()
        val field = owned.javaClass.getDeclaredField("ownerRef").apply { isAccessible = true }
        (field.get(owned) as WeakReference<*>).clear()
        context.unregisterFailure = failure
        expectFatal { owned.onReceive(context, Intent()) }
        assertTrue(registry("ownedReceivers").isEmpty())
    }

    @Test
    fun generalCleanupPropagatesAndDoesNotPublishSuccessor() {
        ReceiverRegistry.replaceModuleRegistration(key) { throw failure }
        expectFatal { ReceiverRegistry.replaceModuleRegistration(key) {} }
        assertTrue(registry("moduleRegistrations").isEmpty())
    }

    @Test
    fun hookInstallationPropagatesWrappedFatalAndCanRecover() {
        val machine = HookInstallStateMachine()
        expectFatal { machine.install { throw failure } }
        assertEquals(HookInstallStateMachine.State.UNINSTALLED, machine.state)
        assertTrue(machine.install { true })
    }

    @Test
    fun screenRegistrationFailureRollsBackAndCanRetry() {
        val listener = object : ScreenStateController.ScreenStateListener {
            override fun onScreenStateChanged(isOn: Boolean) {}
        }
        try {
            context.registerFailure = failure
            expectFatal { ScreenStateController.addListener(context, listener) }
            context.registerFailure = null
            ScreenStateController.addListener(context, listener)
            assertEquals(2, context.registerCalls)
        } finally {
            ScreenStateController.removeListener(listener)
        }
    }

    @Test
    fun screenCleanupPropagatesAndDropsContext() {
        val listener = object : ScreenStateController.ScreenStateListener {
            override fun onScreenStateChanged(isOn: Boolean) {}
        }
        ScreenStateController.addListener(context, listener)
        context.unregisterFailure = failure
        expectFatal { ScreenStateController.removeListener(listener) }
        val field = ScreenStateController::class.java.getDeclaredField("appContext").apply { isAccessible = true }
        assertEquals(null, field.get(null))
    }

    @Test
    fun tickCleanupPropagatesForBothControllers() {
        for (controller in listOf(StepCounterController, WeatherDataController)) {
            val cls = controller.javaClass
            val tick = cls.getDeclaredField("timeTickReceiver").apply { isAccessible = true }
            val registered = cls.getDeclaredField("timeTickRegistered").apply { isAccessible = true }
            val oldTick = tick.get(null)
            val oldRegistered = registered.getBoolean(null)
            try {
                tick.set(null, receiver)
                registered.setBoolean(null, true)
                context.unregisterFailure = failure
                expectFatal {
                    try {
                        cls.getDeclaredMethod("unregisterTick", Context::class.java)
                            .apply { isAccessible = true }.invoke(controller, context)
                    } catch (e: InvocationTargetException) {
                        throw e.targetException
                    }
                }
            } finally {
                tick.set(null, oldTick)
                registered.setBoolean(null, oldRegistered)
            }
        }
    }

    @Test
    fun callbackBoundaryPropagatesWrappedFatal() {
        expectFatal { CallbackGuard.guarded { throw failure } }
        expectFatal { CallbackGuard.guarded(false) { throw failure } }
    }

    @Test
    fun loggingBoundaryCannotHideWrappedFatal() {
        expectFatal { XposedHelpers.log(failure) }
        expectFatal { XposedHelpers.log("injected", failure) }
    }

    private fun registerModule(value: BroadcastReceiver) = ReceiverRegistry.registerModuleReceiver(
        context, key, value, IntentFilter(), Context.RECEIVER_NOT_EXPORTED,
    )

    private fun registerOwned() = ReceiverRegistry.registerOwnedReceiver(
        context, owner, key, IntentFilter(), Context.RECEIVER_NOT_EXPORTED,
    ) { _, _, _, _ -> }

    private fun expectFatal(action: () -> Unit) {
        try {
            action()
            fail("fatal failure must propagate")
        } catch (actual: Throwable) {
            assertSame(fatal, actual)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun registry(name: String): MutableMap<String, Any> =
        ReceiverRegistry::class.java.getDeclaredField(name).apply { isAccessible = true }
            .get(null) as MutableMap<String, Any>
}
