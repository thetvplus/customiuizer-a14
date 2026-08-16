package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule

/**
 * Behavioral tests for the battery style snapshot.
 */
class BatteryStyleSnapshotTest {

    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            (MainModule.mPrefs).replaceSnapshot(savedPrefs)
        }
    }

    @Test
    fun readBatteryStyleUsesDefaults() {
        val style = SystemUIBatteryHooks.readBatteryStyle()

        assertFalse(style.swap)
        assertEquals(7.5f, style.fontSizeDp)
        assertEquals(7.5f, style.markFontSizeDp)
        assertFalse(style.bold)
        assertEquals(0f, style.leftMarginDp)
        assertEquals(0f, style.rightMarginDp)
        assertEquals(8, style.verticalOffset)
        assertEquals(17, style.markVerticalOffset)
        assertFalse(style.battery4)
    }

    @Test
    fun readBatteryStyleReadsCustomValues() {
        MainModule.mPrefs.put("system_statusbaricons_swap_batteryicon_percentage", true)
        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 24)
        MainModule.mPrefs.put("system_statusbar_batterystyle_mark_fontsize", 26)
        MainModule.mPrefs.put("system_statusbar_batterystyle_bold", true)
        MainModule.mPrefs.put("system_statusbar_batterystyle_leftmargin", 6)
        MainModule.mPrefs.put("system_statusbar_batterystyle_rightmargin", 10)
        MainModule.mPrefs.put("system_statusbar_batterystyle_verticaloffset", 12)
        MainModule.mPrefs.put("system_statusbar_batterystyle_mark_verticaloffset", 20)
        MainModule.mPrefs.put("system_statusbaricons_battery4", true)

        val style = SystemUIBatteryHooks.readBatteryStyle()

        assertTrue(style.swap)
        assertEquals(12.0f, style.fontSizeDp)
        assertEquals(13.0f, style.markFontSizeDp)
        assertTrue(style.bold)
        assertEquals(3.0f, style.leftMarginDp)
        assertEquals(5.0f, style.rightMarginDp)
        assertEquals(12, style.verticalOffset)
        assertEquals(20, style.markVerticalOffset)
        assertTrue(style.battery4)
    }

    @Test
    fun installBatteryStyleSnapshotCachesAndRefreshes() {
        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 30)
        SystemUIBatteryHooks.installBatteryStyleSnapshot()

        val first = SystemUIBatteryHooks.batteryStyle
        assertEquals(15.0f, first?.fontSizeDp)

        MainModule.mPrefs.put("system_statusbar_batterystyle_fontsize", 10)
        SystemUIBatteryHooks.batteryStyle = SystemUIBatteryHooks.readBatteryStyle()

        assertEquals(5.0f, SystemUIBatteryHooks.batteryStyle?.fontSizeDp)
    }
}
