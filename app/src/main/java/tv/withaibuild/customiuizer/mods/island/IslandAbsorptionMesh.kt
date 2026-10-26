package tv.withaibuild.customiuizer.mods.island

import android.graphics.RectF
import kotlin.math.pow

/** Native Canvas mesh, driven by the accepted ROM spring. No independent animation clock. */
internal object IslandAbsorptionMesh {
    const val ROWS = 48
    const val FLOATS = (ROWS + 1) * 4

    fun fill(output: FloatArray, rect: RectF, destination: RectF, progress: Float) {
        require(output.size == FLOATS)
        val t = progress.coerceIn(0f, 1f)
        // Keep the captured page's aspect while the leading edge enters first. The
        // native spring supplies the only clock; the trailing edge compresses later.
        val leading = 1f - (1f - t).pow(2.6f)
        val trailing = t.pow(1.8f)
        val top = rect.top + (destination.top - rect.top) * leading
        val bottom = rect.bottom + (destination.bottom - rect.bottom) * trailing
        val topWidth = rect.width() + (destination.width() - rect.width()) * leading
        val bottomWidth = rect.width() + (destination.width() - rect.width()) * t.pow(2.4f)
        val topCenter = rect.centerX() + (destination.centerX() - rect.centerX()) * leading
        val bottomCenter = rect.centerX() + (destination.centerX() - rect.centerX()) * trailing
        for (row in 0..ROWS) {
            val v = row.toFloat() / ROWS
            val width = topWidth + (bottomWidth - topWidth) * v.pow(.7f)
            val center = topCenter + (bottomCenter - topCenter) * v
            val y = top + (bottom - top) * v.pow(1f + 1.6f * t * (1f - t))
            val index = row * 4
            output[index] = center - width * .5f; output[index + 1] = y
            output[index + 2] = center + width * .5f; output[index + 3] = y
        }
    }

    fun opacity(progress: Float): Float {
        val t = ((progress - .45f) / .55f).coerceIn(0f, 1f)
        return 1f - .82f * t * t * (3f - 2f * t)
    }
}
