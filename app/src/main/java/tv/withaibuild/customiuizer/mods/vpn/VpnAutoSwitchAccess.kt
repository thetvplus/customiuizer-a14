package tv.withaibuild.customiuizer.mods.vpn

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.NetworkInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.LinkProperties
import android.os.Binder
import android.util.SparseArray
import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings as Settings
import java.lang.reflect.Field
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper

/** Resolved once per system_server loader; all calls happen on the native background handler. */
internal class VpnAutoSwitchAccess(private val context: Context, loader: ClassLoader) : VpnAutoSwitchCoordinator.Backend {
    private val managerClass = Class.forName("com.android.server.VpnManagerService", false, loader)
    private val vpnClass = Class.forName("com.android.server.connectivity.Vpn", false, loader)
    private val profileClass = Class.forName("com.android.internal.net.VpnProfile", false, loader)
    private val configClass = Class.forName("com.android.internal.net.VpnConfig", false, loader)
    private val storeClass = Class.forName("com.android.server.connectivity.VpnProfileStore", false, loader)
    private val serviceManager = Class.forName("android.os.ServiceManager", false, loader)
        .getDeclaredMethod("getService", String::class.java)
    private val vpns = field(managerClass, "mVpns")
    private val store = field(managerClass, "mVpnProfileStore")
    private val managerLockdown = field(managerClass, "mLockdownEnabled")
    private val owner = field(vpnClass, "mPackage")
    private val config = field(vpnClass, "mConfig")
    private val connection = field(vpnClass, "mConnection")
    private val runner = field(vpnClass, "mVpnRunner")
    private val alwaysOn = field(vpnClass, "mAlwaysOn")
    private val lockdown = field(vpnClass, "mLockdown")
    private val networkInfo = field(vpnClass, "mNetworkInfo")
    private val configUser = field(configClass, "user")
    private val decode = profileClass.getDeclaredMethod("decode", String::class.java, ByteArray::class.java)
    private val profileName = field(profileClass, "name")
    private val profileType = field(profileClass, "type")
    private val storeGet = storeClass.getDeclaredMethod("get", String::class.java)
    private val storeList = storeClass.getDeclaredMethod("list", String::class.java)
    private val startLegacy = vpnClass.getDeclaredMethod("startLegacyVpn", profileClass, Network::class.java, LinkProperties::class.java)
    private val prepare = vpnClass.getDeclaredMethod("prepare", String::class.java, String::class.java, Int::class.javaPrimitiveType)

    override fun snapshot(): VpnAutoSwitchPolicy.Snapshot = ModuleHelper.guarded(VpnAutoSwitchPolicy.Snapshot(false)) {
        withVpn { manager, vpn -> snapshotOf(manager, vpn) } ?: VpnAutoSwitchPolicy.Snapshot(false)
    }

    private fun snapshotOf(manager: Any, vpn: Any): VpnAutoSwitchPolicy.Snapshot {
        val pkg = owner.get(vpn) as String
        val token = runner.get(vpn) ?: connection.get(vpn)
        val target = if (pkg == Settings.LEGACY) Settings.Target("system", config.get(vpn)?.let { configUser.get(it) as? String } ?: "")
            else Settings.Target("app", pkg)
        return VpnAutoSwitchPolicy.Snapshot(true, managerLockdown.getBoolean(manager) || alwaysOn.getBoolean(vpn) || lockdown.getBoolean(vpn),
            token?.let { VpnAutoSwitchPolicy.Session(target, it) }, (networkInfo.get(vpn) as NetworkInfo).isConnected)
    }

    override fun stop(expected: VpnAutoSwitchPolicy.Session): Boolean = ModuleHelper.guarded(false) { identity {
        withVpn { manager, vpn ->
            val current = snapshotOf(manager, vpn)
            if (current.protected || !expected.same(current.session)) false
            else prepare.invoke(vpn, expected.target.let { if (it.kind == "system") Settings.LEGACY else it.id }, Settings.LEGACY, 1) == true
        } ?: false
    } }

