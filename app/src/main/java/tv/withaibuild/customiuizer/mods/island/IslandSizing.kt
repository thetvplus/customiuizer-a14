package tv.withaibuild.customiuizer.mods.island

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shared collapsed forms: content may widen the short island, never consume the edge essentials. */
internal object IslandSizing {
    const val SHORT_DP = 184
    const val LONG_MAX_DP = 240
    const val COLLAPSED_HEIGHT_DP = 40
    const val EDGE_DP = 16

    fun maximumWidth(density: Float, screen: Int, clearance: Int,
        romMaximum: Int = Int.MAX_VALUE, shortDp: Int = SHORT_DP,
        longDp: Int = LONG_MAX_DP, manual: Boolean = false): Int {
        val short = if (manual) shortDp.coerceIn(160, 224) else SHORT_DP
        val long = if (manual) maxOf(short, longDp.coerceIn(184, 280)) else LONG_MAX_DP
        return minOf((long * density).roundToInt(), screen - (24 * density).roundToInt(),
            clearance, romMaximum).coerceAtLeast(0)
    }

    fun collapsed(density: Float, screen: Int, clearance: Int, gap: Int,
        leftContent: Float = 0f, rightContent: Float = 0f, romMaximum: Int = Int.MAX_VALUE,
        shortDp: Int = SHORT_DP, longDp: Int = LONG_MAX_DP, manual: Boolean = false): Int {
        val short = if (manual) shortDp.coerceIn(160, 224) else SHORT_DP
        val long = if (manual) maxOf(short, longDp.coerceIn(184, 280)) else LONG_MAX_DP
        val shortWidth = (short * density).roundToInt()
        val longWidth = (long * density).roundToInt()
        val content = ceil(gap + 2 * (maxOf(leftContent, rightContent) + EDGE_DP * density)).toInt()
        val preferred = if (manual && content > shortWidth) longWidth else maxOf(shortWidth, content)
        val ceiling = maximumWidth(density, screen, clearance, romMaximum, shortDp, longDp, manual)
        return minOf(preferred, ceiling)
    }
}
