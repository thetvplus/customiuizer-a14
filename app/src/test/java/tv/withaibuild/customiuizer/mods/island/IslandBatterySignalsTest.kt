package tv.withaibuild.customiuizer.mods.island

import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandBatterySignalsTest {
    @Test fun initialAndRepeatedBatterySnapshotsDoNotReplayChargingOrLowWarnings() {
        val edges = IslandBatteryEdges()
        assertNull(edges.observe(60, true, true, true))
        assertNull(edges.observe(59, true, true, false))
        assertNull(edges.observe(58, false, false, false))
        assertEquals(IslandBatteryEdges.Event(58, false), edges.observe(58, true, true, false))
        assertNull(edges.observe(58, true, true, false))
        assertNull(edges.observe(100, true, false, false))
        assertNull(edges.observe(99, true, true, false)) // Maintenance charge is the same connection.
        edges.observe(23, false, false, false)
        assertEquals(IslandBatteryEdges.Event(20, true), edges.observe(20, false, false, false))
        assertNull(edges.observe(19, false, false, false))
        assertEquals(IslandBatteryEdges.Event(10, true), edges.observe(10, false, false, false))
        assertNull(edges.observe(9, false, false, false))
        assertEquals(IslandBatteryEdges.Event(5, true), edges.observe(5, false, false, false))
        assertNull(edges.observe(-1, false, false, false))
    }

    @Test fun nativeWarningIsRetainedUntilVisibleAcknowledgementAndAfterHideExpiryOrClose() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val marker = IslandBatteryWarningMarker(resolver, TestScope(dispatcher), dispatcher)
        try {
            assertFalse(IslandBatteryWarningMarker.acknowledged(resolver))
            marker.visible(true); scheduler.runCurrent()
            assertTrue(IslandBatteryWarningMarker.acknowledged(resolver))
            marker.visible(false); scheduler.runCurrent()
            assertFalse(IslandBatteryWarningMarker.acknowledged(resolver))
            Settings.Global.putString(resolver, "customiuizer_island_low_battery_until", (SystemClock.elapsedRealtime() - 1).toString())
            assertFalse(IslandBatteryWarningMarker.acknowledged(resolver))
            Settings.Global.putString(resolver, "customiuizer_island_low_battery_until", (SystemClock.elapsedRealtime() + 20_000).toString())
            assertFalse(IslandBatteryWarningMarker.acknowledged(resolver))
            marker.visible(true); scheduler.runCurrent()
            marker.close(); scheduler.runCurrent()
            assertFalse(IslandBatteryWarningMarker.acknowledged(resolver))
        } finally { marker.close(); scheduler.runCurrent() }
    }
}
