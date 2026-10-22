package tv.withaibuild.customiuizer.utils

import android.content.pm.PackageManager

/** Restore states are positional: dropping one invalid token can target the wrong component. */
internal fun parseMaintenanceStates(raw: String?): IntArray? {
    val tokens = raw?.split(',') ?: return null
    if (tokens.size !in 1..32) return null
    val states = IntArray(tokens.size)
    for (index in tokens.indices) {
        val state = tokens[index].toIntOrNull() ?: return null
        if (state !in PackageManager.COMPONENT_ENABLED_STATE_DEFAULT..
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) return null
        states[index] = state
    }
    return states
}
