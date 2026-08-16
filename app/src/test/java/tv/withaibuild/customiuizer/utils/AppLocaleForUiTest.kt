package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocaleForUiTest {

    @Test
    fun getUserLocaleForUi_repairsInvalidStoredValueToAuto() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "nonsense")

        val result = AppLocaleController.getUserLocaleForUi(fakePrefs)

        assertEquals("auto", result)
        assertEquals("auto", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun getUserLocaleForUi_repairsLegacyAutoValueToAuto() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "1")

        val result = AppLocaleController.getUserLocaleForUi(fakePrefs)

        assertEquals("auto", result)
        assertEquals("auto", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun getUserLocaleForUi_leavesValidValueUnchanged() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "en")

        val result = AppLocaleController.getUserLocaleForUi(fakePrefs)

        assertEquals("en", result)
        assertEquals("en", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun getUserLocaleForUi_returnsAutoForNullPrefs() {
        assertEquals("auto", AppLocaleController.getUserLocaleForUi(null))
    }

    @Test
    fun getUserLocaleForUi_returnsAutoForMissingValue() {
        val fakePrefs = FakeSharedPreferences()
        assertEquals("auto", AppLocaleController.getUserLocaleForUi(fakePrefs))
        assertNull(fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun getUserLocaleSummary_returnsDisplayNameForSupportedLocale() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "pt-BR")

        val summary = AppLocaleController.getUserLocaleSummary("System", fakePrefs)

        assertEquals("Português (Brasil)", summary)
    }

    @Test
    fun getUserLocaleSummary_repairsInvalidValueAndFallsBackToSystemDefault() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "invalid")

        val summary = AppLocaleController.getUserLocaleSummary("System", fakePrefs)

        assertEquals("System", summary)
        assertEquals("auto", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun getUserLocaleSummary_returnsSystemDefaultForAuto() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "auto")

        val summary = AppLocaleController.getUserLocaleSummary("System", fakePrefs)

        assertEquals("System", summary)
    }

    @Test
    fun setUserLocaleReturnsFalseAndRestoresPreviousValue() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "zh-CN")
        fakePrefs.commitShouldSucceed = false

        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertFalse(success)
        assertEquals("zh-CN", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun setUserLocaleReturnsFalseAndRestoresMissingKey() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.commitShouldSucceed = false

        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertFalse(success)
        assertNull(fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun setUserLocaleSavesNewValueWhenCommitSucceeds() {
        val fakePrefs = FakeSharedPreferences()

        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertTrue(success)
        assertEquals("en", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }
}
