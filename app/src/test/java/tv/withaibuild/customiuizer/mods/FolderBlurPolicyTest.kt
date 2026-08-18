package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderBlurPolicyTest {

    @Test
    fun openCloseNeverFallsThroughToHyperosDefaultWhenOverrideIsOn() {
        assertNull(LauncherFolderHooks.resolveAppliedFolderBlurRatio(false, true, 0.5f))
        assertEquals(0f, LauncherFolderHooks.resolveAppliedFolderBlurRatio(true, true, 0f)!!, 0.001f)
        assertEquals(0.6f, LauncherFolderHooks.resolveAppliedFolderBlurRatio(true, true, 0.6f)!!, 0.001f)
        assertEquals(0f, LauncherFolderHooks.resolveAppliedFolderBlurRatio(true, false, 0.6f)!!, 0.001f)
    }

    @Test
    fun dragFramesOnlyClampFastBlurWhileFolderIsActive() {
        assertFalse(LauncherFolderHooks.shouldClampFolderFastBlur(false, true))
        assertFalse(LauncherFolderHooks.shouldClampFolderFastBlur(true, false))
        assertTrue(LauncherFolderHooks.shouldClampFolderFastBlur(true, true))
    }
}
