package tv.withaibuild.customiuizer.mods.island

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shared collapsed forms: content may widen the short island, camera and physical safe bounds take precedence. */
internal object IslandSizing {
    const val SHORT_DP = 184
    const val LONG_MAX_DP = 240
    const val COLLAPSED_HEIGHT_DP = 40
    const val EDGE_DP = 16

    fun maximumWidth(density: Float, screen: Int, clearance: Int,
        romMaximum: Int = Int.MAX_VALUE, shortDp: Int = SHORT_DP,
        longDp: Int = LONG_MAX_DP, manual: Boolean = false, heightDp: Int = COLLAPSED_HEIGHT_DP, centered: Boolean = true): Int {
        val short = if (manual) shortDp.coerceIn(160, 224) else automatic(SHORT_DP, heightDp)
        val long = if (manual) maxOf(short, longDp.coerceIn(184, 280)) else automatic(LONG_MAX_DP, heightDp)
        return minOf((long * density).roundToInt(), screen - (if (centered) (24 * density).roundToInt() else 0),
            clearance, romMaximum).coerceAtLeast(0)
    }

    fun collapsed(density: Float, screen: Int, clearance: Int, gap: Int,
        leftContent: Float = 0f, rightContent: Float = 0f, romMaximum: Int = Int.MAX_VALUE,
        shortDp: Int = SHORT_DP, longDp: Int = LONG_MAX_DP, manual: Boolean = false,
        heightDp: Int = COLLAPSED_HEIGHT_DP, centered: Boolean = true): Int {
        val short = if (manual) shortDp.coerceIn(160, 224) else automatic(SHORT_DP, heightDp)
        val long = if (manual) maxOf(short, longDp.coerceIn(184, 280)) else automatic(LONG_MAX_DP, heightDp)
        val shortWidth = (short * density).roundToInt()
        val longWidth = (long * density).roundToInt()
        val content = ceil(if (centered) gap + 2 * (maxOf(leftContent, rightContent) + EDGE_DP * density)
            else leftContent + rightContent + (2 * EDGE_DP + 8) * density).toInt()
        val preferred = if (manual && content > shortWidth) longWidth else maxOf(shortWidth, content)
        val ceiling = maximumWidth(density, screen, clearance, romMaximum, shortDp, longDp, manual, heightDp, centered)
        return minOf(preferred, ceiling)
    }

    private fun automatic(base: Int, height: Int) = (base * (1f + (height.coerceIn(38, 44) - 40) / 80f)).roundToInt()
}
