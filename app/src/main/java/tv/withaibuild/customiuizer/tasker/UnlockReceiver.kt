package tv.withaibuild.customiuizer.tasker

import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log

class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val bundle = intent.getBundleExtra(Constants.EXTRA_BUNDLE)

            val sender = getSentFromPackage()
            if (sender == null) {
                logLimited("identity-missing", "UnlockReceiver: rejected, broadcast sender identity not shared")
                return
            }

            if (!UnlockTokenProvider().verifyBundle(context, bundle, sender)) {
                logLimited("token-mismatch", "UnlockReceiver: rejected, token/host mismatch for sender=$sender")
                return
            }

            val sendIntent = Intent().apply {
                action = UNLOCK_SET_FORCED
                setPackage("com.android.systemui")
                putExtras(bundle ?: Bundle())
            }
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendBroadcast(sendIntent, null, options)
        } catch (t: Throwable) {
            Log.e(TAG, "UnlockReceiver: unexpected error", t)
        }
    }

    private fun logLimited(reason: String, message: String) {
        val now = SystemClock.elapsedRealtime()
        val last = lastLogTimes[reason]
        if (last == null || now - last > LOG_THROTTLE_MS) {
            lastLogTimes[reason] = now
            Log.w(TAG, message)
        }
    }

    companion object {
        private const val TAG = "CustoMIUIzer-UnlockReceiver"
        private const val UNLOCK_SET_FORCED = "tv.withaibuild.customiuizer.mods.action.UnlockSetForced"
        private const val LOG_THROTTLE_MS = 60_000L
        private val lastLogTimes = mutableMapOf<String, Long>()
    }
}
