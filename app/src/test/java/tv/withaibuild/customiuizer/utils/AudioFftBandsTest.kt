package tv.withaibuild.customiuizer.utils

import org.junit.Assert.*
import org.junit.Test

class AudioFftBandsTest {
    @Test fun fiveBandsUseActualSignedComplexDataAndBoundTheirAttackAndRelease() {
        val fft = ByteArray(512)
        val levels = FloatArray(5)
        // At 48 kHz, bin 16 is 1500 Hz: only the third band may respond.
        fft[32] = -100; fft[33] = 100
        assertEquals(20000f, AudioFftBands.peakPower(fft, 16, 17), 0f)
        assertTrue(AudioFftBands.five(fft, 48_000_000, levels))
        assertTrue(levels[2] > .5f)
        for (index in listOf(0, 1, 3, 4)) assertEquals(0f, levels[index], 0f)
        val before = levels[2]
        fft.fill(0)
        assertTrue(AudioFftBands.five(fft, 48_000_000, levels))
        assertEquals(before * .55f, levels[2], .0001f)
        assertTrue(levels.all { it.isFinite() && it in 0f..1f })
    }

    @Test fun halvingMeasuredAmplitudeHalvesTheSettledBarInsteadOfHoldingItNearTheTop() {
        val fft = ByteArray(512)
        val levels = FloatArray(5)
        fft[2] = -128
        repeat(30) { assertTrue(AudioFftBands.five(fft, 48_000_000, levels)) }
        val strong = levels[0]
        fft[2] = 64
        repeat(30) { assertTrue(AudioFftBands.five(fft, 48_000_000, levels)) }
        assertEquals(strong * .5f, levels[0], .001f)
        assertTrue(levels.drop(1).all { it == 0f })
    }

    @Test fun separatedBeatsDecayBeforeTheNextBeatAndStillAttackPromptly() {
        val fft = ByteArray(512)
        val levels = FloatArray(5)
        fft[2] = -128
        repeat(30) { AudioFftBands.five(fft, 48_000_000, levels) }
        val peak = levels[0]
        fft.fill(0)
        // Four 20 Hz callbacks: silence must clear the previous beat within 200 ms.
        repeat(4) { AudioFftBands.five(fft, 48_000_000, levels) }
        assertTrue("The previous beat must not fill the next quiet interval", levels[0] < peak * .1f)
        fft[2] = -128
        AudioFftBands.five(fft, 48_000_000, levels)
        assertTrue("A new beat must respond in the first capture frame", levels[0] >= peak * .8f)
    }

    @Test fun upperBandsKeepWeakMeasuredTransientsVisibleAtTheSmallDisplaySize() {
        val fft = ByteArray(512)
        val levels = FloatArray(5)
        // Equal physical amplitudes at 93.75, 3750 and 9375 Hz. The last two
        // receive fixed display compensation; this is not independent auto-gain.
        fft[2] = 8
        fft[80] = 8
        fft[200] = 8
        AudioFftBands.five(fft, 48_000_000, levels)
        assertEquals(levels[0] * 3f, levels[3], .0001f)
        assertEquals(levels[0] * 8f, levels[4], .0001f)
        assertEquals(0f, levels[1], 0f)
        assertEquals(0f, levels[2], 0f)
    }

    @Test fun upperBandCompensationDoesNotAnimateQuantizationNoiseOrOverflow() {
        val fft = ByteArray(512)
        val levels = FloatArray(5)
        fft[80] = 1; fft[81] = -1
        fft[200] = -1; fft[201] = 1
        repeat(20) { AudioFftBands.five(fft, 48_000_000, levels) }
        assertArrayEquals(FloatArray(5), levels, 0f)
        fft[80] = -128; fft[81] = -128
        fft[200] = -128; fft[201] = -128
        repeat(20) { AudioFftBands.five(fft, 48_000_000, levels) }
        assertEquals(1f, levels[3], .0001f)
        assertEquals(1f, levels[4], .0001f)
        assertTrue(levels.all { it.isFinite() && it in 0f..1f })
    }

    @Test fun invalidDataIsRejectedAndSilenceRemainsMeasuredSilence() {
        val levels = FloatArray(5)
        assertFalse(AudioFftBands.five(ByteArray(7), 48_000_000, levels))
        assertFalse(AudioFftBands.five(ByteArray(63), 48_000_000, levels))
        assertFalse(AudioFftBands.five(ByteArray(128), 0, levels))
        assertTrue(AudioFftBands.five(ByteArray(128), 44_100_000, levels))
        assertArrayEquals(FloatArray(5), levels, 0f)
    }

    @Test fun nativeRatesAndCaptureLengthsKeepQuietContentBelowStrongContentWithoutCodecGain() {
        // File codecs and PCM depths have already been converted by the native effect;
        // the input here is always signed 8-bit FFT at the reported capture rate.
        for (rate in listOf(8_000_000, 16_000_000, 44_100_000, 48_000_000,
            88_200_000, 96_000_000, 176_400_000, 192_000_000)) {
            for (size in listOf(256, 512, 1024)) {
                val fft = ByteArray(size)
                val bin = kotlin.math.round(1000f * size / (rate / 1000f)).toInt().coerceAtLeast(1)
                val levels = FloatArray(5)
                fft[bin * 2] = 64
                repeat(30) { assertTrue(AudioFftBands.five(fft, rate, levels)) }
                val strong = levels[2]
                assertTrue("rate=$rate, size=$size", strong > .1f && strong < .9f)
                fft[bin * 2] = 2
                repeat(30) { assertTrue(AudioFftBands.five(fft, rate, levels)) }
                assertTrue("quiet rate=$rate, size=$size", levels[2] < strong * .05f)
                fft.fill(0)
                repeat(30) { AudioFftBands.five(fft, rate, levels) }
                assertTrue(levels.all { it >= 0f && it < .0001f })
            }
        }
    }
}
