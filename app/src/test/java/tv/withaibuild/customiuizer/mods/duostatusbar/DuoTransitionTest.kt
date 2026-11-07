package tv.withaibuild.customiuizer.mods.duostatusbar

import org.junit.Assert.*
import org.junit.Test

class DuoTransitionTest {
    @Test fun longFrameAndTinyScaleConvergeWithoutLeavingCellularFramesRunning() {
        for(scale in floatArrayOf(.00001f,.01f,1f,10f)) {
            val spring=DuoSpring(0f); spring.retarget(1f,0L,scale)
            spring.sample(100000L)
            assertTrue(spring.settled); assertEquals(1f,spring.value,.001f)
            val bars=DuoCellularMotion()
            bars.change(0,0,false); bars.change(4 shl 7,0,true,scale); bars.sample(100000L)
            assertFalse(bars.running)
        }
    }
    @Test fun matrixPowersMatchNativeHalfStepIntegrationAcrossRetargetsAndStalls() {
        val spring=DuoSpring(0f)
        var x=0f; var v=0.0; var target=0f; var remainder=0.0; var at=0L
        val random=java.util.Random(147)
        repeat(40) { round ->
            target=if(round%2==0) 1f else 0f
            spring.retarget(target,at)
            repeat(15) {
                val elapsed=1+random.nextInt(2000)
                val steps=elapsed.coerceAtMost(1500)*.3+remainder
                val count=steps.toInt(); remainder=steps-count
                var next=x.toDouble()
                val omega=2.0*Math.PI/.5; val dt=1.0/300
                repeat(count) {
                    v+=(-2*omega*v+omega*omega*(target-next))*dt*.5
                    next+=v*dt
                    v+=(-2*omega*v+omega*omega*(target-next))*dt*.5
                }
                x=next.toFloat()
                if(kotlin.math.abs(x-target)<.001f && kotlin.math.abs(v)<.005) { x=target; v=0.0; remainder=0.0 }
                at+=elapsed; spring.sample(at)
                assertEquals(x,spring.value,.00001f)
            }
        }
    }
    @Test fun nativeDefaultSpringHasNoArtificialBlankMidpointAndSettlesAtFullOpacity() {
        val state=DuoTransition(); state.change(1,0,false); state.change(2,100,true)
        for(time in 100L..1100L step 5) {
            val progress=state.fraction(time)
            assertTrue(progress in 0f..1f)
            assertTrue(state.currentAlpha+state.previousAlpha>=254)
            if(time==200L) assertEquals(.358f,progress,.015f)
        }
        assertFalse(state.running); assertEquals(255,state.currentAlpha); assertEquals(0,state.previousAlpha)
    }
    @Test fun seedDisabledMotionAndDuplicateValuesDoNotCreateWork() {
        val state=DuoTransition(); assertTrue(state.change(1,0,true))
        assertEquals(1f,state.fraction(0),0f)
        repeat(10000) { assertFalse(state.change(1,1,true)) }
        state.change(2,10,false)
        assertFalse(state.running); assertEquals(state.current,state.previous)
    }
    @Test fun reversingPreservesBothVisibleGlyphValuesAndOnlyTheLatestPairSurvives() {
        val state=DuoTransition(); state.change(1,0,false); state.change(2,10,true)
        state.fraction(110)
        val old=state.previousAlpha; val current=state.currentAlpha
        state.change(1,110,true); state.fraction(110)
        assertEquals(old,state.currentAlpha); assertEquals(current,state.previousAlpha)
        repeat(10000) { state.change(3+it%2,150,true) }
        state.fraction(1650)
        assertFalse(state.running); assertEquals(state.current,state.previous)
    }
    @Test fun hiddenDetachedOrFailedOwnerFinishesImmediately() {
        val state=DuoTransition(); state.change(1,0,true); state.change(2,10,true)
        state.finish()
        assertEquals(1f,state.fraction(11),0f); assertEquals(state.current,state.previous)
        state.change(3,12,true); assertEquals(0f,state.fraction(12),0f)
    }
    @Test fun animationScaleChangesTimeWithoutChangingNativeSpringGeometry() {
        val ordinary=DuoTransition(); val slow=DuoTransition()
        ordinary.change(1,0,false); slow.change(1,0,false)
        ordinary.change(2,0,true); slow.change(2,0,true,2f)
        assertEquals(ordinary.fraction(100),slow.fraction(200),.001f)
    }
    @Test fun cellularBarsRetargetFromTheirVisibleValueAndStopWhenNotVisible() {
        val bars=DuoCellularMotion(); val high=4 shl 7
        bars.change(0,0,false); bars.change(high,0,true); bars.sample(100)
        val alpha=bars.color(-1,0) ushr 24
        assertTrue(alpha in 57..254)
        bars.change(0,100,true); assertEquals(alpha,bars.color(-1,0) ushr 24)
        bars.sample(1600); assertEquals(56,bars.color(-1,0) ushr 24); assertFalse(bars.running)
        bars.change(high,1700,false); assertEquals(255,bars.color(-1,0) ushr 24); assertFalse(bars.running)
    }

    @Test fun centerOnlyChangesForVisibleStateAndNeverInventsHeadphones() {
        val config = DuoConfig(true, 24)
        val wifi = DuoNetworkState.WIFI_CONNECTED or (3 shl 4)
        assertEquals(DuoCenter.key(wifi, 0, config), DuoCenter.key(wifi or (4 shl 7), 0, config))
        assertEquals(DuoAudioState.OFFLINE_GLYPH, DuoCenter.glyph(DuoCenter.key(0, 0, config)))
        assertEquals(DuoAudioState.CELLULAR_GLYPH,
            DuoCenter.glyph(DuoCenter.key(DuoNetworkState.MOBILE_SERVICE, 0, config)))
        assertEquals(DuoAudioState.WIFI_GLYPH,
            DuoCenter.glyph(DuoCenter.key(0, 0, DuoConfig(true, 24, networkFallback = false))))
        val hint = DuoAudioState.BLUETOOTH or (2 shl 2)
        assertEquals(2, DuoCenter.value(DuoCenter.key(wifi, hint, config)))
        assertEquals(0, DuoCenter.value(DuoCenter.key(wifi, hint, DuoConfig(true, 24, bluetoothBatteryColor = false))))
        assertEquals(DuoAudioState.WIFI_GLYPH,
            DuoCenter.glyph(DuoCenter.key(wifi, hint, DuoConfig(true, 24, showAudio = false))))
    }
}
