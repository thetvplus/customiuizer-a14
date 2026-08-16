package tv.withaibuild.customiuizer.mods.utils

import android.graphics.Rect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks

class StatusBarInsetsGeometryTest {

    @After
    fun tearDown() {
        StatusBarHeightConfig.generation.set(0L)
        StatusBarHeightConfig.state = StatusBarHeightConfig.State(
            rawPreferenceDp = StatusBarHeightConfig.DEFAULT_SENTINEL,
            enabled = false,
            configuredDp = StatusBarHeightConfig.DEFAULT_DP,
            configuredPx = -1,
            densityDpi = -1,
            density = -1.0f,
        )
    }

    @Test
    fun computeFrameBottom_disabled_returnsOriginal() {
        assertEquals(104, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 110, false))
    }

    @Test
    fun computeFrameBottom_zeroConfigured_returnsOriginal() {
        assertEquals(104, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 0, true))
    }

    @Test
    fun computeFrameBottom_negativeConfigured_returnsOriginal() {
        assertEquals(104, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, -1, true))
    }

    @Test
    fun computeFrameBottom_fuxi_12dp_at_160dpi_top0_bottom104() {
        // 12 dp -> 12 px on 160 dpi; newBottom = 0 + 12 = 12
        assertEquals(12, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 12, true))
    }

    @Test
    fun computeFrameBottom_fuxi_27dp_at_160dpi_top0_bottom104() {
        // 27 dp -> 27 px; newBottom = 27
        assertEquals(27, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 27, true))
    }

    @Test
    fun computeFrameBottom_fuxi_28dp_at_160dpi_top0_bottom104() {
        assertEquals(28, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 28, true))
    }

    @Test
    fun computeFrameBottom_fuxi_35dp_at_160dpi_top0_bottom104() {
        assertEquals(35, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 35, true))
    }

    @Test
    fun computeFrameBottom_fuxi_38dp_at_160dpi_top0_bottom104() {
        assertEquals(38, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 38, true))
    }

    @Test
    fun computeFrameBottom_fuxi_40dp_at_160dpi_top0_bottom104() {
        assertEquals(40, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 40, true))
    }

    @Test
    fun computeFrameBottom_fuxi_40dp_at_440dpi_top0_bottom104() {
        // 40 dp * 440/160 = 110 px; newBottom = 0 + 110 = 110
        assertEquals(110, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 110, true))
    }

    @Test
    fun computeFrameBottom_nonZeroTop_respectsTop() {
        // top=20, configured height 96 -> bottom = 20 + 96 = 116
        assertEquals(116, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(20, 124, 96, true))
    }

    @Test
    fun computeFrameBottom_canShrinkBelowOriginal() {
        // The original 104px bottom must not be treated as a floor.
        assertEquals(74, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 74, true))
    }

    @Test
    fun computeFrameBottom_canGrowAboveOriginal() {
        assertEquals(110, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 110, true))
    }

    @Test
    fun computeFrameBottom_idempotent() {
        val first = SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, 96, true)
        val second = SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, first, 96, true)
        assertEquals(first, second)
    }

    @Test
    fun statusBarHeightConfig_12dp_fuxi_density_440() {
        val dp = 12
        val densityDpi = 440
        val px = (dp * densityDpi / 160f).toInt()
        assertEquals(33, px) // 12 * 2.75 = 33
        assertEquals(33, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, px, true))
    }

    @Test
    fun statusBarHeightConfig_27dp_fuxi_density_440() {
        val dp = 27
        val densityDpi = 440
        val px = (dp * densityDpi / 160f).toInt()
        assertEquals(74, px) // 27 * 2.75 = 74.25 -> 74
        assertEquals(74, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, px, true))
    }

    @Test
    fun statusBarHeightConfig_40dp_fuxi_density_440() {
        val dp = 40
        val densityDpi = 440
        val px = (dp * densityDpi / 160f).toInt()
        assertEquals(110, px) // 40 * 2.75 = 110
        assertEquals(110, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 104, px, true))
    }

    @Test
    fun computeFrameBottom_doesNotUseOriginalBottomAsFloor() {
        val originalBottom = 104
        val configuredPx = 12
        val result = SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, originalBottom, configuredPx, true)
        assertEquals(configuredPx, result)
        assertTrue(result < originalBottom)
    }

    @Test
    fun computeFrameBottom_withNonZeroOriginalTop() {
        // Rotation or secondary display with top=20 and configured 96 -> 116
        assertEquals(116, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(20, 124, 96, true))
    }

    @Test
    fun computeNonDecorTop_growsToConfiguredStatusBarHeight() {
        assertEquals(129, SystemStatusBarInsetsHooks.computeNonDecorTop(104, 129, true))
    }

    @Test
    fun computeNonDecorTop_neverShrinksBelowCutoutSafeInset() {
        assertEquals(104, SystemStatusBarInsetsHooks.computeNonDecorTop(104, 74, true))
    }

    @Test
    fun computeNonDecorTop_disabledKeepsFrameworkInset() {
        assertEquals(104, SystemStatusBarInsetsHooks.computeNonDecorTop(104, 129, false))
    }

    @Test
    fun computeNonDecorFrameTop_appliesOnlyTheInsetDelta() {
        assertEquals(149, SystemStatusBarInsetsHooks.computeNonDecorFrameTop(124, 104, 129, true))
    }

    private companion object {
        fun assertTrue(condition: Boolean) {
            org.junit.Assert.assertTrue(condition)
        }
    }
}
