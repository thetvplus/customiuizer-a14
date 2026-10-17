package tv.withaibuild.customiuizer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutMigrationTest {

    private val aboutFragment = source("app/src/main/java/tv/withaibuild/customiuizer/AboutFragment.kt")
    private val appLocaleController = source("app/src/main/java/tv/withaibuild/customiuizer/utils/AppLocaleController.kt")
    private val mainActivity = source("app/src/main/java/tv/withaibuild/customiuizer/MainActivity.kt")
    private val mainFragment = source("app/src/main/java/tv/withaibuild/customiuizer/MainFragment.kt")
    private val preferenceFragmentBase = source("app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt")
    private val buildGradle = source("app/build.gradle.kts")
    private val mainPreferences = source("app/src/main/res/xml/prefs_main.xml")
    private val aboutLayout = source("app/src/main/res/layout/fragment_about.xml")

    @Test
    fun aboutFragmentIsAPlainFragment() {
        assertTrue("AboutFragment should extend Fragment", aboutFragment.contains("class AboutFragment : Fragment()"))
        assertFalse("AboutFragment should not extend SubFragment", aboutFragment.contains("class AboutFragment : SubFragment"))
    }

    @Test
    fun aboutFragmentDoesNotUsePreferenceInfrastructure() {
        assertFalse("AboutFragment must not use findPreference", aboutFragment.contains("findPreference"))
        assertFalse("AboutFragment must not use ListPreferenceEx", aboutFragment.contains("ListPreferenceEx"))
        assertFalse("AboutFragment must not use PreferenceScreen", aboutFragment.contains("PreferenceScreen"))
        assertFalse("AboutFragment must not use Preference", aboutFragment.contains("androidx.preference.Preference"))
        assertFalse("AboutFragment must not load prefs_about", aboutFragment.contains("R.xml.prefs_about"))
        assertFalse("AboutFragment must not call setPreferencesFromResource", aboutFragment.contains("setPreferencesFromResource"))
    }

    @Test
    fun appLocaleControllerDoesNotDependOnListPreferenceEx() {
        assertFalse("AppLocaleController must not import ListPreferenceEx", appLocaleController.contains("ListPreferenceEx"))
        assertFalse("AppLocaleController must not expose setupLocalePreference", appLocaleController.contains("setupLocalePreference"))
    }

    @Test
    fun aboutLaunchDoesNotRequirePrefsAboutXml() {
        assertFalse("PreferenceFragmentBase must not reference R.xml.prefs_about", preferenceFragmentBase.contains("R.xml.prefs_about"))
        assertTrue("PreferenceFragmentBase should launch AboutFragment", preferenceFragmentBase.contains("AboutFragment()"))
    }

    @Test
    fun toolbarHomeHandlesAboutFragment() {
        assertTrue("MainActivity must pop the back stack for AboutFragment", mainActivity.contains("fragment is AboutFragment"))
        assertTrue("MainActivity must call popBackStackImmediate for AboutFragment", mainActivity.contains("popBackStackImmediate"))
    }

    @Test
    fun prefsAboutXmlIsRemoved() {
        val prefsAbout = File(System.getProperty("user.dir") ?: "").absoluteFile.let { root ->
            var dir: File? = root
            while (dir != null) {
                val candidate = File(dir, "app/src/main/res/xml/prefs_about.xml")
                if (candidate.isFile) return@let candidate
                dir = dir.parentFile
            }
            null
        }
        assertTrue("prefs_about.xml should be deleted", prefsAbout == null || !prefsAbout.exists())
    }

    @Test
    fun noComposeDependenciesIntroduced() {
        assertFalse("build.gradle.kts must not add Compose dependencies", buildGradle.contains("androidx.compose"))
    }

    @Test
    fun languageControlLivesOnHomeBelowSettingsEntry() {
        assertFalse("AboutFragment must not own locale behavior", aboutFragment.contains("AppLocaleController"))
        assertFalse("About layout must not contain a language row", aboutLayout.contains("about_language_row"))

        val settingsEntry = mainPreferences.indexOf("pref_key_miuizer_settingsiconpos")
        val localeEntry = mainPreferences.indexOf("pref_key_miuizer_locale")
        val launcherEntry = mainPreferences.indexOf("pref_key_miuizer_launchericon")
        assertTrue("Home must contain the settings entry", settingsEntry >= 0)
        assertTrue("Language must follow the settings entry", localeEntry > settingsEntry)
        assertTrue("Language must be before the launcher icon entry", launcherEntry > localeEntry)

        assertTrue("MainFragment must guard fatal locale-data failures", mainFragment.contains("FatalErrors.rethrowIfFatal(t)"))
        assertTrue("MainFragment must use the centralized locale display data", mainFragment.contains("AppLocaleController.buildLocaleDisplayData(locale.context)"))
        assertTrue("MainFragment must use the centralized durable locale writer", mainFragment.contains("AppLocaleController.setUserLocale(prefs, newTag)"))
    }

    @Test
    fun homeTitlesUseDistinctResources() {
        assertTrue("MainFragment ActionBar must use the product name", mainFragment.contains("actionBar?.setTitle(R.string.app_name)"))
        assertFalse("MainFragment ActionBar must not use the generic settings title", mainFragment.contains("actionBar?.setTitle(R.string.settings_title)"))
        assertTrue("Home settings category must use the generic settings title", mainPreferences.contains("android:title=\"@string/settings_title\""))
        assertFalse("Home settings category must not reuse the legacy product settings title", mainPreferences.contains("android:title=\"@string/miuizer\""))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir") ?: "").absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
