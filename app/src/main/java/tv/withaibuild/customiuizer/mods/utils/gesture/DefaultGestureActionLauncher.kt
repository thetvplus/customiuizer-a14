package tv.withaibuild.customiuizer.mods.utils.gesture

import android.content.Context
import tv.withaibuild.customiuizer.mods.GlobalActions

/**
 * Production launcher that casts the machine [context] back to [Context] and forwards
 * to [GlobalActions.handleResolvedAction].
 */
class DefaultGestureActionLauncher : GestureActionLauncher {
    override fun launch(context: Any?, key: String, action: Int, skipLock: Boolean): Boolean {
        val ctx = context as? Context ?: return false
        return GlobalActions.handleResolvedAction(ctx, key, action, skipLock, null)
    }
}
