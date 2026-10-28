package tv.withaibuild.customiuizer.mods.island

import android.graphics.RectF

/** The first submitted frames must match the still-visible native window. The
 * deformation starts only after that same session's committed hide is accepted. */
internal class IslandAbsorptionHandoff {
    val source = RectF()
    private var start = 0f
    fun progress(nativeProgress: Float, nativeRect: RectF, presented: Boolean): Float {
        if (!presented || source.isEmpty) {
            source.set(nativeRect)
            start = nativeProgress
        }
        return ((nativeProgress - start) / (1f - start).coerceAtLeast(.01f)).coerceIn(0f, 1f)
    }
}
