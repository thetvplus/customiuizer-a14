package tv.withaibuild.customiuizer.mods.vpn

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.SystemClock
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ContextResolver
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings as Settings
import java.util.concurrent.atomic.AtomicBoolean

/** Installs only when enabled at boot. Native callbacks publish scalars to a shared system handler. */
internal object VpnAutoSwitchHooks {
    @Volatile private var options: Settings.Options? = null
    @Volatile private var runtime: Runtime? = null
    @Volatile var status = "UNAVAILABLE"
        private set

    fun install(loader: ClassLoader) {
        val activity = Class.forName("com.android.server.wm.ActivityRecord", false, loader)
        val state = Class.forName("com.android.server.wm.ActivityRecord\$State", false, loader)
        val task = Class.forName("com.android.server.wm.Task", false, loader)
        val vpn = Class.forName("com.android.server.connectivity.Vpn", false, loader)
        val resumed = state.enumConstants.single { (it as Enum<*>).name == "RESUMED" }
        val pkgField = VpnAutoSwitchAccess.field(activity, "packageName")
        val userField = VpnAutoSwitchAccess.field(activity, "mUserId")
        val taskId = VpnAutoSwitchAccess.field(task, "mTaskId")
        val taskUser = VpnAutoSwitchAccess.field(task, "mUserId")
        val vpnUser = VpnAutoSwitchAccess.field(vpn, "mUserId")
        val getTask = activity.getDeclaredMethod("getTask").apply { isAccessible = true }
        val resumeMethod = activity.getDeclaredMethod("setState", state, String::class.java).apply { isAccessible = true }
        val removeMethod = task.getDeclaredMethod("removedFromRecents").apply { isAccessible = true }
        val updateMethod = vpn.getDeclaredMethod("updateState", Class.forName("android.net.NetworkInfo\$DetailedState", false, loader), String::class.java)
            .apply { isAccessible = true }
        val handler = backgroundHandler(loader)
        val installed = ArrayList<CustomMethodUnhooker>()
        try {
            fun hook(method: java.lang.reflect.Method, callback: MethodHook) {
                installed.add(checkNotNull(ModuleHelper.hookMethod(method, callback)))
            }
            hook(resumeMethod, object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    val config = options ?: return@guarded
                    if (!config.configured || callback.getThrowable() != null || callback.getArg(0) !== resumed) return@guarded
                    val record = callback.getThisObject() ?: return@guarded
                    if (userField.getInt(record) != 0) return@guarded
                    val pkg = pkgField.get(record) as String
                    if (pkg !in config.apps) return@guarded
                    val nativeTask = getTask.invoke(record) ?: return@guarded
                    val id = taskId.getInt(nativeTask)
                    handler.post { ModuleHelper.guarded {
                        val latest = options ?: return@guarded
                        if (!latest.acceptsResumeFrom(config, pkg)) return@guarded
                        current(latest, loader)?.let {
                            if (it.policy.resumed(pkg, 0, id, nativeTask)) it.check()
                        }
                    } }
                }
            })
            hook(removeMethod, object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    val config = options ?: return@guarded
                    if (!config.configured || callback.getThrowable() != null) return@guarded
                    val removed = callback.getThisObject() ?: return@guarded
                    if (taskUser.getInt(removed) != 0) return@guarded
                    val id = taskId.getInt(removed)
                    handler.post { ModuleHelper.guarded {
                        runtime?.let {
                            if (it.policy.removed(id, 0, SystemClock.elapsedRealtime(), removed)) it.check()
                        }
                    } }
                }
            })
            hook(updateMethod, object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    val value = runtime ?: return@guarded
                    if (callback.getThrowable() == null && vpnUser.getInt(callback.getThisObject()) == 0) value.signal()
                }
            })
        } catch (failure: Throwable) {
            installed.asReversed().forEach { ModuleHelper.guarded { it.unhook() } }
            throw failure
        }
        options = Settings.Options.read(MainModule.mPrefs)
        PreferenceObserverRegistry.observePreferenceChange(object : PreferenceObserverRegistry.PreferenceObserver {
            override fun onChange(key: String?) {
                if (key != null && !key.startsWith(Settings.PREFIX)) return
                handler.post { ModuleHelper.guarded {
                    // Read at delivery time so reordered listener threads cannot restore an older snapshot.
                    val updated = Settings.Options.read(MainModule.mPrefs)
                    if (updated == options) return@guarded
                    options = updated
                    runtime?.configure(updated)
                    if (runtime == null) status = if (updated.enabled) "READY" else "DISABLED"
                } }
            }
        })
        status = "READY"
    }

    private fun current(config: Settings.Options, loader: ClassLoader): Runtime? {
        if (options !== config || !config.configured) return null
        runtime?.let { return it }
        val context = ContextResolver.findContext(null) ?: return null
        return Runtime(context, loader, config).also { it.start(); runtime = it }
    }

    internal fun backgroundHandler(loader: ClassLoader): Handler = XposedHelpers.callStaticMethod(
        XposedHelpers.findClass("com.android.internal.os.BackgroundThread", loader), "getHandler") as Handler

    private class Runtime(private val context: Context, loader: ClassLoader, config: Settings.Options) {
        private val access = VpnAutoSwitchAccess(context, loader)
        private val coordinator = VpnAutoSwitchCoordinator(config, access)
        val policy: VpnAutoSwitchPolicy get() = coordinator.policy
        private val handler = backgroundHandler(loader)
        private val currentUser = ActivityManager::class.java.getDeclaredMethod("getCurrentUser")
        @Volatile private var closed = false
        private val queued = AtomicBoolean()
        private val reconcile = Runnable { queued.set(false); ModuleHelper.guarded { check() } }
        private val timer = Runnable { ModuleHelper.guarded { check() } }
        private val userSwitch = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // A different user's session must never inherit primary-user app tracking.
                if (intent.action == "android.intent.action.USER_SWITCHED") handler.post { ModuleHelper.guarded {
                    coordinator.abandon()
                    close()
                    if (runtime === this@Runtime) runtime = null
                } }
            }
        }

        fun start() {
            check(ModuleHelper.registerModuleReceiver(context, "vpnAutoUserSwitch", userSwitch,
                IntentFilter("android.intent.action.USER_SWITCHED"), Context.RECEIVER_NOT_EXPORTED))
        }

        fun signal() {
            if (!closed && queued.compareAndSet(false, true)) handler.post(reconcile)
        }

        fun configure(updated: Settings.Options) {
            if (closed) return
            if (currentUser.invoke(null) != 0) {
                coordinator.abandon()
                close()
                if (runtime === this) runtime = null
                return
            }
            coordinator.configure(updated, SystemClock.elapsedRealtime())
            check()
        }

        fun check() {
            if (closed) return
            handler.removeCallbacks(timer)
            if (currentUser.invoke(null) != 0) {
                coordinator.abandon()
                close()
                if (runtime === this) runtime = null
                status = "UNAVAILABLE"
                return
            }
            val startedAt = SystemClock.elapsedRealtime()
            val next = coordinator.reconcile(startedAt)
            status = coordinator.status.name
            if (!coordinator.active) {
                close()
                if (runtime === this) runtime = null
                return
            }
            next?.let { handler.postDelayed(timer, (it - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)) }
        }

        fun close() {
            if (closed) return
            closed = true
            handler.removeCallbacks(reconcile)
            handler.removeCallbacks(timer)
            coordinator.abandon()
            ModuleHelper.unregisterModuleReceiver("vpnAutoUserSwitch", userSwitch)
        }
    }
}
