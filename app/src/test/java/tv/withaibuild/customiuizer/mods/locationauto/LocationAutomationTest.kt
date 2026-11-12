package tv.withaibuild.customiuizer.mods.locationauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAutomationTest {
    @Test fun listModeAndCloseAreIndependent() {
        val allowed = LocationAutomationOptions(true, "allow", setOf("example.maps"), autoClose = false)
        assertEquals(true, allowed.desired)
        assertFalse(allowed.tracksTasks)
        assertTrue(allowed.copy(autoClose = true).tracksTasks)
        val blocked = allowed.copy(mode = "block", autoClose = true)
        assertEquals(false, blocked.desired)
        assertFalse(blocked.tracksTasks)
        assertNull(allowed.copy(apps = emptySet()).desired)
        assertNull(allowed.copy(enabled = false).desired)
        assertNull(allowed.copy(mode = "unknown").desired)
    }

    @Test fun closesOnlyAfterLastMatchingTaskAndSkipsReadsWhileOwned() {
        val location = Location()
        val policy = LocationAutomation(location::change)
        val first = Any()
        val second = Any()
        policy.resumed(1, first)
        policy.resumed(1, first)
        policy.resumed(2, second)
        policy.removed(1, first)
        assertEquals(listOf(true), location.requests)
        assertTrue(location.enabled)
        policy.removed(2, second)
        assertEquals(listOf(true, false), location.requests)
        assertFalse(location.enabled)
        assertTrue(policy.idle)
    }

    @Test fun staleRemovalCannotCloseReplacementCard() {
        val location = Location()
        val policy = LocationAutomation(location::change)
        val old = Any()
        val replacement = Any()
        policy.resumed(1, old)
        policy.resumed(1, replacement)
        policy.removed(1, old)
        assertTrue(location.enabled)
        policy.removed(1, replacement)
        assertFalse(location.enabled)
    }

    @Test fun preexistingLocationAndManualReenableStayOn() {
        val task = Any()
        val location = Location(enabled = true)
        val preexisting = LocationAutomation(location::change)
        preexisting.resumed(1, task)
        preexisting.removed(1, task)
        assertEquals(listOf(true), location.requests)
        assertTrue(location.enabled)

        location.enabled = false
        val owned = LocationAutomation(location::change)
        owned.resumed(1, task)
        location.enabled = false
        owned.locationTurnedOff()
        location.enabled = true
        owned.removed(1, task)
        assertEquals(listOf(true, true), location.requests)
        assertTrue(location.enabled)
    }

    @Test fun failedEnableDoesNotClaimAnExternalEnable() {
        val location = Location(available = false)
        val policy = LocationAutomation(location::change)
        val task = Any()
        policy.resumed(1, task)
        location.available = true
        location.enabled = true
        policy.removed(1, task)
        assertEquals(listOf(true), location.requests)
        assertTrue(location.enabled)
    }

    @Test fun taskLimitKeepsLocationOnWhenACardCouldStillBeOpen() {
        val location = Location()
        val policy = LocationAutomation(location::change)
        val tasks = List(129) { Any() }
        tasks.forEachIndexed(policy::resumed)
        tasks.dropLast(1).forEachIndexed(policy::removed)
        assertEquals(listOf(true), location.requests)
        assertTrue(location.enabled)
    }

    private class Location(var enabled: Boolean = false, var available: Boolean = true) {
        val requests = mutableListOf<Boolean>()

        fun change(desired: Boolean): Boolean {
            requests.add(desired)
            if (!available || enabled == desired) return false
            enabled = desired
            return true
        }
    }
}
