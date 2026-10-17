package tv.withaibuild.customiuizer

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class MainModuleFatalBoundaryTest {

    @Test
    fun mainModuleCatchThrowableRethrowsFatalBeforeLogging() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")

        // Find every catch (Throwable) block and ensure FatalErrors.rethrowIfFatal is the first call.
        val pattern = Regex(
            """catch\s*\(\s*Throwable\s+\w+\s*\)\s*\{(.*?)(?=catch\s*\(|\}\s*catch|\}\s*$)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val matches = pattern.findAll(source)
        val matchList = matches.toList()
        assertTrue("No catch(Throwable) blocks found; test may be out of date", matchList.isNotEmpty())

        for (match in matchList) {
            val block = match.groupValues[1]
            assertTrue(
                "catch(Throwable) block must call FatalErrors.rethrowIfFatal before logging: $block",
                block.contains("FatalErrors.rethrowIfFatal(")
            )
        }
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
