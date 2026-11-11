package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import android.graphics.Matrix
import android.widget.FrameLayout
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
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun legacySurfaceEdgeUsesTheSameNativeProgressAndGeometryAsTheVisibleFrontCard() {
        val context=RuntimeEnvironment.getApplication()
        for(rotation in intArrayOf(0,1,3)) {
            val parent=FrameLayout(context)
            val front=View(context).apply { layout(450,200,1050,1400);scaleX=.91f;scaleY=.93f;pivotX=37f;translationX=19f }
            parent.addView(front)
            val header=View(context).apply { layout(0,0,600,52) }
            val geometry=DeckCardGeometry(front,parent).apply { apply(.8f) }
            val initial=RectF();geometry.localContent(initial,52f)
            val start=RectF(100f,200f,700f,1400f)
            val alignment=DeckOpenAlignment(7,start)
            alignment.add(front,header,initial,1,geometry.scale)
            val toNative=Matrix().apply {
                if(rotation!=0) { setRotate(rotation*90f);postTranslate(if(rotation==1) 2400f else 0f,if(rotation==3) 1080f else 0f) }
            }
            val toLocal=Matrix();assertTrue(toNative.invert(toLocal))
            alignment.prepareSurface(toLocal,Matrix(),1080f,2400f)
            val surface=requireNotNull(alignment.surface)
            try {
                for(p in floatArrayOf(0f,.1f,.5f,1f,.5f,0f)) {
                    val current=RectF(100f*(1-p),200f*(1-p),700f+380f*p,1400f+1000f*p)
                    val native=RectF();toNative.mapRect(native,current)
                    val worker=Thread { surface.update(native,p) };worker.start();worker.join(1000L)
                    assertFalse(worker.isAlive)
                    alignment.apply(current,p,1-p)
                    val shown=RectF();geometry.localContent(shown,52f)
                    val edge=surface.boundary(current)
                    if(RectF.intersects(shown,current))
                        assertEquals("Legacy clipping and its posted UI must expose the same edge at r=$rotation p=$p",shown.left,edge,.003f)
                    else assertFalse(edge.isFinite())
                }
            } finally { alignment.release();geometry.release() }
            assertFalse(surface.active)
            surface.update(start,0f);assertTrue(surface.boundary(start).isNaN())
        }
    }
    @Test fun aFrontCardOutsideTheWindowsVerticalRangeCannotOccludeIt() {
        for(y in floatArrayOf(-2000f,4000f)) {
            val surface=DeckOpenAlignment.Surface(7,1080f,2400f,RectF(100f,200f,700f,1400f),Matrix(),1f,0f,1f,0f,
                floatArrayOf(700f,y,600f,1000f,1f,0f))
            for(p in floatArrayOf(0f,.1f,.5f,1f,.5f,0f)) {
                val current=RectF(100f*(1-p),200f*(1-p),700f+380f*p,1400f+1000f*p)
                surface.update(current,p)
                assertFalse("A settling native Y offset must match the modern intersection gate",surface.boundary(current).isFinite())
            }
        }
    }
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
    @Test fun knownNativeFadeWritesStayVisibleDuringOpeningAndReturnTheirLatestState() {
        val context=RuntimeEnvironment.getApplication()
        for(reverse in listOf(false,true)) for(replaced in listOf(false,true)) {
            val neighbor=View(context).apply { layout(450,200,1050,1400);alpha=.9f }
            val start=RectF(100f,200f,700f,1400f)
            val alignment=DeckOpenAlignment(7,start)
            alignment.add(neighbor,View(context),RectF(450f,200f,1050f,1400f),1)
            neighbor.alpha=.3f // The actual Legacy callback wrote its fade.
            alignment.apply(RectF(50f,100f,800f,1600f),.5f,0f,nativeFaded=true)
            assertEquals(.9f,neighbor.alpha,0f)
            if(reverse) {
                neighbor.alpha=.9f // A native write equal to our presentation still counts.
                alignment.apply(start,0f,1f,nativeFaded=true)
            }
            if(replaced) neighbor.alpha=.6f
            alignment.release()
            assertEquals(if(replaced) .6f else if(reverse) .9f else .3f,neighbor.alpha,0f)
        }
    }
}
