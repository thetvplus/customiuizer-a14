package tv.withaibuild.customiuizer.mods

import java.io.File
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DockSuggestionCallerScopeTest {

    private class DockActivityFixture {
        class MatchingWorker(owner: DockActivityFixture) : Runnable {
            @JvmField
            val owner = WeakReference(owner)

            @JvmField
            val loading = false

            override fun run() = Unit
        }

        class WrongWorker(@JvmField val owner: DockActivityFixture) : Runnable {
            override fun run() = Unit
        }
    }

    @Test
    fun resolvesVerifiedDockWorkerShapeWithoutUsingItsObfuscatedInnerName() {
        val method = resolveDockSuggestionRomCallerMethod(DockActivityFixture::class.java)

        assertEquals(DockActivityFixture.MatchingWorker::class.java, method.declaringClass)
        assertEquals("run", method.name)
        assertEquals(Void.TYPE, method.returnType)
        assertTrue(method.parameterTypes.isEmpty())
    }

    @Test
    fun nonWhitelistedCallerGetsTheExistingMutablePlaceholderWithoutProceeding() {
        var proceedCalls = 0

        val result = dockSuggestionResult(false) {
            proceedCalls++
            listOf("rom.package")
        }

        assertEquals(0, proceedCalls)
        assertTrue(result is ArrayList<*>)
        @Suppress("UNCHECKED_CAST")
        val list = result as ArrayList<String>
        assertEquals(listOf("xx.yy.zz"), list)
        list.add("still.mutable")
        assertEquals(2, list.size)
    }

    @Test
    fun whitelistedCallerGetsTheExactRomResultOnce() {
        var proceedCalls = 0
        val romResult = arrayListOf("rom.package")

        val result = dockSuggestionResult(true) {
            proceedCalls++
            romResult
        }

        assertEquals(1, proceedCalls)
        assertSame(romResult, result)
    }

    @Test
    fun romExceptionIsPropagatedAndCallerScopeIsCleared() {
        val scope = DockSuggestionCallerScope()
        val expected = IllegalStateException("ROM failure")

        try {
            scope.call {
                dockSuggestionResult(scope.isActive()) { throw expected }
            }
            fail("Expected the original ROM exception")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }

        assertFalse(scope.isActive())
    }

    @Test
    fun nestedCallerScopeRemainsActiveUntilTheOuterCallExits() {
        val scope = DockSuggestionCallerScope()

        scope.call {
            assertTrue(scope.isActive())
            scope.call { assertTrue(scope.isActive()) }
            assertTrue(scope.isActive())
        }

        assertFalse(scope.isActive())
    }

    @Test
    fun dockSuggestionHotPathNoLongerScansTheThreadStack() {
        val various = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/Various.kt"
        )
        val scopeSource = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/DockSuggestionCallerScope.kt"
        )
        val sectionStart = various.indexOf("fun DisableDockSuggestHook(")
        val sectionEnd = various.indexOf("fun AlarmCompatHook(", sectionStart)
        val section = various.substring(sectionStart, sectionEnd)

        assertFalse(section.contains("Thread.currentThread().stackTrace"))
        assertTrue(section.contains("dockSuggestionCallerScope.isActive()"))
        assertTrue(scopeSource.contains("finally {"))
        assertTrue(scopeSource.contains("chain.proceed()"))
    }

    private fun <T> DockSuggestionCallerScope.call(block: () -> T): T {
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
