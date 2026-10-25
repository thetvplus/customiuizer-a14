package tv.withaibuild.customiuizer.mods

import android.graphics.Path
import tv.withaibuild.customiuizer.utils.G4Geometry

/** Compatibility facade; all project-owned continuous rectangle contours share G4Geometry. */
internal object DockCornerGeometry {
    fun radius(screenRadius: Float, manual: Float, fallback: Float, limit: Float) =
        G4Geometry.radius(screenRadius, manual, fallback, limit)
    internal fun heading(t: Double) = G4Geometry.heading(t)
    fun build(path: Path, width: Float, height: Float, radius: Float) = G4Geometry.build(path, width, height, radius)
    fun buildSoft(path: Path, width: Float, height: Float, radius: Float, bow: Float) =
        G4Geometry.buildSoft(path, width, height, radius, bow)
}
