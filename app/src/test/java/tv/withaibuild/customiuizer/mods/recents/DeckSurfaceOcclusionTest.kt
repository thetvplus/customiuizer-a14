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
