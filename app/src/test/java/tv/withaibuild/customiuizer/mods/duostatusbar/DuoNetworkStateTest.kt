package tv.withaibuild.customiuizer.mods.duostatusbar

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch

class DuoNetworkStateTest {
    @Test fun cellularTypeFollowsDefaultDataSimAndNeverSurvivesServiceLossOrReplacement() {
        val state = DuoNetworkState()
        state.subscriptions(10, 20, true)
        assertTrue(state.mobile(0, 10, 4, true, true, 5, 4))
        assertFalse(state.mobile(0, 10, 4, true, true, 5, 4))
        assertFalse(state.mobile(1, 20, 4, true, false, 5, 5))
        assertEquals(4, DuoNetworkState.cellularType(state.snapshot))
        assertTrue(state.mobile(0, 10, 4, true, true, 5, 5))
        assertEquals(5, DuoNetworkState.cellularType(state.snapshot))
        assertTrue(state.mobile(1, 20, 4, true, true, 5, 3))
        assertEquals(3, DuoNetworkState.cellularType(state.snapshot))
        state.airplane(true)
        assertEquals(0, DuoNetworkState.cellularType(state.snapshot))
        state.airplane(false)
        assertEquals(3, DuoNetworkState.cellularType(state.snapshot))
        state.mobile(1, 20, 4, false, true, 5, 3)
        assertEquals(0, DuoNetworkState.cellularType(state.snapshot))
        state.subscriptions(-1, 30, true)
        assertFalse(state.mobile(1, 20, 4, true, true, 5, 5))
        assertEquals(0, DuoNetworkState.cellularType(state.snapshot))
        state.mobile(1, 30, 4, true, true, 5, 99)
        assertEquals(0, DuoNetworkState.cellularType(state.snapshot))
    }

    @Test fun allSixXiaomiStrengthStatesReachTheFourDotsWithoutSaturation() {
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        var lastCoverage = -1
        for (level in 0..5) {
            assertTrue(state.mobile(0, 10, level, true, true, 5))
            val coverage = (0..3).sumOf { DuoNetworkState.dotCoverage(state.snapshot, it) }
            assertEquals(level * 204, coverage)
            assertTrue(coverage > lastCoverage)
            assertFalse(state.mobile(0, 10, level, true, true, 5))
            lastCoverage = coverage
        }
        state.mobile(0, 10, 4, true, true, 5)
        assertEquals(51, DuoNetworkState.dotCoverage(state.snapshot, 3))
        state.mobile(0, 10, 5, true, true, 5)
        assertEquals(255, DuoNetworkState.dotCoverage(state.snapshot, 3))
    }

    @Test fun fourLevelRomStillLightsWholeDotsAndNoServiceClearsPartialDots() {
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        for (level in 0..4) {
            state.mobile(0, 10, level, true, true, 4)
            for (dot in 0..3) assertEquals(if (dot < level) 255 else 0,
                DuoNetworkState.dotCoverage(state.snapshot, dot))
        }
        state.mobile(0, 10, 5, true, true, 5)
        state.mobile(0, 10, 5, false, true, 5)
        for (dot in 0..3) assertEquals(0, DuoNetworkState.dotCoverage(state.snapshot, dot))
        state.mobile(0, 10, 5, true, true, 5)
        state.airplane(true)
        for (dot in 0..3) assertEquals(0, DuoNetworkState.dotCoverage(state.snapshot, dot))
    }

    @Test fun signalScaleBelongsToTheSelectedSubscriptionAndIsResetOnReplacement() {
        val state = DuoNetworkState()
        state.subscriptions(10, 20, true)
        state.mobile(0, 10, 4, true, true, 5)
        state.mobile(1, 20, 4, true, false, 4)
        assertEquals(51, DuoNetworkState.dotCoverage(state.snapshot, 3))
        state.mobile(1, 20, 4, true, true, 4)
        assertEquals(255, DuoNetworkState.dotCoverage(state.snapshot, 3))
        state.subscriptions(-1, 30, true)
        assertFalse(state.mobile(1, 20, 5, true, true, 5))
        state.mobile(1, 30, 1, true, true, 4)
        assertEquals(255, DuoNetworkState.dotCoverage(state.snapshot, 0))
        assertEquals(0, DuoNetworkState.dotCoverage(state.snapshot, 1))
        assertFalse(state.mobile(1, 30, 1, true, true, 6))
    }

