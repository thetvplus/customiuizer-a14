package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

class OwnedRegistrationsTest {

    @Test
    fun cleanupRunsOnlyStaleEntriesAndDropsThem() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("old") { cleaned.add("old-a") }
        registrations.register("old") { cleaned.add("old-b") }
        registrations.register("current") { cleaned.add("current") }

        registrations.cleanupWhere { it == "old" }

        assertEquals(setOf("old-a", "old-b"), cleaned.toSet())
        assertEquals(1, registrations.size)

        // A second pass must not run the already-removed cleanups again.
        cleaned.clear()
        registrations.cleanupWhere { it == "old" }
        assertTrue(cleaned.isEmpty())
        assertEquals(1, registrations.size)
    }

    @Test
    fun allowsMultipleRegistrationsPerOwner() {
        val registrations = OwnedRegistrations<String>()
        registrations.register("gen1") {}
        registrations.register("gen1") {}
        registrations.register("gen1") {}
        assertEquals(3, registrations.size)

        registrations.cleanupWhere { it == "gen1" }
        assertEquals(0, registrations.size)
    }

    @Test
    fun ordinaryCleanupFailureIsIsolatedPerEntry() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("old") { cleaned.add("first") }
        registrations.register("old") { throw IllegalStateException("ROM removed the member") }
        registrations.register("old") { cleaned.add("last") }

        registrations.cleanupWhere { it == "old" }

        // The failing entry must not prevent the other stale registrations from being released,
        // and every stale entry must be dropped so the failure is not retried forever.
        assertEquals(setOf("first", "last"), cleaned.toSet())
        assertEquals(0, registrations.size)
    }

    @Test(expected = OutOfMemoryError::class)
    fun fatalCleanupErrorPropagates() {
        val registrations = OwnedRegistrations<String>()
        registrations.register("old") { throw OutOfMemoryError("fatal") }
        registrations.cleanupWhere { it == "old" }
    }

    @Test(expected = ThreadDeath::class)
    fun threadDeathCleanupErrorPropagates() {
        val registrations = OwnedRegistrations<String>()
        registrations.register("old") { throw ThreadDeath() }
        registrations.cleanupWhere { it == "old" }
    }

    @Test
    fun identityPredicateMatchesGenerationSemantics() {
        val registrations = OwnedRegistrations<Any>()
        val gen1 = Any()
        val gen2 = Any()
        val cleaned = mutableListOf<Any>()
        val gen1Ref = WeakReference(gen1)
        val gen2Ref = WeakReference(gen2)
        registrations.register(gen1) { gen1Ref.get()?.let(cleaned::add) }
        registrations.register(gen2) { gen2Ref.get()?.let(cleaned::add) }

        // Same pattern as the per-display cleanup: everything not owned by the current
        // generation is stale, including entries whose owner was lost.
        val current: Any? = gen2
        registrations.cleanupWhere { it !== current }

        assertEquals(listOf<Any>(gen1), cleaned)
        assertEquals(1, registrations.size)
    }

    @Test
    fun handleCleanupIsExactOnce() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        val handle = registrations.register("owner") { cleaned.add("cleanup") }

        assertTrue(handle.cleanupNow())
        assertEquals(0, registrations.size)
        assertEquals(listOf("cleanup"), cleaned)

        // Second and subsequent calls do nothing and return false.
        assertFalse(handle.cleanupNow())
        assertEquals(0, registrations.size)
        assertEquals(listOf("cleanup"), cleaned)
    }

    @Test
    fun handleCleanupAfterGenerationCleanupIsNoOp() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        val handle = registrations.register("old") { cleaned.add("cleanup") }

        registrations.cleanupWhere { it == "old" }
        assertEquals(listOf("cleanup"), cleaned)

        // The handle must not run the cleanup again.
        assertFalse(handle.cleanupNow())
        assertEquals(0, registrations.size)
        assertEquals(listOf("cleanup"), cleaned)
    }

    @Test
    fun cleanupIsReentrantSafe() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("old-a") { cleaned.add("old-a") }
        registrations.register("old-b") {
            cleaned.add("old-b")
            // Reenter cleanupWhere while it is still running. The snapshot must not include
            // the new registration, so it is cleaned on the next pass, not during this one.
            registrations.register("new") { cleaned.add("new") }
            registrations.cleanupWhere { it == "new" }
        }

        registrations.cleanupWhere { it.startsWith("old") }

        // old-b's reentrant register("new") and cleanup of new ran inside the callback.
        assertTrue("new" in cleaned)
        assertEquals(0, registrations.size)
    }

    @Test
    fun cleanupCallbackFailureDoesNotBlockOthers() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("a") { cleaned.add("a") }
        registrations.register("b") { throw NoSuchMethodError("missing") }
        registrations.register("c") { cleaned.add("c") }

        registrations.cleanupWhere { true }

        assertEquals(setOf("a", "c"), cleaned.toSet())
        assertEquals(0, registrations.size)
    }

    @Test
    fun weakOwnerAllowsCollection() {
        val registrations = OwnedRegistrations<Any>()
        var owner: Any? = Any()
        val handle = registrations.register(owner!!) { }
        assertEquals(1, registrations.size)

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        // After the owner is collectable, cleanupWhere sees it as stale and drops the entry.
        registrations.cleanupWhere { true }
        assertEquals(0, registrations.size)
        assertFalse(handle.cleanupNow())
    }

    @Test
    fun emptyCleanupIsSafe() {
        val registrations = OwnedRegistrations<String>()
        registrations.cleanupWhere { true }
        assertEquals(0, registrations.size)
    }

    @Test
    fun cleanupWhereRunsCallbackAfterOwnerCollection() {
        val registrations = OwnedRegistrations<Any>()
        var owner: Any? = Any()
        val cleaned = mutableListOf<String>()
        val ownerRef = WeakReference(owner)
        registrations.register(owner!!) { cleaned.add("cleaned") }

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        registrations.cleanupWhere { true }
        assertEquals(listOf("cleaned"), cleaned)
        assertEquals(0, registrations.size)
    }

    @Test
    fun cleanupNowRunsCallbackAfterOwnerCollection() {
        val registrations = OwnedRegistrations<Any>()
        var owner: Any? = Any()
        val cleaned = mutableListOf<String>()
        val handle = registrations.register(owner!!) { cleaned.add("cleaned") }

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        assertTrue(handle.cleanupNow())
        assertEquals(listOf("cleaned"), cleaned)
        assertEquals(0, registrations.size)
    }

    @Test
    fun cleanupAllRunsCallbackAfterOwnerCollection() {
        val registrations = OwnedRegistrations<Any>()
        var owner: Any? = Any()
        val cleaned = mutableListOf<String>()
        registrations.register(owner!!) { cleaned.add("cleaned") }

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        registrations.cleanupAll()
        assertEquals(listOf("cleaned"), cleaned)
        assertEquals(0, registrations.size)
    }

    @Test
    fun handleCleanupBeforeCleanupAllDoesNotDuplicate() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        val handle = registrations.register("owner") { cleaned.add("cleanup") }

        assertTrue(handle.cleanupNow())
        registrations.cleanupAll()

        assertEquals(listOf("cleanup"), cleaned)
        assertEquals(0, registrations.size)
    }

    @Test
    fun cleanupAllBeforeHandleDoesNotDuplicate() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        val handle = registrations.register("owner") { cleaned.add("cleanup") }

        registrations.cleanupAll()
        assertFalse(handle.cleanupNow())

        assertEquals(listOf("cleanup"), cleaned)
        assertEquals(0, registrations.size)
    }

    @Test
    fun cleanupAllSnapshotDoesNotRunNewRegistrations() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("a") {
            cleaned.add("a")
            registrations.register("b") { cleaned.add("b") }
        }

        registrations.cleanupAll()

        // 'b' is in the live list, not the snapshot, so it is not cleaned by this pass.
        assertEquals(listOf("a"), cleaned)
        assertEquals(1, registrations.size)
    }

    @Test
    fun cleanupWhereReentryIsIsolated() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("a") {
            cleaned.add("a")
            registrations.register("b") { cleaned.add("b") }
            registrations.cleanupWhere { it == "b" }
        }

        registrations.cleanupWhere { it == "a" }

        assertTrue("b" in cleaned)
        assertEquals(0, registrations.size)
    }

    @Test(expected = OutOfMemoryError::class)
    fun cleanupAllFatalPropagates() {
        val registrations = OwnedRegistrations<String>()
        registrations.register("a") { throw OutOfMemoryError("fatal") }
        registrations.register("b") { }
        registrations.cleanupAll()
    }

    @Test
    fun cleanupAllIsExactOnce() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("a") { cleaned.add("a") }

        registrations.cleanupAll()
        registrations.cleanupAll()

        assertEquals(listOf("a"), cleaned)
        assertEquals(0, registrations.size)
    }
}
