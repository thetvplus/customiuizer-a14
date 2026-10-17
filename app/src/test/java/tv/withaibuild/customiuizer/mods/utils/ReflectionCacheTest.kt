package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * A fake [com.android.systemui.Dependency] stand-in used to exercise the four
 * ReflectionCache states.
 *
 * It is package-private and only referenced by name so that the cache under test
 * has to locate it, resolve its `get(Class)` method, and invoke it just like it
 * would with the real SystemUI class.
 */
private class FakeDependency {
    companion object {
        @JvmField
        var ready: Boolean = false

        @JvmField
        var throwOnGet: Throwable? = null

        @JvmStatic
        fun get(clazz: Class<*>): Any? {
            throwOnGet?.let { throw it }
            return if (ready) INSTANCE else null
        }

        val INSTANCE = Any()
    }
}

/**
 * Resolves a named fake dependency class. The resolver is under test control:
 * [className] can point to the fake or a missing class, and [throwOnResolve] can
 * inject failures before the method is returned.
 */
private class FakeDependencyMethodResolver : DependencyMethodResolver {
    var className: String = FakeDependency::class.java.name
    var throwOnResolve: Throwable? = null

    override fun resolve(classLoader: ClassLoader?): Method? {
        throwOnResolve?.let { throw it }
        val depClass = try {
            XposedHelpers.findClassIfExists(className, classLoader)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            null
        }
        return depClass?.getDeclaredMethod("get", Class::class.java)?.apply { isAccessible = true }
    }
}

/**
 * Tests for [ReflectionCache].
 *
 * The cache must distinguish CLASS_MISSING, METHOD_MISSING, DEPENDENCY_NOT_READY
 * and DEPENDENCY_FOUND; it must cache negatives per ClassLoader, allow retry after
 * a safe lifecycle, and stay bounded.
 */
class ReflectionCacheTest {

    private lateinit var cache: ReflectionCache
    private lateinit var resolver: FakeDependencyMethodResolver

    @Before
    fun setUp() {
        resolver = FakeDependencyMethodResolver()
        cache = ReflectionCache(resolver)
    }

    @After
    fun tearDown() {
        FakeDependency.ready = false
        FakeDependency.throwOnGet = null
        resolver.throwOnResolve = null
        resolver.className = FakeDependency::class.java.name
    }

    @Test
    fun getDepInstance_missingClass_cachesClassMissing() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        val className = "does.not.exist.Class"

        assertNull(cache.getDepInstance(loader, className))
        assertNull(cache.getDepInstance(loader, className))

