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
                if (pkg !in options.apps) return@guarded
                val owner = if (options.tracksTasks) getTask.invoke(record) ?: return@guarded else null
                val id = if (owner != null) taskId.getInt(owner) else -1
                runtime.handler.post { ModuleHelper.guarded {
                    if (runtime.options !== options) return@guarded
                    if (owner != null) runtime.resumeTask(id, owner) else runtime.setIfNeeded(desired)
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
                    runtime.handler.post { ModuleHelper.guarded {
                        if (runtime.options === options) runtime.removeTask(id, owner)
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
                    if (updated == runtime.options) return@guarded
                    runtime.options = updated
                    runtime.stopTracking()
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

        fun removeTask(id: Int, owner: Any) {
            val active = tracking ?: return
            try {
                active.policy.removed(id, owner)
            } finally {
                if (active.policy.idle) stopTracking()
            }
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
            active.context.unregisterReceiver(active)
        }

        private inner class Tracking(val context: Context) : BroadcastReceiver() {
            val policy = LocationAutomation(::setIfNeeded)

            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (tracking !== this) return@guarded
                when (intent.action) {
                    USER_SWITCHED -> stopTracking()
                    LocationManager.MODE_CHANGED_ACTION -> {
                        if (!intent.getBooleanExtra(LocationManager.EXTRA_LOCATION_ENABLED, true)) {
                            policy.locationTurnedOff()
                        }
                    }
                }
            }
        }
    }

    private const val USER_SWITCHED = "android.intent.action.USER_SWITCHED"
}
