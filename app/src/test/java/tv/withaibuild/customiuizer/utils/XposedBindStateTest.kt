package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.XposedServiceManager.State

/**
 * The bind state is what decides whether the user is told the module is not active.
 *
 * The regression these cover: a language change kills and relaunches the settings
 * process, the LSPosed bind then takes longer than the timeout, and a waiter that
 * treated TIMED_OUT as decided stopped waiting and reported a false negative.
 */
class XposedBindStateTest {

    @Test
    fun unknownIsProvisional() {
        assertTrue(State.UNKNOWN.isProvisional)
    }

    @Test
    fun timedOutIsProvisional() {
        // The whole point: the timeout fired, but nothing was observed. A waiter must
        // keep waiting rather than stop here and read it as a negative.
        assertTrue(State.TIMED_OUT.isProvisional)
    }

    @Test
    fun boundAndDisconnectedAreDecided() {
        assertFalse(State.BOUND.isProvisional)
        assertFalse(State.DISCONNECTED.isProvisional)
    }

    @Test
    fun disconnectedIsTheOnlyProvenNegative() {
        // init() is never called in this test, so the budget has not elapsed: this is the
        // "nothing has been observed yet" situation every caller starts in.
        assertFalse(XposedServiceManager.decisionBudgetElapsed())

        assertTrue(XposedServiceManager.shouldReportInactive(State.DISCONNECTED))
        assertFalse(XposedServiceManager.shouldReportInactive(State.TIMED_OUT))
        assertFalse(XposedServiceManager.shouldReportInactive(State.UNKNOWN))
        assertFalse(XposedServiceManager.shouldReportInactive(State.BOUND))
    }

    @Test
    fun timedOutNeverBecomesAProvenNegative() {
        val initElapsedRealtime = XposedServiceManager::class.java
            .getDeclaredField("initElapsedRealtime")
            .apply { isAccessible = true }
        val original = initElapsedRealtime.getLong(null)
        try {
            // JVM Android stubs return zero here. Starting one full budget before zero
            // makes the manager's own elapsed-time check deterministically true without
            // sleeping, and reproduces the false dialog shown after a rapid restart.
            initElapsedRealtime.setLong(
                null,
                -XposedServiceManager.FULL_DECISION_BUDGET_MS
            )
            assertTrue(XposedServiceManager.decisionBudgetElapsed())
            assertFalse(XposedServiceManager.shouldReportInactive(State.TIMED_OUT))
        } finally {
            initElapsedRealtime.setLong(null, original)
        }
    }

    @Test
    fun theBudgetSpansTwoBindWindows() {
        // A single window is what expires into TIMED_OUT; the budget has to outlast it,
        // or nothing would ever be waited out beyond the timeout that caused the bug.
        assertTrue(
            XposedServiceManager.FULL_DECISION_BUDGET_MS >
                XposedServiceManager.BIND_DECISION_TIMEOUT_MS * 2
        )
    }

    @Test
    fun anUnstartedManagerNeverConcludesAnything() {
        // Without init() there is no registration to time out, so a budget measured from
        // it must not silently expire and turn UNKNOWN into a report.
        assertFalse(XposedServiceManager.decisionBudgetElapsed())
        assertFalse(XposedServiceManager.shouldReportInactive(State.UNKNOWN))
    }

    @Test
    fun nothingIsUndeliveredBeforeAnythingHasBeenChanged() {
        // A fresh process has mirrored nothing and missed nothing, so the dialog must not
        // add the "your changes have not arrived" sentence to a first-run message.
        assertFalse(XposedServiceManager.hasUndeliveredChanges())
    }

    @Test
    fun aDecidedStateIsDecidedRegardlessOfTheBudget() {
        val original = XposedServiceManager.state
        try {
            XposedServiceManager.state = State.BOUND
            assertTrue(XposedServiceManager.isDecided())

            XposedServiceManager.state = State.DISCONNECTED
            assertTrue(XposedServiceManager.isDecided())

            // Provisional with an unstarted budget: callers must keep waiting.
            XposedServiceManager.state = State.TIMED_OUT
            assertFalse(XposedServiceManager.isDecided())

            XposedServiceManager.state = State.UNKNOWN
            assertFalse(XposedServiceManager.isDecided())
        } finally {
            XposedServiceManager.state = original
        }
    }
}
