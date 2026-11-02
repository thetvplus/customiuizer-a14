package tv.withaibuild.customiuizer.mods.island

import tv.withaibuild.customiuizer.utils.PrefMap

/** One choice for all sources. Instant shares the absorption handoff without capturing a page. */
internal enum class IslandReturnEffect {
    NONE, INSTANT, ABSORPTION;

    companion object {
        fun read(prefs: PrefMap): IslandReturnEffect {
            val key = "system_island_return_effect"
            if (prefs.getBoolean(key) || prefs.getStringAsInt(key, 0) == 1) return ABSORPTION
            return when (prefs.getString(key, "none")) {
                "absorption" -> ABSORPTION
                "instant" -> INSTANT
                else -> NONE
            }
        }
    }
}
