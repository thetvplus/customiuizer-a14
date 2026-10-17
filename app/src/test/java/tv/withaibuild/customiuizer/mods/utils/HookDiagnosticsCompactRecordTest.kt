package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HookDiagnosticsCompactRecordTest {
    @Before
    fun resetBefore() = HookDiagnostics.reset()

    @After
    fun resetAfter() = HookDiagnostics.reset()

    @Test
    fun bothRecordOverloadsKeepTheSamePolicyForEveryStatus() {
        for (status in HookDiagnostics.Status.entries) {
            repeat(3) {
                HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "C", "m", "", status, "failure")
            }
            val convenienceSummary = HookDiagnostics.summary()
            val convenienceDetails = HookDiagnostics.snapshot()
            HookDiagnostics.reset()
            repeat(3) {
                HookDiagnostics.record(HookDiagnostics.Record(
                    "p", HookDiagnostics.Kind.METHOD, "C", "m", "", status, "failure"
                ))
            }
            assertEquals("summary for $status", convenienceSummary, HookDiagnostics.summary())
            assertEquals("detail policy for $status", convenienceDetails, HookDiagnostics.snapshot())
            HookDiagnostics.reset()
        }
    }

    @Test
    fun repeatedCompactStatusesKeepCountsAndFailureDetailStaysDeduplicated() {
        repeat(4) {
            HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "C", "m", "",
                HookDiagnostics.Status.INSTALLED, "")
        }
        repeat(3) {
            HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "C", "m", "",
                HookDiagnostics.Status.SILENTLY_SKIPPED, "")
        }
        HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "C", "m", "",
            HookDiagnostics.Status.INSTALL_FAILED, "old failure")
        HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "C", "m", "",
            HookDiagnostics.Status.INSTALL_FAILED, "latest failure")

        val verbose = HookDiagnostics.isVerboseDiagnostics()
        val summary = HookDiagnostics.summary()
        assertEquals(if (verbose) 1 else 4, summary[HookDiagnostics.Status.INSTALLED])
        assertEquals(if (verbose) 1 else 3, summary[HookDiagnostics.Status.SILENTLY_SKIPPED])
        assertEquals(1, summary[HookDiagnostics.Status.INSTALL_FAILED])
        val details = HookDiagnostics.snapshot()
        assertEquals(if (verbose) 3 else 1, details.size)
        assertEquals("latest failure", details.single { it.status == HookDiagnostics.Status.INSTALL_FAILED }.exceptionType)
    }

    @Test
    fun failureStoreRemainsBoundedAfterSuccessfulTraffic() {
        val bound = HookDiagnostics.detailBound()
        repeat(bound + 20) { index ->
            HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "C$index", "m", "",
                HookDiagnostics.Status.INSTALL_FAILED, "failure")
        }
        repeat(1000) {
            HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "success", "m", "",
                HookDiagnostics.Status.INSTALLED, "")
        }
        val details = HookDiagnostics.snapshot()
        assertTrue(details.size <= bound)
        if (!HookDiagnostics.isVerboseDiagnostics()) {
            assertEquals(bound, details.size)
            assertTrue(details.all { it.status == HookDiagnostics.Status.INSTALL_FAILED })
            assertEquals(1000, HookDiagnostics.summary()[HookDiagnostics.Status.INSTALLED])
        }
    }

    @Test
    fun resetClearsCompactCountsAndDetailsFromBothOverloads() {
        HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "C", "m", "",
            HookDiagnostics.Status.INSTALLED, "")
        HookDiagnostics.record(HookDiagnostics.Record("p", HookDiagnostics.Kind.METHOD, "C", "m", "",
            HookDiagnostics.Status.SILENTLY_SKIPPED, ""))
        HookDiagnostics.record("p", HookDiagnostics.Kind.METHOD, "C", "m", "",
            HookDiagnostics.Status.INSTALL_FAILED, "failure")
        HookDiagnostics.reset()
        assertTrue(HookDiagnostics.snapshot().isEmpty())
        assertTrue(HookDiagnostics.summary().values.all { it == 0 })
    }
}
