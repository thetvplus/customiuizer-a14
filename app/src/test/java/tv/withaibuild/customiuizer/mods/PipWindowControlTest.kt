package tv.withaibuild.customiuizer.mods

import org.junit.Assert.*
import org.junit.Test

class PipWindowControlTest {
    private class Native : PipWindowPort {
        var current: PipWindowPort.Task? = PipWindowPort.Task(Any(), Any())
        var registered = true
        var visible = true
        var policyHidden = false
        var stashed = false
        var releaseAllowed = true
        var registerAllowed = true
        var visibilityFailure: Throwable? = null
        val events = ArrayList<String>()
        var filter: (Boolean) -> Boolean = { it }
        override fun task() = current
        override fun inputRegistered() = registered
        override fun input(enabled: Boolean): Boolean {
            events.add("input:$enabled")
            if (enabled && !registerAllowed || !enabled && !releaseAllowed) return false
            registered = enabled
            return true
        }
        override fun visibility(visible: Boolean, releasing: Boolean) {
            events.add("visible:$visible")
            this.visible = filter(visible)
            policyHidden = !visible && !releasing
            visibilityFailure?.let { visibilityFailure = null; throw it }
        }
        override fun hideMenu() { events.add("menu") }
        override fun toggleStash(): Boolean { stashed = !stashed; events.add("stash:$stashed"); return true }
    }
    private val native = Native()
    private val controller = PipWindowControl(native).also { native.filter = it::nativeVisibility }
    private val task get() = native.current!!

    @Test fun fullHideAndRestorePairVisibilityAndInputWithoutChangingTheTask() {
        val original = task
        assertTrue(controller.toggleFull(original))
        assertFalse(native.visible); assertFalse(native.registered); assertTrue(controller.isHidden)
        assertTrue(native.policyHidden)
        assertSame(original, task)
        assertTrue(controller.toggleFull(original))
        assertTrue(native.visible); assertTrue(native.registered); assertFalse(controller.isHidden)
        assertFalse(native.policyHidden)
        assertEquals(listOf("menu", "input:false", "visible:false", "visible:true", "input:true"), native.events)
    }

    @Test fun keyguardCannotRevealAHiddenWindowAndManualRestoreWhileLockedIsRejected() {
        controller.toggleFull(task)
        native.current = task.copy(locked = true)
        assertFalse(controller.nativeVisibility(false))
        val before = native.events.size
        assertFalse(controller.toggleFull(task))
        assertEquals(before, native.events.size)
        native.current = task.copy(locked = false)
        assertFalse(controller.nativeVisibility(true))
        assertTrue(controller.toggleFull(task))
        assertTrue(native.visible); assertTrue(native.registered)
    }

    @Test fun nativeAnimationOrExitReclaimsOnlyTheOwnedSurfaceAndItsInput() {
        controller.toggleFull(task)
        controller.nativeTransition(Any())
        assertTrue(controller.isHidden)
        controller.nativeTransition(task.surface)
        assertFalse(controller.isHidden)
        assertTrue(native.visible); assertTrue(native.registered)
    }

    @Test fun aNativeInvisibleRequestIsPreservedWhenTheLeaseIsReleased() {
        controller.toggleFull(task)
        controller.nativeVisibility(false)
        controller.nativeTransition()
        assertFalse(native.visible)
        assertFalse("The native alpha request must not retain our Surface hidden flag", native.policyHidden)
        assertTrue(native.registered)
        assertFalse(controller.isHidden)
    }

    @Test fun replacementTasksNeverReceiveAnOldCommandOrAnOldVisibilityRestore() {
        val old = task
        controller.toggleFull(old)
        native.current = PipWindowPort.Task(Any(), Any())
        native.registered = true; native.visible = true
        val count = native.events.size
        assertFalse(controller.toggleFull(old))
        controller.nativeTransition()
        assertEquals(count, native.events.size)
        assertFalse(controller.isHidden)
    }

    @Test fun missingUnstableLockedAndSecondaryDisplayTasksAreRejectedWithoutSideEffects() {
        val original = task
        for (candidate in listOf(original.copy(stable = false), original.copy(locked = true), original.copy(display = 1))) {
            native.current = candidate
            assertFalse(controller.toggleFull(candidate)); assertFalse(controller.toggleStash(candidate))
        }
        native.current = null
        assertFalse(controller.toggleFull(original))
        assertTrue(native.events.isEmpty())
    }

    @Test fun failedInputReleaseNeverHidesTheWindow() {
        native.releaseAllowed = false
        assertFalse(controller.toggleFull(task))
        assertTrue(native.visible); assertTrue(native.registered); assertFalse(controller.isHidden)
        assertFalse(native.events.any { it.startsWith("visible:") })
    }

    @Test fun failureAfterHidingRepairsBothSurfaceAndInputAndPreservesFatalErrors() {
        for (failure in listOf(IllegalStateException("native failure"), OutOfMemoryError("fatal"))) {
            native.visibilityFailure = failure
            assertSame(failure, assertThrows(failure.javaClass) { controller.toggleFull(task) })
            assertTrue(native.visible); assertTrue(native.registered); assertFalse(controller.isHidden)
        }
    }

    @Test fun failedInputRestoreKeepsARecoverableHiddenLeaseAndCanBeRetried() {
        controller.toggleFull(task)
        native.registerAllowed = false
        assertFalse(controller.toggleFull(task))
        assertFalse(native.visible); assertFalse(native.registered); assertTrue(controller.isHidden)
        native.registerAllowed = true
        assertTrue(controller.toggleFull(task))
        assertTrue(native.visible); assertTrue(native.registered); assertFalse(controller.isHidden)
    }

    @Test fun edgeStashingRemainsANativeActionAndCanFollowAFullHide() {
        controller.toggleFull(task)
        assertTrue(controller.toggleStash(task))
        assertTrue(native.visible); assertTrue(native.registered); assertTrue(native.stashed)
        assertFalse(controller.isHidden)
        assertTrue(controller.toggleStash(task))
        assertFalse(native.stashed)
    }
}
