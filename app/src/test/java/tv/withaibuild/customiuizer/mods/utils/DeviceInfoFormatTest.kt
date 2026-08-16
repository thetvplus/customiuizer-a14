package tv.withaibuild.customiuizer.mods.utils

import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInfoFormatTest {

    @Test
    fun oneDecimalFormatterMatchesLegacyTemperatureDomain() {
        for (batteryTenths in -500..1_500) {
            val value = batteryTenths / 10f
            assertEquals(legacyOneDecimal(value), formatMonitorOneDecimal(value))
        }

        for (cpuMilliDegrees in -50_000..200_000 step 17) {
            val value = cpuMilliDegrees / 1000f
            assertEquals(legacyOneDecimal(value), formatMonitorOneDecimal(value))
        }
    }

    @Test
    fun twoDecimalFormatterMatchesLegacyCurrentDomain() {
        for (milliAmps in -100_000..100_000 step 7) {
            val value = milliAmps / 1000f
            assertEquals(legacyTwoDecimals(value), formatMonitorTwoDecimals(value))
        }
    }

    @Test
    fun twoDecimalFormatterMatchesLegacyPowerDomain() {
        for (microVolts in 2_500_000..5_500_000 step 7_919) {
            for (milliAmps in -10_000..10_000 step 977) {
                val volts = microVolts / 1000f / 1000f
                val value = abs(volts * milliAmps) / 1000
                assertEquals(
                    "microVolts=$microVolts milliAmps=$milliAmps value=$value",
                    legacyTwoDecimals(value),
                    formatMonitorTwoDecimals(value)
                )
            }
        }
    }

    private fun legacyOneDecimal(value: Float): String =
        String.format(Locale.ROOT, "%.1f", value)

    private fun legacyTwoDecimals(value: Float): String =
        String.format(Locale.ROOT, "%.2f", value)
}
