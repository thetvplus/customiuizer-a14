package tv.withaibuild.customiuizer.mods.utils

/**
 * View-layer status-bar geometry.
 *
 * Window / insets height is owned by [tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks].
 * Global offset is applied through [StatusBarSafeGeometry]: reserve height, then
 * translate. Optical leaf scanning is not used.
 */
object StatusBarContentGeometry {

    const val PREF_KEY = "system_statusbar_content_vertical_offset"
    const val RAW_DEFAULT = 20
    const val RAW_SHIFT = 20
    const val DP_DIVIDER = 2f
    const val DUAL_ROWS_PREF = "system_statusbar_dualrows"

    /**
     * Preference units are stored unsigned around [RAW_SHIFT]. `20` is 0 dp.
     * Each stored step is 0.5 dp.
     */
    @JvmStatic
    fun resolveOffsetDp(rawValue: Int): Float = (rawValue - RAW_SHIFT) / DP_DIVIDER

    @JvmStatic
    fun resolveOffsetPx(rawValue: Int, density: Float): Float =
        resolveOffsetDp(rawValue) * density

    /**
     * Dual-row is a custom layout and may consume the extra window pixels.
     * Single-row keeps the inflated SystemUI height unless a non-zero offset
     * needs window-space room to place the content block.
     */
    @JvmStatic
    fun shouldFillWindowForDualRows(dualRows: Boolean, windowHeightPx: Int): Boolean =
        dualRows && windowHeightPx > 0

    @JvmStatic
    fun shouldFillWindowForOffset(requestedOffsetPx: Float, windowHeightPx: Int): Boolean =
        windowHeightPx > 0 && kotlin.math.abs(requestedOffsetPx) >= 0.5f

    /**
     * Intrinsic height used as [StatusBarSafeGeometry] natural content.
     * MATCH_PARENT / unknown originals follow the window.
     */
    @JvmStatic
    fun naturalContentHeightPx(
        originalOwnerHeightPx: Int,
        windowHeightPx: Int,
        dualRows: Boolean,
    ): Int {
        if (windowHeightPx <= 0) {
            return if (originalOwnerHeightPx > 0) originalOwnerHeightPx else 0
        }
        if (dualRows || originalOwnerHeightPx <= 0) return windowHeightPx
        return if (originalOwnerHeightPx > windowHeightPx) windowHeightPx else originalOwnerHeightPx
    }

    @JvmStatic
    fun ownerTargetHeightPx(
        originalOwnerHeightPx: Int,
        windowHeightPx: Int,
        dualRows: Boolean,
        requestedOffsetPx: Float,
    ): Int {
        if (windowHeightPx <= 0) return originalOwnerHeightPx
        if (dualRows || shouldFillWindowForOffset(requestedOffsetPx, windowHeightPx)) {
            return windowHeightPx
        }
        if (originalOwnerHeightPx > 0) {
            return if (originalOwnerHeightPx > windowHeightPx) windowHeightPx else originalOwnerHeightPx
        }
        return originalOwnerHeightPx
    }
}
