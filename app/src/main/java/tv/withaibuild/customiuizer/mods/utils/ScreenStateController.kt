package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

/**
 * Central screen on/off observer.
 *
 * Registers a single [Intent.ACTION_SCREEN_ON] / [Intent.ACTION_SCREEN_OFF]
 * receiver and dispatches to registered listeners. The receiver is only kept
 * alive while at least one listener exists, so unrelated features do not pay
 * the cost of receiving every screen broadcast.
 */
object ScreenStateController {

    interface ScreenStateListener {
        fun onScreenStateChanged(isOn: Boolean)
    }

    @Volatile
    private var screenOn: Boolean = true

    private val listeners = ArrayList<ScreenStateListener>(4)
    private val lock = Any()
    private var appContext: Context? = null
    private var receiver: BroadcastReceiver? = null

    /** Whether the screen is currently on. May return true if unknown. */
    @JvmStatic
    fun isScreenOn(): Boolean = screenOn

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @JvmStatic
    fun addListener(context: Context, listener: ScreenStateListener) {
        var notifyInitial = false
        var initialState = screenOn
        synchronized(lock) {
            if (listeners.contains(listener)) return
            val first = listeners.isEmpty()
            listeners.add(listener)

            if (first) {
                val ctx = context.applicationContext
                appContext = ctx
                screenOn = (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                        val isOn = when (intent.action) {
                            Intent.ACTION_SCREEN_ON -> true
                            Intent.ACTION_SCREEN_OFF -> false
                            else -> return
                        }
                        if (screenOn == isOn) return
                        screenOn = isOn
                        // Copy to avoid concurrent modification while iterating.
                        val copy: List<ScreenStateListener>
                        synchronized(lock) {
                            copy = ArrayList(listeners)
                        }
                        for (l in copy) {
                            try {
                                l.onScreenStateChanged(isOn)
                            } catch (t: Throwable) {
                                XposedHelpers.log(t)
                            }
                        }
                    }
                }.also { r ->
                    ctx.registerReceiver(
                        r,
                        IntentFilter().apply {
                            addAction(Intent.ACTION_SCREEN_ON)
                            addAction(Intent.ACTION_SCREEN_OFF)
                        },
                        Context.RECEIVER_NOT_EXPORTED
                    )
                }
                notifyInitial = false
                initialState = screenOn
            } else {
                // Give the new listener the current state, but call outside the lock.
                notifyInitial = true
                initialState = screenOn
            }
        }

        if (notifyInitial) {
            try {
                listener.onScreenStateChanged(initialState)
            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
        }
    }

    @JvmStatic
    fun removeListener(listener: ScreenStateListener) {
        synchronized(lock) {
            listeners.remove(listener)
            if (listeners.isEmpty()) {
                stopLocked()
            }
        }
    }

    private fun stopLocked() {
        val r = receiver ?: return
        receiver = null
        try {
            appContext?.unregisterReceiver(r)
        } catch (_: Throwable) {
        }
        appContext = null
        screenOn = true
    }
}
