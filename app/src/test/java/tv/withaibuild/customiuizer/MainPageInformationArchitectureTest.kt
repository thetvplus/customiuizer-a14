package tv.withaibuild.customiuizer

import tv.withaibuild.customiuizer.testsupport.repositorySource as source

import org.junit.Assert.assertTrue
import org.junit.Test

class MainPageInformationArchitectureTest {
    @Test
    fun homePageGroupsByUserFacingFeatureSemantics() {
        val xml = source("app/settings/prefs_main.json")
        val mods = xml.substringAfter("\"android:key\": \"prefs_cat\"").substringBefore("pref_key_miuizer")
        val order = listOf(
            "pref_key_statusbar",
            "pref_key_notifications",
            "pref_key_launcher",
            "pref_key_controls",
            "pref_key_system",
            "pref_key_various",
        ).map { mods.indexOf(it) }
        assertTrue(order.all { it >= 0 })
        assertTrue(order == order.sorted())
        for (category in listOf("statusbar", "notifications", "launcher", "controls", "system", "various")) {
            assertTrue(xml.contains("\"android:title\": \"@string/settings_category_$category\""))
            assertTrue(xml.contains("\"android:summary\": \"@string/settings_category_${category}_summary\""))
        }
        assertTrue(xml.contains("\"android:title\": \"@string/settings_title\""))
    }

}
