package tv.withaibuild.customiuizer.mods.recents

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** iOS velocity-direction candidate selection on Android's existing q lattice.
 * Native prediction, bounds, velocity and PhysicalScroller still drive motion. */
internal object IosDeckSnap {
    fun target(scroll: Float,gap: Float,count: Int,fingerVelocity: Int): Float? {
        if(!scroll.isFinite() || !gap.isFinite() || gap<=0f || count<2 || fingerVelocity==0) return null
        var cursor=-scroll/gap
        if(cursor<0f || cursor>count-1f) return null
        val nearest=round(cursor)
        if(abs(cursor-nearest)<=2f*Math.ulp(cursor)) cursor=nearest
        // iOS uses content-offset velocity; native A14 reports finger velocity.
        // LTR content velocity has the opposite sign, hence right -> ceil.
        val index=if(fingerVelocity>0) ceil(cursor) else floor(cursor)
        return -index.coerceIn(0f,count-1f)*gap
    }
}
