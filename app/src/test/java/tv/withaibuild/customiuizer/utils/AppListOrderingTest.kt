package tv.withaibuild.customiuizer.utils

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListOrderingTest {
    private fun apps(size: Int) = ArrayList<AppData>(size).apply {
        repeat(size) { index ->
            add(AppData().apply {
                pkgName = "app.${index / 2}"
                user = if (index % 2 == 0) 0 else 999
                label = "App $index"
            })
        }
    }

    @Test
    fun matchesStableSortForEverySelectionOfEightRowsIncludingDualUsers() {
        val source = apps(8)
        for (mask in 0 until 256) {
            val selected = source.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val expected = source.sortedBy { if (it in selected) 0 else 1 }
            val actual = selectedAppsFirst(source) { it in selected }
            assertEquals("selection mask $mask", expected, actual)
            assertEquals((0 until 8).map { "App $it" }, source.map { it.label })
        }
    }

    @Test
    fun selectionIsReadExactlyOncePerRow() {
        val source = apps(1000)
        val calls = IntArray(source.size)
        val result = selectedAppsFirst(source) {
            val index = it.label.removePrefix("App ").toInt()
            calls[index]++
            index % 3 == 0
        }
        assertTrue(calls.all { it == 1 })
        assertEquals(source.size, result.size)
        assertSame(source[0], result[0])
        assertSame(source[999], result[333])
        assertSame(source[1], result[334])
    }

    @Test
    fun aNewPublicationReadsCurrentSelectionWithoutRetainingOldState() {
        val source = apps(4)
        var selectedUser = 999
        assertEquals(listOf(source[1], source[3], source[0], source[2]),
            selectedAppsFirst(source) { it.user == selectedUser })
        selectedUser = 0
        assertEquals(listOf(source[0], source[2], source[1], source[3]),
            selectedAppsFirst(source) { it.user == selectedUser })
    }

    @Test
    fun uniformAndEmptySelectionsReuseTheInputList() {
        val empty = apps(0)
        assertSame(empty, selectedAppsFirst(empty) { error("empty list queried") })
        val source = apps(4)
        assertSame(source, selectedAppsFirst(source) { false })
        assertSame(source, selectedAppsFirst(source) { true })
    }

    @Test
    fun failedReadLeavesSourceIntactAndPropagatesTheFailure() {
        val source = apps(4)
        val before = source.toList()
        for (failure in listOf(IllegalStateException("service unavailable"), OutOfMemoryError("test"))) {
            try {
                selectedAppsFirst(source) {
                    if (it === source[2]) throw failure
                    it.user == 999
                }
                throw AssertionError("failure was swallowed")
            } catch (actual: Throwable) {
                assertSame(failure, actual)
            }
            assertEquals(before, source)
        }
    }

    @Test
    fun compareServiceReadCountsWithThePreviousComparator() {
        val source = apps(1000)
        val random = Random(20260915L)
        val selected = source.filter { random.nextBoolean() }.toSet()
        var beforeCalls = 0
        val expected = source.sortedWith { first, second ->
            beforeCalls += 2
            val firstSelected = first in selected
            val secondSelected = second in selected
            when {
                firstSelected == secondSelected -> 0
                firstSelected -> -1
                else -> 1
            }
        }
        var afterCalls = 0
        val actual = selectedAppsFirst(source) {
            afterCalls++
            it in selected
        }
        assertEquals(expected, actual)
        assertEquals(1000, afterCalls)
        assertTrue("control must exercise repeated reads", beforeCalls > afterCalls)
        println("APP_LIST_ORDERING rows=1000 previousReads=$beforeCalls currentReads=$afterCalls")
    }
}
