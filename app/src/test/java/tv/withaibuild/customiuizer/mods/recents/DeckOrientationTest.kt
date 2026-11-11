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
    class Container(context: android.content.Context): FrameLayout(context) {
        @JvmField var mRecentsContainerRotation=1
    }
    // Only the selected native ABI is supplied. No launcher constructor, Hook
    // installation or physical/visual orientation equivalence is simulated.
    open class Algorithm {
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
            setField(api,"recentsRotation",Container::class.java.getField("mRecentsContainerRotation"))
            val container=Container(host)
            for((target,source) in mapOf("window" to "window","visualInsets" to "insets","base" to "base",
                "padding" to "padding","count" to "count","gap" to "gap","headerHeight" to "header"))
                setField(api,target,Algorithm::class.java.getField(source))
            setField(api,"visualLandscape",Algorithm::class.java.getMethod("isLandscapeVisually"))
            setField(api,"minimum",Algorithm::class.java.getField("minimum"))
            setField(api,"minimumScroll",Algorithm::class.java.getMethod("calculateMinScrollP"))
            for(name in listOf("rect","scale","visible","z")) setField(api,name,Transform::class.java.getField(name))
            val owner=unconstructed(DeckOwner::class.java)
            setField(owner,"gestureBoundary",DeckGestureBoundary())
            val stack=FrameLayout(host)
            val frozen=HashMap<Int,Any>()
            val pairs=IdentityHashMap<Any,Any>().apply { put(Any(),Any()) }
            for((name,value) in mapOf("api" to api,"stack" to stack,"container" to container,"headerConfig" to DeckHeaderConfig(0,0),
                "frozen" to frozen,"pairs" to pairs,"cards" to LinkedHashMap<Any,Any>(),
                "drawCards" to ArrayList<Any>(),"linkedSprings" to ArrayList<Any>(),
                "surfaceCards" to emptyArray<DeckSurfaceShadow.CardState?>(),
                "router" to DeckTouchRouter { false },"ticketTimeout" to Runnable {})) setField(owner,name,value)
            setField(owner,"rotation",1)
            setField(owner,"baseAllowed",true);setField(owner,"tasksAllowed",true);setField(owner,"transition",1L)
            val algorithm=Algorithm()
            val scope=owner.Scope(algorithm)
            setField(owner,"current",scope);setField(owner,"stable",scope)
            setField(owner,"geometryReady",true)
            val transform=Transform()
            assertTrue("Portrait host, landscape visual window",scope.write(0,0f,transform))
            val motion=DeckMotion.modern(DeckModernSpringParametersTest.Native::class.java)
            val spring=DeckModernSpringParametersTest.Native()
            motion.mark(spring,owner,DeckSpringScene.OPEN)
            motion.afterConfigure(spring,"OPEN_FROM_RECENTS",false)
            assertEquals("Landscape uses the same iOS scene profile",4,spring.calls.size)
            assertEquals(DeckSpringScene.OPEN.response*spring.durationRatio,spring.mWidthBundle.getStiffness(),0f)
            DeckMotion.clear(spring,owner,owner.transition)
            assertEquals((1040f-600f)*.5f,transform.rect.top,.001f)
            val expectedContent=RectF(transform.rect)
            val contentGeometry=IosDeckGeometry(2280f,1100f,1f)
            contentGeometry.content(expectedContent,
                (XposedHelpers.getAdditionalInstanceField(transform,DeckOwner.FRAME) as DeckFrame).scale,52f)
            val preparedContent=RectF()
            assertTrue(scope.contentAt(0,0f,preparedContent));assertEquals(expectedContent,preparedContent)
            fun assertEndpoint(index: Int,q: Float) {
                assertTrue(scope.write(index,q,transform))
                val displayed=RectF(transform.rect)
                contentGeometry.content(displayed,transform.scale,52f)
                assertTrue(scope.contentAt(index,q,preparedContent))
                assertEquals("Gesture endpoints use the same bounded pose as layout",displayed,preparedContent)
                val nativeContent=RectF(transform.rect).apply { top+=52f }
                scope.content(index,q,nativeContent)
                assertEquals("Window queries use that same bounded pose",displayed,nativeContent)
            }
            assertEndpoint(0,-10f*scope.gap)
            assertEndpoint(3,10f*scope.gap)
            assertEquals("The real window queries populate endpoints retired by rotation",2,frozen.size)
            assertTrue(scope.write(0,0f,transform))
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
            container.mRecentsContainerRotation=0
            assertSame(failure,assertThrows(IllegalStateException::class.java) { scope.refreshOrientation() })
            assertTrue(frozen.isEmpty());assertTrue(pairs.isEmpty())
            assertEquals(0L,owner.transition)
            assertNotEquals(oldFrame.generation,owner.geometryGeneration)
            assertFalse(scope.landscape)
            owner.rotationChanged(0)
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
            spring.calls.clear()
            motion.mark(spring,owner,DeckSpringScene.OPEN)
            motion.afterConfigure(spring,"OPEN_FROM_RECENTS",false)
            assertEquals(4,spring.calls.size)
            // Reconfigure restores the native baseline before selecting the
            // next iOS scene, in either direction.
            motion.beforeConfigure(spring)
            assertEquals(200f,spring.mWidthBundle.getStiffness(),0f)
            DeckMotion.clear(spring,owner,owner.transition)
            repeat(8) { turn ->
                val cached=Transform()
                assertTrue((XposedHelpers.getAdditionalInstanceField(transform,DeckOwner.FRAME) as DeckFrame).copyTo(cached))
                val previous=XposedHelpers.getAdditionalInstanceField(cached,DeckOwner.FRAME) as DeckFrame
                val receipt=IosRecentsDeckHooks.WindowReceipt(owner,owner.transition)
                algorithm.visual=turn%2==0
                container.mRecentsContainerRotation=if(algorithm.visual) 1 else 0
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
                repeat(3) { direction ->
                    for(step in -40..40) for(index in 0..3) {
                        val q=(if(direction%2==0) step else -step)*scope.gap*.25f
                        assertEndpoint(index,q)
                    }
                }
                assertFalse(previous.copyTo(Transform()))
            }
            // The two landscape directions share width/height and the visual
            // boolean, but an old window receipt still belongs to different axes.
            setField(api,"recents",Algorithm::class.java)
            host.setContentView(stack)
            val display=shadowOf(stack.display)
            try {
                display.setRotation(Surface.ROTATION_90)
                container.mRecentsContainerRotation=1
                algorithm.visual=true;algorithm.window.set(0,0,2400,1080)
                owner.refreshGeometry()
                assertEquals(android.content.res.Configuration.ORIENTATION_PORTRAIT,stack.resources.configuration.orientation)
                val ticket=owner.beginTransition(true)
                assertTrue("A fixed portrait Window admits its prepared native landscape viewport",ticket!=0L)
                owner.endTransition(ticket)
                owner.refreshGeometry()
                setField(owner,"transition",99L)
                assertTrue(scope.write(0,0f,transform))
                assertTrue(scope.contentAt(0,0f,preparedContent))
                val receipt=IosRecentsDeckHooks.WindowReceipt(owner,99L)
                val endpoint=DeckFrame(owner,RectF(0f,0f,1100f,600f),0,1f,true,1f,1f)
                assertTrue(receipt.matches(owner))
                owner.rotationChanged(Surface.ROTATION_270)
                container.mRecentsContainerRotation=3
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
