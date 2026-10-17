package tv.withaibuild.customiuizer.mods.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import tv.withaibuild.customiuizer.mods.GlobalActions

private val fastRebootReceiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GlobalActions.ACTION_PREFIX + "FastReboot") return
        ModuleHelper.guarded {
            if (isOrderedBroadcast) resultCode = GlobalActions.ACTION_FAILED
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val mService = XposedHelpers.getObjectField(pm, "mService")
                if (isOrderedBroadcast) resultCode = GlobalActions.ACTION_HANDLED
                // Does not return on success.
                XposedHelpers.callMethod(mService, "reboot", false, null, false)
            } catch (t: Throwable) {
                if (t is OutOfMemoryError) throw t
                if (isOrderedBroadcast) resultCode = GlobalActions.ACTION_FAILED
                XposedHelpers.log(t)
            }
        }
    }
}

internal fun setupFastRebootReceiver(context: Context): Boolean {
    val filter = IntentFilter(GlobalActions.ACTION_PREFIX + "FastReboot")
    return ModuleHelper.registerModuleReceiver(
        context,
        "fastRebootReceiver",
        fastRebootReceiver,
        filter,
        Context.RECEIVER_EXPORTED,
        GlobalActions.BROADCAST_PERMISSION
    )
}
