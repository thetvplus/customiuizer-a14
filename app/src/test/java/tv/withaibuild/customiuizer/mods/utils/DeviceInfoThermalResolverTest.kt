package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class DeviceInfoThermalResolverTest {

    @Test
    fun parseThermalZoneIdsIncludesOddAndHighZones() {
        val ids = ModuleHelper.parseThermalZoneIds(
            arrayOf(
                "cooling_device0",
                "thermal_zone7",
                "thermal_zone45",
                "thermal_zone64",
                "thermal_zoneabc",
                "thermal_zone0"
            )
        )
        assertArrayEquals(intArrayOf(0, 7, 45, 64), ids)
    }

    @Test
    fun parseThermalZoneIdsHandlesNullAndEmpty() {
        assertArrayEquals(intArrayOf(), ModuleHelper.parseThermalZoneIds(null))
        assertArrayEquals(intArrayOf(), ModuleHelper.parseThermalZoneIds(emptyArray()))
    }

    @Test
    fun discoversOddAndHighCpuZones() {
        val result = ModuleHelper.scanForCpuThermalId(
            listZones = { intArrayOf(7, 45, 64) },
            readType = { index ->
                when (index) {
                    7 -> "battery"
                    45 -> "cpu-0-0-usr"
                    64 -> "gpu-usr"
                    else -> null
                }
            }
        )
        assertEquals(45, result)
    }

    @Test
    fun prefersExactCpuOverCpuss() {
        val result = ModuleHelper.scanForCpuThermalId(
            listZones = { intArrayOf(1, 64) },
            readType = { index ->
                if (index == 1) "cpuss-0-usr" else "cpu-1-0-usr"
            }
        )
        assertEquals(64, result)
    }

    @Test
    fun acceptsCpussWhenNoExactCpuExists() {
        val result = ModuleHelper.scanForCpuThermalId(
            listZones = { intArrayOf(12) },
            readType = { "cpuss-1-usr" }
        )
        assertEquals(12, result)
    }

    @Test
    fun rejectsNonCpuSensorTypes() {
        val rejected = listOf(
            "battery",
            "gpu-usr",
            "gpu-0-0-usr",
            "charger-skin-therm-adc",
            "quiet-therm-adc",
            "camera-therm",
            "modem0-pa0-usr",
            "skin-therm",
            "aoss-0-usr",
            "xo-therm"
        )
        for (type in rejected) {
            assertEquals(
                type,
                "NONE",
                ModuleHelper.rankCpuThermalType(type).name
            )
        }
        val result = ModuleHelper.scanForCpuThermalId(
            listZones = { IntArray(rejected.size) { it } },
            readType = { rejected[it] }
        )
        assertEquals(-1, result)
    }

    @Test
    fun unreadableZoneDoesNotStopScan() {
        val result = ModuleHelper.scanForCpuThermalId(
            listZones = { intArrayOf(3, 7) },
            readType = { index ->
                if (index == 3) throw RuntimeException("unreadable")
                "cpu-0"
            }
        )
        assertEquals(7, result)
    }

    @Test
    fun ordinaryListFailureFailOpensToNoMatch() {
        val result = ModuleHelper.scanForCpuThermalId(
            listZones = { throw IllegalStateException("list failed") },
            readType = { "cpu-0" }
        )
        assertEquals(-1, result)
    }

    @Test(expected = ThreadDeath::class)
    fun listZonesPropagatesThreadDeath() {
        ModuleHelper.scanForCpuThermalId(
            listZones = { throw ThreadDeath() },
            readType = { "cpu-0" }
        )
    }

    @Test(expected = OutOfMemoryError::class)
    fun listZonesPropagatesOutOfMemoryError() {
        ModuleHelper.scanForCpuThermalId(
            listZones = { throw OutOfMemoryError("oom") },
            readType = { "cpu-0" }
        )
    }

    @Test(expected = InternalError::class)
    fun listZonesPropagatesWrappedVirtualMachineError() {
        ModuleHelper.scanForCpuThermalId(
            listZones = { throw InvocationTargetException(InternalError("vm"), null) },
            readType = { "cpu-0" }
        )
    }

    @Test
    fun acceptedQualcommTypePrefixes() {
        assertEquals("EXACT_CPU", ModuleHelper.rankCpuThermalType("cpu-0").name)
        assertEquals("EXACT_CPU", ModuleHelper.rankCpuThermalType("cpu-0-0-usr").name)
        assertEquals("EXACT_CPU", ModuleHelper.rankCpuThermalType("cpu_big-0").name)
        assertEquals("EXACT_CPU", ModuleHelper.rankCpuThermalType("cpu_little-0").name)
        assertEquals("EXACT_CPU", ModuleHelper.rankCpuThermalType("cpu_prime-0").name)
        assertEquals("KNOWN_CPUSS", ModuleHelper.rankCpuThermalType("cpuss-0-usr").name)
        assertTrue(ModuleHelper.rankCpuThermalType("cpu-0").ordinal > ModuleHelper.rankCpuThermalType("cpuss-0-usr").ordinal)
    }
}
