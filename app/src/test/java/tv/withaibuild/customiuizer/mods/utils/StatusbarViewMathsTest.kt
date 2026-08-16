package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
