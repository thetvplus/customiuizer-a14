package tv.withaibuild.customiuizer.utils

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Four continuous corners using the shared quintic curvature ramp.
 * Unit Hermite controls are prepared once; callers own their reusable Path. */
internal object G4RoundRectGeometry {
    private const val STEPS = 12
    private const val RAMP = .25
    private fun heading(t: Double): Double {
        if (t > .5) return PI * .5 - heading(1 - t)
        val integral = if (t < RAMP) RAMP * G4Geometry.curvatureIntegral(t / RAMP) else t - RAMP * .5
        return PI * .5 * integral / (1 - RAMP)
    }
    private var radiusToExtent = 1f
    private val corner = run {
        val points = DoubleArray(STEPS * 6)
        val step = 1.0 / STEPS
        var x = 0.0; var y = 0.0
        for (i in 0 until STEPS) {
            val a = heading(i * step); val b = heading((i + 1) * step); val m = heading((i + .5) * step)
            val nx = x + step / 6 * (cos(a) + 4 * cos(m) + cos(b))
            val ny = y + step / 6 * (sin(a) + 4 * sin(m) + sin(b))
            val at = i * 6
            points[at] = x + step / 3 * cos(a); points[at + 1] = y + step / 3 * sin(a)
            points[at + 2] = nx - step / 3 * cos(b); points[at + 3] = ny - step / 3 * sin(b)
            points[at + 4] = nx; points[at + 5] = ny
            x = nx; y = ny
        }
        // Map the recovered layer-radius parameter onto this G4 curve's circular
        // curvature scale. Its shoulder extends beyond that radius; this Android
        // calibration is not a recovered UIKit path.
        radiusToExtent = (x * PI * .5 / (1 - RAMP)).toFloat()
        FloatArray(points.size) { (points[it] / x).toFloat() }.apply {
            this[1] = 0f; this[3] = 0f
            this[size - 4] = 1f; this[size - 2] = 1f; this[lastIndex] = 1f
        }
    }
    private val circularCorner = FloatArray(STEPS * 6).apply {
        val step = PI * .5 / STEPS
        val control = 4.0 / 3 * tan(step / 4)
        for (i in 0 until STEPS) {
            val a = i * step; val b = (i + 1) * step; val at = i * 6
            this[at] = (sin(a) + control * cos(a)).toFloat()
            this[at + 1] = (1 - cos(a) + control * sin(a)).toFloat()
            this[at + 2] = (sin(b) - control * cos(b)).toFloat()
            this[at + 3] = (1 - cos(b) - control * sin(b)).toFloat()
            this[at + 4] = sin(b).toFloat(); this[at + 5] = (1 - cos(b)).toFloat()
        }
    }
    /** Optional circular limit for narrow capsule ends; ordinary G4 callers stay unchanged. */
    fun build(path: Path, width: Float, height: Float, radius: Float, circularity: Float = 0f) {
        val blend = circularity.coerceIn(0f, 1f)
        val r = (radius * (radiusToExtent + (1f - radiusToExtent) * blend)).coerceIn(0f, minOf(width, height) * .5f)
        if (blend == 1f) {
            path.addRoundRect(0f, 0f, width, height, r, r, Path.Direction.CW)
            return
        }
        path.moveTo(width - r, 0f)
        for (side in 0..3) {
            if (side == 1) path.lineTo(width, height - r)
            if (side == 2) path.lineTo(r, height)
            if (side == 3) path.lineTo(0f, r)
            val ox = when (side) { 0 -> width - r; 1 -> width; 2 -> r; else -> 0f }
            val oy = when (side) { 0 -> 0f; 1 -> height - r; 2 -> height; else -> r }
            val xx = if (side == 0) r else if (side == 2) -r else 0f
            val xy = if (side == 1) r else if (side == 3) -r else 0f
            val yx = if (side == 1) -r else if (side == 3) r else 0f
            val yy = if (side == 0) r else if (side == 2) -r else 0f
            for (i in 0 until STEPS) {
                val at = i * 6
                fun point(index: Int) = corner[index] + (circularCorner[index] - corner[index]) * blend
                path.cubicTo(ox + xx * point(at) + yx * point(at + 1), oy + xy * point(at) + yy * point(at + 1),
                    ox + xx * point(at + 2) + yx * point(at + 3), oy + xy * point(at + 2) + yy * point(at + 3),
                    ox + xx * point(at + 4) + yx * point(at + 5), oy + xy * point(at + 4) + yy * point(at + 5))
            }
        }
        path.close()
    }
}
