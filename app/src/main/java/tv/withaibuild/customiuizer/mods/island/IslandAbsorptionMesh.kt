package tv.withaibuild.customiuizer.mods.island

import android.graphics.RectF
import kotlin.math.pow

/** Native Canvas mesh, driven by the accepted ROM spring. No independent animation clock. */
internal object IslandAbsorptionMesh {
    const val ROWS = 48
    const val FLOATS = (ROWS + 1) * 4
    private val rowWidths = FloatArray(ROWS + 1) { (it.toFloat() / ROWS).pow(.85f) }

    /** A late or faded frame stays native: overlapping translucent layers during
     * commit would briefly increase opacity. Opaque mid-screen releases are admitted. */
    fun mayReplaceNative(progress: Float, alpha: Float) = progress >= 0f && progress < .55f && alpha >= .98f && alpha <= 1f

    /** Only merge once every row fits inside the destination; outside it the page
     * stays at release opacity. The actual island supplies its own live colour/alpha. */
    fun mergeOpacity(bounds: RectF, destination: RectF): Float {
        if (!destination.contains(bounds)) return 1f
        val remaining = ((bounds.height() / destination.height() - .08f) / .92f).coerceIn(0f, 1f)
        return remaining * remaining * (3f - 2f * remaining)
    }

    /** At most 6% softer at the neck, after takeover, never across the page body.
     * Spatial contact and the first 14% of travel both ease in from zero. */
    fun contactColors(colors: IntArray, vertices: FloatArray, destination: RectF, progress: Float,
        contactY: Float = destination.centerY()) {
        val phase = (progress / .14f).coerceIn(0f, 1f)
        val strength = .06f * phase * phase * (3f - 2f * phase)
        for (row in 0..ROWS) {
            val neck = (1f - row / (ROWS * .16f)).coerceIn(0f, 1f)
            val contact = (1f - kotlin.math.abs(vertices[row * 4 + 1] - contactY) /
                destination.height().coerceAtLeast(1f)).coerceIn(0f, 1f)
            val alpha = (255f * (1f - strength * neck * neck * contact)).toInt().coerceIn(0, 255)
            val color = (alpha shl 24) or 0x00ffffff
            colors[row * 2] = color; colors[row * 2 + 1] = color
        }
    }

    /** Native progress includes the drag before release. Admission and mesh travel
     * belong to the accepted return, including a release from an already small page. */
    fun returnProgress(progress: Float, atCommit: Float): Float =
        ((progress - atCommit) / (1f - atCommit).coerceAtLeast(.01f)).coerceIn(0f, 1f)

    fun fill(output: FloatArray, rect: RectF, destination: RectF, progress: Float, bounds: RectF? = null,
        intakeY: Float = destination.centerY()) {
        require(output.size == FLOATS)
        val t = progress.coerceIn(0f, 1f)
        // Keep the captured page's aspect while the leading edge enters first. The
        // native spring supplies the only clock; the trailing edge compresses later.
        val grip = (t / .14f).coerceIn(0f, 1f)
        val leading = grip * grip * (3f - 2f * grip)
        val trailing = t.pow(2.25f)
        val holeHeight = maxOf(1f, destination.height() * .08f)
        val holeTop = intakeY - holeHeight * .5f
        val holeBottom = intakeY + holeHeight * .5f
        val neck = minOf(destination.width() * .28f, destination.height() * .8f)
        val top = rect.top + (holeTop - rect.top) * leading
        val bottom = rect.bottom + (holeBottom - rect.bottom) * trailing
        val topWidth = rect.width() + (neck - rect.width()) * leading
        val bottomWidth = rect.width() + (neck - rect.width()) * t.pow(2.4f)
        val topCenter = rect.centerX() + (destination.centerX() - rect.centerX()) * leading
        val bottomCenter = rect.centerX() + (destination.centerX() - rect.centerX()) * trailing
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        for (row in 0..ROWS) {
            val v = row.toFloat() / ROWS
            val width = topWidth + (bottomWidth - topWidth) * rowWidths[row]
            val center = topCenter + (bottomCenter - topCenter) * v
            val y = top + (bottom - top) * v.pow(1f + 2.6f * t * (1f - t))
            val index = row * 4
            output[index] = center - width * .5f; output[index + 1] = y
            output[index + 2] = center + width * .5f; output[index + 3] = y
            left = minOf(left, output[index]); right = maxOf(right, output[index + 2])
        }
        // Off-centre releases can have their widest edge on an interior row.
        // Test the whole drawn mesh before it is allowed to merge with the island.
        bounds?.set(left, top, right, bottom)
    }

    /** Remaining trailing-body travel, measured in the same geometry used to draw. */
    fun tailProgress(progress: Float): Float = progress.coerceIn(0f, 1f).pow(2.25f)
}
