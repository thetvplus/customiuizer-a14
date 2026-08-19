package tv.withaibuild.customiuizer.mods.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import tv.withaibuild.customiuizer.utils.Helpers

/**
 * Bridges preference change notifications from the module app to system_server.
 *
 * libxposed RemotePreferences in system_server does not deliver change callbacks, so a
 * preference the user changes after boot never reaches the process-local snapshot on its own.
 * (Confirmed device evidence 2026-08-19.) All other hooked processes keep their native
 * listener; this receiver exists only because system_server has none.
 *
 * Contract:
 * - Single-key: Intent carries the changed storage key only, never a value.
 * - Bulk: Intent carries no values, only a bulk marker.
 * - Only trusted sender (module app) is accepted.
 * - system_server reads current values from its own RemotePreferences instance.
 * - system_server never writes back to RemotePreferences (one-way, no feedback loop).
 * - Installed once per process lifetime; no polling and no timers.
 */
object SystemServerPreferenceInvalidation {

    const val ACTION_INVALIDATE = "tv.withaibuild.customiuizer.mods.action.InvalidatePreference"
    const val EXTRA_KEY = "k"
    const val EXTRA_BULK = "b"

    private const val RECEIVER_KEY = "systemServerPreferenceInvalidationReceiver"
    private const val MAX_KEY_LENGTH = 256

    private var installed = false

    @JvmStatic
    fun install(context: Context, bootstrap: PreferenceBootstrap) {
        if (installed) return
        installed = true

        val filter = IntentFilter(ACTION_INVALIDATE)
        ModuleHelper.registerModuleReceiver(
            context,
            RECEIVER_KEY,
            InvalidationReceiver(bootstrap),
            filter,
            Context.RECEIVER_EXPORTED,
        )
    }

    private class InvalidationReceiver(
        private val bootstrap: PreferenceBootstrap,
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ModuleHelper.guarded {
                if (!ModuleHelper.isTrustedBroadcast(this, Helpers.modulePkg)) return@guarded

                if (intent.getBooleanExtra(EXTRA_BULK, false)) {
                    bootstrap.refreshRemoteKey(null)
                    return@guarded
                }

                val key = intent.getStringExtra(EXTRA_KEY)
                if (key == null || !isValidKey(key)) return@guarded
                bootstrap.refreshRemoteKey(key)
            }
        }
    }

    private fun isValidKey(key: String): Boolean {
        return key.isNotEmpty() && key.length <= MAX_KEY_LENGTH
    }
}
