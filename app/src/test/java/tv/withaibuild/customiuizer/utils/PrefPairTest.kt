package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the `first|second` preference encoding.
 *
 * The hook side used to decode these values with `split("\\|".toRegex())`; these cases pin the
 * behaviour that the literal, allocation free implementation has to keep.
 */
class PrefPairTest {

    @Test
    fun firstReturnsThePartBeforeTheDelimiter() {
        assertEquals("com.example", PrefPair.first("com.example|com.example.MainActivity"))
    }

    @Test
    fun firstReturnsTheWholeValueWithoutDelimiter() {
        assertEquals("com.example", PrefPair.first("com.example"))
    }

    @Test
    fun firstIsEmptyWhenTheValueStartsWithTheDelimiter() {
        assertEquals("", PrefPair.first("|only-second"))
    }

    @Test
    fun firstStopsAtTheFirstDelimiter() {
        assertEquals("a", PrefPair.first("a|b|c"))
    }

    @Test
    fun firstEqualsIgnoresCaseLikeTheOldEqualsIgnoreCase() {
        assertTrue(PrefPair.firstEquals("AA:BB:CC:DD:EE:FF|Home", "aa:bb:cc:dd:ee:ff"))
        assertTrue(PrefPair.firstEquals("com.example", "COM.EXAMPLE"))
    }

    @Test
    fun firstEqualsRejectsPrefixesAndSuffixes() {
        assertFalse(PrefPair.firstEquals("com.example.app|Act", "com.example"))
        assertFalse(PrefPair.firstEquals("com.example|Act", "com.example.app"))
        assertFalse(PrefPair.firstEquals("com.example|Act", "Act"))
    }

    @Test
    fun containsFirstMatchesAnyEntry() {
        val pairs = setOf("aa:bb|Speaker", "cc:dd|Headset")
        assertTrue(PrefPair.containsFirst(pairs, "cc:dd"))
        assertFalse(PrefPair.containsFirst(pairs, "ee:ff"))
    }

    @Test
    fun containsFirstHandlesNullAndEmptySets() {
        assertFalse(PrefPair.containsFirst(null, "aa:bb"))
        assertFalse(PrefPair.containsFirst(emptySet(), "aa:bb"))
    }

    @Test
    fun splitWithTheDelimiterKeepsTrailingEmptyComponents() {
        // Kotlin's split(Regex) and split(literal) both keep them; the callers rely on
        // `size >= 2 && [1].isNotBlank()` rather than on Java's trailing-empty stripping.
        val components = "com.example|".split(PrefPair.DELIMITER)
        assertEquals(2, components.size)
        assertEquals("com.example", components[0])
        assertTrue(components[1].isBlank())
    }
}
