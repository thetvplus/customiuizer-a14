package tv.withaibuild.customiuizer.mods.volumedialogautohide

/**
 * Immutable snapshot of the volume dialog auto-hide delay preferences.
 *
 * [expanded] and [collapsed] are built from a single captured [PrefMap] generation
 * so the two values are always mutually consistent.
 */
internal data class VolumeDialogAutohideDelaySnapshot(
    val expanded: Int,
    val collapsed: Int,
)
