package tv.withaibuild.customiuizer.mods.island

import android.graphics.RectF
import kotlin.math.sin

/** Native Canvas mesh, driven by the accepted ROM spring. No independent animation clock. */
internal object IslandAbsorptionMesh {
    const val ROWS = 48
    const val FLOATS = (ROWS + 1) * 4

    fun fill(output: FloatArray, rect: RectF, destination: RectF, progress: Float) {
        require(output.size == FLOATS)
        val t = progress.coerceIn(0f, 1f)
        // Both ends have zero deformation. The leading edge narrows first while the
        // trailing edge retains mass; all rows then converge inside the island mask.
        val envelope = 16f * t * t * (1f - t) * (1f - t)
        for (row in 0..ROWS) {
            val v = row.toFloat() / ROWS
            val pull = envelope * (1f - v) * (1f - v)
            val width = rect.width() + (minOf(rect.width(), destination.width() * .76f) - rect.width()) * pull * .9f
            val center = rect.centerX() + (destination.centerX() - rect.centerX()) * pull * .8f
            val y = rect.top + rect.height() * (v - sin(Math.PI * v).toFloat() * envelope * .12f)
            val index = row * 4
            output[index] = center - width * .5f; output[index + 1] = y
            output[index + 2] = center + width * .5f; output[index + 3] = y
        }
    }
}
