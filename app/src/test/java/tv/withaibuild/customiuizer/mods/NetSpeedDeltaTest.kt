package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the traffic-sample -> speed conversion used by the detailed network speed
 * feature's background sampler.
 *
 * The regression these tests lock down: a sample that could not be accounted for
 * (`TrafficStats.UNSUPPORTED`, i.e. a negative value) must never be stored as the
 * baseline for the next sample. If it were, the following successful sample would
 * subtract a negative total and publish the whole cumulative byte counter as a
 * single interval of traffic, showing an absurd spike in the status bar.
 */
class NetSpeedDeltaTest {

    private val oneSecond = 1_000_000_000L

    // 1. 首个样本（基线为 0）报告 0，并记录新基线
    @Test
    fun firstSample_reportsZeroAndStoresBaseline() {
        val delta = SystemUIStatusBarHooks.computeNetSpeedDelta(
            newTxBytes = 5_000L,
            newRxBytes = 9_000L,
            prevTxTotal = 0L,
            prevRxTotal = 0L,
            elapsedNanos = oneSecond,
        )

        assertEquals(0L, delta.txSpeed)
        assertEquals(0L, delta.rxSpeed)
        assertEquals(5_000L, delta.txTotal)
        assertEquals(9_000L, delta.rxTotal)
    }

    // 2. 常规增量按经过时间换算为每秒速度
    @Test
    fun steadySample_computesPerSecondSpeed() {
        val delta = SystemUIStatusBarHooks.computeNetSpeedDelta(
            newTxBytes = 3_000L,
            newRxBytes = 11_000L,
            prevTxTotal = 1_000L,
            prevRxTotal = 1_000L,
            elapsedNanos = 2 * oneSecond,
        )

        assertEquals(1_000L, delta.txSpeed)
        assertEquals(5_000L, delta.rxSpeed)
        assertEquals(3_000L, delta.txTotal)
        assertEquals(11_000L, delta.rxTotal)
    }

    // 3. 计数器回绕/重置产生负增量时报告 0，但仍记录新基线
    @Test
    fun counterReset_reportsZeroButRebaselines() {
        val delta = SystemUIStatusBarHooks.computeNetSpeedDelta(
            newTxBytes = 10L,
            newRxBytes = 10L,
            prevTxTotal = 9_999L,
            prevRxTotal = 9_999L,
            elapsedNanos = oneSecond,
        )

        assertEquals(0L, delta.txSpeed)
        assertEquals(0L, delta.rxSpeed)
        assertEquals(10L, delta.txTotal)
        assertEquals(10L, delta.rxTotal)
    }

    // 4. 不可用样本（负值）报告 0 且不得写入负基线
    @Test
    fun unavailableSample_doesNotStoreNegativeBaseline() {
        val delta = SystemUIStatusBarHooks.computeNetSpeedDelta(
            newTxBytes = -1L,
            newRxBytes = -1L,
            prevTxTotal = 500_000L,
            prevRxTotal = 500_000L,
            elapsedNanos = oneSecond,
        )

        assertEquals(0L, delta.txSpeed)
        assertEquals(0L, delta.rxSpeed)
        assertEquals(0L, delta.txTotal)
        assertEquals(0L, delta.rxTotal)
    }

    // 5. 单侧不可用同样触发重新基线，避免另一侧被污染
    @Test
    fun partiallyUnavailableSample_rebaselinesBothDirections() {
        val delta = SystemUIStatusBarHooks.computeNetSpeedDelta(
            newTxBytes = 700_000L,
            newRxBytes = -1L,
            prevTxTotal = 500_000L,
            prevRxTotal = 500_000L,
            elapsedNanos = oneSecond,
        )

        assertEquals(0L, delta.txSpeed)
        assertEquals(0L, delta.rxSpeed)
        assertEquals(0L, delta.txTotal)
        assertEquals(0L, delta.rxTotal)
    }

    // 6. 回归核心：不可用样本之后的正常样本不得被当成一整秒的累计流量
    @Test
    fun sampleAfterUnavailableSample_doesNotReportCumulativeSpike() {
        val unavailable = SystemUIStatusBarHooks.computeNetSpeedDelta(
            newTxBytes = -1L,
            newRxBytes = -1L,
            prevTxTotal = 4_000_000_000L,
            prevRxTotal = 4_000_000_000L,
            elapsedNanos = oneSecond,
        )

        val next = SystemUIStatusBarHooks.computeNetSpeedDelta(
            newTxBytes = 4_000_001_000L,
            newRxBytes = 4_000_002_000L,
            prevTxTotal = unavailable.txTotal,
            prevRxTotal = unavailable.rxTotal,
            elapsedNanos = oneSecond,
        )

        // With a -1 baseline this would have been ~4 GB/s instead of a re-baseline.
        assertEquals(0L, next.txSpeed)
        assertEquals(0L, next.rxSpeed)
        assertEquals(4_000_001_000L, next.txTotal)
        assertEquals(4_000_002_000L, next.rxTotal)
    }

    // 7. 重新基线后的下一次样本恢复正常速度计算
    @Test
    fun sampleAfterRebaseline_resumesNormalSpeed() {
        val delta = SystemUIStatusBarHooks.computeNetSpeedDelta(
            newTxBytes = 4_000_003_000L,
            newRxBytes = 4_000_005_000L,
            prevTxTotal = 4_000_001_000L,
            prevRxTotal = 4_000_002_000L,
            elapsedNanos = oneSecond,
        )

        assertEquals(2_000L, delta.txSpeed)
        assertEquals(3_000L, delta.rxSpeed)
    }
}
