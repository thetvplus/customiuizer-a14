package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import android.graphics.Matrix
import android.view.View
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers.setField
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeckLegacyOpeningTest {
    private fun <T> unconstructed(type: Class<T>): T {
        val field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible=true }
        return requireNotNull(type.cast(field.type.getMethod("allocateInstance",Class::class.java).invoke(field.get(null),type)))
    }
    private inner class Fixture {
        val context=RuntimeEnvironment.getApplication()
        val selected=View(context)
        val parent=FrameLayout(context)
        val owner=unconstructed(DeckOwner::class.java).apply {
            setField(this,"gestureBoundary",DeckGestureBoundary())
            setField(this,"baseAllowed",true);setField(this,"tasksAllowed",true)
            setField(this,"geometryReady",true);setField(this,"transition",1L)
            setField(this,"stack",parent);setField(this,"global",Matrix());setField(this,"local",Matrix())
            setField(this,"gestureTarget",RectF())
        }
        val geometry=DeckCardGeometry(selected,parent)

    }
    private fun bind(f: Fixture) {
        val card=unconstructed(DeckOwner.Card::class.java)
        setField(card,"owner",f.owner);setField(card,"geometry",f.geometry);setField(card,"taskId",7)
        XposedHelpers.setAdditionalInstanceField(f.selected,DeckOwner.CARD,card)
    }
    fun target()=Unit
    private class Chain(val values: Array<Any?>,val native: (Array<Any?>)->Any?): XposedInterface.Chain {
        var proceeds=0
        var argumentCopies=0
        override fun getExecutable()=DeckLegacyOpeningTest::class.java.getDeclaredMethod("target")
        override fun getThisObject(): Any?=null
        override fun getArg(index: Int)=values[index]
        override fun getArgs(): List<Any?> { argumentCopies++;return values.toList() }
        override fun proceed(): Any? { proceeds++;return native(values) }
        override fun proceed(args: Array<Any?>): Any? { proceeds++;return native(args) }
        override fun proceedWith(receiver: Any): Any?=throw AssertionError("receiver changed")
        override fun proceedWith(receiver: Any,args: Array<Any?>): Any?=throw AssertionError("receiver changed")
    }
    @Test fun everyLegacyClipModeUnfoldsVisibleNeighborsFromTheFirstSampleAndCanReverse() {
        for(horizontal in listOf(false,true)) for(mode in 0..3) {
            val f=Fixture();bind(f)
            val neighbor=View(f.context).apply { layout(450,200,1050,1400) }
            val header=View(f.context)
            val start=RectF(100f,200f,700f,1400f)
            val alignment=DeckOpenAlignment(7,start)
            alignment.add(neighbor,header,RectF(450f,200f,1050f,1400f),1)
            setField(f.owner,"opening",alignment)
            try {
                for(progress in floatArrayOf(0f,.1f,.25f,.5f,1f,.35f,0f)) {
                    val current=RectF(100f*(1f-progress),200f*(1f-progress),700f+200f*progress,1400f+400f*progress)
                    val chain=Chain(arrayOf(f.selected,progress,horizontal,mode,Any(),start,current)) { args ->
                        // External ROM UI callback's verified branch. Surface clipping
                        // has already consumed its own original mode and is untouched.
                        assertSame(start,args[5]);assertSame(current,args[6])
                        assertEquals(progress,args[1])
                        if(args[2]==true && args[3]==1)
                            alignment.apply(args[6] as RectF,args[1] as Float,(1f-progress/.4f).coerceIn(0f,1f))
                        else if(args[2]==true) neighbor.alpha=(1f-2f*progress).coerceIn(0f,1f)
                        null
                    }
                    IosRecentsDeckHooks.legacyOpeningHook().intercept(chain)
                    assertEquals(1,chain.proceeds)
                    assertEquals("Neighbor remains visible while moving",1f,neighbor.alpha,0f)
                    assertEquals("The foreground card retains its captured size in every clip mode",1f,neighbor.scaleX,.0001f)
                    if(progress==0f) assertEquals(0f,neighbor.translationX,.0001f)
                    else assertTrue("No late fade-only gap",neighbor.translationX>0f)
                    assertEquals("No per-frame argument array or remarshal",0,chain.argumentCopies)
                    assertEquals("Caller mode is retained for Surface code",mode,chain.values[3])
                }
            } finally {
                alignment.release();f.geometry.release()
                XposedHelpers.removeAdditionalInstanceField(f.selected,DeckOwner.CARD)
            }
        }
    }
    @Test fun missingOwnershipRetiredTransitionAndQuickSwitchKeepTheNativeBranch() {
        for(reason in 0..4) {
            val f=Fixture()
            if(reason!=0) bind(f)
            when(reason) {
                1 -> setField(f.owner,"transition",0L)
                2 -> setField(f.owner,"closed",true)
                3 -> setField(f.owner,"gestureBoundary",DeckGestureBoundary().apply { quickSwitching=true })
                4 -> setField(f.geometry,"owned",false)
            }
            val args=arrayOf<Any?>(f.selected,.2f,false,3,Any(),RectF(),RectF())
            val result=Any()
            val chain=Chain(args) { actual -> assertSame(args,actual);result }
            try {
                assertSame(result,IosRecentsDeckHooks.legacyOpeningHook().intercept(chain))
                assertEquals(1,chain.proceeds);assertEquals(0,chain.argumentCopies)
            } finally {
                f.geometry.release();XposedHelpers.removeAdditionalInstanceField(f.selected,DeckOwner.CARD)
            }
        }
    }
    @Test fun adaptingTheUiBranchPreservesNativeFailureIdentityAndDoesNotRetry() {
        val f=Fixture();bind(f)
        val failure=IllegalStateException("native callback")
        val chain=Chain(arrayOf(f.selected,.2f,true,3,Any(),RectF(),RectF())) { throw failure }
        try {
            assertSame(failure,runCatching { IosRecentsDeckHooks.legacyOpeningHook().intercept(chain) }.exceptionOrNull())
            assertEquals(1,chain.proceeds)
        } finally {
            f.geometry.release();XposedHelpers.removeAdditionalInstanceField(f.selected,DeckOwner.CARD)
        }
    }
}
