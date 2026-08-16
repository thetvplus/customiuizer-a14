package tv.withaibuild.customiuizer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastRebootContractTest {

    private val mainModule = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
    private val systemUiBootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt")
    private val fastRebootBootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FastRebootBootstrap.kt")
    private val hooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt")
    private val actions = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt")
    private val preferences = source("app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt")

    @Test
    fun fastRebootRegistersOnceWithoutDependingOnCustomActions() {
        val customActionGate =
            "if (hasConfiguredGlobalActions()) GlobalActionSystemServerHooks.setupStatusBar(lpparam)"

        assertEquals(2, systemUiBootstrap.countOccurrences("setupFastRebootReceiver("))
        assertTrue(systemUiBootstrap.indexOf("setupFastRebootReceiver(") < systemUiBootstrap.indexOf(customActionGate))
        assertFalse(hooks.contains("setupFastRebootReceiver"))
        assertFalse(hooks.contains("\"fastRebootReceiver\""))
    }

    @Test
    fun customActionRegistrationConditionIsUnchanged() {
        val customActionGate =
            "if (hasConfiguredGlobalActions()) GlobalActionSystemServerHooks.setupStatusBar(lpparam)"

        assertEquals(1, systemUiBootstrap.countOccurrences(customActionGate))
    }

    @Test
    fun fastRebootActionRemainsStableAndDedicated() {
        val fastRebootAction = "GlobalActions.ACTION_PREFIX + \"FastReboot\""
        val customSetup = hooks.section(
            "fun setupStatusBar(lpparam: PackageReadyParam)",
            "if (hasConfiguredActionCode(28))"
        )
        val customReceiver = actions.section(
            "val mSBReceiver: BroadcastReceiver",
            "fun setupForegroundMonitor("
        )

        assertTrue(preferences.contains("Intent($fastRebootAction)"))
        assertTrue(fastRebootBootstrap.contains("IntentFilter($fastRebootAction)"))
        assertTrue(fastRebootBootstrap.contains("fastRebootReceiver"))
        assertFalse(fastRebootBootstrap.contains("GlobalActions.fastRebootReceiver"))
        assertFalse(customSetup.contains("\"FastReboot\""))
        assertFalse(customReceiver.contains("\"FastReboot\""))
    }

    @Test
    fun fastRebootBootstrapDoesNotReferenceGlobalActionsRuntime() {
        assertFalse("FastRebootBootstrap should not runtime-depend on GlobalActions executor",
            fastRebootBootstrap.contains("GlobalActions.fastRebootReceiver") ||
            fastRebootBootstrap.contains("GlobalActions.handleAction") ||
            fastRebootBootstrap.contains("GlobalActions.mSBReceiver")
        )
        assertTrue(fastRebootBootstrap.contains("GlobalActions.ACTION_PREFIX"))
        assertTrue(fastRebootBootstrap.contains("GlobalActions.BROADCAST_PERMISSION"))
    }

    @Test
    fun failurePromptsDistinguishDeliveryFromExecutionWithoutBinderClaims() {
        val sendSoftReboot = preferences.section(
            "private fun sendSoftReboot()",
            "private fun showFastRebootFailure("
        )

        assertTrue(sendSoftReboot.contains("GlobalActions.ACTION_UNHANDLED -> R.string.fast_reboot_not_received"))
        assertTrue(sendSoftReboot.contains("GlobalActions.ACTION_FAILED -> R.string.fast_reboot_failed"))
        assertFalse(sendSoftReboot.contains("showXposedDialog"))
        assertFalse(sendSoftReboot.contains("lsposed_not_connected"))
    }

    @Test
    fun customActionFilterSequenceIsUnchangedApartFromFastReboot() {
        val customSetup = hooks.section(
            "fun setupStatusBar(lpparam: PackageReadyParam)",
            "if (hasConfiguredActionCode(28))"
        )
        val actual = Regex("""intentfilter\.addAction\(GlobalActions\.ACTION_PREFIX \+ "([^"]+)"\)""")
            .findAll(customSetup)
            .map { it.groupValues[1] }
            .toList()
        val expected = listOf(
            "ExpandNotifications", "ExpandSettings", "OpenRecents", "OpenVolumeDialog",
            "ToggleGPS", "ToggleHotspot", "ToggleZenMode", "ToggleFlashlight",
            "ToggleNightMode", "ToggleWiFi", "ToggleBluetooth", "ToggleNFC",
            "ToggleSoundProfile", "ToggleAutoRotation", "ToggleMobileData",
            "ClearMemory", "ClearNotifications", "RestartSystemUI", "RestartLauncher",
            "RestartSecurityCenter", "FloatingWindow", "SwitchOneHanded", "ScrollToTop",
            "WakeUp", "GoToSleep", "LockDevice", "TakeScreenshot", "OpenPowerMenu",
            "VolumeUp", "VolumeDown", "GoBack", "LaunchIntent", "SaveLastMusicPausedTime"
        )

        assertEquals(expected, actual)
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return substring(startIndex, endIndex)
    }

    private fun String.countOccurrences(needle: String): Int {
        return windowed(needle.length).count { it == needle }
    }
}
