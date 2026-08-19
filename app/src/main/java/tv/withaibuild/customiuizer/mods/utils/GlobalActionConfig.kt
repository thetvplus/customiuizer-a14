package tv.withaibuild.customiuizer.mods.utils

import android.util.SparseBooleanArray
import tv.withaibuild.customiuizer.MainModule

internal val customActionKeys = arrayOf(
    "controls_backlong", "controls_homelong", "controls_menulong",
    "controls_powerdt",
    "controls_fsg_assist_left", "controls_fsg_assist_right",
    "controls_fsg_swipeandstop",
    "controls_navbarleft", "controls_navbarleftlong",
    "controls_navbarright", "controls_navbarrightlong",
    "launcher_swipedown", "launcher_swipeup", "launcher_swipedown2", "launcher_swipeup2",
    "launcher_swipeleft", "launcher_swiperight",
    "launcher_doubletap", "launcher_pinch", "launcher_shake", "launcher_spread",
    "system_statusbarcontrols", "system_statusbarcontrols_longpress",
    "system_lockscreenshortcuts_left", "system_lockscreenshortcuts_right"
)

@Volatile
private var customActionCodeMap: SparseBooleanArray? = null

@Volatile
private var customToggleMap: SparseBooleanArray? = null

@Volatile
private var customActionsReady = false

private val customActionConfigLock = Any()

private fun ensureCustomActionMaps() {
    if (customActionsReady) return
    synchronized(customActionConfigLock) {
        if (customActionsReady) return
        val actionMap = SparseBooleanArray()
        val toggleMap = SparseBooleanArray()
        for (key in customActionKeys) {
            val action = MainModule.mPrefs.getInt(key + "_action", 1)
            if (action > 1) actionMap.put(action, true)
            if (action == 10) {
                val toggle = MainModule.mPrefs.getInt(key + "_toggle", 0)
                if (toggle > 0) toggleMap.put(toggle, true)
            }
        }
        customActionCodeMap = actionMap
        customToggleMap = toggleMap
        customActionsReady = true
    }
}

internal fun hasConfiguredGlobalActions(): Boolean {
    ensureCustomActionMaps()
    return customActionCodeMap!!.size() > 0
}

internal fun hasConfiguredActionCode(code: Int): Boolean {
    ensureCustomActionMaps()
    return customActionCodeMap!!.get(code)
}

internal fun hasConfiguredToggle(what: Int): Boolean {
    ensureCustomActionMaps()
    return customToggleMap!!.get(what)
}
