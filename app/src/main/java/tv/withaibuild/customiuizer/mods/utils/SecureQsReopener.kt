package tv.withaibuild.customiuizer.mods.utils

import android.os.Handler

/** Owns at most one delayed reopen. A later unlock attempt invalidates older callbacks. */
internal class SecureQsReopener(private val handler: Handler, private val unlocked: () -> Boolean, private val open: () -> Unit) {
    private var generation = 0L
    private var completed = 0L
    private var pending: Runnable? = null

    @Synchronized
    fun begin(stillAllowed: () -> Boolean): Runnable {
        val id = ++generation
        pending?.let(handler::removeCallbacks)
        pending = null
        return Runnable { ModuleHelper.guarded { unlocked(id, stillAllowed) } }
    }

    @Synchronized
    private fun unlocked(id: Long, stillAllowed: () -> Boolean) {
        if (id != generation || id == completed) return
        completed = id
        if (!unlocked() || !stillAllowed()) return
        pending?.let(handler::removeCallbacks)
        val action = Runnable { ModuleHelper.guarded { deliver(id, stillAllowed) } }
        pending = action
        if (!handler.postDelayed(action, 800)) pending = null
    }

    @Synchronized
    private fun deliver(id: Long, stillAllowed: () -> Boolean) {
        if (id != generation) return
        pending = null
        if (unlocked() && stillAllowed()) open()
    }
}
