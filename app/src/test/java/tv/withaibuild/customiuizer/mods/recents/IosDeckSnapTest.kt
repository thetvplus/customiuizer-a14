package tv.withaibuild.customiuizer.mods.recents

import org.junit.Assert.*
import org.junit.Test

class IosDeckSnapTest {
    @Test fun projectedTargetsRespectFingerDirectionWithoutReversingContentVelocity() {
        for(gap in floatArrayOf(1f,1.28f,1.43333f)) {
            assertEquals(-2f*gap,IosDeckSnap.target(-1.2f*gap,gap,5,1000)!!,.00001f)
            assertEquals(-gap,IosDeckSnap.target(-1.8f*gap,gap,5,-1000)!!,.00001f)
            for(i in 0..4) for(v in intArrayOf(-2000,2000))
                assertEquals(-i*gap,IosDeckSnap.target(-i*gap,gap,5,v)!!,.00001f)
        }
    }
    @Test fun idleNearestBoundsAndDegenerateStacksRetainNativeBehavior() {
        assertNull(IosDeckSnap.target(-.6f,1f,5,0))
        assertNull(IosDeckSnap.target(.1f,1f,5,1000))
        assertNull(IosDeckSnap.target(-4.1f,1f,5,-1000))
        for(n in 0..1) assertNull(IosDeckSnap.target(0f,1f,n,1000))
        assertNull(IosDeckSnap.target(Float.NaN,1f,5,1000))
        assertNull(IosDeckSnap.target(-1f,0f,5,1000))
        assertNull(IosDeckSnap.target(-1f,Float.POSITIVE_INFINITY,5,1000))
    }
}
