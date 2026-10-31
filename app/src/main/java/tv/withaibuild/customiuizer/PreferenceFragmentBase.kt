package tv.withaibuild.customiuizer

import android.annotation.SuppressLint
import android.animation.Animator
import androidx.compose.runtime.*
import tv.withaibuild.customiuizer.ui.*
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import tv.withaibuild.customiuizer.ui.SettingsActionBar as ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import java.util.Collections
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.BackupRestore
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.XposedServiceManager

open class PreferenceFragmentBase : Fragment() {

    private val restoreDocument = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { doRestoreSettings(it) }
    }
    private val backupDocument = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { saveBackup(it) }
    }

    private var actContext: Context? = null
    lateinit var preferenceManager: PreferenceManager
        private set
    var preferenceScreen: androidx.preference.PreferenceScreen
        get() = requireNotNull(preferenceManager.preferenceScreen)
        set(value) { preferenceManager.setPreferences(value) }
    private var preferencesAttached = false
    private var dialogPreference by mutableStateOf<androidx.preference.Preference?>(null)
    private var scrollTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createPreferences(savedInstanceState)
    }

    // Retain AndroidX persistence and dependency dispatch behind the Compose presentation.
    @SuppressLint("RestrictedApi")
    private fun createPreferences(savedInstanceState: Bundle?) {
        preferenceManager = PreferenceManager(requireContext()).apply {
            sharedPreferencesName = AppHelper.prefsName
            sharedPreferencesMode = Context.MODE_PRIVATE
            onPreferenceTreeClickListener = PreferenceManager.OnPreferenceTreeClickListener { onPreferenceTreeClick(it) }
            onDisplayPreferenceDialogListener = PreferenceManager.OnDisplayPreferenceDialogListener { dialogPreference = it }
        }
        onCreatePreferences(savedInstanceState, null)
        savedInstanceState?.getBundle("settings.preferences")?.let { preferenceManager.preferenceScreen?.restoreHierarchyState(it) }
        savedInstanceState?.getString("settings.dialog")?.let { dialogPreference = findPreference(it) }
    }

    fun <T : androidx.preference.Preference> findPreference(key: CharSequence): T? = preferenceManager.findPreference(key)

    protected fun setSettingsPage(page: Int) {
        preferenceScreen = tv.withaibuild.customiuizer.prefs.ComposeSettingsPages.create(page, requireContext(), preferenceManager)
    }

    open fun onPreferenceTreeClick(preference: androidx.preference.Preference): Boolean = false

    protected fun requestPreferenceScroll(key: String) { scrollTarget = key }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?,
        savedInstanceState: Bundle?): View = settingsView {
        val screen = preferenceScreen
        val model = remember(screen) { SettingsPreferenceList(screen) }
        DisposableEffect(model) { onDispose { model.close() } }
        SettingsContent(model)
        dialogPreference?.let { preference ->
            val row = model.rows.firstOrNull { it.preference === preference }
            // Observe the same scalar invalidation as the visible row; an open editor cannot
            // outlive a disabled/hidden setting or a removed page entry.
            row?.revision?.intValue
            if (row == null || !preference.isEnabled || !preference.isVisible) {
                LaunchedEffect(preference) { dialogPreference = null }
            } else SettingsPreferenceEditor(preference) { dialogPreference = null }
        }
    }

    @Composable
    internal open fun SettingsContent(model: SettingsPreferenceList) {
        SettingsPreferencePage(model, scrollTarget, { scrollTarget = null }, { dialogPreference = it })
    }

    override fun onStart() {
        super.onStart()
        preferenceManager.preferenceScreen?.let { if (!preferencesAttached) { it.onAttached(); preferencesAttached = true } }
        (activity as? MainActivity)?.settingsBar?.bind(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val hierarchy = Bundle()
        preferenceManager.preferenceScreen?.saveHierarchyState(hierarchy)
        outState.putBundle("settings.preferences", hierarchy)
        dialogPreference?.key?.let { outState.putString("settings.dialog", it) }
        super.onSaveInstanceState(outState)
    }

    /**
     * AndroidX invokes change listeners before ListPreference persists its new value. A listener
     * that changes sibling visibility can therefore rebind this row against the previous entry.
     * Commit the accepted pending selection first so row text and dependent state share one value.
     */
    protected fun syncPendingListSelection(preference: androidx.preference.Preference, value: Any?): String? {
        val selected = value as? String ?: return null
        (preference as? ListPreference)?.value = selected
        return selected
    }

    @JvmField
    protected var toolbarMenu = false

    @JvmField
    protected var animDur = 350

    @JvmField
    protected var activeMenus = ""

    @JvmField
    protected var isCustomActionBar = false

    protected open fun getActionBar(): ActionBar? {
        val act = activity as AppCompatActivity?
        return (act as? MainActivity)?.settingsBar
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (toolbarMenu) {
            tv.withaibuild.customiuizer.ui.populateSettingsMenu(menu)
        }
        if (isCustomActionBar) {
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                item.isVisible = item.itemId == R.id.edit_confirm
            }
        } else {
            val isMainFragment = this is MainFragment
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                val menuId = item.itemId
                item.isVisible = when {
                    menuId == R.id.search_btn -> isMainFragment
                    menuId == R.id.restart_menu -> isMainFragment
                    menuId == R.id.restartlauncher ||
                        menuId == R.id.restartsystemui ||
                        menuId == R.id.restartsecuritycenter ||
                        menuId == R.id.softreboot -> {
                        isMainFragment && (activeMenus == "all" || activeMenus.contains(MAP_KEYS[menuId] ?: ""))
                    }
                    activeMenus == "all" && menuId == R.id.edit_confirm -> false
                    activeMenus == "all" -> true
                    else -> {
                        val menuKey = MAP_KEYS[menuId]
                        menuKey != null && activeMenus.contains(menuKey)
                    }
                }
            }
        }
    }

    open fun confirmEdit() {}

    open fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = AppHelper.prefsName
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (this !is MainFragment && item.itemId in RESTART_MENU_IDS) return false
        when (item.itemId) {
            R.id.edit_confirm -> {
                confirmEdit()
                return true
            }
            R.id.restartlauncher -> {
                restartTarget("com.miui.home", R.string.restart_launcher_done, R.string.restart_launcher_failed)
                return true
            }
            R.id.restartsystemui -> {
                restartTargetProcess("com.android.systemui", R.string.restart_systemui_done, R.string.restart_systemui_failed)
                return true
            }
            R.id.restartsecuritycenter -> {
                restartTarget("com.miui.securitycenter", R.string.restart_securitycenter_done, R.string.restart_securitycenter_failed)
                return true
            }
            R.id.backuprestore -> {
                showBackupRestoreDialog()
                return true
            }
            R.id.softreboot -> {
                confirmSoftReboot()
                return true
            }
            R.id.about -> {
                openSubFragment(
                    AboutFragment(),
                    null,
                    AppHelper.SettingsType.Edit,
                    AppHelper.ActionBarType.HomeUp,
                    R.string.app_about,
                    0
                )
                return true
            }
        }
        return false
    }

    /**
     * Waits until the bind state can no longer change the answer, i.e. the service bound,
     * died, or [XposedServiceManager.FULL_DECISION_BUDGET_MS] elapsed since registration.
     *
     * Returns false if the coroutine was cancelled, in which case the caller must stop.
     *
     * The budget is measured from registration, not from this call, so on any screen
     * opened more than a few seconds into the process this returns without waiting at all.
     * The upper bound is a safety net for the case where the manager was never started.
     */
    protected suspend fun awaitBindDecision(): Boolean {
        val deadline = System.currentTimeMillis() + XposedServiceManager.FULL_DECISION_BUDGET_MS
        while (!XposedServiceManager.isDecided() && System.currentTimeMillis() < deadline) {
            if (!coroutineContext.isActive) return false
            delay(100L)
        }
        return coroutineContext.isActive
    }

    /**
     * Asks, then sends the soft reboot to the copy of the module living in SystemUI.
     *
     * Deliberately not gated on the bind state. The reboot is a broadcast to
     * [GlobalActions.fastRebootReceiver], which the module registers inside SystemUI;
     * whether *this* process holds an LSPosed service binder has no bearing on whether that
     * receiver is there. Gating on it turned a settings app that had merely failed to bind -
     * which a captured log shows can last for the whole life of the process - into a settings
     * app that refused an action which would have worked.
     *
     * So the action is attempted, and the answer comes from the attempt: an ordered broadcast
     * that nobody claims arrives back carrying [GlobalActions.ACTION_UNHANDLED], and only
     * then is the user told anything. When it is claimed the device is already going down,
     * so the result usually never arrives - which is the correct outcome, not a missing one.
     *
     * A SystemUI still running the previous build claims nothing, because the result code is
     * new. The reported failure is then wrong, but it is also unreachable: the reboot has
     * already started by the time the result comes back. It resolves itself as soon as
     * SystemUI reloads the module.
     */
    private fun confirmSoftReboot() {
        val act = activity as? AppCompatActivity ?: return
        if (act.isFinishing || act.isDestroyed || !isAdded) return

        AlertDialog.Builder(getValidContext())
            .setTitle(R.string.soft_reboot)
            .setMessage(R.string.soft_reboot_ask)
            .setPositiveButton(android.R.string.ok) { _, _ -> sendSoftReboot() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun sendSoftReboot() {
        val intent = Intent(GlobalActions.ACTION_PREFIX + "FastReboot")
        // Explicitly addressed. An ordered broadcast with no package would be offered to
        // every receiver on the device before this one, any of which could claim it.
        intent.setPackage("com.android.systemui")

        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, received: Intent) {
                val message = when (resultCode) {
                    GlobalActions.ACTION_UNHANDLED -> R.string.fast_reboot_not_received
                    GlobalActions.ACTION_FAILED -> R.string.fast_reboot_failed
                    else -> return
                }
                // Resolved now, not captured at send time: the result can be up to the
                // broadcast timeout late, and by then the Activity that opened the
                // confirmation may be gone. Holding a reference to it would both leak it and
                // risk showing a dialog on a destroyed window.
                val current = activity as? AppCompatActivity ?: return
                if (!isAdded) return
                showFastRebootFailure(current, message)
            }
        }

        // One-shot: this receiver is passed to the broadcast, never registered, so there is
        // nothing to unregister and nothing left behind once the result comes back.
        ModuleHelper.sendOrderedBroadcastWithIdentity(
            getValidContext(),
            intent,
            null,
            resultReceiver,
            null,
            GlobalActions.ACTION_UNHANDLED,
            null,
            null
        )
    }

    private fun showFastRebootFailure(act: AppCompatActivity, message: Int) {
        if (act.isFinishing || act.isDestroyed) return
        AlertDialog.Builder(act)
            .setTitle(R.string.warning)
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
    }

    /**
     * Reports that the LSPosed service is not reachable.
     *
     * The wording deliberately stops short of "the module is not active". Only an observed
     * disconnect or a registration failure reaches this path; a bind timeout remains
     * unknown and must not be upgraded into a negative conclusion.
     *
     * When settings have been changed since the mirror last reached the module, that is added
     * to the message: it is the one consequence of an unbound service the user can see for
     * themselves, and without it a toggle that quietly does nothing looks like a broken
     * feature rather than a missing connection.
     */
    open fun showXposedDialog(act: AppCompatActivity?) {
        if (act == null || act.isFinishing || act.isDestroyed) return
        val message = if (XposedServiceManager.hasUndeliveredChanges()) {
            act.getString(R.string.lsposed_not_connected) + "\n\n" +
                act.getString(R.string.lsposed_changes_not_delivered)
        } else {
            act.getString(R.string.lsposed_not_connected)
        }
        AlertDialog.Builder(act)
            .setTitle(R.string.warning)
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
    }

    /**
     * Restarts a package by force-stopping it (root shell). The system will restart it on demand.
     */
    private fun restartTarget(packageName: String, successRes: Int, failureRes: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val idResult = AppHelper.executeRootCommand("id")
            if (idResult.first != 0 || !idResult.second.contains("uid=0")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getValidContext(), R.string.restart_no_root, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            if (!synchronizeForRestart()) return@launch
            val cmd = "am force-stop $packageName"
            val result = AppHelper.executeRootCommand(cmd)
            withContext(Dispatchers.Main) {
                val ctx = getValidContext()
                if (result.first == 0) {
                    Toast.makeText(ctx, successRes, Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("miuizer", "restart $packageName failed: exit=${result.first}, out=${result.second}")
                    Toast.makeText(ctx, "$failureRes: ${result.second}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Kills a running process by name (root shell). Used for SystemUI which should be restarted by the system.
     */
    private fun restartTargetProcess(processName: String, successRes: Int, failureRes: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val idResult = AppHelper.executeRootCommand("id")
            if (idResult.first != 0 || !idResult.second.contains("uid=0")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getValidContext(), R.string.restart_no_root, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            if (!synchronizeForRestart()) return@launch
            // pidof returns exit 1 when no process is found; handle that explicitly.
            val pidResult = AppHelper.executeRootCommand("pidof $processName")
            if (pidResult.first != 0 || pidResult.second.isBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getValidContext(), R.string.restart_target_not_running, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val pids = pidResult.second.split("\\s+".toRegex()).filter { it.isNotEmpty() }.joinToString(" ")
            val killResult = AppHelper.executeRootCommand("kill -9 $pids")
            withContext(Dispatchers.Main) {
                val ctx = getValidContext()
                if (killResult.first == 0) {
                    Toast.makeText(ctx, successRes, Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("miuizer", "kill $processName failed: exit=${killResult.first}, out=${killResult.second}")
                    Toast.makeText(ctx, "$failureRes: ${killResult.second}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun synchronizeForRestart(): Boolean {
        if (XposedServiceManager.synchronizeBeforeRestart()) return true
        withContext(Dispatchers.Main) {
            if (isAdded) Toast.makeText(getValidContext(), R.string.restart_settings_sync_failed, Toast.LENGTH_LONG).show()
        }
        return false
    }

    open fun showBackupRestoreDialog() {
        val act = activity as AppCompatActivity?
        if (act == null || act.isFinishing || act.isDestroyed) return

        AlertDialog.Builder(act)
            .setTitle(R.string.backup_restore)
            .setMessage(R.string.backup_restore_choose)
            .setPositiveButton(R.string.do_restore) { _, _ -> restoreSettings(act) }
            .setNegativeButton(R.string.do_backup) { _, _ -> backupSettings(act) }
            .show()
    }

    private fun initFragment() {
        val actionBar = getActionBar() ?: return

        val showBack = if (this is MainFragment) {
            val act = activity as AppCompatActivity?
            act != null && act.intent.getBooleanExtra("from.settings", false)
        } else {
            true
        }
        actionBar.setDisplayHomeAsUpEnabled(showBack)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initFragment()
    }

    override fun onDestroyView() {
        dialogPreference = null
        if (preferencesAttached) preferenceManager.preferenceScreen?.onDetached()
        preferencesAttached = false
        super.onDestroyView()
    }

    open fun openSubFragment(
        fragment: Fragment,
        args: Bundle?,
        settingsType: AppHelper.SettingsType,
        abType: AppHelper.ActionBarType,
        titleResId: Int,
        contentResId: Int
    ) {
        openSubFragment(
            fragment,
            args,
            settingsType,
            abType,
            resources.getString(titleResId),
            contentResId
        )
    }

    open fun openSubFragment(
        fragment: Fragment,
        args: Bundle?,
        settingsType: AppHelper.SettingsType,
        abType: AppHelper.ActionBarType,
        title: String?,
        contentResId: Int
    ) {
        if (!isAdded) return
        val fragmentManager = parentFragmentManager
        if (fragmentManager.isStateSaved) return

        val fragmentArgs = args ?: Bundle()
        fragmentArgs.putInt("settingsType", settingsType.ordinal)
        fragmentArgs.putInt("abType", abType.ordinal)
        fragmentArgs.putString("titleResId", title)
        fragmentArgs.putInt("contentResId", contentResId)
        var order = 100.0f
        try {
            val view = view
            if (view != null) order = view.translationZ
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
        }
        fragmentArgs.putFloat("order", order)

        val existingArgs = fragment.arguments
        if (existingArgs == null) {
            fragment.arguments = fragmentArgs
        } else {
            existingArgs.clear()
            existingArgs.putAll(fragmentArgs)
        }

        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                R.animator.fragment_open_enter,
                R.animator.fragment_open_exit,
                R.animator.fragment_close_enter,
                R.animator.fragment_close_exit
            )
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onCreateAnimator(transit: Int, enter: Boolean, nextAnim: Int): Animator? {
        if (nextAnim == 0) return null

        val view = view ?: return null
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        if (screenWidth <= 0) return null

        val startTrans: Float
        val endTrans: Float
        when (nextAnim) {
            R.animator.fragment_open_enter -> {
                startTrans = screenWidth
                endTrans = 0.0f
            }
            R.animator.fragment_open_exit -> {
                startTrans = 0.0f
                endTrans = -screenWidth
            }
            R.animator.fragment_close_enter -> {
                startTrans = -screenWidth
                endTrans = 0.0f
            }
            R.animator.fragment_close_exit -> {
                startTrans = 0.0f
                endTrans = screenWidth
            }
            else -> return null
        }

        // ValueAnimator applies the system duration scale. Multiplying here applies it twice
        // and adds a settings-provider read while FragmentManager prepares each transition.
        // Fragment collects animators before preparing the view's entry visibility.
        // Changing alpha here makes it skip the entry animation and leaves the page
        // offscreen. Let Fragment own visibility and move the view only when started.
        return ObjectAnimator.ofFloat(view, View.TRANSLATION_X, startTrans, endTrans).apply {
            this.duration = animDur.toLong()
            interpolator = DecelerateInterpolator(1.2f)
        }
    }

    private var currentLocale: Locale? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        actContext = context
        currentLocale = context.resources.configuration.locales[0]
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newLocale = newConfig.locales[0]
        if (newLocale != currentLocale) {
            currentLocale = newLocale
            reloadPreferences()
        }
    }

    private var reloadPending = false

    override fun onResume() {
        super.onResume()
        if (reloadPending) lifecycleScope.launch {
            // FragmentManager may still be dispatching resume. No persistent callback.
            yield()
            if (isResumed && reloadPending) reloadPreferences()
        }
    }

    open fun reloadPreferences() {
        if (!isAdded) return
        if (parentFragmentManager.isStateSaved) {
            reloadPending = true
            return
        }
        reloadPending = false
        val dialogKey = dialogPreference?.key
        val hierarchy = Bundle()
        preferenceManager.preferenceScreen?.saveHierarchyState(hierarchy)
        if (preferencesAttached) preferenceManager.preferenceScreen?.onDetached()
        preferencesAttached = false
        preferenceManager.setPreferences(null)
        onCreatePreferences(null, null)
        preferenceManager.preferenceScreen?.restoreHierarchyState(hierarchy)
        // Separate transactions are required to actually recreate the Compose view.
        parentFragmentManager.beginTransaction().detach(this).commitNow()
        dialogPreference = dialogKey?.let { findPreference(it) }
        parentFragmentManager.beginTransaction().attach(this).commitNow()
    }

    override fun onDetach() {
        super.onDetach()
        actContext = null
    }

    open fun getValidContext(): Context {
        val attachedContext = actContext
        if (attachedContext != null) return attachedContext
        val act = activity
        return if (act == null) requireContext() else act.applicationContext
    }

    @Suppress("UNUSED_PARAMETER")
    open fun backupSettings(act: AppCompatActivity) {
        backupDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, BackupRestore.generateBackupFilename())
        })
    }

    private fun saveBackup(uri: Uri) {
        val owner = activity as? AppCompatActivity ?: return
        val app = owner.applicationContext
        lifecycleScope.launch {
            try {
                val completed = BackupRestore.runDocumentOperation {
                    val prefs = AppHelper.appPrefs ?: error("Preferences unavailable")
                    val output = app.contentResolver.openOutputStream(uri)
                        ?: error("Backup output stream unavailable")
                    BackupRestore.performBackup(prefs, output)
                } ?: return@launch
                if (completed && isAdded && !owner.isFinishing && !owner.isDestroyed) {
                    AlertDialog.Builder(owner)
                        .setTitle(R.string.do_backup)
                        .setMessage(R.string.backup_ok)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                coroutineContext.ensureActive()
                Log.e("BackupRestore", "Backup failed", t)
                if (isAdded && !owner.isFinishing && !owner.isDestroyed) {
                    AlertDialog.Builder(owner)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.storage_cannot_backup)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    open fun restoreSettings(act: AppCompatActivity) {
        // SAF providers own availability, including cloud and internal storage.
        restoreDocument.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        })
    }

    open fun doRestoreSettings(uri: Uri?) {
        val validAct = activity as? AppCompatActivity ?: return
        val validUri = uri ?: return
        val app = validAct.applicationContext
        lifecycleScope.launch {
            try {
                val result = BackupRestore.runDocumentOperation {
                    val prefs = AppHelper.appPrefs
                        ?: throw IllegalStateException("Preferences unavailable")
                    val inputStream = app.contentResolver.openInputStream(validUri)
                        ?: throw IllegalStateException("Backup input stream unavailable")
                    val componentName = ComponentName(app, Helpers.LAUNCHER_ALIAS_NAME)
                    BackupRestore.performRestore(
                        inputStream,
                        app.packageManager,
                        prefs,
                        componentName,
                    )
                } ?: return@launch

                withContext(Dispatchers.Main) {
                    if (validAct.isFinishing || validAct.isDestroyed || !isAdded) return@withContext
                    when (result.status) {
                        BackupRestore.Status.SUCCESS -> {
                            AlertDialog.Builder(validAct)
                                .setTitle(R.string.do_restore)
                                .setMessage(R.string.restore_ok)
                                .setCancelable(false)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    validAct.finish()
                                    validAct.startActivity(validAct.intent)
                                }
                                .show()
                        }
                        BackupRestore.Status.PARTIAL_FAILURE -> {
                            AlertDialog.Builder(validAct)
                                .setTitle(R.string.warning)
                                .setMessage(R.string.storage_cannot_restore)
                                .setPositiveButton(android.R.string.ok) { _, _ -> }
                                .show()
                        }
                        BackupRestore.Status.FAILURE -> {
                            AlertDialog.Builder(validAct)
                                .setTitle(R.string.warning)
                                .setMessage(R.string.storage_cannot_restore)
                                .setPositiveButton(android.R.string.ok) { _, _ -> }
                                .show()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                coroutineContext.ensureActive()
                Log.e("BackupRestore", "Restore failed", t)
                withContext(Dispatchers.Main) {
                    if (validAct.isFinishing || validAct.isDestroyed || !isAdded) return@withContext
                    AlertDialog.Builder(validAct)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.storage_cannot_restore)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                }
            }
        }
    }

    companion object {
        private val RESTART_MENU_IDS = intArrayOf(
            R.id.restartlauncher, R.id.restartsystemui, R.id.restartsecuritycenter, R.id.softreboot,
        )

        @JvmField
        protected val MAP_KEYS: Map<Int, String>

        init {
            val map = HashMap<Int, String>()
            map[R.id.search_btn] = "search"
            map[R.id.restartlauncher] = "launcher"
            map[R.id.restartsystemui] = "systemui"
            map[R.id.restartsecuritycenter] = "securitycenter"
            map[R.id.edit_confirm] = "edit"
            map[R.id.softreboot] = "reboot"
            map[R.id.backuprestore] = "settings"
            map[R.id.resetsettings] = "reset"
            map[R.id.about] = "about"
            MAP_KEYS = Collections.unmodifiableMap(map)
        }
    }
}
