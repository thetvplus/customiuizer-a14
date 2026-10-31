package tv.withaibuild.customiuizer.subs

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import miui.os.Build
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx
import tv.withaibuild.customiuizer.prefs.PreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.MaintenanceJournal
import tv.withaibuild.customiuizer.utils.parseMaintenanceStates
import tv.withaibuild.customiuizer.utils.XposedServiceManager

class Various : SubFragment() {
    private val maintenanceContext by lazy { requireContext().applicationContext }
    private val maintenanceJournal get() = MaintenanceJournal.open(maintenanceContext)

    private fun recoverySnapshot(key: String) = ModuleHelper.guarded<MaintenanceJournal.Snapshot?>(null) {
        maintenanceJournal.snapshot(key)
    }

    private fun configureMaintenancePreference(preference: CheckBoxPreferenceEx) {
        if (!preference.extras.containsKey("maintenance_summary"))
            preference.extras.putCharSequence("maintenance_summary", preference.summary)
        preference.isPersistent = false
        val configured = AppHelper.appPrefs?.getBoolean(preference.key, false) == true
        val retained = maintenanceJournal.contains(preference.key)
        preference.isChecked = configured || retained
        preference.summary = if (retained && !configured) maintenanceContext.getString(R.string.various_maintenance_recovery)
            else preference.extras.getCharSequence("maintenance_summary")
    }

