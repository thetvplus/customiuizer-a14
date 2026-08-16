package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ControlCenterGestureRuntimeHolderTest {

    private val configPublisher = GestureConfigPublisher({ GestureConfig() })
    private val effectExecutor = FakeGestureEffectExecutor()

    private fun dependenciesFor(ownerId: Int, stub: BrightnessDisplayStub): GestureDependencies {
        return GestureDependencies(
            ownerId = ownerId,
            classLoaderIdentity = "test",
            displayManager = stub,
            displayId = 0,
            minimumBacklight = 0.0f,
            maximumBacklight = 1.0f,
            audioManager = Any(),
            statusBarHeight = 80,
            screenWidth = 1080,
            density = 3.0f,
            getBrightnessMethod = BrightnessDisplayStub::class.java.getMethod("getBrightness", Int::class.java),
        )
    }

    private fun holder(stub: BrightnessDisplayStub, arbiter: PhysicalGestureArbiter): Pair<ControlCenterGestureRuntimeHolder, MutableList<Pair<ClassLoader, GestureMachine>>> {
        val installed = mutableListOf<Pair<ClassLoader, GestureMachine>>()
        val resolver = object : GestureDependenciesResolver {
            override fun prepare(
                ownerId: Int,
                classLoaderIdentity: String,
                context: Any,
            ): GestureDependenciesResult = GestureDependenciesResult.Ready(dependenciesFor(ownerId, stub))
        }
        val h = ControlCenterGestureRuntimeHolder(
            configPublisher = configPublisher,
            effectExecutor = effectExecutor,
            arbiter = arbiter,
            dependenciesResolver = resolver,
            installHooks = { loader, machine -> installed.add(loader to machine) },
        )
        return h to installed
    }

    @Test
    fun activeRuntime_isNotPublishedBeforeInstallHooksSucceed() {
        val loader = FakeClassLoader()
        val (h, _) = holder(BrightnessDisplayStub(), PhysicalGestureArbiter())

        val runtime = h.bind(loader)

        assertNotNull(h.activeRuntime())
        assertSame(loader, h.activeRuntime()?.classLoader)
    }

    @Test
    fun installHooksFailure_doesNotPublishActiveRuntime() {
        val loader = FakeClassLoader()
        var callCount = 0
        val resolver = object : GestureDependenciesResolver {
            override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult {
                return GestureDependenciesResult.NotReady
            }
        }
        val h = ControlCenterGestureRuntimeHolder(
            configPublisher = configPublisher,
            effectExecutor = effectExecutor,
            arbiter = PhysicalGestureArbiter(),
            dependenciesResolver = resolver,
            installHooks = { _, _ ->
                callCount++
                throw RuntimeException("install hooks failed")
            },
        )

        try {
            h.bind(loader)
        } catch (_: RuntimeException) {
            // expected
        }

        assertEquals(1, callCount)
        assertNull(h.activeRuntime())
    }

    @Test
    fun sameLoader_doesNotReinstallHooks() {
        val classLoader = FakeClassLoader()
        val (h, installed) = holder(BrightnessDisplayStub(), PhysicalGestureArbiter())

        h.bind(classLoader)
        h.bind(classLoader)

        assertEquals(1, installed.size)
        assertSame(classLoader, installed[0].first)
    }

    @Test
    fun newLoader_clearsOldMachine() {
        val loaderA = FakeClassLoader()
        val loaderB = FakeClassLoader()
        val arbiter = PhysicalGestureArbiter()
        val stub = BrightnessDisplayStub()
        val (h, _) = holder(stub, arbiter)

        val runtimeA = h.bind(loaderA)
        val machineA = runtimeA.machine

        // Acquire a token on the old machine.
        machineA.prepare(1, Any())
        val token = GestureEvent(
            entry = GestureEntry.CONTROL_CENTER_TOUCH,
            actionMasked = GestureAction.DOWN,
            downTime = 0L,
            eventTime = 0L,
            x = 100f,
            y = 10f,
            pointerCount = 1,
            ownerId = 1,
            deviceId = 1,
            source = 0x1002,
        )
        machineA.dispatch(token, Any())
        assertEquals(1, arbiter.heldTokenCount())

        val runtimeB = h.bind(loaderB)

        assertNotSame(machineA, runtimeB.machine)
        assertEquals(0, arbiter.heldTokenCount())
    }

    @Test
    fun newLoader_replacesActiveRuntime() {
        val loaderA = FakeClassLoader()
        val loaderB = FakeClassLoader()
        val (h, _) = holder(BrightnessDisplayStub(), PhysicalGestureArbiter())

        val runtimeA = h.bind(loaderA)
        val runtimeB = h.bind(loaderB)

        assertNotSame(runtimeA, runtimeB)
        assertSame(loaderB, h.activeRuntime()?.classLoader)
    }

    @Test
    fun repeatedLoaderReplacement_doesNotGrowRuntimeState() {
        val loaders = List(5) { FakeClassLoader() }
        val (h, installed) = holder(BrightnessDisplayStub(), PhysicalGestureArbiter())

        for (loader in loaders) {
            h.bind(loader)
        }

        assertEquals(5, installed.size)
        assertNotNull(h.activeRuntime())
        assertSame(loaders.last(), h.activeRuntime()?.classLoader)
    }

    @Test
    fun oldLoaderDetach_doesNotClearNewRuntime() {
        val loaderA = FakeClassLoader()
        val loaderB = FakeClassLoader()
        val arbiter = PhysicalGestureArbiter()
        val stub = BrightnessDisplayStub()
        val (h, _) = holder(stub, arbiter)

        h.bind(loaderA)
        val runtimeB = h.bind(loaderB)

        // The new machine should be able to acquire a token after the old runtime was cleared.
        runtimeB.machine.prepare(2, Any())
        runtimeB.machine.dispatch(
            GestureEvent(
                entry = GestureEntry.CONTROL_CENTER_TOUCH,
                actionMasked = GestureAction.DOWN,
                downTime = 0L,
                eventTime = 0L,
                x = 100f,
                y = 10f,
                pointerCount = 1,
                ownerId = 2,
                deviceId = 2,
                source = 0x1002,
            ),
            Any(),
        )

        assertEquals(1, arbiter.heldTokenCount())
        assertSame(loaderB, h.activeRuntime()?.classLoader)
    }

    @Test
    fun runtimeIdentity_isRuntimeToken_notClassLoaderToString() {
        val loader = FakeClassLoader()
        val (h, installed) = holder(BrightnessDisplayStub(), PhysicalGestureArbiter())

        h.bind(loader)

        assertEquals(1, installed.size)
        assertEquals("cc-1", installed[0].second.classLoaderIdentity())
        assertNotEquals(loader.toString(), installed[0].second.classLoaderIdentity())
    }

    @Test
    fun twoDifferentLoaders_getDifferentIdentities() {
        val (h, _) = holder(BrightnessDisplayStub(), PhysicalGestureArbiter())

        val runtimeA = h.bind(FakeClassLoader())
        val runtimeB = h.bind(FakeClassLoader())

        assertNotEquals(runtimeA.machine.classLoaderIdentity(), runtimeB.machine.classLoaderIdentity())
    }

    @Test
    fun unbind_clearsMachineAndDropsRuntime() {
        val loader = FakeClassLoader()
        val arbiter = PhysicalGestureArbiter()
        val (h, _) = holder(BrightnessDisplayStub(), arbiter)

        val runtime = h.bind(loader)
        runtime.machine.prepare(1, Any())
        runtime.machine.dispatch(
            GestureEvent(
                entry = GestureEntry.CONTROL_CENTER_TOUCH,
                actionMasked = GestureAction.DOWN,
                downTime = 0L,
                eventTime = 0L,
                x = 100f,
                y = 10f,
                pointerCount = 1,
                ownerId = 1,
                deviceId = 1,
                source = 0x1002,
            ),
            Any(),
        )
        assertEquals(1, arbiter.heldTokenCount())

        h.unbind()

        assertEquals(0, arbiter.heldTokenCount())
        assertEquals(null, h.activeRuntime())
    }

    @Test
    fun unbind_twice_is_idempotent() {
        val loader = FakeClassLoader()
        val arbiter = PhysicalGestureArbiter()
        val (h, _) = holder(BrightnessDisplayStub(), arbiter)

        h.bind(loader)
        h.unbind()
        h.unbind()

        assertEquals(0, arbiter.heldTokenCount())
        assertEquals(null, h.activeRuntime())
    }

    @Test
    fun bind_after_unbind_creates_new_runtime() {
        val loader = FakeClassLoader()
        val arbiter = PhysicalGestureArbiter()
        val (h, _) = holder(BrightnessDisplayStub(), arbiter)

        val runtimeA = h.bind(loader)
        h.unbind()
        val runtimeB = h.bind(loader)

        assertNotSame(runtimeA, runtimeB)
        assertEquals("cc-2", runtimeB.machine.classLoaderIdentity())
    }

    private class FakeClassLoader : ClassLoader() {
        override fun toString(): String = "FakeClassLoader@${hashCode()}"
    }
}
