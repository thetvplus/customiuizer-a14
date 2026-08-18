package tv.withaibuild.customiuizer.mods

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleIdentityContractTest {

    private val globalActions = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt")
    )
    private val systemServerHooks = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt")
    )

    @Test
    fun toggleThisUsesIdentityAwareCommonSendAction() {
        val toggleThis = section(globalActions, "private fun toggleThis", "fun isMediaActionsAllowed")
        assertTrue(toggleThis.contains("GlobalActionToggles.broadcastAction(what)"))
        assertTrue(toggleThis.contains("commonSendAction(context, action)"))
        assertFalse(toggleThis.contains("sendBroadcast("))
        assertTrue(globalActions.contains("fun commonSendAction") && globalActions.contains("sendBroadcastWithIdentity"))
    }

    @Test
    fun allTwelveToggleBroadcastsAreRegisteredOnTheHostReceiver() {
        val expected = listOf(
            "ToggleWiFi",
            "ToggleBluetooth",
            "ToggleGPS",
            "ToggleNFC",
            "ToggleSoundProfile",
            "ToggleAutoBrightness",
            "ToggleAutoRotation",
            "ToggleFlashlight",
            "ToggleMobileData",
            "ToggleHotspot",
            "ToggleZenMode",
            "ToggleNightMode",
        )
        val hostReceivers = globalActions + "\n" + systemServerHooks
        for (action in expected) {
            assertTrue(
                "host receiver missing $action",
                hostReceivers.contains("+ \"$action\""),
            )
        }
    }

    private fun section(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        val endIndex = source.indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return source.substring(startIndex, endIndex)
    }
}
