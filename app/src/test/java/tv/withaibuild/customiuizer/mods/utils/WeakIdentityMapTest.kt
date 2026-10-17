package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WeakIdentityMapTest {

    private class EqualOwner {
        override fun equals(other: Any?): Boolean = other is EqualOwner
        override fun hashCode(): Int = 1
    }

    @Test
    fun sameInstanceSharesPendingState() {
        val map = WeakIdentityMap<Any, Any>()
        val owner = Any()
        val state = Any()
        assertNull(map.put(owner, state))
        assertSame(state, map[owner])
    }

    @Test
    fun equalButDistinctOwnersDoNotShareState() {
        val map = WeakIdentityMap<EqualOwner, String>()
        val a = EqualOwner()
        val b = EqualOwner()
        map.put(a, "A")
        map.put(b, "B")
        assertEquals("A", map[a])
        assertEquals("B", map[b])
        assertNotSame(map[a], map[b])
    }

    @Test
    fun identityHashCodeCollisionsAreDisambiguated() {
        val map = WeakIdentityMap<Any, Any>()
        val a = Any()
        val b = Any()
        map.put(a, "A")
        map.put(b, "B")
        assertEquals("A", map[a])
        assertEquals("B", map[b])
    }

    @Test
    fun removeByIdentityIsExact() {
        val map = WeakIdentityMap<EqualOwner, String>()
        val a = EqualOwner()
        val b = EqualOwner()
        map.put(a, "A")
        map.put(b, "B")
        map.remove(a)
        assertNull(map[a])
        assertEquals("B", map[b])
    }

    @Test
    fun replaceRefreshesValueForSameIdentity() {
        val map = WeakIdentityMap<Any, String>()
        val owner = Any()
        assertNull(map.put(owner, "first"))
        assertEquals("first", map.put(owner, "second"))
        assertEquals("second", map[owner])
    }

    @Test
    fun expungeReturnsClearedValuesAndRemovesThem() {
        val map = WeakIdentityMap<Any, String>()
        var owner: Any? = Any()
        map.put(owner!!, "state")

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        val cleared = map.expunge()
        assertEquals(listOf("state"), cleared)
        assertEquals(0, map.size)
    }

    @Test
    fun valuesSnapshotOnlyReturnsReachableValues() {
        val map = WeakIdentityMap<Any, String>()
        var owner: Any? = Any()
        map.put(owner!!, "alive")

        val aliveSnapshot = map.valuesSnapshot()
        assertEquals(listOf("alive"), aliveSnapshot)

        owner = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        val afterGcSnapshot = map.valuesSnapshot()
        assertEquals(0, afterGcSnapshot.size)
    }
}
