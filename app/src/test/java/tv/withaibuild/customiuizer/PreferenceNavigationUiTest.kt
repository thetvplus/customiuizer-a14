package tv.withaibuild.customiuizer

import android.app.Application
import android.animation.AnimatorSet
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import androidx.preference.PreferenceGroup
import java.time.Duration
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowApplicationPackageManager
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks
import tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.prefs.SeekBarPreference

/** Runs the production FragmentManager, preference XML and animations on an Android 14 model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class PreferenceNavigationUiTest {

    @Test
    fun homeAndAboutUseTheProductNameForTheSelectedLanguage() {
        for (tag in listOf("en", "ja-JP", "ru-RU", "zh-CN", "zh-TW")) {
            val configuration = android.content.res.Configuration(activity.resources.configuration)
            configuration.setLocale(java.util.Locale.forLanguageTag(tag))
            val context = activity.createConfigurationContext(configuration)
            val expected = if (tag.startsWith("zh")) "米客 A14" else "CustoMIUIzer A14"
            assertEquals(tag, expected, context.getString(R.string.app_name))
            assertEquals(tag, expected, context.getString(R.string.app_name_full))
        }
    }

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    private var maintenanceReceiver: BroadcastReceiver? = null

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
        maintenanceReceiver?.let { activity.unregisterReceiver(it) }
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
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun everyHomeCategoryAndAllItsSettingsPagesAreVisible() {
        requireNotNull(AppHelper.appPrefs).edit().putString("pref_key_system_strong_toast_mode", "3")
            .putBoolean("pref_key_system_island_media", true).apply()
        Helpers.getAllMods(activity, true)
        val standaloneKeys = Helpers.allModsList.filter { it.page != null }.mapNotNull { it.sub }.toSet()
        assertEquals("All standalone page entries, including shared effects, must be indexed", 31, standaloneKeys.size)
        val visited = mutableSetOf<String>()
        capturePage("home")
        for (key in arrayOf("pref_key_system", "pref_key_launcher", "pref_key_controls", "pref_key_various")) {
            val home = currentPage() as MainFragment
            assertTrue(home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>(key))))
            settleNavigation()
            val category = currentPage() as SubFragment
            assertPreferencePageVisible(category)
            capturePage(key)

            val keys = directoryKeys(category.preferenceScreen)
            for (settingKey in keys) {
                val selector = currentPage() as SubFragment
                val settingsEntry = requireNotNull(selector.findPreference<Preference>(settingKey))
                assertEquals("Directories use a navigation disclosure", R.layout.preference_widget_disclosure, settingsEntry.widgetLayoutResource)
                settingsEntry.performClick()
                settleNavigation()
                assertTrue("$settingKey must open a fresh settings page", currentPage() !== selector)
                assertPreferencePageVisible(currentPage())
                capturePage(settingKey)
                for (entryKey in standaloneKeys) {
                    val parent = currentPage() as SubFragment
                    val entry = parent.findPreference<Preference>(entryKey) ?: continue
                    assertEquals("Standalone pages use the same navigation disclosure", R.layout.preference_widget_disclosure, entry.widgetLayoutResource)
                    entry.performClick()
                    settleNavigation()
                    assertTrue("$entryKey must open its standalone page", currentPage() !== parent)
                    assertPreferencePageVisible(currentPage())
                    capturePage(entryKey)
                    visited.add(entryKey)
                    pressToolbarBack()
                    assertSame("Back must restore the owner page", parent, currentPage())
                }
                pressToolbarBack()
                assertPreferencePageVisible(currentPage())
            }
            pressToolbarBack()
            assertTrue(currentPage() is MainFragment)
            assertPreferencePageVisible(currentPage())
        }
        assertEquals(standaloneKeys, visited)
    }

    @Test
    fun searchOpensMovedAndDeepSettingsAndReturnsHome() {
        Helpers.getAllMods(activity, true)
        for (key in arrayOf("pref_key_system_volumeblur_collapsed", "pref_key_system_statusbaricons_wifi",
            "pref_key_system_autobrightness_limitmin", "pref_key_various_disable_miui_daemon",
            "pref_key_system_island_feedback_wifi", "pref_key_system_island_music_down")) {
            val result = Helpers.allModsList.single { it.key == key }
            val home = currentPage() as MainFragment
            assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(home, "openSearchResult",
                ReflectionHelpers.ClassParameter.from(tv.withaibuild.customiuizer.utils.ModData::class.java, result)))
            settleNavigation()
            val page = currentPage() as SubFragment
            assertPreferencePageVisible(page)
            assertTrue("Search must resolve the exact row in its real controller", page.findPreference<Preference>(key) != null)
            pressToolbarBack()
            assertSame(home, currentPage())
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun narrowChinesePageKeepsItsLayoutAndNestedDependenciesAfterRecreation() {
        openSettingsPage("pref_key_system", "pref_key_system_cat_audio")
        val page = currentPage() as SubFragment
        val master = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_volume_mode_button_colors"))
        val color = requireNotNull(page.findPreference<Preference>("pref_key_system_volume_mode_button_icon_color"))
        assertEquals(master.isChecked, color.isEnabled)
        assertTrue(master.callChangeListener(true))
        master.isChecked = true
        assertTrue(color.isEnabled)
        capturePage("audio-320-zh")
        controller.recreate()
        activity = controller.get()
        settleNavigation()
        val restored = currentPage() as SubFragment
        assertPreferencePageVisible(restored)
        assertTrue(requireNotNull(restored.findPreference<CheckBoxPreferenceEx>(master.key)).isChecked)
        assertTrue(requireNotNull(restored.findPreference<Preference>(color.key)).isEnabled)
        pressToolbarBack()
        pressToolbarBack()
        assertTrue(currentPage() is MainFragment)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "zh-rCN-w640dp-h360dp-land-night")
    fun landscapeNightPageAndActionEditorRetainNavigationAndConfirmation() {
        openSettingsPage("pref_key_launcher", "pref_key_launcher_cat_gestures")
        val owner = currentPage() as SubFragment
        requireNotNull(owner.findPreference<Preference>("pref_key_launcher_doubletap")).performClick()
        settleNavigation()
        val editor = currentPage() as SubFragment
        val menu = PopupMenu(activity, editor.requireView()).menu
        editor.onCreateOptionsMenu(menu, activity.menuInflater)
        assertTrue(menu.findItem(R.id.edit_confirm).isVisible)
        assertFalse(menu.findItem(R.id.restart_menu).isVisible)
        val spinner = editor.requireView().findViewById<tv.withaibuild.customiuizer.prefs.SpinnerEx>(R.id.action)
        spinner.setSelection(1)
        settleNavigation()
        val selected = spinner.getSelectedArrayValue()
        assertTrue("The action selector follows the available width", spinner.width > 240 * resourcesDensity())
        capturePage("action-landscape-night-zh")
        assertTrue(editor.onOptionsItemSelected(menu.findItem(R.id.edit_confirm)))
        settleNavigation()
        assertSame(owner, currentPage())
        assertEquals(selected, requireNotNull(AppHelper.appPrefs).getInt("pref_key_launcher_doubletap_action", -1))
        assertPreferencePageVisible(owner)
        capturePage("gestures-landscape-night-zh")
    }

    private fun resourcesDensity() = activity.resources.displayMetrics.density

    private fun openIslandPage(style: String = "3"): SubFragment {
        openSettingsPage("pref_key_system", "pref_key_system_cat_island")
        val page = currentPage() as SubFragment
        val mode = requireNotNull(page.findPreference<ListPreference>("pref_key_system_strong_toast_mode"))
        assertTrue(mode.callChangeListener(style)); mode.value = style
        assertEquals(style == "3", requireNotNull(page.findPreference<Preference>("pref_key_system_island_music_cat")).parent!!.isVisible)
        return page
    }

    private fun openIslandMusic(): SubFragment {
        val island = openIslandPage()
        requireNotNull(island.findPreference<Preference>("pref_key_system_island_music_cat")).performClick()
        settleNavigation()
        return currentPage() as SubFragment
    }

    @Test fun musicSwipeSettingFollowsTheIslandModeAndMediaIntegration() {
        val page = openIslandMusic()
        val media = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media"))
        val swipe = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media_swipe"))
        val floating = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_hide_in_floating_window"))
        assertTrue(media.isEnabled); assertTrue(swipe.isChecked); assertFalse(swipe.isEnabled)
        assertFalse(floating.isEnabled)
        assertTrue(media.widgetLayoutResource != R.layout.preference_widget_disclosure)
        media.isChecked = true; assertTrue(swipe.isEnabled); assertTrue(floating.isEnabled)
        media.isChecked = false; assertFalse(swipe.isEnabled); assertFalse(floating.isEnabled)
        pressToolbarBack(); pressToolbarBack(); pressToolbarBack()
        val disabled = openIslandPage("0")
        assertFalse(requireNotNull(disabled.findPreference<Preference>("pref_key_system_island_music_cat")).isEnabled)
        assertFalse(requireNotNull(disabled.findPreference<Preference>("pref_key_system_island_layout_cat")).isEnabled)
    }

    @Test fun musicReturnRequiresTheModeMediaSwitchAndManualAppSelection() {
        val page = openIslandMusic()
        val media = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media"))
        val returning = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media_return"))
        val apps = requireNotNull(page.findPreference<Preference>("pref_key_system_island_media_return_apps"))
        assertFalse(returning.isChecked); assertFalse(returning.isEnabled); assertFalse(apps.isEnabled)
        media.isChecked = true
        assertTrue(returning.isEnabled); assertFalse(apps.isEnabled)
        returning.isChecked = true; assertTrue(apps.isEnabled)
        assertTrue(requireNotNull(AppHelper.appPrefs).getStringSet(apps.key, emptySet())!!.isEmpty())
        apps.performClick(); settleNavigation()
        assertTrue(currentPage() is tv.withaibuild.customiuizer.subs.AppSelector)
        assertEquals(apps.key, currentPage().arguments?.getString("key"))
        assertTrue(currentPage().arguments?.getBoolean("multi") == true)
        pressToolbarBack()
        media.isChecked = false
        assertFalse(returning.isEnabled); assertFalse(apps.isEnabled)
        pressToolbarBack(); pressToolbarBack(); pressToolbarBack()
        val disabled = openIslandPage("0")
        assertFalse(requireNotNull(disabled.findPreference<Preference>("pref_key_system_island_music_cat")).isEnabled)
        assertFalse(requireNotNull(disabled.findPreference<Preference>("pref_key_system_island_notifications_cat")).isEnabled)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun applicationSearchStaysAboveTheScrollingListWhenTheViewportShrinks() {
        val page = openIslandMusic()
        requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media")).isChecked = true
        requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media_return")).isChecked = true
        requireNotNull(page.findPreference<Preference>("pref_key_system_island_media_return_apps")).performClick()
        settleNavigation()
        val selector = currentPage() as tv.withaibuild.customiuizer.subs.AppSelector
        val fields = requireNotNull(selector.view).findViewById<View>(R.id.fields)
        val header = fields.findViewById<View>(R.id.searchView)
        val list = requireNotNull(selector.listView)
        list.adapter = android.widget.ArrayAdapter(activity, android.R.layout.simple_list_item_1,
            (1..120).map { "Application $it" })
        val originalHeader = android.graphics.Rect()
        for (height in listOf(640, 310)) {
            fields.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            fields.layout(0, 0, 320, height)
            list.setSelection(80)
            fields.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            fields.layout(0, 0, 320, height)
            val headerBounds = android.graphics.Rect(); header.getGlobalVisibleRect(headerBounds)
            val listPosition = IntArray(2); list.getLocationOnScreen(listPosition)
            assertTrue("Rows must stay below the fixed search field", listPosition[1] >= headerBounds.bottom)
            assertTrue("Only the list viewport shrinks for the keyboard", list.height in 1 until height)
            assertTrue("The regression must exercise a scrolled list", list.firstVisiblePosition > 0)
            if (originalHeader.isEmpty) originalHeader.set(headerBounds)
            else assertEquals("Scrolling and resizing must leave search in place", originalHeader, headerBounds)
            assertTrue("Overscrolled children must remain inside the list viewport",
                (list.parent as android.view.ViewGroup).clipChildren)
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun waveformModeKeepsItsCurrentValueAndAVisibleFallbackExplanation() {
        val island = openIslandPage()
        assertFalse(requireNotNull(AppHelper.appPrefs).getBoolean("pref_key_system_island_media", false))
        requireNotNull(island.findPreference<Preference>("pref_key_system_island_effects_cat")).performClick()
        settleNavigation()
        val page = currentPage() as SubFragment
        val wave = requireNotNull(page.findPreference<ListPreference>("pref_key_system_island_waveform_mode"))
        assertTrue("Recording can configure the shared waveform without enabling media", wave.isEnabled)
        assertEquals("1", wave.value)
        wave.value = "2"
        val waveRow = LayoutInflater.from(activity).inflate(wave.layoutResource, null)
        wave.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(waveRow))
        assertEquals(activity.getString(R.string.system_island_waveform_real),
            waveRow.findViewById<TextView>(android.R.id.hint).text.toString())
        val expected = activity.getString(R.string.system_island_waveform_mode_summ)
        val detail = waveRow.findViewById<TextView>(android.R.id.summary)
        assertEquals(View.VISIBLE, detail.visibility)
        assertEquals(expected, detail.text.toString())
        waveRow.measure(View.MeasureSpec.makeMeasureSpec((320 * activity.resources.displayMetrics.density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        waveRow.layout(0, 0, waveRow.measuredWidth, waveRow.measuredHeight)
        fun bounds(view: View) = android.graphics.Rect(0, 0, view.width, view.height).also {
            (waveRow as android.view.ViewGroup).offsetDescendantRectToMyCoords(view, it)
        }
        val descriptionBounds = bounds(detail)
        assertTrue(descriptionBounds.top >= bounds(waveRow.findViewById(android.R.id.title)).bottom)
        assertTrue(descriptionBounds.bottom <= waveRow.height)
        assertTrue(descriptionBounds.right <= bounds(waveRow.findViewById(android.R.id.hint)).left)
        val adapter = page.listView.adapter as androidx.preference.PreferenceGroupAdapter
        page.listView.scrollToPosition(adapter.getPreferenceAdapterPosition(wave))
        settleNavigation()
        capturePage("island-waveform-320-zh")
    }

    @Test fun islandGroupsFollowContentAppearanceAndInteractionAndDisableAsAWhole() {
        val page = openIslandPage()
        fun pref(suffix: String) = requireNotNull(page.findPreference<Preference>("pref_key_system_island_$suffix"))
        val content = requireNotNull(pref("music_cat").parent)
        val appearance = requireNotNull(pref("layout_cat").parent)
        val interaction = requireNotNull(pref("actions_cat").parent)
        assertSame(content, pref("excluded_apps").parent)
        assertEquals(null, page.findPreference<Preference>("pref_key_system_island_hide_in_floating_window"))
        assertSame(appearance, pref("effects_cat").parent)
        assertSame(interaction, pref("idle").parent)
        assertTrue(setOf(content, appearance, interaction).size == 3)
        for (suffix in listOf("waveform_mode", "entrance_style", "simplified", "expand_down"))
            assertEquals(null, page.findPreference<Preference>("pref_key_system_island_$suffix"))
        val mode = requireNotNull(page.findPreference<ListPreference>("pref_key_system_strong_toast_mode"))
        assertTrue(mode.callChangeListener("0")); mode.value = "0"
        for (group in listOf(content, appearance, interaction)) {
            assertFalse(group.isVisible)
        }
        for (suffix in listOf("music_cat", "effects_cat", "idle")) assertFalse(pref(suffix).isEnabled)
        assertTrue(mode.callChangeListener("3")); mode.value = "3"
        for (group in listOf(content, appearance, interaction)) {
            assertTrue(group.isVisible)
        }
        for (suffix in listOf("music_cat", "effects_cat", "idle")) assertTrue(pref(suffix).isEnabled)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun musicEditorUsesCurrentDefaultsAndKeepsIndependentActionsWhenTrackSwipesAreOff() {
        val mediaPage = openIslandMusic()
        requireNotNull(mediaPage.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media")).isChecked=true
        pressToolbarBack()
        val islandPage = currentPage() as SubFragment
        requireNotNull(islandPage.findPreference<Preference>("pref_key_system_island_actions_cat")).performClick(); settleNavigation()
        val musicPage = currentPage() as SubFragment
        val key="pref_key_system_island_music_left"
        val left=requireNotNull(musicPage.findPreference<Preference>(key))
        assertTrue(left.isEnabled)
        left.performClick(); settleNavigation()
        val editor=currentPage() as SubFragment
        val spinner=editor.requireView().findViewById<tv.withaibuild.customiuizer.prefs.SpinnerEx>(R.id.action)
        assertEquals(87,spinner.getSelectedArrayValue())
        spinner.setSelection(requireNotNull(spinner.entryValues).indexOf(18)); settleNavigation()
        capturePage("music-action-320-zh")
        val menu=PopupMenu(activity,editor.requireView()).menu
        editor.onCreateOptionsMenu(menu,activity.menuInflater)
        assertTrue(editor.onOptionsItemSelected(menu.findItem(R.id.edit_confirm))); settleNavigation()
        assertSame(musicPage,currentPage())
        assertEquals(18,requireNotNull(AppHelper.appPrefs).getInt(key+"_action",-1))
        assertEquals(activity.getString(R.string.array_global_actions_volume_up),left.summary.toString())
        requireNotNull(AppHelper.appPrefs).edit().putBoolean("pref_key_system_island_media_swipe", false).apply()
        assertTrue(left.isEnabled)
        val idleDown = requireNotNull(musicPage.findPreference<Preference>("pref_key_system_island_idle_down"))
        assertEquals(activity.resources.getStringArray(R.array.global_actions_island_music)[activity.resources.getIntArray(R.array.global_actions_island_music_val).indexOf(2)], idleDown.summary.toString())
        pressToolbarBack()
        val page = currentPage() as SubFragment
        requireNotNull(page.findPreference<Preference>("pref_key_system_island_feedback_cat")).performClick(); settleNavigation()
        assertFalse(requireNotNull((currentPage() as SubFragment).findPreference<CheckBoxPreferenceEx>("pref_key_system_island_feedback_wifi")).isChecked)
        val sources = requireNotNull((currentPage() as SubFragment)
            .findPreference<PreferenceGroup>("pref_key_system_island_live_kinds"))
        assertEquals(10, sources.preferenceCount) // Intro, eight sources, and the shared return switch.
        assertTrue(isSupportingPreference(sources.getPreference(0)))
        assertEquals(activity.getString(R.string.system_island_live_sources_summary), sources.getPreference(0).summary)
        activity.resources.getStringArray(R.array.system_island_live_values).forEachIndexed { index, value ->
            assertEquals("Source order must remain semantic in every language", "pref_key_system_island_live_kinds_" + value,
                sources.getPreference(index + 1).key)
        }
        assertEquals("pref_key_system_island_live_return", sources.getPreference(9).key)
        val recorder = requireNotNull((currentPage() as SubFragment)
            .findPreference<CheckBoxPreferenceEx>("pref_key_system_island_live_kinds_recorder"))
        assertFalse(recorder.isPersistent)
        assertFalse(recorder.isChecked)
        recorder.performClick(); settleNavigation()
        assertTrue(recorder.isChecked)
        assertEquals(setOf("recorder"), AppHelper.appPrefs!!.getStringSet("pref_key_system_island_live_kinds", emptySet()))
        assertFalse(AppHelper.appPrefs!!.contains("pref_key_system_island_live_kinds_recorder"))
        recorder.performClick(); settleNavigation()
        assertTrue(AppHelper.appPrefs!!.getStringSet("pref_key_system_island_live_kinds", emptySet())!!.isEmpty())
        assertTrue(requireNotNull((currentPage() as SubFragment)
            .findPreference<CheckBoxPreferenceEx>("pref_key_system_island_live_return")).isChecked)
        pressToolbarBack()
        requireNotNull(page.findPreference<Preference>("pref_key_system_island_notifications_cat")).performClick(); settleNavigation()
        val notifications = currentPage() as SubFragment
        val chat=requireNotNull(notifications.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_chat"))
        val allowed=requireNotNull(notifications.findPreference<Preference>("pref_key_system_island_notification_apps"))
        assertFalse(allowed.isEnabled); chat.isChecked=true; assertTrue(allowed.isEnabled)
        assertTrue(requireNotNull(AppHelper.appPrefs).getStringSet(allowed.key,emptySet())!!.isEmpty())
    }

    private fun openSettingsPage(categoryKey: String, pageKey: String) {
        val home = currentPage() as MainFragment
        assertTrue(home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>(categoryKey))))
        settleNavigation()
        requireNotNull((currentPage() as SubFragment).findPreference<Preference>(pageKey)).performClick()
        settleNavigation()
        assertPreferencePageVisible(currentPage())
    }

    @Test
    fun aboutCommunityOpensTheCanonicalUrlOnlyOnClick() {
        val home = currentPage() as MainFragment
        val about = PopupMenu(activity, home.requireView()).menu.add(0, R.id.about, 0, "About")
        home.onOptionsItemSelected(about)
        settleNavigation()
        val page = currentPage() as AboutFragment
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        val view = page.requireView()
        view.measure(View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY))
        view.layout(view.left, view.top, view.right, view.bottom)
        val community = view.findViewById<View>(R.id.about_community_row)
        assertTrue(community.isShown)
        assertTrue(community.width > 0 && community.height > 0)
        assertEquals(BuildConfig.COMMUNITY_URL.removePrefix("https://"),
            view.findViewById<TextView>(R.id.about_community_summary).text.toString())
        capturePage("about-community", view)
        community.performClick()
        val intent = requireNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(BuildConfig.COMMUNITY_URL, intent.dataString)
        assertEquals(null, shadowOf(activity).nextStartedActivity)
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
    fun navigationPageKeepsKeyboardFixOppositeToVisibleBarActions() {
        val home = currentPage() as MainFragment
        home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_controls")))
        settleNavigation()
        requireNotNull((currentPage() as SubFragment)
            .findPreference<Preference>("pref_key_controls_cat_navbar")).performClick()
        settleNavigation()
        val page = currentPage() as SubFragment
        val hidden = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_controls_nonavbar"))
        val keyboard = requireNotNull(page.findPreference<Preference>("pref_key_controls_nonavbar_fix_inputmethod"))
        val back = requireNotNull(page.findPreference<Preference>("pref_key_controls_backlong"))
        assertFalse(keyboard.isEnabled)
        assertTrue(back.isEnabled)
        assertTrue(hidden.callChangeListener(true))
        hidden.isChecked = true
        assertTrue(keyboard.isEnabled)
        assertFalse(back.isEnabled)
        pressToolbarBack()
        requireNotNull((currentPage() as SubFragment)
            .findPreference<Preference>("pref_key_controls_cat_navbar")).performClick()
        settleNavigation()
        val restored = currentPage() as SubFragment
        assertTrue(requireNotNull(restored.findPreference<Preference>(keyboard.key)).isEnabled)
        val restoredHidden = requireNotNull(restored.findPreference<CheckBoxPreferenceEx>(hidden.key))
        assertTrue(restoredHidden.callChangeListener(false))
        restoredHidden.isChecked = false
        assertFalse(requireNotNull(restored.findPreference<Preference>(keyboard.key)).isEnabled)
        assertTrue(requireNotNull(restored.findPreference<Preference>(back.key)).isEnabled)
    }

    @Test
    fun removedDockMaterialMigratesToPanelAndKeepsBothGlassMaterialsSelectable() {
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
        assertEquals(listOf("0", "2", "3"), material.entryValues.map { it.toString() })
        assertEquals("0", material.value)
        assertEquals("0", requireNotNull(AppHelper.appPrefs).getString(key, null))
        material.value = "2"
        pressToolbarBack()
        requireNotNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_launcher_cat_dock")).performClick()
        settleNavigation()
        assertEquals("2", (currentPage() as SubFragment).findPreference<ListPreference>(key)?.value)
        requireNotNull((currentPage() as SubFragment).findPreference<ListPreference>(key)).value = "3"
        pressToolbarBack()
        requireNotNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_launcher_cat_dock")).performClick()
        settleNavigation()
        assertEquals("3", (currentPage() as SubFragment).findPreference<ListPreference>(key)?.value)
    }

    @Test
    fun dockHeightAndSideSpacingHaveIndependentValuesAndRestoreAfterNavigation() {
        val key = "pref_key_launcher_dock_background_inset"
        val home = currentPage() as MainFragment
        home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_launcher")))
        settleNavigation()
        requireNotNull((currentPage() as SubFragment)
            .findPreference<Preference>("pref_key_launcher_cat_dock")).performClick()
        settleNavigation()
        val page = currentPage() as SubFragment
        val inset = requireNotNull(page.findPreference<SeekBarPreference>(key))
        val row = LayoutInflater.from(activity).inflate(R.layout.preference_seekbar12, page.listView, false)
        inset.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row))
        val label = row.findViewById<TextView>(R.id.seekbar_value)
        for ((stored, displayed) in listOf(12 to "12 dp", 17 to "17 dp", 24 to "24 dp")) {
            inset.setValue(stored, true)
            assertEquals(displayed, label.text.toString())
            assertEquals(stored, requireNotNull(AppHelper.appPrefs).getInt(key, -1))
        }
        val shrinkKey = "pref_key_launcher_dock_background_shrink"
        val shrink = requireNotNull(page.findPreference<SeekBarPreference>(shrinkKey))
        shrink.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row))
        for ((stored, displayed) in listOf(12 to "-5 dp", 17 to "0 dp", 22 to "+5 dp", 34 to "+17 dp")) {
            shrink.setValue(stored, true)
            assertEquals(displayed, label.text.toString())
            assertEquals(stored, requireNotNull(AppHelper.appPrefs).getInt(shrinkKey, -1))
            assertEquals(24, requireNotNull(AppHelper.appPrefs).getInt(key, -1))
        }
        pressToolbarBack()
        requireNotNull((currentPage() as SubFragment)
            .findPreference<Preference>("pref_key_launcher_cat_dock")).performClick()
        settleNavigation()
        requireNotNull((currentPage() as SubFragment).findPreference<SeekBarPreference>(shrinkKey))
        assertEquals(34, requireNotNull(AppHelper.appPrefs).getInt(shrinkKey, -1))
    }

    @Test
    fun toastPositionMigratesLegacyBottomAndShowsOnlyTheSelectedOffsetAfterReturning() {
        requireNotNull(AppHelper.appPrefs).edit().putInt("pref_key_system_toast_bottom", 120)
            .remove("pref_key_system_toast_position").commit()
        val home = currentPage() as MainFragment
        home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_system")))
        settleNavigation()
        requireNotNull((currentPage() as SubFragment)
            .findPreference<Preference>("pref_key_system_cat_toasts")).performClick()
        settleNavigation()
        val page = currentPage() as SubFragment
        val position = requireNotNull(page.findPreference<ListPreference>("pref_key_system_toast_position"))
        assertEquals("3", position.value)
        for (value in listOf("0", "1", "2", "3")) {
            assertTrue(position.callChangeListener(value))
            position.value = value
            for ((mode, key) in listOf("1" to "top", "2" to "center", "3" to "bottom")) {
                assertEquals(mode == value, requireNotNull(page.findPreference<Preference>("pref_key_system_toast_$key")).isVisible)
            }
        }
        assertTrue(position.callChangeListener("2"))
        position.value = "2"
        val center = requireNotNull(page.findPreference<SeekBarPreference>("pref_key_system_toast_center"))
        val row = LayoutInflater.from(activity).inflate(R.layout.preference_seekbar12, page.listView, false)
        center.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row))
        for ((stored, displayed) in listOf(0 to "-400 dp", 400 to "0 dp", 430 to "+30 dp", 800 to "+400 dp")) {
            center.setValue(stored, true)
            assertEquals(displayed, row.findViewById<TextView>(R.id.seekbar_value).text.toString())
        }
        pressToolbarBack()
        requireNotNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_system_cat_toasts")).performClick()
        settleNavigation()
        val restored = currentPage() as SubFragment
        assertEquals("2", restored.findPreference<ListPreference>("pref_key_system_toast_position")?.value)
        assertTrue(requireNotNull(restored.findPreference<Preference>("pref_key_system_toast_center")).isVisible)
        assertEquals(120, requireNotNull(AppHelper.appPrefs).getInt("pref_key_system_toast_bottom", -1))
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

    @Test
    fun configurationCommitFailureCompensatesTheSuccessfulSystemMutationOnce() {
        installSystemPackage("com.miui.daemon")
        val transport = installMaintenanceBridge()
        val toggle = requireNotNull(openExclusivePage().findPreference<CheckBoxPreferenceEx>("pref_key_various_disable_miui_daemon"))
        val originalPrefs = requireNotNull(AppHelper.appPrefs)
        var failNext = true
        AppHelper.appPrefs = object : android.content.SharedPreferences by originalPrefs {
            override fun edit(): android.content.SharedPreferences.Editor {
                val editor = originalPrefs.edit()
                return object : android.content.SharedPreferences.Editor by editor {
                    override fun commit(): Boolean {
                        if (failNext) { failNext = false; return false }
                        return editor.commit()
                    }
                    override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor {
                        editor.putBoolean(key, value); return this
                    }
                }
            }
        }
        toggle.callChangeListener(true)
        confirmLatestDialog()
        assertEquals(0, activity.packageManager.getApplicationEnabledSetting("com.miui.daemon"))
        assertEquals(2, transport.sends)
        assertFalse(toggle.isChecked)
        assertFalse(tv.withaibuild.customiuizer.utils.MaintenanceJournal.open(activity).contains(toggle.key))
    }

    @Test
    fun interruptedRestrictionRemainsRecoverableAfterReopeningTheSettingsPage() {
        installSystemPackage("com.miui.daemon")
        val journal = tv.withaibuild.customiuizer.utils.MaintenanceJournal.open(activity)
        val intent = Intent(GlobalActions.SET_MIUI_DAEMON_STATE_ACTION)
            .putExtra(GlobalActions.EXTRA_APPLICATION_STATE, 3)
        journal.prepare("pref_key_various_disable_miui_daemon", intent, true)
        activity.packageManager.setApplicationEnabledSetting("com.miui.daemon", 3, 0)
        installMaintenanceBridge()
        val toggle = requireNotNull(openExclusivePage().findPreference<CheckBoxPreferenceEx>("pref_key_various_disable_miui_daemon"))
        assertTrue(toggle.isChecked)
        assertFalse(requireNotNull(AppHelper.appPrefs).getBoolean(toggle.key, false))
        assertEquals(activity.getString(R.string.various_maintenance_recovery), toggle.summary)
        toggle.callChangeListener(false)
        settleNavigation()
        assertEquals(0, activity.packageManager.getApplicationEnabledSetting("com.miui.daemon"))
        assertFalse(toggle.isChecked)
        assertFalse(journal.contains(toggle.key))
    }

    @Test
    fun daemonRestoreThenDisableAgainCapturesTheNewOriginalState() {
        installSystemPackage("com.miui.daemon")
        activity.packageManager.setApplicationEnabledSetting("com.miui.daemon", 3, 0)
        requireNotNull(AppHelper.appPrefs).edit()
            .putBoolean("pref_key_various_disable_miui_daemon", true)
            .putInt("internal_miui_daemon_application_state", 0).commit()
        installMaintenanceBridge()
        val page = openExclusivePage()
        val toggle = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_various_disable_miui_daemon"))
        assertFalse(toggle.callChangeListener(false))
        settleNavigation()
        assertEquals(0, activity.packageManager.getApplicationEnabledSetting("com.miui.daemon"))
        toggle.callChangeListener(true)
        confirmLatestDialog()
        assertEquals(0, requireNotNull(AppHelper.appPrefs).getInt("internal_miui_daemon_application_state", -1))
        toggle.callChangeListener(false)
        settleNavigation()
        assertEquals(0, activity.packageManager.getApplicationEnabledSetting("com.miui.daemon"))
        assertFalse(toggle.isChecked)
    }

    @Test
    fun updaterRestoreThenDisableAgainCapturesTheNewComponentState() {
        val service = ComponentName("com.android.updater", "com.android.updater.UpdateService")
        installSystemPackage(service.packageName, service.className)
        activity.packageManager.setComponentEnabledSetting(service, 2, 0)
        requireNotNull(AppHelper.appPrefs).edit()
            .putBoolean("pref_key_various_disable_update_services", true)
            .putString("internal_updater_service_names", service.className)
            .putString("internal_updater_service_states", "0").commit()
        installMaintenanceBridge()
        val page = openExclusivePage()
        val toggle = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_various_disable_update_services"))
        toggle.callChangeListener(false)
        settleNavigation()
        assertEquals(0, activity.packageManager.getComponentEnabledSetting(service))
        toggle.callChangeListener(true)
        confirmLatestDialog()
        assertEquals("0", requireNotNull(AppHelper.appPrefs).getString("internal_updater_service_states", null))
        toggle.callChangeListener(false)
        settleNavigation()
        assertEquals(0, activity.packageManager.getComponentEnabledSetting(service))
        assertFalse(toggle.isChecked)
    }

    @Test
    fun domesticSecurityCenterTrimsOnlyItsDeclaredAnalyticsReceiverAndRestoresIt() {
        val packageId = "com.miui.securitycenter"
        val analytics = ComponentName(packageId, "com.miui.analytics.RemoteAnalyticsReceiver")
        val guard = ComponentName(packageId, "com.miui.antivirus.service.GuardService")
        installSystemPackage(packageId, service = guard.className, receiver = analytics.className)
        installMaintenanceBridge()
        val toggle = requireNotNull(openExclusivePage().findPreference<CheckBoxPreferenceEx>(
            "pref_key_various_trim_security_center_marketing",
        ))
        assertTrue(toggle.isEnabled)
        toggle.callChangeListener(true)
        confirmLatestDialog()
        assertTrue(toggle.isChecked)
        assertEquals(2, activity.packageManager.getComponentEnabledSetting(analytics))
        assertEquals(0, activity.packageManager.getComponentEnabledSetting(guard))
        toggle.callChangeListener(false)
        settleNavigation()
        assertFalse(toggle.isChecked)
        assertEquals(0, activity.packageManager.getComponentEnabledSetting(analytics))
        assertEquals(0, activity.packageManager.getComponentEnabledSetting(guard))
    }

    @Test
    fun failedMaintenanceSendLeavesTheActionEnabledForRetry() {
        val transport = installMaintenanceBridge()
        transport.failure = SecurityException("bridge unavailable")
        val action = requireNotNull(openExclusivePage().findPreference<Preference>("pref_key_various_clear_update_state"))
        action.performClick()
        confirmLatestDialog()
        assertTrue(action.isEnabled)
        transport.failure = null
        action.performClick()
        confirmLatestDialog()
        assertEquals(2, transport.sends)
        assertTrue(action.isEnabled)
    }

    @Test
    fun fatalMaintenanceSendStillPropagatesAndReleasesTheDisabledControl() {
        val transport = installMaintenanceBridge()
        val fatal = OutOfMemoryError("bridge send")
        transport.failure = fatal
        val action = requireNotNull(openExclusivePage().findPreference<Preference>("pref_key_various_clear_update_state"))
        action.performClick()
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { confirmLatestDialog() })
        assertTrue(action.isEnabled)
    }

    @Test
    @Config(shadows = [CoercingPackageManager::class])
    fun daemonReadbackMismatchRollsBackTheActualPackageState() {
        installSystemPackage("com.miui.daemon")
        val apply = GlobalActionSystemServerHooks::class.java.getDeclaredMethod(
            "applyMiuiDaemonState", Context::class.java, Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        assertEquals(false, apply.invoke(GlobalActionSystemServerHooks, activity, 3))
        assertEquals(0, activity.packageManager.getApplicationEnabledSetting("com.miui.daemon"))
    }

    @Suppress("DEPRECATION")
    private fun installSystemPackage(packageId: String, service: String? = null, receiver: String? = null) {
        val app = ApplicationInfo().apply {
            packageName = packageId
            flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_INSTALLED
            enabled = true
        }
        shadowOf(activity.packageManager).installPackage(PackageInfo().apply {
            packageName = packageId
            applicationInfo = app
            signatures = arrayOf(android.content.pm.Signature(byteArrayOf(1, 2, 3)))
            if (service != null) services = arrayOf(ServiceInfo().apply {
                packageName = packageId
                name = service
                applicationInfo = app
            })
            if (receiver != null) receivers = arrayOf(ActivityInfo().apply {
                packageName = packageId
                name = receiver
                applicationInfo = app
            })
        })
    }

    private fun openExclusivePage(): SubFragment {
        val home = currentPage() as MainFragment
        home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_various")))
        settleNavigation()
        requireNotNull((currentPage() as SubFragment)
            .findPreference<Preference>("pref_key_various_cat_exclusive")).performClick()
        settleNavigation()
        return currentPage() as SubFragment
    }

    private fun confirmLatestDialog() {
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        settleNavigation()
    }

    private fun installMaintenanceBridge(): MaintenanceTransport {
        val transport = MaintenanceTransport(activity.baseContext)
        ReflectionHelpers.setField(activity, "mBase", transport)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != GlobalActions.CLEAR_UPDATER_STATE_ACTION &&
                    !tv.withaibuild.customiuizer.utils.MaintenanceJournal.matches(context, intent)) {
                    resultCode = GlobalActions.ACTION_FAILED
                    return
                }
                when (intent.action) {
                    GlobalActions.SET_MIUI_DAEMON_STATE_ACTION -> context.packageManager.setApplicationEnabledSetting(
                        "com.miui.daemon", intent.getIntExtra(GlobalActions.EXTRA_APPLICATION_STATE, -1), 0,
                    )
                    GlobalActions.SET_UPDATER_SERVICES_ACTION -> {
                        val names = requireNotNull(intent.getStringArrayExtra(GlobalActions.EXTRA_UPDATER_SERVICE_NAMES))
                        val states = requireNotNull(intent.getIntArrayExtra(GlobalActions.EXTRA_UPDATER_SERVICE_STATES))
                        for (i in names.indices) context.packageManager.setComponentEnabledSetting(
                            ComponentName("com.android.updater", names[i]), states[i], 0,
                        )
                    }
                    GlobalActions.SET_XIAOMI_COMPONENTS_ACTION -> {
                        // Exercise the production allowlist, declaration checks and rollback-capable writer.
                        val apply = GlobalActionSystemServerHooks::class.java.getDeclaredMethod(
                            "applyXiaomiComponentStates", Context::class.java, Array<String>::class.java,
                            Array<String>::class.java, IntArray::class.java,
                        ).apply { isAccessible = true }
                        val handled = apply.invoke(GlobalActionSystemServerHooks, context,
                            intent.getStringArrayExtra(GlobalActions.EXTRA_PACKAGE_NAMES),
                            intent.getStringArrayExtra(GlobalActions.EXTRA_COMPONENT_NAMES),
                            intent.getIntArrayExtra(GlobalActions.EXTRA_COMPONENT_STATES)) == true
                        resultCode = if (handled) GlobalActions.ACTION_HANDLED else GlobalActions.ACTION_FAILED
                        return
                    }
                }
                resultCode = GlobalActions.ACTION_HANDLED
            }
        }
        activity.registerReceiver(receiver, IntentFilter(GlobalActions.SET_MIUI_DAEMON_STATE_ACTION).apply {
            addAction(GlobalActions.SET_UPDATER_SERVICES_ACTION)
            addAction(GlobalActions.CLEAR_UPDATER_STATE_ACTION)
            addAction(GlobalActions.SET_XIAOMI_COMPONENTS_ACTION)
        }, Context.RECEIVER_EXPORTED)
        maintenanceReceiver = receiver
        return transport
    }

    private class MaintenanceTransport(base: Context) : ContextWrapper(base) {
        var failure: Throwable? = null
        var sends = 0
        override fun sendOrderedBroadcast(intent: Intent, receiverPermission: String?, options: Bundle?,
            resultReceiver: BroadcastReceiver?, scheduler: Handler?, initialCode: Int, initialData: String?, initialExtras: Bundle?,
        ) {
            sends++
            failure?.let { throw it }
            assertEquals("android", intent.`package`)
            // Run the ordered result chain against a local framework fixture, preserving the real callback path.
            // Robolectric models the ordered result chain through the overload without options.
            super.sendOrderedBroadcast(Intent(intent).setPackage(packageName), receiverPermission,
                resultReceiver, scheduler, initialCode, initialData, initialExtras)
        }
    }

    @Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
    class CoercingPackageManager : ShadowApplicationPackageManager() {
        @Implementation
        override fun setApplicationEnabledSetting(packageName: String, newState: Int, flags: Int) {
            super.setApplicationEnabledSetting(packageName, if (newState == 3) 2 else newState, flags)
        }
    }

    private fun currentPage(): Fragment = requireNotNull(
        activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
    )

    private fun directoryKeys(group: PreferenceGroup): List<String> = buildList {
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            if (preference is PreferenceGroup) addAll(directoryKeys(preference)) else add(requireNotNull(preference.key))
        }
    }

    private fun capturePage(name: String, view: View = activity.window.decorView) {
        val directory = System.getenv("A14_SETTINGS_RENDER_DIR")?.let(::File) ?: return
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

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
        val menu = PopupMenu(activity, view).menu
        page.onCreateOptionsMenu(menu, activity.menuInflater)
        for (id in intArrayOf(R.id.restartlauncher, R.id.restartsystemui, R.id.restartsecuritycenter, R.id.softreboot)) {
            assertEquals("Restart actions belong to home", page is MainFragment, menu.findItem(id)?.isVisible == true)
            if (page !is MainFragment) {
                val action = menu.findItem(id) ?: menu.add(0, id, 0, "Restart")
                assertFalse("Subpages must also reject a stale restart action", page.onOptionsItemSelected(action))
            }
        }
    }
}
