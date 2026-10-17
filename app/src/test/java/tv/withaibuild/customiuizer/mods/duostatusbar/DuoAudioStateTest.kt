package tv.withaibuild.customiuizer.mods.duostatusbar

import org.junit.Assert.*
import org.junit.Test

class DuoAudioStateTest {
    @Test fun nonAudioBluetoothDeviceDoesNotBecomeHeadphones() {
        val state = DuoAudioState()
        assertFalse(state.bluetooth(true, true, false, false, 0L, true))
        assertTrue(state.bluetooth(true, true, true, false, 0L, true))
        assertEquals(DuoAudioState.BLUETOOTH, state.snapshot)
        assertTrue(state.bluetooth(true, true, false, false, 0L, true))
        assertEquals(0, state.snapshot)
        assertTrue(state.bluetooth(true, true, false, true, 0L, true))
        assertTrue(state.bluetooth(false, true, true, true, 0L, true))
        assertEquals(0, state.snapshot)
    }

    @Test fun wiredDisconnectKeepsConnectedBluetoothAndDuplicateEventsDoNoWork() {
        val state = DuoAudioState()
        state.bluetooth(true, true, true, true, 0L, true)
        state.wired(true, 0L, true)
        assertEquals(DuoAudioState.WIRED or DuoAudioState.BLUETOOTH, state.snapshot)
        repeat(10000) {
            assertFalse(state.wired(true, 1000L, true))
            assertFalse(state.bluetooth(true, true, true, true, 0L, true))
        }
        state.wired(false, 1000L, true)
        assertEquals(DuoAudioState.BLUETOOTH, state.snapshot)
        assertTrue(state.clear())
        assertFalse(state.clear())
    }

    @Test fun networkWarningsAndAirplaneHavePriorityAndWifiReturnsAfterDisconnect() {
        val wifi = DuoNetworkState.WIFI_CONNECTED
        val wired = DuoAudioState.WIRED
        val bluetooth = DuoAudioState.BLUETOOTH
        assertEquals(DuoAudioState.WIFI_GLYPH, DuoAudioState.glyph(wifi, 0))
        assertEquals(DuoAudioState.WIRED_GLYPH, DuoAudioState.glyph(wifi, wired or bluetooth))
        assertEquals(DuoAudioState.BLUETOOTH_GLYPH, DuoAudioState.glyph(wifi, bluetooth))
        assertEquals(DuoAudioState.WIFI_GLYPH,
            DuoAudioState.glyph(wifi or DuoNetworkState.WIFI_UNVALIDATED, wired or bluetooth))
        assertEquals(DuoAudioState.PLANE_GLYPH, DuoAudioState.glyph(DuoNetworkState.AIRPLANE, bluetooth))
        assertEquals(DuoAudioState.BLUETOOTH_GLYPH,
            DuoAudioState.glyph(wifi or DuoNetworkState.AIRPLANE, bluetooth))
        assertEquals(DuoAudioState.WIFI_GLYPH, DuoAudioState.glyph(wifi, 0))
    }

    @Test fun concurrentIndependentConnectionsDoNotLoseEachOther() {
        val state = DuoAudioState()
        val wired = Thread { repeat(10000) { state.wired(it % 2 == 0, 0L, true) }; state.wired(true, 0L, true) }
        val bluetooth = Thread { repeat(10000) { state.bluetooth(true, it % 2 == 0, true, false, 0L, true) }
            state.bluetooth(true, true, true, false, 0L, true) }
        wired.start(); bluetooth.start(); wired.join(); bluetooth.join()
        assertEquals(DuoAudioState.WIRED or DuoAudioState.BLUETOOTH, state.snapshot)
    }

    @Test fun alreadyConnectedSeedDoesNotNotifyAndOnlyANewConnectionStartsTheHint() {
        val state = DuoAudioState()
        assertFalse(state.bluetooth(true, true, true, false, 1000L, false))
        assertFalse(state.bluetooth(true, true, true, true, 1500L, true))
        assertEquals(0L, state.expiresAt)
        assertFalse(state.bluetooth(true, false, false, false, 2000L, true))
        assertTrue(state.bluetooth(true, true, true, false, 2500L, true))
        assertEquals(5500L, state.expiresAt)
        repeat(10000) { assertFalse(state.bluetooth(true, true, true, it % 2 == 0, 3000L, true)) }
        assertEquals(5500L, state.expiresAt)
    }

    @Test fun overlappingHintsExpireIndependentlyAndDoNotRepeatWhileConnected() {
        val state = DuoAudioState()
        state.bluetooth(true, true, true, false, 1000L, true)
        state.wired(true, 2000L, true)
        assertEquals(4000L, state.expiresAt)
        assertFalse(state.expire(3999L))
        assertTrue(state.expire(4000L))
        assertEquals(DuoAudioState.WIRED, state.snapshot)
        assertEquals(5000L, state.expiresAt)
        assertFalse(state.bluetoothBattery(1, true))
        assertTrue(state.expire(5000L))
        assertEquals(0, state.snapshot)
        assertEquals(0L, state.expiresAt)
        assertFalse(state.expire(5000L))
        assertFalse(state.bluetooth(true, true, true, true, 5000L, true))
        assertFalse(state.wired(true, 5000L, true))
        assertEquals(0L, state.expiresAt)
    }

