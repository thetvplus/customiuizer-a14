package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Matrix
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckGeometryTest {
    @Test fun frontCardRemainsAdmittedWhileItsLeadingEdgeIsStillInsideTheScreen() {
        for(page in floatArrayOf(.55f,.75f,.85f)) {
            val model=IosDeckGeometry(1080f,864f,459f/160f,page)
            for(step in 0..300) {
                val cursor=step/100f
                val presentation=model.presentationCursor(0,cursor)
                val scale=model.scale(0,presentation)
                val leading=model.left(0,presentation,0f)+864f*(1f-scale)*.5f
                if(leading<1080f) assertTrue("page=$page cursor=$cursor left=$leading must not recycle on screen",
                    model.candidate(0,8,cursor))
            }
        }
    }
    @Test fun repeatedMovesCaptureOnceAndSnapHandsAlphaToTheNativeSpring() {
        val (parent,view)=pair(); val geometry=DeckCardGeometry(view,parent)
        geometry.apply(.8f); view.alpha=.6f
        geometry.trackSwipe(-50f,-20f)
        repeat(8) { geometry.trackSwipe(-55f,-22f); geometry.updateSwipe() }
        geometry.snapSwipe()
        assertEquals(.6f,view.alpha,0f)
        view.alpha=.8f; geometry.updateSwipe(); assertEquals(.8f,view.alpha,0f)
        view.alpha=1f; geometry.finishSwipe(); assertEquals(1f,view.alpha,0f)
        geometry.release(); assertEquals(1f,view.alpha,0f)
    }
    @Test fun nativeWritesDuringTrackingCommitAndReuseKeepTheirOwnAlpha() {
        val (parent,view)=pair(); val geometry=DeckCardGeometry(view,parent)
        geometry.apply(.8f); view.alpha=.7f; geometry.trackSwipe(-50f,-20f)
        view.alpha=.4f; geometry.nativeAlphaWritten(.4f); geometry.trackSwipe(-80f,-30f)
        geometry.finishSwipe(); assertEquals(.4f,view.alpha,0f)
        geometry.trackSwipe(-40f,-15f)
        geometry.nativeAlphaWritten(1f) // indistinguishable value, explicit native write
        geometry.snapSwipe(); assertEquals(1f,view.alpha,0f)
        geometry.finishSwipe(); view.alpha=.5f; geometry.trackSwipe(-60f,-20f)
        geometry.commitSwipe(); view.alpha=.2f; geometry.updateSwipe(); geometry.release()
        assertEquals(.2f,view.alpha,0f); assertNull(view.animationMatrix)
        val reused=DeckCardGeometry(view,parent); reused.apply(.7f); reused.release()
        assertEquals(.2f,view.alpha,0f)
    }
    @Test fun detachDuringDragRestoresOnlyAlphaStillOwnedByTheModule() {
        val (parent,view)=pair(); val geometry=DeckCardGeometry(view,parent)
        geometry.apply(.8f); view.alpha=.35f; geometry.trackSwipe(-50f,-20f)
        geometry.release(); assertEquals(.35f,view.alpha,0f)
        val next=DeckCardGeometry(view,parent); next.apply(.8f); next.trackSwipe(-50f,-20f)
        view.alpha=.15f; next.release(); assertEquals(.15f,view.alpha,0f)
        next.release(); assertEquals(.15f,view.alpha,0f)
    }
    @Test fun verticalTrackingKeepsNativeQualificationButDisplaysTheFingerPose() {
        val (parent,view)=pair()
        val geometry=DeckCardGeometry(view,parent)
        geometry.apply(.8f)
        val initial=RectF(); geometry.screenContent(initial,0f)
        view.scaleX=.85f; view.scaleY=.85f; view.translationY=-35f; view.alpha=.9f
        geometry.trackSwipe(-90f,-35f)
        assertEquals(.85f,view.scaleX,0f)
        assertEquals(-35f,view.translationY,0f)
        assertEquals(1f,view.alpha,0f)
        val rect=RectF(); geometry.screenContent(rect,0f)
        assertEquals(view.width*.8f,rect.width(),.01f)
        assertEquals(initial.top-90f,rect.top,.01f)
        val held=RectF(rect)
        repeat(20) { geometry.updateSwipe() }
        geometry.screenContent(rect,0f); assertEquals(held,rect)
        // Reverse before release, then cancel: both axes return on native spring.
        view.scaleX=.95f; view.scaleY=.95f; view.translationY=-12f
        geometry.trackSwipe(-30f,-12f)
        geometry.releaseSwipe(); geometry.snapSwipe()
        view.scaleX=1f; view.scaleY=1f; view.translationY=0f; view.alpha=1f
        geometry.updateSwipe(); geometry.finishSwipe()
        geometry.screenContent(rect,0f)
        assertEquals(view.width*.8f,rect.width(),.01f)
        assertEquals(1f,view.alpha,0f)
        geometry.release(); assertNull(view.animationMatrix)
    }

    @Test fun aNewNativeMatrixDuringSwipeRejectsFurtherPresentationWrites() {
        val (parent,view)=pair()
        val geometry=DeckCardGeometry(view,parent)
        geometry.apply(.8f)
        view.scaleX=.9f; view.scaleY=.9f; view.translationY=-30f; view.alpha=.9f
        assertTrue(geometry.trackSwipe(-60f,-30f))
        val external=Matrix().apply { setTranslate(12f,19f) }
        view.setAnimationMatrix(external)
        assertFalse(geometry.updateSwipe())
        geometry.release()
        assertEquals(external,view.animationMatrix)
        assertEquals(.9f,view.alpha,0f)
        assertFalse(geometry.trackSwipe(-70f,-35f))
    }

    @Test fun unusedGeometryNeverRestoresOverANewerNativeMatrix() {
        val (parent,view)=pair()
        val geometry=DeckCardGeometry(view,parent)
        val native=Matrix().apply { setTranslate(12f,19f) }
        view.setAnimationMatrix(native)
        geometry.release()
        assertEquals(native,view.animationMatrix)
    }
    @Test fun invisibleFrontCardsRetainTheirExitSideWithoutUnboundedExponentials() {
        val model=IosDeckGeometry(1080f,588f,3f)
        val front=model.presentationCursor(0,1000f)
        val back=model.presentationCursor(1000,0f)
        assertEquals(3f,front,0f)
        assertEquals(994f,back,0f)
        assertTrue(model.left(0,front,0f)>1080f)
        assertTrue(model.left(1000,back,0f).isFinite())
        assertEquals(.4f,model.presentationCursor(0,.4f),0f)
    }
    @Test fun nativeSpringMayFinishWithoutAFrameworkAnimator() {
        var ended=false
        val listener=DeckSpringEndListener({}, { ended=true })
        listener.javaClass.getDeclaredMethod("onAnimationEnd",android.animation.Animator::class.java).invoke(listener,null)
        assertTrue(ended)
    }
    @Test fun nativeFullFrameAnchorDepthAndHeaderRemainCoherent() {
        for(w in floatArrayOf(720f,1080f,1440f)) {
            val model=IosDeckGeometry(w,w*.62f,w/360f)
            for(cursor in floatArrayOf(0f,.4f,1f,4.5f)) {
                val index=cursor.toInt()
                val s=model.scale(index,cursor)
                assertTrue(s>0f)
                val rawLeft=model.left(index,cursor,0f)
                val actualLeft=rawLeft+(w*.62f)*(1-s)/2
                val depth=kotlin.math.ln((w*.125f+18*w/360)/w)/9+.13f*(cursor-index)
                val nativeScale=.75f*(1+.12f*depth)
                val leading=(-18*w/360+w*kotlin.math.exp(9*depth)-w*.125f)/.75f*nativeScale
                assertEquals(leading+(1-nativeScale)*w/2,actualLeft,.001f)
                assertTrue(model.scale(index+1,cursor)<s)
                val rect=RectF(rawLeft,100f,rawLeft+w*.62f,900f)
                val original=RectF(rect)
                model.content(rect,s,40f)
                assertEquals(original.centerX()+(original.left-original.centerX())*s,rect.left,.001f)
                assertEquals(original.centerY()+(original.top+40-original.centerY())*s,rect.top,.001f)
            }
            assertFalse(model.candidate(-1,10,0f)); assertFalse(model.candidate(10,10,9f))
            assertTrue(model.candidate(0,1,0f)); assertFalse(model.candidate(7,10,0f))
        }
    }
    private fun pair(): Pair<FrameLayout,View> {
        val parent=FrameLayout(RuntimeEnvironment.getApplication())
        val view=View(parent.context)
        parent.addView(view); parent.layout(0,0,600,1000); view.layout(100,200,500,800)
        return parent to view
    }
    @Test fun inverseHitAndSurfaceIncludeOwnedMatrixAndNativeGestureScale() {
        val (parent,view)=pair()
        view.scaleX=.8f; view.scaleY=.8f; view.translationX=20f
        val geometry=DeckCardGeometry(view,parent)
        assertTrue(geometry.apply(.5f))
        assertEquals(.8f,view.scaleX,0f)
        val content=RectF(); geometry.screenContent(content,50f)
        assertEquals(230f,content.left,.001f); assertEquals(390f,content.right,.001f)
        assertTrue(geometry.contains(content.centerX(),content.centerY()))
        assertFalse(geometry.contains(content.left-1,content.top))
        geometry.release(); assertNull(view.animationMatrix)
        assertEquals(.8f,view.scaleX,0f); assertEquals(20f,view.translationX,0f)
        val foreign=Matrix().apply { setTranslate(3f,2f) }; view.setAnimationMatrix(foreign)
        assertFalse(DeckCardGeometry(view,parent).owned)
        assertEquals(foreign,view.animationMatrix)
    }
    @Test fun childGetsOneInverseStreamAndOneCancelWhenNativeTakesOver() {
        val (parent,view)=pair(); val geometry=DeckCardGeometry(view,parent); geometry.apply(.5f)
        val events=mutableListOf<Int>(); var localX=-1f
        view.setOnTouchListener { _,event -> events+=event.actionMasked; localX=event.x; true }
        var take=false; val router=DeckTouchRouter { take }
        fun event(action: Int)=MotionEvent.obtain(0,10,action,300f,500f,0)
        event(MotionEvent.ACTION_DOWN).let { e -> router.begin(e,false,geometry); assertEquals(true,router.touch(e)); e.recycle() }
        assertEquals(200f,localX,0f)
        take=true
        event(MotionEvent.ACTION_MOVE).let { e -> assertNull(router.touch(e)); e.recycle() }
        event(MotionEvent.ACTION_UP).let { e -> assertNull(router.touch(e)); e.recycle() }
        assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_CANCEL),events)
        events.clear(); take=false
        view.setOnTouchListener { _,e -> events+=e.actionMasked; if(e.actionMasked==MotionEvent.ACTION_UP) router.cancel(); true }
        event(MotionEvent.ACTION_DOWN).let { e -> router.begin(e,false,geometry); router.touch(e); e.recycle() }
        event(MotionEvent.ACTION_UP).let { e -> router.touch(e); e.recycle() }
        assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP),events)
    }
}
