package tv.withaibuild.customiuizer.mods.utils

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Window-space status-bar geometry. Content is sized so a later translation
 * cannot cross [0, windowHeight]. No View types; O(1); no allocation in the
 * hot calculation besides the returned data class.
 */
object StatusBarSafeGeometry {

    data class Layout(
        val safeContentHeightPx: Int,
        val effectiveOffsetPx: Float,
        val contentTopPx: Float,
        val contentBottomPx: Float,
    ) {
        fun staysInsideWindow(windowHeightPx: Int, tolerancePx: Float = 1f): Boolean {
            if (windowHeightPx <= 0) return true
            return contentTopPx >= -tolerancePx &&
                contentBottomPx <= windowHeightPx + tolerancePx
        }
    }

    /**
     * @param windowHeightPx StatusBarWindowView height; <= 0 means unmeasured (fail-open).
     * @param naturalContentHeightPx Intrinsic content height before offset reservation.
     * @param requestedOffsetPx User global offset in px. Near-zero keeps native top alignment.
     */
    @JvmStatic
    fun resolve(
        windowHeightPx: Int,
        naturalContentHeightPx: Int,
        requestedOffsetPx: Float,
    ): Layout {
        if (windowHeightPx <= 0) {
            val height = naturalContentHeightPx.coerceAtLeast(0)
            return Layout(height, 0f, 0f, height.toFloat())
        }
        val natural = if (naturalContentHeightPx > 0) naturalContentHeightPx else windowHeightPx
        val maxNatural = if (natural > windowHeightPx) windowHeightPx else natural
        if (abs(requestedOffsetPx) < 0.5f) {
            return Layout(maxNatural, 0f, 0f, maxNatural.toFloat())
        }
        val reserved = (2f * abs(requestedOffsetPx)).roundToInt().coerceAtLeast(0)
        var safe = windowHeightPx - reserved
        if (safe < 1) {
            safe = maxNatural.coerceAtLeast(1).coerceAtMost(windowHeightPx)
            return centered(windowHeightPx, safe, requestedOffsetPx)
        }
        if (safe > maxNatural) safe = maxNatural
        if (safe < 1) safe = 1
        return centered(windowHeightPx, safe, requestedOffsetPx)
    }

    private fun centered(windowHeightPx: Int, safeHeightPx: Int, requestedOffsetPx: Float): Layout {
        val slack = (windowHeightPx - safeHeightPx) / 2f
        val effective = when {
            requestedOffsetPx > slack -> slack
            requestedOffsetPx < -slack -> -slack
            else -> requestedOffsetPx
        }
        val top = (windowHeightPx - safeHeightPx) / 2f + effective
        return Layout(safeHeightPx, effective, top, top + safeHeightPx)
    }
}
