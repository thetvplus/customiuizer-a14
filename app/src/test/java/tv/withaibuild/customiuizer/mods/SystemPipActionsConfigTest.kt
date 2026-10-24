package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SystemPipActionsConfigTest {
    @Before fun reset() {
        MainModule.mPrefs.clear()
        val config = Class.forName("tv.withaibuild.customiuizer.mods.utils.GlobalActionConfigKt")
        config.getDeclaredField("customActionsReady").apply { isAccessible = true }.set(null, false)
        config.getDeclaredField("customActionCodeMap").apply { isAccessible = true }.set(null, null)
        config.getDeclaredField("customToggleMap").apply { isAccessible = true }.set(null, null)
    }
    @After fun cleanup() = reset()

    @Test fun anUnconfiguredActionOrInactiveIslandNeedsNoPipHooks() {
        val prefs = MainModule.mPrefs
        prefs.put("system_island_music_down_action", 33)
        assertFalse(SystemPipActions.selected(prefs, 33))
        prefs.put("system_strong_toast_mode", "3")
        assertFalse(SystemPipActions.selected(prefs, 33))
        prefs.put("system_island_media", true)
        assertTrue(SystemPipActions.selected(prefs, 33))
        assertFalse(SystemPipActions.selected(prefs, 34))
        prefs.put("system_island_music_down_action", 34)
        assertFalse(SystemPipActions.selected(prefs, 33))
        assertTrue(SystemPipActions.selected(prefs, 34))
    }

    @Test fun aGlobalGestureWorksWithTheIslandDisabled() {
        val prefs = MainModule.mPrefs
        prefs.put("controls_powerdt_action", 34)
        assertTrue(SystemPipActions.selected(prefs, 34))
        assertFalse(SystemPipActions.selected(prefs, 33))
        assertFalse(SystemPipActions.request(34)) // No native owner has been installed.
    }
}
