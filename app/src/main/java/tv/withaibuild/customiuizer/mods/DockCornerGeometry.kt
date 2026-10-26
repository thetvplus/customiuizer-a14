package tv.withaibuild.customiuizer.mods

import android.graphics.Path
import tv.withaibuild.customiuizer.utils.G4Geometry

/** One native outer outline for every Dock material; inset bevels may use shared G4. */
internal object DockCornerGeometry {
    /** The Launcher panel sets the scale. Physical display corners are only a ceiling. */
    fun radius(screenRadius: Float, manual: Float, fallback: Float, limit: Float): Float {
        val automatic = minOf(fallback, limit * .64f,
            if (screenRadius > 0f) screenRadius else fallback)
        val radius = if (manual > 0f) manual else automatic
        return radius.coerceIn(0f, limit.coerceAtLeast(0f))
    }
    internal fun heading(t: Double) = G4Geometry.heading(t)
    /** All Dock material silhouettes use the compositor's rounded rectangle. The
     * G4 helper below remains available only for the inset decorative bevel. */
    fun outline(path: Path, width: Float, height: Float, radius: Float) {
        val r = radius.coerceIn(0f, minOf(width, height).coerceAtLeast(0f) * .5f)
        path.addRoundRect(0f, 0f, width, height, r, r, Path.Direction.CW)
    }
    fun build(path: Path, width: Float, height: Float, radius: Float) = G4Geometry.build(path, width, height, radius)
    fun buildSoft(path: Path, width: Float, height: Float, radius: Float, bow: Float) =
        G4Geometry.buildSoft(path, width, height, radius, bow)
}
