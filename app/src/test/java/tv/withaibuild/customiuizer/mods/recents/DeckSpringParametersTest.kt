package tv.withaibuild.customiuizer.mods.recents

import org.junit.Assert.*
import org.junit.Test

class DeckSpringParametersTest {
    class OldNative {
        class Property(name: String) : androidx.dynamicanimation.animation.FloatPropertyCompat<OldNative>(name) {
            override fun getValue(obj: OldNative)=0f
            override fun setValue(obj: OldNative,value: Float) {}
        }
        companion object {
            @JvmField val RECT_CENTERX=Property("x")
            @JvmField val RECT_CENTERY=Property("y")
            @JvmField val RECT_WIDTH=Property("width")
            @JvmField val RECT_RATIO=Property("ratio")
        }
        @JvmField var mCenterXDamping=.8f
        @JvmField var mCenterYDamping=.8f
        @JvmField var mWidthDamping=.8f
        @JvmField var mRatioDamping=.8f
        @JvmField var mCenterXStiffness=200f
        @JvmField var mCenterYStiffness=200f
        @JvmField var mWidthStiffness=200f
        @JvmField var mRatioStiffness=400f
        var failAt: Property?=null
        val calls=mutableListOf<androidx.dynamicanimation.animation.FloatPropertyCompat<OldNative>>()
        private fun setAnimParam(axis: androidx.dynamicanimation.animation.FloatPropertyCompat<OldNative>,damping: Float,response: Float) {
            calls+=axis
            if(axis===failAt) error("native setter failure")
            // A spy confirms delegation to native conversion, not its formula.
            val converted=response*1.5f
            when(axis) {
                RECT_CENTERX -> { mCenterXDamping=damping; mCenterXStiffness=converted }
                RECT_CENTERY -> { mCenterYDamping=damping; mCenterYStiffness=converted }
                RECT_WIDTH -> { mWidthDamping=damping; mWidthStiffness=converted }
                RECT_RATIO -> { mRatioDamping=damping; mRatioStiffness=converted }
                else -> error("unexpected native property")
            }
        }
    }
    /** A setter spy, not a second spring solver. Native response conversion is
     * independently evidenced in the target APK. */
    class Native {
        enum class RectAnimType { RECT_CENTER_X,RECT_CENTER_Y,RECT_WIDTH,RECT_RATIO,RADIUS,ALPHA }
        @JvmField var mCenterXDamping=.8f
        @JvmField var mCenterYDamping=.8f
        @JvmField var mWidthDamping=.8f
        @JvmField var mRatioDamping=.8f
        @JvmField var mCenterXStiffness=200f
        @JvmField var mCenterYStiffness=200f
        @JvmField var mWidthStiffness=200f
        @JvmField var mRatioStiffness=400f
        var radius=.7f; var alpha=.9f; var velocity=1520f
        val calls=mutableListOf<Triple<RectAnimType,Float,Float>>()
        private fun setAnimParam(axis: RectAnimType,damping: Float,response: Float) {
            calls+=Triple(axis,damping,response)
            when(axis) {
                RectAnimType.RECT_CENTER_X -> { mCenterXDamping=damping; mCenterXStiffness=response }
                RectAnimType.RECT_CENTER_Y -> { mCenterYDamping=damping; mCenterYStiffness=response }
                RectAnimType.RECT_WIDTH -> { mWidthDamping=damping; mWidthStiffness=response }
                RectAnimType.RECT_RATIO -> { mRatioDamping=damping; mRatioStiffness=response }
                RectAnimType.RADIUS -> radius=damping
                RectAnimType.ALPHA -> alpha=damping
            }
        }
    }
    @Test fun sceneProfilesUseTheNativeSetterOncePerGeometricAxisAndLeaveOtherChannelsAlone() {
        val parameters=DeckSpringParameters(Native::class.java)
        for(scene in DeckSpringScene.entries) {
            val spring=Native(); val saved=parameters.apply(spring,scene)
            assertEquals(4,spring.calls.size)
            assertEquals(4,spring.calls.map { it.first }.distinct().size)
            for(call in spring.calls) {
                assertEquals(scene.damping,call.second,0f); assertEquals(scene.response,call.third,0f)
            }
            assertEquals(.7f,spring.radius,0f); assertEquals(.9f,spring.alpha,0f); assertEquals(1520f,spring.velocity,0f)
            parameters.restore(spring,saved)
            assertEquals(.8f,spring.mCenterXDamping,0f); assertEquals(400f,spring.mRatioStiffness,0f)
        }
        assertEquals(.336f,DeckSpringScene.OPEN.response,0f); assertEquals(1f,DeckSpringScene.OPEN.damping,0f)
        assertEquals(.531f,DeckSpringScene.GESTURE.response,0f); assertEquals(.94f,DeckSpringScene.GESTURE.damping,0f)
    }
    @Test fun releasingDoesNotOverwriteAParameterSetByTheNextNativeScene() {
        val parameters=DeckSpringParameters(Native::class.java); val spring=Native()
        val saved=parameters.apply(spring,DeckSpringScene.GESTURE)
        spring.mCenterXDamping=.5f; spring.mCenterXStiffness=220f
        parameters.restore(spring,saved)
        assertEquals(.5f,spring.mCenterXDamping,0f); assertEquals(220f,spring.mCenterXStiffness,0f)
        assertEquals(.8f,spring.mCenterYDamping,0f); assertEquals(400f,spring.mRatioStiffness,0f)
    }
    @Test fun oldPropertyInterfaceUsesNativeSingletonsAndRollsBackAPartialFailure() {
        val parameters=DeckSpringParameters(OldNative::class.java)
        for(scene in DeckSpringScene.entries) {
            val spring=OldNative()
            val saved=parameters.apply(spring,scene)
            assertEquals(listOf(OldNative.RECT_CENTERX,OldNative.RECT_CENTERY,OldNative.RECT_WIDTH,OldNative.RECT_RATIO),spring.calls)
            assertEquals(scene.damping,spring.mCenterXDamping,0f)
            assertEquals(scene.response*1.5f,spring.mRatioStiffness,0f)
            parameters.restore(spring,saved)
            assertEquals(.8f,spring.mCenterXDamping,0f); assertEquals(400f,spring.mRatioStiffness,0f)
        }
        val spring=OldNative().apply { failAt=OldNative.RECT_WIDTH }
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) { parameters.apply(spring,DeckSpringScene.GESTURE) }
        assertEquals(.8f,spring.mCenterXDamping,0f); assertEquals(200f,spring.mCenterYStiffness,0f)
    }
    @Test fun nativeChangeToOneMemberPreservesTheEntireParameterPair() {
        val parameters=DeckSpringParameters(Native::class.java); val spring=Native()
        val saved=parameters.apply(spring,DeckSpringScene.GESTURE)
        spring.mCenterXDamping=.5f
        spring.mWidthStiffness=220f
        parameters.restore(spring,saved)
        assertEquals(.5f,spring.mCenterXDamping,0f)
        assertEquals(DeckSpringScene.GESTURE.response,spring.mCenterXStiffness,0f)
        assertEquals(DeckSpringScene.GESTURE.damping,spring.mWidthDamping,0f)
        assertEquals(220f,spring.mWidthStiffness,0f)
    }
}
