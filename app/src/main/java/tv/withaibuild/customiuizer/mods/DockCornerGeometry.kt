package tv.withaibuild.customiuizer.mods

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Analytic G4 model: quintic curvature ramps have zero first/second derivatives at both
 * ends. Integrate heading by arc length, then render a cached Hermite cubic approximation,
 * rather than an exact G4 spline. No native library, bitmap, shader, or work in draw().
 * Geometry approach: https://github.com/tsuijunxi/ContinuousCornerKit
 */
internal object DockCornerGeometry {
    private const val STEPS = 32

    /** Native Launcher panel radius is a scale reference; display corners are only a ceiling. */
    fun radius(screenRadius: Float, manual: Float, fallback: Float, limit: Float): Float {
        val automatic = minOf(fallback, limit * 0.64f,
            if (screenRadius > 0f) screenRadius else fallback)
        val radius = if (manual > 0f) manual else automatic
        return radius.coerceIn(0f, limit.coerceAtLeast(0f))
    }

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

    /** A sub-dp, smooth polynomial warp gives the near-straight edges a very shallow bow.
     * Transform endpoint tangents too, so the existing G4 approximation keeps smooth joins.
     * Used for the optical bevel; the native compositor itself accepts only rounded rectangles.
     */
    fun buildSoft(path: Path, width: Float, height: Float, radius: Float, bow: Float) {
        val amount = bow.coerceIn(0f, minOf(width, height).coerceAtLeast(0f) * 0.01f)
        if (amount == 0f || radius <= 0f) { build(path, width, height, radius); return }
        val w = width - 2 * amount
        val h = height - 2 * amount
        val r = (radius - amount).coerceIn(0f, minOf(w, h) * 0.5f)
        val soft = SoftPath(path, w, h, amount)
        soft.move(r, 0f)
        soft.line(w - r, 0f)
        for (rotation in 0..3) {
            val x = when (rotation) { 0 -> w - r; 1 -> w; 2 -> r; else -> 0f }
            val y = when (rotation) { 0 -> 0f; 1 -> h - r; 2 -> h; else -> r }
            for (i in 0 until STEPS) {
                val offset = i * 6
                val ax = quarter[offset] * r
                val ay = quarter[offset + 1] * r
                val bx = quarter[offset + 2] * r
                val by = quarter[offset + 3] * r
                val ex = quarter[offset + 4] * r
                val ey = quarter[offset + 5] * r
                when (rotation) {
                    0 -> soft.cubic(x + ax, y + ay, x + bx, y + by, x + ex, y + ey)
                    1 -> soft.cubic(x - ay, y + ax, x - by, y + bx, x - ey, y + ex)
                    2 -> soft.cubic(x - ax, y - ay, x - bx, y - by, x - ex, y - ey)
                    else -> soft.cubic(x + ay, y - ax, x + by, y - bx, x + ey, y - ex)
                }
            }
            when (rotation) {
                0 -> soft.line(w, h - r)
                1 -> soft.line(r, h)
                2 -> soft.line(0f, r)
            }
        }
        path.close()
    }

    private class SoftPath(val path: Path, val width: Float, val height: Float, val bow: Float) {
        private var x = 0f
        private var y = 0f
        private fun nx(x: Float) = 2f * x / width - 1f
        private fun ny(y: Float) = 2f * y / height - 1f
        private fun px(x: Float, y: Float) = x + bow + bow * nx(x) * (1f - ny(y) * ny(y))
        private fun py(x: Float, y: Float) = y + bow + bow * ny(y) * (1f - nx(x) * nx(x))
        private fun tx(x: Float, y: Float, dx: Float, dy: Float) =
            dx * (1f + 2f * bow / width * (1f - ny(y) * ny(y))) - dy * 4f * bow * nx(x) * ny(y) / height
        private fun ty(x: Float, y: Float, dx: Float, dy: Float) =
            dy * (1f + 2f * bow / height * (1f - nx(x) * nx(x))) - dx * 4f * bow * nx(x) * ny(y) / width
        fun move(ex: Float, ey: Float) {
            path.moveTo(px(ex, ey), py(ex, ey)); x = ex; y = ey
        }
        fun line(ex: Float, ey: Float) = cubic(x + (ex - x) / 3f, y + (ey - y) / 3f,
            ex - (ex - x) / 3f, ey - (ey - y) / 3f, ex, ey)
        fun cubic(ax: Float, ay: Float, bx: Float, by: Float, ex: Float, ey: Float) {
            path.cubicTo(px(x, y) + tx(x, y, ax - x, ay - y), py(x, y) + ty(x, y, ax - x, ay - y),
                px(ex, ey) + tx(ex, ey, bx - ex, by - ey), py(ex, ey) + ty(ex, ey, bx - ex, by - ey),
                px(ex, ey), py(ex, ey))
            x = ex; y = ey
        }
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
