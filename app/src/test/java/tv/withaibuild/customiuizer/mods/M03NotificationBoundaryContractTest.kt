package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** ROM bodies are inspected separately; these prevent reintroducing broad installation points. */
class M03NotificationBoundaryContractTest {
    @Test fun launchOptionsAndCoordinatorRulesKeepTheirNarrowOwners() {
        val shade = source("SystemUINotificationHooks.kt")
        val launch = source("NotificationClickOptionsHook.kt")
        assertFalse(shade.contains("clickNotifyOptions"))
        assertFalse(launch.contains("sendAndReturnResult"))
        assertFalse(Regex("\"attach\"\\s*,\\s*HookerClassHelper\\.DO_NOTHING").containsMatchIn(shade))
        assertTrue(shade.contains("\"onViewBound\", entry"))
        assertTrue(shade.contains("\"scheduleHistoryNotification\", entry"))
        assertTrue(shade.contains("\"shouldFilterOut\", entry"))
        assertTrue(shade.contains("\"setFold\", entry"))
        assertTrue(shade.contains("\"canAlertHeadsUpCommon\", entry"))
        assertFalse(shade.contains("\"canAlertAwakeCommon\", disableHeadsUpHook"))
        assertTrue(launch.contains("scope.within(launch) { chain.proceed() }"))
    }

    @Test fun nativeBackgroundResetAndSummaryRulesRemainActive() {
        val source = source("SystemColorizeNotificationHooks.kt")
        assertFalse(source.contains("\"setTint\""))
        assertFalse(source.contains("\"getCustomBackgroundColor\""))
        assertTrue(source.contains("tint.getInt(row) != palette.tint"))
        assertFalse(source.contains("makeNotificationGroupHeader"))
        assertTrue(source.contains("info.uid / 100000, info.packageName"))
    }

    private fun source(name: String): String {
        var root = File(java.lang.System.getProperty("user.dir")).absoluteFile
        while (!File(root, "app/src/main").isDirectory) root = root.parentFile ?: error("Missing repository")
        return File(root, "app/src/main/java/tv/withaibuild/customiuizer/mods/$name").readText()
    }
}