    override fun start(target: Settings.Target): Boolean = ModuleHelper.guarded(false) { identity {
        if (!target.valid) return@identity false
        if (target.kind == "system") {
            val manager = serviceManager.invoke(null, "vpn_management")?.takeIf(managerClass::isInstance) ?: return@identity false
            // Store I/O and network Binder calls must finish before taking native VPN locks.
            val profile = loadProfile(manager, target.id) ?: return@identity false
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val network = connectivity.activeNetwork ?: return@identity false
            val egress = connectivity.getLinkProperties(network) ?: return@identity false
            withVpn { manager, vpn ->
                val state = snapshotOf(manager, vpn)
                if (state.protected || state.session != null) false
                else {
                    startLegacy.invoke(vpn, profile, null, egress)
                    true
                }
            } ?: false
        } else {
            val state = snapshot()
            if (!state.known || state.protected || state.session != null) return@identity false
            val pm = context.packageManager
            val uid = pm.getApplicationInfo(target.id, 0).uid
            if (uid / 100000 != 0 || context.getSystemService(AppOpsManager::class.java)
                    .unsafeCheckOpNoThrow("android:activate_vpn", uid, target.id) != AppOpsManager.MODE_ALLOWED) return@identity false
            val intent = Intent("${target.id}.action.START_CLASH").setPackage(target.id).addCategory(Intent.CATEGORY_DEFAULT)
            val activity = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            if (activity != null && activity.exported && activity.enabled && activity.packageName == target.id) {
                intent.component = ComponentName(activity.packageName, activity.name)
                val latest = snapshot()
                if (!latest.known || latest.protected || latest.session != null) return@identity false
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                true
            } else {
                val services = pm.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE).setPackage(target.id), PackageManager.GET_META_DATA)
                    .mapNotNull { it.serviceInfo }.filter { it.enabled && it.permission == "android.permission.BIND_VPN_SERVICE" }
                val service = services.singleOrNull() ?: return@identity false
                if (service.metaData?.getBoolean(VpnService.SERVICE_META_DATA_SUPPORTS_ALWAYS_ON, true) == false) return@identity false
                val latest = snapshot()
                if (!latest.known || latest.protected || latest.session != null) return@identity false
                context.startService(Intent(VpnService.SERVICE_INTERFACE).setComponent(ComponentName(target.id, service.name))) != null
            }
        }
    } }

    /** Only profile identifiers and labels cross IPC. Credentials remain in the system store. */
    fun profiles(): List<Pair<String, String>> = identity {
        val manager = serviceManager.invoke(null, "vpn_management")?.takeIf(managerClass::isInstance) ?: return@identity emptyList()
        val keys = storeList.invoke(store.get(manager), "VPN_") as? Array<*> ?: return@identity emptyList()
        keys.take(128).mapNotNull { raw ->
            val key = raw as? String ?: return@mapNotNull null
            val profile = loadProfile(manager, key) ?: return@mapNotNull null
            key to (profileName.get(profile) as String).take(120)
        }
    }

    private fun loadProfile(manager: Any, key: String): Any? {
        if (key.isBlank() || key.length > 256) return null
        val bytes = storeGet.invoke(store.get(manager), "VPN_$key") as? ByteArray ?: return null
        val profile = decode.invoke(null, key, bytes) ?: return null
        // Android 14's supported system profiles are IKEv2. Do not revive retired protocols.
        return profile.takeIf { profileType.getInt(it) in 6..9 }
    }

    private fun <T> withVpn(block: (Any, Any) -> T): T? {
        val manager = serviceManager.invoke(null, "vpn_management")?.takeIf(managerClass::isInstance) ?: return null
        val array = vpns.get(manager) as SparseArray<*>
        return synchronized(array) {
            val vpn = array.get(0) ?: return@synchronized null
            synchronized(vpn) { block(manager, vpn) }
        }
    }

    companion object {
        fun field(type: Class<*>, name: String): Field = type.getDeclaredField(name).apply { isAccessible = true }
        inline fun <T> identity(block: () -> T): T {
            val token = Binder.clearCallingIdentity()
            try { return block() } finally { Binder.restoreCallingIdentity(token) }
        }
    }
}
