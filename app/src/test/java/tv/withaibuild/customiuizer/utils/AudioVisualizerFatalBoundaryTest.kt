package tv.withaibuild.customiuizer.utils

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural guard: every catch(Throwable) block in AudioVisualizer must either rethrow fatal
 * errors explicitly or route the Throwable through XposedHelpers.log, which already rethrows
 * fatal errors before logging.
 */
class AudioVisualizerFatalBoundaryTest {

    @Test
    fun everyCatchThrowableRethrowsFatal() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt")
        val catchPattern = Regex("catch\\s*\\(\\s*(?:\\w+\\s*:\\s*)?(?:\\w+\\s+)?Throwable(?:\\s+\\w+)?\\s*\\)\\s*\\{")

        var failures = 0
        for (match in catchPattern.findAll(source)) {
            val open = source.indexOf("{", match.range.first) + 1
            val close = findMatchingBrace(source, open - 1)
            assertTrue("catch block at ${match.range.first} is not closed", close > open)

            val body = source.substring(open, close)
            val rethrowsFatal = body.contains("FatalErrors.rethrowIfFatal") || body.contains("XposedHelpers.log")
            if (!rethrowsFatal) {
                failures++
                println("Missing fatal rethrow at ${match.range.first}: $body")
            }
        }
        assertTrue("$failures catch(Throwable) block(s) do not rethrow fatal errors", failures == 0)
    }

    @Test
    fun fallbackCatchBlocksUseExplicitFatalRethrow() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt")
        val fallbackPatterns = listOf(
            "mVisualizer?.enabled" to "return",
            "config.audioAttributes.usage" to "continue",
            "visualizer?.release()" to "}",
            "config.javaClass.getDeclaredMethod(\"isActive\")" to "true",
            "config.javaClass.getDeclaredMethod(\"getSessionId\")" to "0"
        )

        for ((marker, fallback) in fallbackPatterns) {
            val index = source.indexOf(marker)
            assertTrue("marker not found: $marker", index >= 0)

            val catchIndex = source.indexOf("catch (", index)
            assertTrue("no catch after marker: $marker", catchIndex >= 0)

            val open = source.indexOf("{", catchIndex) + 1
            val close = findMatchingBrace(source, open - 1)
            assertTrue("catch block not closed for: $marker", close > open)

            val body = source.substring(open, close)
            assertTrue(
                "fallback boundary for $marker must explicitly rethrow fatal errors; body=[$body]",
                body.contains("FatalErrors.rethrowIfFatal")
            )
        }
    }

    private fun findMatchingBrace(text: String, openOffset: Int): Int {
        var depth = 0
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        var i = openOffset
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && i + 1 < n && text[i + 1] == '/') {
                    inBlockComment = false
                    i++
                }
                inString -> when (c) {
                    '\\' -> if (i + 1 < n) i++
                    '"' -> inString = false
                }
                else -> when (c) {
                    '"' -> inString = true
                    '/' -> if (i + 1 < n) {
                        when (text[i + 1]) {
                            '/' -> { inLineComment = true; i++ }
                            '*' -> { inBlockComment = true; i++ }
                        }
                    }
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
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
