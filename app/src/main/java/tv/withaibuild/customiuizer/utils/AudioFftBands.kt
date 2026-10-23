package tv.withaibuild.customiuizer.utils

import kotlin.math.log10

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
            val energy = if (power > 0f) (10f * log10(power) / 45.16f).coerceIn(0f, 1f) else 0f
            // Bounded attack/release. Only five values survive a frame; raw FFT never does.
            val previous = output[index]
            output[index] = (previous + (energy - previous) * if (energy > previous) .65f else .25f).coerceIn(0f, 1f)
            start = end
        }
        return true
    }
}
