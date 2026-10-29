package tv.withaibuild.customiuizer.mods.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings

/** Read-only extension of the existing signed settings transport, available while disabled. */
internal object VpnAutoSwitchBridge {
    fun install(context: Context, loader: ClassLoader) {
        ModuleHelper.registerModuleReceiver(context, "vpnAutoOptions", object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != VpnAutoSwitchSettings.OPTIONS || getSentFromUid() / 100000 != 0 ||
                    !ModuleHelper.isTrustedBroadcast(this, Helpers.modulePkg)) return
                val pending = goAsync()
                VpnAutoSwitchHooks.backgroundHandler(loader).post {
                    try {
                        ModuleHelper.guarded {
                            val profiles = VpnAutoSwitchAccess(context, loader).profiles()
                            val rules = VpnAutoSwitchSettings.Options.read(MainModule.mPrefs)
                            pending.setResultExtras(Bundle().apply {
                                putStringArrayList("keys", ArrayList(profiles.map { it.first }))
                                putStringArrayList("names", ArrayList(profiles.map { it.second }))
                                putString("status", when {
                                    !rules.enabled -> "DISABLED"
                                    !rules.configured -> "CONFIGURE"
                                    else -> VpnAutoSwitchHooks.status
                                })
                            })
                            pending.setResultCode(-1)
                        }
                    } finally { pending.finish() }
                }
            }
        }, IntentFilter(VpnAutoSwitchSettings.OPTIONS), Context.RECEIVER_EXPORTED, "${Helpers.modulePkg}.permission.BROADCAST")
    }
}
