package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeckWindowApiTest {
    enum class Type { OPEN_FROM_RECENTS,CLOSE_TO_RECENTS,CLOSE_TO_HOME }
    class Params(val targetApps: Any?,val startRect: RectF,val targetRect: RectF,
        val startRadius: Float,val endRadius: Float,val startAlpha: Float,val endAlpha: Float,
        val animType: Type,val ignoreIcon: Boolean,val showTask: Boolean,val isQuickSwitchMode: Boolean,
        val targetView: View?,val animListener: Any?,val clipAnimationHelper: Any?,val clearLastListener: Boolean,
        val currentDisplayRotation: Int,val homeRotation: Int,val needFinishOnAnimEnd: Boolean,
        val currentTaskIndex: Int,val isSplitModeBack: Boolean,val isUseTranslucentAnim: Boolean)
    class Element {
        @JvmField var currentRectFParams: Params?=null
        @JvmField val mAnim=Spring()
        fun animTo(value: Any) { currentRectFParams=value as Params }
        fun updateElementValue(a: Float,b: RectF,c: Float,d: Float,e: Boolean,f: android.graphics.Rect,g: RectF) {}
    }
    class Spring { fun getStartRect()=RectF(1f,2f,3f,4f) }
    class Provider { fun getClosingWindowToRecentAnimParams(a: Any,b: Any,c: Any): Any=a }
    class StaticElement { companion object { @JvmStatic fun animTo(value: Any) {} } }
    private val api=DeckWindowApi(Element::class.java,Params::class.java,Provider::class.java,
        Any::class.java,Any::class.java,Any::class.java)
    private fun params(type: Type,rotation: Int=0,split: Boolean=false,homeRotation: Int=0)=Params(Any(),RectF(1f,2f,3f,4f),
        RectF(10f,20f,30f,40f),12f,18f,.2f,.9f,type,true,false,false,null,Any(),Any(),false,
        rotation,homeRotation,true,7,split,true)
    @Test fun newWindowCopiesOnlyTheCorrespondingCardRadiusAndPreservesItsNativeOwners() {
        for(type in arrayOf(Type.OPEN_FROM_RECENTS,Type.CLOSE_TO_RECENTS)) {
            val original=params(type); val opening=api.scene(original)!!
            val copy=api.scaled(original,.8f,opening) as Params
            assertNotSame(original,copy)
            assertEquals(if(opening) 9.6f else 12f,copy.startRadius,.001f)
            assertEquals(if(opening) 18f else 14.4f,copy.endRadius,.001f)
            assertEquals(12f,original.startRadius,0f); assertEquals(18f,original.endRadius,0f)
            assertSame(original.targetApps,copy.targetApps); assertSame(original.animListener,copy.animListener)
            assertSame(original.clipAnimationHelper,copy.clipAnimationHelper)
            assertSame(original.startRect,copy.startRect); assertSame(original.targetRect,copy.targetRect)
            assertEquals(original.startAlpha,copy.startAlpha,0f); assertEquals(original.endAlpha,copy.endAlpha,0f)
            assertEquals(original.currentTaskIndex,api.index(copy)); assertTrue(copy.needFinishOnAnimEnd)
            val element=Element(); assertFalse(api.accepted(copy,element))
            element.animTo(copy); assertTrue(api.accepted(copy,element)); assertSame(element.mAnim,api.spring(element))
        }
    }
    @Test fun otherWindowFlowsStayNative() {
        assertNull(api.scene(params(Type.CLOSE_TO_HOME)))
        assertNull(api.scene(params(Type.CLOSE_TO_RECENTS,rotation=2)))
        assertNull(api.scene(params(Type.CLOSE_TO_RECENTS,split=true)))
    }
    @Test fun alignedLandscapeWindowsUseTheSameHandoffAsPortrait() {
        for(rotation in listOf(0,1,3)) for(type in listOf(Type.OPEN_FROM_RECENTS,Type.CLOSE_TO_RECENTS)) {
            val original=params(type,rotation=rotation,homeRotation=rotation)
            val opening=type==Type.OPEN_FROM_RECENTS
            assertEquals("Aligned native window at rotation $rotation",opening,api.scene(original))
            val scaled=api.scaled(original,1.25f,opening) as Params
            assertEquals(rotation,scaled.currentDisplayRotation)
            assertEquals(rotation,scaled.homeRotation)
            assertSame(original.animListener,scaled.animListener)
            assertSame(original.clipAnimationHelper,scaled.clipAnimationHelper)
        }
        for(display in listOf(0,1,3)) for(home in listOf(0,1,3))
            assertEquals("Native default home metadata cannot reject visual landscape",true,
                api.scene(params(Type.OPEN_FROM_RECENTS,rotation=display,homeRotation=home)))
        for(display in listOf(2,-1,4))
            assertNull("Unsupported display rotations stay native",api.scene(params(Type.OPEN_FROM_RECENTS,rotation=display)))
    }
    @Test fun aMatchingNameAndSignatureCannotAdmitAStaticWindowConsumer() {
        assertThrows(IllegalArgumentException::class.java) {
            DeckWindowApi(StaticElement::class.java,Params::class.java,Provider::class.java,
                Any::class.java,Any::class.java,Any::class.java)
        }
    }
    @Test fun optionalMotionFailureRetainsNativeTimingWithoutHidingFatalErrors() {
        val native = Any()
        assertSame(native, IosRecentsDeckHooks.optionalMotion { native })
        assertNull(IosRecentsDeckHooks.optionalMotion<Any> { throw NoSuchMethodException("older spring setter") })
        val fatal = OutOfMemoryError("fixture")
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            IosRecentsDeckHooks.optionalMotion<Any> { throw java.lang.reflect.InvocationTargetException(fatal) }
        })
    }
    @Test fun closingUsesEndpointScaleWhileOpeningIncludesTheCurrentNativeGestureScale() {
        val close=api.scaled(params(Type.CLOSE_TO_RECENTS),1.35f,false,.8f) as Params
        assertEquals(18f*1.35f,close.endRadius,.00001f)
        val open=api.scaled(params(Type.OPEN_FROM_RECENTS),1.35f,true,.8f) as Params
        assertEquals(12f*1.35f*.8f,open.startRadius,.00001f)
    }
}
