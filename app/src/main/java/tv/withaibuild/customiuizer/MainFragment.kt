package tv.withaibuild.customiuizer

import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.BackHandler
import android.view.MenuItem
import tv.withaibuild.customiuizer.ui.highlightSearchText
import tv.withaibuild.customiuizer.ui.SettingsSearchField
import tv.withaibuild.customiuizer.ui.SettingsPreferenceList
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.Nullable
import tv.withaibuild.customiuizer.ui.SettingsActionBar as ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx
import tv.withaibuild.customiuizer.subs.CategorySelector
import tv.withaibuild.customiuizer.subs.Controls
import tv.withaibuild.customiuizer.subs.Launcher
import tv.withaibuild.customiuizer.subs.System as SubSystem
import tv.withaibuild.customiuizer.subs.Various
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.AppLocaleController
import tv.withaibuild.customiuizer.utils.AppSelectionSanitizer
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.ModData
import tv.withaibuild.customiuizer.utils.PreferenceResourceResolver
import tv.withaibuild.customiuizer.utils.SearchRouteResolver
import tv.withaibuild.customiuizer.utils.XposedServiceManager

class MainFragment : PreferenceFragmentBase() {

    private var query by mutableStateOf("")
    private var indexRevision by mutableIntStateOf(0)
    private var localeConfirmationDialog: AlertDialog? = null

    private var searchExpanded by mutableStateOf(false)
    private var focusSearch by mutableStateOf(false)
    internal val isSearching: Boolean get() = searchExpanded

    private fun isFragmentReady(act: AppCompatActivity?): Boolean {
        return act != null && !act.isFinishing && isAdded
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        toolbarMenu = true
        activeMenus = "all"
        super.onCreate(savedInstanceState)

        val act = activity as? AppCompatActivity ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            if (act.isFinishing) return@launch
            val prefs = AppHelper.appPrefs
            if (prefs != null) {
                try {
                    val installedPackages = AppSelectionSanitizer.queryInstalledPackageNames(act.packageManager)
                    AppSelectionSanitizer.sanitizeStoredSelections(prefs, installedPackages)
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    AppHelper.log("AppSelection", t)
                }
            }
            Helpers.getAllMods(act, savedInstanceState != null)
            withContext(Dispatchers.Main) { indexRevision++ }
        }

        checkModuleIsActive()

        savedInstanceState?.let {
            searchExpanded = it.getBoolean("search.expanded", false)
            query = if (searchExpanded) it.getString("search.query").orEmpty() else ""
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("search.expanded", searchExpanded)
        outState.putString("search.query", query)
    }

    private fun checkModuleIsActive() {
        lifecycleScope.launch {
            // Keep waiting across the service manager's own bind deadline. A timeout is not
            // proof - only onServiceDied is - and the bind is slowest right after this
            // process was restarted, which is exactly when the user is looking at this
            // screen. The previous code stopped waiting as soon as the state left UNKNOWN,
            // so entering TIMED_OUT ended the wait immediately and was then read as a
            // negative; a language change, which kills and relaunches this process, made
            // that race visible as an intermittent "module not active".
            if (!awaitBindDecision()) return@launch

            val act = activity as? AppCompatActivity ?: return@launch
            if (isFragmentReady(act) && XposedServiceManager.shouldReportInactive()) {
                showXposedDialog(act)
            }
        }
    }

    override fun onCreatePreferences(@Nullable savedInstanceState: Bundle?, @Nullable rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setSettingsPage(R.id.prefs_main)
    }

    private fun doBaseReload() = super.reloadPreferences()

    override fun reloadPreferences() {
        val act = activity as? AppCompatActivity ?: return super.reloadPreferences()
        lifecycleScope.launch(Dispatchers.IO) {
            Helpers.getAllMods(act, true)
            withContext(Dispatchers.Main) {
                doBaseReload()
            }
        }
    }

