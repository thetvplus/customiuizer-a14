package tv.withaibuild.customiuizer.mods.locationauto

import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference

internal data class LocationAutomationOptions(
    val enabled: Boolean,
    val mode: String,
    val apps: Set<String>,
    val autoClose: Boolean,
) {
    val desired: Boolean? = when {
        !enabled || apps.isEmpty() -> null
        mode == ALLOW -> true
        mode == "block" -> false
        else -> null
    }
    val tracksTasks = desired == true && autoClose

    companion object {
        const val PREFIX = "system_locationauto_"
        const val ALLOW = "allow"

        fun read(prefs: PrefMap) = LocationAutomationOptions(
            prefs.getBoolean(PREFIX + "enabled"),
            prefs.getString(PREFIX + "mode", ALLOW),
            prefs.getStringSet(PREFIX + "apps"),
            prefs.getBoolean(PREFIX + "close"),
        )
    }
}

/** Handler-confined tracking for the opt-in close behavior. */
internal class LocationAutomation(private val change: (Boolean) -> Boolean) {
    private val tasks = HashMap<Int, WeakReference<Any>>()
    private var ownsLocation = false
    private var overflow = false
    val idle: Boolean get() = tasks.isEmpty()

    fun resumed(id: Int, task: Any) {
        if (id in tasks || tasks.size < MAX_TASKS) {
            if (tasks[id]?.get() !== task) tasks[id] = WeakReference(task)
        } else {
            // An untracked card may remain open. Leave location on until the rules change.
            overflow = true
        }
        if (!ownsLocation && change(true)) ownsLocation = true
    }

    fun removed(id: Int, task: Any) {
        if (tasks[id]?.get() !== task) return
        tasks.remove(id)
        if (idle && ownsLocation && !overflow) change(false)
    }

    fun locationTurnedOff() {
        ownsLocation = false
    }

    private companion object {
        const val MAX_TASKS = 128
    }
}
