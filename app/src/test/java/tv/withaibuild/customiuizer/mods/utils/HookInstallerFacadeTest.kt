package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HookInstallerFacadeTest {

    @Test
    fun facadeUsesFatalBoundaryBeforeNonfatalFailureHandling() {
        val source = sourceFile("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt").readText()

        assertEquals("No OutOfMemoryError-only catch blocks should exist", 0, countPattern(source, "catch\\s*\\(oom:\\s*OutOfMemoryError\\s*\\)"))
        assertEquals("Four Throwable catch blocks should exist", 4, countPattern(source, "catch\\s*\\(t:\\s*Throwable\\s*\\)"))
        assertEquals(
            "FatalErrors.unwrapAndRethrowIfFatal(t) should be called in each Throwable catch",
            4,
            countPattern(source, "FatalErrors\\.unwrapAndRethrowIfFatal\\(t\\)")
        )
    }

    @Test
    fun findAndHookMethodStringOverloadPreservesNonfatalFailureSemantics() {
        val source = sourceFile("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt").readText()

        val fnStart = source.indexOf(
            "fun findAndHookMethod(className: String, classLoader: ClassLoader?, methodName: String"
        )
        assertTrue("findAndHookMethod(String,...) must exist", fnStart >= 0)

        val fnEnd = source.indexOf(
            "fun findAndHookMethod(clazz: Class<*>, methodName: String",
            fnStart
        )
        val fnBody = source.substring(fnStart, if (fnEnd >= 0) fnEnd else source.length)

        val catchBody = extractThrowableCatch(fnBody)

        assertTrue(
            "String overload must preserve TARGET_CLASS_MISSING",
            fnBody.contains("HookDiagnostics.Status.TARGET_CLASS_MISSING")
        )
        assertTrue(
            "String overload must preserve TARGET_MEMBER_MISSING",
            fnBody.contains("HookDiagnostics.Status.TARGET_MEMBER_MISSING")
        )
        assertTrue(
            "String overload must preserve INSTALL_FAILED",
            fnBody.contains("HookDiagnostics.Status.INSTALL_FAILED")
        )
        assertTrue(
            "String overload must preserve unhooker-null detail",
            fnBody.contains("\"unhooker-null\"")
        )
        assertTrue(
            "String overload must return null on nonfatal failure",
            catchBody.contains("null")
        )
        assertTrue(
            "String overload catch must start with fatal boundary",
            catchBody.startsWith("FatalErrors.unwrapAndRethrowIfFatal(t)")
        )
    }

    @Test
    fun findAndHookMethodClassOverloadPreservesNonfatalFailureSemantics() {
        val source = sourceFile("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt").readText()

        val fnStart = source.indexOf(
            "fun findAndHookMethod(clazz: Class<*>, methodName: String"
        )
        assertTrue("findAndHookMethod(Class,...) must exist", fnStart >= 0)

        val fnEnd = source.indexOf(
            "fun findAndHookMethodSilently(className: String",
            fnStart
        )
        val fnBody = source.substring(fnStart, if (fnEnd >= 0) fnEnd else source.length)

        val catchBody = extractThrowableCatch(fnBody)

        assertTrue(
            "Class overload must preserve TARGET_MEMBER_MISSING",
            fnBody.contains("HookDiagnostics.Status.TARGET_MEMBER_MISSING")
        )
        assertTrue(
            "Class overload must preserve INSTALL_FAILED",
            fnBody.contains("HookDiagnostics.Status.INSTALL_FAILED")
        )
        assertTrue(
            "Class overload must preserve unhooker-null detail",
            fnBody.contains("\"unhooker-null\"")
        )
        assertTrue(
            "Class overload must return null on nonfatal failure",
            catchBody.contains("null")
        )
        assertTrue(
            "Class overload catch must start with fatal boundary",
            catchBody.startsWith("FatalErrors.unwrapAndRethrowIfFatal(t)")
        )
    }

    @Test
    fun findAndHookMethodSilentlyStringOverloadPreservesNonfatalFailureSemantics() {
        val source = sourceFile("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt").readText()

        val fnStart = source.indexOf(
            "fun findAndHookMethodSilently(className: String, classLoader: ClassLoader?, methodName: String"
        )
        assertTrue("findAndHookMethodSilently(String,...) must exist", fnStart >= 0)

        val fnEnd = source.indexOf(
            "fun findAndHookMethodSilently(clazz: Class<*>",
            fnStart
        )
        val fnBody = source.substring(fnStart, if (fnEnd >= 0) fnEnd else source.length)

        val catchBody = extractThrowableCatch(fnBody)

        assertTrue(
            "Silent String overload must preserve SILENTLY_SKIPPED",
            fnBody.contains("HookDiagnostics.Status.SILENTLY_SKIPPED")
        )
        assertTrue(
            "Silent String overload must return false on nonfatal failure",
            catchBody.contains("false")
        )
        assertTrue(
            "Silent String overload catch must start with fatal boundary",
            catchBody.startsWith("FatalErrors.unwrapAndRethrowIfFatal(t)")
        )
    }

    @Test
    fun findAndHookMethodSilentlyClassOverloadPreservesNonfatalFailureSemantics() {
        val source = sourceFile("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt").readText()

        val fnStart = source.indexOf(
            "fun findAndHookMethodSilently(clazz: Class<*>, methodName: String"
        )
        assertTrue("findAndHookMethodSilently(Class,...) must exist", fnStart >= 0)

        val fnBody = source.substring(fnStart)

        val catchBody = extractThrowableCatch(fnBody)

        assertTrue(
            "Silent Class overload must preserve SILENTLY_SKIPPED",
            fnBody.contains("HookDiagnostics.Status.SILENTLY_SKIPPED")
        )
        assertTrue(
            "Silent Class overload must return false on nonfatal failure",
            catchBody.contains("false")
        )
        assertTrue(
            "Silent Class overload catch must start with fatal boundary",
            catchBody.startsWith("FatalErrors.unwrapAndRethrowIfFatal(t)")
        )
    }

    private fun extractThrowableCatch(fnBody: String): String {
        val catchStart = fnBody.indexOf("catch (t: Throwable) {")
        assertTrue("Function must have a catch (t: Throwable) block", catchStart >= 0)
        val bodyStart = catchStart + "catch (t: Throwable) {".length
        var braceDepth = 1
        var i = bodyStart
        while (i < fnBody.length && braceDepth > 0) {
            when (fnBody[i]) {
                '{' -> braceDepth++
                '}' -> braceDepth--
            }
            i++
        }
        return fnBody.substring(bodyStart, i - 1).trim()
    }

    private fun countPattern(text: String, pattern: String): Int {
        val regex = pattern.toRegex()
        return regex.findAll(text).count()
    }

    private fun sourceFile(relativePath: String): File {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.exists()) return candidate
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
