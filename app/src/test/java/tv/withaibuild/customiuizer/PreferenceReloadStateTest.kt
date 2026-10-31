package tv.withaibuild.customiuizer

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.preference.Preference
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import tv.withaibuild.customiuizer.prefs.SeekBarPreference
import tv.withaibuild.customiuizer.ui.SettingsPreferenceEditor
import tv.withaibuild.customiuizer.ui.SettingsTheme
import tv.withaibuild.customiuizer.utils.AppHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreferenceReloadStateTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val previous = AppHelper.appPrefs
    @Before fun prepare() { AppHelper.appPrefs = app.getSharedPreferences("reload-state-test", 0) }
    @After fun close() { AppHelper.appPrefs = previous }
    class ReloadEditorPage : PreferenceFragmentBase() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
                addPreference(SeekBarPreference(requireContext(), null).apply {
                    key = "synthetic_draft"; title = "Draft editor"
                    configureComposeValues(0, 800, 1, 400, 400, 1, false, "%d dp", null, null, true)
                })
            }
        }
    }
    @Test fun fragmentReloadRebindsTheOpenEditorToItsNewModel() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val page = ReloadEditorPage()
            controller.get().supportFragmentManager.beginTransaction().add(android.R.id.content, page).commitNow()
            val original = page.findPreference<Preference>("synthetic_draft")!!
            val editor = ReflectionHelpers.getField<MutableState<Preference?>>(page, "dialogPreference\$delegate")
            editor.value = original
            page.reloadPreferences()
            assertEquals("synthetic_draft", editor.value?.key)
            assertNotSame(original, editor.value)
            assertSame(page.findPreference<Preference>("synthetic_draft"), editor.value)
            assertFalse(AppHelper.appPrefs!!.contains("synthetic_draft"))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun recreatedEditorPreservesDraftWithoutCommittingIt() {
        val restoration = StateRestorationTester(compose)
        var current: SeekBarPreference? = null
        restoration.setContent { SettingsTheme {
            val preference = remember {
                SeekBarPreference(app, null).apply {
                    key = "synthetic_draft"; title = "Draft editor"
                    configureComposeValues(0, 800, 1, 400, 400, 1, false, "%d dp", null, null, true)
                }
            }
            SideEffect { current = preference }
            SettingsPreferenceEditor(preference) {}
        } }
        compose.onNodeWithTag("setting.input").performTextReplacement("25")
        val original = current
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("setting.input").assertTextEquals("25")
        assertNotSame(original, current)
        assertEquals(400, current?.composeProgress)
        assertFalse(AppHelper.appPrefs!!.contains("synthetic_draft"))
    }
}
