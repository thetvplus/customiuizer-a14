package tv.withaibuild.customiuizer.mods.utils

import java.io.FileInputStream
import java.io.RandomAccessFile
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.Properties

private fun newMonitorDecimalFormat(pattern: String) =
    DecimalFormat(pattern, DecimalFormatSymbols(Locale.ROOT)).apply {
        roundingMode = RoundingMode.HALF_UP
        isGroupingUsed = false
    }

private val monitorOneDecimalFormat = ThreadLocal.withInitial { newMonitorDecimalFormat("0.0") }
private val monitorTwoDecimalFormat = ThreadLocal.withInitial { newMonitorDecimalFormat("0.00") }

internal fun formatMonitorOneDecimal(value: Float): String = monitorOneDecimalFormat.get()!!.format(value)

internal fun formatMonitorTwoDecimals(value: Float): String = monitorTwoDecimalFormat.get()!!.format(value)

/**
 * Pure formatting helpers for battery / device-temperature status bar text.
 *
 * This is intentionally framework-free so it can be unit-tested on the JVM and so
 * [DeviceInfoMonitor] keeps the Android lifecycle in one place.
 */
object DeviceInfoFormatter {

    /**
     * Parses a sysfs value, falling back to [fallback].
     *
     * `/sys/class/power_supply/battery/uevent` is a vendor surface: keys go missing and values
     * appear with signs, whitespace or units depending on the kernel. Parsing runs on the monitor
     * thread every two seconds, so a malformed line must produce a wrong-but-harmless reading
     * rather than an exception.
     */
    @JvmStatic
    internal fun parseSysfsInt(raw: String?, fallback: Int = 0): Int {
        if (raw.isNullOrEmpty()) return fallback
        return raw.trim().toIntOrNull() ?: fallback
    }

