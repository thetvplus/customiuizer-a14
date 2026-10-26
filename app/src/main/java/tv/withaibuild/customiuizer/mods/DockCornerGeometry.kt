package tv.withaibuild.customiuizer.mods

import android.graphics.Path
import tv.withaibuild.customiuizer.utils.G4Geometry

/** One size policy for every Dock material; continuous paths use the shared G4 model. */
internal object DockCornerGeometry {
    /** The Launcher panel sets the scale. Physical display corners are only a ceiling. */
    fun radius(screenRadius: Float, manual: Float, fallback: Float, limit: Float): Float {
        val automatic = minOf(fallback, limit * .64f,
            if (screenRadius > 0f) screenRadius else fallback)
        val radius = if (manual > 0f) manual else automatic
        return radius.coerceIn(0f, limit.coerceAtLeast(0f))
    }
    internal fun heading(t: Double) = G4Geometry.heading(t)
    fun build(path: Path, width: Float, height: Float, radius: Float) = G4Geometry.build(path, width, height, radius)
    fun buildSoft(path: Path, width: Float, height: Float, radius: Float, bow: Float) =
        G4Geometry.buildSoft(path, width, height, radius, bow)
}
