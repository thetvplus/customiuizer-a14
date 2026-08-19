package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalActionTogglesTest {

    @Test
    fun twelveToggleIdsMapOntoExistingToggleBroadcastNames() {
        assertEquals(12, GlobalActionToggles.idCount())
        val expected = listOf(
            1 to "ToggleWiFi",
            2 to "ToggleBluetooth",
            3 to "ToggleGPS",
            4 to "ToggleNFC",
            5 to "ToggleSoundProfile",
            6 to "ToggleAutoBrightness",
            7 to "ToggleAutoRotation",
            8 to "ToggleFlashlight",
            9 to "ToggleMobileData",
            10 to "ToggleHotspot",
            11 to "ToggleZenMode",
            12 to "ToggleNightMode",
        )
        for ((id, action) in expected) {
            assertEquals(action, GlobalActionToggles.broadcastAction(id))
        }
        assertNull(GlobalActionToggles.broadcastAction(0))
        assertNull(GlobalActionToggles.broadcastAction(13))
        for (id in 1..12) {
            assertTrue("toggle $id missing label", GlobalActionToggles.labelResId(id) != null)
        }
        assertNull(GlobalActionToggles.labelResId(0))
        assertNull(GlobalActionToggles.labelResId(13))
    }
}
