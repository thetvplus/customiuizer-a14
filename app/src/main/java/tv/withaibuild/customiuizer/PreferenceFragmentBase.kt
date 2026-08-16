package tv.withaibuild.customiuizer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewStub
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import java.util.Collections
import java.util.Locale
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
import tv.withaibuild.customiuizer.utils.AppLocaleController
import tv.withaibuild.customiuizer.utils.AppSelectionSanitizer
import tv.withaibuild.customiuizer.utils.BackupRestore
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.MatchedRestartExecutor
import tv.withaibuild.customiuizer.utils.RestartMask
import tv.withaibuild.customiuizer.utils.RestartPagePolicy
import tv.withaibuild.customiuizer.utils.XposedServiceManager

open class PreferenceFragmentBase : PreferenceFragmentCompat() {

    private var actContext: Context? = null

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

    @JvmField
    protected var headLayoutId = 0

    @JvmField
    protected var tailLayoutId = 0

    protected open fun getActionBar(): ActionBar? {
        val act = activity as AppCompatActivity?
        return act?.supportActionBar
    }

    protected open fun matchedRestartMask(): Int = RestartMask.NONE

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (toolbarMenu) {
            inflater.inflate(R.menu.menu_mods, menu)
        }
        if (isCustomActionBar) {
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                item.isVisible = item.itemId == R.id.edit_confirm
            }
        } else {
            val isMainFragment = this is MainFragment
            val matchedMask = if (!isMainFragment && toolbarMenu) matchedRestartMask() else RestartMask.NONE
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                val menuId = item.itemId
                item.isVisible = when {
                    menuId == R.id.restartmatched -> !isMainFragment && matchedMask != RestartMask.NONE
                    menuId == R.id.restartlauncher ||
                        menuId == R.id.restartsystemui ||
                        menuId == R.id.restartsecuritycenter -> {
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

    private fun restartMatchedTargets() {
        val mask = matchedRestartMask()
        if (mask == RestartMask.NONE) return

        val executor = MatchedRestartExecutor()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = executor.execute(mask)

            withContext(Dispatchers.Main) {
                val act = activity as? AppCompatActivity ?: return@withContext
                if (act.isFinishing || act.isDestroyed || !isAdded) return@withContext

                val message = when {
                    !result.rootGranted -> act.getString(R.string.restart_no_root)
                    result.failed == 0 -> act.getString(R.string.restart_affected_components_done)
                    result.succeeded == 0 -> act.getString(
                        R.string.restart_affected_components_failed,
                        formatFailedMask(result.failedMask)
                    )
                    else -> act.getString(
                        R.string.restart_affected_components_partial,
                        formatFailedMask(result.failedMask)
                    )
                }
                Toast.makeText(act, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatFailedMask(mask: Int): String {
        return RestartPagePolicy.toNameList(mask).joinToString(", ") { name ->
            when (name) {
                "launcher" -> getString(R.string.restart_launcher)
                "systemui" -> getString(R.string.restart_systemui)
                "securitycenter" -> getString(R.string.restart_securitycenter)
                else -> name
            }
        }
    }

    open fun confirmEdit() {}

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = AppHelper.prefsName
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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
            R.id.restartmatched -> {
                restartMatchedTargets()
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
        setHasOptionsMenu(toolbarMenu)
        val actionBar = getActionBar() ?: return

        val showBack = if (this is MainFragment) {
            val act = activity as AppCompatActivity?
            act != null && act.intent.getBooleanExtra("from.settings", false)
        } else {
            true
        }
        actionBar.setDisplayHomeAsUpEnabled(showBack)
    }

    @SuppressLint("WorldReadableFiles")
    open fun onCreate(savedInstanceState: Bundle?, prefDefaults: Int) {
        super.onCreate(savedInstanceState)
        try {
            PreferenceManager.setDefaultValues(getValidContext(), prefDefaults, false)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        }
    }

    protected open fun fixStubLayout(view: View, postion: Int) {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (headLayoutId > 0) {
            val stub = view.findViewById<ViewStub>(R.id.head_stub)
            stub.setLayoutResource(headLayoutId)
            fixStubLayout(stub.inflate(), 1)
        }
        if (tailLayoutId > 0) {
            val stub = view.findViewById<ViewStub>(R.id.tail_stub)
            stub.setLayoutResource(tailLayoutId)
            fixStubLayout(stub.inflate(), 2)
        }
        initFragment()
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
        } catch (_: Throwable) {
        }
        fragmentArgs.putFloat("order", order)

        val existingArgs = fragment.arguments
        if (existingArgs == null) {
            fragment.arguments = fragmentArgs
        } else {
            existingArgs.clear()
            existingArgs.putAll(fragmentArgs)
        }

        finishNavigationFeedback()
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

    /**
     * Finishes the source row's ripple before both fragments are translated side by side.
     * Only currently attached rows are touched, so the work is bounded by the viewport and
     * allocates no animator or long-lived state.
     */
    private fun finishNavigationFeedback() {
        val fragmentView = view ?: return
        fragmentView.isPressed = false
        fragmentView.jumpDrawablesToCurrentState()

        val preferenceList = getListView()
        preferenceList.stopScroll()
        preferenceList.isPressed = false
        preferenceList.jumpDrawablesToCurrentState()
        for (index in 0 until preferenceList.childCount) {
            val row = preferenceList.getChildAt(index)
            row.isPressed = false
            row.jumpDrawablesToCurrentState()
        }
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

        view.translationX = startTrans
        view.alpha = 1.0f

        val duration = (animDur * Helpers.getAnimationScale(2) + 0.5f).toLong()
        val animator = ValueAnimator.ofFloat(startTrans, endTrans)
        animator.duration = duration
        animator.interpolator = DecelerateInterpolator(1.2f)
        animator.addUpdateListener { animation ->
            view.translationX = animation.animatedValue as Float
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.translationX = endTrans
                view.alpha = 1.0f
            }

            override fun onAnimationCancel(animation: Animator) {
                view.translationX = endTrans
                view.alpha = 1.0f
            }
        })
        return animator
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

    open fun reloadPreferences() {
        if (!isAdded) return
        preferenceScreen = null
        onCreatePreferences(null, null)
        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .detach(this)
            .attach(this)
            .commitNowAllowingStateLoss()
        initFragment()
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
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/octet-stream"
        intent.putExtra(
            Intent.EXTRA_TITLE,
            BackupRestore.generateBackupFilename()
        )
        startActivityForResult(intent, SAVE_BACKFILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_BACKFILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                doRestoreSettings(data.data)
            }
        } else if (requestCode == SAVE_BACKFILE && resultCode == Activity.RESULT_OK) {
            try {
                val outputStream = getValidContext().contentResolver
                    .openOutputStream(data!!.data!!)
                    ?: throw IllegalStateException("Backup output stream unavailable")
                BackupRestore.performBackup(AppHelper.appPrefs!!, outputStream)

                AlertDialog.Builder(getValidContext())
                    .setTitle(R.string.do_backup)
                    .setMessage(R.string.backup_ok)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                t.printStackTrace()
                AlertDialog.Builder(getValidContext())
                    .setTitle(R.string.warning)
                    .setMessage(getString(R.string.storage_cannot_backup) + "\n" + t.message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
        }
    }

    open fun restoreSettings(act: AppCompatActivity) {
        if (!Helpers.checkStorageReadable(act)) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/octet-stream"
        startActivityForResult(intent, PICK_BACKFILE)
    }

    open fun doRestoreSettings(uri: Uri?) {
        val validAct = activity as? AppCompatActivity ?: return
        val validUri = uri ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = validAct.contentResolver.openInputStream(validUri)
                    ?: throw IllegalStateException("Backup input stream unavailable")
                val prefs = AppHelper.appPrefs
                    ?: throw IllegalStateException("Preferences unavailable")
                val componentName = ComponentName(validAct, GateWayLauncher::class.java)
                val result = BackupRestore.performRestore(
                    inputStream,
                    validAct.packageManager,
                    prefs,
                    componentName,
                )

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
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                t.printStackTrace()
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
        const val PICK_BACKFILE = 11
        const val SAVE_BACKFILE = 12

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
