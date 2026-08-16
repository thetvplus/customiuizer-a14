package tv.withaibuild.customiuizer.mods.utils.feature

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class CommonPackageFastGateTest {

    @Test
    fun disabledCommonFeaturesSkipRegistryConstruction() {
        assertFalse(CommonPackageFeatures.hasEnabledFeature(PrefMap(), "com.example.app"))

        val source = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val gate = source.indexOf("CommonPackageFeatures.hasEnabledFeature(mPrefs, pkg)")
        val registry = source.indexOf("FeatureInstallRegistry commonRegistry", gate)

        assertTrue(gate >= 0)
        assertTrue(registry > gate)
    }

    @Test
    fun statusBarHeightEnablesCommonFeatureForEveryPackage() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 12) }

        assertTrue(CommonPackageFeatures.hasEnabledFeature(prefs, "com.example.app"))
    }

    @Test
    fun alarmCompatibilityOnlyEnablesSelectedPackage() {
        val prefs = PrefMap().apply {
            put("various_alarmcompat", true)
            put("various_alarmcompat_apps", setOf("com.example.clock"))
        }

        assertTrue(CommonPackageFeatures.hasEnabledFeature(prefs, "com.example.clock"))
        assertFalse(CommonPackageFeatures.hasEnabledFeature(prefs, "com.example.other"))
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
