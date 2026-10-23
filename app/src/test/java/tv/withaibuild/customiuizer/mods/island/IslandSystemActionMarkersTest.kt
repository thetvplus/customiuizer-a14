package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandSystemActionMarkersTest {
    @Test fun unselectedKindsInstallNothingAndReplacementsReleaseTheExactLeases() {
        val installed=mutableListOf<String>()
        val closed=mutableListOf<String>()
        val markers=IslandSystemActionMarkers { target,_ -> installed.add(target); AutoCloseable { closed.add(target) } }
        markers.update(emptySet())
        markers.update(setOf(IslandSystemKind.DND))
        markers.update(setOf(IslandSystemKind.WIFI))
        assertTrue(installed.isEmpty())
        val pair=setOf(IslandSystemKind.AIRPLANE,IslandSystemKind.WIFI)
        markers.update(pair); markers.update(pair)
        assertEquals(listOf("MiuiWifiTile"),installed)
        markers.update(setOf(IslandSystemKind.DND,IslandSystemKind.RINGER))
        assertEquals(listOf("MiuiWifiTile"),closed)
        assertEquals(setOf("MiuiWifiTile","setRingerMode","setRingerModeInternal","MuteTile"),installed.toSet())
        markers.close(); markers.close()
        assertEquals(installed.toSet(),closed.toSet())
        assertEquals(4,closed.size)
        markers.update(IslandSystemKind.entries.toSet())
        assertEquals(4,installed.size)
    }

    @Test fun capacityIsSixAndMissingRomTargetsAreNotRetriedOnUnchangedPreferences() {
        var attempts=0
        val missing=IslandSystemActionMarkers { _,_ -> attempts++; null }
        val all=IslandSystemKind.entries.toSet()
        missing.update(all); missing.update(all)
        assertEquals(6,attempts)
        missing.update(emptySet()); missing.update(all)
        assertEquals(12,attempts)
        missing.close()
    }
}
