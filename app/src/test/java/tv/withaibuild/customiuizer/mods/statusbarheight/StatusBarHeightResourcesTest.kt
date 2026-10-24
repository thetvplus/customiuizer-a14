package tv.withaibuild.customiuizer.mods.statusbarheight

import org.junit.Assert.*
import org.junit.Test

class StatusBarHeightResourcesTest {
    @Test fun enabledRuntimeNeverPersistsAnEnabledPreferenceAndStockRebootRestoresDisabledState() {
        var desired = true // An external tool attempted to enable our ID.
        var state = 3
        var calls = 0
        assertEquals(true, StatusBarHeightResources.updateState(
            clearDesired = { val changed = desired; desired = false; changed },
            proceed = { calls++; state = if (desired) 3 else 2; true },
            enabled = { true }, readState = { state }, enableRuntime = { state = 3; true }))
        assertEquals(1, calls)
        assertFalse(desired)
        assertEquals(3, state)
        // Native boot calculation from the persisted flag, without the module callback.
        state = if (desired) 3 else 2
        assertEquals(2, state)
    }

    @Test fun disabledFeatureAndInvalidNativeStatesCannotBeEnabled() {
        for (nativeState in -1..7) for (enabled in listOf(false, true)) {
            var enabledCalls = 0
            StatusBarHeightResources.updateState({ false }, { false }, { enabled }, { nativeState }, { enabledCalls++; true })
            assertEquals(if (enabled && nativeState == 2) 1 else 0, enabledCalls)
        }
    }

    @Test fun nativeExceptionIsPreservedAndNeverRetried() {
        val expected = IllegalStateException("native failure")
        var calls = 0
        try {
            StatusBarHeightResources.updateState({ false }, { calls++; throw expected }, { true }, { 2 }, { true })
            fail()
        } catch (actual: IllegalStateException) { assertSame(expected, actual) }
        assertEquals(1, calls)
    }
}
