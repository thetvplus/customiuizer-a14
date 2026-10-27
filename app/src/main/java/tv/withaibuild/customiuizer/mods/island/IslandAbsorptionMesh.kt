package tv.withaibuild.customiuizer.mods.island

import android.graphics.RectF
import kotlin.math.pow

/** Native Canvas mesh, driven by the accepted ROM spring. No independent animation clock. */
internal object IslandAbsorptionMesh {
    const val ROWS = 48
    const val FLOATS = (ROWS + 1) * 4

    /** A translucent or late first frame cannot replace the native window without a flash. */
    fun mayReplaceNative(progress: Float, alpha: Float) = progress >= 0f && progress < .55f && alpha >= .98f && alpha <= 1f

    /** Native progress includes the drag before release. Admission and mesh travel
     * belong to the accepted return, including a release from an already small page. */
    fun returnProgress(progress: Float, atCommit: Float): Float =
        ((progress - atCommit) / (1f - atCommit).coerceAtLeast(.01f)).coerceIn(0f, 1f)

    fun fill(output: FloatArray, rect: RectF, destination: RectF, progress: Float) {
        require(output.size == FLOATS)
        val t = progress.coerceIn(0f, 1f)
        // Keep the captured page's aspect while the leading edge enters first. The
        // native spring supplies the only clock; the trailing edge compresses later.
        val grip = (t / .14f).coerceIn(0f, 1f)
        val leading = grip * grip * (3f - 2f * grip)
        val trailing = t.pow(2.25f)
        val holeHeight = maxOf(1f, destination.height() * .08f)
        val holeTop = destination.centerY() - holeHeight * .5f
        val holeBottom = destination.centerY() + holeHeight * .5f
        val neck = minOf(destination.width() * .28f, destination.height() * .8f)
        val top = rect.top + (holeTop - rect.top) * leading
        val bottom = rect.bottom + (holeBottom - rect.bottom) * trailing
        val topWidth = rect.width() + (neck - rect.width()) * leading
        val bottomWidth = rect.width() + (neck - rect.width()) * t.pow(2.4f)
        val topCenter = rect.centerX() + (destination.centerX() - rect.centerX()) * leading
        val bottomCenter = rect.centerX() + (destination.centerX() - rect.centerX()) * trailing
        for (row in 0..ROWS) {
            val v = row.toFloat() / ROWS
            val width = topWidth + (bottomWidth - topWidth) * v.pow(.85f)
            val center = topCenter + (bottomCenter - topCenter) * v
            val y = top + (bottom - top) * v.pow(1f + 2.6f * t * (1f - t))
            val index = row * 4
            output[index] = center - width * .5f; output[index + 1] = y
            output[index + 2] = center + width * .5f; output[index + 3] = y
        }
    }

    fun opacity(progress: Float): Float {
        val start = (progress / .14f).coerceIn(0f, 1f)
        val base = 1f - .04f * start * start * (3f - 2f * start)
        val tail = ((progress - .62f) / .38f).coerceIn(0f, 1f)
        return base * (1f - .94f * tail * tail * (3f - 2f * tail))
    }
}
