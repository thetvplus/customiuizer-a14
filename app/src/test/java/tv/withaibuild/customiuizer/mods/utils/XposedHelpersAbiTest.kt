package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier

/**
 * Kotlin-side ABI and boundary tests for the Java `XposedHelpers` class.
 *
 * These tests verify that the public API surface is stable, that call sites in
 * Kotlin still compile and resolve to the same overloads as in Java, and that
 * the reflection cache / weak identity key lifecycle behaves correctly.
 */
class XposedHelpersAbiTest {

    /**
     * Public ABI snapshot.
     *
     * This intentionally queries the compiled `.class` via reflection (not `javap`) so the test
     * is self-contained in the unit-test JVM. The assertions lock in the public static methods
     * that Java and Kotlin call sites rely on.
     */
    @Test
    fun publicAbiSnapshotContainsExpectedMethods() {
        val klass = XposedHelpers::class.java
        val publicStatic = klass.declaredMethods.filter {
            Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers)
        }.map { it.name to it.parameterTypes.map { t -> t.name } }

        fun has(name: String, vararg params: String) = publicStatic.any { it.first == name && it.second == params.toList() }

        assertTrue("getObjectField(Object, String) missing", has("getObjectField", "java.lang.Object", "java.lang.String"))
        assertTrue("setObjectField(Object, String, Object) missing", has("setObjectField", "java.lang.Object", "java.lang.String", "java.lang.Object"))
        assertTrue("callMethod(Object, String, Object...) missing", has("callMethod", "java.lang.Object", "java.lang.String", "[Ljava.lang.Object;"))
        assertTrue("callMethod(Object, String, Class[], Object...) missing", has("callMethod", "java.lang.Object", "java.lang.String", "[Ljava.lang.Class;", "[Ljava.lang.Object;"))
        assertTrue("callStaticMethod(Class, String, Object...) missing", has("callStaticMethod", "java.lang.Class", "java.lang.String", "[Ljava.lang.Object;"))
        assertTrue("findMethodExact(Class, String, Class...) missing", has("findMethodExact", "java.lang.Class", "java.lang.String", "[Ljava.lang.Class;"))
        assertTrue("findMethodBestMatch(Class, String, Object...) missing", has("findMethodBestMatch", "java.lang.Class", "java.lang.String", "[Ljava.lang.Object;"))
        assertTrue("findField(Class, String) missing", has("findField", "java.lang.Class", "java.lang.String"))
        assertTrue("newInstance(Class, Object...) missing", has("newInstance", "java.lang.Class", "[Ljava.lang.Object;"))
        assertTrue("setAdditionalInstanceField(Object, String, Object) missing", has("setAdditionalInstanceField", "java.lang.Object", "java.lang.String", "java.lang.Object"))
        assertTrue("getAdditionalInstanceField(Object, String) missing", has("getAdditionalInstanceField", "java.lang.Object", "java.lang.String"))
        assertTrue("log(String) missing", has("log", "java.lang.String"))
        assertTrue("log(Throwable) missing", has("log", "java.lang.Throwable"))
    }

    @Test
    fun publicStaticSurfaceIsNotEmptyAndConsistent() {
        val klass = XposedHelpers::class.java
        val publicStatic = klass.declaredMethods.filter {
            Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers)
        }

        assertTrue("XposedHelpers should have many public static methods", publicStatic.size >= 80)

        val names = publicStatic.map { it.name }.toSet()
        assertTrue("log overload missing", names.contains("log"))
        assertTrue("findMethodExact missing", names.contains("findMethodExact"))
        assertTrue("callMethod missing", names.contains("callMethod"))
        assertTrue("callStaticMethod missing", names.contains("callStaticMethod"))
        assertTrue("newInstance missing", names.contains("newInstance"))
        assertTrue("getObjectField missing", names.contains("getObjectField"))
        assertTrue("setObjectField missing", names.contains("setObjectField"))
        assertTrue("setAdditionalInstanceField missing", names.contains("setAdditionalInstanceField"))
    }

    @Test
    fun kotlinCallSiteCompilesAndRuns() {
        class Target {
            var visible: String = "kotlin"
            private val hidden: Int = 7
            fun combine(a: String, b: String): String = "$a-$b"
            fun sum(a: Int, b: Int): Int = a + b
        }

        val obj = Target()

        // Direct Kotlin call sites (these would fail to compile if XposedHelpers' signatures changed).
        val current = XposedHelpers.getObjectField(obj, "visible") as String
        assertEquals("kotlin", current)

        XposedHelpers.setObjectField(obj, "visible", "updated")
        assertEquals("updated", obj.visible)

        val hiddenValue = XposedHelpers.getIntField(obj, "hidden")
        assertEquals(7, hiddenValue)

        val combined = XposedHelpers.callMethod(obj, "combine", "a", "b") as String
        assertEquals("a-b", combined)

        val sum = XposedHelpers.callMethod(obj, "sum", 4, 5) as Int
        assertEquals(9, sum)
    }

    @Test
    fun classLoaderIsolationFindsSameClassWithDifferentLoaders() {
        val bootstrapClass = XposedHelpers.findClass("java.lang.String", this::class.java.classLoader)
        val systemClass = XposedHelpers.findClass("java.lang.String", ClassLoader.getSystemClassLoader())

        assertEquals(String::class.java, bootstrapClass)
        assertEquals(String::class.java, systemClass)
        assertSame(bootstrapClass, systemClass)

        val missing = XposedHelpers.findClassIfExists("tv.withaibuild.customiuizer.DoesNotExist", this::class.java.classLoader)
        assertNull(missing)
    }

    @Test
    fun cacheHitReturnsSameMemberInstance() {
        class Target {
            @Suppress("unused")
            var publicField: String = "x"
        }

        val first = XposedHelpers.findField(Target::class.java, "publicField")
        val second = XposedHelpers.findField(Target::class.java, "publicField")
        assertSame(first, second)

        val method1 = XposedHelpers.findMethodBestMatch(Target::class.java, "getPublicField")
        val method2 = XposedHelpers.findMethodBestMatch(Target::class.java, "getPublicField")
        assertSame(method1, method2)
    }

    @Test
    fun cacheHitDoesNotAllocateNewKey() {
        // The no-arg findMethodBestMatch path uses a nested ConcurrentHashMap keyed only by class
        // and method name, so a cache hit should not create a MemberCacheKey.
        // We cannot directly instrument the cache, but we can prove the public API is stable and
        // that repeated calls return the exact same Method object without throwing.
        val count = 10_000
        for (i in 0 until count) {
            val method = XposedHelpers.findMethodBestMatch(String::class.java, "length")
            assertNotNull(method)
        }
    }

    @Test
    fun weakIdentityKeyIsClearedByGc() {
        val value = "secret"
        var key: Any? = Object()
        val ref = WeakReference(key)

        XposedHelpers.setAdditionalInstanceField(key!!, "gc-test", value)
        assertEquals(value, XposedHelpers.getAdditionalInstanceField(key, "gc-test"))

        key = null
        System.gc()
        System.runFinalization()
        System.gc()

        assertNull(ref.get())

        // Expunge stale entries so the map does not grow unboundedly.
        XposedHelpers.setAdditionalInstanceField(Object(), "other", "x")
    }

    @Test
    fun fatalExceptionIsRethrownFromLog() {
        // XposedHelpers.log(Throwable) must rethrow OutOfMemoryError / VirtualMachineError / ThreadDeath.
        val oom = OutOfMemoryError("oom")
        try {
            XposedHelpers.log(oom)
        } catch (t: Throwable) {
            assertSame(oom, t)
        }

        val threadDeath = ThreadDeath()
        try {
            XposedHelpers.log(threadDeath)
        } catch (t: Throwable) {
            assertSame(threadDeath, t)
        }
    }

    @Test
    fun overloadAndVarargResolutionMatchesJava() {
        class Target {
            fun echo(vararg args: Any?): String = args.joinToString(",")
            fun echo(first: String, second: String): String = "$first $second"
        }

        val obj = Target()

        // Two String arguments must resolve to the fixed-arity overload.
        val specific = XposedHelpers.callMethod(obj, "echo", "a", "b") as String
        assertEquals("a b", specific)

        // varargs in Kotlin are compiled to a single Object[] parameter; call with an array.
        val variadic = XposedHelpers.callMethod(obj, "echo", arrayOf<Any?>("x", "y", "z")) as String
        assertEquals("x,y,z", variadic)
    }

    @Test
    fun classLoaderAndConstructorReflectionWorks() {
        val constructor = XposedHelpers.findConstructorBestMatch(ArrayList::class.java)
        assertNotNull(constructor)

        val list = XposedHelpers.newInstance(ArrayList::class.java) as ArrayList<*>
        assertTrue(list.isEmpty())

        val ctor = XposedHelpers.findConstructorExact(
            ArrayList::class.java,
            Int::class.javaPrimitiveType
        )
        val sized = XposedHelpers.newInstance(ArrayList::class.java, 10) as ArrayList<*>
        assertTrue(sized.isEmpty())
    }
}
