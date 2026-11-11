package tv.withaibuild.customiuizer.mods.recents

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** iOS velocity-direction candidate selection on Android's existing q lattice.
 * Native prediction, bounds, velocity and PhysicalScroller still drive motion. */
internal object IosDeckSnap {
    fun target(predicted: Float,scroll: Float,gap: Float,count: Int,fingerVelocity: Int): Float? {
        if(!predicted.isFinite() || !scroll.isFinite() || !gap.isFinite() || gap<=0f || count<2 || fingerVelocity==0) return null
        val limit=count-1f
        val end=-predicted/gap
        var cursor=-scroll/gap
        if(end !in 0f..limit || cursor !in 0f..limit) return null
        val nearest=round(cursor)
        if(abs(cursor-nearest)<=2f*Math.ulp(cursor)) cursor=nearest
        // Snap near the projected resting position, excluding cards behind the
        // current gesture. Rounding the projected position directionally adds
        // up to a full page to the native fling and weakens its deceleration.
        val target=round(end)
        val index=if(fingerVelocity>0) maxOf(ceil(cursor),target) else minOf(floor(cursor),target)
        return -index*gap
    }
}
