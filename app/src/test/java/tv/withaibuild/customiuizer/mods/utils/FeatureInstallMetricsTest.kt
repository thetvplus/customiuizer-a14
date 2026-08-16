package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureInstallMetricsTest {

    @Test
    fun elapsedMicrosRejectsMissingOrReversedSamples() {
        assertEquals(250L, FeatureInstallMetrics.elapsedMicros(1_000_000L, 1_250_999L))
        assertEquals(FeatureInstallMetrics.UNKNOWN, FeatureInstallMetrics.elapsedMicros(0L, 1_000L))
        assertEquals(FeatureInstallMetrics.UNKNOWN, FeatureInstallMetrics.elapsedMicros(2_000L, 1_000L))
    }

    @Test
    fun allocationDeltaRejectsMissingOrReversedSamples() {
        assertEquals(512L, FeatureInstallMetrics.allocationDelta(1_024L, 1_536L))
        assertEquals(FeatureInstallMetrics.UNKNOWN, FeatureInstallMetrics.allocationDelta(-1L, 1_536L))
        assertEquals(FeatureInstallMetrics.UNKNOWN, FeatureInstallMetrics.allocationDelta(1_536L, 1_024L))
    }
}
