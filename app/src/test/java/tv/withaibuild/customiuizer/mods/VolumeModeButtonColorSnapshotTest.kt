package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import java.util.concurrent.atomic.AtomicReference

class VolumeModeButtonColorSnapshotTest {

    private val enabledKey = "system_volume_mode_button_colors"
    private val backgroundKey = "system_volume_mode_button_background_color"
    private val iconKey = "system_volume_mode_button_icon_color"
    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
        setVolumeModeButtonObserverRegistered(false)
        SystemUIControlCenterHooks.refreshVolumeModeButtonColorSnapshot()
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
        SystemUIControlCenterHooks.refreshVolumeModeButtonColorSnapshot()
    }

    private fun setVolumeModeButtonObserverRegistered(value: Boolean) {
        val field = SystemUIControlCenterHooks::class.java.getDeclaredField("volumeModeButtonObserverRegistered")
        field.isAccessible = true
        field.set(null, value)
    }

    @Test
    fun defaultSnapshotMatchesPreferenceDefaults() {
        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        assertFalse(snapshot.enabled)
        assertEquals(0xffffffff.toInt(), snapshot.backgroundColor)
        assertEquals(0xff277af7.toInt(), snapshot.iconColor)
    }

    @Test
    fun backgroundColorLiveRefreshUpdatesSnapshot() {
        MainModule.mPrefs.put(enabledKey, true)
        MainModule.mPrefs.put(backgroundKey, 0xff123456.toInt())
        MainModule.mPrefs.put(iconKey, 0xff654321.toInt())
        SystemUIControlCenterHooks.onVolumeModeButtonColorPreferenceChanged(backgroundKey)

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        assertTrue(snapshot.enabled)
        assertEquals(0xff123456.toInt(), snapshot.backgroundColor)
        assertEquals(0xff654321.toInt(), snapshot.iconColor)
    }

    @Test
    fun iconColorLiveRefreshUpdatesSnapshot() {
        MainModule.mPrefs.put(enabledKey, true)
        MainModule.mPrefs.put(backgroundKey, 0xffabcdef.toInt())
        MainModule.mPrefs.put(iconKey, 0xfffedcba.toInt())
        SystemUIControlCenterHooks.onVolumeModeButtonColorPreferenceChanged(iconKey)

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        assertEquals(0xffabcdef.toInt(), snapshot.backgroundColor)
        assertEquals(0xfffedcba.toInt(), snapshot.iconColor)
    }

    @Test
    fun enabledKeyLiveRefreshUpdatesSnapshot() {
        MainModule.mPrefs.put(enabledKey, true)
        MainModule.mPrefs.put(backgroundKey, 0xff111111.toInt())
        MainModule.mPrefs.put(iconKey, 0xff222222.toInt())
        SystemUIControlCenterHooks.onVolumeModeButtonColorPreferenceChanged(enabledKey)

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        assertTrue(snapshot.enabled)
        assertEquals(0xff111111.toInt(), snapshot.backgroundColor)
        assertEquals(0xff222222.toInt(), snapshot.iconColor)
    }

    @Test
    fun nullKeyRefreshesAll() {
        MainModule.mPrefs.put(enabledKey, true)
        MainModule.mPrefs.put(backgroundKey, 0xff333333.toInt())
        MainModule.mPrefs.put(iconKey, 0xff444444.toInt())
        SystemUIControlCenterHooks.onVolumeModeButtonColorPreferenceChanged(null)

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        assertTrue(snapshot.enabled)
        assertEquals(0xff333333.toInt(), snapshot.backgroundColor)
        assertEquals(0xff444444.toInt(), snapshot.iconColor)
    }

    @Test
    fun unrelatedKeyDoesNotRefreshSnapshot() {
        MainModule.mPrefs.put(enabledKey, true)
        MainModule.mPrefs.put(backgroundKey, 0xff555555.toInt())
        MainModule.mPrefs.put(iconKey, 0xff666666.toInt())
        SystemUIControlCenterHooks.onVolumeModeButtonColorPreferenceChanged(backgroundKey)

        MainModule.mPrefs.put(backgroundKey, 0xffffff00.toInt())
        SystemUIControlCenterHooks.onVolumeModeButtonColorPreferenceChanged("system_some_other_key")

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        assertEquals(0xff555555.toInt(), snapshot.backgroundColor)
        assertEquals(0xff666666.toInt(), snapshot.iconColor)
    }

    @Test
    fun snapshotIsVisibleAcrossThreads() {
        MainModule.mPrefs.put(enabledKey, true)
        MainModule.mPrefs.put(backgroundKey, 0xff777777.toInt())
        MainModule.mPrefs.put(iconKey, 0xff888888.toInt())

        val observed = AtomicReference<SystemUIControlCenterHooks.VolumeModeButtonColorSnapshot>()
        val thread = Thread {
            SystemUIControlCenterHooks.refreshVolumeModeButtonColorSnapshot()
            observed.set(SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot())
        }
        thread.start()
        thread.join()

        val snapshot = observed.get()
        assertTrue(snapshot.enabled)
        assertEquals(0xff777777.toInt(), snapshot.backgroundColor)
        assertEquals(0xff888888.toInt(), snapshot.iconColor)
    }

    @Test
    fun callbackReadsAllFieldsFromOneSnapshot() {
        MainModule.mPrefs.put(enabledKey, true)
        MainModule.mPrefs.put(backgroundKey, 0xff999999.toInt())
        MainModule.mPrefs.put(iconKey, 0xffaaaaaa.toInt())
        SystemUIControlCenterHooks.refreshVolumeModeButtonColorSnapshot()

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        val enabledRead = snapshot.enabled
        val backgroundRead = snapshot.backgroundColor
        val iconRead = snapshot.iconColor

        assertTrue(enabledRead)
        assertEquals(0xff999999.toInt(), backgroundRead)
        assertEquals(0xffaaaaaa.toInt(), iconRead)
        assertEquals(backgroundRead, snapshot.backgroundColor)
        assertEquals(iconRead, snapshot.iconColor)
    }

    @Test
    fun refreshReplacesSnapshot() {
        MainModule.mPrefs.put(enabledKey, false)
        MainModule.mPrefs.put(backgroundKey, 0xffbbbbbb.toInt())
        MainModule.mPrefs.put(iconKey, 0xffcccccc.toInt())
        SystemUIControlCenterHooks.refreshVolumeModeButtonColorSnapshot()

        val first = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()

        MainModule.mPrefs.put(enabledKey, true)
        MainModule.mPrefs.put(backgroundKey, 0xffdddddd.toInt())
        MainModule.mPrefs.put(iconKey, 0xffeeeeee.toInt())
        SystemUIControlCenterHooks.refreshVolumeModeButtonColorSnapshot()

        val second = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        assertNotSame(first, second)
        assertTrue(second.enabled)
        assertEquals(0xffdddddd.toInt(), second.backgroundColor)
        assertEquals(0xffeeeeee.toInt(), second.iconColor)
    }

    @Test
    fun disabledSnapshotKeepsConfiguredColorsButIsNotApplied() {
        MainModule.mPrefs.put(enabledKey, false)
        MainModule.mPrefs.put(backgroundKey, 0xff123456.toInt())
        MainModule.mPrefs.put(iconKey, 0xff654321.toInt())
        SystemUIControlCenterHooks.refreshVolumeModeButtonColorSnapshot()

        val snapshot = SystemUIControlCenterHooks.getVolumeModeButtonColorSnapshot()
        assertFalse(snapshot.enabled)
        assertEquals(0xff123456.toInt(), snapshot.backgroundColor)
        assertEquals(0xff654321.toInt(), snapshot.iconColor)
    }

    @Test
    fun installVolumeModeButtonColorSnapshotRegistersObserverOnce() {
        val before = PreferenceObserverRegistry.observers.size

        SystemUIControlCenterHooks.installVolumeModeButtonColorSnapshot()
        val afterFirst = PreferenceObserverRegistry.observers.size
        assertEquals("observer must be registered exactly once", before + 1, afterFirst)

        SystemUIControlCenterHooks.installVolumeModeButtonColorSnapshot()
        val afterSecond = PreferenceObserverRegistry.observers.size
        assertEquals("second install must not register another observer", before + 1, afterSecond)
    }
}
