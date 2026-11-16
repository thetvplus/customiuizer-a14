package tv.withaibuild.customiuizer.mods.wifiauto

import android.app.NotificationManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.os.Binder
import android.provider.Settings
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchAccess
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchCoordinator
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchHooks
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings
import tv.withaibuild.customiuizer.mods.wifiauto.WifiAutomationCoordinator.Property

/** Cold-resolved interfaces, with explicit module authorization checks even inside system_server. */
// Host-only: Feature 265 loads this in system_server, whose verified A14 interfaces are not app SDK calls.
// Missing capabilities fail closed in read/write; this class is never used for settings-process access.
@SuppressLint("BlockedPrivateApi", "SoonBlockedPrivateApi")
internal class WifiAutomationAccess(private val context: Context, private val loader: ClassLoader) : WifiAutomationCoordinator.Backend {
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }
    private val notifications by lazy { context.getSystemService(NotificationManager::class.java) }
    private val grant by lazy { NotificationManager::class.java.getDeclaredMethod("isNotificationPolicyAccessGrantedForPackage", String::class.java) }
    private val internalMode by lazy { AudioManager::class.java.getDeclaredMethod("getRingerModeInternal") }
    private val setMode by lazy { AudioManager::class.java.getDeclaredMethod("setRingerModeInternal", Int::class.javaPrimitiveType) }
    private val dndGate by lazy { ModuleHelper.guarded<WifiDndPolicyGate?>(null) { WifiDndPolicyGate(loader) } }
    private val vpnAccess = lazy { ModuleHelper.guarded<VpnAutoSwitchCoordinator.Backend?>(null) { VpnAutoSwitchAccess(context, loader) } }
    var needsVpn = false

    fun identityAllowed(): Boolean = context.packageManager.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, Helpers.modulePkg) == PackageManager.PERMISSION_GRANTED &&
        context.packageManager.checkPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, Helpers.modulePkg) == PackageManager.PERMISSION_GRANTED &&
        context.packageManager.checkPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES, Helpers.modulePkg) == PackageManager.PERMISSION_GRANTED &&
        context.packageManager.checkPermission(android.Manifest.permission.ACCESS_WIFI_STATE, Helpers.modulePkg) == PackageManager.PERMISSION_GRANTED &&
        context.getSystemService(LocationManager::class.java).isLocationEnabled

    private fun policyGranted(): Boolean = grant.invoke(notifications, Helpers.modulePkg) == true

    override fun read(property: Property): Int? = ModuleHelper.guarded<Int?>(null) { identity {
        when (property) {
            Property.DND -> if (policyGranted() && dndGate?.allows(notifications) == true) notifications.currentInterruptionFilter.takeIf { it in 1..4 } else null
            Property.RINGER -> (internalMode.invoke(audio) as Int).takeIf { it in 0..2 }
            Property.ROTATION -> Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION).takeIf { it in 0..1 }
        }
    } }

    override fun write(property: Property, value: Int): Int? = ModuleHelper.guarded<Int?>(null) { identity {
        when (property) {
            Property.DND -> {
                if (!policyGranted() || dndGate?.allows(notifications) != true) return@identity null
                // Only the manual interruption filter is changed. Never change Policy or automatic Zen rules.
                notifications.setInterruptionFilter(value)
            }
            Property.RINGER -> {
                val current = internalMode.invoke(audio) as Int
                val filter = notifications.currentInterruptionFilter
                // A14's native delegate would disable Zen on leaving silent while Zen suppresses the ringer.
                if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && current == AudioManager.RINGER_MODE_SILENT && value != current) return@identity null
                if (filter == NotificationManager.INTERRUPTION_FILTER_ALL && value == AudioManager.RINGER_MODE_SILENT && silentEnablesDnd()) return@identity null
                setMode.invoke(audio, value)
            }
            Property.ROTATION -> if (!Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, value)) return@identity null
        }
        read(property)
    } }

    private val volumePolicy by lazy {
        val manager = Class.forName("android.os.ServiceManager", false, loader).getDeclaredMethod("getService", String::class.java)
        val service = manager.invoke(null, "audio")
        val type = Class.forName("com.android.server.audio.AudioService", false, loader)
        val field = type.getDeclaredField("mVolumePolicy").apply { isAccessible = true }
        val flag = Class.forName("android.media.VolumePolicy", false, loader).getDeclaredField("doNotDisturbWhenSilent")
        service to (field to flag)
    }
    private fun silentEnablesDnd(): Boolean = ModuleHelper.guarded(true) {
        val (service, fields) = volumePolicy
        fields.second.getBoolean(fields.first.get(service))
    }

    override fun vpn(): VpnAutoSwitchCoordinator.Backend? = if (needsVpn || vpnAccess.isInitialized()) vpnAccess.value else null
    override fun vpnBusy(): Boolean = MainModule.mPrefs.getBoolean("system_vpnauto_enabled") || VpnAutoSwitchHooks.busyForWifi()
    override fun vpnTarget() = VpnAutoSwitchSettings.Target.read(MainModule.mPrefs.getAll())

    private inline fun <T> identity(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        try { return block() } finally { Binder.restoreCallingIdentity(token) }
    }
}
