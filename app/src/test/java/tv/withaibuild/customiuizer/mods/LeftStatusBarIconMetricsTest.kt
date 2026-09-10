package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Test

class LeftStatusBarIconMetricsTest {
    @Test
    fun defaultsPreserveNativeSizeAndAlignment() {
        assertEquals(48, SystemUIStatusBarHooks.leftIconHeight(48, 100))
        assertEquals(0f, SystemUIStatusBarHooks.leftIconOffsetDp(12), 0f)
    }

    @Test
    fun adjustmentsRespectBoundsEvenForRestoredInvalidValues() {
        assertEquals(36, SystemUIStatusBarHooks.leftIconHeight(48, Int.MIN_VALUE))
        assertEquals(60, SystemUIStatusBarHooks.leftIconHeight(48, Int.MAX_VALUE))
        assertEquals(1, SystemUIStatusBarHooks.leftIconHeight(0, 100))
        assertEquals(-6f, SystemUIStatusBarHooks.leftIconOffsetDp(Int.MIN_VALUE), 0f)
        assertEquals(6f, SystemUIStatusBarHooks.leftIconOffsetDp(Int.MAX_VALUE), 0f)
        assertEquals(-0.5f, SystemUIStatusBarHooks.leftIconOffsetDp(11), 0f)
        assertEquals(0.5f, SystemUIStatusBarHooks.leftIconOffsetDp(13), 0f)
    }
}
