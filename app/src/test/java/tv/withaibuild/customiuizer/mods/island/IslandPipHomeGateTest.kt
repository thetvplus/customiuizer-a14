package tv.withaibuild.customiuizer.mods.island

import android.content.ComponentName
import android.os.Looper
import android.os.UserHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandPipHomeGateTest {
    private val tasks = TestScope()
    private val dispatcher = StandardTestDispatcher(tasks.testScheduler)
    private val user = UserHandle.getUserHandleForUid(1000)
    private val updates = mutableListOf<IslandPipHomeGate.Update>()
    private fun gate(probe: IslandPipCandidatePort) = IslandPipHomeGate(tasks, dispatcher, probe, updates::add)
    private fun snapshot(generation: Long, state: Int, task: Int = 17, pkg: String = "video") =
        IslandPipSnapshot(generation, state, ComponentName(pkg, "Player"), user, task)

    @Test fun ordinaryMusicReleasesTheProbeWithoutWaitingForTheDeadline() {
        var reads = 0
        val gate = gate { _, _ -> reads++; null }
        gate.begin("music", user); tasks.testScheduler.runCurrent()
        assertEquals(1, reads)
        assertNull(gate.source)
        assertEquals(1, updates.size)
        assertFalse(updates.single().confirmed)
        assertFalse(updates.single().refreshMedia)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandPipHomeGate.TIMEOUT + 1))
        assertEquals(1, updates.size)
        gate.close()
    }

    @Test fun declaredAutoPipWaitsAcrossTheOrdinaryMediaDeadlineUntilItsOwnNativeTaskSettles() {
        val gate = gate { pkg, who -> IslandPipCandidate(pkg, who, 17) }
        gate.begin("video", user); tasks.testScheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        assertNotNull(gate.source)
        assertTrue(gate.confirmed)
        gate.nativeChanged(snapshot(1, 3))
        gate.nativeChanged(snapshot(2, 4, task = 19, pkg = "other.video"))
        assertNotNull(gate.source)
        gate.nativeChanged(snapshot(3, 4), renderNow = false)
        assertNull(gate.source)
        assertTrue(updates.last().confirmed)
        assertTrue(updates.last().refreshMedia)
        assertFalse(updates.last().renderNow)
        gate.close()
    }

    @Test fun anOldPinnedSnapshotCannotFinishANewFullscreenCandidate() {
        val gate = gate { pkg, who -> IslandPipCandidate(pkg, who, 17) }
        gate.nativeChanged(snapshot(5, 4))
        gate.begin("video", user); tasks.testScheduler.runCurrent()
        assertNotNull(gate.source)
        gate.nativeChanged(snapshot(6, 4))
        assertNull(gate.source)
        gate.close()
    }

    @Test fun nativeCancellationAndLostNativeEventsReleaseTheOnlyPendingIntent() {
        val gate = gate { pkg, who -> IslandPipCandidate(pkg, who, 17) }
        gate.begin("video", user); tasks.testScheduler.runCurrent()
        gate.nativeChanged(snapshot(1, 3)); gate.nativeChanged(snapshot(2, 0))
        assertNull(gate.source)
        assertTrue(updates.last().refreshMedia)
        gate.begin("video", user); tasks.testScheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandPipHomeGate.TIMEOUT + 1))
        assertNull(gate.source)
        assertTrue(updates.last().refreshMedia)
        gate.close()
    }

    @Test fun replacingOrClosingAnOwnerInvalidatesLateQueriesAndDequeuedOldExpiry() {
        val answer = CompletableDeferred<IslandPipCandidate?>()
        val gate = gate { _, _ -> answer.await() }
        gate.begin("old", user); tasks.testScheduler.runCurrent()
        gate.begin("new", user); tasks.testScheduler.runCurrent()
        ReflectionHelpers.getField<Runnable>(gate, "expiry").run()
        assertEquals("new", gate.source!!.packageName)
        gate.close()
        answer.complete(IslandPipCandidate("old", user, 17)); tasks.testScheduler.runCurrent()
        assertNull(gate.source)
        assertTrue(updates.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandPipHomeGate.TIMEOUT + 1))
        assertTrue(updates.isEmpty())
    }
}
