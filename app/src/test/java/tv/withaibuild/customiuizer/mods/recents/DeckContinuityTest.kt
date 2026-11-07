package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import android.graphics.Matrix
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers.setField
import org.robolectric.util.ReflectionHelpers.getField
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.IdentityHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeckContinuityTest {
    class Transform {
        @JvmField var rect=RectF()
        @JvmField var scale=1f
        @JvmField var visible=false
        @JvmField var z=0f
    }
    class TaskView : FrameLayout(RuntimeEnvironment.getApplication()) {
        val task=Any()
        @JvmField var mFullscreenProgress=0f
        val header=FrameLayout(context)
        fun getHeaderView()=header
    }
    class Handler {
        @JvmField val tasks=ArrayList<Any>()
        @JvmField val start=ArrayList<Transform>()
        @JvmField val end=ArrayList<Transform>()
    }
    class Visual { fun isLandscapeVisually()=true }
    private fun <T> unconstructed(type: Class<T>): T {
        val field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible=true }
        return requireNotNull(type.cast(field.type.getMethod("allocateInstance",Class::class.java).invoke(field.get(null),type)))
    }
    private class Fixture(val owner: DeckOwner,val handler: Handler,val stack: FrameLayout,val cards: LinkedHashMap<View,DeckOwner.Card>)
    private fun fixture(): Fixture {
        val api=unconstructed(DeckNativeApi::class.java)
        setField(api,"recents",Visual::class.java)
        for(name in listOf("rect","scale","visible","z")) setField(api,name,Transform::class.java.getField(name))
        setField(api,"getTask",TaskView::class.java.getMethod("getTask"))
        setField(api,"getHeader",TaskView::class.java.getMethod("getHeaderView"))
        setField(api,"fullscreenProgress",TaskView::class.java.getField("mFullscreenProgress"))
        for((target,source) in mapOf("currentTasks" to "tasks","startTransforms" to "start","endTransforms" to "end"))
            setField(api,target,Handler::class.java.getField(source))
        val owner=unconstructed(DeckOwner::class.java)
        val handler=Handler()
        val stack=FrameLayout(RuntimeEnvironment.getApplication())
        val cards=LinkedHashMap<View,DeckOwner.Card>()
        for((name,value) in mapOf("api" to api,"handler" to handler,"stack" to stack,"cards" to cards,
            "drawCards" to ArrayList<DeckOwner.Card>(),"pairs" to IdentityHashMap<Any,Any>(),
            "depthOrder" to DeckDepthOrder(stack),"global" to Matrix(),"local" to Matrix(),"surface" to Matrix(),
            "gestureReference" to RectF(),"gestureTarget" to RectF(),"occludingBounds" to RectF(),
            "depthIndices" to android.util.SparseIntArray(),"frozen" to HashMap<Any,Any>(),
            "linkedSprings" to ArrayList<Any>(),"router" to DeckTouchRouter { false },
            "ticketTimeout" to Runnable {})) setField(owner,name,value)
        setField(owner,"baseAllowed",true);setField(owner,"tasksAllowed",true)
        setField(owner,"geometryReady",true);setField(owner,"transition",1L)
        return Fixture(owner,handler,stack,cards)
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun fixedPortraitWindowKeepsOpeningAndOcclusionInTheVisualTaskSpaceInBothLandscapeDirections() {
        for(rotation in intArrayOf(1,3)) for(selectedIndex in 0..1) {
            val f=fixture()
            val root=FrameLayout(f.stack.context)
            val container=FrameLayout(f.stack.context)
            root.addView(container);container.addView(f.stack)
            root.layout(0,0,1080,2400);container.layout(0,0,2400,1080);f.stack.layout(0,0,2400,1080)
            container.pivotX=0f;container.pivotY=0f;container.rotation=rotation*90f
            container.translationX=if(rotation==1) 1080f else 0f
            container.translationY=if(rotation==3) 2400f else 0f
            setField(f.owner,"rotation",rotation)
            val api=getField<DeckNativeApi>(f.owner,"api")
            setField(api,"visualLandscape",Visual::class.java.getMethod("isLandscapeVisually"))
            setField(f.owner,"current",f.owner.Scope(Visual()))
            val cards=(0..1).map { index ->
                val view=TaskView();f.stack.addView(view)
                val left=400-index*200
                view.layout(left,200,left+800,800)
                view.addView(view.header);view.header.layout(0,0,800,50)
                val icon=View(view.context);val title=TextView(view.context);val badge=View(view.context)
                for(child in arrayOf(icon,title,badge)) view.header.addView(child)
                val header=DeckHeaderOwner(view.header,icon,title,badge,DeckHeaderConfig(0,0))
                val geometry=DeckCardGeometry(view,f.stack).apply { apply(.8f) }
                val shadow=DeckCardShadow(view,view.header,22f,view,0f)
                DeckOwner.Card(f.owner,geometry,view.task,index,index,header,1f,shadow).also { f.cards[view]=it }
            }
            setField(f.owner,"drawCards",ArrayList(cards))
            try {
                val selected=cards[selectedIndex]
                val neighbor=cards[1-selectedIndex]
                val start=RectF();selected.geometry.screenContent(start,0f)
                f.owner.alignOpening(selected,start,start,0f,1f)
                if(selectedIndex==1) {
                    val shown=RectF();selected.geometry.localContent(shown,0f)
                    val front=RectF();neighbor.geometry.localContent(front,0f)
                    assertEquals("Surface clipping uses the same visual X axis",front.left,
                        f.owner.openingBoundary(shown)!!,.003f)
                }
                for(progress in floatArrayOf(.5f,0f,1f)) {
                    val end=RectF(0f,0f,1080f,2400f)
                    val current=RectF(start.left+(end.left-start.left)*progress,
                        start.top+(end.top-start.top)*progress,start.right+(end.right-start.right)*progress,
                        start.bottom+(end.bottom-start.bottom)*progress)
                    f.owner.alignOpening(selected,start,current,progress,1f-progress)
                }
                val shown=RectF();neighbor.geometry.localContent(shown,0f)
                if(selectedIndex==0) assertTrue("The back card unfolds out of the visible window: r=$rotation, rect=$shown",shown.right<=-24.99f)
                else assertTrue("The front card clears the opening app: r=$rotation, rect=$shown",shown.left>=2424.99f)
            } finally {
                org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(f.owner,"clearOpening")
                cards.forEach { it.release() };f.cards.clear()
                f.owner.endTransition(f.owner.transition)
            }
        }
    }
    @Test fun anUnpreparedViewportCannotStartAWindowHandoff() {
        val f=fixture()
        setField(f.owner,"geometryReady",false);setField(f.owner,"transition",0L)
        setField(f.owner,"linkedSprings",ArrayList<Any>())
        setField(f.owner,"frozen",HashMap<Any,Any>())
        setField(f.owner,"router",DeckTouchRouter { false })
        setField(f.owner,"ticketTimeout",Runnable {})
        val ticket=f.owner.beginTransition()
        try { assertEquals("No window ticket before its viewport is ready",0L,ticket) }
        finally { f.owner.endTransition(ticket) }
    }
    @Test fun repeatedOpeningGeometryQueriesKeepOneTicketAndTheCurrentPresentation() {
        val f=fixture()
        val host=Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        host.get().setContentView(f.stack)
        setField(f.owner,"sequence",1L)
        setField(f.owner,"linkedSprings",ArrayList<Any>())
        val frozen=hashMapOf(0 to Any())
        setField(f.owner,"frozen",frozen)
        setField(f.owner,"router",DeckTouchRouter { false })
        setField(f.owner,"ticketTimeout",Runnable {})
        val opening=DeckOpenAlignment(1,RectF(0f,0f,400f,800f))
        setField(f.owner,"opening",opening)
        val receipt=IosRecentsDeckHooks.WindowReceipt(f.owner,1L)
        try {
            repeat(8) {
                assertEquals("Read-only start-rect queries reuse the click's transition",1L,f.owner.beginTransition())
                assertSame(opening,getField<DeckOpenAlignment>(f.owner,"opening"))
                assertEquals(1,frozen.size)
                assertTrue(receipt.matches(f.owner))
            }
            assertEquals("A new deliberate intent still replaces the transition",2L,f.owner.beginTransition(true))
            assertFalse(receipt.matches(f.owner))
            assertTrue(frozen.isEmpty())
            assertNull(getField<DeckOpenAlignment?>(f.owner,"opening"))
        } finally {
            setField(f.owner,"closed",true)
            f.owner.endTransition(f.owner.transition)
            host.pause().stop().destroy()
        }
    }
    @Test fun rightNeighborEntersContinuouslyWhenTheOldestCardIsDismissedAtTheScrollEdge() {
        val f=fixture(); val view=TaskView()
        val model=IosDeckGeometry(1080f,864f,3f)
        fun endpoint(cursor: Float,visible: Boolean)=Transform().also {
            val left=model.left(5,cursor,0f)
            it.rect.set(left,100f,left+864f,1700f)
            f.owner.putFrame(it,5,model.scale(5,cursor),visible,.16f,1f)
        }
        val a=endpoint(7f,false);val b=endpoint(6f,true)
        assertTrue(a.rect.left>1350f);assertTrue(b.rect.left<1080f)
        f.handler.tasks.add(view.task);f.handler.start.add(a);f.handler.end.add(b)
        // Native updateTaskViewTransforms copies the start, then evaluates LTRB.
        // Include reverse progress: cancellation must retrace the same geometry.
        for(progress in floatArrayOf(0f,.1f,.5f,1f,.5f,0f)) {
            val out=Transform()
            val left=a.rect.left+(b.rect.left-a.rect.left)*progress
            out.rect.set(left,100f,left+864f,1700f)
            f.owner.interpolate(progress)
            f.owner.applyInterpolation(view,out)
            f.owner.interpolate(null)
            assertEquals("Refill must start outside the right edge and follow native progress",left,out.rect.left,.001f)
            assertEquals(a.scale+(b.scale-a.scale)*progress,out.scale,.0001f)
            assertTrue(out.visible)
        }
    }
    @Test fun oldNativeTransformCopiesCannotAcquireANewOrientationGeneration() {
        val f=fixture();val source=Transform();val target=Transform()
        f.owner.putFrame(source,2,.8f,true,.3f,1f)
        val old=XposedHelpers.getAdditionalInstanceField(source,DeckOwner.FRAME) as DeckFrame
        repeat(8) {
            setField(f.owner,"geometryGeneration",f.owner.geometryGeneration+1)
            assertFalse("Native copy must not revalidate a retired orientation",old.copyTo(target))
            assertTrue("Reject the stale native rectangle as well as its Deck scale",DeckFrame.isRetired(target))
            val next=Transform()
            DeckFrame.copy(target,next)
            assertTrue("A second native copy must retain rejection",DeckFrame.isRetired(next))
            f.owner.putFrame(next,2,.7f,true,.3f,1f)
            assertFalse("Freshly computed current geometry can take over",DeckFrame.isRetired(next))
        }
    }
    @Test fun windowFramesCannotCrossTicketsOwnersOrRepeatedOrientationChanges() {
        val f=fixture();val other=fixture()
        val original=IosRecentsDeckHooks.WindowReceipt(f.owner,1L)
        assertTrue(original.matches(f.owner));assertFalse(original.matches(other.owner))
        repeat(8) {
            val ticket=f.owner.transition
            val receipt=IosRecentsDeckHooks.WindowReceipt(f.owner,ticket)
            assertTrue(receipt.matches(f.owner))
            setField(f.owner,"geometryGeneration",f.owner.geometryGeneration+1)
            assertFalse(receipt.matches(f.owner))
            setField(f.owner,"transition",ticket+1)
            assertFalse(receipt.matches(f.owner))
        }
        val current=IosRecentsDeckHooks.WindowReceipt(f.owner,f.owner.transition)
        setField(f.owner,"closed",true)
        assertFalse(current.matches(f.owner));assertFalse(original.matches(f.owner))
    }
    @Test fun entryAlphaWritesAfterTheFirstFrameAreCorrectedBeforeDrawingWithoutTouch() {
        val f=fixture();val view=TaskView();f.stack.addView(view)
        val header=FrameLayout(view.context);val icon=View(view.context);val title=TextView(view.context);val badge=View(view.context)
        for(child in arrayOf(icon,title,badge)) header.addView(child)
        val headerOwner=DeckHeaderOwner(header,icon,title,badge,DeckHeaderConfig(0,0))
        val card=unconstructed(DeckOwner.Card::class.java)
        val shadow=DeckCardShadow(view,header,0f,View(view.context),0f)
        setField(card,"header",headerOwner);setField(card,"geometry",DeckCardGeometry(view,f.stack))
        setField(card,"shadow",shadow)
        f.cards[view]=card
        setField(f.owner,"drawCards",arrayListOf(card))
        headerOwner.pose(.8f,3,-.35f)
        try {
            f.owner.onPreDraw() // The first frame cannot be the only reconciliation.
            for(alpha in floatArrayOf(.2f,.6f,1f)) {
                header.alpha=alpha // Native ViewPropertyAnimator bypasses TaskView setters.
                title.alpha=1f
                f.owner.onPreDraw()
                assertEquals(alpha*.7075f,header.alpha,.001f)
                assertEquals(0f,title.alpha,0f)
            }
        } finally { headerOwner.close();shadow.close() }
        assertEquals(1f,header.alpha,0f)
    }
}
