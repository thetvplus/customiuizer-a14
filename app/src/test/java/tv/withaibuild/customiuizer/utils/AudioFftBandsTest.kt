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
        assertEquals(before * .75f, levels[2], .0001f)
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
}
