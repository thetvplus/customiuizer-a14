package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.island.IslandEntranceStyle
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeature
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeState
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicReference

class StrongToastRuntimeConfigTest {
    @After
    fun tearDown() {
        StrongToastRuntimeState.instance?.let { PreferenceObserverRegistry.observers.remove(it.preferenceObserver) }
        StrongToastRuntimeState.instance = null
        StrongToastRuntimeState.installed = false
        SystemUIStrongToastHooks.snapshotRef = null
        SystemUIStrongToastHooks.installed = false
    }

    @Test
    fun nativeMotionSimplificationRefreshesWithoutChangingAnActiveEventSnapshot() {
        val prefs = PrefMap().apply { put("system_strong_toast_mode", "3") }
        val state = StrongToastRuntimeState.install(prefs)
        val first = state.snapshotRef.get()
        assertEquals(false, first.simplified)
        prefs.put("system_island_simplified", true)
        state.preferenceObserver.onChange("system_island_simplified")
        assertEquals(true, state.snapshotRef.get().simplified)
        assertEquals(false, first.simplified)
    }

    @Test
    fun entranceStyleChangesApplyToTheNextEventWithoutChangingTheCurrentEvent() {
        val prefs = PrefMap().apply { put("system_strong_toast_mode", "3") }
        val state = StrongToastRuntimeState.install(prefs)
        val first = state.snapshotRef.get()
        assertEquals(IslandEntranceStyle.TOP, first.entranceStyle)
        val event = Object()
        SystemUIStrongToastHooks.snapshotRef = state.snapshotRef
        SystemUIStrongToastHooks.storeSnapshot(event, first)

        prefs.put("system_island_entrance_style", "0")
        state.preferenceObserver.onChange("system_island_entrance_style")
        assertEquals(IslandEntranceStyle.CENTER, state.snapshotRef.get().entranceStyle)
        assertEquals(IslandEntranceStyle.TOP, SystemUIStrongToastHooks.resolveSnapshot(event)!!.entranceStyle)
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, state.snapshotRef.get().mode)
        assertSame(state, StrongToastRuntimeState.install(prefs))
    }

    @Test
    fun entranceStyleParsingMatchesModuleOptionsAndRejectsUnknownRestoredValues() {
        for ((stored, expected) in listOf(
            "0" to IslandEntranceStyle.CENTER,
            "1" to IslandEntranceStyle.TOP,
            1 to IslandEntranceStyle.TOP,
            -1 to IslandEntranceStyle.TOP,
            "2" to IslandEntranceStyle.TOP,
            "invalid" to IslandEntranceStyle.TOP,
            true to IslandEntranceStyle.TOP,
        )) {
            val prefs = PrefMap().apply { put("system_island_entrance_style", stored) }
            val native = StrongToastRuntimeState.buildSnapshot(prefs.getAll())
            assertEquals(expected, native.entranceStyle)
            assertEquals(tv.withaibuild.customiuizer.mods.island.IslandOptions.read(prefs).entranceStyle,
                native.entranceStyle)
        }
    }

    @Test
    fun snapshotIgnoresLegacyPositionAndOffset() {
        val snapshot = StrongToastRuntimeState.buildSnapshot(
            mapOf(
                "system_strong_toast_mode" to "3",
                "system_strong_toast_position" to "1",
                "system_strong_toast_bottom_offset" to "25",
            )
        )
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, snapshot.mode)
        assertEquals(
            StrongToastRuntimeState.buildSnapshot(mapOf("system_strong_toast_mode" to "3")),
            snapshot,
        )
        assertEquals(0, snapshot.islandOffsetDp)
    }

    @Test
    fun islandOffsetShiftMapsStoredMidpointToNeutralAndAcceptsSignedRange() {
        assertEquals(0, StrongToastRuntimeState.resolveIslandOffsetDp(null))
        assertEquals(0, StrongToastRuntimeState.resolveIslandOffsetDp(24))
        assertEquals(-24, StrongToastRuntimeState.resolveIslandOffsetDp(0))
        assertEquals(24, StrongToastRuntimeState.resolveIslandOffsetDp(48))
        assertEquals(8, StrongToastRuntimeState.resolveIslandOffsetDp("32"))
        assertEquals(-24, StrongToastRuntimeState.resolveIslandOffsetDp(-99))
        assertEquals(24, StrongToastRuntimeState.resolveIslandOffsetDp(99))
        assertEquals(
            12,
            StrongToastRuntimeState.buildSnapshot(
                mapOf(
                    "system_strong_toast_mode" to "3",
                    "system_strong_toast_island_offset" to 36,
                )
            ).islandOffsetDp,
        )
    }

    @Test
    fun islandOffsetChangesApplyLiveToTheNextEvent() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put("system_strong_toast_island_offset", 24)
        }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(0, state.snapshotRef.get().islandOffsetDp)

        prefs.put("system_strong_toast_island_offset", 30)
        state.preferenceObserver.onChange("system_strong_toast_island_offset")
        assertEquals(6, state.snapshotRef.get().islandOffsetDp)
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, state.snapshotRef.get().mode)
    }

    @Test
    fun compactHeightIsBoundedAndChangesOnlyTheNextFrozenNativeEvent() {
        val prefs = PrefMap().apply { put("system_strong_toast_mode", "3") }
        val state = StrongToastRuntimeState.install(prefs)
        val original = state.snapshotRef.get()
        assertEquals(40, original.islandHeightDp)
        val event = Object()
        SystemUIStrongToastHooks.snapshotRef = state.snapshotRef
        SystemUIStrongToastHooks.storeSnapshot(event, original)

        prefs.put("system_island_height", 44)
        state.preferenceObserver.onChange("system_island_height")
        assertEquals(44, state.snapshotRef.get().islandHeightDp)
        assertEquals(40, SystemUIStrongToastHooks.resolveSnapshot(event)!!.islandHeightDp)
        for ((stored, expected) in listOf(-100 to 38, 100 to 44, 41 to 41)) {
            prefs.put("system_island_height", stored)
            state.preferenceObserver.onChange("system_island_height")
            assertEquals(expected, state.snapshotRef.get().islandHeightDp)
        }
    }

    @Test
    fun manualCollapsedWidthsPublishLiveAndCannotInvertTheTwoForms() {
        val prefs = PrefMap().apply { put("system_strong_toast_mode", "3") }
        val state = StrongToastRuntimeState.install(prefs)
        val first = state.snapshotRef.get()
        assertTrue(first.automaticWidth)
        assertEquals(184, first.shortWidthDp)
        assertEquals(240, first.longWidthDp)
        prefs.put("system_island_width_auto", false)
        state.preferenceObserver.onChange("system_island_width_auto")
        assertEquals(false, state.snapshotRef.get().automaticWidth)
        prefs.put("system_island_width_short", 1000)
        state.preferenceObserver.onChange("system_island_width_short")
        assertEquals(224, state.snapshotRef.get().shortWidthDp)
        prefs.put("system_island_width_medium", -1)
        state.preferenceObserver.onChange("system_island_width_medium")
        assertEquals(224, state.snapshotRef.get().longWidthDp)
        assertEquals(184, first.shortWidthDp)
    }

    @Test
    fun modeChangesApplyLiveToTheNextEvent() {
        val prefs = PrefMap().apply { put("system_strong_toast_mode", "0") }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, state.snapshotRef.get().mode)

        prefs.put("system_strong_toast_mode", "3")
        state.preferenceObserver.onChange("system_strong_toast_mode")
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, state.snapshotRef.get().mode)
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(prefs))
    }

    @Test
    fun legacyModeFourMigratesToDynamicIsland() {
        assertEquals(
            StrongToastPresentationMode.DYNAMIC_ISLAND,
            StrongToastRuntimeState.buildSnapshot(mapOf("system_strong_toast_mode" to "4")).mode,
        )
    }

    @Test
    fun unrelatedPreferenceDoesNotReplaceSnapshot() {
        val state = StrongToastRuntimeState.install(PrefMap().apply { put("system_strong_toast_mode", "3") })
        val before = state.snapshotRef.get()
        state.preferenceObserver.onChange("system_unrelated")
        assertSame(before, state.snapshotRef.get())
    }

    @Test
    fun legacyPositionAndOffsetChangesDoNotReplaceSnapshot() {
        val prefs = PrefMap().apply { put("system_strong_toast_mode", "3") }
        val state = StrongToastRuntimeState.install(prefs)
        val before = state.snapshotRef.get()
        prefs.put("system_strong_toast_position", "1")
        prefs.put("system_strong_toast_bottom_offset", 25)
        state.preferenceObserver.onChange("system_strong_toast_position")
        state.preferenceObserver.onChange("system_strong_toast_bottom_offset")
        assertSame(before, state.snapshotRef.get())
    }

    @Test
    fun eventSnapshotRemainsImmutableAfterLiveChange() {
        val first = StrongToastRuntimeSnapshot(StrongToastPresentationMode.DYNAMIC_ISLAND)
        val second = StrongToastRuntimeSnapshot(StrongToastPresentationMode.HIDE)
        val view = Object()
        SystemUIStrongToastHooks.snapshotRef = AtomicReference(first)
        SystemUIStrongToastHooks.storeSnapshot(view, first)
        SystemUIStrongToastHooks.snapshotRef = AtomicReference(second)

        assertEquals(first, SystemUIStrongToastHooks.resolveSnapshot(view))
    }
}