    @Test fun unknownStateDoesNotReplaceAnyNativeIcon() {
        val state = DuoNetworkState()
        assertEquals(0, state.snapshot)
        state.subscriptions(10, 20, true)
        assertEquals(0, state.snapshot and 3)
        state.wifi(true, true, 4)
        assertEquals(DuoNetworkState.WIFI_READY, state.snapshot and 3)
    }

    @Test fun connectedWifiIncludesLowestLevelAndDisconnectClearsStrength() {
        val state = DuoNetworkState()
        state.wifi(true, true, 0)
        assertTrue(state.snapshot and DuoNetworkState.WIFI_CONNECTED != 0)
        state.wifi(true, true, 4)
        assertEquals(4, DuoNetworkState.wifiLevel(state.snapshot))
        state.wifi(false, true, 4)
        assertEquals(0, DuoNetworkState.wifiLevel(state.snapshot))
        assertEquals(0, state.snapshot and DuoNetworkState.WIFI_CONNECTED)
    }

    @Test fun serviceFallbackDistinguishesZeroBarsFromNoServiceAndAirplaneMode() {
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        state.mobile(0, 10, 0, true, true)
        assertTrue(state.snapshot and DuoNetworkState.MOBILE_SERVICE != 0)
        assertEquals(DuoAudioState.CELLULAR_GLYPH, DuoAudioState.glyph(state.snapshot, 0))
        state.mobile(0, 10, 0, false, true)
        assertEquals(DuoAudioState.OFFLINE_GLYPH, DuoAudioState.glyph(state.snapshot, 0))
        state.airplane(true)
        assertEquals(DuoAudioState.PLANE_GLYPH, DuoAudioState.glyph(state.snapshot, 0))
        state.wifi(true, true, 2)
        assertEquals(DuoAudioState.WIFI_GLYPH, DuoAudioState.glyph(state.snapshot, 0))
    }

    @Test fun duplicateWifiAndMobileEventsDoNotScheduleRefresh() {
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        assertTrue(state.wifi(true, true, 3))
        repeat(10000) { assertFalse(state.wifi(true, true, 3)) }
        assertTrue(state.mobile(0, 10, 3, true, true))
        repeat(10000) { assertFalse(state.mobile(0, 10, 3, true, true)) }
    }

    @Test fun connectedWifiWithoutInternetKeepsItsWarningUntilValidationOrDisconnect() {
        val state = DuoNetworkState()
        state.wifi(true, true, 4, false)
        assertTrue(state.snapshot and DuoNetworkState.WIFI_UNVALIDATED != 0)
        assertFalse(state.wifi(true, true, 4, false))
        assertTrue(state.wifi(true, true, 4, true))
        assertEquals(0, state.snapshot and DuoNetworkState.WIFI_UNVALIDATED)
        state.wifi(true, true, 4, false)
        state.wifi(false, false, 0, false)
        assertEquals(0, state.snapshot and DuoNetworkState.WIFI_UNVALIDATED)
    }

    @Test fun dualSimUsesDefaultDataSimEvenIfSecondarySignalIsStronger() {
        val state = DuoNetworkState()
        state.subscriptions(10, 20, true)
        state.mobile(1, 20, 4, true, false)
        assertEquals(0, state.snapshot and DuoNetworkState.MOBILE_READY)
        state.mobile(0, 10, 1, true, true)
        assertEquals(1, DuoNetworkState.cellLevel(state.snapshot))
        assertFalse(state.mobile(1, 20, 3, true, false))
    }

    @Test fun switchingDataSimTakesEffectBeforeOldControllerRefreshes() {
        val state = DuoNetworkState()
        state.subscriptions(10, 20, true)
        state.mobile(0, 10, 1, true, true)
        state.mobile(1, 20, 4, true, true)
        assertEquals(4, DuoNetworkState.cellLevel(state.snapshot))
        assertFalse(state.mobile(0, 10, 3, true, false))
    }

