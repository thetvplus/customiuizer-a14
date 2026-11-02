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
    private fun context(tag: String, fontScale: Float = 1f) = RuntimeEnvironment.getApplication()
        .let { app -> app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(tag)))
            this.fontScale = fontScale
        }) }

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
