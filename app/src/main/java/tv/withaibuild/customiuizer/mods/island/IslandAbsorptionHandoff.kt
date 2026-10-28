package tv.withaibuild.customiuizer.mods.island

import android.graphics.RectF

/** Keep following native geometry through takeover, including its release momentum.
 * Only deformation progress rebases when this session's committed hide is accepted. */
internal class IslandAbsorptionHandoff {
    val source = RectF()
    private var start = 0f
    fun progress(nativeProgress: Float, nativeRect: RectF, presented: Boolean): Float {
        if (!presented || source.isEmpty) {
            start = nativeProgress
        }
        source.set(nativeRect)
        return ((nativeProgress - start) / (1f - start).coerceAtLeast(.01f)).coerceIn(0f, 1f)
    }
}
