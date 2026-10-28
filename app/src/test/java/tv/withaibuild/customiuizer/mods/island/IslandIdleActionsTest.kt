package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class IslandIdleActionsTest {
    @Test fun idleDefaultsHaveNoImplicitTapAndExposeTheNotificationGesture() {
        val actions = IslandIdleActions.read(PrefMap())
        assertEquals(1, actions.tap)
        assertEquals(1, actions.gestures.left)
        assertEquals(2, actions.gestures.down)
        assertEquals(2, IslandIdleActions.defaultForKey("pref_key_system_island_idle_down"))
        assertEquals(87, IslandIdleActions.defaultForKey("pref_key_system_island_music_left"))
    }
    @Test fun idleMappingsAreIndependentAndRejectUnsupportedActions() {
        val prefs = PrefMap().apply {
            put("system_island_idle_left_action", 30)
            put("system_island_idle_tap_action", 8)
            put("system_island_idle_down_action", 999)
            put("system_island_music_down_action", 85)
        }
        val idle = IslandIdleActions.read(prefs)
        assertEquals(30, idle.gestures.left)
        assertEquals(8, idle.tap)
        assertEquals(1, idle.gestures.down)
        assertEquals(85, IslandMusicActions.read(prefs).down)
    }
    @Test fun contentRegionsRemainSymmetricAndTrackTheCameraDuringMorphs() {
        for (width in 100..352) {
            assertEquals(width - 40f, IslandContentLayout.leading(width.toFloat(), 40, 6f, 2f) +
                IslandContentLayout.trailing(width.toFloat(), 40, 6f, 2f), .001f)
        }
        assertEquals(.08f, IslandContentLayout.opticalShift(0, 100, 24), 0f)
        assertEquals(0f, IslandContentLayout.opticalShift(2, 2, 24), 0f)
    }
}
