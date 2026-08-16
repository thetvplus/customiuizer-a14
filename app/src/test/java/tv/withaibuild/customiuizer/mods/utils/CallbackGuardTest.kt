package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CallbackGuardTest {

    @Test
    fun logsRuntimeExceptionAndContinues() {
        var executed = false
        CallbackGuard.guarded { executed = true; throw RuntimeException("test") }
        assertTrue(" guarded block should have been executed", executed)
    }

    @Test
    fun rethrowsThreadDeath() {
        var executed = false
        try {
            CallbackGuard.guarded { executed = true; throw ThreadDeath() }
            fail("ThreadDeath must propagate through guarded")
        } catch (ignored: ThreadDeath) {
            // expected
        }
        assertTrue("guarded block should have been executed before throw", executed)
    }

    @Test
    fun rethrowsVirtualMachineError() {
        var executed = false
        try {
            CallbackGuard.guarded { executed = true; throw InternalError("test") }
            fail("VirtualMachineError must propagate through guarded")
        } catch (ignored: InternalError) {
            // expected
        }
        assertTrue("guarded block should have been executed before throw", executed)
    }

    @Test
    fun rethrowsOutOfMemoryError() {
        var executed = false
        try {
            CallbackGuard.guarded { executed = true; throw OutOfMemoryError("test") }
            fail("OutOfMemoryError must propagate through guarded")
        } catch (ignored: OutOfMemoryError) {
            // expected
        }
        assertTrue("guarded block should have been executed before throw", executed)
    }

    @Test
    fun fallback_returnsFallbackForRuntimeException() {
        val result = CallbackGuard.guarded("fallback") {
            throw IllegalStateException("test")
            "ok"
        }
        assertEquals("fallback", result)
    }

    @Test
    fun fallback_rethrowsThreadDeath() {
        try {
            CallbackGuard.guarded("fallback") { throw ThreadDeath() }
            fail("ThreadDeath must propagate through guarded fallback")
        } catch (ignored: ThreadDeath) {
            // expected
        }
    }

    @Test
    fun fallback_rethrowsVirtualMachineError() {
        try {
            CallbackGuard.guarded("fallback") { throw InternalError("test") }
            fail("VirtualMachineError must propagate through guarded fallback")
        } catch (ignored: InternalError) {
            // expected
        }
    }
}
