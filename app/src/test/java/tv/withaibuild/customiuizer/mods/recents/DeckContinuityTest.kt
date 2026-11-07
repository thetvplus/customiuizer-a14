package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers.setField
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
    }
    class Handler {
        @JvmField val tasks=ArrayList<Any>()
        @JvmField val start=ArrayList<Transform>()
        @JvmField val end=ArrayList<Transform>()
    }
    private fun <T> unconstructed(type: Class<T>): T {
        val field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible=true }
        return requireNotNull(type.cast(field.type.getMethod("allocateInstance",Class::class.java).invoke(field.get(null),type)))
    }
    private class Fixture(val owner: DeckOwner,val handler: Handler,val stack: FrameLayout,val cards: LinkedHashMap<View,DeckOwner.Card>)
    private fun fixture(): Fixture {
        val api=unconstructed(DeckNativeApi::class.java)
        for(name in listOf("rect","scale","visible","z")) setField(api,name,Transform::class.java.getField(name))
        setField(api,"getTask",TaskView::class.java.getMethod("getTask"))
        for((target,source) in mapOf("currentTasks" to "tasks","startTransforms" to "start","endTransforms" to "end"))
            setField(api,target,Handler::class.java.getField(source))
        val owner=unconstructed(DeckOwner::class.java)
        val handler=Handler()
        val stack=FrameLayout(RuntimeEnvironment.getApplication())
        val cards=LinkedHashMap<View,DeckOwner.Card>()
        for((name,value) in mapOf("api" to api,"handler" to handler,"stack" to stack,"cards" to cards,
            "drawCards" to ArrayList<DeckOwner.Card>(),"pairs" to IdentityHashMap<Any,Any>(),
            "depthOrder" to DeckDepthOrder(stack))) setField(owner,name,value)
        setField(owner,"baseAllowed",true);setField(owner,"tasksAllowed",true)
        setField(owner,"geometryReady",true);setField(owner,"transition",1L)
        return Fixture(owner,handler,stack,cards)
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
        val shadow=DeckCardShadow(view,header,0f,View(view.context))
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
