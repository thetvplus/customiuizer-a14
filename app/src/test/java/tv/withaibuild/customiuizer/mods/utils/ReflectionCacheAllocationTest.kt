package tv.withaibuild.customiuizer.mods.utils

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Pins the property that makes the reflection cache usable on a hot path: a hit must not
 * allocate.
 *
 * Hook bodies in SystemUI and Launcher perform hundreds of field reads per invocation, at draw
 * and scroll frequency. When the lookup key was built per call, the cache itself was the source
 * of the steady-state garbage, so a "cached" access still cost an object.
 *
 * The allocation measurement uses the HotSpot thread allocation counter and is skipped where it
 * is unavailable; the behavioural assertions below run everywhere.
 */
class ReflectionCacheAllocationTest {

    open class Base {
        @JvmField
        var inheritedField: Int = 7

        fun inheritedNoArgMethod(): String = "base"
    }

    class Derived : Base() {
        @JvmField
        var ownField: String = "own"
    }

    private fun allocatedBytes(): Long? {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean ?: return null
        if (!bean.isThreadAllocatedMemorySupported) return null
        bean.isThreadAllocatedMemoryEnabled = true
        return bean.getThreadAllocatedBytes(Thread.currentThread().id)
    }

    private fun measureAllocation(iterations: Int, block: () -> Unit): Long? {
        repeat(2000) { block() } // warm the cache and let the JIT settle
        val before = allocatedBytes() ?: return null
        repeat(iterations) { block() }
        val after = allocatedBytes() ?: return null
        return after - before
    }

    /**
     * Control for the two assertions below.
     *
     * Without this, a counter that always reported zero — unsupported, disabled, or optimised
     * away — would make them pass no matter what the cache did.
     */
    @Test
    fun theAllocationCounterObservesAllocation() {
        val sink = arrayOfNulls<Any>(1)
        val iterations = 200_000
        val allocated = measureAllocation(iterations) { sink[0] = Any() }
        assumeTrue("thread allocation counter unavailable", allocated != null)

        assertTrue(
            "counter reported ${allocated} bytes for $iterations allocations; it is not measuring",
            allocated!! >= iterations
        )
    }

    @Test
    fun cachedFieldLookupDoesNotAllocate() {
        val clazz = Derived::class.java
        val iterations = 200_000
        val allocated = measureAllocation(iterations) { XposedHelpers.findField(clazz, "ownField") }
        assumeTrue("thread allocation counter unavailable", allocated != null)

        val perCall = allocated!!.toDouble() / iterations
        assertTrue(
            "cached findField allocated ~$perCall bytes per call; the cache key is back",
            perCall < 1.0
        )
    }

    @Test
    fun cachedNoArgMethodLookupDoesNotAllocate() {
        val clazz = Derived::class.java
        val iterations = 200_000
        // Inherited, so the general path would build one key for the failed exact lookup and a
        // second for the best-match lookup.
        val allocated = measureAllocation(iterations) {
            XposedHelpers.findMethodBestMatch(clazz, "inheritedNoArgMethod")
        }
        assumeTrue("thread allocation counter unavailable", allocated != null)

        val perCall = allocated!!.toDouble() / iterations
        assertTrue(
            "cached no-arg findMethodBestMatch allocated ~$perCall bytes per call",
            perCall < 1.0
        )
    }

    @Test
    fun fieldsAreResolvedThroughTheClassHierarchyAndCachedPerClass() {
        val own = XposedHelpers.findField(Derived::class.java, "ownField")
        val inherited = XposedHelpers.findField(Derived::class.java, "inheritedField")

        assertSame(own, XposedHelpers.findField(Derived::class.java, "ownField"))
        assertSame(inherited, XposedHelpers.findField(Derived::class.java, "inheritedField"))
        assertEquals(Base::class.java, inherited.declaringClass)

        // The same name on a different class must not collide across the nested maps.
        assertSame(
            XposedHelpers.findField(Base::class.java, "inheritedField"),
            XposedHelpers.findField(Base::class.java, "inheritedField")
        )
    }

    @Test
    fun missingFieldStaysNegativeAndKeepsItsErrorText() {
        val expected = "${Derived::class.java.name}#noSuchField"
        repeat(2) {
            val error = runCatching { XposedHelpers.findField(Derived::class.java, "noSuchField") }
                .exceptionOrNull()
            assertTrue("expected NoSuchFieldError, got $error", error is NoSuchFieldError)
            assertEquals(expected, error!!.message)
        }
    }

    @Test
    fun noArgLookupResolvesInheritedMethodsAndCachesNegatives() {
        val method = XposedHelpers.findMethodBestMatch(Derived::class.java, "inheritedNoArgMethod")
        assertSame(method, XposedHelpers.findMethodBestMatch(Derived::class.java, "inheritedNoArgMethod"))
        assertEquals("base", method.invoke(Derived()))

        repeat(2) {
            val error = runCatching {
                XposedHelpers.findMethodBestMatch(Derived::class.java, "noSuchMethod")
            }.exceptionOrNull()
            assertTrue("expected NoSuchMethodError, got $error", error is NoSuchMethodError)
        }
    }
}
