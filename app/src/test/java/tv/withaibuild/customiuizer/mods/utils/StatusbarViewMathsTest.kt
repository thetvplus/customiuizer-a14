package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.resolveNetSpeedTypefaceStyle

class StatusbarViewMathsTest {

    @Test
    fun clampNegativeToZero() {
        assertEquals(0, StatusbarViewMaths.clampStatusIconInsertIndex(-1, 5))
    }

    @Test
    fun clampZeroUnchanged() {
        assertEquals(0, StatusbarViewMaths.clampStatusIconInsertIndex(0, 5))
    }

    @Test
    fun clampInRangeUnchanged() {
        assertEquals(3, StatusbarViewMaths.clampStatusIconInsertIndex(3, 5))
    }

    @Test
    fun clampChildCountUnchanged() {
        assertEquals(5, StatusbarViewMaths.clampStatusIconInsertIndex(5, 5))
    }

    @Test
    fun clampBeyondToChildCount() {
        assertEquals(5, StatusbarViewMaths.clampStatusIconInsertIndex(10, 5))
    }

    @Test
    fun clampForEmptyGroup() {
        assertEquals(0, StatusbarViewMaths.clampStatusIconInsertIndex(0, 0))
    }

    @Test
    fun zeroFontSizeKeepsSystemAppearance() {
        assertEquals(null, StatusbarViewMaths.resolveCustomTextSizeDp(0))
    }

    @Test
    fun customFontSizeUsesDivider() {
        assertEquals(8f, StatusbarViewMaths.resolveCustomTextSizeDp(16)!!, 0.001f)
        assertEquals(13.5f, StatusbarViewMaths.resolveCustomTextSizeDp(27)!!, 0.001f)
    }

    @Test
    fun textFitHeightPrefersLaidOutParentOverOverflowingLeaf() {
        assertEquals(60, StatusbarViewMaths.resolvedTextFitHeightPx(92, 60))
        assertEquals(80, StatusbarViewMaths.resolvedTextFitHeightPx(80, 0))
        assertEquals(40, StatusbarViewMaths.resolvedTextFitHeightPx(0, 40))
    }

    @Test
    fun textFitHeightUsesSmallestLaidOutAncestor() {
        assertEquals(60, StatusbarViewMaths.resolvedTextFitHeightPx(90, 90, 60, 129))
        assertEquals(60, StatusbarViewMaths.resolvedTextFitHeightPx(40, 60, 60, 129))
        assertEquals(90, StatusbarViewMaths.resolvedTextFitHeightPx(90, 0, 0, 0))
    }

    @Test
    fun availableHeightSubtractsPadding() {
        assertEquals(40, StatusbarViewMaths.availableTextHeightPx(50, 6, 4))
        assertEquals(0, StatusbarViewMaths.availableTextHeightPx(8, 6, 4))
        assertEquals(0, StatusbarViewMaths.availableTextHeightPx(0, 0, 0))
    }

    @Test
    fun defaultSizeUnchangedWhenItFits() {
        assertEquals(16f, StatusbarViewMaths.fittedTextSizePx(16f, 18f, 20, 1, 1f, 6f), 0.001f)
    }

    @Test
    fun defaultSizeShrinksOnOverflow() {
        val fitted = StatusbarViewMaths.fittedTextSizePx(16f, 18f, 10, 1, 1f, 6f)
        assertTrue(fitted < 16f)
        assertTrue(fitted >= 6f)
        val occupied = StatusbarViewMaths.occupiedHeightPx(18f * (fitted / 16f), 1, 1f)
        assertTrue(occupied <= 10f + 0.5f)
    }

    @Test
    fun customSizeUnchangedWhenItFits() {
        assertEquals(22f, StatusbarViewMaths.fittedTextSizePx(22f, 24f, 40, 1, 1f, 6f), 0.001f)
    }

    @Test
    fun customSizeShrinksOnOverflow() {
        val fitted = StatusbarViewMaths.fittedTextSizePx(22f, 24f, 12, 1, 1f, 6f)
        assertTrue(fitted < 22f)
        assertTrue(fitted >= 6f)
    }

    @Test
    fun dualLineClockSizeShrinksToRow() {
        val fitted = StatusbarViewMaths.fittedTextSizePx(38f, 42f, 60, 2, 0.85f, 6f)
        assertTrue(fitted < 38f)
        val occupied = StatusbarViewMaths.occupiedHeightPx(42f * (fitted / 38f), 2, 0.85f)
        assertTrue(occupied <= 60f + 0.5f)
    }

    @Test
    fun degenerateMetricsStillShrinksDualLineIntoWindow() {
        val fitted = StatusbarViewMaths.fittedTextSizePx(38f, 38f, 80, 2, 0.85f, 6f)
        assertTrue(fitted < 38f)
        val occupied = StatusbarViewMaths.occupiedHeightPx(38f * 1.25f * (fitted / 38f), 2, 0.85f)
        assertTrue(occupied <= 80f + 0.5f)
    }

