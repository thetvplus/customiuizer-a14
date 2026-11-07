package tv.withaibuild.customiuizer.mods.recents

import org.junit.Assert.*
import org.junit.Test

class DeckPoseCompletionTest {
    @Test fun completedNativeWorkDoesNotKeepLaterGestureSamplesOnASpring() {
        val gate=DeckPoseCompletion()
        var calls=0
        assertFalse(gate.active)
        val end=gate.track(Runnable { calls++; assertTrue(gate.active) })
        // updateTaskView returned; native translationY spring still owns work.
        repeat(30) { assertTrue(gate.active) }
        end.run()
        assertEquals(1,calls)
        repeat(120) { assertFalse("Later window samples must be applied directly",gate.active) }
    }

    @Test fun overlappingAndReentrantCompletionsDoNotReleaseNewerWork() {
        val gate=DeckPoseCompletion()
        lateinit var newer: DeckPoseCompletion.Completion
        val first=gate.track(Runnable { newer=gate.track(Runnable {}) })
        val second=gate.track(Runnable {})
        first.run(); second.run()
        assertTrue(gate.active)
        // Late repeated native notification must not consume the newer ticket.
        second.run(); second.finish()
        assertTrue(gate.active)
        newer.run()
        assertFalse(gate.active)
    }

    @Test fun originalExceptionAndRepeatedCallbackSemanticsArePreserved() {
        val gate=DeckPoseCompletion()
        val failure=IllegalStateException("native completion")
        var calls=0
        val end=gate.track(Runnable { calls++; throw failure })
        repeat(2) {
            try { end.run(); fail("Original exception must propagate") }
            catch(actual: IllegalStateException) { assertSame(failure,actual) }
            assertFalse(gate.active)
        }
        assertEquals(2,calls)
        val failedInvocation=gate.track(Runnable { fail("Failed invocation must not synthesize completion") })
        failedInvocation.finish(); failedInvocation.finish()
        assertFalse(gate.active)
    }
}
