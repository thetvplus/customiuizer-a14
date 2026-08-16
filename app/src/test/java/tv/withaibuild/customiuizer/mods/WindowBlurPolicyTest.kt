package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBlurPolicyTest {
    @Test
    fun modulePolicyOnlyAddsAReasonToDisableBlur() {
        assertFalse(SystemDisplayHooks.resolveWindowBlursDisabled(false, false))
        assertTrue(SystemDisplayHooks.resolveWindowBlursDisabled(true, false))
        assertTrue(SystemDisplayHooks.resolveWindowBlursDisabled(false, true))
        assertTrue(SystemDisplayHooks.resolveWindowBlursDisabled(true, true))
    }
}
