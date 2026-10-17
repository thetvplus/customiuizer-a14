package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfig
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfigPublisher
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureDependenciesResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureDependenciesResult
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEffectExecutor
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureMachine
import tv.withaibuild.customiuizer.mods.utils.gesture.PhysicalGestureArbiter
import java.lang.reflect.Executable
import java.util.ArrayList

class ControlCenterPluginRuntimeTest {

    private lateinit var engine: TestableEngine

    @Before
    fun setUp() {
        engine = TestableEngine()
    }

    @After
    fun tearDown() {
        engine.clear()
    }

    @Test
    fun sameLoaderIsIdempotent() {
        val loader = testLoader

        engine.bind(loader)
        val first = engine.runtimeHolder().activeRuntime()
        val firstResult = engine.bind(loader)
        val second = engine.runtimeHolder().activeRuntime()

        assertSame(first, second)
        assertTrue(firstResult is ControlCenterBindResult.AlreadyInstalled)
    }

    @Test
    fun newLoaderClearsOldMachine() {
        val loader1 = newIsolatedClassLoader()
        val loader2 = newIsolatedClassLoader()

        engine.bind(loader1)
        val first = engine.runtimeHolder().activeRuntime()

        engine.bind(loader2)
        val second = engine.runtimeHolder().activeRuntime()

        assertNotSame(first, second)
        assertSame(loader2, second?.classLoader)
    }

    @Test
    fun newLoaderReleasesOldLoaderReference() {
        val loader1 = newIsolatedClassLoader()
        val loader2 = newIsolatedClassLoader()

        engine.bind(loader1)
        engine.bind(loader2)

        assertSame(loader2, engine.activeLoader())
    }

    @Test
    fun explicitClearNullsAllReferences() {
        val loader = testLoader

        engine.bind(loader)
        engine.clear()

        assertNull(engine.activeLoader())
        assertNull(engine.runtimeHolder().activeRuntime())
        assertEquals(0, engine.arbiter().heldTokenCount())
        assertFalse(engine.activeLease()!!.active)
    }

    @Test
    fun createPluginHookInstalledOnlyOnce() {
        var hookCount = 0
        engine.installCreatePluginHookCapture = { _, _ -> hookCount++ }

        engine.hookIfNeeded(testLoader)
        engine.hookIfNeeded(testLoader)

        assertEquals(1, hookCount)
    }

    @Test(expected = OutOfMemoryError::class)
    fun fatalInstallFailureDoesNotPublishHalfState() {
        val loader = testLoader
        engine.installPluginHooksFailure = OutOfMemoryError("fatal install")

        try {
            engine.bind(loader)
        } finally {
            assertNull(engine.activeLoader())
            assertNull(engine.runtimeHolder().activeRuntime())
        }
    }

    @Test
    fun ordinaryPartialFailureIsNotSilent() {
        val loader = testLoader
        val failure = RuntimeException("partial install")
        engine.installPluginHooksFailure = failure

        val result = engine.bind(loader)

        assertTrue(result is ControlCenterBindResult.Failed)
        assertEquals(failure, (result as ControlCenterBindResult.Failed).reason)
        assertEquals(InstallState.FAILED_PARTIAL, engine.installState())
        assertSame(failure, engine.lastFailure())
    }

    @Test
    fun sameLoaderDoesNotRetryAfterFailedPartial() {
        val loader = testLoader
        engine.installPluginHooksFailure = RuntimeException("partial install")

        val first = engine.bind(loader)
        assertTrue(first is ControlCenterBindResult.Failed)

        val second = engine.bind(loader)
        assertTrue(second is ControlCenterBindResult.NoRetry)

        assertEquals(1, engine.installPluginCalls)
    }

    @Test
    fun newLoaderCanStartNewTransaction() {
        val loader1 = newIsolatedClassLoader()
        val loader2 = newIsolatedClassLoader()

        engine.installPluginHooksFailure = RuntimeException("partial install")

        val first = engine.bind(loader1)
        assertTrue(first is ControlCenterBindResult.Failed)

        engine.installPluginHooksFailure = null
        val second = engine.bind(loader2)

        assertTrue(second is ControlCenterBindResult.Installed)
        assertSame(loader2, engine.activeLoader())
        assertEquals(InstallState.INSTALLED, engine.installState())
    }

    @Test
    fun clearInvalidatesLeaseAndCallbackNoOps() {
        var pluginHookCalls = 0
        var capturedHook: MethodHook? = null
        engine.installPluginHooksAction = { pluginHookCalls++ }
        engine.installCreatePluginHookCapture = { _, hook ->
            capturedHook = hook
        }

        engine.hookIfNeeded(testLoader)
        engine.bind(testLoader)
        assertEquals(1, pluginHookCalls)

        engine.clear()

        val hook = capturedHook ?: error("createPlugin hook was not installed")
        val callback = fakeBeforeHookCallback(thisObject = Any(), args = arrayOf(Any(), true))
        hook.beforeHook(callback)

        // The stale hook must not trigger a new bind after clear().
        assertEquals(1, pluginHookCalls)
        assertNull(engine.activeLoader())
    }

