package tv.withaibuild.customiuizer

import android.os.Bundle
import android.view.View
import tv.withaibuild.customiuizer.ui.SettingsActionBar as ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference



import tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx
import tv.withaibuild.customiuizer.prefs.PreferenceState
import tv.withaibuild.customiuizer.subs.AppSelector
import tv.withaibuild.customiuizer.subs.MultiAction
import tv.withaibuild.customiuizer.subs.SortableList
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers

open class SubFragment : PreferenceFragmentBase() {

    private var contentResId = 0
    @JvmField
    var settingTitle = ""
    @JvmField
    protected var sub: String? = null
    @JvmField
    protected var catInfo: Bundle? = null
    @JvmField
    protected var isStandalone = false
    private var order = 100.0f
    private var highlightKey: String? = null
    internal var settingsType = AppHelper.SettingsType.Preference
    internal var abType = AppHelper.ActionBarType.Edit

    @JvmField
    var openAppsEdit = Preference.OnPreferenceClickListener { preference ->
        openApps(preference.key)
        true
    }

    @JvmField
    var openAppsBWEdit = Preference.OnPreferenceClickListener { preference ->
        openAppsBW(preference.key)
        true
    }

    @JvmField
    var openShareEdit = Preference.OnPreferenceClickListener { preference ->
        openShare(preference.key)
        true
    }

    @JvmField
    var openOpenWithEdit = Preference.OnPreferenceClickListener { preference ->
        openOpenWith(preference.key)
        true
    }

    @JvmField
    var openLauncherActions = Preference.OnPreferenceClickListener { preference ->
        openMultiAction(preference, MultiAction.Actions.LAUNCHER)
        true
    }

    @JvmField
    var openNavbarActions = Preference.OnPreferenceClickListener { preference ->
        openMultiAction(preference, MultiAction.Actions.NAVBAR)
        true
    }

    @JvmField
    var openStatusbarActions = Preference.OnPreferenceClickListener { preference ->
        openMultiAction(preference, MultiAction.Actions.STATUSBAR)
        true
    }

    @JvmField
    var openLockScreenActions = Preference.OnPreferenceClickListener { preference ->
        openMultiAction(preference, MultiAction.Actions.LOCKSCREEN)
        true
    }

    @JvmField
    var openLaunchActions = Preference.OnPreferenceClickListener { preference ->
        openMultiAction(preference, MultiAction.Actions.LAUNCH)
        true
    }

    val openMusicActions = Preference.OnPreferenceClickListener { preference ->
        openMultiAction(preference, MultiAction.Actions.MUSIC)
        true
    }

