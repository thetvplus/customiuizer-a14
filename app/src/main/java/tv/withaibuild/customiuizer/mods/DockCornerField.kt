package tv.withaibuild.customiuizer.mods

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import tv.withaibuild.customiuizer.utils.G4Geometry
import kotlin.math.sqrt

/** One lazy, immutable 16 KiB data texture. Shared G4 contour drives optical distance
 * and normals; no curve search or new allocation in a fragment/frame. No Context retained. */
internal object DockCornerField {
    val shader: BitmapShader by lazy {
        val points = G4Geometry.cornerPoints()
        val pixels = IntArray(64 * 64)
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
            fun byte(value: Float) = ((value * .5f + .5f) * 255f + .5f).toInt().coerceIn(0, 255)
            pixels[y * 64 + x] = (255 shl 24) or (byte(distance) shl 16) or (byte(nx) shl 8) or byte(ny)
        }
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, 64, 0, 0, 64, 64)
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
    }
}
