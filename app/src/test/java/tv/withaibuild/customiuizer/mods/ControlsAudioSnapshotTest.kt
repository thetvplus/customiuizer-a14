package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
        MainModule.mPrefs.put("controls_fsg_width", 150)
        MainModule.mPrefs.put("controls_volumecursor_apps", setOf("com.ime"))
        MainModule.mPrefs.put("controls_volumecursor", true)
        MainModule.mPrefs.put("controls_fingerprintsuccess", "3")
        Controls.refreshControlsSnapshot()

        assertEquals(4, Controls.controlsConfig.backLongAction)
        assertEquals(150, Controls.controlsConfig.fsgWidth)
        assertTrue("com.ime" in Controls.controlsConfig.volumeCursorApps)
        assertTrue(Controls.controlsConfig.volumeCursorEnabled)
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

    @Test
    fun ignoredAppsLoseOnlyModernAndLegacyCallEventsWithoutMutatingTheCaller() {
        MainModule.mPrefs.put("system_ignorecalls_apps", setOf("com.player"))
        SystemAudioHooks.refreshAudioHapticsSnapshot()
        val requested = setOf(1, 6, 16, 32, 36)
        assertEquals(setOf(1, 16, 32), SystemAudioHooks.filterCallStateEvents("com.player", requested))
        assertEquals(setOf(1, 6, 16, 32, 36), requested)
        assertSame(requested, SystemAudioHooks.filterCallStateEvents("com.phone", requested))
        assertSame(requested, SystemAudioHooks.filterCallStateEvents(null, requested))
    }

    @Test
    fun unaffectedRegistrationsAreReusedAndCallOnlyRegistrationsBecomeEmpty() {
        MainModule.mPrefs.put("system_ignorecalls_apps", setOf("com.player"))
        SystemAudioHooks.refreshAudioHapticsSnapshot()
        val unrelated = setOf(1, 16, 32)
        assertSame(unrelated, SystemAudioHooks.filterCallStateEvents("com.player", unrelated))
        assertTrue(SystemAudioHooks.filterCallStateEvents("com.player", setOf(6, 36)).isEmpty())
        MainModule.mPrefs.put("system_ignorecalls_apps", emptySet<String>())
        SystemAudioHooks.refreshAudioHapticsSnapshot()
        val callEvents = setOf(6, 36)
        assertSame(callEvents, SystemAudioHooks.filterCallStateEvents("com.player", callEvents))
    }
}
