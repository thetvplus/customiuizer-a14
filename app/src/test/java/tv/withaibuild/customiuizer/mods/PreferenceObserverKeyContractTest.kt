package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Behavioral tests for the preference observer null-key contract used by the P1-B snapshots.
 */
class PreferenceObserverKeyContractTest {

    @Before
    fun setUp() {
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
        MainModule.mPrefs.clear()
        setObserverRegisteredFlag(SystemUILockScreenHooks, "swipeSuppressionObserverRegistered", false)
        setObserverRegisteredFlag(LauncherFolderHooks, "folderPreferenceObserverRegistered", false)
        setObserverRegisteredFlag(SystemUIBatteryHooks, "batteryStyleObserverRegistered", false)
    }

    @After
    fun tearDown() {
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
        MainModule.mPrefs.clear()
    }

    @Test
    fun swipeSuppressionSpecificKeyRefreshes() {
        MainModule.mPrefs.put("system_lockscreenshortcuts_right_off", true)
        MainModule.mPrefs.put("system_lockscreenshortcuts_left_off", false)
        SystemUILockScreenHooks.installSwipeSuppressionSnapshot()

        MainModule.mPrefs.put("system_lockscreenshortcuts_right_off", false)
        PreferenceObserverRegistry.handlePreferenceChanged("system_lockscreenshortcuts_right_off")

        assertFalse(getSwipeRightOff())
    }

    @Test
    fun swipeSuppressionUnrelatedKeyDoesNotRefresh() {
        MainModule.mPrefs.put("system_lockscreenshortcuts_right_off", true)
        SystemUILockScreenHooks.installSwipeSuppressionSnapshot()

        MainModule.mPrefs.put("system_lockscreenshortcuts_right_off", false)
        PreferenceObserverRegistry.handlePreferenceChanged("system_statusbarheight")

        assertTrue(getSwipeRightOff())
    }

    @Test
    fun swipeSuppressionNullKeyRefreshes() {
        MainModule.mPrefs.put("system_lockscreenshortcuts_right_off", true)
        MainModule.mPrefs.put("system_lockscreenshortcuts_left_off", true)
        SystemUILockScreenHooks.installSwipeSuppressionSnapshot()

        MainModule.mPrefs.put("system_lockscreenshortcuts_right_off", false)
        MainModule.mPrefs.put("system_lockscreenshortcuts_left_off", false)
        PreferenceObserverRegistry.handlePreferenceChanged(null)

        assertFalse(getSwipeRightOff())
        assertFalse(getSwipeLeftOff())
    }

    @Test
    fun folderSpecificKeyRefreshes() {
        MainModule.mPrefs.put("launcher_folderwidth", true)
        MainModule.mPrefs.put("launcher_folderblur_opacity", 60)
        LauncherFolderHooks.installFolderPreferenceSnapshot()

        MainModule.mPrefs.put("launcher_folderblur_opacity", 80)
        PreferenceObserverRegistry.handlePreferenceChanged("launcher_folderblur_opacity")

        assertEquals(0.8f, getFolderBlurRatio(), 0.001f)
    }

    @Test
    fun folderUnrelatedKeyDoesNotRefresh() {
        MainModule.mPrefs.put("launcher_folderblur_opacity", 60)
        LauncherFolderHooks.installFolderPreferenceSnapshot()

        MainModule.mPrefs.put("launcher_folderblur_opacity", 80)
        PreferenceObserverRegistry.handlePreferenceChanged("system_statusbarheight")

        assertEquals(0.6f, getFolderBlurRatio(), 0.001f)
    }

    @Test
    fun folderNullKeyRefreshes() {
        MainModule.mPrefs.put("launcher_folderwidth", false)
        MainModule.mPrefs.put("launcher_folderblur_opacity", 60)
        LauncherFolderHooks.installFolderPreferenceSnapshot()

        MainModule.mPrefs.put("launcher_folderwidth", true)
        MainModule.mPrefs.put("launcher_folderblur_opacity", 80)
        PreferenceObserverRegistry.handlePreferenceChanged(null)

        assertTrue(getFolderWidthEnabled())
        assertEquals(0.8f, getFolderBlurRatio(), 0.001f)
    }

    @Test
    fun folderBlurDisableOverridesOpacity() {
        assertEquals(0f, LauncherFolderHooks.resolveFolderBlurRatio(true, 80), 0.001f)
        assertEquals(0.8f, LauncherFolderHooks.resolveFolderBlurRatio(false, 80), 0.001f)
        assertEquals(1f, LauncherFolderHooks.resolveFolderBlurRatio(false, 120), 0.001f)
        assertFalse(LauncherFolderHooks.resolveFolderBlurOverrideEnabled(false, 0))
        assertTrue(LauncherFolderHooks.resolveFolderBlurOverrideEnabled(true, 0))
        assertTrue(LauncherFolderHooks.resolveFolderBlurOverrideEnabled(false, 80))
    }

    @Test
    fun batterySpecificKeyRefreshes() {
        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 20)
        SystemUIBatteryHooks.installBatteryStyleSnapshot()
        val initial = SystemUIBatteryHooks.batteryStyle!!.fontSizeDp

        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 30)
        PreferenceObserverRegistry.handlePreferenceChanged("system_statusbar_batterystyle_fontsize")

        assertNotEquals(initial, SystemUIBatteryHooks.batteryStyle!!.fontSizeDp)
        assertEquals(15.0f, SystemUIBatteryHooks.batteryStyle!!.fontSizeDp, 0.001f)
    }

    @Test
    fun batteryUnrelatedKeyDoesNotRefresh() {
        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 20)
        SystemUIBatteryHooks.installBatteryStyleSnapshot()
        val initial = SystemUIBatteryHooks.batteryStyle!!.fontSizeDp

        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 30)
        PreferenceObserverRegistry.handlePreferenceChanged("launcher_folderwidth")

        assertEquals(initial, SystemUIBatteryHooks.batteryStyle!!.fontSizeDp)
    }

    @Test
    fun batteryNullKeyRefreshes() {
        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 20)
        SystemUIBatteryHooks.installBatteryStyleSnapshot()

        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 30)
        PreferenceObserverRegistry.handlePreferenceChanged(null)

        assertEquals(15.0f, SystemUIBatteryHooks.batteryStyle!!.fontSizeDp, 0.001f)
    }

    private fun getSwipeRightOff(): Boolean {
        val field = SystemUILockScreenHooks::class.java.getDeclaredField("swipeRightOff")
        field.isAccessible = true
        return field.get(null) as Boolean
    }

    private fun getSwipeLeftOff(): Boolean {
        val field = SystemUILockScreenHooks::class.java.getDeclaredField("swipeLeftOff")
        field.isAccessible = true
        return field.get(null) as Boolean
    }

    private fun getFolderWidthEnabled(): Boolean {
        val field = LauncherFolderHooks::class.java.getDeclaredField("folderWidthEnabled")
        field.isAccessible = true
        return field.get(null) as Boolean
    }

    private fun getFolderBlurRatio(): Float {
        val field = LauncherFolderHooks::class.java.getDeclaredField("folderBlurRatio")
        field.isAccessible = true
        return field.get(null) as Float
    }

    private fun setObserverRegisteredFlag(obj: Any, name: String, value: Boolean) {
        val field = obj.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(null, value)
    }
}
