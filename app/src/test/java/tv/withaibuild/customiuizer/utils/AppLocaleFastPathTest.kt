package tv.withaibuild.customiuizer.utils

import androidx.core.os.LocaleListCompat
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The language setting is a non-core feature, so an install that never uses it must not
 * pay for it on every application start. `apply()` runs from `MainApplication.onCreate`;
 * with the user on `auto` and nothing ever applied by us, it must not reach the framework
 * locale service.
 *
 * The correctness constraint these pin down is the other half: the fast path must never
 * strand a user who switches an explicit language back to `auto`, because that switch is
 * exactly the case where the framework locale still has to be cleared.
 */
class AppLocaleFastPathTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var gateway: TestGateway
    private var originalDefaultLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        gateway = TestGateway()
        originalDefaultLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalDefaultLocale)
    }

    private fun markerValue(): String? =
        fakePrefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null)

    private fun appliedLocaleLists(): List<LocaleListCompat> = gateway.appliedLists

    private fun runApply(prefs: FakeSharedPreferences = fakePrefs): Boolean =
        AppLocaleController.apply(prefs, gateway)

    private class TestGateway : AppLocaleController.AppLocaleGateway {
        var storedLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()
        var shouldFail = false
        val appliedLists = ArrayList<LocaleListCompat>()
        var getCallCount = 0
            private set

        override fun getCurrentLocales(): LocaleListCompat {
            getCallCount++
            return storedLocales
        }

        override fun setLocales(locales: LocaleListCompat): Boolean {
            if (shouldFail) return false
            storedLocales = locales
            appliedLists.add(locales)
            return true
        }
    }

    @Test
    fun untouchedInstallNeverQueriesTheFrameworkLocale() {
        val changed = runApply()

        assertFalse(changed)
        assertEquals("the framework locale must not be read at all", 0, gateway.getCallCount)
        assertTrue(appliedLocaleLists().isEmpty())
        assertNull("no marker should be written", markerValue())
    }

    @Test
    fun untouchedInstallStaysOnTheFastPathAcrossRestarts() {
        repeat(5) { runApply() }

        assertEquals(0, gateway.getCallCount)
    }

    @Test
    fun explicitLocaleIsAppliedAndRecorded() {
        AppLocaleController.setUserLocale(fakePrefs, "zh-CN")

        val changed = runApply()

        assertTrue(changed)
        assertEquals(1, appliedLocaleLists().size)
        assertEquals("zh-CN", markerValue())
    }

    @Test
    fun explicitLocaleStillReconcilesOnEveryStart() {
        // The chosen trade-off: the cost is paid only while the feature is switched on.
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()
        gateway.storedLocales = appliedLocaleLists().last()
        val queriesAfterFirstStart = gateway.getCallCount

        runApply()

        assertTrue("an explicit locale keeps reconciling", gateway.getCallCount > queriesAfterFirstStart)
    }

    @Test
    fun revertingToAutoClearsTheFrameworkLocaleAndTheMarker() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()
        gateway.storedLocales = appliedLocaleLists().last()
        gateway.appliedLists.clear()

        AppLocaleController.setUserLocale(fakePrefs, "auto")
        val changed = runApply()

        assertTrue("switching back to auto must clear the framework locale", changed)
        assertEquals(1, appliedLocaleLists().size)
        assertTrue("auto clears the list", appliedLocaleLists()[0].isEmpty)
        assertNull("the marker is dropped once the system is back in control", markerValue())
    }

    @Test
    fun revertingToAutoRestoresTheFastPath() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()
        AppLocaleController.setUserLocale(fakePrefs, "auto")
        runApply()

        gateway.storedLocales = LocaleListCompat.getEmptyLocaleList()
        val queriesBefore = gateway.getCallCount
        gateway.appliedLists.clear()

        runApply()

        assertEquals("back to zero cost", queriesBefore, gateway.getCallCount)
        assertTrue(appliedLocaleLists().isEmpty())
    }

    @Test
    fun installPredatingTheMarkerIsMigratedAndCanStillRevert() {
        // An install upgraded from a build without the marker: an explicit locale is
        // already stored and already in force, so apply() finds nothing to change.
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "zh-CN")
        gateway.storedLocales = AppLocaleController.toLocaleListCompat("zh-CN")

        val changed = runApply()

        assertFalse("nothing to apply", changed)
        assertEquals("but the marker must be backfilled", "zh-CN", markerValue())

        // Without that backfill this switch would take the fast path and leave the user
        // stuck in Chinese for good.
        AppLocaleController.setUserLocale(fakePrefs, "auto")
        val reverted = runApply()

        assertTrue(reverted)
        assertTrue(appliedLocaleLists().last().isEmpty)
        assertNull(markerValue())
    }

    @Test
    fun aRestoredBackupForcesOneFullReconcile() {
        // This device has an explicit locale in force; the restored backup is on auto and
        // wiped the marker, so only the invalidation keeps the clear from being skipped.
        gateway.storedLocales = AppLocaleController.toLocaleListCompat("zh-CN")
        AppLocaleController.invalidateFastPath(fakePrefs)

        val changed = runApply()

        assertTrue("the restored auto setting must reach the framework", changed)
        assertTrue(appliedLocaleLists().last().isEmpty)
        assertNull("and the shortcut comes back afterwards", markerValue())
    }

    @Test
    fun invalidationIsNotMistakenForAnAppliedTag() {
        AppLocaleController.invalidateFastPath(fakePrefs)
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "en")

        runApply()

        assertEquals("en", markerValue())
    }

    @Test
    fun aFailedFrameworkWriteDoesNotDropTheMarker() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()
        gateway.storedLocales = appliedLocaleLists().last()

        // Make the framework write fail so apply() reports false and must not update the marker.
        AppLocaleController.setUserLocale(fakePrefs, "auto")
        gateway.shouldFail = true
        AppLocaleController.apply(fakePrefs, gateway)

        assertEquals(
            "the marker must survive so the next start retries the clear",
            "en",
            markerValue()
        )
    }
}
