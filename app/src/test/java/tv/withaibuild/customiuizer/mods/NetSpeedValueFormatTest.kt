package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NetSpeedValueFormatTest {

    @Test
    fun formatNetSpeedValue_matchesLegacyFormatterAtDisplayBoundaries() {
        val byteValues = longArrayOf(
            0L,
            40L,
            51L,
            52L,
            61L,
            1_064L,
            1_075L,
            1_076L,
            10_179L,
            10_189L,
            102_287L,
            102_338L,
            102_389L,
            102_400L,
            102_902L,
            102_912L,
            1_022_464L,
            1_022_976L
        )

        byteValues.forEach { bytes ->
            val value = bytes / 1024.0f
            assertEquals(legacyFormat(value), formatNetSpeedValue(value))
        }
    }

    @Test
    fun formatNetSpeedValue_matchesLegacyFormatterAcrossNetworkSpeedRange() {
        for (rawBytesPerSecond in 0L..4_194_304L step 257L) {
            var displayValue = rawBytesPerSecond / 1024.0f
            if (displayValue > 999.0f) displayValue /= 1024.0f

            assertEquals(
                "bytes=$rawBytesPerSecond value=$displayValue",
                legacyFormat(displayValue),
                formatNetSpeedValue(displayValue)
            )
        }
    }

    private fun legacyFormat(value: Float): String =
        if (value < 100.0f) String.format(Locale.ROOT, "%.1f", value)
        else String.format(Locale.ROOT, "%.0f", value)
}