    private companion object {
        const val UPDATER_PACKAGE = "com.android.updater"
        const val UPDATE_SERVICES_PREF = "pref_key_various_disable_update_services"
        const val UPDATE_SERVICE_NAMES_SNAPSHOT = "internal_updater_service_names"
        const val UPDATE_SERVICE_STATES_SNAPSHOT = "internal_updater_service_states"
        const val CLEAR_UPDATE_PREF = "pref_key_various_clear_update_state"
        const val MIUI_DAEMON_PACKAGE = "com.miui.daemon"
        const val MIUI_DAEMON_PREF = "pref_key_various_disable_miui_daemon"
        const val MIUI_DAEMON_STATE_SNAPSHOT = "internal_miui_daemon_application_state"
        const val DAEMON_NETWORK_TRIM_PREF = "pref_key_various_trim_miui_daemon_network"
        const val XIAOMI_ANALYTICS_TRIM_PREF = "pref_key_various_disable_xiaomi_analytics"
        const val SECURITY_CENTER_MARKETING_TRIM_PREF =
            "pref_key_various_trim_security_center_marketing"
        const val SECURITY_CENTER_ANTIVIRUS_TRIM_PREF =
            "pref_key_various_remove_security_center_antivirus"
        const val PROFILE_PACKAGES_SUFFIX = "_packages"
        const val PROFILE_COMPONENTS_SUFFIX = "_components"
        const val PROFILE_STATES_SUFFIX = "_states"
        val DAEMON_NETWORK_COMPONENTS = listOf(
            ComponentName(
                MIUI_DAEMON_PACKAGE,
                "com.miui.daemon.performance.cloudcontrol.CloudControlSyncService"
            ),
            ComponentName(
                MIUI_DAEMON_PACKAGE,
                "com.miui.daemon.performance.mispeed.CloudServerReceiver"
            ),
            ComponentName(
                MIUI_DAEMON_PACKAGE,
                "com.miui.daemon.mqsas.jobs.EventUploadService"
            ),
            ComponentName(
                MIUI_DAEMON_PACKAGE,
                "com.miui.daemon.mqsas.jobs.FileUploadService"
            ),
            ComponentName(
                MIUI_DAEMON_PACKAGE,
                "com.miui.daemon.mqsas.jobs.HeartBeatUploadService"
            )
        )
        val SECURITY_CENTER_MARKETING_COMPONENTS = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.analytics.RemoteAnalyticsReceiver"),
            ComponentName(
                "com.miui.securitycenter",
                "com.facebook.ads.AudienceNetworkContentProvider"
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.my.target.common.MyTargetContentProvider"
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.yandex.mobile.ads.core.initializer.MobileAdsInitializeProvider"
            ),
            ComponentName("com.miui.securitycenter", "com.yandex.metrica.MetricaService"),
            ComponentName("com.miui.securitycenter", "com.yandex.metrica.ConfigurationService"),
            ComponentName(
                "com.miui.securitycenter",
                "com.yandex.metrica.ConfigurationJobService"
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.yandex.metrica.PreloadInfoContentProvider"
            )
        )
        val SECURITY_CENTER_ANTIVIRUS_COMPONENTS = listOf(
            "com.miui.antivirus.activity.MainActivity",
            "com.miui.antivirus.activity.SettingsActivity",
            "com.miui.antivirus.whitelist.WhiteListActivity",
            "com.miui.antivirus.activity.VirusMonitorDialogActivity",
            "com.miui.antivirus.activity.DangerousAlertActivity",
            "com.miui.antivirus.activity.VirusDetailActivity",
            "com.miui.antivirus.activity.MonitoredAppSettingsActivity",
            "com.miui.antivirus.activity.WebsiteSecurityCheckActivity",
            "com.miui.antivirus.activity.SignExceptionActivity",
            "com.miui.antivirus.service.GuardService",
            "com.miui.antivirus.service.DialogService",
            "com.miui.antivirus.service.VirusAutoUpdateJobService",
            "com.miui.antivirus.receiver.UpdaterReceiver",
            "com.miui.securityscan.job.ScanJobService"
        ).map { className -> ComponentName("com.miui.securitycenter", className) }
        val XIAOMI_ANALYTICS_PACKAGES = listOf("com.miui.analytics", "com.miui.msa.global")
        const val BLOCK_NOTIFICATION_PROMPTS_PREF =
            "pref_key_various_block_notification_permission_prompts"
        const val BLOCK_LOCATION_PROMPTS_PREF =
            "pref_key_various_block_location_permission_prompts"
        val PERMISSION_CONTROLLER_PACKAGES = listOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        selectSub()
        if (Build.IS_INTERNATIONAL_BUILD) {
            findPreference<Preference>("pref_key_various_disable_defraud_apps_detect")?.isVisible = false
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        findPreference<Preference>("pref_key_various_alarmcompat_apps")?.setOnPreferenceClickListener(openAppsEdit)

        findPreference<Preference>("pref_key_various_calluibright_cat")?.setOnPreferenceClickListener {
            openSubFragment(Various_CallUIBright(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.various_calluibright_title, R.id.prefs_various_calluibright)
            true
        }

        findPreference<Preference>("pref_key_various_hiddenfeatures_cat")?.setOnPreferenceClickListener {
            openSubFragment(Various_HiddenFeatures(), null, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.various_hiddenfeatures_title, R.id.prefs_various_hiddenfeatures)
            true
        }

        setupUpdaterServiceControl()
        setupUpdaterStateCleaner()
        setupMiuiDaemonControl()
        setupComponentTrimProfile(
            DAEMON_NETWORK_TRIM_PREF,
            DAEMON_NETWORK_COMPONENTS,
            R.string.various_trim_miui_daemon_network_confirm
        )
        setupPackageTrimProfile(
            XIAOMI_ANALYTICS_TRIM_PREF,
            XIAOMI_ANALYTICS_PACKAGES,
            R.string.various_disable_xiaomi_analytics_confirm
        )
        setupComponentTrimProfile(
            SECURITY_CENTER_MARKETING_TRIM_PREF,
            SECURITY_CENTER_MARKETING_COMPONENTS,
            R.string.various_trim_security_center_marketing_confirm
        )
        setupComponentTrimProfile(
            SECURITY_CENTER_ANTIVIRUS_TRIM_PREF,
            SECURITY_CENTER_ANTIVIRUS_COMPONENTS,
            R.string.various_remove_security_center_antivirus_confirm
        )
        setupPermissionControllerScopeRequests()

        try {
            val act = activity ?: throw Throwable()
            val pkgInfo = act.packageManager.getApplicationInfo("com.miui.packageinstaller", PackageManager.MATCH_DISABLED_COMPONENTS)
            if (!pkgInfo.enabled) throw Throwable()
        } catch (e: Throwable) {
            val pref = findPreference<CheckBoxPreferenceEx>("pref_key_various_miuiinstaller")
            pref?.isChecked = false
            pref?.setUnsupported(true)
            pref?.setSummary(R.string.various_miuiinstaller_error)
        }
    }

    private fun setupPermissionControllerScopeRequests() {
        val scopeContext = context?.applicationContext ?: return
        val packageManager = scopeContext.packageManager
        val installedTargets = PERMISSION_CONTROLLER_PACKAGES.filter { packageName ->
            try {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_DISABLED_COMPONENTS
                )
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        if (installedTargets.isEmpty()) return
        for (key in listOf(BLOCK_NOTIFICATION_PROMPTS_PREF, BLOCK_LOCATION_PROMPTS_PREF)) {
            val preference = findPreference<CheckBoxPreferenceEx>(key) ?: continue
            preference.setOnPreferenceChangeListener { _, newValue ->
                if (newValue != true) return@setOnPreferenceChangeListener true
                val requested = XposedServiceManager.requestScope(installedTargets) {
                        success, message ->
                    if (!success) {
                        AppHelper.log("PermissionScope", message ?: "scope request rejected")
                        Toast.makeText(
                            scopeContext,
                            R.string.various_permission_scope_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                if (!requested) {
                    Toast.makeText(
                        scopeContext,
                        R.string.various_permission_scope_unavailable,
                        Toast.LENGTH_LONG
                    ).show()
                }
                true
            }
        }
    }

    private fun setupUpdaterStateCleaner() {
        val preference = findPreference<PreferenceEx>(CLEAR_UPDATE_PREF) ?: return
        preference.setOnPreferenceClickListener {
            val activity = activity ?: return@setOnPreferenceClickListener false
            AlertDialog.Builder(activity)
                .setTitle(R.string.various_clear_update_state_title)
                .setMessage(R.string.various_clear_update_state_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestMaintenanceAction(
                        preference,
                        Intent(GlobalActions.CLEAR_UPDATER_STATE_ACTION).setPackage("android"),
                        R.string.various_clear_update_state_success,
                        R.string.various_clear_update_state_failed
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

    private fun setupMiuiDaemonControl() {
        val preference = findPreference<CheckBoxPreferenceEx>(MIUI_DAEMON_PREF) ?: return
        configureMaintenancePreference(preference)
        val context = context ?: return
        try {
            val info = context.packageManager.getApplicationInfo(
                MIUI_DAEMON_PACKAGE,
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
            if (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0) {
                throw PackageManager.NameNotFoundException()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            preference.isChecked = false
            preference.setUnsupported(true)
            preference.setSummary(R.string.various_disable_miui_daemon_unavailable)
            return
        }
        preference.setOnPreferenceChangeListener { _, value ->
            val disable = value == true
            if (disable == preference.isChecked) return@setOnPreferenceChangeListener false
            // Capture each operation, not the state from when this page was opened.
            val originalState = context.packageManager.getApplicationEnabledSetting(MIUI_DAEMON_PACKAGE)
            val targetState = if (disable) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            } else {
                recoverySnapshot(MIUI_DAEMON_PREF)?.original?.singleOrNull()
                    ?: AppHelper.appPrefs?.getInt(MIUI_DAEMON_STATE_SNAPSHOT, Int.MIN_VALUE)
                    ?.takeIf { it != Int.MIN_VALUE }
                    ?: return@setOnPreferenceChangeListener false
            }
            val runAction = {
                val intent = Intent(GlobalActions.SET_MIUI_DAEMON_STATE_ACTION).apply {
                    setPackage("android")
                    putExtra(GlobalActions.EXTRA_APPLICATION_STATE, targetState)
                }
                requestMaintenanceAction(
                    preference,
                    intent,
                    R.string.various_disable_miui_daemon_success,
                    R.string.various_disable_miui_daemon_failed,
                    profileEnabled = disable
                ) {
                    AppHelper.appPrefs?.edit()?.apply {
                        putBoolean(MIUI_DAEMON_PREF, disable)
                        if (disable) {
                            putInt(MIUI_DAEMON_STATE_SNAPSHOT, recoverySnapshot(MIUI_DAEMON_PREF)?.original?.singleOrNull() ?: originalState)
                        } else {
                            remove(MIUI_DAEMON_STATE_SNAPSHOT)
                        }
                    }?.let { check(it.commit()) }
                    preference.isChecked = disable
                }
            }
            if (disable) {
                val activity = activity ?: return@setOnPreferenceChangeListener false
                AlertDialog.Builder(activity)
                    .setTitle(R.string.various_disable_miui_daemon_title)
                    .setMessage(R.string.various_disable_miui_daemon_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ -> runAction() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                runAction()
            }
            false
        }
    }

    private fun setupComponentTrimProfile(
        preferenceKey: String,
        knownComponents: List<ComponentName>,
        confirmation: Int
    ) {
        val preference = findPreference<CheckBoxPreferenceEx>(preferenceKey) ?: return
        configureMaintenancePreference(preference)
        val packageManager = context?.packageManager ?: return
        val declared = HashSet<String>()
        for (packageName in knownComponents.map { it.packageName }.toSet()) {
            val info = try {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or
                        PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS
                )
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            }
            info.activities?.forEach { declared.add("$packageName/${it.name}") }
            info.services?.forEach { declared.add("$packageName/${it.name}") }
            info.receivers?.forEach { declared.add("$packageName/${it.name}") }
            info.providers?.forEach { declared.add("$packageName/${it.name}") }
        }
        val available = knownComponents.filter {
            "${it.packageName}/${it.className}" in declared
        }
        if (available.isEmpty()) {
            preference.isChecked = false
            preference.setUnsupported(true)
            preference.setSummary(R.string.various_xiaomi_trim_unavailable)
            return
        }
        preference.setOnPreferenceChangeListener { _, value ->
            val enable = value == true
            if (enable == preference.isChecked) return@setOnPreferenceChangeListener false
            val snapshot = if (enable) {
                available to IntArray(available.size) { index ->
                    packageManager.getComponentEnabledSetting(available[index])
                }
            } else {
                readComponentProfileSnapshot(preferenceKey)
                    ?: return@setOnPreferenceChangeListener false
            }
            val requestedStates = if (enable) {
                IntArray(snapshot.first.size) { PackageManager.COMPONENT_ENABLED_STATE_DISABLED }
            } else {
                snapshot.second
            }
            val runAction = {
                val intent = Intent(GlobalActions.SET_XIAOMI_COMPONENTS_ACTION).apply {
                    setPackage("android")
                    putExtra(
                        GlobalActions.EXTRA_PACKAGE_NAMES,
                        snapshot.first.map { it.packageName }.toTypedArray()
                    )
                    putExtra(
                        GlobalActions.EXTRA_COMPONENT_NAMES,
                        snapshot.first.map { it.className }.toTypedArray()
                    )
                    putExtra(GlobalActions.EXTRA_COMPONENT_STATES, requestedStates)
                }
                requestMaintenanceAction(
                    preference,
                    intent,
                    R.string.various_xiaomi_trim_success,
                    R.string.various_xiaomi_trim_failed,
                    profileEnabled = enable
                ) {
                    persistComponentProfileSnapshot(
                        preferenceKey,
                        enable,
                        snapshot.first,
                        snapshot.second
                    )
                    preference.isChecked = enable
                }
            }
            if (enable) showTrimConfirmation(confirmation, snapshot.first.size, runAction)
            else runAction()
            false
        }
    }

    private fun setupPackageTrimProfile(
        preferenceKey: String,
        knownPackages: List<String>,
        confirmation: Int
    ) {
        val preference = findPreference<CheckBoxPreferenceEx>(preferenceKey) ?: return
        configureMaintenancePreference(preference)
        val packageManager = context?.packageManager ?: return
        val installed = knownPackages.filter { packageName ->
            try {
                val info = packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_DISABLED_COMPONENTS
                )
                info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 &&
                    info.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED != 0
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        if (installed.isEmpty()) {
            preference.isChecked = false
            preference.setUnsupported(true)
            preference.setSummary(R.string.various_xiaomi_trim_unavailable)
            return
        }
        preference.setOnPreferenceChangeListener { _, value ->
            val enable = value == true
            if (enable == preference.isChecked) return@setOnPreferenceChangeListener false
            val snapshot = if (enable) {
                installed to IntArray(installed.size) { index ->
                    packageManager.getApplicationEnabledSetting(installed[index])
                }
            } else {
                readPackageProfileSnapshot(preferenceKey)
                    ?: return@setOnPreferenceChangeListener false
            }
            val requestedStates = if (enable) {
                IntArray(snapshot.first.size) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                }
            } else {
                snapshot.second
            }
            val runAction = {
                val intent = Intent(GlobalActions.SET_XIAOMI_PACKAGES_ACTION).apply {
                    setPackage("android")
                    putExtra(GlobalActions.EXTRA_PACKAGE_NAMES, snapshot.first.toTypedArray())
                    putExtra(GlobalActions.EXTRA_COMPONENT_STATES, requestedStates)
                }
                requestMaintenanceAction(
                    preference,
                    intent,
                    R.string.various_xiaomi_trim_success,
                    R.string.various_xiaomi_trim_failed,
                    profileEnabled = enable
                ) {
                    persistPackageProfileSnapshot(
                        preferenceKey,
                        enable,
                        snapshot.first,
                        snapshot.second
                    )
                    preference.isChecked = enable
                }
            }
            if (enable) showTrimConfirmation(confirmation, snapshot.first.size, runAction)
            else runAction()
            false
        }
    }

    private fun showTrimConfirmation(message: Int, count: Int, action: () -> Unit) {
        val activity = activity ?: return
        AlertDialog.Builder(activity)
            .setTitle(R.string.various_xiaomi_trim_confirm_title)
            .setMessage(getString(message, count))
            .setPositiveButton(android.R.string.ok) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun persistComponentProfileSnapshot(
        key: String,
        enabled: Boolean,
        components: List<ComponentName>,
        states: IntArray
    ) {
        AppHelper.appPrefs?.edit()?.apply {
            putBoolean(key, enabled)
            if (enabled) {
                putString(
                    key + PROFILE_PACKAGES_SUFFIX,
                    components.joinToString("\n") { it.packageName }
                )
                putString(
                    key + PROFILE_COMPONENTS_SUFFIX,
                    components.joinToString("\n") { it.className }
                )
                putString(key + PROFILE_STATES_SUFFIX, (recoverySnapshot(key)?.original ?: states).joinToString(","))
            } else {
                remove(key + PROFILE_PACKAGES_SUFFIX)
                remove(key + PROFILE_COMPONENTS_SUFFIX)
                remove(key + PROFILE_STATES_SUFFIX)
            }
        }?.let { check(it.commit()) }
    }

    private fun readComponentProfileSnapshot(
        key: String
    ): Pair<List<ComponentName>, IntArray>? {
        recoverySnapshot(key)?.let { saved ->
            return saved.targets.map { ComponentName(it.packageName, it.component ?: return null) } to saved.original
        }
        if (maintenanceJournal.contains(key)) return null
        val prefs = AppHelper.appPrefs ?: return null
        val packages = prefs.getString(key + PROFILE_PACKAGES_SUFFIX, null)
            ?.split('\n') ?: return null
        val names = prefs.getString(key + PROFILE_COMPONENTS_SUFFIX, null)
            ?.split('\n') ?: return null
        val states = readProfileStates(key) ?: return null
        if (packages.isEmpty() || packages.size != names.size || names.size != states.size) return null
        return names.indices.map { ComponentName(packages[it], names[it]) } to states
    }

    private fun persistPackageProfileSnapshot(
        key: String,
        enabled: Boolean,
        packages: List<String>,
        states: IntArray
    ) {
        AppHelper.appPrefs?.edit()?.apply {
            putBoolean(key, enabled)
            if (enabled) {
                putString(key + PROFILE_PACKAGES_SUFFIX, packages.joinToString("\n"))
                putString(key + PROFILE_STATES_SUFFIX, (recoverySnapshot(key)?.original ?: states).joinToString(","))
            } else {
                remove(key + PROFILE_PACKAGES_SUFFIX)
                remove(key + PROFILE_STATES_SUFFIX)
            }
        }?.let { check(it.commit()) }
    }

    private fun readPackageProfileSnapshot(key: String): Pair<List<String>, IntArray>? {
        recoverySnapshot(key)?.let { saved -> return saved.targets.map { it.packageName } to saved.original }
        if (maintenanceJournal.contains(key)) return null
        val packages = AppHelper.appPrefs?.getString(key + PROFILE_PACKAGES_SUFFIX, null)
            ?.split('\n') ?: return null
        val states = readProfileStates(key) ?: return null
        return if (packages.isNotEmpty() && packages.size == states.size) packages to states else null
    }

    private fun readProfileStates(key: String): IntArray? =
        parseMaintenanceStates(AppHelper.appPrefs?.getString(key + PROFILE_STATES_SUFFIX, null))

    private fun requestMaintenanceAction(
        preference: Preference,
        intent: Intent,
        successMessage: Int,
        failedMessage: Int,
        profileEnabled: Boolean? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val context = context ?: return
        val journal = maintenanceJournal
        val attempt = if (profileEnabled == null) null else ModuleHelper.guarded<MaintenanceJournal.Attempt?>(null) {
            journal.prepare(preference.key, intent, profileEnabled)
        }
        if (profileEnabled != null && attempt == null) {
            Toast.makeText(context, failedMessage, Toast.LENGTH_LONG).show()
            return
        }
        val request = intent
        val requestContext = context
        preference.isEnabled = false
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                preference.isEnabled = true
                val verified = ModuleHelper.guarded(false) {
                    if (resultCode != GlobalActions.ACTION_HANDLED || attempt?.verify() == false) return@guarded false
                    onSuccess?.invoke()
                    attempt?.finish()
                    true
                }
                if (verified) {
                    if (preference is CheckBoxPreferenceEx) configureMaintenancePreference(preference)
                    if (successMessage != 0) Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
                    return
                }
                val message = if (resultCode == GlobalActions.ACTION_UNHANDLED) {
                    R.string.various_disable_update_services_bridge_unavailable
                } else {
                    failedMessage
                }
                recoverMaintenanceFailure(requestContext, preference, request, attempt, message)
            }
        }
        try {
            ModuleHelper.sendOrderedBroadcastWithIdentity(
                context,
                intent,
                null,
                resultReceiver,
                Handler(Looper.getMainLooper()),
                GlobalActions.ACTION_UNHANDLED
            )
        } catch (t: Throwable) {
            preference.isEnabled = true
            FatalErrors.unwrapAndRethrowIfFatal(t)
            AppHelper.log("SystemMaintenance", t)
            recoverMaintenanceFailure(requestContext, preference, request, attempt, failedMessage)
        }
    }

    private fun recoverMaintenanceFailure(context: Context, preference: Preference, request: Intent,
        attempt: MaintenanceJournal.Attempt?, message: Int) {
        val complete = {
            ModuleHelper.guarded {
                if (attempt?.restored() == true) {
                    check(AppHelper.appPrefs?.edit()?.putBoolean(preference.key, false)?.commit() == true)
                    attempt.recovered()
                }
                if (preference is CheckBoxPreferenceEx) configureMaintenancePreference(preference)
            }
            preference.isEnabled = true
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        val rollback = ModuleHelper.guarded<Intent?>(null) { attempt?.rollbackIntent(request) }
        if (rollback == null) { complete(); return }
        preference.isEnabled = false
        try {
            // One bounded compensation request; an unsuccessful rollback keeps recovery on disk.
            ModuleHelper.sendOrderedBroadcastWithIdentity(context, rollback, null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent?) = ModuleHelper.guarded { complete() }
                }, Handler(Looper.getMainLooper()), GlobalActions.ACTION_UNHANDLED)
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            complete()
        }
    }

    private fun declaredUpdaterServices(): Pair<Array<String>, IntArray>? {
        val context = context ?: return null
        return try {
            val services = context.packageManager.getPackageInfo(
                UPDATER_PACKAGE,
                PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
            ).services
                ?.map { it.name }
                ?.filter { it.startsWith("$UPDATER_PACKAGE.") }
                ?.distinct()
                ?.sorted()
                .orEmpty()
            if (services.isEmpty() || services.size > 32) return null
            val states = IntArray(services.size) { index ->
                context.packageManager.getComponentEnabledSetting(
                    ComponentName(UPDATER_PACKAGE, services[index])
                )
            }
            services.toTypedArray() to states
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun setupUpdaterServiceControl() {
        val preference = findPreference<CheckBoxPreferenceEx>(UPDATE_SERVICES_PREF) ?: return
        configureMaintenancePreference(preference)
        val current = declaredUpdaterServices()
        if (current == null) {
            preference.isChecked = false
            preference.setUnsupported(true)
            preference.setSummary(R.string.various_disable_update_services_unavailable)
            return
        }
        preference.setOnPreferenceChangeListener { _, value ->
            if ((value == true) == preference.isChecked) return@setOnPreferenceChangeListener false
            if (value == true) {
                val activity = activity ?: return@setOnPreferenceChangeListener false
                AlertDialog.Builder(activity)
                    .setTitle(R.string.various_disable_update_services_title)
                    .setMessage(getString(R.string.various_disable_update_services_confirm, current.first.size))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val latest = declaredUpdaterServices()
                        if (latest == null) {
                            Toast.makeText(context, R.string.various_disable_update_services_failed, Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }
                        requestUpdaterServiceState(
                            preference,
                            true,
                            latest.first,
                            IntArray(latest.first.size) {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            },
                            latest.second
                        )
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                val snapshot = readUpdaterServiceSnapshot()
                if (snapshot == null) {
                    Toast.makeText(
                        context,
                        R.string.various_disable_update_services_failed,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    requestUpdaterServiceState(
                        preference,
                        false,
                        snapshot.first,
                        snapshot.second,
                        null
                    )
                }
            }
            false
        }
    }

    private fun readUpdaterServiceSnapshot(): Pair<Array<String>, IntArray>? {
        recoverySnapshot(UPDATE_SERVICES_PREF)?.let { saved ->
            return saved.targets.map { it.component ?: return null }.toTypedArray() to saved.original
        }
        if (maintenanceJournal.contains(UPDATE_SERVICES_PREF)) return null
        val prefs = AppHelper.appPrefs ?: return null
        val names = prefs.getString(UPDATE_SERVICE_NAMES_SNAPSHOT, null)
            ?.split('\n')
            ?.toTypedArray()
            ?: return null
        val states = parseMaintenanceStates(prefs.getString(UPDATE_SERVICE_STATES_SNAPSHOT, null))
            ?: return null
        return if (names.isNotEmpty() && names.none { it.isEmpty() } && names.size == states.size) names to states else null
    }

    private fun requestUpdaterServiceState(
        preference: CheckBoxPreferenceEx,
        enabled: Boolean,
        names: Array<String>,
        requestedStates: IntArray,
        snapshotStates: IntArray?
    ) {
        val intent = Intent(GlobalActions.SET_UPDATER_SERVICES_ACTION).apply {
            setPackage("android")
            putExtra(GlobalActions.EXTRA_UPDATER_SERVICE_NAMES, names)
            putExtra(GlobalActions.EXTRA_UPDATER_SERVICE_STATES, requestedStates)
        }
        requestMaintenanceAction(preference, intent, 0, R.string.various_disable_update_services_failed, profileEnabled = enabled) {
            AppHelper.appPrefs?.edit()?.apply {
                putBoolean(UPDATE_SERVICES_PREF, enabled)
                if (enabled && snapshotStates != null) {
                    putString(UPDATE_SERVICE_NAMES_SNAPSHOT, names.joinToString("\n"))
                    putString(UPDATE_SERVICE_STATES_SNAPSHOT, (recoverySnapshot(UPDATE_SERVICES_PREF)?.original ?: snapshotStates).joinToString(","))
                } else if (!enabled) {
                    remove(UPDATE_SERVICE_NAMES_SNAPSHOT)
                    remove(UPDATE_SERVICE_STATES_SNAPSHOT)
                }
            }?.let { check(it.commit()) }
            preference.isChecked = enabled
        }
    }
}
