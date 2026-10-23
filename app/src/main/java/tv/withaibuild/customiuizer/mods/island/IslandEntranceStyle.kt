package tv.withaibuild.customiuizer.mods.island

/** Stable preference values shared by module activities and frozen native island events. */
internal enum class IslandEntranceStyle {
    CENTER,
    TOP;

    companion object {
        fun fromPreference(value: Int): IslandEntranceStyle = if (value == 0) CENTER else TOP
    }
}
