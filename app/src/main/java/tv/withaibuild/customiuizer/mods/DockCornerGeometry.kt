package tv.withaibuild.customiuizer.mods

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * G4 curvature model: quintic curvature ramps have zero first/second derivatives at both
 * ends. Integrate heading by arc length, then approximate the curve with cached Hermite
 * cubics. No native library, bitmap, shader, or work in draw().
 * Geometry approach: https://github.com/tsuijunxi/ContinuousCornerKit
 */
internal object DockCornerGeometry {
    private const val STEPS = 32

    internal fun heading(t: Double): Double {
        if (t > 0.5) return PI / 2 - heading(1 - t)
        val u = t * 2
        return PI / 2 * u * u * u * u * (2.5 - 3 * u + u * u)
    }

    // A single immutable unit quarter, first created only by an enabled Dock background.
    private val quarter = run {
        val result = FloatArray(STEPS * 6)
        val step = 1.0 / STEPS
        var x = 0.0
        var y = 0.0
        for (i in 0 until STEPS) {
            val a = heading(i * step)
            val b = heading((i + 1) * step)
            val middle = heading((i + 0.5) * step)
            val nextX = x + step / 6 * (cos(a) + 4 * cos(middle) + cos(b))
            val nextY = y + step / 6 * (sin(a) + 4 * sin(middle) + sin(b))
            val offset = i * 6
            result[offset] = (x + step / 3 * cos(a)).toFloat()
            result[offset + 1] = (y + step / 3 * sin(a)).toFloat()
            result[offset + 2] = (nextX - step / 3 * cos(b)).toFloat()
            result[offset + 3] = (nextY - step / 3 * sin(b)).toFloat()
            result[offset + 4] = nextX.toFloat()
            result[offset + 5] = nextY.toFloat()
            x = nextX
            y = nextY
        }
        for (i in result.indices) result[i] = (result[i] / x).toFloat().coerceIn(0f, 1f)
        result[result.lastIndex - 1] = 1f
        result[result.lastIndex] = 1f
        result
    }

    fun build(path: Path, width: Float, height: Float, radius: Float) {
        val r = radius.coerceIn(0f, minOf(width, height) * 0.5f)
        path.moveTo(r, 0f)
        path.lineTo(width - r, 0f)
        append(path, width - r, 0f, r, 0)
        path.lineTo(width, height - r)
        append(path, width, height - r, r, 1)
        path.lineTo(r, height)
        append(path, r, height, r, 2)
        path.lineTo(0f, r)
        append(path, 0f, r, r, 3)
        path.close()
    }

    private fun append(path: Path, x: Float, y: Float, radius: Float, rotation: Int) {
        for (i in 0 until STEPS) {
            val offset = i * 6
            val ax = quarter[offset] * radius
            val ay = quarter[offset + 1] * radius
            val bx = quarter[offset + 2] * radius
            val by = quarter[offset + 3] * radius
            val ex = quarter[offset + 4] * radius
            val ey = quarter[offset + 5] * radius
            when (rotation) {
                0 -> path.cubicTo(x + ax, y + ay, x + bx, y + by, x + ex, y + ey)
                1 -> path.cubicTo(x - ay, y + ax, x - by, y + bx, x - ey, y + ex)
                2 -> path.cubicTo(x - ax, y - ay, x - bx, y - by, x - ex, y - ey)
                else -> path.cubicTo(x + ay, y - ax, x + by, y - bx, x + ey, y - ex)
            }
        }
    }
}
