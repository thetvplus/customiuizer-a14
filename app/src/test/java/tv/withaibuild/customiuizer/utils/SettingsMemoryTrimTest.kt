package tv.withaibuild.customiuizer.utils

import android.content.ComponentCallbacks2
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMemoryTrimTest {

    @After
    fun tearDown() {
        Helpers.launchableAppsList = null
        Helpers.shareAppsList = null
        Helpers.openWithAppsList = null
        AppHelper.installedAppsList = null
    }

    @Test
    fun runningLevelsKeepCaches() {
        assertFalse(SettingsMemoryTrim.shouldReleaseRegenerableCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
        assertFalse(SettingsMemoryTrim.shouldReleaseRegenerableCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertFalse(SettingsMemoryTrim.shouldReleaseRegenerableCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
    }

    @Test
    fun hiddenAndBackgroundLevelsReleaseCaches() {
        assertTrue(SettingsMemoryTrim.shouldReleaseRegenerableCaches(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertTrue(SettingsMemoryTrim.shouldReleaseRegenerableCaches(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertTrue(SettingsMemoryTrim.shouldReleaseRegenerableCaches(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }

    @Test
    fun releaseNullsRegenerableListsWithoutTouchingCallerOwnedCopy() {
        val launchable = arrayListOf(AppData())
        Helpers.launchableAppsList = launchable
        Helpers.shareAppsList = arrayListOf(AppData())
        Helpers.openWithAppsList = arrayListOf(AppData())
        AppHelper.installedAppsList = arrayListOf(AppData())

        SettingsMemoryTrim.releaseRegenerableCaches()

        assertNull(Helpers.launchableAppsList)
        assertNull(Helpers.shareAppsList)
        assertNull(Helpers.openWithAppsList)
        assertNull(AppHelper.installedAppsList)
        assertEquals(1, launchable.size)
    }
}
