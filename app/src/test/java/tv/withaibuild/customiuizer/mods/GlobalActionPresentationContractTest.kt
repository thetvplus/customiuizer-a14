package tv.withaibuild.customiuizer.mods

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.R

class GlobalActionPresentationContractTest {

    private val arrays = Files.readString(Path.of("src/main/res/values/arrays.xml"))
    private val appHelper = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/utils/AppHelper.kt")
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
    fun advertisedActionsHavePresentationMapping() {
        for (name in actionArrays) {
            for (id in integerItems("${name}_val")) {
                if (GlobalActionPresentation.isParameterized(id)) continue
                assertNotEquals(
                    "$name action $id has no title mapping",
                    0,
                    GlobalActionPresentation.titleResId(id),
                )
            }
        }
    }

    @Test
    fun mediaActionsResolveToMediaLabels() {
        assertEquals(R.string.array_media_playpause, GlobalActionPresentation.titleResId(85))
        assertEquals(R.string.array_media_next, GlobalActionPresentation.titleResId(87))
        assertEquals(R.string.array_media_prev, GlobalActionPresentation.titleResId(88))
        assertEquals(R.string.array_media_playpause, GlobalActions.getActionResId(85))
        assertEquals(R.string.array_media_next, GlobalActions.getActionResId(87))
        assertEquals(R.string.array_media_prev, GlobalActions.getActionResId(88))
    }

    @Test
    fun parameterizedActionsKeepSpecialPresentationPath() {
        for (id in listOf(8, 9, 10, 20)) {
            assertEquals(0, GlobalActionPresentation.titleResId(id))
            assertEquals(0, GlobalActions.getActionResId(id))
        }
        assertTrue(appHelper.contains("GlobalActionToggles.labelResId(what)"))
    }

    @Test
    fun everyToggleIdHasALabel() {
        assertEquals(12, GlobalActionToggles.idCount())
        for (id in integerItems("global_toggles_val")) {
            assertTrue("toggle $id has no label", GlobalActionToggles.labelResId(id) != null)
        }
        assertEquals(
            R.string.system_statusbaricons_hotspot_title,
            GlobalActionToggles.labelResId(10),
        )
        assertEquals(R.string.system_statusbaricons_dnd_title, GlobalActionToggles.labelResId(11))
        assertEquals(R.string.various_calluibright_night_title, GlobalActionToggles.labelResId(12))
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
}
