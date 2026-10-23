package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandSystemFeedbackTest {
    private fun mode(kind: IslandSystemKind, value: Int = 1) = IslandSystemActivity(kind, value)

    @Test fun dndAndItsRingerConsequencesProduceOneFeedbackInEitherArrivalOrder() {
        for (reverse in listOf(false, true)) {
            val slot = IslandSystemFeedback()
            if (reverse) slot.offer(mode(IslandSystemKind.RINGER, 2), 0)
            slot.offer(mode(IslandSystemKind.DND), 50)
            if (!reverse) slot.offer(mode(IslandSystemKind.RINGER, 2), 100)
            assertNull(slot.poll(199))
            assertEquals(mode(IslandSystemKind.DND), slot.poll(250)?.activity)
            assertNull(slot.poll(1000))
        }
    }

    @Test fun airplaneKeepsOnlyItsPrimaryFeedbackButRadioOnAndExplicitCommandsRemainIndependent() {
        val slot = IslandSystemFeedback()
        slot.offer(mode(IslandSystemKind.WIFI, 0), 0)
        slot.offer(mode(IslandSystemKind.AIRPLANE), 50)
        slot.offer(mode(IslandSystemKind.BLUETOOTH, 0), 100)
        slot.offer(mode(IslandSystemKind.DATA, 0), 150)
        assertEquals(mode(IslandSystemKind.AIRPLANE), slot.poll(200)?.activity)
        slot.offer(mode(IslandSystemKind.WIFI), 220)
        assertEquals(mode(IslandSystemKind.WIFI), slot.poll(420)?.activity)
        slot.offer(mode(IslandSystemKind.DND), 500)
        slot.independent(IslandSystemKind.RINGER, 510)
        assertEquals(mode(IslandSystemKind.DND), slot.offer(mode(IslandSystemKind.RINGER), 520)?.activity)
        assertEquals(mode(IslandSystemKind.RINGER), slot.poll(700)?.activity)
    }

    @Test fun unrelatedOperationsPreemptWithoutBeingSwallowedOrBuildingAPlaybackQueue() {
        val slot = IslandSystemFeedback()
        slot.offer(mode(IslandSystemKind.DND), 0)
        assertEquals(mode(IslandSystemKind.DND), slot.offer(mode(IslandSystemKind.TORCH), 50)?.activity)
        assertEquals(mode(IslandSystemKind.TORCH), slot.poll(200)?.activity)
        assertNull(slot.poll(10_000))
    }

    @Test fun floodKeepsOneLatestStateWithAFixedDeadlineAndNoStaleReplay() {
        val slot = IslandSystemFeedback()
        repeat(10_000) { slot.offer(mode(IslandSystemKind.NFC, it), it % 200L) }
        assertNull(slot.poll(199))
        assertEquals(9999, slot.poll(200)?.activity?.value)
        assertNull(slot.poll(400))
        slot.offer(mode(IslandSystemKind.TORCH), 500)
        assertNull(slot.poll(500 + IslandSystemFeedback.LIFETIME))
        slot.offer(mode(IslandSystemKind.NFC), 5000)
        slot.clear()
        assertNull(slot.poll(5200))
    }

}
