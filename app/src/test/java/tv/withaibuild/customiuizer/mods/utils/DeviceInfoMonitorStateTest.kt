package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoMonitorStateTest {

    @Test
    fun startNewGenerationReturnsMonotonicIds() {
        val state = DeviceInfoMonitorState()
        val first = state.startNewGeneration()
        val second = state.startNewGeneration()
        val third = state.startNewGeneration()

        assertTrue(first < second)
        assertTrue(second < third)
        assertEquals(third, state.activeBgHandlerId)
        assertEquals(third, state.activeMainHandlerId)
    }

    @Test
    fun oldGenerationIsRejected() {
        val state = DeviceInfoMonitorState()
        val oldId = state.startNewGeneration()
        state.startNewGeneration()

        assertFalse(state.isActive(oldId))
        assertFalse(state.isActiveBg(oldId))
        assertFalse(state.isActiveMain(oldId))
    }

    @Test
    fun currentGenerationIsActive() {
        val state = DeviceInfoMonitorState()
        val id = state.startNewGeneration()

        assertTrue(state.isActive(id))
        assertTrue(state.isActiveBg(id))
        assertTrue(state.isActiveMain(id))
    }

    @Test
    fun stopRejectsAllGenerations() {
        val state = DeviceInfoMonitorState()
        val id = state.startNewGeneration()
        state.stop()

        assertFalse(state.isActive(id))
        assertEquals(-1, state.activeBgHandlerId)
        assertEquals(-1, state.activeMainHandlerId)
    }

    @Test
    fun startNewGenerationAfterStopResumesWithFreshId() {
        val state = DeviceInfoMonitorState()
        val first = state.startNewGeneration()
        state.stop()
        val second = state.startNewGeneration()

        assertTrue(second > first)
        assertTrue(state.isActive(second))
    }

    @Test
    fun consecutiveFailCountBacksOff() {
        val state = DeviceInfoMonitorState()

        state.resetFailCount()
        assertEquals(2000L, state.calculateDelay(2000L, 60000L))

        state.bumpFailCount()
        assertEquals(4000L, state.calculateDelay(2000L, 60000L))

        state.bumpFailCount()
        assertEquals(8000L, state.calculateDelay(2000L, 60000L))

        state.bumpFailCount()
        state.bumpFailCount()
        state.bumpFailCount()
        assertEquals(60000L, state.calculateDelay(2000L, 60000L))

        state.resetFailCount()
        assertEquals(2000L, state.calculateDelay(2000L, 60000L))
    }

    @Test
    fun screenOnDefaultsToTrue() {
        val state = DeviceInfoMonitorState()
        assertTrue(state.screenOn)
        state.screenOn = false
        assertFalse(state.screenOn)
    }

    @Test
    fun shouldPublishRejectsOldGeneration() {
        val state = DeviceInfoMonitorState()
        val oldId = state.startNewGeneration()
        state.startNewGeneration()

        assertFalse(state.shouldPublish(oldId, 91, true, "10℃ 100mA"))
        assertEquals(Pair(false, ""), state.getLastPublished(91))
    }

    @Test
    fun commitPublishedOnlyAcceptsCurrentGeneration() {
        val state = DeviceInfoMonitorState()
        val oldId = state.startNewGeneration()
        val newId = state.startNewGeneration()

        assertFalse(state.commitPublished(oldId, 91, true, "stale"))
        assertTrue(state.commitPublished(newId, 91, true, "fresh"))
        assertEquals(Pair(true, "fresh"), state.getLastPublished(91))
    }

    @Test
    fun startNewGenerationResetsPublishedState() {
        val state = DeviceInfoMonitorState()
        val first = state.startNewGeneration()
        state.commitPublished(first, 91, true, "10℃ 100mA")
        val second = state.startNewGeneration()

        assertEquals(Pair(false, ""), state.getLastPublished(91))
        assertTrue(state.shouldPublish(second, 91, true, "10℃ 100mA"))
    }

    @Test
    fun stopResetsPublishedState() {
        val state = DeviceInfoMonitorState()
        val first = state.startNewGeneration()
        state.commitPublished(first, 92, true, "45℃")
        state.stop()

        assertEquals(Pair(false, ""), state.getLastPublished(92))
        assertFalse(state.shouldPublish(first, 92, true, "45℃"))
    }

    @Test
    fun shouldPublishDeduplicatesSameText() {
        val state = DeviceInfoMonitorState()
        val id = state.startNewGeneration()

        assertTrue(state.shouldPublish(id, 91, true, "10℃ 100mA"))
        state.commitPublished(id, 91, true, "10℃ 100mA")
        assertFalse(state.shouldPublish(id, 91, true, "10℃ 100mA"))
    }

    @Test
    fun oldGenerationDoesNotPolluteNewGenerationState() {
        val state = DeviceInfoMonitorState()
        val oldId = state.startNewGeneration()

        // Simulate an old message being accepted after a new generation has started.
        val newId = state.startNewGeneration()
        state.commitPublished(oldId, 91, true, "stale")

        // The stale commit must not have written into the new generation's state.
        assertEquals(Pair(false, ""), state.getLastPublished(91))
        assertTrue(state.shouldPublish(newId, 91, true, "stale"))
    }

    @Test
    fun commitPublishedChecksGenerationBeforeUpdating() {
        val state = DeviceInfoMonitorState()
        val id = state.startNewGeneration()

        // Mimic the exact check the main handler performs: generation must match and
        // the handler id must still be active.
        val updateType = 92
        val updateShow = true
        val updateText = "45℃"
        val accepted = id == id && state.isActiveMain(id) && state.commitPublished(id, updateType, updateShow, updateText)

        assertTrue(accepted)
        assertEquals(Pair(updateShow, updateText), state.getLastPublished(updateType))
    }

    @Test
    fun stopInvalidatesInFlightMessages() {
        val state = DeviceInfoMonitorState()
        val id = state.startNewGeneration()
        state.commitPublished(id, 91, true, "10℃")
        state.stop()

        assertFalse(state.isActiveMain(id))
        assertFalse(state.shouldPublish(id, 91, true, "10℃"))
        assertEquals(Pair(false, ""), state.getLastPublished(91))
    }
}
