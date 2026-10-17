package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class DeviceInfoMonitorBoundaryTest {

    @Test
    fun readBatteryPropsFailsSafely() {
        // The real sysfs path is not available in the JVM; the function must degrade to null.
        assertNull(DeviceInfoFormatter.readBatteryProps())
    }

    @Test
    fun readCpuTempFailsSafelyForUnknownZone() {
        assertNull(DeviceInfoFormatter.readCpuTemp(-1))
    }

    @Test
    fun readCpuTempFailsSafelyForInvalidZone() {
        assertNull(DeviceInfoFormatter.readCpuTemp(9999))
    }

    @Test
    fun formatterSurvivesEmptyBatteryProperties() {
        val cfg = DeviceInfoConfig(
            showBatteryDetail = true,
            showDeviceTemp = false,
            batteryInCharge = false,
            batteryTempDecimal = false,
            batteryFixCurrentRatio = false,
            batteryPositive = false,
            batterySingleRow = false,
            batteryReverseOrder = false,
            batteryHideUnit = 0,
            deviceTempSingleRow = false,
            deviceTempReverseOrder = false,
            deviceTempHideUnit = false,
            batteryContentOpt = 1,
            deviceTempContentOpt = 1
        )
        val text = DeviceInfoFormatter.formatBatteryInfo(cfg, Properties())
        assertTrue(text.isNotEmpty())
        assertEquals("0℃\n0mA", text)
    }

    @Test
    fun formatterSurvivesMalformedValues() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "nan")
            setProperty("POWER_SUPPLY_CURRENT_NOW", "")
            setProperty("POWER_SUPPLY_VOLTAGE_NOW", "invalid")
        }
        val cfg = DeviceInfoConfig(
            showBatteryDetail = true,
            showDeviceTemp = false,
            batteryInCharge = false,
            batteryTempDecimal = false,
            batteryFixCurrentRatio = false,
            batteryPositive = false,
            batterySingleRow = true,
            batteryReverseOrder = false,
            batteryHideUnit = 0,
            deviceTempSingleRow = false,
            deviceTempReverseOrder = false,
            deviceTempHideUnit = false,
            batteryContentOpt = 5,
            deviceTempContentOpt = 1
        )
        val text = DeviceInfoFormatter.formatBatteryInfo(cfg, props)
        // No exception, finite string, no NaN/Infinity markers.
        assertTrue(text.isNotEmpty())
        assertTrue("output must be finite and printable: $text", text.toFloatOrNull() == null || text.toFloatOrNull()!!.isFinite())
        assertTrue(text.contains("0"))
    }

    @Test
    fun parseSysfsIntOverflowFallsBack() {
        assertEquals(0, DeviceInfoFormatter.parseSysfsInt("999999999999"))
        assertEquals(-1, DeviceInfoFormatter.parseSysfsInt("999999999999", -1))
    }

    @Test
    fun formatDeviceInfoSurvivesNullCpuProps() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "300")
        }
        val cfg = DeviceInfoConfig(
            showBatteryDetail = false,
            showDeviceTemp = true,
            batteryInCharge = false,
            batteryTempDecimal = false,
            batteryFixCurrentRatio = false,
            batteryPositive = false,
            batterySingleRow = false,
            batteryReverseOrder = false,
            batteryHideUnit = 0,
            deviceTempSingleRow = false,
            deviceTempReverseOrder = false,
            deviceTempHideUnit = false,
            batteryContentOpt = 1,
            deviceTempContentOpt = 3
        )
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, props, "")
        assertEquals("", text)
    }
}
