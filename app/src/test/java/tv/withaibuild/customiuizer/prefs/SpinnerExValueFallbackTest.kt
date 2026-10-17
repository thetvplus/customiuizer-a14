package tv.withaibuild.customiuizer.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class SpinnerExValueFallbackTest {

    @Test
    fun resolveSelectionIndex_usesMatchedIndex_whenValueExists() {
        val values = intArrayOf(1, 2, 3, 10, 20)
        assertEquals(3, SpinnerEx.resolveSelectionIndex(10, values))
    }

    @Test
    fun resolveSelectionIndex_fallsBackToFirst_whenValueMissingOrInvalid() {
        val values = intArrayOf(1, 2, 3)
        assertEquals(0, SpinnerEx.resolveSelectionIndex(0, values))
        assertEquals(0, SpinnerEx.resolveSelectionIndex(-1, values))
        assertEquals(0, SpinnerEx.resolveSelectionIndex(99, values))
        assertEquals(0, SpinnerEx.resolveSelectionIndex(1, null))
    }

    @Test
    fun resolveSelectedValue_fallsBackToFirstValue_whenSelectionOutOfRange() {
        val values = intArrayOf(1, 2, 3)
        assertEquals(2, SpinnerEx.resolveSelectedValue(1, values))
        assertEquals(1, SpinnerEx.resolveSelectedValue(-1, values))
        assertEquals(1, SpinnerEx.resolveSelectedValue(99, values))
    }
}
