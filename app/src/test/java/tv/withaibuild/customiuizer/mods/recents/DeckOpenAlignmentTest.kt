package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeckOpenAlignmentTest {
    @Test fun everyNeighborClearsTheFullscreenFloorBeforeNativeFinishRemovesTheDeck() {
        val context=RuntimeEnvironment.getApplication()
        for(slot in intArrayOf(-2,-1,1,2)) {
            val center=400+350*slot
            val view=View(context).apply { layout(center-300,200,center+300,1400) }
            val alignment=DeckOpenAlignment(7,RectF(100f,200f,700f,1400f))
            alignment.add(view,View(context),RectF(view.left.toFloat(),200f,view.right.toFloat(),1400f),slot)
            alignment.apply(RectF(0f,0f,900f,1800f),1f,0f)
            val actualCenter=view.left+view.width*.5f+view.translationX
            val halfWidth=view.width*view.scaleX*.5f
            if(slot>0) assertTrue(actualCenter-halfWidth>=900f)
            else assertTrue(actualCenter+halfWidth<=0f)
            alignment.release()
        }
    }
    @Test fun unequalNeighborSpacingTracksTheOpeningRectAndRestoresOnlyOwnedValues() {
        val context=RuntimeEnvironment.getApplication()
        val near=View(context).apply { layout(0,0,600,1200) }
        val far=View(context).apply { layout(0,0,600,1200) }
        val h1=View(context); val h2=View(context)
        val start=RectF(100f,200f,700f,1400f)
        val alignment=DeckOpenAlignment(7,start)
        alignment.add(near,h1,RectF(450f,200f,1050f,1400f),1)
        alignment.add(far,h2,RectF(500f,200f,1100f,1400f),2)
        alignment.apply(start,0f,1f)
        assertEquals(0f,near.translationX,0f)
        alignment.apply(RectF(0f,0f,900f,1800f),.5f,.3f)
        assertEquals(1.5f,near.scaleX,0f)
        assertEquals(425f,near.translationX,.001f)
        assertEquals(875f,far.translationX,.001f)
        assertEquals(.3f,h1.alpha,0f)
        assertEquals(0f,near.translationZ,0f)
        far.translationX=17f // Later native ownership must survive release.
        alignment.release()
        assertEquals(0f,near.translationX,0f)
        assertEquals(1f,near.scaleX,0f)
        assertEquals(17f,far.translationX,0f)
        assertEquals(1f,h1.alpha,0f)
    }
}
