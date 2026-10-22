package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaintenanceStateCodecTest {
    @Test
    fun existingRestorableStatesKeepTheirOrderAndMeaning() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4), parseMaintenanceStates("0,1,2,3,4"))
        assertArrayEquals(intArrayOf(2, 0, 2), parseMaintenanceStates("2,0,2"))
    }

    @Test
    fun corruptTokensCannotShiftRestorationOntoAnotherComponent() {
        for (raw in listOf("0,broken,2", "broken,0,2", "0,2,broken", "0,,2", "0,2,")) {
            assertNull("reject the whole positional snapshot: $raw", parseMaintenanceStates(raw))
        }
    }

    @Test
    fun missingOverflowAndUnsupportedStatesAreRejected() {
        for (raw in listOf(null, "", " ", "0,-1", "5", "999999999999", "0, 2")) {
            assertNull("invalid restore state: $raw", parseMaintenanceStates(raw))
        }
    }

    @Test
    fun snapshotBoundMatchesThePrivilegedBridgeLimit() {
        assertArrayEquals(IntArray(32), parseMaintenanceStates(List(32) { "0" }.joinToString(",")))
        assertNull(parseMaintenanceStates(List(33) { "0" }.joinToString(",")))
    }
}
