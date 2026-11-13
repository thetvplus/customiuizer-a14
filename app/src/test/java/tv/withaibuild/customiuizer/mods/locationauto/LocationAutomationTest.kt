package tv.withaibuild.customiuizer.mods.locationauto

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocationAutomationTest {
    private val handler = Handler(Looper.getMainLooper())
    private val clock get() = shadowOf(handler.looper)
    private fun automation(location: Location) = LocationAutomation(handler, location::change) {}

    @Test fun listModeAndCloseAreIndependent() {
        val allowed = LocationAutomationOptions(true, "allow", setOf("example.maps"), autoClose = false)
        assertEquals(true, allowed.desired)
        assertFalse(allowed.tracksTasks)
        assertTrue(allowed.copy(autoClose = true).tracksTasks)
        val blocked = allowed.copy(mode = "block", autoClose = true)
        assertEquals(false, blocked.desired)
        assertFalse(blocked.tracksTasks)
        assertFalse(blocked.delaysBlockedClose)
        assertTrue(blocked.copy(closeDelaySeconds = 1).delaysBlockedClose)
        assertFalse(allowed.copy(closeDelaySeconds = 30).delaysBlockedClose)
        assertNull(allowed.copy(apps = emptySet()).desired)
        assertNull(allowed.copy(enabled = false).desired)
        assertNull(allowed.copy(mode = "unknown").desired)
    }

    @Test fun closesOnlyAfterLastMatchingTaskAndSkipsReadsWhileOwned() {
        val location = Location()
        val policy = automation(location)
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
        val policy = automation(location)
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
        val preexisting = automation(location)
        preexisting.resumed(1, task)
        preexisting.removed(1, task)
        assertEquals(listOf(true), location.requests)
        assertTrue(location.enabled)

        location.enabled = false
        val owned = automation(location)
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
        val policy = automation(location)
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
        val policy = automation(location)
        val tasks = List(129) { Any() }
        tasks.forEachIndexed(policy::resumed)
        tasks.dropLast(1).forEachIndexed(policy::removed)
        assertEquals(listOf(true), location.requests)
        assertTrue(location.enabled)
    }

    @Test fun collectedTaskFreesACapacitySlotAndDoesNotBlockLastRemoval() {
        val location = Location()
        val policy = automation(location)
        val tasks = List(128) { Any() }
        tasks.forEachIndexed(policy::resumed)
        clearTaskReference(policy, 0)
        val replacement = Any()
        policy.resumed(128, replacement)
        clearTaskReference(policy, 1)
        for (id in 2 until tasks.size) policy.removed(id, tasks[id])
        assertEquals(listOf(true), location.requests)
        assertTrue(policy.removed(128, replacement))
        assertEquals(listOf(true, false), location.requests)
        assertTrue(policy.idle)
        assertFalse(location.enabled)
    }

    private fun clearTaskReference(policy: LocationAutomation, id: Int) {
        val field = LocationAutomation::class.java.getDeclaredField("tasks").apply { isAccessible = true }
        val tracked = field.get(policy) as Map<*, *>
        (tracked[id] as WeakReference<*>).clear()
    }

    @Test fun allowlistResumesImmediatelyAndReopeningCancelsThePendingClose() {
        val location = Location()
        val policy = automation(location)
        val first = Any()
        val second = Any()
        policy.resumed(1, first)
        assertEquals(listOf(true), location.requests)
        policy.removed(1, first, 30)
        clock.idleFor(Duration.ofSeconds(29))
        assertTrue(location.enabled)
        policy.resumed(2, second)
        clock.idleFor(Duration.ofSeconds(1))
        assertTrue(location.enabled)
        policy.removed(2, second, 1)
        clock.idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(true, false), location.requests)
    }

    @Test fun userTurnOffReleasesThePendingAllowlistClose() {
        val location = Location()
        val policy = automation(location)
        val task = Any()
        policy.resumed(1, task)
        policy.removed(1, task, 1)
        location.enabled = false
        policy.locationTurnedOff()
        location.enabled = true
        clock.idleFor(Duration.ofSeconds(1))
        assertTrue(location.enabled)
        assertEquals(listOf(true), location.requests)
    }

    @Test fun recoveredCardRemovalChecksOwnershipAndClosesImmediately() {
        val location = Location(enabled = true)
        var revision = 10
        val recordedRevision = revision
        fun recovered() = LocationAutomation(handler, location::change, acquire = { true }, release = {
            if (revision == recordedRevision) location.change(false)
        }) {}

        revision++
        recovered().unobservedTaskRemoved(0)
        assertTrue(location.enabled)
        assertTrue(location.requests.isEmpty())

        revision = recordedRevision
        val policy = recovered()
        val task = Any()
        policy.resumed(2, task)
        policy.unobservedTaskRemoved(0)
        assertTrue(location.enabled)
        policy.removed(2, task, 30)
        clock.idleFor(Duration.ofSeconds(30))
        assertEquals(listOf(false), location.requests)

        location.enabled = true
        recovered().unobservedTaskRemoved(0)
        assertFalse(location.enabled)
    }

    @Test fun lastUnobservedCardCanFinishCurrentOwnershipWithConfiguredDelay() {
        val location = Location()
        val observed = Any()
        val maps = ComponentName("example.maps", "Maps")
        val apps = setOf(maps.packageName)
        var recent = listOf(recentTask(1, base = maps), recentTask(2, base = maps))
        var removedId = 1
        fun tracking() = LocationAutomation(handler, location::change, release = {
            if (!hasRemainingLocationTasks(recent, removedId, apps)) location.change(false)
        }) {}

        val first = tracking()
        first.resumed(1, observed)
        first.removed(1, observed, 1)
        clock.idleFor(Duration.ofSeconds(1))
        assertTrue(location.enabled)

        removedId = 2
        recent = listOf(recentTask(2, base = maps))
        tracking().unobservedTaskRemoved(30)
        clock.idleFor(Duration.ofSeconds(29))
        assertTrue(location.enabled)
        clock.idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(true, false), location.requests)
    }

    @Test fun unobservedRemovalRestartsTheAllowlistDelayWhileCloseIsPending() {
        val location = Location()
        val policy = automation(location)
        val observed = Any()
        policy.resumed(1, observed)
        policy.removed(1, observed, 30)
        clock.idleFor(Duration.ofSeconds(29))
        policy.unobservedTaskRemoved(30)
        clock.idleFor(Duration.ofSeconds(29))
        assertEquals(listOf(true), location.requests)
        clock.idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(true, false), location.requests)
    }

    @Test fun nativeRecentTasksPreventClosingForUnobservedMatchingCards() {
        val apps = setOf("example.maps")
        val maps = ComponentName("example.maps", "Maps")
        val removed = recentTask(1, base = maps)
        val other = recentTask(2)
        assertFalse(hasRemainingLocationTasks(listOf(removed, other), 1, apps))
        assertTrue(hasRemainingLocationTasks(listOf(removed, recentTask(2, base = maps)), 1, apps))
        assertTrue(hasRemainingLocationTasks(listOf(removed, recentTask(2, top = maps)), 1, apps))
        assertFalse(hasRemainingLocationTasks(List(127) { recentTask(it) }, 1, apps))
        assertTrue(hasRemainingLocationTasks(List(128) { recentTask(it) }, 1, apps))
    }

    @Suppress("DEPRECATION")
    private fun recentTask(id: Int, base: ComponentName? = null, top: ComponentName? = null) =
        ActivityManager.RecentTaskInfo().apply {
            persistentId = id
            baseIntent = Intent().setComponent(base)
            topActivity = top
        }

    @Test fun blocklistKeepsOneDeadlineUntilLeavingAndDoesNotReenable() {
        val location = Location(enabled = true)
        val policy = automation(location)
        policy.requestClose(10)
        clock.idleFor(Duration.ofSeconds(9))
        policy.requestClose(10)
        assertTrue(location.enabled)
        clock.idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(false), location.requests)

        location.enabled = true
        policy.requestClose(10)
        policy.cancelClose()
        clock.idleFor(Duration.ofSeconds(10))
        assertTrue(location.enabled)
        assertEquals(listOf(false), location.requests)
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