    @Test fun losingServiceOnDefaultSimDoesNotBorrowOtherSimSignal() {
        val state = DuoNetworkState()
        state.subscriptions(10, 20, true)
        state.mobile(1, 20, 4, true, false)
        state.mobile(0, 10, 4, false, true)
        assertEquals(0, DuoNetworkState.cellLevel(state.snapshot))
        assertEquals(DuoNetworkState.MOBILE_READY, state.snapshot and DuoNetworkState.MOBILE_READY)
    }

    @Test fun retiredSubscriptionCannotResurrectAfterRemovalOrSlotReplacement() {
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        state.mobile(0, 10, 4, true, true)
        state.subscriptions(30, -1, true)
        assertFalse(state.mobile(0, 10, 4, true, true))
        assertEquals(0, state.snapshot and DuoNetworkState.MOBILE_READY)
        state.mobile(0, 30, 2, true, true)
        assertEquals(2, DuoNetworkState.cellLevel(state.snapshot))
        state.subscriptions(-1, -1, true)
        assertFalse(state.mobile(0, 30, 2, true, true))
        assertEquals(0, DuoNetworkState.cellLevel(state.snapshot))
        assertEquals(DuoNetworkState.MOBILE_READY, state.snapshot and DuoNetworkState.MOBILE_READY)
    }

    @Test fun airplaneStopsCellularAndAllowsWifiThenRestoresCellular() {
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        state.mobile(0, 10, 3, true, true)
        state.wifi(true, true, 4)
        state.airplane(true)
        assertEquals(0, DuoNetworkState.cellLevel(state.snapshot))
        assertEquals(4, DuoNetworkState.wifiLevel(state.snapshot))
        assertTrue(state.snapshot and DuoNetworkState.WIFI_CONNECTED != 0)
        state.airplane(false)
        assertEquals(3, DuoNetworkState.cellLevel(state.snapshot))
    }

    @Test fun unsupportedSubscriptionsKeepNativeMobileIcons() {
        val state = DuoNetworkState()
        state.subscriptions(10, 20, false)
        assertFalse(state.mobile(0, 10, 3, true, true))
        assertEquals(0, state.snapshot and DuoNetworkState.MOBILE_READY)
        state.subscriptions(10, -1, true)
        state.mobile(0, 10, 3, true, true)
        assertEquals(3, DuoNetworkState.cellLevel(state.snapshot))
    }

    @Test fun sameSubscriptionsRecoverWithServiceLossObservedDuringNativeFallback() {
        val state = DuoNetworkState()
        state.wifi(true, true, 3)
        state.subscriptions(10, 20, true)
        state.mobile(0, 10, 5, true, true, 5, 5)
        state.mobile(1, 20, 4, true, false, 4, 4)

        assertTrue(state.subscriptions(10, 20, false))
        val fallback = state.snapshot
        assertFalse(state.mobile(0, 10, 0, false, true, 5, 0))
        assertFalse(state.mobile(1, 20, 1, true, false, 4, 4))
        assertEquals(fallback, state.snapshot)
        assertEquals(0, state.snapshot and DuoNetworkState.MOBILE_READY)

        assertTrue(state.subscriptions(10, 20, true))
        assertEquals(3, state.snapshot and 3)
        assertEquals(0, state.snapshot and DuoNetworkState.MOBILE_SERVICE)
        assertEquals(0, DuoNetworkState.cellularType(state.snapshot))
        for (dot in 0..3) assertEquals(0, DuoNetworkState.dotCoverage(state.snapshot, dot))
        assertEquals(3, DuoNetworkState.wifiLevel(state.snapshot))
        assertFalse(state.mobile(0, 10, 0, false, true, 5, 0))
    }