    @Test
    fun localOffsetClampsInsideAvailable() {
        assertEquals(2f, StatusbarViewMaths.clampVerticalOffsetPx(10f, 20, 16), 0.001f)
        assertEquals(-2f, StatusbarViewMaths.clampVerticalOffsetPx(-10f, 20, 16), 0.001f)
    }

    @Test
    fun globalThenLocalStayInsideRow() {
        val global = StatusBarSafeGeometry.resolve(120, 120, 10f)
        assertTrue(global.staysInsideWindow(120))
        val row = global.safeContentHeightPx / 2
        val fitted = StatusbarViewMaths.fittedTextSizePx(18f, 20f, row, 2, 0.85f, 6f)
        val occupied = StatusbarViewMaths.occupiedHeightPx(fitted, 2, 0.85f).toInt()
        val local = StatusbarViewMaths.clampVerticalOffsetPx(8f, row, occupied)
        val slack = ((row - occupied) / 2f).coerceAtLeast(0f)
        assertTrue(kotlin.math.abs(local) <= slack + 0.001f)
    }

    @Test
    fun heightGrowRestoresTowardRequested() {
        val requested = 16f
        val shrunk = StatusbarViewMaths.fittedTextSizePx(requested, 18f, 10, 1, 1f, 6f)
        assertTrue(shrunk < requested)
        val restored = StatusbarViewMaths.fittedTextSizePx(requested, 18f, 40, 1, 1f, 6f)
        assertEquals(requested, restored, 0.001f)
    }

    @Test
    fun unlaidViewDefersFit() {
        assertEquals(16f, StatusbarViewMaths.fittedTextSizePx(16f, 18f, 0, 2, 0.85f, 6f), 0.001f)
        assertEquals(16f, StatusbarViewMaths.shrinkToFitPx(16f, 0, 2, 0.85f, 6f), 0.001f)
    }

    @Test
    fun shrinkKeepsRequestedWhenItFitsSingleLine() {
        assertEquals(16f, StatusbarViewMaths.shrinkToFitPx(16f, 20, 1, 1f, 6f), 0.001f)
    }

    @Test
    fun shrinkReducesOversizedSingleLine() {
        assertEquals(10f, StatusbarViewMaths.shrinkToFitPx(16f, 10, 1, 1f, 6f), 0.001f)
    }

    @Test
    fun shrinkReducesOversizedDualLine() {
        val fitted = StatusbarViewMaths.shrinkToFitPx(20f, 20, 2, 0.85f, 6f)
        val occupied = StatusbarViewMaths.occupiedHeightPx(fitted, 2, 0.85f)
        assertTrue(occupied <= 20f + 0.01f)
        assertTrue(fitted < 20f)
        assertTrue(fitted >= 6f)
    }

    @Test
    fun shrinkNeverEnlarges() {
        assertEquals(8f, StatusbarViewMaths.shrinkToFitPx(8f, 40, 1, 1f, 6f), 0.001f)
    }

    @Test
    fun shrinkKeepsRequestedBeforeLayout() {
        assertEquals(16f, StatusbarViewMaths.shrinkToFitPx(16f, 0, 2, 0.85f, 6f), 0.001f)
        assertEquals(16f, StatusbarViewMaths.shrinkToFitPx(16f, -1, 1, 1f, 6f), 0.001f)
    }

    @Test
    fun offsetClampPositiveOverflow() {
        assertEquals(2f, StatusbarViewMaths.clampVerticalOffsetPx(10f, 20, 16), 0.001f)
    }

    @Test
    fun offsetClampNegativeOverflow() {
        assertEquals(-2f, StatusbarViewMaths.clampVerticalOffsetPx(-10f, 20, 16), 0.001f)
    }

    @Test
    fun offsetClampInRangeUnchanged() {
        assertEquals(1f, StatusbarViewMaths.clampVerticalOffsetPx(1f, 20, 16), 0.001f)
        assertEquals(-1f, StatusbarViewMaths.clampVerticalOffsetPx(-1f, 20, 16), 0.001f)
    }

    @Test
    fun offsetClampKeepsRequestedBeforeLayout() {
        assertEquals(8f, StatusbarViewMaths.clampVerticalOffsetPx(8f, 0, 10), 0.001f)
        assertEquals(8f, StatusbarViewMaths.clampVerticalOffsetPx(8f, 20, 0), 0.001f)
    }

    @Test
    fun boldStylePreservesNonDefaultFamilyBits() {
        assertEquals(android.graphics.Typeface.BOLD, resolveNetSpeedTypefaceStyle(0, true))
        assertEquals(android.graphics.Typeface.BOLD or android.graphics.Typeface.ITALIC, resolveNetSpeedTypefaceStyle(android.graphics.Typeface.ITALIC, true))
        assertEquals(android.graphics.Typeface.ITALIC, resolveNetSpeedTypefaceStyle(android.graphics.Typeface.ITALIC, false))
    }
}
