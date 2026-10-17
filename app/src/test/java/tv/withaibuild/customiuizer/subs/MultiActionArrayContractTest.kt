package tv.withaibuild.customiuizer.subs

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.GlobalActionToggles

class MultiActionArrayContractTest {

    private val arrays = Files.readString(Path.of("src/main/res/values/arrays.xml"))
    private val globalActions = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt")
    )
    private val intentHelper = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionsIntentHelper.kt")
    )
    private val multiAction = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/subs/MultiAction.kt")
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
    fun advertisedActionIdsHaveHandlersExceptNone() {
        val handled = handledActionIds()
        for (name in actionArrays) {
            for (id in integerItems("${name}_val")) {
                if (id <= 1) continue
                assertTrue(
                    "$name advertises action $id with no handler",
                    id in handled,
                )
            }
        }
    }

    @Test
    fun actionArraysHaveMatchingEntryAndValueCounts() {
        for (name in actionArrays) {
            assertEquals(
                name,
                stringItemCount(name),
                integerItems("${name}_val").size,
            )
        }
    }

    @Test
    fun lockscreenArrayIncludesToggleAlignedWithValueTen() {
        assertEquals(6, stringItemCount("global_lockscreen_actions"))
        assertEquals(
            listOf(1, 12, 8, 9, 20, 10),
            integerItems("global_lockscreen_actions_val"),
        )
        val entries = items("global_lockscreen_actions", "string-array")
        assertEquals("@string/array_global_actions_toggle", entries.last())
    }

    @Test
    fun launcherAdvertisesTheExpectedActionMatrix() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 17, 12, 26, 8, 9, 20, 10, 13, 14, 22, 23),
            integerItems("global_actions_launcher_val"),
        )
    }

    @Test
    fun parameterizedActionsHaveUiAndMatchingPreferenceSuffixes() {
        val controls = section(multiAction, "private fun updateControls", "override fun onActivityResult")
        assertTrue(controls.contains("8 -> apps.visibility"))
        assertTrue(controls.contains("9 -> shortcuts.visibility"))
        assertTrue(controls.contains("10 -> toggles.visibility"))
        assertTrue(controls.contains("20 -> activities.visibility"))

        assertTrue(multiAction.contains("mKey + \"_app\""))
        assertTrue(multiAction.contains("mKey + \"_shortcut\""))
        assertTrue(multiAction.contains("mKey + \"_activity\""))
        assertTrue(multiAction.contains("mKey + \"_toggle\""))
        assertTrue(multiAction.contains("mKey + \"_app_user\""))
        assertTrue(multiAction.contains("mKey + \"_activity_user\""))
        assertTrue(multiAction.contains("mKey + \"_shortcut_intent\""))

        assertTrue(intentHelper.contains("IntentType.APP -> \"_app\""))
        assertTrue(intentHelper.contains("IntentType.ACTIVITY -> \"_activity\""))
        assertTrue(intentHelper.contains("IntentType.SHORTCUT -> \"_shortcut_intent\""))
        assertTrue(globalActions.contains("key + \"_toggle\""))
    }

    @Test
    fun toggleValuesHaveImplementations() {
        val toggleValues = integerItems("global_toggles_val")
        assertEquals(stringItemCount("global_toggles"), toggleValues.size)
        val mapping = Files.readString(
            Path.of("src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionToggles.kt")
        )
        assertEquals(12, toggleValues.size)
        assertTrue(mapping.contains("\"WiFi\""))
        assertTrue(mapping.contains("\"NightMode\""))
        for (id in toggleValues) {
            assertTrue("toggle $id has no mapping", GlobalActionToggles.broadcastAction(id) != null)
        }
    }

    private fun handledActionIds(): Set<Int> {
        val resolved = section(globalActions, "private fun executeResolvedAction", "fun getActionResId")
        val ids = Regex("""^\s+(\d+) -> """, RegexOption.MULTILINE)
            .findAll(resolved)
            .map { it.groupValues[1].toInt() }
            .toMutableSet()
        val media = Regex("""action in (\d+)\.\.(\d+)""").find(globalActions)
        if (media != null) {
            val start = media.groupValues[1].toInt()
            val end = media.groupValues[2].toInt()
            ids.addAll(start..end)
        }
        return ids
    }

    private fun stringItemCount(name: String): Int = items(name, "string-array").size

    private fun integerItems(name: String): List<Int> =
        items(name, "integer-array").map { it.trim().toInt() }

    private fun items(name: String, tag: String): List<String> {
        val start = arrays.indexOf("<$tag name=\"$name\">")
        val end = arrays.indexOf("</$tag>", start)
        check(start >= 0 && end > start) { "Missing $tag $name" }
        return Regex("""<item>(.*?)</item>""")
            .findAll(arrays.substring(start, end))
            .map { it.groupValues[1] }
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
