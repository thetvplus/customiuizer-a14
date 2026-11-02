package tv.withaibuild.customiuizer.utils

import org.junit.Assert.*
import org.junit.Test

class AudioFftBandsTest {
    @Test fun signedComplexBinsUseTheActualFrequencyAndExcludeDcAndNyquist() {
        val fft = ByteArray(512)
        val levels = FloatArray(5)
        fft[0] = 127; fft[1] = -128
        fft[32] = -100; fft[33] = 100 // 1500 Hz at 48 kHz.
        assertEquals(20000f, AudioFftBands.peakPower(fft, 16, 17), 0f)
        assertTrue(AudioFftBands.five(fft, 48_000_000, levels))
        assertEquals(kotlin.math.sqrt(20000f / 32768f), levels[2], .00001f)
        for (index in listOf(0, 1, 3, 4)) assertEquals(0f, levels[index], 0f)
        fft[32] = 0; fft[33] = 0
        AudioFftBands.five(fft, 48_000_000, levels)
        assertArrayEquals(FloatArray(5), levels, 0f)
    }

    @Test fun equalBinAmplitudesHaveEqualHeightsWithoutTrebleBiasOrPerBandGain() {
        val fft = ByteArray(512)
        for (bin in listOf(1, 4, 16, 64, 128)) { fft[bin * 2] = 32; fft[bin * 2 + 1] = -16 }
        val levels = FloatArray(5)
        assertTrue(AudioFftBands.five(fft, 48_000_000, levels))
        assertTrue(levels[0] in .19f.. .20f)
        for (level in levels) assertEquals(levels[0], level, 0f)
        // A single strong high-frequency band cannot rescale the other bands.
        fft[256] = -128; fft[257] = -128
        AudioFftBands.five(fft, 48_000_000, levels)
        assertEquals(1f, levels[4], 0f)
        for (index in 0..3) assertEquals(kotlin.math.sqrt(1280f / 32768f), levels[index], .00001f)
    }

    @Test fun actualBinFrequenciesKeepTheFiveRangesOrderedWithoutOverlapAtEverySupportedRate() {
        for (rate in listOf(8_000_000, 16_000_000, 44_100_000, 48_000_000, 96_000_000, 192_000_000)) {
            for (size in listOf(256, 512, 1024)) {
                val fft = ByteArray(size)
                val levels = FloatArray(5)
                for (bin in 1 until size / 2) {
                    fft.fill(0); fft[bin * 2] = 64
                    assertTrue(AudioFftBands.five(fft, rate, levels))
                    val hz = bin * (rate / 1000.0) / size
                    val band = when { hz < 63 || hz > 16000 -> -1; hz <= 200 -> 0; hz <= 630 -> 1; hz <= 2000 -> 2; hz <= 6300 -> 3; else -> 4 }
                    for (index in 0..4) assertEquals("rate=$rate size=$size bin=$bin hz=$hz band=$band",
                        if (index == band) kotlin.math.sqrt(.125f) else 0f, levels[index], .00001f)
                }
            }
        }
    }

    @Test fun halvingMeasuredAmplitudeHalvesTheBarAndSilenceIsDeliveredWithoutAnotherSourceEnvelope() {
        val fft = ByteArray(512)
        val levels = FloatArray(5)
        fft[2] = -128; AudioFftBands.five(fft, 48_000_000, levels)
        val strong = levels[0]
        fft[2] = 64; AudioFftBands.five(fft, 48_000_000, levels)
        assertEquals(strong * .5f, levels[0], .00001f)
        fft.fill(0); AudioFftBands.five(fft, 48_000_000, levels)
        assertArrayEquals("Only the display owns the release tail", FloatArray(5), levels, 0f)
    }

    @Test fun quantizationNoiseRemainsQuietAndMaximumSignedComponentsStayBounded() {
        val fft = ByteArray(512)
        val levels = FloatArray(5)
        fft[128] = 1; fft[129] = -1; fft[256] = -1; fft[257] = 1
        AudioFftBands.five(fft, 48_000_000, levels)
        assertArrayEquals(FloatArray(5), levels, 0f)
        fft[128] = -128; fft[129] = -128; fft[256] = -128; fft[257] = -128
        AudioFftBands.five(fft, 48_000_000, levels)
        assertEquals(1f, levels[3], 0f); assertEquals(1f, levels[4], 0f)
        assertTrue(levels.all { it.isFinite() && it in 0f..1f })
    }

    @Test fun invalidDataIsRejectedAndSilenceRemainsMeasuredSilence() {
        val levels = FloatArray(5)
        assertFalse(AudioFftBands.five(ByteArray(7), 48_000_000, levels))
        assertFalse(AudioFftBands.five(ByteArray(63), 48_000_000, levels))
        assertFalse(AudioFftBands.five(ByteArray(128), 0, levels))
        assertFalse(AudioFftBands.five(ByteArray(128), 200_000_000, levels))
        assertTrue(AudioFftBands.five(ByteArray(128), 44_100_000, levels))
        assertArrayEquals(FloatArray(5), levels, 0f)
    }
}
