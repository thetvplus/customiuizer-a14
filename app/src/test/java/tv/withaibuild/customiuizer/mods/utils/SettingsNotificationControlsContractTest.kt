package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsNotificationControlsContractTest {
    @Test
    fun settingsFeatureOverridesAllAndroid14BlockDecisions() {
        val hooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt")
        val feature = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SettingsFeatures.kt")
        assertTrue(hooks.contains("com.android.settings.notification.app.NotificationPreferenceController"))
        assertTrue(hooks.contains("\"isAppBlockable\""))
        assertTrue(hooks.contains("\"isChannelBlockable\""))
        assertTrue(hooks.contains("\"isChannelConfigurable\""))
        assertTrue(hooks.contains("\"isChannelGroupBlockable\""))
        assertTrue(hooks.contains("com.android.settings.notification.app.AppNotificationSettings"))
        assertTrue(hooks.contains("\"setupBlock\""))
        assertFalse(hooks.contains("mHasNotifPermission"))
        assertTrue(hooks.contains("XposedHelpers.callMethod(block, \"setEnabled\", true)"))
        assertFalse(hooks.contains("as? Preference"))
        assertTrue(hooks.contains("skipped = true\n                    result = HashSet<String>()"))
        assertTrue(feature.contains("UnlockSettingsNotificationControlsHook(lpparam)"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
