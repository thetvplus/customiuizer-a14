package tv.withaibuild.customiuizer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.Nullable
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope

import java.util.ArrayList

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx
import tv.withaibuild.customiuizer.prefs.PreferenceState
import tv.withaibuild.customiuizer.prefs.SpinnerEx
import tv.withaibuild.customiuizer.prefs.SpinnerExFake
import tv.withaibuild.customiuizer.subs.AppSelector
import tv.withaibuild.customiuizer.subs.MultiAction
import tv.withaibuild.customiuizer.subs.SortableList
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.RestartMask
import tv.withaibuild.customiuizer.utils.RestartPagePolicy

/**
 * Pure decision policy for whether a SubFragment-derived page should enable the
 * options menu.  [Preference] pages always get menu capability so the matched
 * restart action can be resolved; [Edit] pages only get a menu when they use
 * a custom action bar (e.g. the edit-confirm action).
 */
internal fun shouldEnablePreferenceToolbar(
    settingsType: AppHelper.SettingsType,
    isCustomActionBar: Boolean
): Boolean = isCustomActionBar || (settingsType == AppHelper.SettingsType.Preference)

open class SubFragment : PreferenceFragmentBase() {

    private var contentResId = 0
    private var pageRestartMask = RestartMask.NONE
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
    @JvmField
    var padded = true
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
        sub = args.getString("sub")?.takeIf { it.isNotBlank() }
        isStandalone = args.getBoolean("isStandalone", false)
        highlightKey = args.getString("mod")
        if (abType == AppHelper.ActionBarType.Edit) {
            isCustomActionBar = true
        }
        pageRestartMask = if (settingsType == AppHelper.SettingsType.Preference) {
            RestartPagePolicy.maskFor(contentResId, sub)
        } else {
            RestartMask.NONE
        }
        toolbarMenu = toolbarMenu || shouldEnablePreferenceToolbar(settingsType, isCustomActionBar)

        if (contentResId == 0) {
            activity?.finish()
            return
        }

        if (settingsType == AppHelper.SettingsType.Preference) {
            super.onCreate(savedInstanceState, contentResId)
        } else {
            super.onCreate(savedInstanceState)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (settingsType == AppHelper.SettingsType.Edit) {
            loadSharedPrefs()
        }
        val actionBar: ActionBar? = getActionBar()
        if (actionBar != null) {
            when {
                isStandalone && catInfo != null && catInfo!!.getBoolean("isDynamic") -> {
                    actionBar.setTitle(settingTitle + " ⟲")
                }
                !isStandalone && !sub.isNullOrBlank() -> {
                    val screen = preferenceScreen
                    val category = screen.getPreference(0) as PreferenceCategoryEx
                    if (category.isDynamic()) {
                        actionBar.setTitle(category.title.toString() + " ⟲")
                    } else {
                        actionBar.setTitle(category.title)
                    }
                }
                else -> actionBar.setTitle(settingTitle)
            }
        }
    }

