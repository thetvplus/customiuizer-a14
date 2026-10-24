package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StatusBarActionDiscoveryTest {
    private val keys = listOf("system_statusbarcontrols_dt", "system_statusbarcontrols_dt_left",
        "system_statusbarcontrols_dt_right")

    @Before @After fun reset() {
        MainModule.mPrefs.clear()
        val owner = Class.forName("tv.withaibuild.customiuizer.mods.utils.GlobalActionConfigKt")
        owner.getDeclaredField("customActionsReady").apply { isAccessible = true }.set(null, false)
        for (name in listOf("customActionCodeMap", "customToggleMap"))
            owner.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
    }

    @Test fun eachDoubleTapEntryCanInstallItsPipActionWithoutAnotherGesture() {
        for (key in keys) for (code in listOf(33, 34)) {
            reset()
            MainModule.mPrefs.put(key + "_action", code)
            assertTrue("$key must install PiP action $code", hasConfiguredActionCode(code))
            assertFalse(hasConfiguredActionCode(if (code == 33) 34 else 33))
        }
    }

    @Test fun doubleTapTogglesUseTheSameDiscoveryAndDefaultEntriesInstallNothing() {
        assertFalse(hasConfiguredGlobalActions())
        for (key in keys) {
            reset()
            MainModule.mPrefs.put(key + "_action", 10)
            MainModule.mPrefs.put(key + "_toggle", 6)
            assertTrue(hasConfiguredActionCode(10))
            assertTrue(hasConfiguredToggle(6))
        }
    }
}
