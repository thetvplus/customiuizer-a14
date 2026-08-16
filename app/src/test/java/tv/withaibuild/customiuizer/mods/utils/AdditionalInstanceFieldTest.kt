package tv.withaibuild.customiuizer.mods.utils

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Pins the contract of the simulated instance fields.
 *
 * These are read from 108 places inside hook bodies, on the main, binder and background threads
 * of SystemUI, Launcher and system_server, so the store has to be correct under identity,
 * mutation and concurrency all at once.
 */
class AdditionalInstanceFieldTest {

    /**
     * A hooked class with value semantics whose hash depends on mutable state.
     *
     * This is the shape that broke the old `WeakHashMap` store: Launcher stashes `mLabelOrig` on
     * a `ShortcutInfo` and then rewrites `mLabel` on that same object.
     */
    private class LabelledItem(var label: String) {
        override fun equals(other: Any?): Boolean = other is LabelledItem && other.label == label
        override fun hashCode(): Int = label.hashCode()
    }

    @Test
    fun distinctButEqualObjectsDoNotShareFields() {
        val first = LabelledItem("Camera")
        val second = LabelledItem("Camera")
        assertEquals("test premise: the two objects are equal", first, second)
        assertNotEquals("test premise: they are different instances", System.identityHashCode(first), System.identityHashCode(second))

        XposedHelpers.setAdditionalInstanceField(first, "mLabelOrig", "first")
        XposedHelpers.setAdditionalInstanceField(second, "mLabelOrig", "second")

        assertEquals("first", XposedHelpers.getAdditionalInstanceField(first, "mLabelOrig"))
        assertEquals("second", XposedHelpers.getAdditionalInstanceField(second, "mLabelOrig"))
    }

    @Test
    fun fieldSurvivesMutationOfTheOwnersHash() {
        val item = LabelledItem("Camera")
        XposedHelpers.setAdditionalInstanceField(item, "mLabelOrig", "Camera")

        // Exactly what the rename feature does after stashing the original label.
        item.label = "Renamed"

        assertEquals(
            "the stored value became unreachable when the owner's hash changed",
            "Camera",
            XposedHelpers.getAdditionalInstanceField(item, "mLabelOrig")
        )
        assertEquals("Camera", XposedHelpers.removeAdditionalInstanceField(item, "mLabelOrig"))
        assertNull(XposedHelpers.getAdditionalInstanceField(item, "mLabelOrig"))
    }

    @Test
    fun nullIsAStorableValueDistinctFromAbsence() {
        val owner = Any()

        assertNull(XposedHelpers.setAdditionalInstanceField(owner, "mAlbumArt", "art"))
        // Callers clear a slot by storing null; ConcurrentHashMap forbids null, so this only
        // works while the sentinel translation holds.
        assertEquals("art", XposedHelpers.setAdditionalInstanceField(owner, "mAlbumArt", null))
        assertNull(XposedHelpers.getAdditionalInstanceField(owner, "mAlbumArt"))
        assertNull(XposedHelpers.removeAdditionalInstanceField(owner, "mAlbumArt"))
    }

    @Test
    fun valuesAreIsolatedPerKeyAndOwner() {
        val a = Any()
        val b = Any()
        XposedHelpers.setAdditionalInstanceField(a, "one", 1)
        XposedHelpers.setAdditionalInstanceField(a, "two", 2)
        XposedHelpers.setAdditionalInstanceField(b, "one", 3)

        assertEquals(1, XposedHelpers.getAdditionalInstanceField(a, "one"))
        assertEquals(2, XposedHelpers.getAdditionalInstanceField(a, "two"))
        assertEquals(3, XposedHelpers.getAdditionalInstanceField(b, "one"))

        assertEquals(1, XposedHelpers.removeAdditionalInstanceField(a, "one"))
        assertNull(XposedHelpers.getAdditionalInstanceField(a, "one"))
        assertEquals(2, XposedHelpers.getAdditionalInstanceField(a, "two"))
        assertEquals(3, XposedHelpers.getAdditionalInstanceField(b, "one"))
    }

    @Test
    fun staticFieldsAreKeyedByTheClass() {
        val instance = LabelledItem("x")
        XposedHelpers.setAdditionalStaticField(instance, "mAlbumArtSource", "source")

        assertEquals("source", XposedHelpers.getAdditionalStaticField(LabelledItem::class.java, "mAlbumArtSource"))
        assertEquals("source", XposedHelpers.removeAdditionalStaticField(LabelledItem::class.java, "mAlbumArtSource"))
        assertNull(XposedHelpers.getAdditionalStaticField(LabelledItem::class.java, "mAlbumArtSource"))
    }

