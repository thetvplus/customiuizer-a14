package tv.withaibuild.customiuizer.mods.homeindicator

import org.junit.Assert.*
import org.junit.Test

class HomeIndicatorGeometryTest {
    @Test fun designPointsMapByDisplayProportionAndNotAndroidDensity() {
        assertEquals(HomeIndicatorGeometry.Frame(34f,41f,432f,15f),
            HomeIndicatorGeometry.frame(500,80,1206,2622))
        assertEquals(HomeIndicatorGeometry.Frame(63f,41f,675f,15f),
            HomeIndicatorGeometry.frame(800,80,2622,1206))
        val small=HomeIndicatorGeometry.frame(200,40,402,874)!!
        assertEquals(5f,small.height,0f); assertEquals(27f,small.top,0f)
        val large=HomeIndicatorGeometry.frame(800,160,1608,3496)!!
        assertEquals(small.height*4,large.height,0f)
    }
    @Test fun nativeInputAndClippingBoundsWinOverTheRequestedReferenceWidth() {
        val clipped=HomeIndicatorGeometry.frame(280,80,1206,2622)!!
        assertEquals(280f,clipped.width,0f); assertEquals(0f,clipped.left,0f)
        assertNull(HomeIndicatorGeometry.frame(280,20,1206,2622))
        assertNull(HomeIndicatorGeometry.frame(0,80,1206,2622))
        assertNull(HomeIndicatorGeometry.frame(280,80,1206,1206))
    }
    @Test fun armRoundingKeepsAdjacentRepresentableValuesAndNegativeTies() {
        assertEquals(0.0,HomeIndicatorGeometry.roundAway(Math.nextDown(.5)),0.0)
        assertEquals(1.0,HomeIndicatorGeometry.roundAway(.5),0.0)
        assertEquals(-1.0,HomeIndicatorGeometry.roundAway(-.5),0.0)
        assertEquals(-2.0,HomeIndicatorGeometry.roundAway(-1.5),0.0)
    }
    @Test fun platformAdmissionDoesNotRequireDeviceOrBuildIdentity() {
        assertTrue(tv.withaibuild.customiuizer.utils.HomeIndicatorSupport.supported(34,arrayOf("arm64-v8a")))
        assertFalse(tv.withaibuild.customiuizer.utils.HomeIndicatorSupport.supported(36,arrayOf("arm64-v8a")))
        assertFalse(tv.withaibuild.customiuizer.utils.HomeIndicatorSupport.supported(34,emptyArray()))
    }
}
