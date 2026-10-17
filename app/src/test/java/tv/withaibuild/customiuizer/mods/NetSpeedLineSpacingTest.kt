package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetSpeedLineSpacingTest {

    @Test
    fun resolveNetSpeedLineSpacing_defaultsMatchLegacyAlgorithm() {
        assertEquals(0.90f, resolveNetSpeedLineSpacing(13, 100), 0.0001f)
        assertEquals(0.85f, resolveNetSpeedLineSpacing(18, 100), 0.0001f)
    }

    @Test
    fun resolveNetSpeedLineSpacing_adjustmentScalesLinearly() {
        val compact = resolveNetSpeedLineSpacing(13, 70)
        val loose = resolveNetSpeedLineSpacing(13, 130)
        val default = resolveNetSpeedLineSpacing(13, 100)

        assertTrue("70% should be more compact than 100%", compact < default)
        assertTrue("130% should be looser than 100%", loose > default)
    }

    @Test
    fun resolveNetSpeedLineSpacing_clampsToBounds() {
        val tooLow = resolveNetSpeedLineSpacing(13, 0)
        val atMinimum = resolveNetSpeedLineSpacing(13, 70)
        assertEquals(atMinimum, tooLow, 0.0001f)

        val tooHigh = resolveNetSpeedLineSpacing(13, 200)
        val atMaximum = resolveNetSpeedLineSpacing(13, 130)
        assertEquals(atMaximum, tooHigh, 0.0001f)
    }

    @Test
    fun resolveNetSpeedLineSpacing_fontSizeThresholdIs17() {
        assertEquals(0.90f, resolveNetSpeedLineSpacing(17, 100), 0.0001f)
        assertEquals(0.85f, resolveNetSpeedLineSpacing(18, 100), 0.0001f)
    }
}
