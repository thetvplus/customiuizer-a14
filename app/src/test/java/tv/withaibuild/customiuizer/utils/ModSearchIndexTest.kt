package tv.withaibuild.customiuizer.utils

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two properties the mod search relies on.
 *
 * The filter is a single linear scan with no sort and no per-item allocation. That is only
 * correct because the index is sorted once when it is built and because each entry carries
 * its own lowered title.
 */
class ModSearchIndexTest {

    private fun mod(breadcrumbs: String, title: String, key: String = title): ModData =
        ModData().apply {
            this.breadcrumbs = breadcrumbs
            this.title = title
            this.key = key
        }

    private fun filter(source: List<ModData>, query: String): List<ModData> {
        val lowered = query.lowercase(Locale.ROOT)
        return source.filter { it.titleLower.contains(lowered) }
    }

    @Test
    fun titleLowerIsDerivedFromTheTitle() {
        val entry = mod("System", "Hide Status Bar Clock")
        assertEquals("hide status bar clock", entry.titleLower)

        entry.title = "Scramble PIN"
        assertEquals("scramble pin", entry.titleLower)
    }

    @Test
    fun titleLowerUsesARootLocale() {
        // Turkish lowercases 'I' to a dotless 'ı'. If the index and the query disagree on
        // locale, a Turkish user cannot find an English mod title by typing it.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val entry = mod("System", "IMEI")
            assertEquals("imei", entry.titleLower)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun filteringASortedIndexPreservesOrder() {
        val unsorted = listOf(
            mod("System/Status bar", "Zen mode"),
            mod("Launcher", "Animation speed"),
            mod("System/Status bar", "Alarm icon"),
            mod("Launcher", "Zoom animation"),
            mod("System", "Alarm sound")
        )

        val sorted = unsorted.sortedWith(Helpers.MOD_DISPLAY_ORDER)

        // Sorting once and then filtering must equal filtering and then sorting; that
        // equivalence is what lets the per-keystroke sort be removed.
        for (query in listOf("", "a", "an", "zo", "alarm", "nothing-matches")) {
            assertEquals(
                "query '$query'",
                filter(unsorted, query).sortedWith(Helpers.MOD_DISPLAY_ORDER).map { it.title },
                filter(sorted, query).map { it.title }
            )
        }
    }

    @Test
    fun displayOrderIsBreadcrumbFirstThenTitleAndCaseInsensitive() {
        val sorted = listOf(
            mod("system", "beta"),
            mod("System", "Alpha"),
            mod("Launcher", "Gamma")
        ).sortedWith(Helpers.MOD_DISPLAY_ORDER)

        assertEquals(listOf("Gamma", "Alpha", "beta"), sorted.map { it.title })
    }

    @Test
    fun theComparatorIsATotalOrder() {
        val entries = listOf(
            mod("A", "x"), mod("A", "y"), mod("B", "x"), mod("a", "X"), mod("B", "y")
        )
        for (a in entries) for (b in entries) {
            val ab = Helpers.MOD_DISPLAY_ORDER.compare(a, b)
            val ba = Helpers.MOD_DISPLAY_ORDER.compare(b, a)
            assertTrue(
                "antisymmetry broken for '${a.breadcrumbs}/${a.title}' vs '${b.breadcrumbs}/${b.title}'",
                (ab == 0 && ba == 0) || (ab < 0 && ba > 0) || (ab > 0 && ba < 0)
            )
        }
    }
}