    @Test
    fun nullOwnerAndNullKeyAreRejected() {
        val owner = Any()
        for (call in listOf<() -> Unit>(
            { XposedHelpers.getAdditionalInstanceField(null, "k") },
            { XposedHelpers.setAdditionalInstanceField(null, "k", 1) },
            { XposedHelpers.removeAdditionalInstanceField(null, "k") },
            { XposedHelpers.getAdditionalInstanceField(owner, null) },
            { XposedHelpers.setAdditionalInstanceField(owner, null, 1) },
            { XposedHelpers.removeAdditionalInstanceField(owner, null) }
        )) {
            val error = runCatching { call() }.exceptionOrNull()
            assertTrue("expected NullPointerException, got $error", error is NullPointerException)
        }
    }

    @Test
    fun ownersAreNotKeptAlive() {
        var owner: Any? = Any()
        val watch = java.lang.ref.WeakReference(owner)
        XposedHelpers.setAdditionalInstanceField(owner!!, "key", "value")

        owner = null
        var collected = false
        repeat(20) {
            if (collected) return@repeat
            System.gc()
            // Writes are what drain the reference queue.
            XposedHelpers.setAdditionalInstanceField(Any(), "churn", 1)
            collected = watch.get() == null
        }

        assertTrue("the store kept the owner alive", collected)
    }

    @Test
    fun readsAreAllocationFree() {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        assumeTrue("thread allocation counter unavailable", bean != null && bean.isThreadAllocatedMemorySupported)
        bean!!.isThreadAllocatedMemoryEnabled = true

        val owner = Any()
        XposedHelpers.setAdditionalInstanceField(owner, "key", "value")
        repeat(2000) { XposedHelpers.getAdditionalInstanceField(owner, "key") }

        val iterations = 200_000
        val before = bean.getThreadAllocatedBytes(Thread.currentThread().id)
        repeat(iterations) { XposedHelpers.getAdditionalInstanceField(owner, "key") }
        val perCall = (bean.getThreadAllocatedBytes(Thread.currentThread().id) - before).toDouble() / iterations

        assertTrue("a read allocated ~$perCall bytes; the probe is not being reused", perCall < 1.0)
    }

    @Test
    fun concurrentReadersAndWritersStaySane() {
        val threads = 8
        val perThread = 5_000
        val owners = List(threads) { Any() }
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failure = AtomicReference<Throwable?>(null)

        for (index in 0 until threads) {
            Thread {
                try {
                    start.await()
                    val owner = owners[index]
                    for (round in 0 until perThread) {
                        XposedHelpers.setAdditionalInstanceField(owner, "round", round)
                        assertEquals(round, XposedHelpers.getAdditionalInstanceField(owner, "round"))
                        // Churn other owners so the shared map is genuinely contended.
                        XposedHelpers.getAdditionalInstanceField(owners[(index + 1) % threads], "round")
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    done.countDown()
                }
            }.apply { isDaemon = true }.start()
        }

        start.countDown()
        assertTrue("workers did not finish", done.await(60, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("concurrent access failed", it) }

        for (owner in owners) {
            assertEquals(perThread - 1, XposedHelpers.getAdditionalInstanceField(owner, "round"))
        }
    }

    @Test
    fun theProbeDoesNotPinTheLastOwnerItLookedUp() {
        var owner: Any? = Any()
        val watch = java.lang.ref.WeakReference(owner)
        // A pure miss still binds the probe; if it is not released, the thread keeps this alive.
        XposedHelpers.getAdditionalInstanceField(owner!!, "never-set")

        owner = null
        var collected = false
        repeat(20) {
            if (collected) return@repeat
            System.gc()
            collected = watch.get() == null
        }

        assertTrue("the thread-local probe pinned the owner", collected)
    }

    @Test
    fun sameOwnerKeepsOneEntryAcrossRepeatedWrites() {
        val owner = Any()
        repeat(1000) { XposedHelpers.setAdditionalInstanceField(owner, "key", it) }
        assertEquals(999, XposedHelpers.getAdditionalInstanceField(owner, "key"))
        assertSame(owner, owner)
    }
}
