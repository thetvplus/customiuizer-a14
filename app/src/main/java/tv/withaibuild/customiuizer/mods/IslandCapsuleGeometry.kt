package tv.withaibuild.customiuizer.mods

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Full capsule ends: G4 curvature ramps join the straight crown to a circular side.
 * Unlike four continuous rectangle corners, curvature stays nonzero at each side's midpoint.
 * The analytic curve is rendered with cached Hermite cubics, not an exact G4 spline.
 */
internal object IslandCapsuleGeometry {
    private const val STEPS = 64
    private const val RAMP = 0.25

    internal fun heading(t: Double): Double {
        if (t > 1.0) return PI - heading(2.0 - t)
        val integral = if (t < RAMP) {
            val u = t / RAMP
            RAMP * u * u * u * u * (2.5 - 3 * u + u * u)
        } else t - RAMP * 0.5
        return PI * 0.5 * integral / (1 - RAMP * 0.5)
    }

    private val cap = run {
        val result = FloatArray(STEPS * 6)
        val step = 2.0 / STEPS
        var x = 0.0
        var y = 0.0
        for (i in 0 until STEPS) {
            val a = heading(i * step)
            val b = heading((i + 1) * step)
            val middle = heading((i + 0.5) * step)
            val nx = x + step / 6 * (cos(a) + 4 * cos(middle) + cos(b))
            val ny = y + step / 6 * (sin(a) + 4 * sin(middle) + sin(b))
            val offset = i * 6
            result[offset] = (x + step / 3 * cos(a)).toFloat()
            result[offset + 1] = (y + step / 3 * sin(a)).toFloat()
            result[offset + 2] = (nx - step / 3 * cos(b)).toFloat()
            result[offset + 3] = (ny - step / 3 * sin(b)).toFloat()
            result[offset + 4] = nx.toFloat()
            result[offset + 5] = ny.toFloat()
            x = nx; y = ny
        }
        for (i in result.indices) result[i] = (result[i] / y).toFloat()
        result[result.lastIndex - 1] = 0f
        result[result.lastIndex] = 1f
        result
    }

    fun build(path: Path, width: Float, height: Float) {
        val extent = cap[(STEPS / 2 - 1) * 6 + 4] * height
        val end = minOf(extent, width * 0.5f)
        val sx = if (extent > 0) height * end / extent else 0f
        path.moveTo(end, 0f)
        path.lineTo(width - end, 0f)
        for (side in 0..1) {
            val origin = if (side == 0) width - end else end
            val direction = if (side == 0) 1f else -1f
            val top = if (side == 0) 0f else height
            for (i in 0 until STEPS) {
                val at = i * 6
                path.cubicTo(origin + direction * cap[at] * sx, top + direction * cap[at + 1] * height,
                    origin + direction * cap[at + 2] * sx, top + direction * cap[at + 3] * height,
                    origin + direction * cap[at + 4] * sx, top + direction * cap[at + 5] * height)
                // Subdivide the smooth end cap without changing its physical curvature.
                // The zero-length side joins let API 34 interpolate the actual contours.
                if (i == STEPS / 2 - 1) path.lineTo(if (side == 0) width else 0f, height * .5f)
            }
            if (side == 0) path.lineTo(end, height)
        }
        path.close()
    }
}