        val state = cache.loaderStates[loader]
        assertNotNull(state)
        assertTrue(
            "missing class must be cached as ClassMissing",
            state!!.classResults[className] is DepResult.ClassMissing
        )
    }

    @Test
    fun getDepInstance_missingMethod_cachesMethodMissingPerLoader() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = "does.not.exist.Dependency"

        assertNull(cache.getDepInstance(loader, "java.lang.String"))
        assertNull(cache.getDepInstance(loader, "java.lang.Runnable"))

        val state = cache.loaderStates[loader]
        assertNotNull(state)
        assertTrue(
            "first missing dependency method must be cached as MethodMissing",
            state!!.classResults["java.lang.String"] is DepResult.MethodMissing
        )
        assertTrue(
            "second class must also be cached as MethodMissing",
            state.classResults["java.lang.Runnable"] is DepResult.MethodMissing
        )
    }

    @Test
    fun getDepInstance_dependencyNotReady_retriesAfterSafeLifecycle() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = FakeDependency::class.java.name
        val className = "java.lang.String"

        FakeDependency.ready = false

        // Not ready is cached within the current lifecycle.
        assertNull(cache.getDepInstance(loader, className))
        assertNull(cache.getDepInstance(loader, className))

        val state = cache.loaderStates[loader]
        assertTrue(
            "null Dependency.get result must be cached as DependencyNotReady",
            state!!.classResults[className] is DepResult.DependencyNotReady
        )

        // Make the dependency ready, but without a lifecycle signal the cached
        // negative result must still be returned.
        FakeDependency.ready = true
        assertNull(cache.getDepInstance(loader, className))

        // A safe lifecycle allows retry.
        cache.onSafeLifecycle(loader)
        val found = cache.getDepInstance(loader, className)
        assertSame(FakeDependency.INSTANCE, found)

        // Found results are stable and returned without reflection on the hot path.
        assertSame(found, cache.getDepInstance(loader, className))
    }

    @Test
    fun getDepInstance_dependencyFound_idempotent() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = FakeDependency::class.java.name
        FakeDependency.ready = true

        val r1 = cache.getDepInstance(loader, "java.lang.Runnable")
        val r2 = cache.getDepInstance(loader, "java.lang.Runnable")
        val r3 = cache.getDepInstance(loader, "java.lang.Runnable")

        assertNotNull(r1)
        assertSame(r1, r2)
        assertSame(r2, r3)

        val state = cache.loaderStates[loader]
        assertTrue(
            "positive result must be cached as DependencyFound",
            state!!.classResults["java.lang.Runnable"] is DepResult.DependencyFound
        )
    }

    @Test
    fun getDepInstance_isolatedPerClassLoader() {
        val loader1 = object : ClassLoader(javaClass.classLoader) {}
        val loader2 = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = FakeDependency::class.java.name
        FakeDependency.ready = true

        val r1 = cache.getDepInstance(loader1, "java.lang.Runnable")
        val r2 = cache.getDepInstance(loader2, "java.lang.Runnable")

        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals(r1, cache.getDepInstance(loader1, "java.lang.Runnable"))
        assertEquals(r2, cache.getDepInstance(loader2, "java.lang.Runnable"))
    }

    @Test
    fun loaderCache_isBounded() {
        resolver.className = "does.not.exist.Dependency"

        for (i in 0 until 100) {
            val loader = object : ClassLoader(javaClass.classLoader) {}
            cache.getDepInstance(loader, "java.lang.Runnable")
        }

        val loaders = cache.loaderStates.size
        assertTrue(
            "global loader cache must stay within ${ReflectionCache.MAX_LOADERS}, was $loaders",
            loaders <= ReflectionCache.MAX_LOADERS
        )
    }

    @Test(expected = OutOfMemoryError::class)
    fun getDepInstance_dependencyMethodOom_doesNotPolluteState() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = FakeDependency::class.java.name
        resolver.throwOnResolve = OutOfMemoryError("OOM in getDeclaredMethod")

        try {
            cache.getDepInstance(loader, "java.lang.String")
        } finally {
            val state = cache.loaderStates[loader]
            assertNotNull(state)
            assertFalse(
                "dependencyMethodResolved must not be set after OOM",
                state!!.dependencyMethodResolved
            )
            assertNull(
                "dependencyMethod must not be set after OOM",
                state.dependencyMethod
            )
        }
    }

    @Test
    fun getDepInstance_dependencyThrowsInternalError_propagatesAndDoesNotCacheNotReady() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = FakeDependency::class.java.name
        FakeDependency.throwOnGet = InternalError("dep vm error")

        try {
            cache.getDepInstance(loader, "java.lang.String")
            assertTrue("wrapped InternalError from Dependency.get must propagate", false)
        } catch (e: InternalError) {
            // Expected: the wrapped VM error must not be converted to DependencyNotReady.
        }

        val state = cache.loaderStates[loader]
        assertNotNull(state)
        assertFalse(
            "wrapped InternalError must not be cached as DependencyNotReady",
            state!!.classResults["java.lang.String"] is DepResult.DependencyNotReady
        )
    }

    @Test
    fun getDepInstance_dependencyThrowsThreadDeath_propagatesAndDoesNotCacheNotReady() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = FakeDependency::class.java.name
        FakeDependency.throwOnGet = ThreadDeath()

        try {
            cache.getDepInstance(loader, "java.lang.String")
            assertTrue("wrapped ThreadDeath from Dependency.get must propagate", false)
        } catch (e: ThreadDeath) {
            // Expected: the wrapped thread error must not be converted to DependencyNotReady.
        }

        val state = cache.loaderStates[loader]
        assertNotNull(state)
        assertFalse(
            "wrapped ThreadDeath must not be cached as DependencyNotReady",
            state!!.classResults["java.lang.String"] is DepResult.DependencyNotReady
        )
    }

    @Test
    fun getDepInstance_dependencyThrowsRuntimeException_degradesToNotReady() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = FakeDependency::class.java.name
        FakeDependency.throwOnGet = RuntimeException("transient dep failure")

        assertNull(cache.getDepInstance(loader, "java.lang.String"))

        val state = cache.loaderStates[loader]
        assertNotNull(state)
        assertTrue(
            "ordinary RuntimeException from Dependency.get must remain isolated as DependencyNotReady",
            state!!.classResults["java.lang.String"] is DepResult.DependencyNotReady
        )
    }

    @Test
    fun classResultCache_isBounded() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        resolver.className = "does.not.exist.Dependency"

        for (i in 0 until 100) {
            cache.getDepInstance(loader, "does.not.exist.Class$i")
        }

        val state = cache.loaderStates[loader]
        assertNotNull(state)
        assertTrue(
            "per-loader class cache must stay within ${ReflectionCache.MAX_CLASSES_PER_LOADER}, was ${state!!.classResults.size}",
            state.classResults.size <= ReflectionCache.MAX_CLASSES_PER_LOADER
        )
    }
}
