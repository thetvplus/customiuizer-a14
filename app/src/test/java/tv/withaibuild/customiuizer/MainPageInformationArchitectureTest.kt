package tv.withaibuild.customiuizer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainPageInformationArchitectureTest {
    @Test
    fun homePageGroupsByUserFacingFeatureSemantics() {
        val xml = source("app/src/main/res/xml/prefs_main.xml")
        val mods = xml.substringAfter("android:key=\"prefs_cat\"").substringBefore("pref_key_miuizer")
        val order = listOf(
            "pref_key_system",
            "pref_key_launcher",
            "pref_key_controls",
            "pref_key_various",
        ).map { mods.indexOf(it) }
        assertTrue(order.all { it >= 0 })
        assertTrue(order == order.sorted())
        assertTrue(xml.contains("android:title=\"@string/system_mods\""))
        assertTrue(xml.contains("android:title=\"@string/launcher_title\""))
        assertTrue(xml.contains("android:title=\"@string/controls_mods\""))
        assertTrue(xml.contains("android:title=\"@string/various_mods\""))
        assertTrue(xml.contains("android:title=\"@string/settings_title\""))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")!!).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found")
        }
    }
}