    @Composable
    internal override fun SettingsContent(model: SettingsPreferenceList) {
        val focusManager = LocalFocusManager.current
        val closeSearch = {
            focusManager.clearFocus(force = true)
            searchExpanded = false
            focusSearch = false
            query = ""
        }
        BackHandler(enabled = searchExpanded) { closeSearch() }
        Column(Modifier.fillMaxSize()) {
            if (searchExpanded) SettingsSearchField(query, { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("home.search"),
                requestFocus = focusSearch, onClose = closeSearch)
            if (!searchExpanded) Box(Modifier.weight(1f)) { super.SettingsContent(model) }
            else if (query.isNotBlank()) {
                val terms = remember(query) { tv.withaibuild.customiuizer.utils.searchTerms(query) }
                val highlight = MaterialTheme.colorScheme.primary
                val matches = remember(query, indexRevision) {
                    Helpers.allModsList.filter { mod -> if (query == Helpers.NEW_MODS_SEARCH_QUERY)
                        Helpers.newMods.contains(mod.key) else mod.matchesSearch(terms) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    items(matches.size, key = { index -> matches[index].key + ":" + index }) { index ->
                        val mod = matches[index]
                        Surface(Modifier.fillMaxWidth().padding(bottom = 2.dp).testTag("search.result:" + mod.key).clickable {
                            if (openSearchResult(mod)) {
                                closeSearch()
                            }
                        }) {
                            Column(Modifier.padding(16.dp)) {
                                val title = remember(mod.title, terms, highlight) { highlightSearchText(mod.title, terms, highlight) }
                                val path = remember(mod.breadcrumbs, terms, highlight) { highlightSearchText(mod.breadcrumbs, terms, highlight) }
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(path, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.search_btn) {
            searchExpanded = true
            focusSearch = true
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        focusSearch = false
        super.onStop()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val actionBar: ActionBar? = getActionBar()
        actionBar?.setTitle(R.string.app_name)

        bindLocalePreference()

        findPreference<Preference>("pref_key_miuizer_launchericon")?.setOnPreferenceChangeListener { _, newValue ->
            val act = activity as? AppCompatActivity ?: return@setOnPreferenceChangeListener false
            val pm = act.packageManager
            val component = ComponentName(act, Helpers.LAUNCHER_ALIAS_NAME)
            if (newValue == true) {
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            } else {
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            true
        }
    }

    override fun onDestroyView() {
        // Replacing the home page exits search; configuration recreation keeps the query.
        if (isRemoving) {
            searchExpanded = false
            focusSearch = false
            query = ""
        }
        super.onDestroyView()
        localeConfirmationDialog?.dismiss()
        localeConfirmationDialog = null
    }

    private fun bindLocalePreference() {
        val locale = findPreference<ListPreferenceEx>(AppLocaleController.LOCALE_PREF_KEY) ?: return
        val prefs = AppHelper.appPrefs
        if (prefs == null) {
            locale.isEnabled = false
            return
        }

        locale.isPersistent = false
        val (displayEntries, entryValues) = try {
            AppLocaleController.buildLocaleDisplayData(locale.context)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            AppHelper.log("MainLocale", t)
            locale.isEnabled = false
            return
        }

        locale.entries = displayEntries
        locale.entryValues = entryValues
        locale.value = AppLocaleController.getUserLocaleForUi(prefs)
        locale.setOnPreferenceChangeListener { _, newValue ->
            val newTag = newValue as? String ?: return@setOnPreferenceChangeListener false
            if (newTag == AppLocaleController.getUserLocaleForUi(prefs)) {
                return@setOnPreferenceChangeListener true
            }
            showLocaleChangeConfirmation(newTag, locale)
            false
        }
    }

    private fun showLocaleChangeConfirmation(newTag: String, locale: ListPreferenceEx) {
        val act = activity as? AppCompatActivity ?: return
        if (act.isFinishing || act.isDestroyed || !isAdded) return

        localeConfirmationDialog?.dismiss()
        localeConfirmationDialog = AlertDialog.Builder(act)
            .setTitle(R.string.dialog_change_locale_title)
            .setMessage(R.string.dialog_change_locale_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dialog_change_locale_confirm) { _, _ ->
                val prefs = AppHelper.appPrefs
                if (prefs == null || !AppLocaleController.setUserLocale(prefs, newTag)) {
                    Toast.makeText(act, R.string.dialog_change_locale_save_failed, Toast.LENGTH_SHORT).show()
                    prefs?.let { locale.value = AppLocaleController.getUserLocaleForUi(it) }
                    return@setPositiveButton
                }
                if (!act.isFinishing && !act.isDestroyed && isAdded) {
                    AppLocaleController.exitApplicationAfterLocaleSave(act)
                }
            }
            .setOnDismissListener { localeConfirmationDialog = null }
            .create()
        localeConfirmationDialog?.show()
    }

    private fun openSearchResult(mod: ModData): Boolean {
        val page = mod.page ?: return openModCat(mod.cat.name, mod.sub, mod.key)
        if (!isAdded || parentFragmentManager.isStateSaved) return false
        val fragment = page.createFragment() ?: return false
        val bundle = Bundle().apply {
            putString("cat", mod.cat.name)
            putString("sub", mod.sub)
            putString("mod", mod.key)
            putBoolean("isStandalone", true)
            if (page.dynamic) putBundle("catInfo", Bundle().apply { putBoolean("isDynamic", true) })
        }
        openSubFragment(fragment, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, page.title, page.resource)
        return true
    }

    private fun openModCat(cat: String, sub: String?, mod: String): Boolean {
        val route = SearchRouteResolver.resolve(cat, sub, mod) ?: return false
        if (!isAdded) return false

        val bundle = Bundle().apply {
            putString("cat", route.category)
            putString("mod", route.key)
            route.sub?.let { putString("sub", it) }
        }

        return when (route.category) {
            "pref_key_system" -> {
                if (route.isCategorySelector()) {
                    openSubFragment(CategorySelector(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_mods, PreferenceResourceResolver.categorySelector(route.category) ?: return false)
                } else {
                    openSubFragment(SubSystem(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_mods, PreferenceResourceResolver.resolve(route.category, route.sub))
                }
                true
            }
            "pref_key_launcher" -> {
                if (route.isCategorySelector()) {
                    openSubFragment(CategorySelector(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.launcher_title, PreferenceResourceResolver.categorySelector(route.category) ?: return false)
                } else {
                    openSubFragment(Launcher(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.launcher_title, PreferenceResourceResolver.resolve(route.category, route.sub))
                }
                true
            }
            "pref_key_controls" -> {
                if (route.isCategorySelector()) {
                    openSubFragment(CategorySelector(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.controls_mods, PreferenceResourceResolver.categorySelector(route.category) ?: return false)
                } else {
                    openSubFragment(Controls(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.controls_mods, PreferenceResourceResolver.resolve(route.category, route.sub))
                }
                true
            }
            "pref_key_various" -> {
                if (route.isCategorySelector()) {
                    openSubFragment(CategorySelector(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.various_mods, PreferenceResourceResolver.categorySelector(route.category) ?: return false)
                } else {
                    openSubFragment(Various(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.various_mods, PreferenceResourceResolver.resolve(route.category, route.sub))
                }
                true
            }
            else -> false
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key ?: return super.onPreferenceTreeClick(preference)
        val modsCat = findPreference<PreferenceCategory>("prefs_cat")
        return if (modsCat?.findPreference<Preference>(key) != null && openModCat(key, null, key)) {
            true
        } else {
            super.onPreferenceTreeClick(preference)
        }
    }

}
