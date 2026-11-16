package tv.withaibuild.customiuizer.mods.homeindicator

import kotlin.math.ceil
import kotlin.math.floor

/** Geometry subset transcribed from the 22G100 code-only library:
 * phoneDefaultSettings, suggestedSize, nativeBaseFrame and layoutFrame.
 * 402 is an explicit Android design reference, NOT an observed UIScreen value.
 * Physical short edge / 402 maps reference points to this display's pixels.
 * The native handle's clipping/layout remains authoritative. */
internal object HomeIndicatorGeometry {
    data class Frame(val left: Float, val top: Float, val width: Float, val height: Float)
    fun roundAway(value: Double): Double {
        return if (value >= 0.0) {
            val lower = floor(value)
            if (value - lower < .5) lower else lower + 1.0
        } else {
            val upper = ceil(value)
            if (upper - value < .5) upper else upper - 1.0
        }
    }
    fun frame(
        width: Int, height: Int, screenWidth: Int, screenHeight: Int, centerX: Float = width * .5f,
    ): Frame? {
        if (minOf(width, height, screenWidth, screenHeight) <= 0 || screenWidth == screenHeight) return null
        val scale = minOf(screenWidth, screenHeight) / 402.0
        val long = maxOf(screenWidth, screenHeight) / scale
        val minWidth = roundAway(402.0 * .3574)
        val maxWidth = roundAway(long * .2571)
        val contentWidth = screenWidth / scale
        val t = ((contentWidth - 402.0) / (long - 402.0)).coerceIn(0.0, 1.0)
        val desired = Math.fma(t, maxWidth - minWidth, minWidth) * scale
        // Do not resize the input target, expand gesture insets or draw outside
        // the host RenderNode. A narrow host bounds the Android approximation.
        val maxCenteredWidth = 2.0 * minOf(centerX, width - centerX)
        val w = minOf(roundAway(desired), maxCenteredWidth)
        val h = roundAway(5.0 * scale)
        val y = roundAway(height - h - 8.0 * scale)
        if (h < 1.0 || y < 0.0 || w < h) return null
        return Frame(roundAway(centerX - w * .5).toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
    }
}
