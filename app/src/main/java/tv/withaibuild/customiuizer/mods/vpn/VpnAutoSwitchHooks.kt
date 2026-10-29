package tv.withaibuild.customiuizer.mods.vpn

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.SystemClock
import io.github.libxposed.api.XposedInterface
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
        val process = Class.forName("com.android.server.am.ProcessRecord", false, loader)
        val ams = Class.forName("com.android.server.am.ActivityManagerService", false, loader)
        val vpn = Class.forName("com.android.server.connectivity.Vpn", false, loader)
        val resumed = state.enumConstants.single { (it as Enum<*>).name == "RESUMED" }
        val pkgField = VpnAutoSwitchAccess.field(activity, "packageName")
        val userField = VpnAutoSwitchAccess.field(activity, "mUserId")
        val appField = VpnAutoSwitchAccess.field(activity, "app")
        val taskId = VpnAutoSwitchAccess.field(task, "mTaskId")
        val taskUser = VpnAutoSwitchAccess.field(task, "mUserId")
        val info = VpnAutoSwitchAccess.field(process, "info")
        val processName = VpnAutoSwitchAccess.field(process, "processName")
        val processOwner = VpnAutoSwitchAccess.field(process, "mWindowProcessController")
        val vpnUser = VpnAutoSwitchAccess.field(vpn, "mUserId")
        val getTask = activity.getDeclaredMethod("getTask").apply { isAccessible = true }
        val resumeMethod = activity.getDeclaredMethod("setState", state, String::class.java).apply { isAccessible = true }
        val removeMethod = task.getDeclaredMethod("removedFromRecents").apply { isAccessible = true }
        val deathMethod = ams.declaredMethods.single { it.name == "handleAppDiedLocked" && it.parameterTypes.firstOrNull() == process }
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
                    val owner = appField.get(record) ?: return@guarded
                    handler.post { ModuleHelper.guarded {
                        current(config, loader)?.let {
                            if (it.policy.resumed(pkg, 0, id, owner)) it.check()
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
                        if (options === config) runtime?.let {
                            if (it.policy.removed(id, 0, SystemClock.elapsedRealtime())) it.check()
                        }
                    } }
                }
            })
            hook(deathMethod, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val config = options
                    var pkg: String? = null
                    var owner: Any? = null
                    ModuleHelper.guarded {
                        if (config?.configured == true) {
                            val record = chain.getArg(0) ?: return@guarded
                            val app = info.get(record) as ApplicationInfo
                            if (app.uid / 100000 == 0 && app.packageName in config.apps && processName.get(record) == app.packageName) {
                                pkg = app.packageName
                                owner = processOwner.get(record)
                            }
                        }
                    }
                    val result = chain.proceed()
                    val deadPackage = pkg
                    val deadOwner = owner
                    if (deadPackage != null && deadOwner != null) handler.post { ModuleHelper.guarded {
                        if (options === config) runtime?.let {
                            if (it.policy.died(deadPackage, deadOwner, SystemClock.elapsedRealtime())) it.check()
                        }
                    } }
                    return result
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
                val updated = Settings.Options.read(MainModule.mPrefs)
                handler.post { ModuleHelper.guarded {
                    if (updated == options) return@guarded
                    val current = runtime
                    val retained = current != null && updated.configured && current.policy.options.target == updated.target
                    if (retained) current!!.policy.update(updated, SystemClock.elapsedRealtime())
                    else { current?.close(); runtime = null }
                    options = updated
                    status = if (updated.enabled) "READY" else "DISABLED"
                    if (retained) current!!.check()
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
        val policy = VpnAutoSwitchPolicy(config)
        private val access = VpnAutoSwitchAccess(context, loader)
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
                    policy.relinquish()
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

        fun check() {
            if (closed) return
            handler.removeCallbacks(timer)
            if (currentUser.invoke(null) != 0) {
                policy.relinquish()
                close()
                if (runtime === this) runtime = null
                status = "UNAVAILABLE"
                return
            }
            val snapshot = access.snapshot()
            when (policy.evaluate(snapshot, SystemClock.elapsedRealtime())) {
                VpnAutoSwitchPolicy.Action.START -> {
                    val started = ModuleHelper.guarded(false) { access.start(policy.options.target) }
                    if (!started) policy.startFailed() else signal()
                }
                VpnAutoSwitchPolicy.Action.STOP -> {
                    val session = policy.owned
                    policy.relinquish()
                    if (session != null) access.stop(session)
                }
                VpnAutoSwitchPolicy.Action.NONE -> Unit
            }
            status = policy.status.name
            // Unknown backend state must not turn an expired deadline into a 1 ms retry loop.
            policy.nextDelay(snapshot, SystemClock.elapsedRealtime())?.let { handler.postDelayed(timer, it) }
        }

        fun close() {
            if (closed) return
            closed = true
            handler.removeCallbacks(reconcile)
            handler.removeCallbacks(timer)
            val session = policy.owned
            policy.relinquish()
            if (session != null && policy.options.autoClose) ModuleHelper.guarded { access.stop(session) }
            ModuleHelper.unregisterModuleReceiver("vpnAutoUserSwitch", userSwitch)
        }
    }
}
