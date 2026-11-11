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
    @Test fun scrollCoordinateRebasePreservesTheRenderedPoseBeforeTheNativeSpringStarts() {
        for(landscape in listOf(false,true)) {
            val (parent,view)=pair()
            if(landscape) view.layout(180,70,980,670)
            val header=50f;val geometry=DeckCardGeometry(view,parent)
            val before=RectF();val scratch=RectF()
            try {
                geometry.apply(.82f)
                view.pivotX=73f;view.pivotY=41f
                view.scaleX=.61f;view.scaleY=.67f
                view.translationX=37f;view.translationY=-132f
                geometry.localContent(before,header)
                // The native linear-page correction is not the Deck's new frame.
                view.translationX-=615f
                view.layout(view.left+191,view.top+13,view.right+191,view.bottom+13)
                geometry.apply(.91f)
                geometry.localContent(scratch,header)
                assertTrue(kotlin.math.abs(before.left-scratch.left)>100f)
                assertTrue(geometry.rebaseContent(before,header,scratch))
                geometry.localContent(scratch,header)
                assertEquals(before.left,scratch.left,.002f);assertEquals(before.top,scratch.top,.002f)
                assertEquals(before.right,scratch.right,.002f);assertEquals(before.bottom,scratch.bottom,.002f)
                // Zero native progress reads exactly the captured start. Its
                // existing root=1/0 endpoint is still the new stable Deck frame.
                val sx=view.scaleX;val sy=view.scaleY;val tx=view.translationX;val ty=view.translationY
                for(p in floatArrayOf(0f,.2f,.7f,1f)) {
                    view.scaleX=sx+(1f-sx)*p;view.scaleY=sy+(1f-sy)*p
                    view.translationX=tx*(1f-p);view.translationY=ty*(1f-p)
                    geometry.localContent(scratch,header)
                    assertTrue(scratch.width()>0f && scratch.height()>0f)
                    if(p==0f) assertEquals(before.left,scratch.left,.002f)
                }
                assertEquals(view.width*.91f,scratch.width(),.002f)
                assertEquals((view.height-header)*.91f,scratch.height(),.002f)
            } finally { geometry.release() }
        }
    }
    @Test fun focused22G100RenderWitnessesKeepContainerScaleOutOfTheCardTransform() {
        // Explicit mapping inputs, not recovered scene defaults: W=B=390,
        // density=1, page=.75, minimum frame origin=0. ASM 0x1d860e550 /
        // 0x1d860e698 / 0x1d860e64c / 0x1d860e44c supplies depth, transform,
        // render scale and leading offset. The additive container scale in
        // 0x1d8d117dc belongs to a different consumer.
        val model=IosDeckGeometry(390f,390f,1f,.75f)
        val samples=arrayOf(
            floatArrayOf(0f,0f,.73234808f,0f),
            floatArrayOf(1f,0f,.72064808f,-44.231464f),
            floatArrayOf(0f,.5f,.73819808f,52.230613f),
            floatArrayOf(1f,.5f,.72649808f,-28.636795f),
            floatArrayOf(0f,1f,.74404808f,147.14097f))
        for(sample in samples) {
            val index=sample[0].toInt();val cursor=sample[1]
            assertEquals(sample[2],model.scale(index,cursor),.000001f)
            assertEquals(sample[3],model.left(index,cursor,0f),.0005f)
        }
    }

    @Test fun landscapeNativePoseMatchesTheWindowThroughHoldReleaseAndReverse() {
        val parent=FrameLayout(RuntimeEnvironment.getApplication())
        val view=View(parent.context)
        parent.addView(view);parent.layout(0,0,2400,1080);view.layout(650,200,1750,800)
        val header=52f
        val geometry=DeckCardGeometry(view,parent)
        val scale=1.35f
        val target=RectF(650f,200f,1750f,800f)
        IosDeckGeometry(2280f,1100f,1f).content(target,scale,header)
        geometry.apply(scale)
        try {
            for(p in floatArrayOf(0f,.2f,.6f,.6f,.9f,1f,.9f,.2f,0f)) {
                val width=2400f+(target.width()-2400f)*p
                val window=RectF(0f,0f,width,width*(600f-header)/1100f)
                window.offset(target.centerX()-window.centerX(),target.centerY()-window.centerY())
                val input=RectF()
                assertTrue(geometry.windowInput(window,target,target,0,25f,2400f,0f,header,input))
                // Both verified native horizontal consumers apply these same
                // root properties before the owned presentation matrix.
                geometry.alignClosing(input,0,25f,header)
                val shown=RectF();geometry.screenContent(shown,header)
                assertEquals(window.left,shown.left,.002f);assertEquals(window.top,shown.top,.002f)
                assertEquals(window.right,shown.right,.002f);assertEquals(window.bottom,shown.bottom,.002f)
            }
        } finally { geometry.release() }
    }

    @Test fun nativeAppGestureUsesScreenWindowOnceAndArrivesAtTheDeckFrame() {
        val reference=RectF(100f,200f,500f,800f)
        IosDeckGeometry(600f,400f,1f).content(reference,.8f,50f)
        for(neighbor in listOf(false,true)) {
            val (parent,view)=pair()
            if(neighbor) view.layout(-220,200,180,800)
            val scale=if(neighbor) .7f else .8f
            val target=RectF(view.left.toFloat(),200f,view.right.toFloat(),800f)
            IosDeckGeometry(600f,400f,1f).content(target,scale,50f)
            val geometry=DeckCardGeometry(view,parent)
            // Native preparation can precede the first real card layout.
            view.layout(0,0,0,0);geometry.apply(scale)
            view.layout(if(neighbor) -220 else 100,200,if(neighbor) 180 else 500,800)
            try {
                val slots=if(neighbor) -1 else 0
                for(p in floatArrayOf(0f,.4f,.8f,1f,.8f,.4f,0f)) {
                    val width=800f+(reference.width()-800f)*p
                    val window=RectF(reference.left*p,reference.top*p,reference.left*p+width,
                        reference.top*p+width*550f/400f)
                    val saved=RectF(window);val input=RectF();val bend=300f*.3f*.3f*.3f
                    assertTrue(geometry.windowInput(window,reference,target,slots,25f,800f,bend,50f,input))
                    assertEquals(saved,window)
                    // Verified native horizontal updateTaskView consumer: root
                    // width, ordinal page offset, header center and mPer bend.
                    val s=input.width()/view.width
                    view.scaleX=s;view.scaleY=s
                    view.translationX=input.centerX()-(view.left+view.width*.5f)+slots*(input.width()+25f)
                    view.translationY=input.top+(view.height-50f)*s*.5f-50f*s*.5f-(view.top+view.height*.5f)+bend
                    val actual=RectF();geometry.screenContent(actual,50f)
                    val ratio=window.width()/reference.width()
                    val pages=slots*(window.width()+25f)
                    val dx=pages+((target.centerX()-reference.centerX())*ratio-pages)*p
                    assertEquals(window.centerX()+dx,actual.centerX(),.002f)
                    assertEquals(window.centerY()+(target.centerY()-reference.centerY())*ratio*p,actual.centerY(),.002f)
                    if(!neighbor) assertEquals(window.width(),actual.width(),.002f)
                    if(p==1f) {
                        assertEquals(target.left,actual.left,.002f);assertEquals(target.top,actual.top,.002f)
                        assertEquals(target.right,actual.right,.002f);assertEquals(target.bottom,actual.bottom,.002f)
                        assertEquals(1f,view.scaleX,.002f);assertEquals(0f,view.translationX,.002f);assertEquals(0f,view.translationY,.002f)
                    }
                    assertTrue(geometry.windowInput(window,reference,target,slots,25f,800f,0f,50f,input))
                    geometry.alignClosing(input,slots,25f,50f)
                    val closing=RectF();geometry.screenContent(closing,50f)
                    assertEquals(actual.left,closing.left,.002f);assertEquals(actual.top,closing.top,.002f)
                    assertEquals(actual.right,closing.right,.002f);assertEquals(actual.bottom,closing.bottom,.002f)
                }
            } finally { geometry.release() }
        }
    }
    @Test fun cardAndWindowKeepOneCenterWhileNativePivotChanges() {
        val (parent,view)=pair()
        view.pivotX=0f;view.pivotY=0f
        val geometry=DeckCardGeometry(view,parent)
        val expected=RectF(100f,200f,500f,800f)
        IosDeckGeometry(600f,400f,1f).content(expected,.8f,50f)
        try {
            assertTrue(geometry.apply(.8f))
            val actual=RectF();geometry.screenContent(actual,50f)
            assertEquals("Card content and native window endpoint share their center",expected,actual)
            view.pivotX=40f;view.pivotY=100f
            assertTrue(geometry.apply(.8f))
            geometry.screenContent(actual,50f);assertEquals(expected,actual)
            view.scaleX=.7f;view.scaleY=.65f;view.translationY=-20f
            assertTrue(geometry.trackSwipe(-55f,-20f))
            geometry.screenContent(actual,50f)
            expected.offset(0f,-55f)
            assertEquals(expected.left,actual.left,.001f);assertEquals(expected.top,actual.top,.001f)
            assertEquals(expected.right,actual.right,.001f);assertEquals(expected.bottom,actual.bottom,.001f)
            assertEquals(40f,view.pivotX,0f);assertEquals(100f,view.pivotY,0f)
            assertEquals(.7f,view.scaleX,0f);assertEquals(.65f,view.scaleY,0f)
        } finally { geometry.release() }
    }
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