    @Test fun detachmentFinishesTheHintAndReattachmentDoesNotReplayIt() {
        val state = DuoAudioState()
        state.bluetooth(true, true, true, false, 1000L, true)
        state.wired(true, 1000L, true)
        assertTrue(state.finishHint())
        assertFalse(state.finishHint())
        assertFalse(state.bluetooth(true, true, true, true, 1500L, false))
        assertFalse(state.bluetooth(true, true, true, true, 1500L, true))
        assertFalse(state.wired(true, 1500L, false))
        assertFalse(state.wired(true, 1500L, true))
        assertEquals(0, state.snapshot)
        assertEquals(0L, state.expiresAt)
    }

    @Test fun freshBatteryColorsAreBoundedAndNeverExtendOrReplayTheHint() {
        val state = DuoAudioState()
        assertFalse(state.bluetoothBattery(1, true))
        state.bluetooth(true, true, true, false, 1000L, true)
        assertTrue(state.bluetoothBattery(2, true))
        assertEquals(2, DuoAudioState.batteryStep(state.snapshot))
        repeat(10000) { assertFalse(state.bluetoothBattery(2, true)) }
        assertEquals(4000L, state.expiresAt)
        assertTrue(state.bluetoothBattery(10, false))
        assertEquals(0, DuoAudioState.batteryStep(state.snapshot))
        assertFalse(state.bluetoothBattery(11, true))
        state.bluetoothBattery(5, true)
        state.expire(4000L)
        assertEquals(0, state.snapshot)
        assertFalse(state.bluetoothBattery(1, true))
    }

    @Test fun newConnectionAndWiredGlyphNeverInheritTheOldBluetoothBattery() {
        val state = DuoAudioState()
        state.bluetooth(true, true, true, false, 1000L, true)
        state.bluetoothBattery(2, true)
        state.wired(true, 1000L, true)
        assertEquals(DuoAudioState.WIRED_GLYPH, DuoAudioState.glyph(0, state.snapshot))
        state.bluetooth(true, false, false, false, 1500L, true)
        state.bluetooth(true, true, true, false, 2000L, true)
        assertEquals(0, DuoAudioState.batteryStep(state.snapshot))
        state.finishHint()
        assertEquals(0, state.snapshot)
    }

    @Test fun wiredHintRestoresWifiWhileStillConnectedWithoutExtendingItsDeadline() {
        val state = DuoAudioState()
        val wifi = DuoNetworkState.WIFI_CONNECTED
        assertTrue(state.wired(true, 1000L, true))
        assertEquals(DuoAudioState.WIRED_GLYPH, DuoAudioState.glyph(wifi, state.snapshot))
        repeat(10000) { assertFalse(state.wired(true, 2000L, true)) }
        assertEquals(4000L, state.expiresAt)
        assertFalse(state.expire(3999L))
        assertTrue(state.expire(4000L))
        assertEquals(DuoAudioState.WIFI_GLYPH, DuoAudioState.glyph(wifi, state.snapshot))
        assertFalse(state.wired(true, 5000L, true))
        assertFalse(state.wired(true, 5000L, false))
        assertEquals(0L, state.expiresAt)
        assertFalse(state.wired(false, 6000L, true))
        assertTrue(state.wired(true, 7000L, true))
        assertEquals(10000L, state.expiresAt)
    }

    @Test fun alreadyConnectedWiredSeedDoesNotReplayAndOfflineFallbackReturnsAfterHint() {
        val state = DuoAudioState()
        assertFalse(state.wired(true, 1000L, false))
        assertFalse(state.wired(true, 1500L, true))
        assertEquals(0L, state.expiresAt)
        assertFalse(state.wired(false, 2000L, true))
        assertTrue(state.wired(true, 2500L, true))
        assertTrue(state.expire(5500L))
        assertEquals(DuoAudioState.CELLULAR_GLYPH,
            DuoAudioState.glyph(DuoNetworkState.MOBILE_SERVICE, state.snapshot))
        assertEquals(DuoAudioState.OFFLINE_GLYPH, DuoAudioState.glyph(0, state.snapshot))
    }

    @Test fun wiredExpiryPreservesTheLaterBluetoothHintAndItsBattery() {
        val state = DuoAudioState()
        state.wired(true, 1000L, true)
        state.bluetooth(true, true, true, false, 2000L, true)
        state.bluetoothBattery(2, true)
        assertTrue(state.expire(4000L))
        assertEquals(DuoAudioState.BLUETOOTH_GLYPH, DuoAudioState.glyph(0, state.snapshot))
        assertEquals(2, DuoAudioState.batteryStep(state.snapshot))
        assertEquals(5000L, state.expiresAt)
        assertTrue(state.expire(5000L))
        assertEquals(0, state.snapshot)
    }
}
