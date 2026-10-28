package tv.withaibuild.customiuizer.mods.island

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shared collapsed forms: content may widen the short island, camera and physical safe bounds take precedence. */
internal object IslandSizing {
    const val SHORT_DP = 160
    const val LONG_MAX_DP = 218
    const val COLLAPSED_HEIGHT_DP = 40
    const val EDGE_DP = 16
    const val SCREEN_EDGE_DP = 24
    const val LANDSCAPE_EDGE_DP = 48
    const val LANDSCAPE_SAFE_MARGIN_DP = 16
    const val LANDSCAPE_MAX_WIDTH_DP = 240
    const val HEIGHT_MIN_DP = 32
    const val HEIGHT_MAX_DP = 64
    const val SHORT_MIN_DP = 96
    const val SHORT_MAX_DP = 320
    const val LONG_MIN_DP = 120
    const val LONG_MAX_MANUAL_DP = 400
    const val FORM_GAP_DP = 24

    fun maximumWidth(density: Float, screen: Int, clearance: Int,
        romMaximum: Int = Int.MAX_VALUE, shortDp: Int = 184,
        longDp: Int = 240, manual: Boolean = false, heightDp: Int = COLLAPSED_HEIGHT_DP, centered: Boolean = true): Int {
        val short = if (manual) shortDp.coerceIn(SHORT_MIN_DP, SHORT_MAX_DP) else automatic(SHORT_DP, heightDp)
        val long = if (manual) maxOf(short, longDp.coerceIn(LONG_MIN_DP, LONG_MAX_MANUAL_DP)) else automatic(LONG_MAX_DP, heightDp)
        return minOf((long * density).roundToInt(), screen - (if (centered) (2 * SCREEN_EDGE_DP * density).roundToInt() else 0),
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

    fun automaticHeight(holeDp: Int, statusBarDp: Int = 0): Int {
        val preferred = if (holeDp <= 0) 38 else (32f + holeDp * .25f).roundToInt().coerceIn(36, 40)
        if (statusBarDp <= 0) return preferred
        val ceiling = (statusBarDp - 6).coerceIn(32, 40)
        val cameraMinimum = (holeDp + 8).coerceIn(32, 40)
        return minOf(preferred, ceiling).coerceAtLeast(cameraMinimum)
    }

    fun idleWidth(density: Float, height: Int, hole: Int, short: Int, manualDp: Int = 0): Int {
        val preferred = if (manualDp > 0) manualDp.coerceIn(48, 120) * density
            else maxOf(100f * density, height * 2.5f)
        val minimum = maxOf(height, hole + (16 * density).roundToInt())
        val ceiling = if (manualDp > 0) short else maxOf(minimum, short - (16 * density).roundToInt())
        return maxOf(preferred.roundToInt(), minimum).coerceAtMost(ceiling.coerceAtMost(short))
    }

    /** Expanded content can borrow the status-bar space whose intersecting icons fade.
     * Its viewport bound is separate from the quiet, compact form's icon clearance. */
    fun expanded(density: Float, screen: Int, safeWidth: Int, gap: Int, caption: Float,
        heightDp: Int = COLLAPSED_HEIGHT_DP, centered: Boolean = true, shortWidth: Int = 0): Int {
        val minimum = maxOf(automatic(SHORT_DP + FORM_GAP_DP, heightDp) * density,
            shortWidth + FORM_GAP_DP * density)
        val maximum = automatic(320, heightDp) * density
        val content = if (centered) gap + 2 * (maxOf(caption, 24 * density) + EDGE_DP * density)
            else caption + (2 * EDGE_DP + 32) * density
        return minOf(maxOf(minimum, content).coerceAtMost(maximum).roundToInt(),
            screen - (2 * SCREEN_EDGE_DP * density).roundToInt(), safeWidth).coerceAtLeast(0)
    }

    /** A small catch may borrow free space, while keeping both physical edges safe. */
    fun elasticMaximum(width: Int, left: Int, safeLeft: Int, safeRight: Int, density: Float): Int =
        width + minOf((8 * density).roundToInt(),
            2 * minOf(left - safeLeft, safeRight - left - width).coerceAtLeast(0))

    fun horizontalOffsetLimit(short: Int, smallest: Int, gap: Int, contentInset: Int): Int =
        minOf((smallest - gap) / 2, (short - gap) / 2 - contentInset).coerceAtLeast(0)

    fun automatic(base: Int, height: Int) = (base * (1f + (height.coerceIn(HEIGHT_MIN_DP, HEIGHT_MAX_DP) - COLLAPSED_HEIGHT_DP) / 80f)).roundToInt()
}
