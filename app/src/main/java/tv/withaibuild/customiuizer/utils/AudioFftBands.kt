package tv.withaibuild.customiuizer.utils

import kotlin.math.sqrt
import kotlin.math.ceil

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
        // Approximately half-decade spacing, with the display window capped at 63 Hz–16 kHz.
        var start = ceil(63f * fft.size / hertz).toInt().coerceAtLeast(1)
        for (index in 0..4) {
            val frequency = when (index) { 0 -> 200f; 1 -> 630f; 2 -> 2000f; 3 -> 6300f; else -> minOf(16000f, hertz * .5f) }
            val end = ((frequency * fft.size / hertz).toInt() + 1).coerceIn(start, fft.size / 2)
            val power = peakPower(fft, start, end)
            // Equal complex-bin amplitudes have equal heights in every band. Do not
            // exaggerate treble or auto-normalize each band. The View owns the one
            // time-based attack/release envelope; capture retains only five scalars.
            output[index] = if (power <= 2f) 0f else sqrt(power / 32768f).coerceAtMost(1f)
            start = end
        }
        return true
    }
}