    @Test fun defaultSimStrengthScaleAndTypeStayCurrentWhileNativeFallbackDoesNotRefresh() {
        val state = DuoNetworkState()
        state.subscriptions(10, 20, true)
        state.mobile(0, 10, 4, true, true, 5, 5)
        state.mobile(1, 20, 1, true, false, 5, 4)
        state.subscriptions(10, 20, false)
        val fallback = state.snapshot
        assertFalse(state.mobile(1, 20, 4, true, true, 4, 3))
        assertFalse(state.mobile(0, 10, 5, true, false, 5, 5))
        assertEquals(fallback, state.snapshot)

        state.subscriptions(10, 20, true)
        assertEquals(DuoNetworkState.MOBILE_READY, state.snapshot and DuoNetworkState.MOBILE_READY)
        assertEquals(4, DuoNetworkState.cellLevel(state.snapshot))
        assertEquals(3, DuoNetworkState.cellularType(state.snapshot))
        for (dot in 0..3) assertEquals(255, DuoNetworkState.dotCoverage(state.snapshot, dot))
        assertFalse(state.mobile(1, 20, 4, true, true, 4, 3))
    }

    @Test fun unchangedCurrentSimCanRecoverWithoutWaitingForAnotherMobileCallback() {
        for (slot in 0..1) {
            val state = DuoNetworkState()
            val first = if (slot == 0) 10 else -1
            val second = if (slot == 1) 10 else -1
            state.subscriptions(first, second, true)
            state.mobile(slot, 10, 2, true, true, 5, 4)
            val original = state.snapshot
            state.subscriptions(first, second, false)
            state.subscriptions(first, second, true)
            assertEquals(original, state.snapshot)
            assertFalse(state.mobile(slot, 10, 2, true, true, 5, 4))
        }
    }

    @Test fun nativeFallbackStillRejectsRetiredCardsInvalidSlotsAndUnknownSignalScales() {
        val state = DuoNetworkState()
        state.subscriptions(10, 20, true)
        state.mobile(0, 10, 2, true, true, 5, 4)
        val original = state.snapshot
        state.subscriptions(10, 20, false)
        val fallback = state.snapshot
        assertFalse(state.mobile(0, 30, 4, true, true, 4, 5))
        assertFalse(state.mobile(2, 10, 4, true, true, 4, 5))
        assertFalse(state.mobile(0, 10, 4, true, true, 6, 5))
        assertEquals(fallback, state.snapshot)
        state.subscriptions(10, 20, true)
        assertEquals(original, state.snapshot)
        state.subscriptions(30, 20, true)
        assertEquals(0, state.snapshot and DuoNetworkState.MOBILE_READY)
        assertFalse(state.mobile(0, 10, 5, true, true, 5, 5))
        assertTrue(state.mobile(0, 30, 1, true, true, 4, 3))
        assertEquals(1, DuoNetworkState.cellLevel(state.snapshot))
        assertEquals(3, DuoNetworkState.cellularType(state.snapshot))
    }

    @Test fun invalidLevelsAreBoundedAndInvalidSlotsAreIgnored() {
        val state = DuoNetworkState()
        assertFalse(state.mobile(2, 10, 5, true, true))
        assertFalse(state.mobile(0, -1, 5, true, true))
        state.subscriptions(-1, 20, true)
        state.mobile(1, 20, 99, true, false)
        assertEquals(4, DuoNetworkState.cellLevel(state.snapshot))
        state.mobile(1, 20, -9, true, false)
        assertEquals(0, DuoNetworkState.cellLevel(state.snapshot))
        state.wifi(true, true, 99)
        assertEquals(4, DuoNetworkState.wifiLevel(state.snapshot))
    }

    @Test fun interleavedNativeThreadsPublishCompleteBoundedSnapshots() {
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        val start = CountDownLatch(1)
        val wifi = Thread { start.await(); repeat(10000) { state.wifi(true, true, it % 5) } }
        val mobile = Thread { start.await(); repeat(10000) { state.mobile(0, 10, it % 5, true, true) } }
        wifi.start(); mobile.start(); start.countDown()
        wifi.join(10000); mobile.join(10000)
        assertFalse(wifi.isAlive); assertFalse(mobile.isAlive)
        assertEquals(3, state.snapshot and 3)
        assertEquals(4, DuoNetworkState.wifiLevel(state.snapshot))
        assertEquals(4, DuoNetworkState.cellLevel(state.snapshot))
    }
}
