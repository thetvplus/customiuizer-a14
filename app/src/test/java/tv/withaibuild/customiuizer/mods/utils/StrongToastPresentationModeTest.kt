package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeature
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeState
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File

class StrongToastPresentationModeTest {
    @Test
    fun preferenceValues_mapToBoundedModesAndLegacyMigration() {
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(0))
        assertEquals(StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT, StrongToastPresentationMode.fromPreference(1))
        assertEquals(StrongToastPresentationMode.HIDE, StrongToastPresentationMode.fromPreference(2))
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPresentationMode.fromPreference(3))
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPresentationMode.fromPreference(4))
    }

    @Test
    fun hooksRemainEnabledForLiveModeChanges() {
        for (mode in listOf("0", "1", "2", "3", "4")) {
            assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
                put("system_strong_toast_mode", mode)
            }))
        }
    }

    @Test
    fun matchModeGeometryRemainsBounded() {
        assertEquals(100, SystemUIStrongToastHooks.resolveMatchWindowHeightPx(100))
        assertEquals(80, SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, 20))
        assertTrue(SystemUIStrongToastHooks.matchModeHidesChin(100, 60))
    }

    @Test
    fun islandTopMarginAnchorsToCutoutAndAppliesSignedUserOffset() {
        assertEquals(36, SystemUIStrongToastHooks.resolveIslandTopMarginPx(36, 208, 141, 0))
        assertEquals(46, SystemUIStrongToastHooks.resolveIslandTopMarginPx(36, 208, 141, 10))
        assertEquals(26, SystemUIStrongToastHooks.resolveIslandTopMarginPx(36, 208, 141, -10))
        assertEquals(0, SystemUIStrongToastHooks.resolveIslandTopMarginPx(36, 208, 141, -100))
        assertEquals(33, SystemUIStrongToastHooks.resolveIslandTopMarginPx(-1, 208, 141, 0))
        assertEquals(8, SystemUIStrongToastHooks.resolveIslandTopMarginPx(-1, 0, 141, 8))
        assertEquals(0, SystemUIStrongToastHooks.resolveIslandTopMarginPx(-1, 0, 141, -8))
    }

    @Test
    fun islandWindowGrowsOnlyWhenTheAnchoredPillWouldNotFit() {
        assertEquals(208, SystemUIStrongToastHooks.resolveIslandWindowHeightPx(208, 141, 36))
        assertEquals(241, SystemUIStrongToastHooks.resolveIslandWindowHeightPx(208, 141, 100))
        assertEquals(-2, SystemUIStrongToastHooks.resolveIslandWindowHeightPx(-2, 141, 36))
    }

    @Test
    fun dynamicIslandReshapesTheRomRowInPlace() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        val branch = source.substringAfter("StrongToastPresentationMode.DYNAMIC_ISLAND -> {")
            .substringBefore("\n                            }")
        assertTrue(branch.contains("applyDynamicIslandCapsule"))
        assertTrue(branch.contains("setFitInsetsTypes(0)"))
        assertTrue(branch.contains("FLAG_LAYOUT_NO_LIMITS"))
        assertFalse(branch.contains("layoutParams.width = 1"))
        assertFalse(source.contains("Gravity.BOTTOM"))
        assertFalse(source.contains("forBottom("))
        assertFalse(source.contains("resolveBottom"))
        assertFalse(source.contains("DynamicIslandHost.shared"))
    }

    @Test
    fun islandOffsetPreferenceShiftStaysAlignedWithRuntime() {
        val xml = source("app/src/main/res/xml/prefs_system.xml")
        val block = xml.substringAfter("pref_key_system_strong_toast_island_offset")
            .substringBefore("pref_key_system_statusbar_iconsize")
        val shift = StrongToastRuntimeState.ISLAND_OFFSET_SHIFT
        assertTrue(block.contains("android:defaultValue=\"$shift\""))
        assertTrue(block.contains("miuizer:negativeShift=\"$shift\""))
        assertTrue(block.contains("miuizer:maxValue=\"${shift * 2}\""))
        assertTrue(block.contains("miuizer:showplus=\"true\""))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")!!).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found")
        }
    }
}
