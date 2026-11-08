package tv.withaibuild.customiuizer

import tv.withaibuild.customiuizer.testsupport.repositorySource as source

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MainPageInformationArchitectureTest {
    @Test
    fun homePageGroupsByUserFacingFeatureSemantics() {
        val xml = source("app/settings/prefs_main.json")
        val mods = xml.substringAfter("\"android:key\": \"prefs_cat\"").substringBefore("pref_key_miuizer")
        val order = listOf(
            "pref_key_system",
            "pref_key_launcher",
            "pref_key_controls",
            "pref_key_various",
        ).map { mods.indexOf(it) }
        assertTrue(order.all { it >= 0 })
        assertTrue(order == order.sorted())
        for (title in listOf("system_mods", "launcher_title", "controls_mods", "various_mods")) {
            assertTrue(xml.contains("\"android:title\": \"@string/$title\""))
        }
        assertFalse(mods.contains("pref_key_statusbar"))
        assertFalse(mods.contains("pref_key_notifications"))
        assertFalse(xml.contains("pref_key_miuizer_backup"))
        assertTrue(xml.contains("\"android:title\": \"@string/settings_title\""))
    }

}
