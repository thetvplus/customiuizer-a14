package tv.withaibuild.customiuizer.mods.locationauto

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Handler
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.appbehavior.AppWindowBehaviorHooks.field
import tv.withaibuild.customiuizer.mods.appbehavior.AppWindowBehaviorHooks.method
import tv.withaibuild.customiuizer.mods.utils.ContextResolver
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchHooks.backgroundHandler

internal object LocationAutomationHooks {
    fun install(loader: ClassLoader) {
        val activity = Class.forName("com.android.server.wm.ActivityRecord", false, loader)
        val state = Class.forName("com.android.server.wm.ActivityRecord\$State", false, loader)
        val task = Class.forName("com.android.server.wm.Task", false, loader)
        val resumed = checkNotNull(state.enumConstants).single { (it as Enum<*>).name == "RESUMED" }
        val activityUser = field(activity, "mUserId")
        val packageName = field(activity, "packageName")
        val getTask = method(activity, "getTask")
        val getBaseIntent = method(task, "getBaseIntent")
        val taskId = field(task, "mTaskId")
        val taskUser = field(task, "mUserId")
        val setState = method(activity, "setState", state, String::class.java)
        val removedFromRecents = method(task, "removedFromRecents")
        val runtime = Runtime(backgroundHandler(loader))
        val resumeHandle = checkNotNull(ModuleHelper.hookMethod(setState, object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                val options = runtime.options ?: return@guarded
                val desired = options.desired ?: return@guarded
                if (callback.getArg(0) !== resumed || callback.getThrowable() != null) return@guarded
                val record = callback.getThisObject() ?: return@guarded
                if (activityUser.getInt(record) != 0) return@guarded
                val pkg = packageName.get(record) as? String ?: return@guarded
                val matching = pkg in options.apps
                if (!matching && !options.delaysBlockedClose) return@guarded
                val owner = if (options.tracksTasks) getTask.invoke(record) ?: return@guarded else null
                val id = if (owner != null) taskId.getInt(owner) else -1
                runtime.handler.post { ModuleHelper.guarded {
                    if (runtime.options !== options) return@guarded
                    when {
                        !matching -> runtime.stopTracking()
                        options.delaysBlockedClose -> runtime.deferBlockedClose(options.closeDelaySeconds)
                        owner != null -> runtime.resumeTask(id, owner)
                        else -> runtime.setIfNeeded(desired)
                    }
                } }
            }
        }))
        try {
            checkNotNull(ModuleHelper.hookMethod(removedFromRecents, object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    val options = runtime.options ?: return@guarded
                    if (!options.tracksTasks || callback.getThrowable() != null) return@guarded
                    val owner = callback.getThisObject() ?: return@guarded
                    if (taskUser.getInt(owner) != 0) return@guarded
                    val id = taskId.getInt(owner)
                    val basePackage = (getBaseIntent.invoke(owner) as? Intent)?.component?.packageName
                    runtime.handler.post { ModuleHelper.guarded {
                        if (runtime.options === options) runtime.removeTask(id, owner, basePackage, options)
                    } }
                }
            }))
        } catch (failure: Throwable) {
            ModuleHelper.guarded { resumeHandle.unhook() }
            throw failure
        }
        runtime.options = LocationAutomationOptions.read(MainModule.mPrefs)
        PreferenceObserverRegistry.observePreferenceChange(object : PreferenceObserverRegistry.PreferenceObserver {
            override fun onChange(key: String?) {
                if (key != null && !key.startsWith(LocationAutomationOptions.PREFIX)) return
                runtime.handler.post { ModuleHelper.guarded {
                    val updated = LocationAutomationOptions.read(MainModule.mPrefs)
                    val optionsBefore = runtime.options ?: return@guarded
                    if (updated == optionsBefore) return@guarded
                    runtime.options = updated
                    if (!updated.tracksTasks || updated.apps != optionsBefore.apps) {
                        runtime.stopTracking()
                        ContextResolver.findContext(null)?.let(runtime::clearOwnership)
                    }
                } }
            }
        })
    }

    private class Runtime(val handler: Handler) {
        @Volatile var options: LocationAutomationOptions? = null
        private val currentUser = method(ActivityManager::class.java, "getCurrentUser")
        private val setLocation = method(LocationManager::class.java, "setLocationEnabledForUser",
            Boolean::class.javaPrimitiveType!!, UserHandle::class.java)
        private var tracking: Tracking? = null
        private var claimedRevision: String? = null

        private fun isPrimaryUser() = currentUser.invoke(null) == 0

        fun setIfNeeded(desired: Boolean): Boolean {
            if (!isPrimaryUser()) return false
            val context = ContextResolver.findContext(null) ?: return false
            val location = context.getSystemService(LocationManager::class.java) ?: return false
            if (location.isLocationEnabled == desired) return false
            setLocation.invoke(location, desired, Process.myUserHandle())
            return location.isLocationEnabled == desired
        }

        fun resumeTask(id: Int, owner: Any) {
            if (id < 0 || !isPrimaryUser()) return
            val active = tracking ?: startTracking() ?: return
            active.policy.resumed(id, owner)
        }

        fun removeTask(id: Int, owner: Any, basePackage: String?, options: LocationAutomationOptions) {
            if (!isPrimaryUser()) return
            tracking?.let { active ->
                active.lastRemovedId = id
                val delay = if (claimedRevision == null) 0 else options.closeDelaySeconds
                if (active.policy.removed(id, owner, delay) || !active.policy.idle) return
            }
            if (basePackage !in options.apps) return
            val context = ContextResolver.findContext(null) ?: return
            val revision = ownedRevision(context) ?: return
            if (revision == claimedRevision) return
            val active = tracking ?: startTracking() ?: return
            active.lastRemovedId = id
            active.policy.recoveredTaskRemoved()
        }

        @Suppress("DEPRECATION") // Android 14's SettingsProvider owns this persisted row.
        private fun locationRevision(context: Context): String? {
            val uri = Settings.Secure.getUriFor(Settings.Secure.LOCATION_MODE)
            return context.contentResolver.query(uri, arrayOf("_id", "value"), null, null, null)?.use {
                if (!it.moveToFirst() || (it.getString(1)?.toIntOrNull() ?: 0) <= 0) return@use null
                it.getString(0)?.takeIf { id -> id.toLongOrNull() != null }
            }
        }

        private fun ownedRevision(context: Context): String? {
            val recorded = Settings.Secure.getString(context.contentResolver, OWNER_REVISION) ?: return null
            if (recorded == locationRevision(context)) return recorded
            clearOwnership(context)
            return null
        }

        fun clearOwnership(context: Context) {
            claimedRevision = null
            if (Settings.Secure.getString(context.contentResolver, OWNER_REVISION) != null) {
                Settings.Secure.putString(context.contentResolver, OWNER_REVISION, null)
            }
        }

        private fun acquire(context: Context): Boolean {
            if (!isPrimaryUser()) return false
            if (ownedRevision(context) != null) return true
            if (!setIfNeeded(true)) return false
            val revision = locationRevision(context) ?: return false
            if (!Settings.Secure.putString(context.contentResolver, OWNER_REVISION, revision)) return false
            claimedRevision = revision
            return true
        }

        @Suppress("DEPRECATION") // system_server can read the primary user's native recent tasks.
        private fun release(context: Context, removedId: Int) {
            val rules = options?.takeIf { it.tracksTasks } ?: return
            if (!isPrimaryUser()) return
            val revision = ownedRevision(context) ?: return
            val am = context.getSystemService(ActivityManager::class.java) ?: return
            val recent = am.getRecentTasks(MAX_RECENT_TASKS, 0)
            if (hasRemainingLocationTasks(recent, removedId, rules.apps)) return
            if (!isPrimaryUser() || ownedRevision(context) != revision) return
            if (setIfNeeded(false)) clearOwnership(context)
        }

        fun deferBlockedClose(delaySeconds: Int) {
            if (!isPrimaryUser()) return
            (tracking ?: startTracking())?.policy?.requestClose(delaySeconds)
        }

        private fun startTracking(): Tracking? {
            val context = ContextResolver.findContext(null) ?: return null
            val active = Tracking(context)
            context.registerReceiver(active, IntentFilter().apply {
                addAction(LocationManager.MODE_CHANGED_ACTION)
                addAction(USER_SWITCHED)
            }, null, handler, Context.RECEIVER_NOT_EXPORTED)
            tracking = active
            return active
        }

        fun stopTracking() {
            val active = tracking ?: return
            tracking = null
            active.policy.cancelClose()
            active.context.unregisterReceiver(active)
        }

        private inner class Tracking(val context: Context) : BroadcastReceiver() {
            var lastRemovedId = -1
            val policy = LocationAutomation(handler, ::setIfNeeded,
                acquire = { acquire(context) }, release = { release(context, lastRemovedId) }) {
                if (tracking === this) stopTracking()
            }

            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (tracking !== this) return@guarded
                when (intent.action) {
                    USER_SWITCHED -> {
                        claimedRevision = null
                        stopTracking()
                    }
                    LocationManager.MODE_CHANGED_ACTION -> {
                        if (!intent.getBooleanExtra(LocationManager.EXTRA_LOCATION_ENABLED, true)) {
                            if (ownedRevision(context) == null) policy.locationTurnedOff()
                        }
                    }
                }
            }
        }
    }

    private const val USER_SWITCHED = "android.intent.action.USER_SWITCHED"
    private const val OWNER_REVISION = "customiuizer_location_owner_revision"
}

private const val MAX_RECENT_TASKS = 128

internal fun hasRemainingLocationTasks(tasks: List<ActivityManager.RecentTaskInfo>, removedId: Int, apps: Set<String>): Boolean =
    tasks.size >= MAX_RECENT_TASKS || tasks.any {
        it.persistentId != removedId &&
            (it.baseIntent.component?.packageName in apps || it.topActivity?.packageName in apps)
    }
