package tv.withaibuild.customiuizer.mods.island

/** Shared physical content rules, also used while the capsule is between two sizes. */
internal object IslandContentLayout {
    const val CELL_DP = 24
    const val SYMBOL_DP = 20
    const val TEXT_GAP_DP = 8

    fun leading(width: Float, gap: Int, cameraOffset: Float, translation: Float): Float =
        if (gap > 0) (width - gap) * .5f + cameraOffset - translation else width

    fun trailing(width: Float, gap: Int, cameraOffset: Float, translation: Float): Float =
        if (gap > 0) (width - gap) * .5f - cameraOffset + translation else width

    fun opticalShift(before: Int, after: Int, intrinsic: Int): Float =
        if (intrinsic > 0) ((after - before) / (2f * intrinsic)).coerceIn(-.08f, .08f) else 0f
}
