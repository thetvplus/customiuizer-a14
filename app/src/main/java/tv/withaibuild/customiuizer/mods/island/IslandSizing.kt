package tv.withaibuild.customiuizer.mods.island

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shared collapsed forms: content may widen the short island, camera and physical safe bounds take precedence. */
internal object IslandSizing {
    const val SHORT_DP = 172
    const val LONG_MAX_DP = 224
    const val COLLAPSED_HEIGHT_DP = 40
    const val EDGE_DP = 16
    const val HEIGHT_MIN_DP = 32
    const val HEIGHT_MAX_DP = 64
    const val SHORT_MIN_DP = 96
    const val SHORT_MAX_DP = 320
    const val LONG_MIN_DP = 120
    const val LONG_MAX_MANUAL_DP = 400

    fun maximumWidth(density: Float, screen: Int, clearance: Int,
        romMaximum: Int = Int.MAX_VALUE, shortDp: Int = 184,
        longDp: Int = 240, manual: Boolean = false, heightDp: Int = COLLAPSED_HEIGHT_DP, centered: Boolean = true): Int {
        val short = if (manual) shortDp.coerceIn(SHORT_MIN_DP, SHORT_MAX_DP) else automatic(SHORT_DP, heightDp)
        val long = if (manual) maxOf(short, longDp.coerceIn(LONG_MIN_DP, LONG_MAX_MANUAL_DP)) else automatic(LONG_MAX_DP, heightDp)
        return minOf((long * density).roundToInt(), screen - (if (centered) (24 * density).roundToInt() else 0),
            clearance, romMaximum).coerceAtLeast(0)
    }

    fun collapsed(density: Float, screen: Int, clearance: Int, gap: Int,
        leftContent: Float = 0f, rightContent: Float = 0f, romMaximum: Int = Int.MAX_VALUE,
        shortDp: Int = 184, longDp: Int = 240, manual: Boolean = false,
        heightDp: Int = COLLAPSED_HEIGHT_DP, centered: Boolean = true): Int {
        val short = if (manual) shortDp.coerceIn(SHORT_MIN_DP, SHORT_MAX_DP) else automatic(SHORT_DP, heightDp)
        val long = if (manual) maxOf(short, longDp.coerceIn(LONG_MIN_DP, LONG_MAX_MANUAL_DP)) else automatic(LONG_MAX_DP, heightDp)
        val shortWidth = (short * density).roundToInt()
        val longWidth = (long * density).roundToInt()
        val content = ceil(if (centered) gap + 2 * (maxOf(leftContent, rightContent) + EDGE_DP * density)
            else leftContent + rightContent + (2 * EDGE_DP + 8) * density).toInt()
        // Form selection must not depend on the manual size: shrinking the short form
        // must not promote the same content to a wider long form. Match the natural
        // short-form threshold at this height, then apply the chosen manual dimension.
        val shortThreshold = ((if (manual) (184 * (1f + (heightDp - 40) / 80f)).roundToInt()
            else automatic(SHORT_DP, heightDp)) * density).roundToInt()
        val preferred = if (manual) {
            if (content > shortThreshold) longWidth else shortWidth
        } else maxOf(shortWidth, content)
        val ceiling = maximumWidth(density, screen, clearance, romMaximum, shortDp, longDp, manual, heightDp, centered)
        return minOf(preferred, ceiling)
    }

    fun automaticHeight(holeDp: Int): Int = if (holeDp <= 0) 38 else (32f + holeDp * .25f).roundToInt().coerceIn(36, 40)

    fun idleWidth(density: Float, height: Int, hole: Int, short: Int, manualDp: Int = 0): Int {
        val preferred = if (manualDp > 0) manualDp.coerceIn(48, 120) * density else height * 1.8f
        return maxOf(preferred.roundToInt(), height, hole + (16 * density).roundToInt()).coerceAtMost(short)
    }

    fun automatic(base: Int, height: Int) = (base * (1f + (height.coerceIn(HEIGHT_MIN_DP, HEIGHT_MAX_DP) - COLLAPSED_HEIGHT_DP) / 80f)).roundToInt()
}
