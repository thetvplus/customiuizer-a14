package tv.withaibuild.customiuizer.utils

import kotlin.math.sqrt

/** Shared FFT math. Signed complex bins, no retained audio or callback-time allocation. */
internal object AudioFftBands {
    fun peakPower(fft: ByteArray, start: Int, end: Int): Float {
        var peak = 0
        for (bin in start.coerceAtLeast(1) until minOf(end, fft.size / 2)) {
            val real = fft[bin * 2].toInt()
            val imaginary = fft[bin * 2 + 1].toInt()
            peak = maxOf(peak, real * real + imaginary * imaginary)
        }
        return peak.toFloat()
    }

    /** Five frequency bands, adapted to the actual callback sample rate (in milli-Hz). */
    fun five(fft: ByteArray, sampleRate: Int, output: FloatArray): Boolean {
        if (output.size != 5 || fft.size < 32 || fft.size and (fft.size - 1) != 0 || sampleRate <= 0) return false
        val hertz = sampleRate / 1000f
        if (hertz < 8000f || hertz > 192000f) return false
        var start = 1
        for (index in 0..4) {
            val frequency = when (index) { 0 -> 200f; 1 -> 600f; 2 -> 1800f; 3 -> 6000f; else -> hertz * .5f }
            val end = ((frequency * fft.size / hertz).toInt() + 1).coerceIn(start, fft.size / 2)
            val power = peakPower(fft, start, end)
            // Preserve amplitude changes: logarithmic compression held normal music near
            // full height. Signed 8-bit complex components have at most 32768 power.
            val energy = sqrt(power / 32768f)
            // At 20 Hz a quiet interval falls below 10% in four frames (200 ms).
            // Only five values survive a frame; raw FFT never does.
            val previous = output[index]
            output[index] = (previous + (energy - previous) * if (energy > previous) .8f else .45f).coerceIn(0f, 1f)
            start = end
        }
        return true
    }
}
