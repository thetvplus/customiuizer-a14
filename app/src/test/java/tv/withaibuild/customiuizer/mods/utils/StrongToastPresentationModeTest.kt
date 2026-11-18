package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.testsupport.repositorySource as source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeature
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeState
import tv.withaibuild.customiuizer.utils.PrefMap

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
    fun dynamicIslandDoesNotCreateOrReshapeAnyNativeForeheadWindow() {
        val hooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(hooks.contains("StrongToastPresentationMode.DYNAMIC_ISLAND -> Unit"))
        assertFalse(hooks.contains("installContentLayout"))
        assertFalse(hooks.contains("applyDynamicIslandCapsule"))
        assertFalse(hooks.contains("NativeWindow("))
        assertTrue(hooks.contains("snapshot.isDynamicIsland || snapshot.mode == StrongToastPresentationMode.HIDE"))
    }

    @Test
    fun islandOffsetPreferenceShiftStaysAlignedWithRuntime() {
        val xml = source("app/settings/prefs_system_island_layout.json")
        val block = xml.substringAfter("pref_key_system_strong_toast_island_offset")
            .substringBefore("pref_key_system_statusbar_iconsize")
        val shift = StrongToastRuntimeState.ISLAND_OFFSET_SHIFT
        assertTrue(block.contains("\"android:defaultValue\": \"$shift\""))
        assertTrue(block.contains("\"app:negativeShift\": \"$shift\""))
        assertTrue(block.contains("\"app:maxValue\": \"${shift * 2}\""))
        assertTrue(block.contains("\"app:showplus\": \"true\""))
    }

}