    /**
     * Reads the whole battery uevent file. Returns `null` when the file is missing, empty or
     * cannot be parsed.
     */
    @JvmStatic
    internal fun readBatteryProps(): Properties? {
        return try {
            FileInputStream("/sys/class/power_supply/battery/uevent").use { fis ->
                Properties().apply { load(fis) }
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    /**
     * Reads the CPU thermal zone temperature. Returns `null` when the zone is unknown or the file
     * cannot be read.
     */
    @JvmStatic
    internal fun readCpuTemp(thermalId: Int): String? {
        if (thermalId == -1) return null
        return try {
            RandomAccessFile("/sys/devices/virtual/thermal/thermal_zone$thermalId/temp", "r").use { it.readLine() }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    /**
     * Formats the battery temperature / current / power text for custom icon type 91.
     */
    @JvmStatic
    internal fun formatBatteryInfo(cfg: DeviceInfoConfig, props: Properties): String {
        val opt = cfg.batteryContentOpt
        var simpleTempVal = ""
        if (opt == 1 || opt == 4) {
            val tempVal = parseSysfsInt(props.getProperty("POWER_SUPPLY_TEMP"))
            simpleTempVal = if (cfg.batteryTempDecimal) {
                (tempVal / 10f).toString()
            } else {
                if (tempVal % 10 == 0) (tempVal / 10).toString() else (tempVal / 10f).toString()
            }
        }

        val currentRatio = if (cfg.batteryFixCurrentRatio) 1f else 1000f
        val curReadVal = parseSysfsInt(props.getProperty("POWER_SUPPLY_CURRENT_NOW"))
        var rawCurr = -1 * Math.round(curReadVal / currentRatio)

        var currVal = ""
        var currUnit = "mA"
        if (opt == 1 || opt == 3 || opt == 5) {
            if (cfg.batteryPositive) rawCurr = Math.abs(rawCurr)
            if (Math.abs(rawCurr) > 999) {
                currUnit = "A"
                currVal = formatMonitorTwoDecimals(rawCurr / 1000f)
            } else {
                currVal = rawCurr.toString()
            }
        }

        val hideUnit = cfg.batteryHideUnit
        val tempUnit = if (hideUnit == 1 || hideUnit == 2) "" else "℃"
        val powerUnit = if (hideUnit == 1 || hideUnit == 3) "" else "W"
        val finalCurrUnit = if (hideUnit == 1 || hideUnit == 3) "" else currUnit

        var simpleWatt = ""
        if (opt == 2 || opt == 4 || opt == 5) {
            val voltVal = parseSysfsInt(props.getProperty("POWER_SUPPLY_VOLTAGE_NOW")) / 1000f / 1000f
            simpleWatt = formatMonitorTwoDecimals(Math.abs(voltVal * rawCurr) / 1000)
        }

        val splitChar = if (cfg.batterySingleRow) " " else "\n"
        return when (opt) {
            1 -> if (cfg.batteryReverseOrder) {
                currVal + finalCurrUnit + splitChar + simpleTempVal + tempUnit
            } else {
                simpleTempVal + tempUnit + splitChar + currVal + finalCurrUnit
            }
            4 -> if (cfg.batteryReverseOrder) {
                simpleWatt + powerUnit + splitChar + simpleTempVal + tempUnit
            } else {
                simpleTempVal + tempUnit + splitChar + simpleWatt + powerUnit
            }
            2 -> simpleWatt + powerUnit
            5 -> if (cfg.batteryReverseOrder) {
                simpleWatt + powerUnit + splitChar + currVal + finalCurrUnit
            } else {
                currVal + finalCurrUnit + splitChar + simpleWatt + powerUnit
            }
            else -> currVal + finalCurrUnit
        }
    }

    /**
     * Preference `system_statusbar_showdevicetemperature_content` / `onoff_val`:
     * 1 = both, 2 = battery temperature, 3 (and any other) = CPU temperature.
     */
    @JvmStatic
    internal fun needsBatteryTemperature(contentOpt: Int): Boolean = contentOpt == 1 || contentOpt == 2

    @JvmStatic
    internal fun needsCpuTemperature(contentOpt: Int): Boolean = contentOpt != 2

    /**
     * Formats the device temperature text for custom icon type 92.
     *
     * Missing sources are omitted rather than rendered as 0.0. "Both" degrades to whichever
     * sensor is available; an empty result means nothing to show this tick.
     */
    @JvmStatic
    internal fun formatDeviceInfo(cfg: DeviceInfoConfig, props: Properties?, cpuProps: String?): String {
        val tempUnit = if (cfg.deviceTempHideUnit) "" else "℃"
        val splitChar = if (cfg.deviceTempSingleRow) " " else "\n"
        val batteryPart = props?.let {
            formatMonitorOneDecimal(parseSysfsInt(it.getProperty("POWER_SUPPLY_TEMP")) / 10f) + tempUnit
        }
        val cpuPart = cpuProps?.takeUnless { it.isEmpty() }?.let {
            formatMonitorOneDecimal(parseSysfsInt(it) / 1000f) + tempUnit
        }
        return when (cfg.deviceTempContentOpt) {
            2 -> batteryPart.orEmpty()
            1 -> formatBothDeviceTemps(batteryPart, cpuPart, cfg.deviceTempReverseOrder, splitChar)
            else -> cpuPart.orEmpty()
        }
    }

    private fun formatBothDeviceTemps(
        batteryPart: String?,
        cpuPart: String?,
        reverse: Boolean,
        splitChar: String
    ): String {
        if (batteryPart != null && cpuPart != null) {
            return if (reverse) cpuPart + splitChar + batteryPart else batteryPart + splitChar + cpuPart
        }
        return batteryPart ?: cpuPart.orEmpty()
    }
}

/**
 * Immutable snapshot of the user preferences the formatter needs.
 */
internal data class DeviceInfoConfig(
    val showBatteryDetail: Boolean,
    val showDeviceTemp: Boolean,
    val batteryInCharge: Boolean,
    val batteryTempDecimal: Boolean,
    val batteryFixCurrentRatio: Boolean,
    val batteryPositive: Boolean,
    val batterySingleRow: Boolean,
    val batteryReverseOrder: Boolean,
    val batteryHideUnit: Int,
    val deviceTempSingleRow: Boolean,
    val deviceTempReverseOrder: Boolean,
    val deviceTempHideUnit: Boolean,
    val batteryContentOpt: Int,
    val deviceTempContentOpt: Int
)
