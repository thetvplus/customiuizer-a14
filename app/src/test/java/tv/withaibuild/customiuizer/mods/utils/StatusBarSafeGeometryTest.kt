package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StatusBarSafeGeometryTest {

    @Test
    fun offsetZeroKeepsNativeTopAlignment() {
        val layout = StatusBarSafeGeometry.resolve(129, 80, 0f)
        assertEquals(80, layout.safeContentHeightPx)
        assertEquals(0f, layout.effectiveOffsetPx, 0.001f)
        assertEquals(0f, layout.contentTopPx, 0.001f)
        assertEquals(80f, layout.contentBottomPx, 0.001f)
        assertTrue(layout.staysInsideWindow(129))
    }

    @Test
    fun positiveOffsetReservesThenTranslates() {
        val layout = StatusBarSafeGeometry.resolve(129, 129, 10f)
        assertEquals(109, layout.safeContentHeightPx)
        assertEquals(10f, layout.effectiveOffsetPx, 0.001f)
        assertEquals(20f, layout.contentTopPx, 0.001f)
        assertEquals(129f, layout.contentBottomPx, 0.001f)
        assertTrue(layout.staysInsideWindow(129))
    }

    @Test
    fun negativeOffsetReservesThenTranslates() {
        val layout = StatusBarSafeGeometry.resolve(129, 129, -10f)
        assertEquals(109, layout.safeContentHeightPx)
        assertEquals(-10f, layout.effectiveOffsetPx, 0.001f)
        assertEquals(0f, layout.contentTopPx, 0.001f)
        assertEquals(109f, layout.contentBottomPx, 0.001f)
        assertTrue(layout.staysInsideWindow(129))
    }

    @Test
    fun offsetExceedingSlackIsClamped() {
        val layout = StatusBarSafeGeometry.resolve(40, 24, 20f)
        assertTrue(layout.staysInsideWindow(40))
        assertTrue(abs(layout.effectiveOffsetPx) <= (40 - layout.safeContentHeightPx) / 2f + 0.001f)
        assertTrue(layout.contentTopPx >= -1f)
        assertTrue(layout.contentBottomPx <= 41f)
    }

    @Test
    fun windowSmallerThanNaturalCapsHeight() {
        val layout = StatusBarSafeGeometry.resolve(60, 80, 0f)
        assertEquals(60, layout.safeContentHeightPx)
        assertEquals(0f, layout.effectiveOffsetPx, 0.001f)
        assertTrue(layout.staysInsideWindow(60))
    }

    @Test
    fun windowLargerThanNaturalKeepsNaturalWhenOffsetZero() {
        val layout = StatusBarSafeGeometry.resolve(129, 80, 0f)
        assertEquals(80, layout.safeContentHeightPx)
        assertEquals(0f, layout.contentTopPx, 0.001f)
        assertTrue(layout.staysInsideWindow(129))
    }

    @Test
    fun windowLargerKeepsNaturalWhenOffsetFits() {
        val layout = StatusBarSafeGeometry.resolve(129, 80, 10f)
        assertEquals(80, layout.safeContentHeightPx)
        assertEquals(10f, layout.effectiveOffsetPx, 0.001f)
        assertTrue(layout.staysInsideWindow(129))
        assertTrue(layout.contentTopPx >= -1f)
        assertTrue(layout.contentBottomPx <= 130f)
    }

    @Test
    fun roundingBoundariesStayInside() {
        val layout = StatusBarSafeGeometry.resolve(100, 100, 1f)
        assertTrue(layout.staysInsideWindow(100))
        assertEquals(98, layout.safeContentHeightPx)
        assertEquals(1f, layout.effectiveOffsetPx, 0.001f)
    }

    @Test
    fun unmeasuredWindowDoesNotApplyOffset() {
        val layout = StatusBarSafeGeometry.resolve(0, 80, 12f)
        assertEquals(80, layout.safeContentHeightPx)
        assertEquals(0f, layout.effectiveOffsetPx, 0.001f)
    }

    @Test
    fun fillingWindowWithOffsetShrinksInsteadOfClipping() {
        val layout = StatusBarSafeGeometry.resolve(40, 40, 10f)
        assertEquals(20, layout.safeContentHeightPx)
        assertEquals(10f, layout.effectiveOffsetPx, 0.001f)
        assertTrue(layout.staysInsideWindow(40))
        assertFalse(layout.safeContentHeightPx == 40 && layout.effectiveOffsetPx != 0f)
    }
}
