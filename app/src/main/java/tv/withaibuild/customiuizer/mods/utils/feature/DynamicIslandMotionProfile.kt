package tv.withaibuild.customiuizer.mods.utils.feature

import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition
/**
 * Pure, testable geometry and timing for the single top Dynamic Island motion path.
 *
 * MUTE / DND / CHARGING share this profile. Event type never changes capsule motion geometry.
 */
internal data class DynamicIslandMotionProfile(
    val windowEnvelope: DynamicIslandWindowEnvelope,
    val windowHeightPx: Int,
    val capsuleTopMarginPx: Int,
    val capsuleBottomMarginPx: Int,
    val restingTranslationY: Float,
    val restingScale: Float,
    val entranceStartTranslationY: Float,
    val entranceStartScale: Float,
    val exitEndTranslationY: Float,
    val exitEndScale: Float,
    val maxDragTranslationY: Float,
    val pivotY: Float,
    val entranceDurationMs: Long,
    val exitDurationMs: Long,
    val position: StrongToastPosition = StrongToastPosition.TOP,
) {
    val edgeTravelPx: Int
        get() = kotlin.math.abs(maxDragTranslationY - restingTranslationY).toInt()

    fun scaleForProgress(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return restingScale + (entranceStartScale - restingScale) * p
    }

    fun capsuleFitsWindow(
        capsuleTopAtRest: Int,
        capsuleHeightPx: Int,
    ): Boolean = windowEnvelope.fitsAllPhases(capsuleHeightPx, capsuleTopAtRest)

    companion object {

        internal const val ENTRANCE_START_SCALE = DynamicIslandWindowEnvelope.ENTRANCE_START_SCALE
        internal const val EXIT_END_SCALE = 0f
        internal const val ENTRANCE_DURATION_MS = 340L
        internal const val EXIT_DURATION_MS = 220L
        internal const val DRAG_RELEASE_DURATION_MS = 220L

        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomClearancePx: Int,
            maxEdgeTravelPx: Int,
        ): DynamicIslandMotionProfile = fromEnvelope(
            envelope = DynamicIslandWindowEnvelope.forTop(
                visualHeightPx,
                topMarginPx,
                bottomClearancePx,
                maxEdgeTravelPx,
            ),
            entranceDurationMs = ENTRANCE_DURATION_MS,
        )

        /** Transitional source-compatibility wrapper; no runtime configuration selects Bottom. */
        @Deprecated("Dynamic Island is TOP-only")
        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topClearancePx: Int,
            bottomPaddingPx: Int,
            maxEdgeTravelPx: Int,
        ): DynamicIslandMotionProfile = fromEnvelope(
            envelope = DynamicIslandWindowEnvelope.forBottom(
                visualHeightPx,
                topClearancePx,
                bottomPaddingPx,
                maxEdgeTravelPx,
            ),
            entranceDurationMs = ENTRANCE_DURATION_MS,
        )

        private fun fromEnvelope(
            envelope: DynamicIslandWindowEnvelope,
            entranceDurationMs: Long,
        ): DynamicIslandMotionProfile = DynamicIslandMotionProfile(
            windowEnvelope = envelope,
            windowHeightPx = envelope.requiredHostHeightPx,
            capsuleTopMarginPx = envelope.shellTopMarginPx,
            capsuleBottomMarginPx = envelope.shellBottomMarginPx,
            restingTranslationY = 0f,
            restingScale = 1f,
            entranceStartTranslationY = envelope.entranceStartTranslationY,
            entranceStartScale = envelope.entranceStartScale,
            exitEndTranslationY = envelope.entranceStartTranslationY,
            exitEndScale = EXIT_END_SCALE,
            maxDragTranslationY = envelope.maxDragTranslationY,
            pivotY = envelope.pivotY,
            entranceDurationMs = entranceDurationMs,
            exitDurationMs = EXIT_DURATION_MS,
            position = envelope.position,
        )
    }
}
