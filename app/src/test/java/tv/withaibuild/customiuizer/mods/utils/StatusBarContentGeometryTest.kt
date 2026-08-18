package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarContentGeometryTest {

    @Test
    fun defaultRawValueIsZeroDp() {
        assertEquals(0f, StatusBarContentGeometry.resolveOffsetDp(StatusBarContentGeometry.RAW_DEFAULT), 0.001f)
        assertEquals(0f, StatusBarContentGeometry.resolveOffsetPx(20, 3f), 0.001f)
    }

    @Test
    fun rawStepsAreHalfDp() {
        assertEquals(-10f, StatusBarContentGeometry.resolveOffsetDp(0), 0.001f)
        assertEquals(10f, StatusBarContentGeometry.resolveOffsetDp(40), 0.001f)
        assertEquals(-0.5f, StatusBarContentGeometry.resolveOffsetDp(19), 0.001f)
        assertEquals(0.5f, StatusBarContentGeometry.resolveOffsetDp(21), 0.001f)
        assertEquals(-15f, StatusBarContentGeometry.resolveOffsetPx(10, 3f), 0.001f)
        assertEquals(15f, StatusBarContentGeometry.resolveOffsetPx(30, 3f), 0.001f)
    }

    @Test
    fun dualRowsMayFillWindow_singleRowDoesNotWithoutOffset() {
        assertTrue(StatusBarContentGeometry.shouldFillWindowForDualRows(true, 129))
        assertFalse(StatusBarContentGeometry.shouldFillWindowForDualRows(false, 129))
        assertFalse(StatusBarContentGeometry.shouldFillWindowForDualRows(true, 0))
        assertFalse(StatusBarContentGeometry.shouldFillWindowForOffset(0f, 129))
        assertTrue(StatusBarContentGeometry.shouldFillWindowForOffset(10f, 129))
    }

    @Test
    fun ownerTargetCapsSingleRowToWindowAndFillsForOffset() {
        assertEquals(80, StatusBarContentGeometry.ownerTargetHeightPx(80, 129, false, 0f))
        assertEquals(60, StatusBarContentGeometry.ownerTargetHeightPx(80, 60, false, 0f))
        assertEquals(129, StatusBarContentGeometry.ownerTargetHeightPx(80, 129, false, 8f))
        assertEquals(129, StatusBarContentGeometry.ownerTargetHeightPx(80, 129, true, 0f))
        assertEquals(-1, StatusBarContentGeometry.ownerTargetHeightPx(-1, 129, false, 0f))
    }

    @Test
    fun naturalContentFollowsWindowForDualRowsAndMatchParent() {
        assertEquals(129, StatusBarContentGeometry.naturalContentHeightPx(-1, 129, false))
        assertEquals(129, StatusBarContentGeometry.naturalContentHeightPx(80, 129, true))
        assertEquals(80, StatusBarContentGeometry.naturalContentHeightPx(80, 129, false))
        assertEquals(60, StatusBarContentGeometry.naturalContentHeightPx(80, 60, false))
    }
}