    override fun onCreatePreferences(@Nullable savedInstanceState: Bundle?, @Nullable rootKey: String?) {
        if (settingsType == AppHelper.SettingsType.Preference) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            setPreferencesFromResource(contentResId, rootKey)
            val highlightKeyLocal = highlightKey
            val highlightPref = if (highlightKeyLocal != null) findPreference<Preference>(highlightKeyLocal) as? PreferenceState else null
            if (highlightPref != null) {
                highlightPref.applyHighlight()
            } else {
                highlightKey = null
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val crtInflator = inflater.cloneInContext(requireContext())
        return if (settingsType == AppHelper.SettingsType.Preference) {
            super.onCreateView(crtInflator, container, savedInstanceState)
        } else {
            val view = crtInflator.inflate(if (padded) R.layout.prefs_common_padded else R.layout.prefs_common, container, false)
            crtInflator.inflate(contentResId, view as FrameLayout)
            view
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.translationZ = order
    }

    override fun onStart() {
        super.onStart()
        val key = highlightKey
        if (key != null) {
            val mList = getListView()
            val position = (mList.adapter as PreferenceGroup.PreferencePositionCallback).getPreferenceAdapterPosition(key)
            highlightKey = null
            if (position < 9) return
            val smoothScroller = object : LinearSmoothScroller(mList.context) {
                override fun getVerticalSnapPreference(): Int {
                    return LinearSmoothScroller.SNAP_TO_START
                }
            }
            smoothScroller.setTargetPosition(position)
            lifecycleScope.launch {
                val delayMs = (animDur * Helpers.getAnimationScale(2) + 30).toLong()
                delay(delayMs)
                if (isAdded) {
                    mList.layoutManager?.startSmoothScroll(smoothScroller)
                }
            }
        }
    }

    open fun saveSharedPrefs() {
        val root = view ?: run {
            Log.e("miuizer", "View not yet ready!")
            return
        }
        val nViews = collectEditPersistenceViews(root.findViewById(R.id.container))
        val editor = AppHelper.appPrefs?.edit() ?: return
        var dirty = false
        for (nView in nViews) {
            try {
                val wrote = applyTaggedEditPersistence(
                    editPersistenceKind(nView),
                    nView.tag as? String,
                    onSpinnerInt = {
                        editor.putInt(nView.tag as String, (nView as SpinnerEx).getSelectedArrayValue())
                    },
                    onSpinnerFake = {
                        val spinner = nView as SpinnerExFake
                        editor.putString(nView.tag as String, spinner.value)
                        spinner.applyOthers()
                    },
                    onText = {
                        editor.putString(nView.tag as String, (nView as TextView).text.toString())
                    },
                )
                if (wrote) dirty = true
            } catch (e: Throwable) {
                Log.e("miuizer", "Cannot save sub preference!")
            }
        }
        if (dirty) editor.apply()
    }

    open fun loadSharedPrefs() {
        val root = view ?: run {
            Log.e("miuizer", "View not yet ready!")
            return
        }
        val nViews = collectEditPersistenceViews(root.findViewById(R.id.container))
        for (nView in nViews) {
            try {
                if (nView.tag != null && editPersistenceKind(nView) == EditPersistenceKind.TEXT) {
                    (nView as TextView).text = AppHelper.getStringOfAppPrefs(nView.tag as String, "")
                }
            } catch (e: Throwable) {
                Log.e("miuizer", "Cannot load sub preference!")
            }
        }
    }

    private fun openApps(key: String?) {
        val args = Bundle()
        args.putString("key", key)
        args.putBoolean("multi", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(this, 0)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.layout.prefs_app_selector)
    }

    private fun openAppsBW(key: String?) {
        val args = Bundle()
        args.putString("key", key)
        args.putBoolean("multi", true)
        args.putBoolean("bw", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(this, 0)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.layout.prefs_app_selector)
    }

    private fun openShare(key: String?) {
        val args = Bundle()
        args.putString("key", key)
        args.putBoolean("multi", true)
        args.putBoolean("share", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(this, 0)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.layout.prefs_app_selector)
    }

    private fun openOpenWith(key: String?) {
        val args = Bundle()
        args.putString("key", key)
        args.putBoolean("multi", true)
        args.putBoolean("openwith", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(this, 0)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.layout.prefs_app_selector)
    }

    private fun openMultiAction(pref: Preference, actions: MultiAction.Actions) {
        val args = Bundle()
        args.putString("key", pref.key)
        args.putInt("actions", actions.ordinal)
        openSubFragment(MultiAction(), args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.Edit, pref.title.toString(), R.layout.prefs_multiaction)
    }

    fun openStandaloneApp(pref: Preference, targetFrag: Fragment, resultId: Int) {
        val args = Bundle()
        args.putString("key", pref.key)
        args.putBoolean("standalone", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(targetFrag, resultId)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_app, R.layout.prefs_app_selector)
    }

    fun openPrivacyAppEdit(targetFrag: Fragment, resultId: Int) {
        val args = Bundle()
        args.putBoolean("privacy", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(targetFrag, resultId)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.layout.prefs_app_selector)
    }

    fun openLockedAppEdit(targetFrag: Fragment, resultId: Int) {
        val args = Bundle()
        args.putBoolean("applock", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(targetFrag, resultId)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_apps, R.layout.prefs_app_selector)
    }

    fun openLaunchableList(pref: Preference, targetFrag: Fragment, resultId: Int) {
        val args = Bundle()
        args.putString("key", pref.key)
        args.putBoolean("custom_titles", true)
        val appSelector = AppSelector()
        appSelector.setTargetFragment(targetFrag, resultId)
        openSubFragment(appSelector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.launcher_renameapps_list_title, R.layout.prefs_app_selector)
    }

    fun openActivitiesItemList(pref: Preference) {
        val args = Bundle()
        args.putBoolean("activities", true)
        args.putString("key", pref.key)
        args.putString("titleResId", pref.title.toString())
        openSubFragment(SortableList(), args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, pref.title.toString(), R.layout.prefs_sortable_list)
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
                    if (category.isDynamic()) {
                        actionBar.setTitle(pref.title.toString() + " ⟲")
                    } else {
                        actionBar.setTitle(pref.title)
                    }
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

    override fun matchedRestartMask(): Int = pageRestartMask
}
