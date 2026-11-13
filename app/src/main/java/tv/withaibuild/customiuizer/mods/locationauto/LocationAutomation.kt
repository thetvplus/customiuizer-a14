package tv.withaibuild.customiuizer.mods.locationauto

import android.os.Handler
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference

internal data class LocationAutomationOptions(
    val enabled: Boolean,
    val mode: String,
    val apps: Set<String>,
    val autoClose: Boolean,
    val closeDelaySeconds: Int = 0,
) {
    val desired: Boolean? = when {
        !enabled || apps.isEmpty() -> null
        mode == ALLOW -> true
        mode == BLOCK -> false
        else -> null
    }
    val tracksTasks = desired == true && autoClose
    val delaysBlockedClose = desired == false && closeDelaySeconds > 0

    companion object {
        const val PREFIX = "system_locationauto_"
        const val ALLOW = "allow"
        const val BLOCK = "block"

        fun read(prefs: PrefMap) = LocationAutomationOptions(
            prefs.getBoolean(PREFIX + "enabled"),
            prefs.getString(PREFIX + "mode", ALLOW),
            prefs.getStringSet(PREFIX + "apps"),
            prefs.getBoolean(PREFIX + "close"),
            prefs.getInt(PREFIX + "close_delay", 0).coerceIn(0, 30),
        )
    }
}

/** Handler-confined task ownership and one pending automatic close. */
internal class LocationAutomation(
    private val handler: Handler,
    private val change: (Boolean) -> Boolean,
    private val acquire: () -> Boolean = { change(true) },
    private val release: () -> Unit = { change(false) },
    private val finished: () -> Unit,
) {
    private val tasks = HashMap<Int, WeakReference<Any>>()
    private var ownsLocation = false
    private var overflow = false
    val idle: Boolean get() = tasks.isEmpty()
    private var closePending = false
    private var closeOwned = false
    private val close = Runnable {
        ModuleHelper.guarded {
            closePending = false
            try {
                if (closeOwned) release() else change(false)
            } finally { finished() }
        }
    }

    fun resumed(id: Int, task: Any) {
        cancelClose()
        if (tasks.size == MAX_TASKS && id !in tasks) discardCollectedTasks()
        if (id in tasks || tasks.size < MAX_TASKS) {
            if (tasks[id]?.get() !== task) tasks[id] = WeakReference(task)
        } else {
            // An untracked card may remain open. Leave location on until the rules change.
            overflow = true
        }
        if (!ownsLocation && acquire()) ownsLocation = true
    }

    fun removed(id: Int, task: Any, delaySeconds: Int = 0): Boolean {
        if (tasks[id]?.get() !== task) return false
        tasks.remove(id)
        discardCollectedTasks()
        if (idle) {
            if (ownsLocation && !overflow) requestClose(delaySeconds, owned = true)
            else finished()
        }
        return true
    }

    fun recoveredTaskRemoved() {
        if (idle && !overflow) requestClose(0, owned = true)
    }

    fun requestClose(delaySeconds: Int, owned: Boolean = false) {
        if (closePending) return
        closeOwned = owned
        if (delaySeconds == 0) close.run()
        else {
            closePending = handler.postDelayed(close, delaySeconds * 1000L)
            if (!closePending) finished()
        }
    }

    fun cancelClose() {
        if (!closePending) return
        closePending = false
        handler.removeCallbacks(close)
    }

    fun locationTurnedOff() {
        ownsLocation = false
        cancelClose()
        if (idle) finished()
    }

    private fun discardCollectedTasks() {
        if (tasks.isNotEmpty()) tasks.values.removeIf { it.get() == null }
    }

    private companion object {
        const val MAX_TASKS = 128
    }
}
