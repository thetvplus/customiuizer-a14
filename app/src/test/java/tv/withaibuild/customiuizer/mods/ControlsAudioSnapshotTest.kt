package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule

/**
 * Behavioral tests for Controls and audio/haptics preference snapshots.
 */
class ControlsAudioSnapshotTest {

    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
        Controls.refreshControlsSnapshot()
        SystemAudioHooks.refreshAudioHapticsSnapshot()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            MainModule.mPrefs.replaceSnapshot(savedPrefs)
        }
        Controls.refreshControlsSnapshot()
        SystemAudioHooks.refreshAudioHapticsSnapshot()
    }

    @Test
    fun controlsSnapshotRebuildsWithoutHookReinstall() {
        MainModule.mPrefs.put("controls_backlong_action", 1)
        MainModule.mPrefs.put("controls_fsg_width", 100)
        MainModule.mPrefs.put("controls_volumecursor_apps", emptySet<String>())
        Controls.refreshControlsSnapshot()
        assertEquals(1, Controls.controlsConfig.backLongAction)
        assertEquals(100, Controls.controlsConfig.fsgWidth)
        assertFalse("com.ime" in Controls.controlsConfig.volumeCursorApps)

        MainModule.mPrefs.put("controls_backlong_action", 4)
        MainModule.mPrefs.put("controls_fsg_width", 80)
        MainModule.mPrefs.put("controls_volumecursor_apps", setOf("com.ime"))
        MainModule.mPrefs.put("controls_fingerprintsuccess", "3")
        Controls.refreshControlsSnapshot()

        assertEquals(4, Controls.controlsConfig.backLongAction)
        assertEquals(80, Controls.controlsConfig.fsgWidth)
        assertTrue("com.ime" in Controls.controlsConfig.volumeCursorApps)
        assertEquals(3, Controls.controlsConfig.fingerprintSuccess)
    }

    @Test
    fun audioHapticsSnapshotRebuildsWithoutHookReinstall() {
        MainModule.mPrefs.put("system_qshaptics", "1")
        MainModule.mPrefs.put("system_vibration", "2")
        MainModule.mPrefs.put("system_vibration_apps", setOf("com.vibe"))
        MainModule.mPrefs.put("system_vibration_amp_ringer", 50)
        SystemAudioHooks.refreshAudioHapticsSnapshot()

        assertEquals(1, SystemAudioHooks.audioHapticsConfig.qsHaptics)
        assertEquals(2, SystemAudioHooks.audioHapticsConfig.vibrationMode)
        assertTrue("com.vibe" in SystemAudioHooks.audioHapticsConfig.vibrationApps)
        assertEquals(0.5f, SystemAudioHooks.audioHapticsConfig.ampRinger, 0.001f)

        MainModule.mPrefs.put("system_qshaptics", "3")
        MainModule.mPrefs.put("system_vibration_amp_ringer", 100)
        SystemAudioHooks.refreshAudioHapticsSnapshot()

        assertEquals(3, SystemAudioHooks.audioHapticsConfig.qsHaptics)
        assertEquals(1.0f, SystemAudioHooks.audioHapticsConfig.ampRinger, 0.001f)
    }
}