    @JvmField
    var openActivitiesList = Preference.OnPreferenceClickListener { preference ->
        openActivitiesItemList(preference)
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val args = arguments
        if (args == null) {
            activity?.finish()
            return
        }
        settingsType = AppHelper.SettingsType.values()[args.getInt("settingsType")]
        abType = AppHelper.ActionBarType.values()[args.getInt("abType")]
        contentResId = args.getInt("contentResId")
        settingTitle = args.getString("titleResId") ?: ""
        order = args.getFloat("order", 100.0f) + 10.0f
        catInfo = args.getBundle("catInfo")
        sub = tv.withaibuild.customiuizer.utils.SearchRouteResolver.normalizeSub(args.getString("sub"))
        isStandalone = args.getBoolean("isStandalone", false)
        highlightKey = args.getString("mod")
        if (abType == AppHelper.ActionBarType.Edit) {
            isCustomActionBar = true
        }
        toolbarMenu = toolbarMenu || isCustomActionBar

        if (contentResId == 0) {
            activity?.finish()
            return
        }

        super.onCreate(savedInstanceState)
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (settingsType == AppHelper.SettingsType.Preference) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setSettingsPage(contentResId)
            val highlightKeyLocal = highlightKey
            val highlightPref = if (highlightKeyLocal != null) findPreference<Preference>(highlightKeyLocal) as? PreferenceState else null
            if (highlightPref != null) {
                highlightPref.applyHighlight()
            } else {
                highlightKey = null
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.translationZ = order
        if (settingsType == AppHelper.SettingsType.Edit) {
            loadSharedPrefs()
        }
        val actionBar: ActionBar? = getActionBar()
        if (actionBar != null) {
            when {
                !isStandalone && !sub.isNullOrBlank() -> {
                    val screen = preferenceScreen
                    val category = screen.getPreference(0) as PreferenceCategoryEx
                    actionBar.setTitle(category.title)
                }
                else -> actionBar.setTitle(settingTitle)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        highlightKey?.let { requestPreferenceScroll(it) }
        highlightKey = null
    }

    /** Editors explicitly persist their own draft; navigation and ordinary settings need no view traversal. */
    open fun saveSharedPrefs() = Unit
    open fun loadSharedPrefs() = Unit

    private fun openApps(key: String?) {
        val args = Bundle()
        args.putString("key", key)
        args.putBoolean("multi", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(this, 0)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.id.prefs_app_selector)
    }

    private fun openAppsBW(key: String?) {
        val args = Bundle()
        args.putString("key", key)
        args.putBoolean("multi", true)
        args.putBoolean("bw", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(this, 0)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.id.prefs_app_selector)
    }

    private fun openShare(key: String?) {
        val args = Bundle()
        args.putString("key", key)
        args.putBoolean("multi", true)
        args.putBoolean("share", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(this, 0)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.id.prefs_app_selector)
    }

    private fun openOpenWith(key: String?) {
        val args = Bundle()
        args.putString("key", key)
        args.putBoolean("multi", true)
        args.putBoolean("openwith", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(this, 0)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.id.prefs_app_selector)
    }

    private fun openMultiAction(pref: Preference, actions: MultiAction.Actions) {
        val args = Bundle()
        args.putString("key", pref.key)
        args.putInt("actions", actions.ordinal)
        openSubFragment(MultiAction(), args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.Edit, pref.title.toString(), R.id.prefs_multiaction)
    }

    fun openStandaloneApp(pref: Preference, targetFrag: Fragment, resultId: Int) {
        val args = Bundle()
        args.putString("key", pref.key)
        args.putBoolean("standalone", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(targetFrag, resultId)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_app, R.id.prefs_app_selector)
    }

    fun openPrivacyAppEdit(targetFrag: Fragment, resultId: Int) {
        val args = Bundle()
        args.putBoolean("privacy", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(targetFrag, resultId)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.id.prefs_app_selector)
    }

    fun openLockedAppEdit(targetFrag: Fragment, resultId: Int) {
        val args = Bundle()
        args.putBoolean("applock", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(targetFrag, resultId)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.id.prefs_app_selector)
    }

    fun openLaunchableList(pref: Preference, targetFrag: Fragment, resultId: Int) {
        val args = Bundle()
        args.putString("key", pref.key)
        args.putBoolean("custom_titles", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(targetFrag, resultId)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.launcher_renameapps_list_title, R.id.prefs_app_selector)
    }

    fun openActivitiesItemList(pref: Preference) {
        val args = Bundle()
        args.putBoolean("activities", true)
        args.putString("key", pref.key)
        args.putString("titleResId", pref.title.toString())
        openSubFragment(SortableList(), args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, pref.title.toString(), R.id.prefs_sortable_list)
    }

    fun selectSub() {
        if (isStandalone || sub.isNullOrBlank()) return
        val screen = preferenceScreen
        val cnt = screen.preferenceCount
        for (i in cnt - 1 downTo 0) {
            val pref = screen.getPreference(i)
            if (pref.key != sub) {
                screen.removePreference(pref)
            } else {
                val category = pref as PreferenceCategoryEx
                val actionBar: ActionBar? = getActionBar()
                if (actionBar != null) {
                    actionBar.setTitle(pref.title)
                }
                category.hide()
            }
        }
    }

    fun finish() {
        val act = activity as? AppCompatActivity
        Helpers.hideKeyboard(act, view)
        val fragmentManager = parentFragmentManager
        if (isAdded && !fragmentManager.isStateSaved) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun confirmEdit() {
        saveSharedPrefs()
        finish()
    }
}
