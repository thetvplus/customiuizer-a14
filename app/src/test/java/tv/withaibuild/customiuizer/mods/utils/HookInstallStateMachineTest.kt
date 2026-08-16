package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallStateMachineTest {

    @Test
    fun firstFailureIsRetryable() {
        val machine = HookInstallStateMachine()
        var calls = 0
        val ok = machine.install {
            calls++
            false
        }
        assertFalse(ok)
        assertEquals(1, calls)
        assertEquals(HookInstallStateMachine.State.UNINSTALLED, machine.state)
    }

    @Test
    fun secondAttemptSucceeds() {
        val machine = HookInstallStateMachine()
        val results = mutableListOf(false, true)
        assertFalse(machine.install { results.removeAt(0) })
        assertTrue(machine.install { results.removeAt(0) })
        assertEquals(HookInstallStateMachine.State.INSTALLED, machine.state)
    }

    @Test
    fun successOnlyOnce() {
        val machine = HookInstallStateMachine()
        var calls = 0
        assertTrue(machine.install { calls++; true })
        assertFalse(machine.install { calls++; true })
        assertEquals(HookInstallStateMachine.State.INSTALLED, machine.state)
        assertEquals(1, calls)
    }

    @Test
    fun reentrantInstallIsIgnored() {
        val machine = HookInstallStateMachine()
        var innerCalls = 0
        machine.install {
            innerCalls++
            // Reenter from inside the install block: the nested call must see INSTALLING and
            // return false immediately without starting another attempt.
            val nested = machine.install { innerCalls++; true }
            assertFalse(nested)
            true
        }
        assertEquals(1, innerCalls)
    }

    @Test
    fun fatalErrorPropagatesAndResetsToUninstalled() {
        val machine = HookInstallStateMachine()
        try {
            machine.install { throw OutOfMemoryError("fatal") }
            assertTrue("expected OutOfMemoryError", false)
        } catch (oom: OutOfMemoryError) {
            // expected
        }
        assertEquals(HookInstallStateMachine.State.UNINSTALLED, machine.state)
    }

    @Test
    fun linkageErrorIsNonFatalRetryable() {
        val machine = HookInstallStateMachine()
        assertFalse(machine.install { throw LinkageError("missing") })
        assertEquals(HookInstallStateMachine.State.UNINSTALLED, machine.state)
    }

    @Test
    fun noSuchMethodErrorIsNonFatalRetryable() {
        val machine = HookInstallStateMachine()
        assertFalse(machine.install { throw NoSuchMethodError("no method") })
        assertEquals(HookInstallStateMachine.State.UNINSTALLED, machine.state)
    }
}
