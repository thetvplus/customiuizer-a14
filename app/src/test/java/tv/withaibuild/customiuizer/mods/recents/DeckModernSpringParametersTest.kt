package tv.withaibuild.customiuizer.mods.recents

import org.junit.Assert.*
import org.junit.Test

class DeckModernSpringParametersTest {
    class Bundle {
        private var damping=.8f; private var stiffness=200f
        var velocity=1200f
        fun getDamping()=damping
        fun getStiffness()=stiffness
        fun setDampingAndStiffness(d: Float,s: Float) { damping=d; stiffness=s }
    }
    class Native {
        enum class AnimType { OPEN_FROM_RECENTS, CLOSE_TO_RECENTS }
        @JvmField val mCenterXBundle=Bundle()
        @JvmField val mCenterYBundle=Bundle()
        @JvmField val mWidthBundle=Bundle()
        @JvmField val mRatioBundle=Bundle()
        @JvmField val mRadiusBundle=Bundle()
        @JvmField val mAlphaBundle=Bundle()
        var durationRatio=1.5f
        var failAt: String?=null
        val calls=ArrayList<String>()
        fun setAnimParamByType(type: AnimType) {}
        private fun setAnimParam(axis: String,damping: Float,response: Float) {
            calls.add(axis)
            if(axis==failAt) error("native setter failure")
            val bundle=when(axis) {
                "centerX" -> mCenterXBundle; "centerY" -> mCenterYBundle
                "Width" -> mWidthBundle; "Ratio" -> mRatioBundle
                else -> error("unexpected axis")
            }
            bundle.setDampingAndStiffness(damping,response*durationRatio)
        }
    }
    @Test fun modernKeysUseNativeDurationConversionWithoutChangingVelocityRadiusOrAlpha() {
        val adapter=DeckModernSpringParameters(Native::class.java)
        for(scene in DeckSpringScene.entries) {
            val spring=Native(); val saved=adapter.apply(spring,scene)
            assertEquals(listOf("centerX","centerY","Width","Ratio"),spring.calls)
            assertEquals(scene.response*1.5f,spring.mWidthBundle.getStiffness(),0f)
            assertEquals(scene.damping,spring.mCenterYBundle.getDamping(),0f)
            assertEquals(1200f,spring.mWidthBundle.velocity,0f)
            assertEquals(200f,spring.mRadiusBundle.getStiffness(),0f)
            assertEquals(.8f,spring.mAlphaBundle.getDamping(),0f)
            spring.mCenterXBundle.setDampingAndStiffness(.6f,180f)
            adapter.restore(spring,saved)
            assertEquals(.6f,spring.mCenterXBundle.getDamping(),0f)
            assertEquals(200f,spring.mWidthBundle.getStiffness(),0f)
        }
    }
    @Test fun partialNativeFailureRollsBackEveryOwnedPair() {
        val adapter=DeckModernSpringParameters(Native::class.java); val spring=Native().apply { failAt="Width" }
        try { adapter.apply(spring,DeckSpringScene.GESTURE); fail("must fail") }
        catch (_: java.lang.reflect.InvocationTargetException) { }
        assertEquals(.8f,spring.mCenterXBundle.getDamping(),0f)
        assertEquals(200f,spring.mCenterYBundle.getStiffness(),0f)
    }
}
