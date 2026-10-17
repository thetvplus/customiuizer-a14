package tv.withaibuild.customiuizer.mods

import java.io.File
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LauncherUnlockGridsScopeTest {

    @Suppress("unused")
    private class UtilitiesFixture {
        companion object {
            @JvmStatic
            fun isNoWordModel(): Boolean = true

            @JvmStatic
            fun isNoWordModel(@Suppress("UNUSED_PARAMETER") fallback: Boolean): Boolean = fallback

            @JvmStatic
            fun unrelated(): Boolean = false
        }

        fun isNoWordModel(@Suppress("UNUSED_PARAMETER") ignored: Int): Boolean = false
    }

    private class NoStaticFixture {
        @Suppress("unused")
        fun isNoWordModel(): Boolean = true
    }

    @Test
    fun resolvesOnlyTheStaticNoArgumentBooleanShape() {
        val method = resolveNoWordModelMethod(UtilitiesFixture::class.java)

        assertEquals("isNoWordModel", method.name)
        assertEquals(0, method.parameterCount)
        assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
    }

    @Test
    fun rejectsRomsWithoutTheStaticShape() {
        try {
            resolveNoWordModelMethod(NoStaticFixture::class.java)
            fail("Expected an ABI check failure")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("isNoWordModel"))
        }
    }

    @Test
    fun scopeIsActiveOnlyInsideTheWrappedCall() {
        val scope = UnlockGridsNoWordScope()

        assertFalse(scope.isActive())
        val value = scope.call {
            assertTrue(scope.isActive())
            "result"
        }

        assertEquals("result", value)
        assertFalse(scope.isActive())
    }

    @Test
    fun nestedCallsKeepTheOuterScopeActive() {
        val scope = UnlockGridsNoWordScope()

        scope.call {
            assertTrue(scope.isActive())
            scope.call {
                assertTrue(scope.isActive())
            }
            assertTrue(scope.isActive())
        }

        assertFalse(scope.isActive())
    }

    @Test
    fun exceptionIsPropagatedAndScopeIsCleared() {
        val scope = UnlockGridsNoWordScope()
        val expected = IllegalStateException("ROM failure")

        try {
            scope.call<Unit> { throw expected }
            fail("Expected the wrapped exception")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }

        assertFalse(scope.isActive())
    }

    @Test
    fun scopeIsNotVisibleToOtherThreads() {
        val scope = UnlockGridsNoWordScope()
        val entered = CountDownLatch(1)
        val checked = CountDownLatch(1)
        var otherThreadSawScope = true

        val other = Thread {
            entered.await()
            otherThreadSawScope = scope.isActive()
            checked.countDown()
        }
        other.start()

        scope.call {
            entered.countDown()
            checked.await()
        }
        other.join()

        assertFalse("The caller scope must not leak to other threads", otherThreadSawScope)
    }

    @Test
    fun unlockGridsNoLongerInstallsHooksFromInsideAHook() {
        val hooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt")
        val start = hooks.indexOf("fun UnlockGridsHook(")
        val end = hooks.indexOf("fun HorizontalSpacingRes(", start)
        val hook = hooks.substring(start, end)

        assertFalse("No hook may be installed from inside another hook", hook.contains("nowordHook"))
        assertFalse(hook.contains("unhook()"))
        assertTrue(hook.contains("unlockGridsNoWordScope.enter()"))
        assertTrue(hook.contains("unlockGridsNoWordScope.exit()"))

        val scopeSource = source("app/src/main/java/tv/withaibuild/customiuizer/mods/UnlockGridsNoWordScope.kt")
        assertTrue(scopeSource.contains("finally {") || hook.contains("finally {"))
    }

    private fun <T> UnlockGridsNoWordScope.call(block: () -> T): T {
        enter()
        return try {
            block()
        } finally {
            exit()
        }
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
