package tv.withaibuild.customiuizer.mods.duostatusbar

import org.junit.Assert.*
import org.junit.Test

class DuoTransitionTest {
    @Test fun eachTransitionFrameDrawsOnlyOneGlyphAndEndsAtFullOpacity() {
        val state = DuoTransition()
        state.change(1, 0L, 180L)
        state.change(2, 100L, 180L)
        for (time in 100L..280L) {
            val progress = state.fraction(time)
            assertEquals(if (time < 190L) 1 else 2, state.drawKey(progress))
            assertTrue(state.drawAlpha(progress) in 0..255)
        }
        assertEquals(255, state.drawAlpha(0f))
        assertEquals(0, state.drawAlpha(0.5f))
        assertEquals(255, state.drawAlpha(1f))
        assertEquals(2, state.drawKey(1f))
    }

    @Test fun seedAndDisabledAnimationsSnapAndDuplicatesDoNoWork() {
        val state = DuoTransition()
        assertTrue(state.change(1, 0L, 180L))
        assertEquals(1f, state.fraction(0L), 0f)
        repeat(10000) { assertFalse(state.change(1, 1L, 180L)) }
        state.change(2, 10L, 0L)
        assertEquals(1f, state.fraction(10L), 0f)
        assertEquals(2, state.previous)
    }

    @Test fun transitionIsFiniteAndRapidUpdatesCannotExtendItsDeadline() {
        val state = DuoTransition()
        state.change(1, 0L, 180L)
        state.change(2, 100L, 180L)
        assertEquals(0f, state.fraction(100L), 0f)
        repeat(10000) { state.change(2 + it % 2, 150L, 180L) }
        assertEquals(1f, state.fraction(280L), 0f)
        assertEquals(state.current, state.previous)
        state.change(4, 300L, Long.MAX_VALUE)
        assertEquals(1f, state.fraction(660L), 0f)
    }

    @Test fun hiddenDetachedOrFailedOwnerFinishesImmediately() {
        val state = DuoTransition()
        state.change(1, 0L, 180L); state.change(2, 10L, 180L)
        state.finish()
        assertEquals(1f, state.fraction(11L), 0f)
        assertEquals(state.current, state.previous)
        state.change(3, 12L, 180L)
        assertEquals(0f, state.fraction(12L), 0f)
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
