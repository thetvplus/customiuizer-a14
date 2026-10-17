package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The FFT capture callback and the Choreographer callback run at audio and display rates, so
 * they are held to a stricter contract than the rest of the view.
 */
class AudioVisualizerHotPathTest {

    @Test
    fun fftCaptureScansTheBufferOnlyOnce() {
        val body = block("override fun onFftDataCapture(")

        assertFalse("the silence pre-scan must not traverse the FFT buffer again", body.contains("allZeros"))
        assertFalse("no full-buffer helper may run before the band loop", source().contains("fun allZeros("))
    }

    @Test
    fun fftCaptureTakesTheFrameLockExactlyOnce() {
        val body = block("override fun onFftDataCapture(")

        assertEquals(
            "the 31 band results must be published with a single lock acquisition",
            1,
            Regex("synchronized\\(mFrameLock\\)").findAll(body).count(),
        )
        assertTrue(body.contains("System.arraycopy(scratchTargets, 0, mPendingTargets, 0, band)"))
    }

    @Test
    fun fftCaptureReusesAScratchBuffer() {
        val source = source()
        assertTrue(source.contains("private val scratchTargets = FloatArray(mBandsNum)"))
        assertFalse(
            "no per-callback array may be allocated on the capture thread",
            block("override fun onFftDataCapture(").contains("FloatArray("),
        )
    }

    @Test
    fun frameSchedulerParksWhenTheAnimationIsSettled() {
        val body = block("override fun doFrame(")

        assertTrue(
            "a settled animation with no pending data must stop rescheduling",
            body.contains("if (fraction >= 1f && !needsInvalidate)"),
        )
        assertTrue(body.contains("if (!mNewDataPending) return"))
    }

    /** Returns the body of the declaration starting at [header], balanced by braces. */
    private fun block(header: String): String {
        val source = source()
        val start = source.indexOf(header)
        check(start >= 0) { "Declaration not found: $header" }
        var index = source.indexOf('{', start)
        check(index >= 0) { "Body not found: $header" }
        var depth = 0
        val end: Int
        while (true) {
            when (source[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) {
                end = index
                break
            }
            index++
        }
        return source.substring(start, end + 1)
    }

    private fun source(): String {
        val relativePath = "app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt"
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