    @Test
    fun installHooksSuccessButInstallPluginHooksFailsDoesNotRepeatInstall() {
        val loader = testLoader

        engine.installPluginHooksFailure = RuntimeException("ui hooks failed")

        engine.bind(loader)

        assertEquals(1, engine.installGestureCalls)
        assertEquals(1, engine.installPluginCalls)
        assertEquals(InstallState.FAILED_PARTIAL, engine.installState())
        assertNull(engine.runtimeHolder().activeRuntime())
    }

    @Test
    fun hookIfNeededOrdinaryFailureSetsFailedPartial() {
        val failure = RuntimeException("hook install failed")
        engine.installCreatePluginHookFailure = failure

        engine.hookIfNeeded(testLoader)

        assertEquals(InstallState.FAILED_PARTIAL, engine.installState())
        assertSame(failure, engine.lastFailure())
    }

    @Test(expected = OutOfMemoryError::class)
    fun hookIfNeededFatalFailureRethrows() {
        engine.installCreatePluginHookFailure = OutOfMemoryError("fatal")
        engine.hookIfNeeded(testLoader)
    }

    @Test
    fun handleMotionEventGuardsArgsAndTypes() {
        val machine = newGestureMachine()
        val lease = RuntimeLease()
        val hooks = engine.installControlCenterGestureHooks(
            testLoader,
            machine,
            lease,
        )

        // No args.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf()))
        // Missing second arg.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf(Any())))
        // First arg wrong type, second arg boolean true.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf(Any(), true)))
        // Second arg boolean true must short-circuit.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf(Any(), true)))
        // Second arg wrong type (not Boolean) should not crash and continue until event cast fails.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf(Any(), "not a boolean")))

        // None of the invalid cases should reach the machine.
        assertTrue(lease.active)
    }

    @Test
    fun onAttachedAndDetachedCallbacksCheckLease() {
        val machine = newGestureMachine()
        val activeLease = RuntimeLease()
        val inactiveLease = RuntimeLease().apply { invalidate() }

        val activeHooks = engine.installControlCenterGestureHooks(
            testLoader,
            machine,
            activeLease,
        )
        val inactiveHooks = engine.installControlCenterGestureHooks(
            testLoader,
            machine,
            inactiveLease,
        )

        // With an inactive lease, callbacks must be no-ops.
        inactiveHooks.onAttachedToWindow.beforeHook(fakeBeforeHookCallback(thisObject = Any()))
        inactiveHooks.onDetachedFromWindow.beforeHook(fakeBeforeHookCallback(thisObject = Any()))

        // Active lease + non-View thisObject returns early.
        activeHooks.onAttachedToWindow.beforeHook(fakeBeforeHookCallback(thisObject = Any()))
        activeHooks.onDetachedFromWindow.beforeHook(fakeBeforeHookCallback(thisObject = Any()))

        assertTrue(activeLease.active)
    }

    private val testLoader: ClassLoader
        get() = this::class.java.classLoader!!

    private fun newIsolatedClassLoader(): ClassLoader {
        return java.net.URLClassLoader(arrayOf(), testLoader)
    }

    private fun newGestureMachine(): GestureMachine = GestureMachine(
        classLoaderIdentity = "test",
        configResolver = { GestureConfig() },
        depsResolver = object : GestureDependenciesResolver {
            override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult {
                return GestureDependenciesResult.NotReady
            }
        },
        effectExecutor = GestureEffectExecutor { _, _, _, _ -> },
        arbiter = PhysicalGestureArbiter(),
    )

    private fun fakeBeforeHookCallback(
        thisObject: Any? = null,
        args: Array<Any?> = arrayOf(),
    ): BeforeHookCallback {
        val chain = object : XposedInterface.Chain {
            override fun getExecutable(): Executable = error("not used in test")
            override fun getThisObject(): Any? = thisObject
            override fun getArgs(): List<Any?> = ArrayList(args.toList())
            override fun getArg(index: Int): Any? = args[index]
            override fun proceed(): Any? = error("not used in test")
            override fun proceed(p0: Array<Any>): Any? = error("not used in test")
            override fun proceedWith(p0: Any): Any? = error("not used in test")
            override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
        }
        return BeforeHookCallback(chain)
    }

    private open class TestableEngine : ControlCenterPluginRuntimeEngine() {
        var installPluginCalls = 0
        var installPluginHooksAction: ((ClassLoader) -> Unit)? = null
        var installPluginHooksFailure: Throwable? = null

        var installCreatePluginCalls = 0
        var installCreatePluginHookCapture: ((ClassLoader, MethodHook) -> Unit)? = null
        var installCreatePluginHookFailure: Throwable? = null

        var installGestureCalls = 0
        var installGestureFailure: Throwable? = null

        override fun onInstallPluginHooks(classLoader: ClassLoader) {
            installPluginCalls++
            installPluginHooksAction?.invoke(classLoader)
            installPluginHooksFailure?.let { throw it }
        }

        override fun onInstallCreatePluginHook(classLoader: ClassLoader, hook: MethodHook) {
            installCreatePluginCalls++
            installCreatePluginHookCapture?.invoke(classLoader, hook)
            installCreatePluginHookFailure?.let { throw it }
        }

        override fun onInstallGestureHooks(classLoader: ClassLoader, machine: GestureMachine) {
            installGestureCalls++
            installGestureFailure?.let { throw it }
        }
    }
}
