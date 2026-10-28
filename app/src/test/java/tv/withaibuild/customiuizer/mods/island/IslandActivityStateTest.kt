package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandActivityStateTest {
    @Test fun newerPersistentActivityWinsAndEndingItRevealsThePreviousSlotWithoutClearingIt() {
        val state = IslandActivityState<String, String>()
        state.setActivity("clock", "00:12")
        state.setMedia("player", "music")
        assertNull(state.activity(0)); assertEquals("music", state.media(0))
        state.setActivity("clock", "00:13")
        state.setMedia("player", "track metadata")
        assertEquals("player", state.identity(1))
        state.mediaEligible = false
        assertEquals("00:13", state.activity(2))
        state.mediaEligible = true
        assertEquals("player", state.identity(3))
        state.setMedia("player", null)
        assertEquals("clock", state.identity(4)); assertEquals("00:13", state.activity(4))
        state.setMedia("player", "resumed"); state.activateMedia("player")
        assertEquals("player", state.identity(5))
        state.setActivity("alarm", "ringing", urgent = true)
        state.activateMedia("player")
        assertEquals("alarm", state.identity(6))
        state.setActivity(null, null)
        assertEquals("resumed", state.media(7))
        state.dismiss(); assertNull(state.media(8)); assertNull(state.identity(8))
    }

    @Test fun liveActivityPreemptsMediaButTemporaryFeedbackRestoresTheSameLiveIdentity() {
        val state = IslandActivityState<String, String>()
        state.setMedia("player", "music")
        state.setActivity("timer", "00:12")
        assertEquals("timer", state.identity(0))
        assertNull(state.media(0)); assertEquals("music", state.uncoveredMedia())
        val lease = state.offer("copied", 0, 100)
        assertEquals("copied", state.toast(20)); assertNull(state.activity(20))
        state.release(lease)
        assertEquals("00:12", state.activity(30)); assertEquals("timer", state.identity(30))
        state.allowed = false
        assertNull(state.activity(40)); assertNull(state.uncoveredMedia())
        state.allowed = true; state.dismiss()
        assertEquals("music", state.media(50))
        state.setActivity("timer2", "00:20"); state.clear(preserveDismissal = true)
        assertNull(state.activity(60)); assertNull(state.identity(60))
    }
    @Test fun leaseOwnershipExpiresWithoutAllowingAnOldFeedbackCallbackToReleaseItsSuccessor() {
        val state = IslandActivityState<String, String>()
        val first = state.offer("system", 0, 200)
        assertTrue(state.owns(first, 199))
        assertFalse(state.owns(first, 200))
        val next = state.offer("app toast", 200, 1000)
        state.release(first)
        assertFalse(state.owns(first, 201))
        assertTrue(state.owns(next, 201))
        assertEquals(1200L, state.deadline())
    }

    @Test fun dismissalSurvivesOwnerRebuildUntilAnExplicitResetOrNewSession() {
        val shared = IslandActivityState.MediaDismissal()
        val first = IslandActivityState<String, String>(shared)
        first.setMedia("session", "track one"); first.dismiss()
        first.clear(preserveDismissal = true)
        val next = IslandActivityState<String, String>(shared)
        next.setMedia("session", "track two")
        assertNull(next.media(0))
        next.allowMediaAgain()
        assertEquals("track two", next.media(0))
        next.dismiss(); next.sessionDestroyed("session")
        next.setMedia("new session", "track three")
        assertEquals("track three", next.media(0))
    }

    @Test fun temporaryFeedbackRestoresTheLatestMediaAndStaleReleaseCannotCloseIt() {
        val state = IslandActivityState<String, String>()
        state.setMedia("player", "song 1")
        val first = state.offer("saved", 0, 3000)
        val second = state.offer("copied", 100, 3000)
        state.setMedia("player", "song 2")
        state.release(first)
        assertEquals("copied", state.toast(200))
        assertNull(state.media(200))
        state.release(second)
        assertEquals("song 2", state.media(200))
    }

    @Test fun updatesNeverRenewTheDeadlineAndPrivacyDoesNotFreezeTime() {
        val state = IslandActivityState<String, String>()
        val lease = state.offer("copying", 0, 2000)
        state.setMedia(1, "music")
        state.update(lease, "copied")
        assertEquals(2000L, state.deadline())
        state.allowed = false
        assertNull(state.toast(100))
        assertNull(state.media(3000))
        state.allowed = true
        assertNull(state.toast(3000))
        assertEquals("music", state.media(3000))
    }

    @Test fun dismissalPrivacyAndStopDoNotResurrectStaleActivities() {
        val state = IslandActivityState<String, String>()
        state.setMedia("track 1", "playing")
        state.dismiss()
        state.setMedia("track 1", "paused")
        assertNull(state.media(0))
        state.setMedia("track 2", "playing")
        state.allowed = false
        assertNull(state.media(0))
        state.allowed = true
        assertEquals("playing", state.media(0))
        state.setMedia(null, null)
        assertNull(state.media(0))
        state.clear()
        assertNull(state.toast(0))
    }
}
