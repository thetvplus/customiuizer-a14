package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Tests for the public framework boundaries that must rethrow [OutOfMemoryError]
 * rather than converting it to a safe failure.  These boundaries also must not leave
 * behind a cached or registered state that looks like success after an OOM.
 */
class FatalBoundaryTest {

    private fun emptyChain(): XposedInterface.Chain {
        return Proxy.newProxyInstance(
            XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Byte::class.javaPrimitiveType -> 0.toByte()
                Short::class.javaPrimitiveType -> 0.toShort()
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                Char::class.javaPrimitiveType -> '\u0000'
                else -> null
            }
        } as XposedInterface.Chain
    }

    private class OomClassLoader : ClassLoader(FatalBoundaryTest::class.java.classLoader) {
        val loaded = mutableListOf<String>()
        override fun loadClass(name: String?, resolve: Boolean): Class<*> {
            if (name == "boom.Oom") throw OutOfMemoryError("simulated class load OOM")
            loaded.add(name ?: "null")
            return super.loadClass(name, resolve)
        }
    }

    private class VmThrowingClassLoader : ClassLoader(FatalBoundaryTest::class.java.classLoader) {
        override fun loadClass(name: String?, resolve: Boolean): Class<*> {
            if (name == "android.app.ActivityThread") throw InternalError("simulated application class loader VM error")
            if (name == "nonexistent.target.Class") throw ClassNotFoundException(name)
            return super.loadClass(name, resolve)
        }
    }

    private class FakeDependency {
        companion object {
            @JvmStatic
            fun get(clazz: Class<*>): Any? {
                if (throwOom) throw OutOfMemoryError("simulated dependency OOM")
                return if (ready) INSTANCE else null
            }

            var ready = false
            var throwOom = false
            val INSTANCE = Any()
        }
    }

    @After
    fun tearDown() {
        FakeDependency.ready = false
        FakeDependency.throwOom = false
    }

    @Test
    fun callbackGuard_swallowsRuntimeExceptionAndRuns() {
        var ran = false
        CallbackGuard.guarded {
            ran = true
            throw IllegalStateException("boom")
        }
        assertTrue("block must run", ran)
    }

    @Test(expected = OutOfMemoryError::class)
    fun callbackGuard_rethrowsOutOfMemoryError() {
        CallbackGuard.guarded {
            throw OutOfMemoryError("boom")
        }
    }

    @Test
    fun callbackGuard_returnsFallbackOnNonFatalError() {
        val result = CallbackGuard.guarded(fallback = 42) {
            throw IllegalStateException("boom")
        }
        assertEquals(42, result)
    }

    @Test(expected = OutOfMemoryError::class)
    fun callbackGuard_doesNotReturnFallbackOnOom() {
        CallbackGuard.guarded(fallback = 42) {
            throw OutOfMemoryError("boom")
        }
    }

    @Test(expected = OutOfMemoryError::class)
    fun methodHook_beforeCallback_rethrowsOutOfMemoryError() {
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                throw OutOfMemoryError("boom")
            }
        }
        hook.beforeHook(HookerClassHelper.BeforeHookCallback(emptyChain()))
    }

    @Test(expected = OutOfMemoryError::class)
    fun methodHook_afterCallback_rethrowsOutOfMemoryError() {
        val before = HookerClassHelper.BeforeHookCallback(emptyChain())
        val hook = object : HookerClassHelper.MethodHook() {
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                throw OutOfMemoryError("boom")
            }
        }
        hook.afterHook(HookerClassHelper.AfterHookCallback(before, null, null))
    }

    @Test(expected = ThreadDeath::class)
    fun methodHook_beforeCallback_rethrowsThreadDeath() {
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                throw ThreadDeath()
            }
        }
        hook.beforeHook(HookerClassHelper.BeforeHookCallback(emptyChain()))
    }

    @Test(expected = ThreadDeath::class)
    fun methodHook_afterCallback_rethrowsThreadDeath() {
        val before = HookerClassHelper.BeforeHookCallback(emptyChain())
        val hook = object : HookerClassHelper.MethodHook() {
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                throw ThreadDeath()
            }
        }
        hook.afterHook(HookerClassHelper.AfterHookCallback(before, null, null))
    }

    @Test(expected = InternalError::class)
    fun methodHook_beforeCallback_rethrowsVirtualMachineError() {
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                throw InternalError("boom")
            }
        }
        hook.beforeHook(HookerClassHelper.BeforeHookCallback(emptyChain()))
    }

    @Test(expected = InternalError::class)
    fun methodHook_afterCallback_rethrowsVirtualMachineError() {
        val before = HookerClassHelper.BeforeHookCallback(emptyChain())
        val hook = object : HookerClassHelper.MethodHook() {
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                throw InternalError("boom")
            }
        }
        hook.afterHook(HookerClassHelper.AfterHookCallback(before, null, null))
    }

    @Test(expected = OutOfMemoryError::class)
    fun throwableLogger_rethrowsOutOfMemoryError() {
        XposedHelpers.log(OutOfMemoryError("boom"))
    }

    @Test(expected = OutOfMemoryError::class)
    fun namedThrowableLogger_rethrowsOutOfMemoryError() {
        XposedHelpers.log("test", OutOfMemoryError("boom"))
    }

    @Test(expected = ThreadDeath::class)
    fun throwableLogger_rethrowsThreadDeath() {
        XposedHelpers.log(ThreadDeath())
    }

    @Test(expected = ThreadDeath::class)
    fun namedThrowableLogger_rethrowsThreadDeath() {
        XposedHelpers.log("test", ThreadDeath())
    }

    @Test(expected = InternalError::class)
    fun throwableLogger_rethrowsVirtualMachineError() {
        XposedHelpers.log(InternalError("boom"))
    }

    @Test(expected = InternalError::class)
    fun namedThrowableLogger_rethrowsVirtualMachineError() {
        XposedHelpers.log("test", InternalError("boom"))
    }

    @Test
    fun findClassIfExists_propagatesVirtualMachineErrorFromApplicationClassLoader() {
        val applicationClassLoaderField = XposedHelpers::class.java.getDeclaredField("applicationClassLoader")
            .apply { isAccessible = true }
        applicationClassLoaderField.set(null, null)

        val loader = VmThrowingClassLoader()
        try {
            XposedHelpers.findClassIfExists("nonexistent.target.Class", loader)
            fail("InternalError from getApplicationClassLoader must propagate")
        } catch (e: InternalError) {
            // expected
        }
    }

    @Test
    fun findClassIfExists_propagatesThreadDeathFromApplicationClassLoader() {
        val applicationClassLoaderField = XposedHelpers::class.java.getDeclaredField("applicationClassLoader")
            .apply { isAccessible = true }
        applicationClassLoaderField.set(null, null)

        val loader = object : ClassLoader(FatalBoundaryTest::class.java.classLoader) {
            override fun loadClass(name: String?, resolve: Boolean): Class<*> {
                if (name == "android.app.ActivityThread") throw ThreadDeath()
                if (name == "nonexistent.target.Class") throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        try {
            XposedHelpers.findClassIfExists("nonexistent.target.Class", loader)
            fail("ThreadDeath from getApplicationClassLoader must propagate")
        } catch (e: ThreadDeath) {
            // expected
        }
    }

    @Test
    fun featureInstallRegistry_installOom_leavesFailedTransientAndRethrows() {
        val registry = FeatureInstallRegistry()
        val feature = object : FeatureDefinition {
            override val id = object : FeatureId {
                override val id = 777
                override val name = "oomFeature"
            }
            override val name = "oomFeature"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult {
                throw OutOfMemoryError("simulated install OOM")
            }
        }

        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("OutOfMemoryError must propagate")
        } catch (oom: OutOfMemoryError) {
            assertEquals(FeatureState.FAILED_TRANSIENT, FeatureInstallState.get(feature.id))
        }
    }

    @Test
    fun featureInstallRegistry_installVmError_leavesFailedTransientAndRethrows() {
        val registry = FeatureInstallRegistry()
        val feature = object : FeatureDefinition {
            override val id = object : FeatureId {
                override val id = 778
                override val name = "vmErrorFeature"
            }
            override val name = "vmErrorFeature"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult {
                throw InternalError("simulated VM error")
            }
        }

        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("InternalError must propagate")
        } catch (e: InternalError) {
            assertEquals(FeatureState.FAILED_TRANSIENT, FeatureInstallState.get(feature.id))
        }
    }

    @Test
    fun reflectionCache_classLookupOom_doesNotCacheClassMissing() {
        val cache = ReflectionCache()
        val loader = OomClassLoader()

        try {
            cache.getDepInstance(loader, "boom.Oom")
            fail("OutOfMemoryError must propagate")
        } catch (oom: OutOfMemoryError) {
            val state = cache.loaderStates[loader]
            assertNull("OOM must not be cached as ClassMissing", state?.classResults?.get("boom.Oom"))
        }
    }

    @Test
    fun reflectionCache_invocationTargetOom_unwrapsAndRethrows() {
        val cache = ReflectionCache(FakeDependencyMethodResolver())
        FakeDependency.throwOom = true
        val loader = FatalBoundaryTest::class.java.classLoader

        try {
            cache.getDepInstance(loader, "java.lang.String")
            fail("OutOfMemoryError must propagate")
        } catch (oom: OutOfMemoryError) {
            val state = cache.loaderStates[loader]
            assertFalse(
                "OOM must not be cached as DependencyNotReady",
                state?.classResults?.get("java.lang.String") is DepResult.DependencyNotReady
            )
        }
    }

    private class FakeDependencyMethodResolver : DependencyMethodResolver {
        override fun resolve(classLoader: ClassLoader?): Method? {
            val depClass = try {
                XposedHelpers.findClassIfExists(FakeDependency::class.java.name, classLoader)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                null
            }
            return depClass?.getDeclaredMethod("get", Class::class.java)?.apply { isAccessible = true }
        }
    }
}
