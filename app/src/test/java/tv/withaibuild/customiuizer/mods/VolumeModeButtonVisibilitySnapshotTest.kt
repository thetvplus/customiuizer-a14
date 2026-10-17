package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import java.util.concurrent.atomic.AtomicReference

class VolumeModeButtonVisibilitySnapshotTest {

    private val hideMuteKey = "system_volume_hide_mute_shortcut"
    private val hideDndKey = "system_volume_hide_dnd_shortcut"
    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
        setVolumeModeButtonObserverRegistered(false)
        SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            MainModule.mPrefs.replaceSnapshot(savedPrefs)
        } else {
            MainModule.mPrefs.clear()
        }
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
        setVolumeModeButtonObserverRegistered(false)
        SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()
    }

    private fun setVolumeModeButtonObserverRegistered(value: Boolean) {
        val field = SystemUIControlCenterHooks::class.java.getDeclaredField("volumeModeButtonObserverRegistered")
        field.isAccessible = true
        field.set(null, value)
    }

    @Test
    fun defaultSnapshotMatchesPreferenceDefaults() {
        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()
        assertFalse(snapshot.hideMute)
        assertFalse(snapshot.hideDnd)
    }

    @Test
    fun hideMuteLiveRefreshUpdatesSnapshot() {
        MainModule.mPrefs.put(hideMuteKey, true)
        MainModule.mPrefs.put(hideDndKey, false)
        SystemUIControlCenterHooks.onVolumeModeButtonVisibilityPreferenceChanged(hideMuteKey)

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()
        assertTrue(snapshot.hideMute)
        assertFalse(snapshot.hideDnd)
    }

    @Test
    fun hideDndLiveRefreshUpdatesSnapshot() {
        MainModule.mPrefs.put(hideMuteKey, false)
        MainModule.mPrefs.put(hideDndKey, true)
        SystemUIControlCenterHooks.onVolumeModeButtonVisibilityPreferenceChanged(hideDndKey)

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()
        assertFalse(snapshot.hideMute)
        assertTrue(snapshot.hideDnd)
    }

    @Test
    fun enabledBothLiveRefreshUpdatesSnapshot() {
        MainModule.mPrefs.put(hideMuteKey, true)
        MainModule.mPrefs.put(hideDndKey, true)
        SystemUIControlCenterHooks.onVolumeModeButtonVisibilityPreferenceChanged(hideMuteKey)

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()
        assertTrue(snapshot.hideMute)
        assertTrue(snapshot.hideDnd)
    }

    @Test
    fun nullKeyRefreshesAll() {
        MainModule.mPrefs.put(hideMuteKey, true)
        MainModule.mPrefs.put(hideDndKey, true)
        SystemUIControlCenterHooks.onVolumeModeButtonVisibilityPreferenceChanged(null)

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()
        assertTrue(snapshot.hideMute)
        assertTrue(snapshot.hideDnd)
    }

    @Test
    fun unrelatedKeyDoesNotRefreshSnapshot() {
        MainModule.mPrefs.put(hideMuteKey, true)
        MainModule.mPrefs.put(hideDndKey, false)
        SystemUIControlCenterHooks.onVolumeModeButtonVisibilityPreferenceChanged(hideMuteKey)

        MainModule.mPrefs.put(hideMuteKey, false)
        MainModule.mPrefs.put(hideDndKey, true)
        SystemUIControlCenterHooks.onVolumeModeButtonVisibilityPreferenceChanged("system_some_other_key")

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()
        assertTrue(snapshot.hideMute)
        assertFalse(snapshot.hideDnd)
    }

    @Test
    fun snapshotIsVisibleAcrossThreads() {
        MainModule.mPrefs.put(hideMuteKey, true)
        MainModule.mPrefs.put(hideDndKey, false)

        val observed = AtomicReference<SystemUIControlCenterHooks.VolumeModeButtonVisibilitySnapshot>()
        val thread = Thread {
            SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()
            observed.set(SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot())
        }
        thread.start()
        thread.join()

        val snapshot = observed.get()
        assertTrue(snapshot.hideMute)
        assertFalse(snapshot.hideDnd)
    }

    @Test
    fun callbackReadsAllFieldsFromOneSnapshot() {
        MainModule.mPrefs.put(hideMuteKey, true)
        MainModule.mPrefs.put(hideDndKey, true)
        SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()
        val hideMuteRead = snapshot.hideMute
        val hideDndRead = snapshot.hideDnd

        assertTrue(hideMuteRead)
        assertTrue(hideDndRead)
        assertSame(hideMuteRead, snapshot.hideMute)
        assertSame(hideDndRead, snapshot.hideDnd)
    }

    @Test
    fun refreshReplacesSnapshot() {
        MainModule.mPrefs.put(hideMuteKey, false)
        MainModule.mPrefs.put(hideDndKey, false)
        SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()

        val first = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()

        MainModule.mPrefs.put(hideMuteKey, true)
        MainModule.mPrefs.put(hideDndKey, true)
        SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()

        val second = SystemUIControlCenterHooks.getVolumeModeButtonVisibilitySnapshot()
        assertNotSame(first, second)
        assertTrue(second.hideMute)
        assertTrue(second.hideDnd)
    }

    @Test
    fun installVolumeModeButtonVisibilitySnapshotRegistersObserverOnce() {
        val before = PreferenceObserverRegistry.observers.size

        SystemUIControlCenterHooks.installVolumeModeButtonVisibilitySnapshot()
        val afterFirst = PreferenceObserverRegistry.observers.size
        assertEquals("observer must be registered exactly once", before + 1, afterFirst)

        SystemUIControlCenterHooks.installVolumeModeButtonVisibilitySnapshot()
        val afterSecond = PreferenceObserverRegistry.observers.size
        assertEquals("second install must not register another observer", before + 1, afterSecond)
    }

    @Test
    fun roleFromIsZenFalseIsMute() {
        assertEquals(
            SystemUIControlCenterHooks.VolumeModeButtonRole.MUTE,
            SystemUIControlCenterHooks.volumeModeButtonRoleFromIsZen(false)
        )
    }

    @Test
    fun roleFromIsZenTrueIsDnd() {
        assertEquals(
            SystemUIControlCenterHooks.VolumeModeButtonRole.DND,
            SystemUIControlCenterHooks.volumeModeButtonRoleFromIsZen(true)
        )
    }

    @Test
    fun roleFromMissingIsZenIsUnknown() {
        assertEquals(
            SystemUIControlCenterHooks.VolumeModeButtonRole.UNKNOWN,
            SystemUIControlCenterHooks.volumeModeButtonRoleFromIsZen(null)
        )
    }
}
