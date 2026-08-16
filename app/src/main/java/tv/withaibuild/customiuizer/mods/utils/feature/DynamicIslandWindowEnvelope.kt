package tv.withaibuild.customiuizer.mods.utils.feature

import android.view.Gravity
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

/**
 * Pure, testable geometry for the top-only Dynamic Island host Window and resting capsule.
 *
 * The module-owned [DynamicIslandHost] Surface is the hard boundary. The capsule only travels
 * and shrinks toward the status-bar edge, with the scale pivot on that edge, so transformed
 * bounds stay inside the resting bounds plus the bounded travel.
 *
 * Host height is `margin + capsule + clearance`. Status-bar insets may inform visual placement
 * of the margin, but never become the host height, clip bounds or animation hard crop.
 */
internal data class DynamicIslandWindowEnvelope(
    val requiredHostHeightPx: Int,
    val shellTopMarginPx: Int,
    val shellBottomMarginPx: Int,
    val parentPaddingTopPx: Int,
    val parentPaddingBottomPx: Int,
    val parentGravity: Int,
    val restingShellTopPx: Int,
    val restingShellBottomPx: Int,
    val entranceStartTranslationY: Float,
    val maxDragTranslationY: Float,
    val pivotY: Float,
    val entranceStartScale: Float,
    val position: StrongToastPosition = StrongToastPosition.TOP,
) {

    fun fitsAllPhases(visualHeightPx: Int, shellTopAtRestPx: Int): Boolean {
        val visual = visualHeightPx.coerceAtLeast(0)
        val restTop = shellTopAtRestPx.coerceAtLeast(0)
        val restBottom = restTop + visual
        return when (position) {
            StrongToastPosition.TOP ->
                restTop + entranceStartTranslationY >= 0f && restBottom <= requiredHostHeightPx
            StrongToastPosition.BOTTOM ->
                restTop >= 0 && restBottom + entranceStartTranslationY <= requiredHostHeightPx
        }
    }

    companion object {

        internal const val ENTRANCE_START_SCALE = 0.90f

        /** Upper bound on travel toward the top edge. */
        internal const val MAX_EDGE_TRAVEL_DP = 20f

        /**
         * Build an envelope for the top (status-bar-side) island.
         *
         * [topMarginPx] may be chosen with status-bar height as a *visual reference*, but the
         * host height itself is never forced equal to a statusBars inset.
         */
        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomClearancePx: Int,
            maxEdgeTravelPx: Int,
        ): DynamicIslandWindowEnvelope {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topMarginPx.coerceAtLeast(0)
            val bottom = bottomClearancePx.coerceAtLeast(0)
            val travel = top.coerceAtMost(maxEdgeTravelPx.coerceAtLeast(0))

            return DynamicIslandWindowEnvelope(
                position = StrongToastPosition.TOP,
                requiredHostHeightPx = visual + top + bottom,
                shellTopMarginPx = top,
                shellBottomMarginPx = 0,
                parentPaddingTopPx = 0,
                parentPaddingBottomPx = bottom,
                parentGravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                restingShellTopPx = top,
                restingShellBottomPx = top + visual,
                entranceStartTranslationY = -travel.toFloat(),
                maxDragTranslationY = -travel.toFloat(),
                pivotY = 0f,
                entranceStartScale = ENTRANCE_START_SCALE,
            )
        }

        /**
         * Transitional source-compatibility wrapper. Runtime configuration cannot select Bottom;
         * callers must migrate to [forTop].
         */
        @Deprecated("Dynamic Island is TOP-only")
        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topClearancePx: Int,
            bottomPaddingPx: Int,
            maxEdgeTravelPx: Int,
        ): DynamicIslandWindowEnvelope {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topClearancePx.coerceAtLeast(0)
            val bottom = bottomPaddingPx.coerceAtLeast(0)
            val travel = bottom.coerceAtMost(maxEdgeTravelPx.coerceAtLeast(0))
            return DynamicIslandWindowEnvelope(
                position = StrongToastPosition.BOTTOM,
                requiredHostHeightPx = visual + top + bottom,
                shellTopMarginPx = 0,
                shellBottomMarginPx = bottom,
                parentPaddingTopPx = top,
                parentPaddingBottomPx = 0,
                parentGravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                restingShellTopPx = top,
                restingShellBottomPx = top + visual,
                entranceStartTranslationY = travel.toFloat(),
                maxDragTranslationY = travel.toFloat(),
                pivotY = visual.toFloat(),
                entranceStartScale = ENTRANCE_START_SCALE,
            )
        }
    }
}
