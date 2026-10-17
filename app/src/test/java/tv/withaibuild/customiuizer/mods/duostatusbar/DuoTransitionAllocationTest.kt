package tv.withaibuild.customiuizer.mods.duostatusbar

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DuoTransitionAllocationTest {
    @Test fun preparedCenterStateAndRapidTransitionsAllocateNoPerEventObjects() {
        val meter = ManagementFactory.getThreadMXBean() as ThreadMXBean
        assumeTrue(meter.isThreadAllocatedMemorySupported)
        meter.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().threadId()
        val config = DuoConfig(true, 24)
        val transition = DuoTransition()
        val signals = DuoNetworkState().apply { subscriptions(10, -1, true) }
        val headphones = DuoAudioState()
        val textConfig = DuoConfig(true, 24, cellularStyle = 2)
        fun exercise(count: Int): Int {
            var checksum = 0
            headphones.clear()
            for (i in 0 until count) {
                val network = DuoNetworkState.WIFI_CONNECTED or ((i % 5) shl 4)
                headphones.wired(i % 4 == 0, i.toLong(), true)
                headphones.bluetooth(true, i % 4 == 1, true, false, i.toLong(), true)
                headphones.bluetoothBattery(i % 11, true)
                transition.change(DuoCenter.key(network, headphones.snapshot, config), i.toLong(), 180L)
                val progress = transition.fraction(i.toLong())
                checksum += transition.drawAlpha(progress) + transition.drawKey(progress)
                signals.mobile(0, 10, i % 6, true, true, 5, DuoCellularType.generation("5G"))
                checksum += DuoGeometry.signalColor(-1, signals.snapshot, i % 4) ushr 24
                checksum += DuoCellularType.label(DuoCenter.value(DuoCenter.key(signals.snapshot, 0, textConfig))).length
            }
            return checksum
        }
        repeat(5) { exercise(100000) }
        val before = meter.getThreadAllocatedBytes(thread)
        val checksum = exercise(1000000)
        val allocated = meter.getThreadAllocatedBytes(thread) - before
        println("Duo audio/center/transition/signals: 1000000 events, $allocated JVM allocated bytes, checksum=$checksum")
        assertTrue("Unexpected per-event allocation: $allocated bytes", allocated <= 4096L)
    }
}
