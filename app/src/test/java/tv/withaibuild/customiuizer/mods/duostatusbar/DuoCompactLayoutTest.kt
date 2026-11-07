package tv.withaibuild.customiuizer.mods.duostatusbar

import org.junit.Assert.*
import org.junit.Test

class DuoCompactLayoutTest {
    @Test fun normalAndroidInputsSelectOnePrimaryAndTheNativeSecondary() {
        val inputs=intArrayOf(0,DuoNetworkState.WIFI_CONNECTED,DuoNetworkState.AIRPLANE,
            DuoNetworkState.WIFI_CONNECTED or DuoNetworkState.AIRPLANE)
        val expected=arrayOf(8 to 3,4 to 8,3 to 7,4 to 7)
        for(i in inputs.indices) {
            assertEquals(expected[i].first,DuoCompactLayout.primary(inputs[i]))
            assertEquals(expected[i].second,DuoCompactLayout.secondary(inputs[i]))
        }
        // Losing service is not evidence of the independent native SOS flag.
        assertEquals(3,DuoCompactLayout.secondary(DuoNetworkState.MOBILE_READY))
    }
    @Test fun nativeUpdatesLiquidSatelliteAndSosKeepTheirSeparatePrecedence() {
        for(update in 0..2) assertEquals(update,DuoCompactLayout.primary(update,true,true,true,true))
        assertEquals(6,DuoCompactLayout.primary(3,true,true,true,true))
        assertEquals(4,DuoCompactLayout.primary(3,true,true,true,false))
        assertEquals(5,DuoCompactLayout.primary(3,false,false,true,true))
        assertEquals(6,DuoCompactLayout.primary(3,true,false,true,false))
        assertEquals(7,DuoCompactLayout.secondary(6,true,true,true))
        for(primary in intArrayOf(3,7,8,9)) {
            assertEquals(3,DuoCompactLayout.secondary(primary,false,true,false))
            assertEquals(9,DuoCompactLayout.secondary(primary,false,true,true))
        }
        assertEquals(6,DuoCompactLayout.secondary(4,false,true,true))
        assertEquals(8,DuoCompactLayout.secondary(6,false,true,false))
    }
}
