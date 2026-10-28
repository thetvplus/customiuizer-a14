package tv.withaibuild.customiuizer.mods.utils

import android.os.Handler

/** One pending callback. Never removes messages owned by the Handler's other clients. */
internal class OwnedHandlerCallback {
    private var handler: Handler? = null
    private var pending: Runnable? = null

    @Synchronized fun post(owner: Handler, callback: Runnable, delayMillis: Long): Boolean {
        cancel()
        handler = owner; pending = callback
        if (owner.postDelayed(callback, delayMillis)) return true
        handler = null; pending = null
        return false
    }
    @Synchronized fun take(callback: Runnable): Boolean {
        if (pending !== callback) return false
        handler = null; pending = null
        return true
    }
    @Synchronized fun cancel() {
        pending?.let { handler?.removeCallbacks(it) }
        handler = null; pending = null
    }
}
