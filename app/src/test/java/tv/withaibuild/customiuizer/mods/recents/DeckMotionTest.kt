package tv.withaibuild.customiuizer.mods.recents

import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers.setField

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeckMotionTest {
    class Algorithm(private val landscape: Boolean) {
        fun isLandscapeVisually()=landscape
    }
    private fun <T> unconstructed(type: Class<T>): T {
        val field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible=true }
        return requireNotNull(type.cast(field.type.getMethod("allocateInstance",Class::class.java).invoke(field.get(null),type)))
    }
    private fun owner(landscape: Boolean=false): DeckOwner {
        val api=unconstructed(DeckNativeApi::class.java)
        setField(api,"visualLandscape",Algorithm::class.java.getMethod("isLandscapeVisually"))
        val owner=unconstructed(DeckOwner::class.java)
        setField(owner,"api",api)
        setField(owner,"stack",FrameLayout(RuntimeEnvironment.getApplication()))
        setField(owner,"current",owner.Scope(Algorithm(landscape)))
        setField(owner,"baseAllowed",true);setField(owner,"tasksAllowed",true)
        setField(owner,"geometryReady",true);setField(owner,"transition",1L)
        return owner
    }
    private fun motion(legacy: Boolean)=if(legacy)
        DeckMotion.legacy(DeckSpringParametersTest.Native::class.java) { 1f }
        else DeckMotion.modern(DeckModernSpringParametersTest.Native::class.java)
    private fun spring(legacy: Boolean): Any=if(legacy) DeckSpringParametersTest.Native()
        else DeckModernSpringParametersTest.Native()
    private fun width(spring: Any)=if(spring is DeckSpringParametersTest.Native) spring.mWidthStiffness
        else (spring as DeckModernSpringParametersTest.Native).mWidthBundle.getStiffness()
    private fun response(spring: Any,scene: DeckSpringScene)=scene.response*
        if(spring is DeckSpringParametersTest.Native) 1f else (spring as DeckModernSpringParametersTest.Native).durationRatio
    private fun configure(motion: DeckMotion,spring: Any,scene: DeckSpringScene) {
        motion.beforeConfigure(spring)
        val types=requireNotNull(motion.configure.parameterTypes[0].enumConstants)
        motion.configure.invoke(spring,types.single { (it as Enum<*>).name==scene.nativeType })
        motion.afterConfigure(spring,scene.nativeType,false)
        motion.initialize?.let { motion.beforeInit(spring); it.invoke(spring) }
    }

    @Test fun everySceneSharesTheSameIosParametersAcrossBothOrientationsAndNativeFamilies() {
        for(legacy in listOf(false,true)) for(landscape in listOf(false,true)) {
            val motion=motion(legacy);val owner=owner(landscape);val spring=spring(legacy)
            assertEquals(landscape,owner.current.landscape)
            for(scene in DeckSpringScene.entries) {
                motion.mark(spring,owner,scene)
                configure(motion,spring,scene)
                assertEquals(response(spring,scene),width(spring),0f)
                DeckMotion.clear(spring,owner,owner.transition)
                assertEquals(200f,width(spring),0f)
            }
        }
    }

    @Test fun nestedClosingRetainsGestureOriginAcrossANewTicketWithoutLeakingToOtherOwners() {
        for(legacy in listOf(false,true)) {
            val motion=motion(legacy);val owner=owner();val unrelated=owner();val spring=spring(legacy)
            motion.enter(owner,DeckSpringScene.GESTURE)
            try {
                // The native closing provider begins a fresh transition inside
                // performAppToRecents before it creates/configures the spring.
                setField(owner,"transition",2L)
                assertEquals(DeckSpringScene.GESTURE,motion.closingScene(owner))
                assertEquals(DeckSpringScene.TOGGLE,motion.closingScene(unrelated))
                motion.enter(owner,motion.closingScene(owner))
                try {
                    configure(motion,spring,DeckSpringScene.GESTURE)
                    assertEquals(response(spring,DeckSpringScene.GESTURE),width(spring),0f)
                } finally { motion.leave() }
                assertEquals(DeckSpringScene.GESTURE,motion.closingScene(owner))
                DeckMotion.clear(spring,owner,2L)
            } finally { motion.leave() }
            assertEquals(DeckSpringScene.TOGGLE,motion.closingScene(owner))
            motion.enter(owner,DeckSpringScene.TOGGLE)
            try {
                configure(motion,spring,DeckSpringScene.TOGGLE)
                assertEquals(response(spring,DeckSpringScene.TOGGLE),width(spring),0f)
            } finally { motion.leave();DeckMotion.clear(spring,owner,2L) }
        }
    }

    @Test fun explicitOpeningWinsOverAnEnclosingGestureAndOnlyTheExpectedSpringIsOwned() {
        for(legacy in listOf(false,true)) {
            val motion=motion(legacy);val owner=owner();val spring=spring(legacy);val other=spring(legacy)
            motion.enter(owner,DeckSpringScene.GESTURE)
            try {
                motion.mark(spring,owner,DeckSpringScene.OPEN)
                configure(motion,spring,DeckSpringScene.OPEN)
                assertEquals(response(spring,DeckSpringScene.OPEN),width(spring),0f)
                DeckMotion.clear(spring,owner,1L)
            } finally { motion.leave() }
            motion.enter(owner,DeckSpringScene.GESTURE,spring)
            try {
                configure(motion,other,DeckSpringScene.GESTURE)
                assertEquals(200f,width(other),0f)
            } finally { motion.leave() }
        }
    }

    @Test fun retargetRestoresThePreviousSceneAndLateCompletionCannotReleaseTheNewTicket() {
        for(legacy in listOf(false,true)) {
            val motion=motion(legacy);val owner=owner();val spring=spring(legacy)
            motion.mark(spring,owner,DeckSpringScene.OPEN)
            configure(motion,spring,DeckSpringScene.OPEN)
            setField(owner,"transition",2L)
            motion.mark(spring,owner,DeckSpringScene.GESTURE)
            assertEquals(200f,width(spring),0f)
            configure(motion,spring,DeckSpringScene.GESTURE)
            DeckMotion.clear(spring,owner,1L)
            assertEquals(response(spring,DeckSpringScene.GESTURE),width(spring),0f)
            DeckMotion.clear(spring,owner,2L);DeckMotion.clear(spring,owner,2L)
            assertEquals(200f,width(spring),0f)
            configure(motion,spring,DeckSpringScene.GESTURE)
            assertEquals("No stale scene after end/cancel",200f,width(spring),0f)
        }
    }

    @Test fun invalidatedOrFailedRequestsReturnNativeParametersWithoutReapplyingLater() {
        for(legacy in listOf(false,true)) {
            val motion=motion(legacy);val owner=owner();val spring=spring(legacy)
            motion.mark(spring,owner,DeckSpringScene.OPEN)
            configure(motion,spring,DeckSpringScene.OPEN)
            setField(owner,"transition",2L)
            configure(motion,spring,DeckSpringScene.OPEN)
            assertEquals(200f,width(spring),0f)
            motion.mark(spring,owner,DeckSpringScene.GESTURE)
            configure(motion,spring,DeckSpringScene.GESTURE)
            motion.failedInit(spring)
            assertEquals(200f,width(spring),0f)
            configure(motion,spring,DeckSpringScene.GESTURE)
            assertEquals(200f,width(spring),0f)
            motion.mark(spring,owner,DeckSpringScene.OPEN)
            motion.beforeConfigure(spring);motion.afterConfigure(spring,"CLOSE_TO_HOME",false)
            configure(motion,spring,DeckSpringScene.OPEN)
            assertEquals("Unrelated native scenes cannot inherit Deck timing",200f,width(spring),0f)
        }
    }

    @Test fun parameterFailureRollsBackAndDropsTheRequestInBothNativeFamilies() {
        for(legacy in listOf(false,true)) {
            val motion=motion(legacy);val owner=owner();val spring=spring(legacy)
            if(spring is DeckSpringParametersTest.Native) spring.failAt=DeckSpringParametersTest.Native.RectAnimType.RECT_WIDTH
            else (spring as DeckModernSpringParametersTest.Native).failAt="Width"
            motion.mark(spring,owner,DeckSpringScene.GESTURE)
            assertThrows(java.lang.reflect.InvocationTargetException::class.java) { configure(motion,spring,DeckSpringScene.GESTURE) }
            assertEquals(200f,width(spring),0f)
            if(spring is DeckSpringParametersTest.Native) spring.failAt=null
            else (spring as DeckModernSpringParametersTest.Native).failAt=null
            configure(motion,spring,DeckSpringScene.GESTURE)
            assertEquals("Failed tuning must not remain armed",200f,width(spring),0f)
        }
    }
}
