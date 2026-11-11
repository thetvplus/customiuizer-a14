package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeckSurfaceOcclusionTest {
    // This exact A14 compat class has sourceContainerBounds/position, not the
    // framework RemoteAnimationTarget's screenSpaceBounds field.
    class Remote(val taskId: Int,val mode: Int,val sourceContainerBounds: Rect,val position: android.graphics.Point)
    class Surface(val surface: Any,val flags: Int,val alpha: Float,val matrix: Matrix,val windowCrop: Rect,
        val layer: Int,val relativeTo: Any,val relativeLayer: Int,val homeSurface: Any,val cornerRadius: Float,
        val backgroundBlurRadius: Int,val visible: Boolean,val shadowRadius: Float,val isShow: Boolean)
    class Helper {
        val mSourceStackBounds=Rect(); val mCurrentRectWithInsets=RectF()
        fun calculateSurfaceParams(a: Remote,b: Remote,c: Remote,d: Any,e: Int,f: Any,g: Any,h: Int,i: Any,j: Boolean): Surface=error("not executed")
    }
    class LegacyHelper {
        val mSourceStackBounds=Rect();val mCurrentRectWithInsets=RectF()
        fun calculateSurfaceParams(a: Remote,b: Remote,c: Remote,d: Any,e: Int,f: Any,g: Any,h: Int,i: Any,j: Boolean,k: Boolean): Surface=error("not executed")
    }
    private fun api()=DeckSurfaceOcclusion(Helper::class.java,Remote::class.java,Any::class.java,Any::class.java,Any::class.java,Surface::class.java)
    @Test fun sameArityWithWrongParametersOrReturnTypeCannotAdmitSurfaceWrites() {
        assertThrows(NoSuchMethodException::class.java) {
            DeckSurfaceOcclusion(Helper::class.java,Any::class.java,Any::class.java,Any::class.java,Any::class.java,Surface::class.java)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeckSurfaceOcclusion(Helper::class.java,Remote::class.java,Any::class.java,Any::class.java,Any::class.java,Any::class.java)
        }
    }
    private fun params()=Surface(Any(),7,.75f,Matrix(),Rect(10,20,1010,2020),11,Any(),13,Any(),19f,23,true,29f,true)
    @Test fun legacyElevenArgumentSurfaceRetainsTheSameCropAndRetirementContract() {
        val api=DeckSurfaceOcclusion(LegacyHelper::class.java,Remote::class.java,Any::class.java,Any::class.java,Any::class.java,Surface::class.java)
        assertEquals(11,api.calculate.parameterCount)
        val helper=LegacyHelper().apply { mSourceStackBounds.set(0,0,1080,2400);mCurrentRectWithInsets.set(10f,20f,1010f,2020f) }
        val presentation=DeckOpenAlignment.Surface(7,1080f,2400f,RectF(0f,0f,1000f,2000f),Matrix(),1f,0f,1f,0f,floatArrayOf(600f,1000f,600f,2000f,1f,0f))
        presentation.update(RectF(0f,0f,1000f,2000f),0f)
        val target=Remote(7,0,Rect(0,0,1080,2400),android.graphics.Point())
        val first=params();assertSame(first,api.apply(presentation,helper,target,0,first))
        assertEquals(300,first.windowCrop.right)
        presentation.active=false
        val retired=params();assertSame(retired,api.apply(presentation,helper,target,0,retired));assertEquals(1010,retired.windowCrop.right)
        assertEquals(29f,retired.shadowRadius,0f)
    }
    @Test fun cropUsesTheNativeBufferTransformAndConservativelyRoundsTheCoveredEdge() {
        val api=api()
        val p=params()
        assertSame(p,api.clip(p,.5f,100f,300.4f))
        assertEquals(Rect(10,20,400,2020),p.windowCrop)
        val next=params()
        assertSame(next,api.clip(next,.5f,100f,2000f))
        assertEquals(Rect(10,20,1010,2020),next.windowCrop)
    }
    @Test fun fullOcclusionCopiesAlphaWithoutAnEmptyCropOrChangingTheNativeSurfaceContract() {
        val api=api()
        val p=params(); val copy=api.clip(p,.5f,100f,104f) as Surface
        assertNotSame(p,copy); assertEquals(0f,copy.alpha,0f); assertEquals(.75f,p.alpha,0f)
        for(field in Surface::class.java.declaredFields) if(field.name!="alpha") {
            field.isAccessible=true; assertEquals(field.name,field.get(p),field.get(copy))
        }
        assertFalse(copy.windowCrop.isEmpty)
    }
    @Test fun transformedAndInvalidCoordinateSystemsCannotBeAdmitted() {
        val v=floatArrayOf(.5f,0f,100f,0f,.5f,200f,0f,0f,1f)
        assertTrue(DeckSurfaceOcclusion.axisAligned(v))
        for(index in intArrayOf(1,3,6,7)) {
            val skew=v.copyOf();skew[index]=.1f;assertFalse(DeckSurfaceOcclusion.axisAligned(skew))
        }
        assertFalse(DeckSurfaceOcclusion.axisAligned(v.copyOf().apply { this[0]=Float.NaN }))
        assertFalse(DeckSurfaceOcclusion.axisAligned(v.copyOf().apply { this[4]=-.5f }))
    }
}
