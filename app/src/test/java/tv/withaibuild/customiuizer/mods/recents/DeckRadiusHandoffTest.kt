package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeckRadiusHandoffTest {
    @Test fun legacySurfaceAndNativeThumbnailArriveAtTheSameDisplayedRadius() {
        val scope=DeckRadiusHandoff(); val owner=Any(); val spring=Any()
        val sourceWidth=1080f; val baseWidth=600f; val a=1.35f; val r=32f
        val endpoint=RectF(20f,80f,20f+baseWidth*a,80f+1200f*a)
        scope.enter(owner,1,spring)
        try {
            scope.capture(owner,1,endpoint,a)
            val native=r*sourceWidth/endpoint.width()
            val corrected=scope.corrected(owner,1,spring,RectF(endpoint).apply { offset(80f,-20f) },native)
            assertEquals(r*a,corrected*endpoint.width()/sourceWidth,.00001f)
            assertEquals(0f,scope.corrected(owner,1,spring,endpoint,0f),0f)
            assertEquals(native,scope.corrected(owner,2,spring,endpoint,native),0f)
            assertEquals(native,scope.corrected(owner,1,Any(),endpoint,native),0f)
        } finally { scope.leave() }
        assertEquals(r,scope.corrected(owner,1,spring,endpoint,r),0f)
    }
    @Test fun nestingRoundedEndpointsAndUnrelatedSpringResetsRemainIsolated() {
        val scope=DeckRadiusHandoff(); val outer=Any(); val inner=Any(); val spring=Any()
        val rect=RectF(0f,0f,810.3f,1620.1f)
        scope.enter(outer,4)
        try {
            scope.capture(outer,4,rect,1.35f)
            scope.enter(inner,5,spring)
            try {
                assertEquals(10f,scope.corrected(outer,4,spring,rect,10f),0f)
                scope.capture(inner,5,rect,1.2f)
                assertEquals(12f,scope.corrected(inner,5,spring,RectF(0f,0f,811f,1622f),10f),.00001f)
                assertEquals(10f,scope.corrected(inner,5,spring,RectF(0f,0f,820f,1620f),10f),0f)
            } finally { scope.leave() }
            assertEquals(13.5f,scope.corrected(outer,4,spring,rect,10f),.00001f)
        } finally { scope.leave() }
    }
}
