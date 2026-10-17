package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LauncherForceFsgNavBarScopeTest {

    private class CallerFixture {
        fun updateFsgWindowState() = Unit

        fun updateFsgWindowState(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

        fun `lambda$showBackStubWindow$42$BaseRecentsImpl`(
            @Suppress("UNUSED_PARAMETER") enabled: Boolean
        ) = Unit

        fun `lambda$showBackStubWindow$43$BaseRecentsImpl`(enabled: Boolean): Boolean = enabled

        fun `lambda$updateFsgWindowVisibilityState$7$BaseRecentsImpl`(
            @Suppress("UNUSED_PARAMETER") entering: Boolean,
            @Suppress("UNUSED_PARAMETER") packageName: String
        ) = Unit

        fun `lambda$updateFsgWindowVisibilityState$8$BaseRecentsImpl`(
            @Suppress("UNUSED_PARAMETER") packageName: String,
            @Suppress("UNUSED_PARAMETER") entering: Boolean
        ) = Unit

        fun unrelated() = Unit
    }

    @Test
    fun resolvesOnlyTheThreeVerifiedDirectCallerShapes() {
        val methods = resolveForceFsgNavBarCallerMethods(CallerFixture::class.java)

        assertEquals(
            setOf(
                "updateFsgWindowState",
                "lambda\$showBackStubWindow\$42\$BaseRecentsImpl",
                "lambda\$updateFsgWindowVisibilityState\$7\$BaseRecentsImpl"
            ),
            methods.mapTo(mutableSetOf()) { it.name }
        )
    }

    @Test
    fun scopeIsActiveOnlyInsideTheWrappedCall() {
        val scope = ForceFsgNavBarCallerScope()

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
        val scope = ForceFsgNavBarCallerScope()

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
        val scope = ForceFsgNavBarCallerScope()
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
    fun launcherHotPathNoLongerScansTheThreadStack() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt")
        val scopeSource = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/ForceFsgNavBarCallerScope.kt"
        )
        val hookStart = source.indexOf("fun FSGesturesHook(")
        val hookEnd = source.indexOf("fun LauncherDoubleTapHook(", hookStart)
        val hook = source.substring(hookStart, hookEnd)

        assertFalse(hook.contains("Thread.currentThread().stackTrace"))
        assertTrue(hook.contains("forceFsgNavBarCallerScope.isActive()"))
        assertTrue(scopeSource.contains("finally {"))
        assertTrue(scopeSource.contains("forceFsgNavBarCallerScope.exit()"))
    }

    private fun <T> ForceFsgNavBarCallerScope.call(block: () -> T): T {
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
