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
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import tv.withaibuild.customiuizer.ui.settingsView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
    @get:Rule val compose = createEmptyComposeRule()

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
    fun pageAnimatorsLeaveSystemDurationScalingToAndroid() {
        val page = currentPage() as MainFragment
        for (scale in listOf(0f, .5f, 1f, 2f, 10f)) {
            Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
            for (resource in listOf(R.animator.a14_settings_open_enter, R.animator.a14_settings_open_exit,
                R.animator.a14_settings_close_enter, R.animator.a14_settings_close_exit)) {
                val animator = requireNotNull(page.onCreateAnimator(0, true, resource))
                assertEquals("Android applies $scale itself; the requested duration stays unscaled", 350L, animator.duration)
            }
        }
    }

    @Test
    fun collectingAnAnimatorPreservesTheFrameworksPendingVisibility() {
        val page = currentPage() as MainFragment
        val view = page.requireView()
        view.alpha = 0f
        view.translationX = 0f
        val animator = page.onCreateAnimator(0, true, R.animator.a14_settings_open_enter)

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
        assertEquals("All standalone page entries, including Wi-Fi automatic settings, must be indexed", 36, standaloneKeys.size)
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
                assertTrue("Directory entries remain selectable", settingsEntry.isSelectable)
                settingsEntry.performClick()
                settleNavigation()
                assertTrue("$settingKey must open a fresh settings page", currentPage() !== selector)
                assertPreferencePageVisible(currentPage())
                capturePage(settingKey)
                if (settingKey in standaloneKeys) visited.add(settingKey)
                for (entryKey in standaloneKeys) {
                    val parent = currentPage() as SubFragment
                    val entry = parent.findPreference<Preference>(entryKey) ?: continue
                    assertTrue("Standalone entries remain selectable", entry.isSelectable)
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

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun selectionDialogsFallBackToSettingTitles() {
        for (key in listOf(tv.withaibuild.customiuizer.utils.AppLocaleController.LOCALE_PREF_KEY,
            "pref_key_miuizer_settingsiconpos")) {
            val preference = requireNotNull((currentPage() as MainFragment).findPreference<ListPreference>(key))
            compose.onNodeWithTag("settings.list").performScrollToNode(hasTestTag(key))
            compose.onNodeWithTag(key).performClick()
            compose.onNode(hasText(preference.title.toString()) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNodeWithText("null").assertDoesNotExist()
            val layouts = ArrayList<TextLayoutResult>()
            compose.onNode(hasText((preference as ListPreference).entries.first().toString()) and
                hasAnyAncestor(isDialog()), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
            val style = layouts.single().layoutInput.style
            assertEquals(16.sp, style.fontSize)
            assertEquals(TextMotion.Animated, style.textMotion)
            assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.preference_primary_text_color), style.color.toArgb())
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun appsAndNetworkRulesOpenDirectlyFromTheirVisibleDirectory() {
        val home = currentPage() as MainFragment
        home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_various")))
        settleNavigation()
        val selector = currentPage() as SubFragment
        for ((suffix, controller, setting) in listOf(
            Triple("appbehavior", "System_AppBehavior", "rotation"),
            Triple("wifiauto", "System_WifiAutomatic", "enabled"),
            Triple("vpnauto", "System_VpnAutoSwitch", "enabled"),
        )) {
            val key = "pref_key_system_${suffix}_cat"
            compose.onNodeWithTag("settings.list").performScrollToNode(hasTestTag(key))
            compose.onNodeWithTag(key).performClick()
            settleNavigation()
            val page = currentPage() as SubFragment
            assertEquals(controller, page.javaClass.simpleName)
            assertTrue(requireNotNull(page.arguments).getBoolean("isStandalone"))
            assertEquals(key, requireNotNull(page.arguments).getString("sub"))
            val title = requireNotNull(selector.findPreference<Preference>(key)).title.toString()
            assertEquals(title, activity.settingsBar.titleText.toString())
            assertSame(page, activity.settingsBar.owner)
            compose.onNode(hasText(title) and !hasAnyAncestor(hasTestTag("settings.list"))).assertIsDisplayed()
            assertTrue(page !== selector)
            assertTrue(page.findPreference<Preference>("pref_key_system_${suffix}_$setting") != null)
            assertPreferencePageVisible(page)
            pressToolbarBack()
            assertSame(selector, currentPage())
        }
        compose.onNodeWithTag("settings.list").performScrollToNode(hasTestTag("pref_key_system_cat_automation"))
        compose.onNodeWithTag("pref_key_system_cat_automation").performClick()
        settleNavigation()
        val connections = currentPage() as SubFragment
        assertTrue(connections.findPreference<Preference>("pref_key_system_wifipassword") != null)
        assertTrue(connections.findPreference<Preference>("pref_key_system_usb_default_function") != null)
        assertNull(connections.findPreference<Preference>("pref_key_system_appbehavior_cat"))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    class RippleProbeFragment : Fragment() {
        var hasOverscroll = false
        var hasRipple = false
        override fun onCreateView(inflater: LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View = settingsView {
            val overscroll = LocalOverscrollFactory.current
            val ripple = LocalRippleConfiguration.current
            SideEffect { hasOverscroll = overscroll != null; hasRipple = ripple != null }
            Text("Moving text", style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun nativeOverscrollIsAvailableAndRipplesLeaveWithTheirPage() {
        val page = RippleProbeFragment()
        activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, page).commitNow()
        settleNavigation()
        assertTrue(page.hasOverscroll)
        assertTrue(page.hasRipple)
        controller.pause()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertFalse("An exiting page must detach its pressed indication", page.hasRipple)
        controller.resume()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertTrue("Returning to the page restores native feedback", page.hasRipple)
    }

    @Test
    fun lightSystemBarsMatchTheSettingsBackground() = assertSystemBarBackground(0xffefeff4.toInt())

    @Test
    fun mainWindowResizesForTheImeWithoutConsumingComposeInsets() {
        val mode = android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        assertEquals(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            activity.window.attributes.softInputMode and mode)
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        assertFalse(content.getChildAt(0).fitsSystemWindows)
        val root = content.getChildAt(0) as android.view.ViewGroup
        assertFalse((root.getChildAt(0) as androidx.compose.ui.platform.ComposeView).consumeWindowInsets)
        assertFalse(activity.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.preference_compose).consumeWindowInsets)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-night")
    fun darkSystemBarsMatchTheSettingsBackground() = assertSystemBarBackground(0xff000000.toInt())

    @Suppress("DEPRECATION")
    private fun assertSystemBarBackground(expected: Int) {
        assertEquals(expected, activity.window.statusBarColor)
        assertEquals(expected, activity.window.navigationBarColor)
        val translucent = android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        assertEquals(0, activity.window.attributes.flags and translucent)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun overflowKeepsRestartActionsInTheirOwnCompactSubmenu() {
        compose.onNodeWithTag("menu.expand").performClick()
        compose.onNodeWithTag("menu.item:" + R.id.restart_menu).assertIsDisplayed()
        compose.onNodeWithTag("menu.item:" + R.id.restartlauncher).assertDoesNotExist()
        compose.onNodeWithTag("menu.item:" + R.id.restart_menu).performClick()
        compose.onNodeWithTag("menu.item:" + R.id.restartlauncher).assertIsDisplayed()
        compose.onNodeWithTag("menu.item:" + R.id.backuprestore).assertDoesNotExist()
        compose.onNodeWithTag("menu.back").performClick()
        compose.onNodeWithTag("menu.item:" + R.id.backuprestore).assertIsDisplayed()
        compose.onNodeWithTag("menu.item:" + R.id.about).performClick()
        settleNavigation()
        assertTrue(currentPage() is AboutFragment)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun homeSearchStartsCollapsedAndBackClosesAnEmptyOrPopulatedSearch() {
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        for (query in listOf("", "brightness")) {
            compose.onNodeWithTag("search.expand").performClick()
            compose.onNodeWithTag("home.search").assertIsFocused().performTextInput(query)
            activity.onBackPressedDispatcher.onBackPressed()
            settleNavigation()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            assertTrue(currentPage() is MainFragment)
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun titlesAndSummariesAreCenteredInTheWholeClickableRow() {
        val page = currentPage() as MainFragment
        for (key in listOf("pref_key_system", "pref_key_launcher", "pref_key_controls", "pref_key_various",
            "pref_key_miuizer_locale", "pref_key_miuizer_launchericon")) {
            val preference = requireNotNull(page.findPreference<Preference>(key))
            val row = compose.onNodeWithTag(key).fetchSemanticsNode().boundsInRoot
            val title = compose.onNode(hasText(preference.title.toString()) and hasAnyAncestor(hasTestTag(key)), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val summary = preference.summary?.toString().orEmpty()
            val bottom = if (summary.isBlank()) title.bottom else compose.onNode(
                hasText(summary) and hasAnyAncestor(hasTestTag(key)), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.bottom
            assertEquals(key, row.center.y, (title.top + bottom) / 2f, 1f)
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun searchNavigationClearsQueryAndFocusBeforeReturningHome() {
        Helpers.getAllMods(activity, true)
        compose.onNodeWithTag("search.expand").performClick()
        val key = "pref_key_system_autobrightness_limitmin"
        val title = Helpers.allModsList.single { it.key == key }.title
        compose.onNodeWithTag("home.search").performTextInput(title)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("search.result:" + key))
        compose.onNodeWithTag("search.result:" + key).performClick()
        settleNavigation()
        assertTrue(currentPage() is SubFragment)
        pressToolbarBack()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("search.expand").performClick()
        compose.onNodeWithTag("home.search").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun emptySearchHidesHomeContentAndClosingRestoresCategories() {
        compose.onNodeWithTag("search.expand").performClick()
        compose.onNodeWithTag("search.status").assertTextEquals(activity.getString(R.string.search_settings_hint))
        compose.onNodeWithTag("pref_key_system").assertDoesNotExist()
        compose.onNodeWithTag("menu.expand").assertDoesNotExist()
        activity.onBackPressedDispatcher.onBackPressed()
        settleNavigation()
        compose.onNodeWithTag("pref_key_system").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun noSearchResultsExplainTheEmptyStateAndClearingRestoresTheHint() {
        Helpers.getAllMods(activity, true)
        compose.onNodeWithTag("search.expand").performClick()
        compose.onNodeWithTag("home.search").performTextInput("no_such_setting_4837")
        compose.onNodeWithTag("search.status").assertTextEquals(activity.getString(R.string.search_settings_no_results))
        compose.onNodeWithContentDescription(activity.getString(androidx.appcompat.R.string.abc_searchview_description_clear)).performClick()
        compose.onNodeWithTag("search.status").assertTextEquals(activity.getString(R.string.search_settings_hint))
        compose.onNodeWithTag("home.search").assertIsFocused()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun recreationPreservesSearchTextWithoutRequestingInputFocus() {
        compose.onNodeWithTag("search.expand").performClick()
        compose.onNodeWithTag("home.search").performTextInput("brightness")
        controller.recreate()
        activity = controller.get()
        settleNavigation()
        compose.onNodeWithTag("home.search").assertIsNotFocused().assertTextEquals("brightness")
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun stoppingSearchReleasesFocusWithoutDiscardingTheQuery() {
        compose.onNodeWithTag("search.expand").performClick()
        compose.onNodeWithTag("home.search").performTextInput("brightness")
        controller.pause().stop()
        controller.start().resume().visible()
        settleNavigation()
        compose.onNodeWithTag("home.search").assertIsNotFocused().assertTextEquals("brightness")
    }

    @Test
    fun searchOpensMovedAndDeepSettingsAndReturnsHome() {
        Helpers.getAllMods(activity, true)
        for (key in arrayOf("pref_key_system_volumeblur_collapsed", "pref_key_system_statusbaricons_wifi",
            "pref_key_system_autobrightness_limitmin", "pref_key_various_disable_miui_daemon",
            "pref_key_system_island_feedback_wifi", "pref_key_system_island_music_down",
            "pref_key_system_media_ios_card", "pref_key_system_statusbarheight", "pref_key_various_gboardpadding_port",
            "pref_key_system_animationscale_window", "pref_key_system_gallery_screenshots_path")) {
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

    @Test fun previousSearchPathsResolveMovedRowsAndKeepTheirBindings() {
        for ((sub, key) in listOf(
            "pref_key_system_cat_audio" to "pref_key_system_media_ios_card",
            "pref_key_system_cat_statusbar" to "pref_key_system_statusbarheight",
            "pref_key_various_cat_gboard" to "pref_key_various_gboardpadding_port",
            "pref_key_system_cat_other" to "pref_key_system_animationscale_window",
        )) {
            val home = currentPage() as MainFragment
            assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(home, "openModCat",
                ReflectionHelpers.ClassParameter.from(String::class.java, "pref_key_system"),
                ReflectionHelpers.ClassParameter.from(String::class.java, sub),
                ReflectionHelpers.ClassParameter.from(String::class.java, key)))
            settleNavigation()
            assertTrue(requireNotNull((currentPage() as SubFragment).findPreference<Preference>(key)).isVisible)
            pressToolbarBack()
            assertSame(home, currentPage())
        }
    }

    @Test fun backupMenuUsesTheExistingDialogWithoutChangingSettings() {
        val home = currentPage() as MainFragment
        val before = requireNotNull(AppHelper.appPrefs).all
        assertNull(home.findPreference<Preference>("pref_key_miuizer_backup"))
        val item = PopupMenu(activity, home.requireView()).menu.add(0, R.id.backuprestore, 0, activity.getString(R.string.backup_restore))
        home.onSettingsAction(item)
        settleNavigation()
        assertTrue(requireNotNull(ShadowDialog.getLatestDialog()).isShowing)
        assertEquals(before, requireNotNull(AppHelper.appPrefs).all)
        requireNotNull(ShadowDialog.getLatestDialog()).dismiss()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun narrowChinesePageKeepsItsLayoutAndNestedDependenciesAfterRecreation() {
        openSettingsPage("pref_key_controls", "pref_key_system_cat_audio")
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
        editor.createSettingsMenu(menu)
        assertTrue(menu.findItem(R.id.edit_confirm).isVisible)
        assertFalse(menu.findItem(R.id.restart_menu).isVisible)
        val labels = activity.resources.getStringArray(R.array.global_actions_launcher)
        val selected = activity.resources.getIntArray(R.array.global_actions_launcher_val)[1]
        compose.onNodeWithText(activity.getString(R.string.launcher_gestures_action)).performClick()
        compose.onAllNodesWithText(labels[1]).onLast().performClick()
        settleNavigation()
        assertTrue("The Compose action editor follows the available width", editor.requireView().width > 240 * resourcesDensity())
        capturePage("action-landscape-night-zh")
        assertTrue(editor.onSettingsAction(menu.findItem(R.id.edit_confirm)))
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

    @Test fun notificationPageKeepsItsContextTitleSeparateFromTheIslandSwitch() {
        val island = openIslandPage()
        val entry = requireNotNull(island.findPreference<Preference>("pref_key_system_island_notifications_cat"))
        assertEquals(activity.getString(R.string.system_island_chat_group), entry.title)
        assertEquals(activity.getString(R.string.system_island_music_group),
            requireNotNull(island.findPreference<Preference>("pref_key_system_island_music_cat")).title)
        entry.performClick(); settleNavigation()
        val toggle = requireNotNull((currentPage() as SubFragment).findPreference<CheckBoxPreferenceEx>("pref_key_system_island_chat"))
        assertEquals(activity.getString(R.string.system_island_chat_title), toggle.title)
        assertTrue(entry.title != toggle.title)
        compose.onNodeWithText(activity.getString(R.string.system_island_chat_group)).assertIsDisplayed()
    }

    @Test fun mediaAndEffectsShareOneManualApplicationSelection() {
        val page = openIslandMusic()
        val media = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media"))
        val display = requireNotNull(page.findPreference<Preference>("pref_key_system_island_media_apps"))
        val effect = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media_return"))
        assertNull(page.findPreference<Preference>("pref_key_system_island_media_scope"))
        assertNull(page.findPreference<Preference>("pref_key_system_island_media_effect_scope"))
        assertNull(page.findPreference<Preference>("pref_key_system_island_media_return_apps"))
        assertFalse(display.isEnabled); assertFalse(effect.isEnabled)
        media.isChecked = true; assertTrue(display.isEnabled); assertTrue(effect.isEnabled)
        effect.isChecked = true
        media.isChecked = false; assertFalse(display.isEnabled)
        media.isChecked = true; assertTrue(display.isEnabled)
        display.performClick(); settleNavigation()
        assertTrue(currentPage() is tv.withaibuild.customiuizer.subs.AppSelector)
        assertEquals(display.key, currentPage().arguments?.getString("key"))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun applicationSearchStaysAboveTheScrollingListWhenTheViewportShrinks() {
        val page = openIslandMusic()
        requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media")).isChecked = true
        val gestures = page
        requireNotNull(gestures.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media_return")).isChecked = true
        val apps = (1..120).map { index -> tv.withaibuild.customiuizer.utils.AppData().apply {
            label = "Application $index"; pkgName = "example.$index"; enabled = true; prepareForList()
        } }
        val originalApps = AppHelper.installedAppsList
        try {
            // Feed the real async loader; replacing appRows can race its pending result.
            AppHelper.installedAppsList = ArrayList(apps)
            requireNotNull(gestures.findPreference<Preference>("pref_key_system_island_media_apps")).performClick()
            settleNavigation()
            val selector = currentPage() as tv.withaibuild.customiuizer.subs.AppSelector
            val rows = ReflectionHelpers.getField<androidx.compose.runtime.MutableState<List<AppSelectionRow>>>(selector, "appRows\$delegate")
            compose.waitUntil(timeoutMillis = 5_000) {
                shadowOf(Looper.getMainLooper()).idle()
                rows.value.size == apps.size &&
                    compose.onAllNodesWithText(apps.first().label).fetchSemanticsNodes().size == 1
            }
            var originalTop: Float? = null
            for (height in listOf(640, 310)) {
                val container = activity.findViewById<View>(R.id.fragment_container)
                container.layoutParams = (container.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                    this.height = height; weight = 0f
                }
                settleNavigation()
                compose.onNodeWithTag("selector.list").performScrollToIndex(80)
                val header = compose.onNodeWithTag("selector.search").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val list = compose.onNodeWithTag("selector.list").fetchSemanticsNode().boundsInRoot
                assertTrue("Rows stay below fixed search", list.top >= header.bottom)
                assertTrue("Only the list viewport shrinks", list.height > 0 && list.height < height)
                if (originalTop == null) originalTop = header.top else assertEquals(originalTop!!, header.top, .5f)
            }
        } finally {
            AppHelper.installedAppsList = originalApps
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
        val gain = requireNotNull(page.findPreference<Preference>("pref_key_system_island_waveform_gain"))
        assertTrue("Recording can configure the shared waveform without enabling media", wave.isEnabled)
        assertEquals("1", wave.value)
        assertFalse(gain.isEnabled)
        assertTrue(wave.callChangeListener("2"))
        wave.value = "2"
        assertTrue(gain.isEnabled)
        compose.onNodeWithTag("settings.list").performScrollToNode(hasTestTag(wave.key))
        compose.onNodeWithText(activity.getString(R.string.system_island_waveform_real)).assertIsDisplayed()
        val expected = activity.getString(R.string.system_island_waveform_mode_summ)
        val detail = compose.onNodeWithText(expected, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(wave.title.toString(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val selected = compose.onNodeWithText(wave.entry.toString(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(detail.top >= title.bottom)
        assertTrue("Selected value follows the explanation without competing for its width", selected.top >= detail.bottom)
        settleNavigation()
        capturePage("island-waveform-320-zh")
    }

    @Test fun absorptionWidthControlsFollowTheEffectAndAutomaticMode() {
        val island = openIslandPage()
        requireNotNull(island.findPreference<Preference>("pref_key_system_island_effects_cat")).performClick()
        settleNavigation()
        val page = currentPage() as SubFragment
        val effect = requireNotNull(page.findPreference<ListPreference>("pref_key_system_island_return_effect"))
        val automatic = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_absorption_header_auto"))
        val width = requireNotNull(page.findPreference<Preference>("pref_key_system_island_absorption_header_width"))
        assertFalse(automatic.isVisible)
        assertFalse(width.isVisible)
        assertTrue(effect.callChangeListener("absorption"))
        effect.value = "absorption"
        assertTrue(automatic.isVisible)
        assertTrue(automatic.isChecked)
        assertFalse(width.isVisible)
        assertTrue(automatic.callChangeListener(false))
        automatic.isChecked = false
        assertTrue(width.isVisible)
        assertTrue(effect.callChangeListener("instant"))
        assertFalse(automatic.isVisible)
        assertFalse(width.isVisible)
    }

    @Test fun mediaDownActionStaysEditableAndLegacyExpansionHasNoControl() {
        requireNotNull(AppHelper.appPrefs).edit().putInt("pref_key_system_island_music_down_action", 85).apply()
        val mediaPage = openIslandMusic()
        requireNotNull(mediaPage.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_media")).isChecked = true
        pressToolbarBack()
        requireNotNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_system_island_actions_cat")).performClick()
        settleNavigation()
        val page = currentPage() as SubFragment
        assertNull(page.findPreference<Preference>("pref_key_system_island_expand_down"))
        val down = requireNotNull(page.findPreference<Preference>("pref_key_system_island_music_down"))
        val double = requireNotNull(page.findPreference<Preference>("pref_key_system_island_music_double"))
        assertTrue(down.isEnabled)
        assertTrue(double.isEnabled)
        assertEquals(85, requireNotNull(AppHelper.appPrefs).getInt("pref_key_system_island_music_down_action", -1))
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
        assertSame(content, pref("live_cat").parent)
        for (suffix in listOf("waveform_mode", "entrance_style", "simplified"))
            assertEquals(null, page.findPreference<Preference>("pref_key_system_island_$suffix"))
        val mode = requireNotNull(page.findPreference<ListPreference>("pref_key_system_strong_toast_mode"))
        assertTrue(mode.callChangeListener("0")); mode.value = "0"
        for (group in listOf(content, appearance, interaction)) {
            assertFalse(group.isVisible)
        }
        for (suffix in listOf("music_cat", "effects_cat", "actions_cat", "live_cat")) assertFalse(pref(suffix).isEnabled)
        assertTrue(mode.callChangeListener("3")); mode.value = "3"
        for (group in listOf(content, appearance, interaction)) {
            assertTrue(group.isVisible)
        }
        for (suffix in listOf("music_cat", "effects_cat", "actions_cat", "live_cat")) assertTrue(pref(suffix).isEnabled)
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
        val labels = activity.resources.getStringArray(R.array.global_actions_island_music)
        val values = activity.resources.getIntArray(R.array.global_actions_island_music_val)
        compose.onNodeWithText(labels[values.indexOf(87)]).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.launcher_gestures_action)).performClick()
        compose.onAllNodesWithText(labels[values.indexOf(18)]).onLast().performClick()
        settleNavigation()
        capturePage("music-action-320-zh")
        val menu=PopupMenu(activity,editor.requireView()).menu
        editor.createSettingsMenu(menu)
        assertTrue(editor.onSettingsAction(menu.findItem(R.id.edit_confirm))); settleNavigation()
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
        pressToolbarBack()
        requireNotNull(page.findPreference<Preference>("pref_key_system_island_live_cat")).performClick(); settleNavigation()
        val live = currentPage() as SubFragment
        val recorder = requireNotNull(live.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_live_recorder"))
        recorder.isChecked = true
        assertTrue(recorder.callChangeListener(true))
        assertEquals(setOf("recorder"), AppHelper.appPrefs!!.getStringSet("pref_key_system_island_live_kinds", emptySet()))
        requireNotNull(live.findPreference<Preference>("pref_key_system_island_live_return"))
        assertFalse(AppHelper.appPrefs!!.contains("pref_key_system_island_live_recorder"))
        pressToolbarBack()
        requireNotNull(page.findPreference<Preference>("pref_key_system_island_notifications_cat")).performClick(); settleNavigation()
        val notifications = currentPage() as SubFragment
        val chat=requireNotNull(notifications.findPreference<CheckBoxPreferenceEx>("pref_key_system_island_chat"))
        val allowed=requireNotNull(notifications.findPreference<Preference>("pref_key_system_island_notification_apps"))
        assertFalse(allowed.isEnabled); chat.isChecked=true; assertTrue(allowed.isEnabled)
        assertTrue(requireNotNull(AppHelper.appPrefs).getStringSet(allowed.key,emptySet())!!.isEmpty())
    }

    @Test fun appsAndToolsDirectorySeparatesAppRulesConnectionsAndSystemToolsWithoutDuplicateEntries() {
        val home = currentPage() as MainFragment
        assertTrue(home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_various"))))
        settleNavigation()
        val directory = currentPage() as SubFragment
        assertPreferencePageVisible(directory)
        val groups = (0 until directory.preferenceScreen.preferenceCount)
            .map { directory.preferenceScreen.getPreference(it) as PreferenceGroup }
        assertEquals(listOf(R.string.prefs_group_apps, R.string.prefs_group_connectivity, R.string.prefs_group_system_tools)
            .map(activity::getText), groups.map { it.title })
        val keys = directoryKeys(directory.preferenceScreen)
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(groups[0].findPreference<Preference>("pref_key_system_appbehavior_cat") != null)
        assertNull(groups[0].findPreference<Preference>("pref_key_system_wifiauto_cat"))
        assertNull(groups[0].findPreference<Preference>("pref_key_system_vpnauto_cat"))
        for (suffix in listOf("wifiauto", "vpnauto"))
            assertTrue(groups[1].findPreference<Preference>("pref_key_system_${suffix}_cat") != null)
        assertTrue(groups[2].findPreference<Preference>("pref_key_various_cat_general") != null)
        assertTrue(groups[2].findPreference<Preference>("pref_key_various_cat_exclusive") != null)
        requireNotNull(directory.findPreference<Preference>("pref_key_system_cat_automation")).performClick()
        settleNavigation()
        val connections = currentPage() as SubFragment
        for (suffix in listOf("appbehavior", "wifiauto", "vpnauto"))
            assertNull(connections.findPreference<Preference>("pref_key_system_${suffix}_cat"))
        assertTrue(requireNotNull(connections.findPreference<Preference>("pref_key_system_wifipassword")).isVisible)
        assertTrue(requireNotNull(connections.findPreference<Preference>("pref_key_system_usb_default_function")).isVisible)
        pressToolbarBack()
        assertSame(directory, currentPage())
        for (suffix in listOf("appbehavior", "wifiauto", "vpnauto"))
            assertTrue(requireNotNull(directory.findPreference<Preference>("pref_key_system_${suffix}_cat")).isVisible)
        requireNotNull(directory.findPreference<Preference>("pref_key_system_cat_other")).performClick()
        settleNavigation()
        assertNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_system_wifiauto_cat"))
        assertNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_system_vpnauto_cat"))
    }

    @Test fun wifiAutomaticSettingsEditsIndependentRulesWithoutActivatingDefaultActions() {
        val config = tv.withaibuild.customiuizer.utils.WifiAutomaticSettings
        val existing = tv.withaibuild.customiuizer.utils.WifiAutomaticSettings.Rule("home", "Home", "home", dnd = 2)
        AppHelper.appPrefs!!.edit().putString("pref_key_system_wifiauto_rules", config.encode(listOf(existing))).apply()
        openSettingsPage("pref_key_various", "pref_key_system_wifiauto_cat")
        val page = currentPage() as tv.withaibuild.customiuizer.subs.System_WifiAutomatic
        assertPreferencePageVisible(page)
        assertFalse(requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_wifiauto_enabled")).isChecked)
        assertFalse(requireNotNull(page.findPreference<Preference>("pref_key_system_wifiauto_rules")).isVisible)
        requireNotNull(page.findPreference<Preference>("pref_key_system_wifiauto_add")).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val editor = ShadowDialog.getLatestDialog() as AlertDialog
        editor.findViewById<View>(android.R.id.content)!!.findViewWithTag<android.widget.EditText>("name").setText("Office")
        editor.findViewById<View>(android.R.id.content)!!.findViewWithTag<android.widget.EditText>("ssid").setText("office")
        editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val saved = config.decode(AppHelper.appPrefs!!.getString("pref_key_system_wifiauto_rules", "").orEmpty())
        assertEquals(2, saved.size)
        assertEquals("office", saved[1].ssid)
        assertFalse(saved[1].manages)
        val list = requireNotNull(page.findPreference<androidx.preference.PreferenceCategory>("pref_key_system_wifiauto_list"))
        list.getPreference(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val edit = ShadowDialog.getLatestDialog() as AlertDialog
        edit.findViewById<View>(android.R.id.content)!!.findViewWithTag<android.widget.EditText>("priority").setText("10")
        edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(10, config.decode(AppHelper.appPrefs!!.getString("pref_key_system_wifiauto_rules", "").orEmpty())[0].priority)
        list.getPreference(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, config.decode(AppHelper.appPrefs!!.getString("pref_key_system_wifiauto_rules", "").orEmpty()).size)
    }

    @Test fun appBehaviorUsesSeparateSwitchesAndStoresBrightnessPerApplication() {
        val prefix = "pref_key_system_appbehavior_"
        AppHelper.appPrefs!!.edit().putStringSet(prefix + "brightness_apps", setOf("app.a", "app.b")).apply()
        openSettingsPage("pref_key_various", prefix + "cat")
        val page = currentPage() as tv.withaibuild.customiuizer.subs.System_AppBehavior
        assertPreferencePageVisible(page)
        for (name in listOf("rotation", "volume", "brightness", "block")) {
            val toggle = requireNotNull(page.findPreference<CheckBoxPreferenceEx>(prefix + name))
            val apps = requireNotNull(page.findPreference<Preference>(prefix + name + "_apps"))
            assertFalse(toggle.isChecked)
            assertFalse(apps.isEnabled)
            toggle.isChecked = true
            assertTrue(apps.isEnabled)
        }
        val app = requireNotNull(page.findPreference<ListPreference>(prefix + "edit_app"))
        val level = requireNotNull(page.findPreference<SeekBarPreference>(prefix + "edit_level"))
        assertEquals(listOf("app.a", "app.b"), app.entryValues.map { it.toString() })
        assertEquals(0, level.getValue())
        assertTrue(level.callChangeListener(45))
        assertTrue(app.callChangeListener("app.b")); app.value = "app.b"
        assertEquals(0, level.getValue())
        assertTrue(level.callChangeListener(70))
        assertTrue(app.callChangeListener("app.a")); app.value = "app.a"
        assertEquals(45, level.getValue())
        assertTrue(level.callChangeListener(0))
        assertEquals("app.b=70", AppHelper.appPrefs!!.getString(prefix + "brightness_values", ""))
        requireNotNull(page.findPreference<Preference>(prefix + "rotation_apps")).performClick()
        settleNavigation()
        val picker = currentPage() as tv.withaibuild.customiuizer.subs.AppSelector
        assertTrue(requireNotNull(picker.arguments).getBoolean("multi"))
        pressToolbarBack()
        assertTrue(currentPage() is tv.withaibuild.customiuizer.subs.System_AppBehavior)
    }

    @Test fun vpnRulesUseOneClientOrProfileAndAMultiAppSelector() {
        openSettingsPage("pref_key_various", "pref_key_system_vpnauto_cat")
        val page = currentPage() as tv.withaibuild.customiuizer.subs.System_VpnAutoSwitch
        assertPreferencePageVisible(page)
        val kind = requireNotNull(page.findPreference<ListPreference>("pref_key_system_vpnauto_kind"))
        val client = requireNotNull(page.findPreference<ListPreference>("pref_key_system_vpnauto_client"))
        val profile = requireNotNull(page.findPreference<ListPreference>("pref_key_system_vpnauto_profile"))
        assertTrue(client.isVisible)
        assertFalse(profile.isVisible)
        assertTrue(kind.callChangeListener("system"))
        kind.value = "system"
        assertFalse(client.isVisible)
        assertTrue(profile.isVisible)
        val autoClose = requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_vpnauto_close"))
        val delay = requireNotNull(page.findPreference<SeekBarPreference>("pref_key_system_vpnauto_delay"))
        assertTrue(autoClose.isChecked)
        assertTrue(delay.isEnabled)
        assertEquals(3, delay.getValue())
        delay.setValue(30, true)
        assertEquals(30, AppHelper.appPrefs!!.getInt("pref_key_system_vpnauto_delay", -1))
        delay.setValue(0, true)
        assertEquals(0, delay.getValue())
        autoClose.isChecked = false
        assertFalse(delay.isEnabled)
        autoClose.isChecked = true
        assertTrue(delay.isEnabled)
        requireNotNull(page.findPreference<Preference>("pref_key_system_vpnauto_apps")).performClick()
        settleNavigation()
        val picker = currentPage() as tv.withaibuild.customiuizer.subs.AppSelector
        assertTrue(requireNotNull(picker.arguments).getBoolean("multi"))
        assertEquals("pref_key_system_vpnauto_apps", picker.arguments?.getString("key"))
        pressToolbarBack()
        assertPreferencePageVisible(currentPage())
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
        home.onSettingsAction(about)
        settleNavigation()
        val page = currentPage() as AboutFragment
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        val view = page.requireView()
        view.measure(View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY))
        view.layout(view.left, view.top, view.right, view.bottom)
        compose.onNodeWithTag("about_community").assertIsDisplayed()
        compose.onNodeWithText(BuildConfig.COMMUNITY_URL.removePrefix("https://")).assertIsDisplayed()
        capturePage("about-community", view)
        compose.onNodeWithTag("about_community").performClick()
        val intent = requireNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(BuildConfig.COMMUNITY_URL, intent.dataString)
        assertEquals(null, shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun returningFromAboutRestoresTheHomeOptions() {
        val home = currentPage() as MainFragment
        val about = PopupMenu(activity, home.requireView()).menu.add(0, R.id.about, 0, "About")
        assertTrue(home.onSettingsAction(about))
        settleNavigation()
        assertTrue(currentPage() is AboutFragment)
        pressToolbarBack()

        assertTrue(currentPage() is MainFragment)
        assertPreferencePageVisible(currentPage())
    }

    @Test
    fun homeIndicatorChecksSystemNavigationOnPageEntryAndResumeWithoutChangingSavedChoice() {
        val device=android.os.Build.DEVICE
        val incremental=android.os.Build.VERSION.INCREMENTAL
        val abis=android.os.Build.SUPPORTED_ABIS
        try {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java,"SUPPORTED_ABIS",arrayOf("arm64-v8a"))
            org.robolectric.shadows.ShadowBuild.setDevice("xaga")
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java,"INCREMENTAL","V816.0.9.0.ULOINXM")
            Settings.Global.putInt(activity.contentResolver,"force_fsg_nav_bar",1)
            Settings.Global.putInt(activity.contentResolver,"hide_gesture_line",0)
            val home=currentPage() as MainFragment
            home.onPreferenceTreeClick(requireNotNull(home.findPreference<Preference>("pref_key_controls")))
            settleNavigation()
            requireNotNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_controls_cat_navbar")).performClick()
            settleNavigation()
            val page=currentPage() as SubFragment
            val row=requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_controls_ios_home_indicator"))
            val hide=requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_controls_nonavbar"))
            assertTrue(row.isEnabled);row.isChecked=true
            for((mode,hidden,expected) in listOf(Triple(0,0,false),Triple(1,1,false),Triple(1,0,true))) {
                Settings.Global.putInt(activity.contentResolver,"force_fsg_nav_bar",mode)
                Settings.Global.putInt(activity.contentResolver,"hide_gesture_line",hidden)
                page.onPause();page.onResume()
                assertEquals(expected,row.isEnabled);assertTrue(row.isChecked)
            }
            hide.callChangeListener(true);hide.isChecked=true
            assertFalse(row.isEnabled)
            page.onPause();page.onResume();assertFalse(row.isEnabled)
            hide.callChangeListener(false);hide.isChecked=false;assertTrue(row.isEnabled)
            org.robolectric.shadows.ShadowBuild.setDevice("unsupported")
            page.onPause();page.onResume();assertTrue(row.isEnabled);assertTrue(row.isChecked)
        } finally {
            org.robolectric.shadows.ShadowBuild.setDevice(device)
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java,"SUPPORTED_ABIS",abis)
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java,"INCREMENTAL",incremental)
        }
    }

    @Test
    fun savedIosCategoryAliasesOpenTheirCurrentVisiblePageAndSurviveRecreation() {
        val aliases=listOf("ios_volume" to "audio","ios_desktop" to "recents")
        for((old,current) in aliases) {
            val home=currentPage() as MainFragment
            val open=MainFragment::class.java.getDeclaredMethod("openModCat",String::class.java,String::class.java,String::class.java)
                .apply { isAccessible=true }
            assertTrue(open.invoke(home,"pref_key_system","pref_key_system_cat_$old","") as Boolean)
            settleNavigation()
            val page=currentPage() as SubFragment
            assertPreferencePageVisible(page)
            assertTrue(page.preferenceScreen.preferenceCount>0)
            assertTrue(page.findPreference<Preference>("pref_key_system_cat_$current")!=null)
            controller.recreate();activity=controller.get();settleNavigation()
            assertPreferencePageVisible(currentPage())
            pressToolbarBack()
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun audioDirectoryUsesItsSystemControllerAndPersistsTheVisibleIosVolumeOptions() {
        val styleKey = "pref_key_system_volume_ios_style"
        val compactKey = "pref_key_system_volume_ios_compact"
        val offsetKey = "pref_key_system_volume_ios_classic_offset"
        openSettingsPage("pref_key_controls", "pref_key_system_cat_audio")
        val page = currentPage() as tv.withaibuild.customiuizer.subs.System
        val style = requireNotNull(page.findPreference<ListPreference>(styleKey))
        assertEquals("1", style.value)
        assertTrue(requireNotNull(page.findPreference<Preference>(compactKey)).isVisible)
        assertFalse(requireNotNull(page.findPreference<Preference>(offsetKey)).isVisible)
        compose.onNodeWithTag(offsetKey).assertDoesNotExist()
        compose.onNodeWithTag("pref_key_system_volume_ios").performClick()
        settleNavigation()
        assertTrue(requireNotNull(page.findPreference<CheckBoxPreferenceEx>("pref_key_system_volume_ios")).isChecked)
        fun select(value: String) {
            val current = currentPage() as tv.withaibuild.customiuizer.subs.System
            val list = requireNotNull(current.findPreference<ListPreference>(styleKey))
            val index = list.entryValues.indexOfFirst { it.toString() == value }
            assertTrue("Enabling the HUD must enable its style choice", list.isEnabled)
            compose.onNodeWithTag("settings.list").performScrollToNode(hasTestTag(styleKey))
            compose.onNodeWithTag(styleKey).assertIsEnabled().assertIsDisplayed().performClick()
            settleNavigation()
            compose.onNode(hasText(list.title.toString()) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText(list.entries[index].toString()) and hasAnyAncestor(isDialog()),
                useUnmergedTree = true).assertIsDisplayed().performClick()
            settleNavigation()
            assertEquals(value, list.value)
            assertEquals(value, requireNotNull(AppHelper.appPrefs).getString(styleKey, null))
        }
        select("2")
        assertFalse(requireNotNull(page.findPreference<Preference>(compactKey)).isVisible)
        assertTrue(requireNotNull(page.findPreference<Preference>(offsetKey)).isVisible)
        compose.onNodeWithTag(compactKey).assertDoesNotExist()
        compose.onNodeWithTag(offsetKey).assertIsDisplayed()
        val offset = requireNotNull(page.findPreference<tv.withaibuild.customiuizer.prefs.SeekBarPreference>(offsetKey))
        assertEquals(-100, offset.getMinValue())
        assertTrue(offset.submitComposeInput("-25"))
        assertEquals(-25, requireNotNull(AppHelper.appPrefs).getInt(offsetKey, 0))
        assertEquals("-25", offset.composeInput)
        pressToolbarBack()
        val directory = currentPage() as SubFragment
        requireNotNull(directory.findPreference<Preference>("pref_key_system_cat_audio")).performClick()
        settleNavigation()
        val restored = currentPage() as tv.withaibuild.customiuizer.subs.System
        assertEquals("2", restored.findPreference<ListPreference>(styleKey)?.value)
        assertFalse(requireNotNull(restored.findPreference<Preference>(compactKey)).isVisible)
        assertTrue(requireNotNull(restored.findPreference<Preference>(offsetKey)).isVisible)
        select("1")
        assertTrue(requireNotNull(restored.findPreference<Preference>(compactKey)).isVisible)
        assertFalse(requireNotNull(restored.findPreference<Preference>(offsetKey)).isVisible)
        pressToolbarBack()
        assertSame(directory, currentPage())
        requireNotNull(directory.findPreference<Preference>("pref_key_system_cat_audio")).performClick()
        settleNavigation()
        val side = currentPage() as tv.withaibuild.customiuizer.subs.System
        assertEquals("1", side.findPreference<ListPreference>(styleKey)?.value)
        assertTrue(requireNotNull(side.findPreference<Preference>(compactKey)).isVisible)
        assertFalse(requireNotNull(side.findPreference<Preference>(offsetKey)).isVisible)
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
    fun retiredDockMaterialsMigrateToPanelAndNativeGlassStaysSelectable() {
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
        requireNotNull((currentPage() as SubFragment).findPreference<ListPreference>(key)).value = "3"
        pressToolbarBack()
        requireNotNull((currentPage() as SubFragment).findPreference<Preference>("pref_key_launcher_cat_dock")).performClick()
        settleNavigation()
        assertEquals("0", (currentPage() as SubFragment).findPreference<ListPreference>(key)?.value)
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
        for ((stored, displayed) in listOf(12 to "12 dp", 17 to "17 dp", 24 to "24 dp")) {
            inset.setValue(stored, true)
            assertEquals(displayed, inset.composeDisplay())
            assertEquals(stored, requireNotNull(AppHelper.appPrefs).getInt(key, -1))
        }
        val shrinkKey = "pref_key_launcher_dock_background_shrink"
        val shrink = requireNotNull(page.findPreference<SeekBarPreference>(shrinkKey))
        for ((stored, displayed) in listOf(12 to "-5 dp", 17 to "0 dp", 22 to "+5 dp", 34 to "+17 dp")) {
            shrink.setValue(stored, true)
            assertEquals(displayed, shrink.composeDisplay())
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
        for ((stored, displayed) in listOf(0 to "-400 dp", 400 to "0 dp", 430 to "+30 dp", 800 to "+400 dp")) {
            center.setValue(stored, true)
            assertEquals(displayed, center.composeDisplay())
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
        val animator = requireNotNull(page.onCreateAnimator(0, false, R.animator.a14_settings_close_exit))
        val set = AnimatorSet().apply { playTogether(animator) }
        set.currentPlayTime = 140L
        assertTrue(page.requireView().translationX > 0f)
        set.reverse()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        compose.waitForIdle()
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

    @Test
    fun systemAppUpdaterUsesVerifiedMaintenanceAndRestoresItsExactOriginalState() {
        installSystemPackage("com.xiaomi.discover")
        installSystemPackage("com.android.updater")
        activity.packageManager.setApplicationEnabledSetting("com.xiaomi.discover", 1, 0)
        installMaintenanceBridge()
        val toggle = requireNotNull(openExclusivePage().findPreference<CheckBoxPreferenceEx>(
            "pref_key_various_disable_system_app_updater"))
        toggle.callChangeListener(true)
        confirmLatestDialog()
        assertTrue(toggle.isChecked)
        assertEquals(3, activity.packageManager.getApplicationEnabledSetting("com.xiaomi.discover"))
        assertEquals(0, activity.packageManager.getApplicationEnabledSetting("com.android.updater"))
        toggle.callChangeListener(false)
        settleNavigation()
        assertFalse(toggle.isChecked)
        assertEquals(1, activity.packageManager.getApplicationEnabledSetting("com.xiaomi.discover"))
        assertFalse(tv.withaibuild.customiuizer.utils.MaintenanceJournal.open(activity).contains(toggle.key))
    }

    @Test
    fun rejectedSystemAppUpdaterChangeDoesNotReportSuccess() {
        installSystemPackage("com.xiaomi.discover")
        activity.packageManager.setApplicationEnabledSetting("com.xiaomi.discover", 1, 0)
        installMaintenanceBridge().failure = SecurityException("bridge unavailable")
        val toggle = requireNotNull(openExclusivePage().findPreference<CheckBoxPreferenceEx>(
            "pref_key_various_disable_system_app_updater"))
        toggle.callChangeListener(true)
        confirmLatestDialog()
        assertFalse(toggle.isChecked)
        assertFalse(requireNotNull(AppHelper.appPrefs).getBoolean(toggle.key, false))
        assertEquals(1, activity.packageManager.getApplicationEnabledSetting("com.xiaomi.discover"))
    }

    @Test
    fun updaterHideOnlyRestoreDoesNotChangeAReinstalledPackageWithoutASnapshot() {
        installSystemPackage("com.xiaomi.discover")
        activity.packageManager.setApplicationEnabledSetting("com.xiaomi.discover", 1, 0)
        requireNotNull(AppHelper.appPrefs).edit()
            .putBoolean("pref_key_various_disable_system_app_updater", true).commit()
        val toggle = requireNotNull(openExclusivePage().findPreference<CheckBoxPreferenceEx>(
            "pref_key_various_disable_system_app_updater"))
        toggle.callChangeListener(false)
        assertFalse(toggle.isChecked)
        assertEquals(1, activity.packageManager.getApplicationEnabledSetting("com.xiaomi.discover"))
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
                    GlobalActions.SET_XIAOMI_PACKAGES_ACTION -> {
                        val apply = GlobalActionSystemServerHooks::class.java.getDeclaredMethod(
                            "applyXiaomiPackageStates", Context::class.java, Array<String>::class.java,
                            IntArray::class.java,
                        ).apply { isAccessible = true }
                        val handled = apply.invoke(GlobalActionSystemServerHooks, context,
                            intent.getStringArrayExtra(GlobalActions.EXTRA_PACKAGE_NAMES),
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
            addAction(GlobalActions.SET_XIAOMI_PACKAGES_ACTION)
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
        compose.waitForIdle()
    }

    private fun assertPreferencePageVisible(fragment: Fragment) {
        val page = fragment as PreferenceFragmentBase
        val view = page.requireView()
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals("The page must end inside the viewport", 0f, view.translationX, 0.01f)
        assertEquals(1f, view.alpha, 0.01f)
        assertTrue("Preference XML must load rows", page.preferenceScreen.preferenceCount > 0)
        // A second PreferenceGroupAdapter replaces AndroidX's single internal listener.
        // Inspect the mounted list without taking its presentation ownership.
        val list = compose.onNodeWithTag("settings.list").assertIsDisplayed()
        assertTrue("Generated page must render visible options", list.onChildren().fetchSemanticsNodes().isNotEmpty())
        val menu = PopupMenu(activity, view).menu
        page.createSettingsMenu(menu)
        for (id in intArrayOf(R.id.restartlauncher, R.id.restartsystemui, R.id.restartsecuritycenter, R.id.softreboot)) {
            assertEquals("Restart actions belong to home", page is MainFragment, menu.findItem(id)?.isVisible == true)
            if (page !is MainFragment) {
                val action = menu.findItem(id) ?: menu.add(0, id, 0, "Restart")
                assertFalse("Subpages must also reject a stale restart action", page.onSettingsAction(action))
            }
        }
    }
}
