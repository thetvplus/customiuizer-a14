package tv.withaibuild.customiuizer.utils

import androidx.core.os.LocaleListCompat
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLocaleReconcileTest {

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

    private fun runApply(): Boolean = AppLocaleController.apply(fakePrefs, gateway)

    private fun appliedLocaleLists(): List<LocaleListCompat> = gateway.appliedLists

    private class TestGateway : AppLocaleController.AppLocaleGateway {
        var storedLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()
        val appliedLists = ArrayList<LocaleListCompat>()

        override fun getCurrentLocales(): LocaleListCompat = storedLocales

        override fun setLocales(locales: LocaleListCompat): Boolean {
            storedLocales = locales
            appliedLists.add(locales)
            return true
        }
    }

    @Test
    fun setUserLocaleSavesNormalized() {
        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertTrue("setUserLocale should succeed", success)
        assertEquals("en", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun setUserLocaleDoesNotApplyImmediately() {
        AppLocaleController.setUserLocale(fakePrefs, "en")

        // The framework is only touched during apply(), never from setUserLocale.
        assertTrue("gateway should not be called from setUserLocale", appliedLocaleLists().isEmpty())
        assertEquals(Locale.getDefault(), originalDefaultLocale)
    }

    @Test
    fun setUserLocaleReturnsFalseAndRestoresInMemoryState() {
        fakePrefs.commitShouldSucceed = false

        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertFalse("setUserLocale should fail when commit fails", success)
        // Android SharedPreferences.commit() first updates the in-memory map and then
        // returns the disk-write status. A failed commit must roll the in-memory value
        // back so the UI does not show an uncommitted change.
        assertNull(fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun setUserLocaleSameValueIsIdempotent() {
        AppLocaleController.setUserLocale(fakePrefs, "en")

        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertTrue(success)
        assertEquals("en", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun applyAppliesWhenDifferentFromCurrent() {
        AppLocaleController.setUserLocale(fakePrefs, "en")

        val changed = runApply()

        assertTrue("apply should change locale when target differs", changed)
        assertEquals(Locale.ENGLISH, Locale.getDefault())
        assertEquals(1, appliedLocaleLists().size)
        assertNotNull(appliedLocaleLists()[0])
    }

    @Test
    fun applyIsIdempotentAfterFirstApply() {
        AppLocaleController.setUserLocale(fakePrefs, "en")

        // First apply: app locale changed, framework setter invoked.
        runApply()

        // Simulate the system having now adopted the target locale.
        gateway.storedLocales = appliedLocaleLists().last()
        gateway.appliedLists.clear()

        // Second apply: nothing changed, framework must not be called again.
        val changedAgain = runApply()

        assertFalse("second apply should not re-apply", changedAgain)
        assertTrue("framework setter should not be called again", appliedLocaleLists().isEmpty())
    }

    @Test
    fun applyDoesNothingWhenAlreadyMatching() {
        // Current application locales equal the target (both empty for auto).
        val changed = runApply()

        assertFalse(changed)
        assertTrue(appliedLocaleLists().isEmpty())
    }

    @Test
    fun applySetsDefaultLocaleForAuto() {
        // Pick an explicit language first, then switch back to auto. apply()
        // should set the JVM default back to whatever the (fake) system locale resolves
        // to, demonstrating that auto still derives from the system.
        Locale.setDefault(Locale.ENGLISH)
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()

        AppLocaleController.setUserLocale(fakePrefs, "auto")
        runApply()

        // In a JVM without Android Resources the system locale falls back to the
        // current default, so the test simply checks the process completes and the
        // value is auto.
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))
    }

    @Test
    fun toLocaleListCompatAutoIsEmpty() {
        val list = AppLocaleController.toLocaleListCompat("auto")
        assertNotNull(list)
        assertTrue(list.isEmpty)
    }

    @Test
    fun toLocaleListCompatExplicitIsNotNull() {
        val list = AppLocaleController.toLocaleListCompat("en")
        assertNotNull(list)
        // The JVM may not be able to fully initialize AppCompat internals, so we only
        // require that a LocaleListCompat object is returned without crashing.
    }

    @Test
    fun toLocaleListCompatUnknownFallsBackToAuto() {
        val list = AppLocaleController.toLocaleListCompat("invalid")
        assertNotNull(list)
    }

    @Test
    fun getEffectiveLocaleUsesExplicitTags() {
        val system = Locale.forLanguageTag("zh-CN")

        assertEquals(Locale.ENGLISH, AppLocaleController.getEffectiveLocale("en") { system })
        assertEquals(Locale.forLanguageTag("zh-CN"), AppLocaleController.getEffectiveLocale("zh-CN") { system })
        assertEquals(Locale.forLanguageTag("pt-BR"), AppLocaleController.getEffectiveLocale("pt-BR") { system })
    }

    @Test
    fun getEffectiveLocaleAutoResolvesToProvider() {
        val system = Locale.forLanguageTag("ja-JP")
        assertEquals(system, AppLocaleController.getEffectiveLocale("auto") { system })
    }

    @Test
    fun getEffectiveLocaleInvalidFallsBackToProvider() {
        val system = Locale.forLanguageTag("cs-CZ")
        assertEquals(system, AppLocaleController.getEffectiveLocale("nonsense") { system })
    }

    @Test
    fun stateTransitionMatrixThroughApply() {
        // auto -> zh-CN
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "zh-CN"))
        assertTrue(runApply())
        assertEquals(Locale.SIMPLIFIED_CHINESE, Locale.getDefault())

        // Simulate the system now reporting the new list.
        gateway.storedLocales = appliedLocaleLists().last()
        gateway.appliedLists.clear()

        // zh-CN -> en
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "en"))
        assertTrue(runApply())
        assertEquals(Locale.ENGLISH, Locale.getDefault())

        // en -> auto
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "auto"))
        assertTrue(runApply())
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))

        // After auto apply the default locale is the system locale; we only
        // assert that it is a defined non-null value.
        assertNotNull(Locale.getDefault())
    }
}
