package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

class StatusBarDisplayRegistryTest {

    @Test
    fun bindAttachesOwnerToDisplayAndProvidesState() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val state = registry.bind(owner, 0)

        assertNotNull(state)
        assertSame(owner, state.generation?.get())
        assertSame(state, registry.get(0))
    }

    @Test
    fun pendingOwnersAreIsolated() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val a = Any()
        val b = Any()
        val stateA = registry.getOrCreatePending(a)
        val stateB = registry.getOrCreatePending(b)

        assertNotSame(stateA, stateB)
        assertEquals(2, registry.allStates().size)
    }

    @Test
    fun sameDisplayReplacementCleansOldGeneration() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val oldOwner = Any()
        val newOwner = Any()
        val cleanups = mutableListOf<Any>()

        val oldState = registry.bind(oldOwner, 0)
        val oldOwnerRef = WeakReference(oldOwner)
        oldState.registrations.register(oldOwner) { oldOwnerRef.get()?.let(cleanups::add) }

        val newState = registry.bind(newOwner, 0)

        assertSame(newState, registry.get(0))
        assertEquals(listOf(oldOwner), cleanups)
        assertEquals(0, oldState.registrations.size)
    }

    @Test
    fun crossDisplayIsolation() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val display0Owner = Any()
        val display1Owner = Any()

        registry.bind(display0Owner, 0)
        registry.bind(display1Owner, 1)

        assertEquals(2, registry.allStates().size)
        assertSame(display0Owner, registry.get(0)?.generation?.get())
        assertSame(display1Owner, registry.get(1)?.generation?.get())

        // Replacing display 0 must not touch display 1.
        val newDisplay0Owner = Any()
        registry.bind(newDisplay0Owner, 0)
        assertSame(display1Owner, registry.get(1)?.generation?.get())
    }

    @Test
    fun sameOwnerReattachKeepsState() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val state = registry.bind(owner, 0)
        state.registrations.register(owner) { }

        val again = registry.bind(owner, 0)
        assertSame(state, again)
        assertEquals(1, again.registrations.size)
    }

    @Test
    fun pendingMigratesToDisplayOnBind() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val pending = registry.getOrCreatePending(owner)
        pending.registrations.register(owner) { }

        val bound = registry.bind(owner, 0)
        assertSame(pending, bound)
        assertSame(bound, registry.get(0))
        assertTrue(registry.allStates().none { it === pending && it !== bound })
    }

    @Test
    fun pruneRemovesDeadStates() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        var owner0: Any? = Any()
        var owner1: Any? = Any()
        registry.bind(owner0!!, 0)
        registry.bind(owner1!!, 1)

        assertEquals(2, registry.allStates().size)

        owner0 = null
        owner1 = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        registry.prune()
        assertEquals(0, registry.allStates().size)
    }

    @Test
    fun multiDisplaySecondRowsAreIndependent() {
        val registry = StatusBarDisplayRegistry<Any, String>()
        val owner0 = Any()
        val owner1 = Any()
        val state0 = registry.bind(owner0, 0)
        val state1 = registry.bind(owner1, 1)

        state0.secondRow = java.lang.ref.WeakReference("row-0")
        state1.secondRow = java.lang.ref.WeakReference("row-1")

        assertEquals("row-0", state0.secondRow?.get())
        assertEquals("row-1", state1.secondRow?.get())
    }

    @Test
    fun pruneReleasesRegistrationsForDeadDisplay() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        var owner: Any? = Any()
        val cleanups = mutableListOf<String>()
        val state = registry.bind(owner!!, 0)
        state.registrations.register(owner!!) { cleanups.add("cleanup") }

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        registry.prune()
        assertEquals(listOf("cleanup"), cleanups)
        assertEquals(0, registry.allStates().size)
    }

    @Test
    fun pruneReleasesRegistrationsForDeadPendingOwner() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        var owner: Any? = Any()
        val cleanups = mutableListOf<String>()
        val state = registry.getOrCreatePending(owner!!)
        state.registrations.register(owner!!) { cleanups.add("cleanup") }

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        registry.prune()
        assertEquals(listOf("cleanup"), cleanups)
        assertEquals(0, registry.allStates().size)
    }

    @Test
    fun pruneKeepsStateWhenCleanupAddsNewRegistration() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        var owner: Any? = Any()
        val state = registry.bind(owner!!, 0)
        state.registrations.register(owner!!) {
            state.registrations.register(Any()) { }
        }

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        registry.prune()
        assertEquals(1, state.registrations.size)
        assertEquals(1, registry.allStates().size)
    }

    @Test
    fun activeGenerationIsNotPruned() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        registry.bind(owner, 0)
        registry.prune()
        assertEquals(1, registry.allStates().size)
    }

    @Test
    fun activeSecondRowWithoutGenerationIsPruned() {
        val registry = StatusBarDisplayRegistry<Any, String>()
        var owner: Any? = Any()
        val state = registry.bind(owner!!, 0)
        state.secondRow = WeakReference("row")

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        // The row is still reachable as a literal, but the generation is gone.
        // The state should be cleaned and removed.
        registry.prune()
        assertEquals(0, registry.allStates().size)
    }

    @Test
    fun displayReuseDoesNotInheritOldRegistrations() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val oldOwner = Any()
        val newOwner = Any()
        val cleanups = mutableListOf<String>()
        val oldState = registry.bind(oldOwner, 0)
        oldState.registrations.register(oldOwner) { cleanups.add("old") }

        val newState = registry.bind(newOwner, 0)
        assertEquals(0, newState.registrations.size)
        assertEquals(listOf("old"), cleanups)
    }

    @Test
    fun pendingOwnersAreWeaklyHeld() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        var owner: Any? = Any()
        registry.getOrCreatePending(owner!!)

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        registry.prune()
        assertEquals(0, registry.allStates().size)
    }

    @Test
    fun pendingABIdentityIsolation() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val a = Any()
        val b = Any()
        val stateA = registry.getOrCreatePending(a)
        val stateB = registry.getOrCreatePending(b)
        assertNotSame(stateA, stateB)
    }

    @Test
    fun pendingMigratesRegistrationsToDisplayOnBind() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val cleanups = mutableListOf<String>()
        val pending = registry.getOrCreatePending(owner)
        pending.registrations.register(owner) { cleanups.add("cleanup") }

        val bound = registry.bind(owner, 0)
        assertSame(pending, bound)
        assertSame(bound, registry.get(0))
    }

    @Test
    fun equalButDistinctPendingOwnersDoNotShareState() {
        val registry = StatusBarDisplayRegistry<EqualOwner, Any>()
        val a = EqualOwner()
        val b = EqualOwner()
        val stateA = registry.getOrCreatePending(a)
        val stateB = registry.getOrCreatePending(b)
        assertNotSame(stateA, stateB)
    }

    @Test
    fun pendingBecomesBoundCountIsZero() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        registry.getOrCreatePending(owner)
        assertEquals(1, registry.pendingCount)

        registry.bind(owner, 0)
        assertEquals(0, registry.pendingCount)
        assertEquals(1, registry.allStatesSnapshot().size)
    }

    @Test
    fun modifyingStateFieldsDoesNotBreakRemoval() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val state = registry.getOrCreatePending(owner)
        state.generation = WeakReference(Any())
        state.secondRow = WeakReference(Any())

        val bound = registry.bind(owner, 0)
        assertSame(state, bound)
        registry.detach(owner)
        assertEquals(0, registry.allStatesSnapshot().size)
    }

    @Test
    fun detachBoundOwnerReleasesState() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val cleanups = mutableListOf<String>()
        val state = registry.bind(owner, 0)
        state.registrations.register(owner) { cleanups.add("cleanup") }

        registry.detach(owner)
        assertEquals(listOf("cleanup"), cleanups)
        assertEquals(0, registry.allStatesSnapshot().size)
    }

    @Test
    fun delayedDetachDoesNotAffectNewGeneration() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val oldOwner = Any()
        val newOwner = Any()
        val oldCleanups = mutableListOf<String>()
        val newCleanups = mutableListOf<String>()

        val oldState = registry.bind(oldOwner, 0)
        oldState.registrations.register(oldOwner) { oldCleanups.add("old") }

        val newState = registry.bind(newOwner, 0)
        newState.registrations.register(newOwner) { newCleanups.add("new") }

        // A delayed detach for the old owner must not release the new generation.
        registry.detach(oldOwner)
        assertEquals(listOf("old"), oldCleanups)
        assertTrue(newCleanups.isEmpty())
        assertSame(newState, registry.get(0))
    }

    @Test
    fun detachIsIdempotent() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val cleanups = mutableListOf<String>()
        val state = registry.bind(owner, 0)
        state.registrations.register(owner) { cleanups.add("cleanup") }

        registry.detach(owner)
        registry.detach(owner)
        assertEquals(listOf("cleanup"), cleanups)
    }

    @Test
    fun pendingCountNotifiesScheduler() {
        var scheduled = 0
        var cancelled = 0
        val registry = StatusBarDisplayRegistry<Any, Any>(
            onPendingChanged = { hasPending ->
                if (hasPending) scheduled++ else cancelled++
            }
        )
        val owner = Any()
        registry.getOrCreatePending(owner)
        assertEquals(1, scheduled)
        assertEquals(0, cancelled)

        registry.bind(owner, 0)
        assertEquals(1, cancelled)
    }

    @Test
    fun stateObjectIdentityIsStableAfterFieldMutation() {
        val registry = StatusBarDisplayRegistry<Any, String>()
        val owner = Any()
        val state = registry.bind(owner, 0)
        val stateRef = state
        state.secondRow = WeakReference("row-0")
        state.generation = WeakReference(owner)
        assertSame(stateRef, state)
        assertSame(state, registry.get(0))
    }

    private class EqualOwner {
        override fun equals(other: Any?): Boolean = other is EqualOwner
        override fun hashCode(): Int = 1
    }
}
