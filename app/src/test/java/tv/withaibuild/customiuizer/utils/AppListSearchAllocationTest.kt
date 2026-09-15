package tv.withaibuild.customiuizer.utils

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Host-JVM allocation evidence for the predicate used by the app-list filter loops. */
class AppListSearchAllocationTest {
    @Volatile private var sink: List<AppData> = emptyList()

    @Test
    fun preparedSearchEliminatesPerRowLowercaseAllocation() {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        assumeTrue("HotSpot allocation counter required", bean?.isThreadAllocatedMemorySupported == true)
        bean!!
        bean.isThreadAllocatedMemoryEnabled = true
        val source = ArrayList<AppData>(1000).apply {
            repeat(1000) { index ->
                add(AppData().apply {
                    label = "Example APPLICATION $index"
                    prepareForList()
                })
            }
        }
        val query = "application 2"
        val previous = {
            source.filterTo(ArrayList()) { it.label.lowercase(Locale.ROOT).contains(query) }
        }
        val current = {
            source.filterTo(ArrayList()) { it.labelLower.contains(query) }
        }
        assertEquals(previous(), current())
        repeat(3000) { sink = previous(); sink = current() }

        fun bytesFor(block: () -> List<AppData>): Long {
            val thread = Thread.currentThread().id
            val before = bean.getThreadAllocatedBytes(thread)
            repeat(1000) { sink = block() }
            return bean.getThreadAllocatedBytes(thread) - before
        }
        val before = bytesFor(previous)
        val after = bytesFor(current)
        assertTrue("positive control must observe lowercase strings", before > 40_000_000)
        assertTrue("prepared matching still allocates result storage, not a String per row",
            after < before / 4)
        println("APP_LIST_SEARCH rows=1000 iterations=1000 previousBytes=$before currentBytes=$after")
    }
}
