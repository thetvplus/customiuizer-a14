package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Properties

class DeviceInfoFormatterTest {

    @Test
    fun parseSysfsIntHandlesNullAndEmpty() {
        assertEquals(0, DeviceInfoFormatter.parseSysfsInt(null))
        assertEquals(0, DeviceInfoFormatter.parseSysfsInt(""))
        assertEquals(0, DeviceInfoFormatter.parseSysfsInt("   "))
    }

    @Test
    fun parseSysfsIntHandlesSignsAndWhitespace() {
        assertEquals(123, DeviceInfoFormatter.parseSysfsInt("123"))
        assertEquals(-456, DeviceInfoFormatter.parseSysfsInt(" -456 "))
        assertEquals(789, DeviceInfoFormatter.parseSysfsInt("+789"))
    }

    @Test
    fun parseSysfsIntFallsBackOnMalformed() {
        assertEquals(0, DeviceInfoFormatter.parseSysfsInt("not a number"))
        assertEquals(0, DeviceInfoFormatter.parseSysfsInt("12v"))
        assertEquals(42, DeviceInfoFormatter.parseSysfsInt("overflow-value", 42))
    }

    @Test
    fun formatBatteryInfoWithDefaultContent() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "345")
            setProperty("POWER_SUPPLY_CURRENT_NOW", "800000")
        }
        val cfg = baseConfig().copy(batteryContentOpt = 1)
        val text = DeviceInfoFormatter.formatBatteryInfo(cfg, props)
        assertFalse(text.isEmpty())
        assert(text.contains("34"))
        assert(text.contains("mA"))
    }

    @Test
    fun formatBatteryInfoCurrentInAmperes() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "350")
            setProperty("POWER_SUPPLY_CURRENT_NOW", "-2500000")
        }
        val cfg = baseConfig().copy(batteryContentOpt = 1, batteryPositive = true)
        val text = DeviceInfoFormatter.formatBatteryInfo(cfg, props)
        assert(text.contains("A"))
    }

    @Test
    fun formatBatteryInfoHandlesMissingVoltage() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "400")
            // POWER_SUPPLY_VOLTAGE_NOW missing
        }
        val cfg = baseConfig().copy(batteryContentOpt = 2)
        val text = DeviceInfoFormatter.formatBatteryInfo(cfg, props)
        assertEquals("0.00W", text)
    }

    @Test
    fun formatBatteryInfoReverseAndHideUnit() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "300")
            setProperty("POWER_SUPPLY_CURRENT_NOW", "800000")
        }
        val cfg = baseConfig().copy(
            batteryContentOpt = 1,
            batteryReverseOrder = true,
            batteryHideUnit = 1,
            batterySingleRow = false
        )
        val text = DeviceInfoFormatter.formatBatteryInfo(cfg, props)
        assertFalse(text.contains("℃"))
        assertFalse(text.contains("mA"))
    }

    @Test
    fun formatBatteryInfoSingleRow() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "250")
            setProperty("POWER_SUPPLY_CURRENT_NOW", "500000")
        }
        val cfg = baseConfig().copy(batteryContentOpt = 1, batterySingleRow = true)
        val text = DeviceInfoFormatter.formatBatteryInfo(cfg, props)
        assertFalse(text.contains("\n"))
    }

    @Test
    fun formatBatteryInfoCurrentRatioFix() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "0")
            setProperty("POWER_SUPPLY_CURRENT_NOW", "800")
        }
        val cfg = baseConfig().copy(
            batteryContentOpt = 3,
            batteryFixCurrentRatio = true,
            batteryPositive = false
        )
        val text = DeviceInfoFormatter.formatBatteryInfo(cfg, props)
        // current is already in mA, -800 mA stays in mA range (< 1000)
        assert(text.startsWith("-800"))
    }

    @Test
    fun formatDeviceInfoDefaultCpuAndBattery() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "370")
        }
        val cfg = baseConfig().copy(deviceTempContentOpt = 1)
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, props, "52000")
        assert(text.contains("37"))
        assert(text.contains("52"))
    }

    @Test
    fun formatDeviceInfoBatteryOnly() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "410")
        }
        val cfg = baseConfig().copy(deviceTempContentOpt = 2)
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, props, "0")
        assert(text.startsWith("41"))
    }

    @Test
    fun formatDeviceInfoReverseAndHideUnit() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "280")
        }
        val cfg = baseConfig().copy(
            deviceTempContentOpt = 1,
            deviceTempReverseOrder = true,
            deviceTempHideUnit = true,
            deviceTempSingleRow = true
        )
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, props, "39000")
        assertFalse(text.contains("℃"))
        assertFalse(text.contains("\n"))
    }

    @Test
    fun formatDeviceInfoMalformedCpuFallsBack() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_TEMP", "300")
        }
        val cfg = baseConfig().copy()
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, props, "not-a-number")
        assert(text.contains("0.0"))
    }

    @Test
    fun contentOptMappingMatchesPreferenceArrays() {
        assertEquals(true, DeviceInfoFormatter.needsBatteryTemperature(1))
        assertEquals(true, DeviceInfoFormatter.needsCpuTemperature(1))
        assertEquals(true, DeviceInfoFormatter.needsBatteryTemperature(2))
        assertEquals(false, DeviceInfoFormatter.needsCpuTemperature(2))
        assertEquals(false, DeviceInfoFormatter.needsBatteryTemperature(3))
        assertEquals(true, DeviceInfoFormatter.needsCpuTemperature(3))
    }

    @Test
    fun batteryOnlyShowsWhenCpuMissing() {
        val props = Properties().apply { setProperty("POWER_SUPPLY_TEMP", "410") }
        val cfg = baseConfig().copy(deviceTempContentOpt = 2)
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, props, null)
        assert(text.startsWith("41"))
        assertFalse(text.contains("\n"))
    }

    @Test
    fun cpuOnlyShowsWhenBatteryMissing() {
        val cfg = baseConfig().copy(deviceTempContentOpt = 3)
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, null, "52000")
        assert(text.startsWith("52"))
        assertFalse(text.contains("\n"))
    }

    @Test
    fun bothShowsBatteryAndCpuWhenPresent() {
        val props = Properties().apply { setProperty("POWER_SUPPLY_TEMP", "370") }
        val cfg = baseConfig().copy(deviceTempContentOpt = 1)
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, props, "52000")
        assert(text.contains("37"))
        assert(text.contains("52"))
        assert(text.contains("\n"))
    }

    @Test
    fun bothFallsBackToBatteryWhenCpuMissing() {
        val props = Properties().apply { setProperty("POWER_SUPPLY_TEMP", "370") }
        val cfg = baseConfig().copy(deviceTempContentOpt = 1)
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, props, null)
        assertEquals("37.0℃", text)
    }

    @Test
    fun bothFallsBackToCpuWhenBatteryMissing() {
        val cfg = baseConfig().copy(deviceTempContentOpt = 1)
        val text = DeviceInfoFormatter.formatDeviceInfo(cfg, null, "52000")
        assertEquals("52.0℃", text)
    }

    @Test
    fun bothMissingSourcesProduceEmptyText() {
        val cfg = baseConfig().copy(deviceTempContentOpt = 1)
        assertEquals("", DeviceInfoFormatter.formatDeviceInfo(cfg, null, null))
        assertEquals("", DeviceInfoFormatter.formatDeviceInfo(cfg.copy(deviceTempContentOpt = 2), null, "52000"))
        assertEquals("", DeviceInfoFormatter.formatDeviceInfo(cfg.copy(deviceTempContentOpt = 3), Properties(), null))
    }

    private fun baseConfig(): DeviceInfoConfig = DeviceInfoConfig(
        showBatteryDetail = true,
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
}
