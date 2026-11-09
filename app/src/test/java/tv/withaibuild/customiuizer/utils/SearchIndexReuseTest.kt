package tv.withaibuild.customiuizer.utils

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SearchIndexReuseTest {
    @Test fun movedSettingsAndAliasesUseTheirSingleVisibleOwner() {
        Helpers.getAllMods(context("zh-CN"), true)
        for ((key, category) in mapOf(
            "pref_key_system_volume_ios" to ModData.ModCat.pref_key_controls,
            "pref_key_system_media_ios_card" to ModData.ModCat.pref_key_system,
            "pref_key_system_recents_ios18_deck" to ModData.ModCat.pref_key_launcher,
            "pref_key_system_statusbar_duo" to ModData.ModCat.pref_key_system,
            "pref_key_system_usb_default_function" to ModData.ModCat.pref_key_various,
        )) assertEquals(key, category, Helpers.allModsList.single { it.key == key }.cat)
        val indicator = Helpers.allModsList.single { it.key == "pref_key_controls_ios_home_indicator" }
        assertTrue(indicator.matchesSearch(searchTerms("小白条")))
        val keyboard = Helpers.allModsList.single { it.key == "pref_key_various_gboardpadding_port" }
        assertTrue(keyboard.matchesSearch(searchTerms("Gboard")))
        assertEquals("pref_key_controls_cat_navbar", keyboard.sub)
        assertTrue(Helpers.allModsList.single { it.key == "pref_key_system_recents_ios18_deck" }
            .matchesSearch(searchTerms("多任务")))
    }

    @Test fun renamedDirectoriesKeepHistoricalNamesAndLocalAliasesInEveryPage() {
        for ((locale, controls, tools) in listOf(
            Triple("en", "Buttons & gestures", "Apps & maintenance"),
            Triple("zh-CN", "按键与手势", "应用与维护"),
            Triple("zh-TW", "按鍵與手勢", "應用程式與維護"),
        )) {
            Helpers.getAllMods(context(locale), true)
            val keyboard = Helpers.allModsList.single { it.key == "pref_key_various_gboardpadding_port" }
            assertTrue(keyboard.matchesSearch(searchTerms("$controls Gboard")))
            val indicator = Helpers.allModsList.single { it.key == "pref_key_controls_ios_home_indicator" }
            assertTrue(indicator.matchesSearch(searchTerms("$controls Home Indicator")))
            val application = Helpers.allModsList.single { it.key == "pref_key_system_appbehavior_brightness" }
            assertTrue(application.matchesSearch(searchTerms(tools)))
            assertTrue(application.matchesSearch(searchTerms(context(locale).getString(tv.withaibuild.customiuizer.R.string.various_mods))))
            assertFalse(application.breadcrumbs.contains(tools))
            val statusbar = Helpers.allModsList.single { it.key == "pref_key_system_statusbar_clocktweak" }
            assertFalse(statusbar.matchesSearch(searchTerms(tools)))
            for ((key, name) in listOf("appbehavior" to "System_AppBehavior", "wifiauto" to "System_WifiAutomatic",
                    "vpnauto" to "System_VpnAutoSwitch")) {
                val entry = Helpers.allModsList.single { it.key == "pref_key_system_${key}_cat" }
                assertEquals(name, entry.page?.createFragment()?.javaClass?.simpleName)
                assertEquals(entry.key, entry.sub)
            }
        }
    }

    private fun context(tag: String, fontScale: Float = 1f) = RuntimeEnvironment.getApplication()
        .let { app -> app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(tag)))
            this.fontScale = fontScale
        }) }

    @Test fun featureNamesFindTheActualHomeIndicatorDeckAndDuoSwitches() {
        for ((language, query) in listOf("en" to "Home Indicator", "zh-CN" to "手势指示条", "zh-TW" to "手勢指示條")) {
            Helpers.getAllMods(context(language), true)
            for ((key, text) in listOf("pref_key_controls_ios_home_indicator" to query,
                    "pref_key_system_recents_ios18_deck" to "DECK", "pref_key_system_statusbar_duo" to "Duo")) {
                val row = Helpers.allModsList.single { it.key == key }
                assertTrue("$language: $key must be found by its feature name", row.matchesSearch(searchTerms(text)))
                assertNotEquals("Enable", row.title)
            }
        }
    }

    @Test
    fun recreationAndFontChangesReuseTheCompleteIndex() {
        Helpers.getAllMods(context("en"), true)
        val first = Helpers.allModsList
        assertTrue(first.isNotEmpty())
        Helpers.getAllMods(context("en", 2f), false)
        assertSame(first, Helpers.allModsList)
        assertEquals(first.size, first.map { it.key }.toSet().size)
    }

    @Test
    fun localeChangeInvalidatesTitlesAndReturningToAnEarlierLocaleRebuilds() {
        Helpers.getAllMods(context("en"), true)
        val english = Helpers.allModsList
        Helpers.getAllMods(context("zh-CN"), false)
        val chinese = Helpers.allModsList
        assertNotSame(english, chinese)
        assertNotEquals(english.map { it.title }, chinese.map { it.title })
        Helpers.getAllMods(context("en"), false)
        assertNotSame(chinese, Helpers.allModsList)
        assertEquals(english.map { it.title }, Helpers.allModsList.map { it.title })
    }

    @Test
    fun explicitReloadStillReplacesTheIndex() {
        Helpers.getAllMods(context("en"), true)
        val first = Helpers.allModsList
        Helpers.getAllMods(context("en"), true)
        assertNotSame(first, Helpers.allModsList)
        assertEquals(first.map { it.key }, Helpers.allModsList.map { it.key })
    }
}
