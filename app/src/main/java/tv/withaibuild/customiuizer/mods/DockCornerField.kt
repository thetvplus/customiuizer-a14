package tv.withaibuild.customiuizer.mods

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.annotation.SuppressLint
import android.util.Half
import java.nio.ByteBuffer
import java.nio.ByteOrder
import tv.withaibuild.customiuizer.utils.G4Geometry
import kotlin.math.sqrt

/** One lazy, immutable 32 KiB half-float texture. Shared G4 contour drives optical distance
 * and normals; no curve search or new allocation in a fragment/frame. No Context retained. */
internal object DockCornerField {
    val shader: BitmapShader by lazy {
        val points = G4Geometry.cornerPoints()
        val pixels = ByteBuffer.allocateDirect(64 * 64 * 8).order(ByteOrder.nativeOrder())
        for (y in 0 until 64) for (x in 0 until 64) {
            val px = x / 63f; val py = y / 63f
            var best = Float.MAX_VALUE
            var distance = 0f; var nx = 0f; var ny = 0f
            for (i in 0 until points.size / 2 - 1) {
                val ax = points[i * 2]; val ay = points[i * 2 + 1]
                val dx = points[i * 2 + 2] - ax; val dy = points[i * 2 + 3] - ay
                val length = dx * dx + dy * dy
                if (length <= 0f) continue
                val t = (((px - ax) * dx + (py - ay) * dy) / length).coerceIn(0f, 1f)
                val ex = px - ax - t * dx; val ey = py - ay - t * dy
                val squared = ex * ex + ey * ey
                if (squared < best) {
                    best = squared
                    val inverse = 1f / sqrt(length)
                    nx = -dy * inverse; ny = dx * inverse
                    distance = sqrt(squared) * if (ex * nx + ey * ny >= 0f) 1f else -1f
                }
            }
            pixels.putHalf(distance * .5f + .5f)
            pixels.putHalf(nx * .5f + .5f)
            pixels.putHalf(ny * .5f + .5f)
            pixels.putHalf(1f)
        }
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.RGBA_F16)
        pixels.rewind()
        bitmap.copyPixelsFromBuffer(pixels)
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
    }

    // Serialize the raw IEEE-754 bits into RGBA_F16. ByteBuffer.putShort returns
    // ByteBuffer; lint mistakes that fluent return type for widening the half value.
    @SuppressLint("HalfFloat")
    private fun ByteBuffer.putHalf(value: Float) { putShort(Half.toHalf(value)) }
}
