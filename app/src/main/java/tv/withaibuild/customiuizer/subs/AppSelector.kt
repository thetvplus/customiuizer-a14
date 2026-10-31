package tv.withaibuild.customiuizer.subs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope

import java.lang.reflect.Method

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.AppSelectionRow
import tv.withaibuild.customiuizer.SubFragmentWithSearch
import tv.withaibuild.customiuizer.utils.selectedAppsFirst
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.AppData
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.AppSelectionSanitizer
import tv.withaibuild.customiuizer.utils.DynamicAppScope
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

class AppSelector : SubFragmentWithSearch() {

    private var sourceApps: List<AppData> = emptyList()
    private val lockedApps = HashSet<String>()
    private var initialized = false
    private var standalone = false
    private var multi = false
    private var bwlist = false
    private var privacy = false
    private var applock = false
    private var customTitles = false
    private var share = false
    private var openwith = false
    private var selectActivity = false
    private var configFetched = false
    private var receiverContext: Context? = null
    private var key: String? = null
    private var mPrivacyAppsMap: HashMap<Int, ArrayList<String>>? = null

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != GlobalActions.EVENT_PREFIX + "PUSHAPPCONFIG") {
                if (isOrderedBroadcast) setResultCode(GlobalActions.ACTION_FAILED)
                return
            }
            if (!ModuleHelper.isTrustedBroadcast(this, "com.miui.home", rejectionResultCode = GlobalActions.ACTION_FAILED)) return
            val datatype = intent.getStringExtra("DATATYPE")
            if (datatype == "privacy") {
                val received = intent.getSerializableExtra("privacyAppsMap", HashMap::class.java) ?: return
                val validated = HashMap<Int, ArrayList<String>>()
                for ((user, apps) in received) {
                    if (user !is Int || apps !is List<*> || apps.any { it !is String }) return
                    validated[user] = ArrayList(apps.filterIsInstance<String>())
                }
                configFetched = true
                initialized = true
                mPrivacyAppsMap = validated
                if (view != null) viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    if (isAdded) setupList()
                }
            }
            if (isOrderedBroadcast) setResultCode(GlobalActions.ACTION_HANDLED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
        standalone = args?.getBoolean("standalone", false) ?: false
        multi = args?.getBoolean("multi", false) ?: false
        bwlist = args?.getBoolean("bw", false) ?: false
        privacy = args?.getBoolean("privacy", false) ?: false
        applock = args?.getBoolean("applock", false) ?: false
        customTitles = args?.getBoolean("custom_titles", false) ?: false
        share = args?.getBoolean("share", false) ?: false
        openwith = args?.getBoolean("openwith", false) ?: false
        selectActivity = args?.getBoolean("activity", false) ?: false
        key = args?.getString("key")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (initialized) {
            setupList()
        } else {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                delay(animDur.toLong())
                loadApps()
                if (privacy && !configFetched) return@launch
                initialized = true
                withContext(Dispatchers.Main) {
                    if (isAdded) setupList()
                }
            }
            registerReceivers()
        }
    }

    private fun loadApps() {
        val ctx = context ?: return
        try {
            if (selectActivity || privacy || applock || (multi && key != null)) {
                if (openwith) {
                    if (Helpers.openWithAppsList == null) {
                        Helpers.getOpenWithApps(ctx)
                    }
                } else if (share) {
                    if (Helpers.shareAppsList == null) {
                        Helpers.getShareApps(ctx)
                    }
                } else {
                    if (AppHelper.installedAppsList == null) {
                        Helpers.getInstalledApps(ctx)
                    }
                }
            } else {
                if (Helpers.launchableAppsList == null) {
                    Helpers.getLaunchableApps(ctx)
                }
            }
            if (applock) {
                val manager = ctx.getSystemService("security")
                val method = manager?.javaClass?.getDeclaredMethod("getApplicationAccessControlEnabledAsUser",
                    String::class.java, Int::class.javaPrimitiveType)?.apply { isAccessible = true }
                val result = HashSet<String>()
                for (app in AppHelper.installedAppsList.orEmpty()) {
                    if (method?.invoke(manager, app.pkgName, app.user) == true) result.add("${app.pkgName}|${app.user}")
                }
                synchronized(lockedApps) { lockedApps.clear(); lockedApps.addAll(result) }
            }
        } catch (e: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(e)
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    private fun setupList() {
        val context = context ?: return
        if (!isAdded) return

        val currentKey = key ?: ""
        if (multi && currentKey != "") {
            val apps = when {
                openwith -> Helpers.openWithAppsList ?: return
                share -> Helpers.shareAppsList ?: return
                else -> AppHelper.installedAppsList ?: return
            }
            val multiUser = openwith || share
            val availableIdentifiers = HashSet<String>(apps.size * 4 / 3 + 1)
            for (app in apps) {
                availableIdentifiers.add(
                    if (multiUser) "${app.pkgName}|${app.user}" else app.pkgName,
                )
            }
            AppHelper.appPrefs?.let { prefs ->
                AppSelectionSanitizer.sanitizeOpenSelector(
                    prefs,
                    currentKey,
                    availableIdentifiers,
                    multiUser,
                    bwlist,
                )
            }
            if (DynamicAppScope.requiresTargetAppScope(currentKey)) {
                DynamicAppScope.requestForSelection(
                    context,
                    currentKey,
                    AppHelper.getStringSetOfAppPrefs(currentKey, emptySet()),
                )
            }

        }
        val available = when {
            openwith -> Helpers.openWithAppsList
            share -> Helpers.shareAppsList
            privacy || applock || selectActivity || multi -> AppHelper.installedAppsList
            else -> Helpers.launchableAppsList
        } ?: return
        sourceApps = available.filter { !(multi && !share && !openwith ||
            selectActivity && currentKey.contains("pref_key_system_applock_skip_activities")) || it.user == 0 }
        if (standalone && !multi && currentKey.isNotEmpty()) sourceApps = listOf(AppData().apply {
            label = getString(R.string.array_default); enabled = true; prepareForList()
        }) + sourceApps
        sourceApps.forEach { it.prepareForList() }
        refreshRows(sort = true)
        loading = false
    }

    private fun refreshRows(sort: Boolean = false) {
        val selected = if (multi) AppHelper.getStringSetOfAppPrefs(key, emptySet()) else emptySet()
        val black = if (bwlist) AppHelper.getStringSetOfAppPrefs(key + "_black", emptySet()) else emptySet()
        val selectedApp = if (standalone && !multi) AppHelper.getStringOfAppPrefs(key, "") else ""
        val selectedUser = AppHelper.getIntOfAppPrefs(key + "_user", 0)
        val rows = sourceApps.map { app ->
            val identifier = if (share || openwith) "${app.pkgName}|${app.user}" else app.pkgName
            val checked = when {
                multi -> identifier in selected || !share && !openwith && "${app.pkgName}|0" in selected
                privacy -> mPrivacyAppsMap?.get(app.user)?.contains(app.pkgName) == true
                applock -> "${app.pkgName}|${app.user}" in lockedApps
                standalone -> selectedUser == app.user && (selectedApp == "${app.pkgName}|${app.actName}" || selectedApp.isNullOrEmpty() && app.pkgName.isEmpty())
                else -> null
            }
            AppSelectionRow(app,
                if (customTitles) AppHelper.getStringOfAppPrefs("$key:${app.pkgName}|${app.actName}|${app.user}", "").orEmpty() else "",
                if (bwlist) null else checked,
                if (bwlist) getString(when { app.pkgName in selected -> R.string.a14_selection_allow; app.pkgName in black -> R.string.a14_selection_block; else -> R.string.array_default }) else "",
                !applock || app.pkgName != "com.miui.securitycenter")
        }
        appRows = if (sort && (multi || privacy || applock || standalone))
            selectedAppsFirst(ArrayList(rows)) { it.app.pkgName.isEmpty() && it.app.actName.isEmpty() || it.selected == true || bwlist && (it.app.pkgName in selected || it.app.pkgName in black) }
            else rows
    }

    override fun selectApp(row: AppSelectionRow) {
        val app = row.app
        if (multi && key != null) {
            handleMultiSelect(app)
        } else if (selectActivity) {
            val args = Bundle().apply { putString("key", key); putString("package", app.pkgName); putInt("user", app.user) }
            val selector = ActivitySelector().apply { setTargetFragment(this@AppSelector, targetRequestCode) }
            openSubFragment(selector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_activity, R.id.prefs_app_selector)
        } else if (privacy) {
            toggleAppPrivacy(app); refreshRows()
        } else if (applock) {
            handleAppLock(app)
        } else if (customTitles) {
            val k = key ?: return
            AppHelper.showInputDialog(activity, "$k:${app.pkgName}|${app.actName}|${app.user}", R.string.launcher_renameapps_modified, 0, 1) { _, text ->
                val editor = AppHelper.appPrefs?.edit() ?: return@showInputDialog
                val name = "$k:${app.pkgName}|${app.actName}|${app.user}"
                if (text.isNullOrEmpty()) editor.remove(name) else editor.putString(name, text)
                editor.apply(); refreshRows()
            }
        } else {
            val intent = Intent().putExtra("app", if (app.pkgName.isEmpty() && app.actName.isEmpty()) "" else "${app.pkgName}|${app.actName}").putExtra("user", app.user)
            targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun handleMultiSelect(app: AppData) {
        val selectedApps = LinkedHashSet(AppHelper.getStringSetOfAppPrefs(key, LinkedHashSet()))
        if (bwlist) {
            val selectedAppsBlack = LinkedHashSet(AppHelper.getStringSetOfAppPrefs(key + "_black", LinkedHashSet()))
            when {
                selectedApps.contains(app.pkgName) -> {
                    selectedApps.remove(app.pkgName)
                    selectedAppsBlack.add(app.pkgName)
                }
                selectedAppsBlack.contains(app.pkgName) -> {
                    selectedApps.remove(app.pkgName)
                    selectedAppsBlack.remove(app.pkgName)
                }
                else -> {
                    selectedApps.add(app.pkgName)
                    selectedAppsBlack.remove(app.pkgName)
                }
            }
            AppHelper.appPrefs!!.edit().putStringSet(key + "_black", selectedAppsBlack).apply()
        } else {
            val identifier = if (share || openwith) app.pkgName + "|" + app.user else app.pkgName
            if (selectedApps.contains(identifier)) {
                selectedApps.remove(identifier)
            } else {
                selectedApps.add(identifier)
                if (openwith) {
                    val mimeKey = key + "_" + app.pkgName + "|" + app.user
                    val mimeFlags = AppHelper.getIntOfAppPrefs(mimeKey, Helpers.MimeType.ALL)
                    val checkedTypes = booleanArrayOf(
                        (mimeFlags and Helpers.MimeType.IMAGE) == Helpers.MimeType.IMAGE,
                        (mimeFlags and Helpers.MimeType.AUDIO) == Helpers.MimeType.AUDIO,
                        (mimeFlags and Helpers.MimeType.VIDEO) == Helpers.MimeType.VIDEO,
                        (mimeFlags and Helpers.MimeType.DOCUMENT) == Helpers.MimeType.DOCUMENT,
                        (mimeFlags and Helpers.MimeType.ARCHIVE) == Helpers.MimeType.ARCHIVE,
                        (mimeFlags and Helpers.MimeType.LINK) == Helpers.MimeType.LINK,
                        (mimeFlags and Helpers.MimeType.OTHERS) == Helpers.MimeType.OTHERS
                    )
                    val builder = AlertDialog.Builder(requireActivity())
                    builder.setTitle(R.string.system_cleanopenwith_datatype)
                    builder.setMultiChoiceItems(R.array.mimetypes, checkedTypes) { _, which, isChecked ->
                        checkedTypes[which] = isChecked
                    }
                    builder.setCancelable(true)
                    builder.setPositiveButton(android.R.string.ok) { _, _ ->
                        var sum = 0
                        var order = 0
                        for (checkedType in checkedTypes) {
                            if (checkedType) sum += 1 shl order
                            order++
                        }
                        AppHelper.appPrefs!!.edit().putInt(mimeKey, sum).apply()
                    }
                    builder.show()
                }
            }
        }
        AppHelper.appPrefs!!.edit().putStringSet(key, selectedApps).apply()
        DynamicAppScope.requestForSelection(context, key, selectedApps)
        refreshRows()
    }

    @SuppressLint("WrongConstant")
    private fun handleAppLock(app: AppData) {
        val act = activity ?: return
        try {
            val mSecurityManager = act.getSystemService("security") ?: return
            val smClass = (mSecurityManager as Any)::class.java
            val getApplicationAccessControlEnabledAsUser: Method = smClass.getDeclaredMethod("getApplicationAccessControlEnabledAsUser", String::class.java, Int::class.javaPrimitiveType)
            getApplicationAccessControlEnabledAsUser.isAccessible = true
            val setApplicationAccessControlEnabledForUser: Method = smClass.getDeclaredMethod("setApplicationAccessControlEnabledForUser", String::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            setApplicationAccessControlEnabledForUser.isAccessible = true
            val enabled = getApplicationAccessControlEnabledAsUser.invoke(mSecurityManager, app.pkgName, app.user) as? Boolean ?: return
            setApplicationAccessControlEnabledForUser.invoke(mSecurityManager, app.pkgName, !enabled, app.user)
            val identifier = "${app.pkgName}|${app.user}"
            if (enabled) lockedApps.remove(identifier) else lockedApps.add(identifier)
            refreshRows()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun registerReceivers() {
        if (privacy) {
            val ctx = receiverContext ?: getValidContext().applicationContext.also {
                it.registerReceiver(configReceiver, IntentFilter(GlobalActions.EVENT_PREFIX + "PUSHAPPCONFIG"), null, null, Context.RECEIVER_EXPORTED)
                receiverContext = it
            }
            val intent = Intent(GlobalActions.EVENT_PREFIX + "FETCHAPPCONFIG")
            intent.putExtra("DATATYPE", "privacy")
            intent.setPackage("com.miui.home")
            ModuleHelper.sendBroadcastWithIdentity(ctx, intent)
        }
    }

    private fun unregisterReceivers() {
        val ctx = receiverContext ?: return
        receiverContext = null
        try {
            ctx.unregisterReceiver(configReceiver)
        } catch (t: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(t) }
    }

    override fun onDestroy() {
        unregisterReceivers()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == targetRequestCode) {
            targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_OK, data)
            finish()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun toggleAppPrivacy(app: AppData) {
        val map = mPrivacyAppsMap ?: return
        val user = app.user
        val privacyApps = map[user] ?: ArrayList<String>().also { map[user] = it }
        val isPrivate = !privacyApps.contains(app.pkgName)
        if (privacyApps.contains(app.pkgName)) {
            privacyApps.remove(app.pkgName)
        } else {
            privacyApps.add(app.pkgName)
        }
        val intent = Intent(GlobalActions.EVENT_PREFIX + "FETCHAPPCONFIG")
        intent.putExtra("DATATYPE", "privacy_change")
        intent.putExtra("app", app.pkgName)
        intent.putExtra("userId", app.user)
        intent.putExtra("privacy", isPrivate)
        intent.setPackage("com.miui.home")
        ModuleHelper.sendBroadcastWithIdentity(getValidContext(), intent)
    }
}
