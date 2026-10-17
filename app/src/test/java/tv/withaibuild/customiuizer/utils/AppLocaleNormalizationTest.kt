package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleNormalizationTest {

    @Test
    fun nullBlankAndLegacyMapToAuto() {
        assertEquals("auto", AppLocaleController.normalizeLocaleTag(null))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag(""))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("   "))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("1"))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("AUTO"))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("unknown"))
    }

    @Test
    fun supportedTagsArePreserved() {
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("auto"))
        assertEquals("en", AppLocaleController.normalizeLocaleTag("en"))
        assertEquals("zh-CN", AppLocaleController.normalizeLocaleTag("zh-CN"))
        assertEquals("zh-TW", AppLocaleController.normalizeLocaleTag("zh-TW"))
        assertEquals("pt-BR", AppLocaleController.normalizeLocaleTag("pt-BR"))
        assertEquals("ru-RU", AppLocaleController.normalizeLocaleTag("ru-RU"))
        assertEquals("es-ES", AppLocaleController.normalizeLocaleTag("es-ES"))
    }

    @Test
    fun getUserLocaleReturnsAutoForNullPrefs() {
        assertEquals("auto", AppLocaleController.getUserLocale(null))
    }

    @Test
    fun getUserLocaleReadsStoredValue() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "en")

        assertEquals("en", AppLocaleController.getUserLocale(fakePrefs))
    }

    @Test
    fun getUserLocaleNormalizesInvalidAndLegacyValues() {
        val fakePrefs = FakeSharedPreferences()

        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "1")
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))

        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "nonsense")
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))

        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "")
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))
    }
}
