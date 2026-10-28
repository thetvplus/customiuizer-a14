package tv.withaibuild.customiuizer.mods

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.util.Pair
import java.util.Collections
import java.util.LinkedList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ShareMenuFilterBoundaryTest {
    private fun item(pkg: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg } }
    @Test fun unselectedAndBothSelectedPackagesDoNotQueryDualAppStateAndDuplicateActivitiesShareOneDecision() {
        val original = List(1000) { item(if (it % 2 == 0) "keep" else "remove") }
        var selections = 0; var queries = 0
        val result = SystemShareMenuHooks.filterResolvedActivities(original, { pkg -> selections++; Pair(pkg == "remove", pkg == "remove") }, { queries++; error("no dual-state query is needed") })
        assertEquals(2, selections); assertEquals(0, queries); assertEquals(500, result.size)
        assertEquals(original.filterIndexed { index, _ -> index % 2 == 0 }, result)
        assertEquals(1000, original.size); assertNotSame(original, result)
    }
    @Test fun originalOnlySelectionKeepsTheDualRouteAndChecksItsPackageOncePerQuery() {
        for (dual in listOf(false, true)) {
            val original = List(5) { item("app") }; var calls = 0
            val result = SystemShareMenuHooks.filterResolvedActivities(original, { Pair(true, false) }, { calls++; dual })
            assertEquals(1, calls)
            if (dual) assertSame(original, result) else assertTrue(result.isEmpty())
        }
    }
    @Test fun noRemovalReturnsTheExactNativeListAndUnknownEntriesAreKept() {
        val original = listOf(item("keep"), ResolveInfo())
        val result = SystemShareMenuHooks.filterResolvedActivities(original, { Pair(false, true) }, { error("unselected original cannot query dual") })
        assertSame(original, result)
    }
    @Test fun immutableAndSequentialListsAreFilteredWithoutMutatingOrReorderingTheNativeResults() {
        val original = Collections.unmodifiableList(LinkedList(listOf(item("a"), item("b"), item("drop"), item("c"))))
        val result = SystemShareMenuHooks.filterResolvedActivities(original, { Pair(it == "drop", true) }, { error("unneeded") })
        assertEquals(listOf("a", "b", "c"), result.map { it.activityInfo.packageName })
        assertEquals(listOf("a", "b", "drop", "c"), original.map { it.activityInfo.packageName })
    }
    @Test fun aLateLookupFailureCannotPartiallyRemoveEntriesFromTheNativeList() {
        val original = listOf(item("remove"), item("fail"))
        assertThrows(IllegalStateException::class.java) {
            SystemShareMenuHooks.filterResolvedActivities(original, { Pair(true, false) }, { pkg -> if (pkg == "fail") error("lookup") else false })
        }
        assertEquals(2, original.size); assertEquals("remove", original[0].activityInfo.packageName)
    }
}
