package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoPlacementTest {

    @Test
    fun dualRowsTempAtRightKeepsMonitorWorkWithoutControllerSlots() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = false,
            showDeviceTemp = true,
            dualRows = true,
            batteryAtRightPref = false,
            tempAtRightPref = true,
        )
        assertTrue(placement.hasDeviceInfoWork)
        assertTrue(placement.controllerManagedIconTypes.isEmpty())
        assertFalse(placement.tempAtRight)
        assertFalse(placement.tempAtLeft)
    }

    @Test
    fun dualRowsTempAtLeftKeepsMonitorWorkWithoutControllerSlots() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = false,
            showDeviceTemp = true,
            dualRows = true,
            batteryAtRightPref = false,
            tempAtRightPref = false,
        )
        assertTrue(placement.hasDeviceInfoWork)
        assertTrue(placement.controllerManagedIconTypes.isEmpty())
        assertFalse(placement.tempAtRight)
        assertFalse(placement.tempAtLeft)
    }

    @Test
    fun singleRowTempAtRightUsesControllerSlot() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = false,
            showDeviceTemp = true,
            dualRows = false,
            batteryAtRightPref = false,
            tempAtRightPref = true,
        )
        assertTrue(placement.hasDeviceInfoWork)
        assertEquals(listOf(92), placement.controllerManagedIconTypes)
        assertTrue(placement.tempAtRight)
        assertFalse(placement.tempAtLeft)
    }

    @Test
    fun singleRowTempAtLeftUsesControllerSlot() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = false,
            showDeviceTemp = true,
            dualRows = false,
            batteryAtRightPref = false,
            tempAtRightPref = false,
        )
        assertTrue(placement.hasDeviceInfoWork)
        assertEquals(listOf(92), placement.controllerManagedIconTypes)
        assertTrue(placement.tempAtLeft)
    }

    @Test
    fun dualRowsBatteryAtRightKeepsMonitorWork() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = true,
            showDeviceTemp = false,
            dualRows = true,
            batteryAtRightPref = true,
            tempAtRightPref = false,
        )
        assertTrue(placement.hasDeviceInfoWork)
        assertTrue(placement.controllerManagedIconTypes.isEmpty())
    }

    @Test
    fun batteryOnlySingleRowLeft() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = true,
            showDeviceTemp = false,
            dualRows = false,
            batteryAtRightPref = false,
            tempAtRightPref = false,
        )
        assertEquals(listOf(91), placement.controllerManagedIconTypes)
    }

    @Test
    fun tempOnlySingleRowRight() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = false,
            showDeviceTemp = true,
            dualRows = false,
            batteryAtRightPref = false,
            tempAtRightPref = true,
        )
        assertEquals(listOf(92), placement.controllerManagedIconTypes)
    }

    @Test
    fun batteryAndTempSingleRow() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = true,
            showDeviceTemp = true,
            dualRows = false,
            batteryAtRightPref = false,
            tempAtRightPref = false,
        )
        assertEquals(listOf(91, 92), placement.controllerManagedIconTypes)
        assertTrue(placement.hasDeviceInfoWork)
    }

    @Test
    fun dualRowsBatteryAndTempAtRightStillHasWork() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = true,
            showDeviceTemp = true,
            dualRows = true,
            batteryAtRightPref = true,
            tempAtRightPref = true,
        )
        assertTrue(placement.hasDeviceInfoWork)
        assertTrue(placement.controllerManagedIconTypes.isEmpty())
    }

    @Test
    fun disabledFeaturesInstallNothing() {
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = false,
            showDeviceTemp = false,
            dualRows = true,
            batteryAtRightPref = true,
            tempAtRightPref = true,
        )
        assertFalse(placement.hasDeviceInfoWork)
        assertTrue(placement.controllerManagedIconTypes.isEmpty())
    }

    @Test
    fun uniqueInstanceResolvesSingleMatch() {
        val args = arrayOf<Any?>("keep", 1, "match")
        assertEquals("match", findUniqueInstance(args) { it is String && it == "match" })
    }

    @Test
    fun uniqueInstanceRejectsDuplicates() {
        val args = arrayOf<Any?>("a", "b")
        assertNull(findUniqueInstance(args) { it is String })
    }

    @Test
    fun uniqueInstanceIgnoresNulls() {
        val args = arrayOf<Any?>(null, 7, null)
        assertEquals(7, findUniqueInstance(args) { it is Int })
    }
}
