package tv.withaibuild.customiuizer.mods.duostatusbar

import android.graphics.Path
import tv.withaibuild.customiuizer.utils.G4RoundRectGeometry

/** Native insideRing single-line metrics: width 2.67, gap 1.67, height 6..10.33.
 * Four slots, linear height interpolation and capsule contour are the bounded
 * Android rendering adaptation; the full native Shape consumer is unresolved. */
internal object DuoIosCellular {
    val paths: Array<Path> by lazy(LazyThreadSafetyMode.NONE) {
        val scale=120f/47f
        val width=2.67f*scale
        val gap=1.67f*scale
        val left=60f-(4f*width+3f*gap)*.5f
        val bottom=(17f+10.33f)*scale
        Array(4) { index ->
            val height=(6f+(10.33f-6f)*index/3f)*scale
            Path().apply {
                G4RoundRectGeometry.build(this,width,height,width*.5f)
                offset(left+index*(width+gap),bottom-height)
            }
        }
    }
}
