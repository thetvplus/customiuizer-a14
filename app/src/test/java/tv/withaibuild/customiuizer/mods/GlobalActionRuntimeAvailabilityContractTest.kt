package tv.withaibuild.customiuizer.mods

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalActionRuntimeAvailabilityContractTest {

    private val arrays = Files.readString(Path.of("src/main/res/values/arrays.xml"))
    private val globalActions = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt")
    )
    private val installer = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt")
    )
    private val hooks = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt")
    )
    private val systemUiBootstrap = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt")
    )

    private val actionArrays = listOf(
        "global_actions_launcher",
        "global_actions_navbar",
        "global_actions_controls",
        "global_actions_statusbar",
        "global_lockscreen_actions",
        "global_launch_actions",
    )

    @Test
    fun advertisedActionsHaveRuntimeSpecs() {
        val known = GlobalActionRuntimeContract.ids()
        for (name in actionArrays) {
            for (id in integerItems("${name}_val")) {
                assertTrue("$name advertises action $id with no runtime spec", id in known)
            }
        }
    }

    @Test
    fun handlersCoverEveryRuntimeSpecExceptNone() {
        val handled = handledActionIds()
        for (spec in GlobalActionRuntimeContract.specs) {
            if (spec.id <= 1) continue
            assertTrue("spec ${spec.id} ${spec.name} has no execute handler", spec.id in handled)
        }
    }

    @Test
    fun phoneWindowManagerReceiverIsAlwaysInstalled() {
        val pwmIds = listOf(11, 14, 16, 24)
        for (id in pwmIds) {
            val spec = GlobalActionRuntimeContract.spec(id)!!
            assertEquals(GlobalActionOwner.SYSTEM_SERVER, spec.executionOwner)
            assertEquals("ALWAYS", spec.receiverInstallMode)
            assertTrue(spec.runtimeChangeSupported)
            assertEquals("NONE", spec.restartRequirement)
            assertTrue(hooks.contains("ACTION_PREFIX + \"${spec.transport}\""))
        }
        assertTrue(installer.contains("GlobalActionSystemServerHooks.setupGlobalActions(lpparam)"))
        assertFalse(installer.contains("hasConfiguredGlobalActions()"))
        assertFalse(installer.contains("if (prefReady &&"))
    }

    @Test
    fun systemUiBaseReceiverIsAlwaysInstalledAfterPrefReady() {
        assertFalse(systemUiBootstrap.contains("if (hasConfiguredGlobalActions()) GlobalActionSystemServerHooks.setupStatusBar(lpparam)"))
        assertTrue(systemUiBootstrap.contains("GlobalActionSystemServerHooks.setupStatusBar(lpparam)"))
        val statusBar = GlobalActionRuntimeContract.spec(2)!!
        assertEquals(GlobalActionOwner.SYSTEMUI, statusBar.executionOwner)
        assertEquals("ALWAYS", statusBar.receiverInstallMode)
        assertEquals("SYSTEMUI", statusBar.restartRequirement)
        assertTrue(statusBar.runtimeChangeSupported)
    }

    @Test
    fun triggerSpecsCoverEveryCustomActionKey() {
        assertEquals(
            tv.withaibuild.customiuizer.mods.utils.customActionKeys.toSet(),
            GlobalActionRuntimeContract.triggerKeys(),
        )
    }

    @Test
    fun systemServerTriggersAreAlwaysInstalled() {
        for (key in listOf("controls_powerdt", "controls_backlong", "controls_homelong", "controls_menulong")) {
            val spec = GlobalActionRuntimeContract.trigger(key)!!
            assertEquals(key, GlobalActionOwner.SYSTEM_SERVER, spec.triggerOwner)
            assertEquals(key, TriggerInstallMode.ALWAYS, spec.triggerInstallMode)
            assertEquals(key, ReceiverInstallMode.ALWAYS, spec.receiverInstallMode)
            assertEquals(key, "NONE", spec.restartRequirement)
            assertEquals(key, "according_to_action", spec.executionOwner)
        }
    }

    @Test
    fun launcherDoubleTapTriggerStaysOnLauncherRestart() {
        val spec = GlobalActionRuntimeContract.trigger("launcher_doubletap")!!
        assertEquals(GlobalActionOwner.LAUNCHER, spec.triggerOwner)
        assertEquals(TriggerInstallMode.PREF_GATED_AT_START, spec.triggerInstallMode)
        assertEquals("LAUNCHER|SYSTEMUI", spec.restartRequirement)
        assertEquals("according_to_action", spec.executionOwner)
    }

    @Test
    fun mediaActionsAreLocalAndNeedNoReceiver() {
        for (id in listOf(85, 87, 88)) {
            val spec = GlobalActionRuntimeContract.spec(id)!!
            assertEquals(GlobalActionOwner.LOCAL, spec.executionOwner)
            assertEquals(null, spec.transport)
            assertTrue(spec.runtimeChangeSupported)
            assertEquals("NONE", spec.restartRequirement)
        }
        assertTrue(globalActions.contains("action in 85..88"))
    }

    private fun handledActionIds(): Set<Int> {
        val resolved = section(globalActions, "private fun executeResolvedAction", "fun getActionResId")
        val ids = Regex("""^\s+(\d+) -> """, RegexOption.MULTILINE)
            .findAll(resolved)
            .map { it.groupValues[1].toInt() }
            .toMutableSet()
        val media = Regex("""action in (\d+)\.\.(\d+)""").find(globalActions)
        if (media != null) {
            ids.addAll(media.groupValues[1].toInt()..media.groupValues[2].toInt())
        }
        return ids
    }

    private fun integerItems(name: String): List<Int> {
        val start = arrays.indexOf("<integer-array name=\"$name\">")
        val end = arrays.indexOf("</integer-array>", start)
        check(start >= 0 && end > start) { "Missing integer-array $name" }
        return Regex("""<item>(.*?)</item>""")
            .findAll(arrays.substring(start, end))
            .map { it.groupValues[1].trim().toInt() }
            .toList()
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
