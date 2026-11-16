package tv.withaibuild.customiuizer.mods.wifiauto

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Handler
import android.provider.Settings
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ContextResolver
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchHooks
import tv.withaibuild.customiuizer.utils.WifiAutomaticSettings as Config

/** Uses the existing system context handoff. No new business Hook, service or thread. */
internal object WifiAutomationHooks {
    @Volatile private var installed = false
    @Volatile private var context: Context? = null
    private lateinit var loader: ClassLoader
    private lateinit var handler: Handler
    private var runtime: Runtime? = null
    private var watching = false
    private val user = ActivityManager::class.java.getDeclaredMethod("getCurrentUser")
    private val boot = Class.forName("android.os.SystemProperties").getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
    private val lifecycle = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            handler.post { ModuleHelper.guarded { when (action) {
                Intent.ACTION_BOOT_COMPLETED, "android.intent.action.USER_SWITCHED" -> refresh()
                "android.location.MODE_CHANGED" -> { refresh(); runtime?.refreshIdentity() }
                else -> if (runtime?.stopped == true) refresh() else runtime?.statesChanged()
            } } }
        }
    }

    fun install(classLoader: ClassLoader) {
        if (installed) return
        loader = classLoader
        handler = VpnAutoSwitchHooks.backgroundHandler(loader)
        installed = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) {
                if (key != null && !key.startsWith(Config.PREFIX) && !key.startsWith("system_vpnauto_")) return
                handler.post { ModuleHelper.guarded { refresh(); runtime?.statesChanged() } }
            }
        })
        // A late first preference publication can arrive after the shared context handoff.
        ContextResolver.findContext(null)?.let { systemReady(it) }
    }

    fun systemReady(value: Context) {
        context = value
        if (installed) handler.post { ModuleHelper.guarded { refresh() } }
    }

    fun refreshFromSettings() {
        if (!installed) return
        handler.post { ModuleHelper.guarded { refresh(); runtime?.refreshIdentity(); runtime?.statesChanged() } }
    }

    private fun refresh() {
        val ctx = context ?: return
        val options = Config.read(MainModule.mPrefs)
        if (!options.active) {
            runtime?.close(user.invoke(null) == 0 && boot.invoke(null, "sys.boot_completed", false) == true)
            if (runtime?.restoring != true) runtime = null
            if (watching && runtime == null) { ModuleHelper.unregisterModuleReceiver("wifiAutomaticLifecycle", lifecycle); watching = false }
            return
        }
        if (!watching) {
            val filter = IntentFilter(Intent.ACTION_BOOT_COMPLETED).apply {
                addAction("android.intent.action.USER_SWITCHED"); addAction("android.location.MODE_CHANGED")
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION); addAction("android.media.INTERNAL_RINGER_MODE_CHANGED_ACTION")
            }
            check(ModuleHelper.registerModuleReceiver(ctx, "wifiAutomaticLifecycle", lifecycle, filter, Context.RECEIVER_NOT_EXPORTED))
            watching = true
        }
        if (user.invoke(null) != 0 || boot.invoke(null, "sys.boot_completed", false) != true) {
            runtime?.close(false); runtime = null
            return
        }
        val current = runtime
        if (current == null) {
            val created = Runtime(ctx, loader, handler, options)
            try { created.start(); runtime = created } catch (error: Throwable) { created.close(false); throw error }
        } else current.configure(options)
    }

    private class Runtime(private val context: Context, loader: ClassLoader, private val handler: Handler, private var options: Config.Options) {
        private val connectivity = context.getSystemService(ConnectivityManager::class.java)
        private val access = WifiAutomationAccess(context, loader)
        private val coordinator = WifiAutomationCoordinator(access)
        private val tracker = WifiNetworkTracker()
        private val raw = HashMap<Network, Pair<String?, String?>>()
        private var closed = false
        private var registered = false
        private var observing = false
        private var priming = false
        private var settling = true
        private val reconcile = Runnable { ModuleHelper.guarded { reconcileNetwork() } }
        private val stateUpdate = Runnable { ModuleHelper.guarded {
            if (!closed && user.invoke(null) == 0)
                coordinator.statesChanged(!settling && tracker.state().known && access.identityAllowed())
        } }
        private val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { statesChanged() }
        }
        private var callback: ConnectivityManager.NetworkCallback? = null
        val stopped get() = closed
        val restoring get() = coordinator.pendingRestoration
        private fun networkCallback() = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onAvailable(network: Network) = ModuleHelper.guarded {
                if (!closed && callback === this) { tracker.available(network); networkChanged() }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = ModuleHelper.guarded {
                if (closed || callback !== this) return@guarded
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    tracker.vpn(network); networkChanged(); statesChanged(); return@guarded
                }
                val info = caps.transportInfo as? WifiInfo
                val ssid = info?.ssid
                val bssid = info?.bssid
                val previous = raw[network]
                if (previous != null && previous.first == ssid && previous.second == bssid) return@guarded
                if (raw.size < 16 || network in raw) raw[network] = ssid to bssid
                tracker.wifi(network, Config.identity(ssid, bssid))
                networkChanged()
            }
            override fun onLost(network: Network) = ModuleHelper.guarded {
                if (!closed && callback === this) { tracker.lost(network); raw.remove(network); networkChanged(); statesChanged() }
            }
        }

        fun start() {
            access.needsVpn = options.vpn
            try { register(); networkChanged() }
            catch (error: Throwable) {
                closed = true
                handler.removeCallbacks(reconcile); handler.removeCallbacks(stateUpdate)
                ModuleHelper.guarded { unregister() }
                raw.clear(); tracker.beginSnapshot()
                throw error
            }
        }

        private fun register() {
            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).apply {
                    if (options.vpn) {
                        removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    }
                }.build()
            val value = networkCallback()
            callback = value
            connectivity.registerNetworkCallback(request, value, handler)
            registered = true
            check(ModuleHelper.replaceModuleRegistration("wifiAutomaticNetwork") { connectivity.unregisterNetworkCallback(value) })
            observing = options.audio || options.rotation
            if (observing) check(ModuleHelper.replaceModuleRegistration("wifiAutomaticProperties") { context.contentResolver.unregisterContentObserver(observer) })
            if (options.audio) context.contentResolver.registerContentObserver(Settings.Global.getUriFor("zen_mode"), false, observer)
            if (options.rotation) context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
            ModuleHelper.guarded { primeNetworks() }
        }

        fun configure(updated: Config.Options) {
            if (closed) {
                closed = false; settling = true; options = updated
                start()
                return
            }
            if (updated == options) return
            coordinator.network(null, false)
            val demandChanged = options.audio != updated.audio || options.rotation != updated.rotation || options.vpn != updated.vpn
            options = updated
            access.needsVpn = updated.vpn
            if (demandChanged) {
                unregister(); tracker.beginSnapshot(); raw.clear(); start()
                return
            }
            networkChanged()
        }

        fun networkChanged() {
            if (closed || priming) return
            settling = true
            handler.removeCallbacks(reconcile)
            handler.postDelayed(reconcile, 500L) // One finite coalesced settle window, including AP handoffs.
        }
        fun statesChanged() {
            if (closed || priming) return
            handler.removeCallbacks(stateUpdate)
            handler.post(stateUpdate)
        }
        fun refreshIdentity() {
            if (closed) return
            raw.clear()
            primeNetworks()
            networkChanged()
        }
        @Suppress("DEPRECATION")
        private fun primeNetworks() {
            val value = callback ?: return
            if (priming) return
            priming = true
            tracker.beginSnapshot()
            raw.clear()
            try {
                var complete = true
                for (network in connectivity.allNetworks) {
                    val caps = connectivity.getNetworkCapabilities(network)
                    if (caps == null) { complete = false; continue }
                    if ((caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) ||
                        (options.vpn && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))) {
                        value.onCapabilitiesChanged(network, caps)
                    }
                }
                if (complete) {
                    tracker.initialReadComplete()
                } else tracker.invalidateSnapshot()
            } finally { priming = false }
        }
        private fun reconcileNetwork() {
            if (closed || user.invoke(null) != 0) return
            // Retry a failed initial read only on this finite native-event handoff, never by self-scheduling.
            if (!tracker.snapshotReady) ModuleHelper.guarded { primeNetworks() }
            val state = tracker.state()
            // Redacted identity suspends commands; a confirmed empty underlying Wi-Fi set is a real exit.
            val known = state.known && (state.identity == null || access.identityAllowed())
            settling = false
            coordinator.network(if (known) state.identity?.let(options::match) else null, known)
        }
        private fun unregister() {
            val value = callback
            callback = null
            try {
                if (registered && value != null) {
                    registered = false
                    ModuleHelper.removeModuleRegistration("wifiAutomaticNetwork")
                }
            } finally {
                if (observing) {
                    observing = false
                    ModuleHelper.removeModuleRegistration("wifiAutomaticProperties")
                }
            }
        }
        fun close(restore: Boolean) {
            if (!closed) {
                closed = true
                handler.removeCallbacks(reconcile); handler.removeCallbacks(stateUpdate)
                ModuleHelper.guarded { unregister() }
                raw.clear(); tracker.beginSnapshot()
            }
            ModuleHelper.guarded { coordinator.close(restore && user.invoke(null) == 0) }
        }
    }
}
