package tv.withaibuild.customiuizer.mods.utils

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class FeatureInstallRegistryTest {

    @Before
    fun reset() {
        FeatureInstallStateTestAccess.clear()
        HookDiagnostics.reset()
        HookDiagnostics.currentProcessName = "test"
    }

    private object ids {
        private var counter = 10000
        fun nextId(): Int = counter++
    }

    private class TestId(override val name: String) : FeatureId {
        override val id = ids.nextId()
    }

    private class DummyFeature(
        override val name: String,
        override val preferenceKey: String? = null,
        override val target: FeatureTarget = FeatureTarget.SYSTEM_UI,
        override val phase: InstallPhase = InstallPhase.PACKAGE_READY,
        val enabled: Boolean = true,
        var result: FeatureInstallResult = FeatureInstallResult.INSTALLED,
    ) : FeatureDefinition {
        override val id = TestId(name)
        var installCalls = 0
        override fun isEnabled(prefs: PrefMap): Boolean = enabled
        override fun install(): FeatureInstallResult {
            installCalls++
            return result
        }
    }

    @Test
    fun installAll_onlyMatchesTargetAndPhase() {
        val registry = FeatureInstallRegistry()
        val hit = DummyFeature("hit", target = FeatureTarget.SYSTEM_UI, phase = InstallPhase.PACKAGE_READY)
        val wrongTarget = DummyFeature("wrongTarget", target = FeatureTarget.LAUNCHER, phase = InstallPhase.PACKAGE_READY)
        val wrongPhase = DummyFeature("wrongPhase", target = FeatureTarget.SYSTEM_UI, phase = InstallPhase.SYSTEM_UI_INITIALIZED)

        registry.register(hit)
        registry.register(wrongTarget)
        registry.register(wrongPhase)

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertEquals(FeatureInstallResult.INSTALLED, results[0])
        assertEquals(1, hit.installCalls)
        assertEquals(0, wrongTarget.installCalls)
        assertEquals(0, wrongPhase.installCalls)
    }

    @Test
    fun installAll_disabledFeatureSkipped() {
        val registry = FeatureInstallRegistry()
        val off = DummyFeature("off", enabled = false)
        registry.register(off)

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertTrue(results[0] == FeatureInstallResult.SKIPPED)
        assertEquals("disabled feature must not call install", 0, off.installCalls)
        assertEquals(FeatureState.NOT_INSTALLED, FeatureInstallState.get(off.id))
    }

    @Test
    fun installAll_idempotent() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("idempotent")
        registry.register(f)

        val first = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        val second = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, f.installCalls)
        assertEquals(FeatureInstallResult.INSTALLED, first[0])
        assertEquals(FeatureInstallResult.ALREADY_INSTALLED, second[0])
    }

    @Test
    fun separateRegistriesDoNotResetInstalledProcessState() {
        val feature = DummyFeature("process-idempotent")
        val first = FeatureInstallRegistry()
        first.register(feature)
        first.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        val second = FeatureInstallRegistry()
        second.register(feature)
        val results = second.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )

        assertEquals(1, feature.installCalls)
        assertEquals(FeatureInstallResult.ALREADY_INSTALLED, results.single())
    }

    @Test
    fun beginInstallClaimIsAtomic() {
        val id = TestId("atomic-claim")
        FeatureInstallState.initialize(id)

        assertEquals(FeatureState.NOT_INSTALLED, FeatureInstallState.beginInstall(id))
        assertEquals(FeatureState.INSTALLING, FeatureInstallState.beginInstall(id))
    }

    @Test
    fun installAll_failedTransientCanRetryAndSucceed() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("transient-retry", result = FeatureInstallResult.FAILED_TRANSIENT)
        registry.register(f)

        val first = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )
        assertEquals(listOf(FeatureInstallResult.FAILED_TRANSIENT), first)
        assertEquals(1, f.installCalls)
        assertEquals(FeatureState.FAILED_TRANSIENT, FeatureInstallState.get(f.id))

        f.result = FeatureInstallResult.INSTALLED
        val second = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )
        assertEquals(listOf(FeatureInstallResult.INSTALLED), second)
        assertEquals(2, f.installCalls)
        assertEquals(FeatureState.INSTALLED, FeatureInstallState.get(f.id))

        val third = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )
        assertEquals(listOf(FeatureInstallResult.ALREADY_INSTALLED), third)
        assertEquals(2, f.installCalls)
    }

    @Test
    fun installAll_failedPermanentDoesNotRetry() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("permanent", result = FeatureInstallResult.FAILED_PERMANENT)
        registry.register(f)

        val first = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )
        val second = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )

        assertEquals(1, f.installCalls)
        assertEquals(FeatureInstallResult.FAILED_PERMANENT, first[0])
        assertEquals(FeatureInstallResult.FAILED_PERMANENT, second[0])
        assertEquals(FeatureState.FAILED_PERMANENT, FeatureInstallState.get(f.id))
    }

    @Test
    fun installAll_collectResultsFalseReturnsEmpty() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("silent")
        registry.register(f)

        val results = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = false
        )

        assertTrue(results.isEmpty())
        assertEquals(1, f.installCalls)
        assertEquals(FeatureState.INSTALLED, FeatureInstallState.get(f.id))
    }

    @Test
    fun register_sameDefinitionIsIdempotent() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("same")
        registry.register(f)
        registry.register(f)

        val results = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )

        assertEquals(1, results.size)
        assertEquals(FeatureInstallResult.INSTALLED, results[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun register_differentDefinitionSameIdThrows() {
        val registry = FeatureInstallRegistry()
        val sharedId = TestId("shared")
        val a = object : FeatureDefinition {
            override val id = sharedId
            override val name = "a"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install() = FeatureInstallResult.INSTALLED
        }
        val b = object : FeatureDefinition {
            override val id = sharedId
            override val name = "b"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install() = FeatureInstallResult.INSTALLED
        }
        registry.register(a)
        registry.register(b)
    }

    @Test
    fun installAll_failureIsolation() {
        val registry = FeatureInstallRegistry()
        val broken = object : FeatureDefinition {
            override val id = TestId("broken")
            override val name = "broken"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult = throw RuntimeException("non-fatal")
        }
        val ok = DummyFeature("ok")
        registry.register(broken)
        registry.register(ok)

        val results = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )

        assertEquals(2, results.size)
        assertEquals(FeatureInstallResult.FAILED_TRANSIENT, results[0])
        assertEquals(FeatureInstallResult.INSTALLED, results[1])
        assertEquals(FeatureState.FAILED_TRANSIENT, FeatureInstallState.get(broken.id))
        assertEquals(FeatureState.INSTALLED, FeatureInstallState.get(ok.id))
    }

    @Test
    fun installAll_exceptionBecomesTransient() {
        val registry = FeatureInstallRegistry()
        val f = object : FeatureDefinition {
            override val id = TestId("explode")
            override val name = "explode"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult = throw IllegalStateException("boom")
        }
        registry.register(f)

        val results = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )

        assertTrue(results[0] == FeatureInstallResult.FAILED_TRANSIENT)
        assertEquals(FeatureState.FAILED_TRANSIENT, FeatureInstallState.get(f.id))
    }

    @Test
    fun lazySpec_disabledFeatureDoesNotCreateDefinition() {
        val registry = FeatureInstallRegistry()
        var createCalls = 0
        val spec = LazyFeatureSpec(
            id = TestId("lazy-off"),
            name = "Lazy Off",
            preferenceKey = "lazy_off",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { false },
            factory = {
                createCalls++
                DummyFeature("lazy-off")
            },
        )

        registry.register(spec)
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertTrue(results[0] == FeatureInstallResult.SKIPPED)
        assertEquals("disabled feature must not call factory", 0, createCalls)
    }

    @Test
    fun lazySpec_enabledFeatureCreatesAndInstalls() {
        val registry = FeatureInstallRegistry()
        var createCalls = 0
        val definition = object : FeatureDefinition {
            override val id = TestId("lazy-on")
            override val name = "Lazy On"
            override val preferenceKey: String? = "lazy_on"
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            var installCalls = 0
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult {
                installCalls++
                return FeatureInstallResult.INSTALLED
            }
        }
        val spec = LazyFeatureSpec(
            id = definition.id,
            name = definition.name,
            preferenceKey = definition.preferenceKey,
            target = definition.target,
            phase = definition.phase,
            enabled = { true },
            factory = {
                createCalls++
                definition
            },
        )

        registry.register(spec)
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertTrue(results[0] == FeatureInstallResult.INSTALLED)
        assertEquals(1, createCalls)
        assertEquals(1, definition.installCalls)
    }

    @Test(expected = OutOfMemoryError::class)
    fun installOne_rethrowsOutOfMemoryErrorAndRollsBackState() {
        val registry = FeatureInstallRegistry()
        val spec = LazyFeatureSpec(
            id = TestId("oom"),
            name = "OOM",
            preferenceKey = "oom",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { true },
            factory = { throw OutOfMemoryError("OOM in factory") },
        )
        registry.register(spec)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        } finally {
            assertTrue(FeatureInstallState.get(spec.id) == FeatureState.FAILED_TRANSIENT)
        }
    }

    @Test(expected = OutOfMemoryError::class)
    fun installOne_rethrowsOomFromCreatedDefinition() {
        val registry = FeatureInstallRegistry()
        val definition = object : FeatureDefinition {
            override val id = TestId("oom-install")
            override val name = "OOM Install"
            override val preferenceKey: String? = "oom_install"
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult {
                throw OutOfMemoryError("OOM in install")
            }
        }
        val spec = LazyFeatureSpec(
            id = definition.id,
            name = definition.name,
            preferenceKey = definition.preferenceKey,
            target = definition.target,
            phase = definition.phase,
            enabled = { true },
            factory = { definition },
        )
        registry.register(spec)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        } finally {
            assertTrue(FeatureInstallState.get(spec.id) == FeatureState.FAILED_TRANSIENT)
        }
    }

    @Test
    fun featureRegistry_rethrowsThreadDeath() {
        val feature = throwingFeature { throw ThreadDeath() }
        val registry = FeatureInstallRegistry()
        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("ThreadDeath must propagate")
        } catch (t: ThreadDeath) {
            assertTrue("ThreadDeath propagated", true)
        }

        assertEquals(
            "state must roll back to FAILED_TRANSIENT before rethrow",
            FeatureState.FAILED_TRANSIENT,
            FeatureInstallState.get(feature.id)
        )
    }

    @Test
    fun featureRegistry_rethrowsVirtualMachineError() {
        val feature = throwingFeature { throw TestVirtualMachineError() }
        val registry = FeatureInstallRegistry()
        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("VirtualMachineError must propagate")
        } catch (e: VirtualMachineError) {
            assertTrue("expected custom VirtualMachineError", e is TestVirtualMachineError)
        }

        assertEquals(
            "state must roll back to FAILED_TRANSIENT before rethrow",
            FeatureState.FAILED_TRANSIENT,
            FeatureInstallState.get(feature.id)
        )
    }

    @Test
    fun featureRegistry_rethrowsWrappedFatal() {
        val feature = throwingFeature { throw InvocationTargetException(ThreadDeath()) }
        val registry = FeatureInstallRegistry()
        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("wrapped ThreadDeath must propagate")
        } catch (t: ThreadDeath) {
            assertTrue("wrapped ThreadDeath propagated", true)
        }

        assertEquals(
            "state must roll back to FAILED_TRANSIENT before rethrow",
            FeatureState.FAILED_TRANSIENT,
            FeatureInstallState.get(feature.id)
        )
    }

    @Test
    fun featureRegistry_executionExceptionWrappingVirtualMachineError_propagates() {
        val feature = throwingFeature { throw ExecutionException(TestVirtualMachineError()) }
        val registry = FeatureInstallRegistry()
        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("ExecutionException-wrapped VirtualMachineError must propagate")
        } catch (e: VirtualMachineError) {
            assertTrue("expected unwrapped VirtualMachineError", e is TestVirtualMachineError)
        }

        assertEquals(
            FeatureState.FAILED_TRANSIENT,
            FeatureInstallState.get(feature.id)
        )
    }

    @Test
    fun featureRegistry_nonFatalStillReturnsFailedTransient() {
        val first = throwingFeature { throw RuntimeException("non-fatal") }
        val second = DummyFeature("non-fatal-second")

        val registry = FeatureInstallRegistry()
        registry.register(first)
        registry.register(second)

        val results = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )

        assertEquals(
            listOf(FeatureInstallResult.FAILED_TRANSIENT, FeatureInstallResult.INSTALLED),
            results
        )
        assertEquals(FeatureState.FAILED_TRANSIENT, FeatureInstallState.get(first.id))
        assertEquals(FeatureState.INSTALLED, FeatureInstallState.get(second.id))
    }

    private fun throwingFeature(install: () -> FeatureInstallResult): FeatureDefinition {
        val id = TestId("throwing-${ids.nextId()}")
        return object : FeatureDefinition {
            override val id = id
            override val name = "throwing"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install() = install()
        }
    }

    private class TestVirtualMachineError : VirtualMachineError("test virtual machine error")
}
