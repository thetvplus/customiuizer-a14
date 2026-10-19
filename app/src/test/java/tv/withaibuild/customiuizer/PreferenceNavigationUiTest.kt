package tv.withaibuild.customiuizer

import android.app.Application
import android.animation.AnimatorSet
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.prefs.SeekBarPreference

/** Runs the production FragmentManager, preference XML and animations on an Android 14 model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class PreferenceNavigationUiTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity

    @Before
    fun launchSettings() {
        val application = RuntimeEnvironment.getApplication()
        Helpers.withinAppContext = true
        Helpers.appContentResolver = application.contentResolver
        AppHelper.appPrefs = application.getSharedPreferences(AppHelper.prefsName, 0)
        miui.os.Build.IS_INTERNATIONAL_BUILD = false
        Settings.Global.putFloat(application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(application.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        activity = controller.get()
        settleNavigation()
        assertPreferencePageVisible(currentPage())
    }

    @After
    fun closeSettings() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
        AppHelper.appPrefs = null
        Helpers.appContentResolver = null
        Helpers.withinAppContext = false
        miui.os.Build.IS_INTERNATIONAL_BUILD = false
    }

    @Test
    fun collectingAnAnimatorPreservesTheFrameworksPendingVisibility() {
        val page = currentPage() as MainFragment
        val view = page.requireView()
        view.alpha = 0f
        view.translationX = 0f
        val animator = page.onCreateAnimator(0, true, R.animator.fragment_open_enter)

        assertTrue("The entry animation must be available", animator != null)
        assertEquals("FragmentManager uses alpha to detect the pending entry", 0f, view.alpha, 0f)
        assertEquals("An animator that is only collected must not move the page", 0f, view.translationX, 0f)
    }

    @Test
    fun everyHomeCategoryAndAllItsSettingsPagesAreVisible() {
        for (key in arrayOf("pref_key_system", "pref_key_launcher", "pref_key_controls", "pref_key_various")) {
            val home = currentPage() as MainFragment
            assertTrue(home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>(key))))
            settleNavigation()
            val category = currentPage() as SubFragment
            assertPreferencePageVisible(category)

            val keys = (0 until category.preferenceScreen.preferenceCount).map {
                category.preferenceScreen.getPreference(it).key
            }
            for (settingKey in keys) {
                val selector = currentPage() as SubFragment
                requireNotNull(selector.findPreference<Preference>(settingKey)).performClick()
                settleNavigation()
                assertTrue("$settingKey must open a fresh settings page", currentPage() !== selector)
                assertPreferencePageVisible(currentPage())
                pressToolbarBack()
                assertPreferencePageVisible(currentPage())
            }
            pressToolbarBack()
            assertTrue(currentPage() is MainFragment)
            assertPreferencePageVisible(currentPage())
        }
    }

    @Test
    fun returningFromAboutRestoresTheHomeOptions() {
        val home = currentPage() as MainFragment
        val about = PopupMenu(activity, home.requireView()).menu.add(0, R.id.about, 0, "About")
        assertTrue(home.onOptionsItemSelected(about))
        settleNavigation()
        assertTrue(currentPage() is AboutFragment)
        pressToolbarBack()

        assertTrue(currentPage() is MainFragment)
        assertPreferencePageVisible(currentPage())
    }

    @Test
    fun removedDockMaterialMigratesToGlassAndKeepsGaussianSelectable() {
        val key = "pref_key_launcher_dock_background_material"
        requireNotNull(AppHelper.appPrefs).edit().putString(key, "1").commit()
        val home = currentPage() as MainFragment
        home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_launcher")))
        settleNavigation()
        val selector = currentPage() as SubFragment
        requireNotNull(selector.findPreference<Preference>("pref_key_launcher_cat_dock")).performClick()
        settleNavigation()
        val page = currentPage() as SubFragment
        val material = requireNotNull(page.findPreference<ListPreference>(key))
        assertEquals(listOf("0", "2"), material.entryValues.map { it.toString() })
        assertEquals("0", material.value)
        assertEquals("0", requireNotNull(AppHelper.appPrefs).getString(key, null))
        material.value = "2"
        pressToolbarBack()
        requireNotNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_launcher_cat_dock")).performClick()
        settleNavigation()
        assertEquals("2", (currentPage() as SubFragment).findPreference<ListPreference>(key)?.value)
    }

    @Test
    fun dockShrinkShowsSignedDpAndPersistsTheExistingSliderEncoding() {
        val key = "pref_key_launcher_dock_background_shrink"
        val home = currentPage() as MainFragment
        home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_launcher")))
        settleNavigation()
        requireNotNull((currentPage() as SubFragment)
            .findPreference<Preference>("pref_key_launcher_cat_dock")).performClick()
        settleNavigation()
        val page = currentPage() as SubFragment
        val shrink = requireNotNull(page.findPreference<SeekBarPreference>(key))
        val row = LayoutInflater.from(activity).inflate(R.layout.preference_seekbar12, page.listView, false)
        shrink.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row))
        val label = row.findViewById<TextView>(R.id.seekbar_value)
        assertEquals("+1 dp", label.text.toString())
        for ((stored, displayed) in listOf(0 to "-12 dp", 12 to "0 dp", 13 to "+1 dp", 24 to "+12 dp")) {
            shrink.setValue(stored, true)
            assertEquals(displayed, label.text.toString())
            assertEquals(stored, requireNotNull(AppHelper.appPrefs).getInt(key, -1))
        }
    }

    @Test
    fun disabledAnimationsStillShowTheDestinationAndReturnedHome() {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        val home = currentPage() as MainFragment
        assertTrue(home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_system"))))
        settleNavigation()
        assertPreferencePageVisible(currentPage())
        pressToolbarBack()
        assertPreferencePageVisible(currentPage())
    }

    @Test
    fun anUncommittedBackGestureKeepsTheCategoryVisibleAndSystemBackRestoresHome() {
        val home = currentPage() as MainFragment
        assertTrue(home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_system"))))
        settleNavigation()
        val dispatcher = activity.onBackPressedDispatcher
        dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
        // The start event enqueues FragmentManager's preparation; the next input
        // event arrives after that main-loop work on a device.
        shadowOf(Looper.getMainLooper()).idle()
        dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, 0.4f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackCancelled()
        settleNavigation()
        assertTrue(currentPage() is SubFragment)
        assertPreferencePageVisible(currentPage())

        dispatcher.onBackPressed()
        settleNavigation()
        assertTrue(currentPage() is MainFragment)
        assertPreferencePageVisible(currentPage())
    }

    @Test
    fun recreatingTheActivityPreservesTheCategoryAndItsReturnPath() {
        val home = currentPage() as MainFragment
        assertTrue(home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_launcher"))))
        settleNavigation()
        controller.recreate()
        activity = controller.get()
        settleNavigation()
        assertTrue(currentPage() is SubFragment)
        assertPreferencePageVisible(currentPage())
        pressToolbarBack()
        assertTrue(currentPage() is MainFragment)
        assertPreferencePageVisible(currentPage())
    }

    @Test
    fun reversingAPartiallySeekedExitAnimatorRestoresItsStartPosition() {
        val page = currentPage() as MainFragment
        val animator = requireNotNull(page.onCreateAnimator(0, false, R.animator.fragment_close_exit))
        val set = AnimatorSet().apply { playTogether(animator) }
        set.currentPlayTime = 140L
        assertTrue(page.requireView().translationX > 0f)
        set.reverse()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(0f, page.requireView().translationX, 0.01f)
    }

    private fun currentPage(): Fragment = requireNotNull(
        activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
    )

    private fun pressToolbarBack() {
        val item = PopupMenu(activity, activity.window.decorView).menu.add(0, android.R.id.home, 0, "Back")
        assertTrue(activity.onOptionsItemSelected(item))
        settleNavigation()
    }

    private fun settleNavigation() {
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    private fun assertPreferencePageVisible(fragment: Fragment) {
        val page = fragment as PreferenceFragmentBase
        val view = page.requireView()
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals("The page must end inside the viewport", 0f, view.translationX, 0.01f)
        assertEquals(1f, view.alpha, 0.01f)
        assertTrue("Preference XML must load rows", page.preferenceScreen.preferenceCount > 0)
        assertTrue("The preference adapter must contain visible options", (page.listView.adapter?.itemCount ?: 0) > 0)
    }
}
