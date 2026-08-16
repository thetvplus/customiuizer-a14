package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkTintRegistrationStateTest {

    @Test
    fun registersExactlyOnce() {
        val state = DarkTintRegistrationState("owner", "test")
        var registerCalls = 0
        val ok = state.register(registerFn = { registerCalls++; true })
        assertTrue(ok)
        assertEquals(1, registerCalls)

        val second = state.register(registerFn = { registerCalls++; true })
        assertFalse(second)
        assertEquals(1, registerCalls)
        assertTrue(state.isActive)
    }

    @Test
    fun releasesAreIdempotent() {
        val state = DarkTintRegistrationState("owner", "test")
        var releaseCalls = 0
        state.register(registerFn = { true })

        val first = state.release { releaseCalls++ }
        assertTrue(first)
        assertEquals(1, releaseCalls)
        assertFalse(state.isActive)
        assertTrue(state.isReleased)

        val second = state.release { releaseCalls++ }
        assertFalse(second)
        assertEquals(1, releaseCalls)
    }

    @Test
    fun releaseWithoutRegisterDoesNotCallReleaseFn() {
        val state = DarkTintRegistrationState("owner", "test")
        var releaseCalls = 0
        val released = state.release { releaseCalls++ }
        assertFalse(released)
        assertEquals(0, releaseCalls)
        assertTrue(state.isReleased)
    }

    @Test
    fun registerAppliesInitialTintOnlyOnSuccess() {
        val state = DarkTintRegistrationState("owner", "test")
        var tintCalls = 0
        var registerCalls = 0

        val first = state.register(
            registerFn = { registerCalls++; false },
            applyInitialTint = { tintCalls++; true }
        )
        assertFalse(first)
        assertEquals(1, registerCalls)
        assertEquals(0, tintCalls)
        assertFalse(state.isActive)

        val second = state.register(
            registerFn = { registerCalls++; true },
            applyInitialTint = { tintCalls++; true }
        )
        assertTrue(second)
        assertEquals(2, registerCalls)
        assertEquals(1, tintCalls)
        assertTrue(state.isActive)
    }

    @Test
    fun releasedStateCanRegisterAgain() {
        val state = DarkTintRegistrationState("owner", "test")
        state.register(registerFn = { true })
        state.release { }

        assertFalse(state.isActive)
        val reRegister = state.register(registerFn = { true })
        assertTrue(reRegister)
        assertTrue(state.isActive)
    }

    @Test
    fun releasedStateDoesNotSetReleasedOnSubsequentRegisterUntilReleasedAgain() {
        val state = DarkTintRegistrationState("owner", "test")
        state.register(registerFn = { true })
        state.release { }
        state.register(registerFn = { true })

        assertTrue(state.isActive)
        assertFalse(state.isReleased)
    }

    @Test
    fun disposeWithoutRegisterStillCallsDisposeFn() {
        val state = DarkTintRegistrationState("owner", "test")
        var disposeCalls = 0
        var wasRegistered: Boolean? = null
        val ok = state.dispose { wasRegistered = it; disposeCalls++ }

        assertTrue(ok)
        assertEquals(1, disposeCalls)
        assertFalse(wasRegistered!!)
        assertTrue(state.isDisposed)
        assertFalse(state.isActive)
    }

    @Test
    fun disposeWithRegisteredCallsDisposeFnAndMarksNotActive() {
        val state = DarkTintRegistrationState("owner", "test")
        var disposeCalls = 0
        var wasRegistered: Boolean? = null
        state.register(registerFn = { true })

        val ok = state.dispose { wasRegistered = it; disposeCalls++ }
        assertTrue(ok)
        assertEquals(1, disposeCalls)
        assertTrue(wasRegistered!!)
        assertFalse(state.isActive)
        assertTrue(state.isDisposed)
    }

    @Test
    fun disposeIsIdempotent() {
        val state = DarkTintRegistrationState("owner", "test")
        var disposeCalls = 0
        state.register(registerFn = { true })
        state.dispose { disposeCalls++ }

        val second = state.dispose { disposeCalls++ }
        assertFalse(second)
        assertEquals(1, disposeCalls)
    }

    @Test
    fun disposeBlocksRegisterAndRelease() {
        val state = DarkTintRegistrationState("owner", "test")
        var fnCalls = 0
        state.dispose { fnCalls++ }

        assertFalse(state.register(registerFn = { fnCalls++; true }))
        assertFalse(state.release { fnCalls++ })
        assertEquals(1, fnCalls)
    }

    @Test
    fun releaseThenDisposeStillCallsDisposeFnOnce() {
        val state = DarkTintRegistrationState("owner", "test")
        var disposeCalls = 0
        var wasRegistered: Boolean? = null
        state.register(registerFn = { true })
        state.release { }

        val ok = state.dispose { wasRegistered = it; disposeCalls++ }
        assertTrue(ok)
        assertFalse(wasRegistered!!)
        assertEquals(1, disposeCalls)
    }

    @Test
    fun disposeThenReleaseIsNoOp() {
        val state = DarkTintRegistrationState("owner", "test")
        var fnCalls = 0
        state.register(registerFn = { true })
        state.dispose { fnCalls++ }

        val released = state.release { fnCalls++ }
        assertFalse(released)
        assertEquals(1, fnCalls)
    }

    @Test
    fun ownerAndRouteAreStored() {
        val state = DarkTintRegistrationState("owner-42", "left")
        assertEquals("owner-42", state.owner)
        assertEquals("left", state.route)
    }

    @Test
    fun isActiveReflectsRegistrationReleaseAndDispose() {
        val state = DarkTintRegistrationState("owner", "test")
        assertFalse(state.isActive)

        state.register(registerFn = { true })
        assertTrue(state.isActive)

        state.release { }
        assertFalse(state.isActive)

        state.dispose { }
        assertFalse(state.isActive)
    }
}
