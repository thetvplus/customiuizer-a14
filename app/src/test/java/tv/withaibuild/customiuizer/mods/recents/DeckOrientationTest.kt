package tv.withaibuild.customiuizer.mods.recents

import android.app.Activity
import android.graphics.Rect
import android.graphics.RectF
import android.widget.FrameLayout
import android.view.Surface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers.setField
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.IdentityHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class,qualifiers="port")
class DeckOrientationTest {
    // Only the selected native ABI is supplied. No launcher constructor, Hook
    // installation or physical/visual orientation equivalence is simulated.
    class Algorithm {
        @JvmField var visual=true
        @JvmField var window=Rect(0,0,2400,1080)
        @JvmField var insets=Rect(90,0,30,40)
        @JvmField var base=RectF(0f,0f,1100f,600f)
        @JvmField var padding=Rect()
        @JvmField var count=4
        @JvmField var gap=30f
        @JvmField var minimum=-99f
        @JvmField var header=52
        fun isLandscapeVisually()=visual
        fun calculateMinScrollP()=-7f
    }
    class Transform {
        @JvmField var rect=RectF()
        @JvmField var scale=1f
        @JvmField var visible=false
        @JvmField var z=0f
    }
    private fun <T> unconstructed(type: Class<T>): T {
        val field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible=true }
        return requireNotNull(type.cast(field.type.getMethod("allocateInstance",Class::class.java).invoke(field.get(null),type)))
    }
    @Test fun visualAxesDriveGeometryAndRotationRetiresAllOldEndpoints() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            val api=unconstructed(DeckNativeApi::class.java)
            for((target,source) in mapOf("window" to "window","visualInsets" to "insets","base" to "base",
                "padding" to "padding","count" to "count","gap" to "gap","headerHeight" to "header"))
                setField(api,target,Algorithm::class.java.getField(source))
            setField(api,"visualLandscape",Algorithm::class.java.getMethod("isLandscapeVisually"))
            setField(api,"minimum",Algorithm::class.java.getField("minimum"))
            setField(api,"minimumScroll",Algorithm::class.java.getMethod("calculateMinScrollP"))
            for(name in listOf("rect","scale","visible","z")) setField(api,name,Transform::class.java.getField(name))
            val owner=unconstructed(DeckOwner::class.java)
            val stack=FrameLayout(host)
            val frozen=HashMap<Int,Any>().apply { put(0,Any()) }
            val pairs=IdentityHashMap<Any,Any>().apply { put(Any(),Any()) }
            for((name,value) in mapOf("api" to api,"stack" to stack,"headerConfig" to DeckHeaderConfig(0,0),
                "frozen" to frozen,"pairs" to pairs,"cards" to LinkedHashMap<Any,Any>(),
                "drawCards" to ArrayList<Any>(),"linkedSprings" to ArrayList<Any>(),
                "router" to DeckTouchRouter { false },"ticketTimeout" to Runnable {})) setField(owner,name,value)
            setField(owner,"baseAllowed",true);setField(owner,"tasksAllowed",true);setField(owner,"transition",1L)
            val algorithm=Algorithm()
            val scope=owner.Scope(algorithm)
            setField(owner,"current",scope);setField(owner,"stable",scope)
            setField(owner,"geometryReady",true)
            val transform=Transform()
            assertTrue("Portrait host, landscape visual window",scope.write(0,0f,transform))
            val motion=DeckModernMotion(DeckModernMotionTest.Native::class.java)
            val spring=DeckModernMotionTest.Native()
            motion.mark(spring,owner,owner.transition,DeckSpringScene.OPEN)
            motion.afterConfigure(spring,"OPEN_FROM_RECENTS",false)
            assertFalse(owner.customMotionAllowed)
            assertTrue("Landscape must retain native window spring parameters",spring.calls.isEmpty())
            assertEquals((1040f-600f)*.5f,transform.rect.top,.001f)
            val expectedContent=RectF(transform.rect)
            IosDeckGeometry(2280f,1100f,1f).content(expectedContent,
                (XposedHelpers.getAdditionalInstanceField(transform,DeckOwner.FRAME) as DeckFrame).scale,52f)
            val preparedContent=RectF()
            assertTrue(scope.contentAt(0,0f,preparedContent));assertEquals(expectedContent,preparedContent)
            val oldFrame=DeckFrame(owner,RectF(transform.rect),0,transform.scale,true,0f,1f)
            val failure=IllegalStateException("retired neighbor")
            val neighbor=object: FrameLayout(host) {
                var failing=false
                override fun setTranslationX(value: Float) { if(failing) throw failure;super.setTranslationX(value) }
            }
            val opening=DeckOpenAlignment(1,RectF(0f,0f,400f,800f))
            opening.add(neighbor,FrameLayout(host),RectF(0f,0f,400f,800f),1)
            setField(owner,"opening",opening);neighbor.failing=true
            algorithm.visual=false
            assertSame(failure,assertThrows(IllegalStateException::class.java) { scope.refreshOrientation() })
            assertTrue(frozen.isEmpty());assertTrue(pairs.isEmpty())
            assertEquals(0L,owner.transition)
            assertNotEquals(oldFrame.generation,owner.geometryGeneration)
            assertFalse(scope.landscape)
            setField(owner,"transition",2L)
            assertFalse("Mixed visual flag/window must fall back",scope.write(0,0f,transform))
            assertFalse("All Deck input and scroll hooks must return to native",owner.allowed)
            assertNull(owner.snap(0f))
            assertEquals("Native scrolling bounds are returned with input",-7f,algorithm.minimum,0f)
            assertTrue(transform.rect.width()>0f)
            algorithm.window.set(0,0,1080,2400)
            owner.refreshGeometry()
            assertTrue("A native layout update can restore the shared contract",owner.allowed)
            assertTrue(scope.write(0,0f,transform))
            assertTrue(owner.customMotionAllowed)
            motion.mark(spring,owner,owner.transition,DeckSpringScene.OPEN)
            motion.afterConfigure(spring,"OPEN_FROM_RECENTS",false)
            assertEquals(4,spring.calls.size)
            // A reconfigure into landscape restores the old owned parameters
            // before allowing native defaults to remain authoritative.
            motion.beforeConfigure(spring)
            assertEquals(200f,spring.mWidthBundle.getStiffness(),0f)
            DeckModernMotion.clear(spring,owner,owner.transition)
            repeat(8) { turn ->
                val cached=Transform()
                assertTrue((XposedHelpers.getAdditionalInstanceField(transform,DeckOwner.FRAME) as DeckFrame).copyTo(cached))
                val previous=XposedHelpers.getAdditionalInstanceField(cached,DeckOwner.FRAME) as DeckFrame
                val receipt=IosRecentsDeckHooks.WindowReceipt(owner,owner.transition)
                algorithm.visual=turn%2==0
                // Visual direction arrives before matching native window bounds.
                owner.refreshGeometry()
                assertFalse(owner.allowed)
                assertEquals(0L,owner.transition)
                assertFalse(receipt.matches(owner))
                assertFalse(previous.copyTo(Transform()))
                if(algorithm.visual) algorithm.window.set(0,0,2400,1080)
                else algorithm.window.set(0,0,1080,2400)
                owner.refreshGeometry()
                setField(owner,"transition",(turn+3).toLong())
                assertTrue(owner.allowed)
                assertTrue(scope.write(0,0f,transform))
                assertFalse(previous.copyTo(Transform()))
            }
            // The two landscape directions share width/height and the visual
            // boolean, but an old window receipt still belongs to different axes.
            setField(api,"recents",Algorithm::class.java)
            host.setContentView(stack)
            val display=shadowOf(stack.display)
            try {
                display.setRotation(Surface.ROTATION_90)
                algorithm.visual=true;algorithm.window.set(0,0,2400,1080)
                owner.refreshGeometry()
                assertEquals("Display rotation before its configuration cannot start a window",0L,owner.beginTransition())
                owner.refreshGeometry()
                setField(owner,"transition",99L)
                assertTrue(scope.write(0,0f,transform))
                assertTrue(scope.contentAt(0,0f,preparedContent))
                val receipt=IosRecentsDeckHooks.WindowReceipt(owner,99L)
                val endpoint=DeckFrame(owner,RectF(0f,0f,1100f,600f),0,1f,true,1f,1f)
                assertTrue(receipt.matches(owner))
                owner.rotationChanged(Surface.ROTATION_270)
                assertFalse("Native display event retires the old window before layout",receipt.matches(owner))
                assertFalse("Retiring frames must also retire the cached native gesture endpoint",
                    scope.contentAt(0,0f,preparedContent))
                display.setRotation(Surface.ROTATION_270)
                owner.refreshGeometry()
                assertTrue(scope.landscape)
                assertFalse("Reverse landscape retires the previous window",receipt.matches(owner))
                assertFalse(endpoint.copyTo(Transform()))
                setField(owner,"transition",100L)
                assertTrue(scope.write(0,0f,transform))
                assertTrue("Only a freshly prepared layout may supply gesture endpoints",scope.contentAt(0,0f,preparedContent))
            } finally { display.setRotation(Surface.ROTATION_0) }
        } finally { host.finish() }
    }
}
