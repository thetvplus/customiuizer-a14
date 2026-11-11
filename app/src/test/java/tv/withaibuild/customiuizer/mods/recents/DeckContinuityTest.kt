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
    class Key { @JvmField var id=7 }
    class Task { @JvmField val key=Key() }
    class Header : FrameLayout(RuntimeEnvironment.getApplication()) {
        @JvmField val icon=View(context)
        @JvmField val title=TextView(context)
        @JvmField val badge=View(context)
    }
    class Thumbnail : View(RuntimeEnvironment.getApplication()) { @JvmField var radius=22 }
    class RootSprings(view: View) {
        private val x=androidx.dynamicanimation.animation.SpringAnimation(view,androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X)
        private val y=androidx.dynamicanimation.animation.SpringAnimation(view,androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y)
        private val sx=androidx.dynamicanimation.animation.SpringAnimation(view,androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X)
        private val sy=androidx.dynamicanimation.animation.SpringAnimation(view,androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y)
        private val alpha=androidx.dynamicanimation.animation.SpringAnimation(view,androidx.dynamicanimation.animation.DynamicAnimation.ALPHA)
        fun getTranslationXSpringAnim()=x
        fun getTranslationYSpringAnim()=y
        fun getScaleXSpringAnim()=sx
        fun getScaleYSpringAnim()=sy
        private val all by lazy { listOf(x,y,sx,sy,alpha) }
        fun getAllSpringAnim()=all
    }
    class TaskView : FrameLayout(RuntimeEnvironment.getApplication()) {
        val task=Task()
        @JvmField var mFullscreenProgress=0f
        val header=Header()
        val thumbnail=Thumbnail()
        private val springs by lazy { RootSprings(this) }
        var headerReads=0
        fun getHeaderView(): View { headerReads++; return header }
        fun getThumbnailView(): View=thumbnail
        fun getSpringAnimationImpl()=springs
    }
    class NativeStack : FrameLayout(RuntimeEnvironment.getApplication()) {
        fun nativeUpdate(view: TaskView,out: Transform,ignoredTiming: Any) {
            view.layout(out.rect.left.toInt(),out.rect.top.toInt(),out.rect.right.toInt(),out.rect.bottom.toInt())
        }
    }
    class RebaseAlgorithm : DeckOrientationTest.Algorithm() {
        internal lateinit var scope: DeckOwner.Scope
        fun nativeTransform(task: Task,q: Float,out: Transform): Transform {
            scope.write(task.key.id,q,out);return out
        }
    }
    class Handler {
        @JvmField val tasks=ArrayList<Any>()
        @JvmField val start=ArrayList<Transform>()
        @JvmField val end=ArrayList<Transform>()
    }
    class Visual { fun isLandscapeVisually()=true }
    class Scroller { @JvmField var scroll=0f }
    class Gesture {
        @JvmField var progress=.3f
        @JvmField var windowMode=2
        fun quickSwitch()=false
        fun pose()=Unit
    }
    class OpeningSpring {
        val start=RectF(100f,200f,700f,1400f)
        var reads=0
        fun getStartRect(): RectF { reads++;return start }
        fun startInGestureThread()=Unit
    }
    class Recents : FrameLayout(RuntimeEnvironment.getApplication()) {
        @JvmField var overview=true
        @JvmField var split=false
    }
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
        setField(owner,"gestureBoundary",DeckGestureBoundary())
        val handler=Handler()
        val stack=NativeStack()
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
    fun legacyOpeningHandsTheSettlingCardSpringsOverOnceBeforeCapturingTheirPose() {
        val f=fixture();val api=getField<DeckNativeApi>(f.owner,"api")
        setField(api,"profile",DeckLauncherProfile.LEGACY);setField(api,"cardSprings",DeckCardSpring(TaskView::class.java))
        setField(api,"springStart",OpeningSpring::class.java.getMethod("getStartRect"))
        setField(api,"visualLandscape",Visual::class.java.getMethod("isLandscapeVisually"))
        setField(f.owner,"current",f.owner.Scope(Visual()))
        val view=TaskView();f.stack.addView(view);view.layout(100,200,700,1400)
        val header=DeckHeaderOwner(view.header,view.header.icon,view.header.title,view.header.badge,DeckHeaderConfig(0,0))
        val geometry=DeckCardGeometry(view,f.stack).apply { apply(.8f) }
        val card=DeckOwner.Card(f.owner,geometry,view.task,7,0,header,1f,DeckCardShadow(view,view.header,22f,view.thumbnail,0f))
        f.cards[view]=card;setField(f.owner,"drawCards",arrayListOf(card))
        XposedHelpers.setAdditionalInstanceField(view,DeckOwner.CARD,card)
        val moving=view.getSpringAnimationImpl().getAllSpringAnim()
        val spring=OpeningSpring();val params=Any()
        var calls=0
        val callback=object: io.github.libxposed.api.XposedInterface.Chain {
            override fun getExecutable()=OpeningSpring::class.java.getMethod("startInGestureThread")
            override fun getThisObject()=spring
            override fun getArg(index: Int): Any?=throw AssertionError("No native start arguments")
            override fun getArgs(): List<Any?> = throw AssertionError("No argument array")
            override fun proceed(): Any? { calls++;return null }
            override fun proceed(updated: Array<Any?>): Any?=throw AssertionError("No remarshal")
            override fun proceedWith(receiver: Any): Any?=throw AssertionError("Native receiver changed")
            override fun proceedWith(receiver: Any,updated: Array<Any?>): Any?=throw AssertionError("Native receiver changed")
        }
        try {
            for(spring in moving) { spring.spring=androidx.dynamicanimation.animation.SpringForce(1f);spring.start() }
            IosRecentsDeckHooks.legacyOpeningCreated(card,params,spring,skip=true)
            IosRecentsDeckHooks.legacySpringStartHook(api).intercept(callback)
            assertTrue("Native skipViewChanges cannot acquire the card pose",moving.all { it.isRunning })
            assertEquals(0,spring.reads)
            IosRecentsDeckHooks.legacyOpeningCreated(card,params,spring,skip=false)
            assertEquals("Creation precedes native BreakableAnimManager's start rewrite",0,spring.reads)
            spring.start.set(80f,130f,720f,1510f)
            IosRecentsDeckHooks.legacySpringStartHook(api).intercept(callback)
            val alignment=getField<DeckOpenAlignment>(f.owner,"opening")
            assertEquals("Capture uses the final native continuation pose",spring.start,getField<RectF>(alignment,"start"))
            assertEquals(1,spring.reads)
            assertTrue("The old card clock cannot overwrite opening alignment",moving.none { it.isRunning })
            moving[0].start()
            IosRecentsDeckHooks.legacySpringStartHook(api).intercept(callback)
            assertTrue("Preparing another frame does not repeatedly cancel native work",moving[0].isRunning)
            assertEquals(1,spring.reads);assertEquals(3,calls)
        } finally {
            moving.forEach { it.cancel() }
            org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(f.owner,"clearOpening")
            card.release()
            XposedHelpers.removeAdditionalInstanceField(view,DeckOwner.CARD)
        }
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun aRunningNativeSpringCannotWriteItsOldCoordinatesBackOnTheFirstFrameAfterRebase() {
        val f=fixture();val api=getField<DeckNativeApi>(f.owner,"api")
        val algorithm=RebaseAlgorithm()
        for((target,source) in mapOf("window" to "window","visualInsets" to "insets","base" to "base",
            "padding" to "padding","count" to "count","gap" to "gap","headerHeight" to "header"))
            setField(api,target,algorithm.javaClass.getField(source))
        setField(api,"visualLandscape",algorithm.javaClass.getMethod("isLandscapeVisually"))
        setField(api,"scroll",Scroller::class.java.getField("scroll"));val scroller=Scroller()
        setField(f.owner,"scroller",scroller);setField(f.owner,"headerConfig",DeckHeaderConfig(0,0))
        val scope=f.owner.Scope(algorithm);algorithm.scope=scope;setField(f.owner,"current",scope)
        setField(api,"taskTransform",algorithm.javaClass.getMethod("nativeTransform",Task::class.java,Float::class.javaPrimitiveType,Transform::class.java))
        setField(api,"updateView",NativeStack::class.java.getMethod("nativeUpdate",TaskView::class.java,Transform::class.java,Any::class.java))
        setField(api,"immediate",Any());setField(f.owner,"restoreTransform",Transform())
        setField(api,"cardSprings",DeckCardSpring(TaskView::class.java))
        val transform=Transform();assertTrue(scope.write(0,0f,transform))
        val view=TaskView();view.task.key.id=0;f.stack.addView(view)
        view.layout(transform.rect.left.toInt(),transform.rect.top.toInt(),transform.rect.right.toInt(),transform.rect.bottom.toInt())
        view.header.layout(0,0,view.width,52)
        val header=DeckHeaderOwner(view.header,view.header.icon,view.header.title,view.header.badge,DeckHeaderConfig(0,0))
        val geometry=DeckCardGeometry(view,f.stack).apply { apply(transform.scale) }
        val shadow=DeckCardShadow(view,view.header,22f,view.thumbnail,0f)
        val card=DeckOwner.Card(f.owner,geometry,view.task,0,0,header,1f,shadow)
        f.cards[view]=card;setField(f.owner,"drawCards",arrayListOf(card))
        XposedHelpers.setAdditionalInstanceField(f.stack,DeckOwner.OWNER,f.owner)
        val springs=view.getSpringAnimationImpl()
        val moving=listOf(springs.getScaleXSpringAnim(),springs.getScaleYSpringAnim(),springs.getTranslationYSpringAnim())
        val starts=floatArrayOf(.61f,.67f,-132f);val speeds=floatArrayOf(-.7f,-.9f,-30f)
        val before=RectF();val after=RectF()
        try {
            for(i in moving.indices) {
                moving[i].spring=androidx.dynamicanimation.animation.SpringForce(if(i==2) -200f else .55f)
                moving[i].setStartValue(starts[i]);moving[i].setStartVelocity(speeds[i]);moving[i].start()
                moving[i].doAnimationFrame(1000L)
            }
            geometry.localContent(before,52f)
            val oldMatrixScale=geometry.scale
            assertTrue(f.owner.prepareScrollRebase())
            scroller.scroll=-scope.gap;view.translationX-=615f // original native rebase
            f.owner.finishScrollRebase(f.owner.transition,f.owner.geometryGeneration)
            geometry.localContent(after,52f)
            assertEquals(before.left,after.left,.003f);assertEquals(before.top,after.top,.003f)
            val startX=view.scaleX;val startY=view.scaleY
            assertEquals(speeds[0]*startX/starts[0],getField<Float>(moving[0],"mVelocity"),.00001f)
            assertEquals(speeds[1]*startY/starts[1],getField<Float>(moving[1],"mVelocity"),.00001f)
            assertEquals(speeds[2]*oldMatrixScale/geometry.scale,getField<Float>(moving[2],"mVelocity"),.0001f)
            // Native resetTaskView retargets running axes without restarting them.
            for(i in moving.indices) {
                moving[i].spring!!.finalPosition=if(i==2) 0f else 1f
                moving[i].doAnimationFrame(1000L) // first native frame, no elapsed time
                assertTrue(moving[i].isRunning)
            }
            geometry.localContent(after,52f)
            assertEquals("Native spring cache must use the rebased X scale",startX,view.scaleX,.00001f)
            assertEquals("Native spring cache must use the rebased Y scale",startY,view.scaleY,.00001f)
            assertEquals(before.top,after.top,.003f);assertEquals(before.right,after.right,.003f)
        } finally {
            moving.forEach { it.cancel() };XposedHelpers.removeAdditionalInstanceField(f.stack,DeckOwner.OWNER);card.release()
        }
    }
    @Test fun scrollRebasePreservesNativeFailureIdentityAndRejectsARetiredGeometry() {
        for(failed in listOf(false,true)) {
            val f=fixture();val view=TaskView();f.stack.addView(view);view.layout(0,0,600,1200)
            val geometry=DeckCardGeometry(view,f.stack)
            val card=unconstructed(DeckOwner.Card::class.java)
            val header=DeckHeaderOwner(view.header,view.header.icon,view.header.title,view.header.badge,DeckHeaderConfig(0,0))
            setField(card,"geometry",geometry);setField(card,"header",header);setField(card,"windowInput",RectF())
            setField(f.owner,"drawCards",arrayListOf(card))
            XposedHelpers.setAdditionalInstanceField(f.stack,DeckOwner.OWNER,f.owner)
            val failure=IllegalStateException("native rebase failed");val result=Any();var calls=0
            val callback=object: io.github.libxposed.api.XposedInterface.Chain {
                override fun getExecutable()=Gesture::class.java.getMethod("pose")
                override fun getThisObject()=f.stack
                override fun getArg(index: Int): Any?=throw AssertionError("No input rewrite")
                override fun getArgs(): List<Any?> = throw AssertionError("No input remarshal")
                override fun proceed(): Any { calls++;if(failed)throw failure;setField(f.owner,"geometryGeneration",1L);return result }
                override fun proceed(args: Array<Any?>): Any=throw AssertionError("No input remarshal")
                override fun proceedWith(receiver: Any): Any=throw AssertionError("Native receiver changed")
                override fun proceedWith(receiver: Any,args: Array<Any?>): Any=throw AssertionError("Native receiver changed")
            }
            try {
                val observed=runCatching { IosRecentsDeckHooks.scrollRebaseHook().intercept(callback) }
                if(failed) assertSame(failure,observed.exceptionOrNull()) else assertSame(result,observed.getOrThrow())
                assertEquals(1,calls)
                assertEquals(0f,view.translationX,0f);assertEquals(1f,view.scaleX,0f)
            } finally { XposedHelpers.removeAdditionalInstanceField(f.stack,DeckOwner.OWNER);header.close();geometry.release() }
        }
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun aNewlyVisibleCardStartsAtItsDeckScaleBeforeTheFirstNativeComposition() {
        val f=fixture();val api=getField<DeckNativeApi>(f.owner,"api")
        setField(api,"getThumbnail",TaskView::class.java.getMethod("getThumbnailView"))
        setField(api,"thumbnailRadius",Thumbnail::class.java.getField("radius"))
        setField(api,"key",Task::class.java.getField("key"));setField(api,"id",Key::class.java.getField("id"))
        for((target,source) in mapOf("headerIcon" to "icon","headerTitle" to "title","headerBadge" to "badge"))
            setField(api,target,Header::class.java.getField(source))
        setField(api,"visualLandscape",Visual::class.java.getMethod("isLandscapeVisually"))
        setField(f.owner,"current",f.owner.Scope(Visual()));setField(f.owner,"headerConfig",DeckHeaderConfig(0,0))
        val view=TaskView();f.stack.addView(view);view.layout(0,0,800,1400)
        val transform=Transform();f.owner.putFrame(transform,3,.72f,true,.25f,1f)
        val frame=XposedHelpers.getAdditionalInstanceField(transform,DeckOwner.FRAME) as DeckFrame
        val card=requireNotNull(f.owner.card(view,frame))
        try {
            assertEquals("A refill card has no previous full-size Deck pose to animate from",.72f,card.geometry.scale,0f)
            assertNotNull(view.animationMatrix)
            assertNull(card.animation)
        } finally { f.owner.remove(view) }
    }
    @Test fun releasePoseKeepsTheNativeSpringFlagAndLeavesTheHiddenSnapshotUnshadowed() {
        verifyGesturePose(reference=0, windowMode=2, admitted=true)
    }

    @Test fun homeGestureUsesTheDeckPoseForTheVirtualPage() {
        verifyGesturePose(reference=-1, windowMode=1, admitted=true)
    }

    @Test fun missingRunningTaskInAnAppGestureKeepsTheNativePose() {
        verifyGesturePose(reference=-1, windowMode=2, admitted=false)
    }

    private fun verifyGesturePose(reference: Int, windowMode: Int, admitted: Boolean) {
        for(profile in DeckLauncherProfile.entries) for(animate in listOf(false,true)) for(index in 0..1) {
            val f=fixture();val api=getField<DeckNativeApi>(f.owner,"api")
            val algorithm=DeckOrientationTest.Algorithm()
            for((target,source) in mapOf("window" to "window","visualInsets" to "insets","base" to "base",
                "padding" to "padding","count" to "count","gap" to "gap","headerHeight" to "header"))
                setField(api,target,algorithm.javaClass.getField(source))
            setField(api,"visualLandscape",algorithm.javaClass.getMethod("isLandscapeVisually"))
            setField(api,"profile",profile)
            setField(api,"gestureProgress",Gesture::class.java.getField("progress"))
            setField(api,"gestureQuickSwitch",Gesture::class.java.getMethod("quickSwitch"))
            setField(api,"navWindowMode",Gesture::class.java.getField("windowMode"))
            setField(api,"scroll",Scroller::class.java.getField("scroll"))
            setField(f.owner,"scroller",Scroller());setField(f.owner,"headerConfig",DeckHeaderConfig(0,0))
            val scope=f.owner.Scope(algorithm);setField(f.owner,"current",scope)
            assertTrue(scope.write(0,0f,Transform()))
            val view=TaskView();f.stack.addView(view);view.layout(500,200,1600,800)
            view.addView(view.thumbnail);view.thumbnail.layout(0,52,1100,600);view.thumbnail.alpha=0f
            val geometry=DeckCardGeometry(view,f.stack).apply { apply(.8f) }
            val shadow=DeckCardShadow(view,view.header,22f,view.thumbnail,1f)
            val card=unconstructed(DeckOwner.Card::class.java)
            setField(card,"owner",f.owner);setField(card,"geometry",geometry);setField(card,"index",index)
            setField(card,"shadow",shadow)
            setField(card,"windowInput",RectF())
            XposedHelpers.setAdditionalInstanceField(view,DeckOwner.CARD,card)
            val completion=Runnable {}
            val endpoint=RectF().also { assertTrue(scope.contentAt(index,0f,it)) }
            val nativeWindow=RectF().apply {
                assertTrue(scope.contentAt(0,0f,this))
                if(reference==-1) offset(width()+25f,0f)
            }
            val originalWindow=RectF(nativeWindow)
            val args=arrayOf<Any?>(view,index,nativeWindow,reference,0,25f,1f,animate,.9f,.5f,completion,0f,0f,false,0,.3f,windowMode,android.graphics.Rect())
            val gesture=Gesture().apply { this.windowMode=windowMode }
            var calls=0
            val callback=object: io.github.libxposed.api.XposedInterface.Chain {
                override fun getExecutable()=Gesture::class.java.getMethod("pose")
                override fun getThisObject()=gesture
                override fun getArg(index: Int)=args[index]
                override fun getArgs(): List<Any?> = args.toList()
                override fun proceed(): Any? { calls++;return null }
                override fun proceed(updated: Array<Any?>): Any? { updated.copyInto(args);return proceed() }
                override fun proceedWith(receiver: Any): Any?=throw AssertionError("Native receiver changed")
                override fun proceedWith(receiver: Any,updated: Array<Any?>): Any?=throw AssertionError("Native receiver changed")
            }
            try {
                IosRecentsDeckHooks.gesturePoseHook(api).intercept(callback)
                assertEquals(1,calls)
                if(admitted) {
                    assertNotSame("The native pose must consume the Deck input for $profile",nativeWindow,args[2])
                    val input=args[2] as RectF
                    val nativeCenterX=input.centerX()+(reference-index)*(input.width()+25f)
                    val nativeCenterY=input.top+300f*.3f*.3f*.3f+(view.height-algorithm.header)*input.width()/view.width*.5f
                    val frameCenterX=view.left+view.width*.5f
                    val frameCenterY=view.top+view.height*.5f
                    assertEquals(endpoint.centerX(),frameCenterX+(nativeCenterX-frameCenterX)*geometry.scale,.001f)
                    assertEquals(endpoint.centerY(),frameCenterY+(nativeCenterY-frameCenterY)*geometry.scale,.001f)
                    assertEquals(endpoint.width(),input.width()*geometry.scale,.001f)
                    val outline=android.graphics.Outline()
                    shadow.getOutline(view,outline)
                    assertEquals("An app leash alone cannot cast a shadow from the hidden card",0f,outline.alpha,0f)
                } else assertSame(nativeWindow,args[2])
                assertEquals("Release must start the native card spring instead of snapping its endpoint",animate,args[7])
                assertSame(completion,args[10])
                assertEquals(originalWindow,nativeWindow)
            } finally { XposedHelpers.removeAdditionalInstanceField(view,DeckOwner.CARD);geometry.release();shadow.close() }
        }
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
                // The first Legacy frame can already fade its neighbors. Capture
                // the true presentation before that native UI callback executes.
                XposedHelpers.setAdditionalInstanceField(selected.view,DeckOwner.CARD,selected)
                try {
                    val args=arrayOf<Any?>(selected.view,0f,true,3,Any(),start,start)
                    val callback=object: io.github.libxposed.api.XposedInterface.Chain {
                        override fun getExecutable()=DeckContinuityTest::class.java.getDeclaredMethod("fixedPortraitWindowKeepsOpeningAndOcclusionInTheVisualTaskSpaceInBothLandscapeDirections")
                        override fun getThisObject(): Any?=null
                        override fun getArg(index: Int)=args[index]
                        override fun getArgs(): List<Any?> = throw AssertionError("No frame argument list")
                        override fun proceed(): Any? { neighbor.view.alpha=.4f;return null }
                        override fun proceed(args: Array<Any?>): Any?=throw AssertionError("No frame remarshal")
                        override fun proceedWith(receiver: Any): Any?=throw AssertionError("Native receiver changed")
                        override fun proceedWith(receiver: Any,args: Array<Any?>): Any?=throw AssertionError("Native owner changed")
                    }
                    IosRecentsDeckHooks.legacyOpeningHook().intercept(callback)
                    assertEquals("The first native fade cannot seed the Deck presentation",1f,neighbor.view.alpha,0f)
                } finally { XposedHelpers.removeAdditionalInstanceField(selected.view,DeckOwner.CARD) }
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
                assertEquals("Window alignment reuses the bound header",0,(neighbor.view as TaskView).headerReads)
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
    @Test fun backgroundGeometryQueriesCannotCreateADeckWindowIntent() {
        val f=fixture()
        setField(f.owner,"transition",0L)
        val api=getField<DeckNativeApi>(f.owner,"api")
        setField(api,"recents",Recents::class.java)
        setField(api,"overview",Recents::class.java.getField("overview"))
        setField(api,"split",Recents::class.java.getField("split"))
        Recents().addView(f.stack)
        // The audited Launcher initializes mInOverviewState to true even while
        // its shared task container is used by other window consumers.
        assertTrue(f.owner.allowed)
        repeat(8) {
            assertEquals("A shared task-rectangle query is not a Deck entry",0L,f.owner.beginTransition())
            assertEquals(0L,f.owner.transition)
        }
        val ticket=f.owner.beginTransition(true)
        try { assertTrue("An explicit app-to-Deck intent can still start",ticket!=0L) }
        finally { f.owner.endTransition(ticket) }
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun quickSwitchRetiresDeckPresentationAndBlocksReplacementOwnersUntilTheNextDeckIntent() {
        val f=fixture();val replacement=fixture()
        setField(f.owner,"sequence",1L)
        val boundary=DeckGestureBoundary()
        setField(f.owner,"gestureBoundary",boundary);setField(replacement.owner,"gestureBoundary",boundary)
        for(active in listOf(f,replacement)) {
            val bridge=getField<DeckNativeApi>(active.owner,"api")
            setField(bridge,"recents",Recents::class.java)
            setField(bridge,"overview",Recents::class.java.getField("overview"))
            setField(bridge,"split",Recents::class.java.getField("split"))
            Recents().addView(active.stack)
        }
        setField(f.owner,"callbacks",android.os.Handler(android.os.Looper.getMainLooper()))
        val api=getField<DeckNativeApi>(f.owner,"api")
        val algorithm=DeckOrientationTest.Algorithm()
        for((target,source) in mapOf("base" to "base","window" to "window","visualInsets" to "insets","minimum" to "minimum"))
            setField(api,target,DeckOrientationTest.Algorithm::class.java.getField(source))
        setField(api,"visualLandscape",DeckOrientationTest.Algorithm::class.java.getMethod("isLandscapeVisually"))
        setField(api,"minimumScroll",DeckOrientationTest.Algorithm::class.java.getMethod("calculateMinScrollP"))
        val scope=f.owner.Scope(algorithm)
        setField(f.owner,"current",scope);setField(f.owner,"stable",scope)
        val view=TaskView();f.stack.addView(view);view.layout(0,0,800,1500)
        view.addView(view.header)
        val icon=View(view.context);val title=TextView(view.context);val badge=View(view.context)
        for(child in arrayOf(icon,title,badge)) view.header.addView(child)
        val header=DeckHeaderOwner(view.header,icon,title,badge,DeckHeaderConfig(0,0))
        val geometry=DeckCardGeometry(view,f.stack).apply { apply(.75f) }
        assertNotNull("The old Deck matrix is present before quick-switch admission changes",view.animationMatrix)
        val shadow=DeckCardShadow(view,view.header,22f,view,0f)
        // The pool may already have rebound this View. Native properties belong
        // to the new task and must survive release of the old Deck matrix.
        val card=DeckOwner.Card(f.owner,geometry,Any(),0,0,header,1f,shadow)
        f.cards[view]=card
        val old=IosRecentsDeckHooks.WindowReceipt(f.owner,1L)
        view.scaleX=.93f;view.scaleY=.91f;view.translationX=37f
        try {
            boundary.quickSwitching=true
            assertFalse(f.owner.allowed);assertFalse(replacement.owner.allowed)
            assertEquals(0L,f.owner.beginTransition(true));assertEquals(0L,replacement.owner.beginTransition(true))
            assertFalse(old.matches(f.owner))
            f.owner.gestureChanged()
            assertEquals(0L,f.owner.transition);assertTrue(f.cards.isEmpty())
            assertNull(view.animationMatrix)
            assertEquals(.93f,view.scaleX,0f);assertEquals(.91f,view.scaleY,0f);assertEquals(37f,view.translationX,0f)
            assertEquals(1L,f.owner.geometryGeneration)
            repeat(4) { assertEquals(0L,f.owner.beginTransition()) }
            boundary.quickSwitching=false
            setField(f.owner,"geometryReady",true)
            val next=f.owner.beginTransition(true)
            assertTrue(next!=0L);assertTrue(f.owner.allowed)
            assertFalse("A quick-switch completion cannot revive the previous receipt",old.matches(f.owner))
        } finally {
            setField(f.owner,"closed",true);f.owner.endTransition(f.owner.transition)
            card.release();f.cards.clear()
        }
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
    @Test fun queuedGestureBoundaryCannotRetireAClosedStack() {
        val f=fixture()
        setField(f.owner,"gestureBoundary",DeckGestureBoundary().apply { quickSwitching=true })
        setField(f.owner,"callbacks",android.os.Handler(android.os.Looper.getMainLooper()))
        val thread=Thread { f.owner.gestureChanged() };thread.start();thread.join()
        setField(f.owner,"closed",true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(0L,f.owner.geometryGeneration)
        assertEquals(1L,f.owner.transition)
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
