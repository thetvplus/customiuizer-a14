package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class StatusBarNetworkSpeedDispatcherTest {

    private data class FakeRow(val name: String, var attached: Boolean = true)

    private class FakeOwner

    private val payload = StatusBarNetworkSpeedDispatcher.NetworkSpeedPayload(
        number = 100,
        unit = "KB/s",
        visible = true,
    )

    @Test
    fun appliesPayloadToCurrentSnapshot() {
        val registry = StatusBarDisplayRegistry<FakeOwner, FakeRow>()
        val owner = FakeOwner()
        val state = registry.bind(owner, 0)
        state.secondRow = java.lang.ref.WeakReference(FakeRow("row-0"))

        val applied = mutableListOf<String>()
        val lastApplied = AtomicLong(0)

        StatusBarNetworkSpeedDispatcher.dispatch(
            payload,
            seq = 1,
            lastApplied = lastApplied,
            registry = registry,
            applier = { s, p ->
                applied.add("${s.generation?.get()}:${p.number}")
            }
        )

        assertEquals(listOf("$owner:100"), applied)
        assertEquals(1, lastApplied.get())
    }

    @Test
    fun staleSequenceIsDropped() {
        val registry = StatusBarDisplayRegistry<FakeOwner, FakeRow>()
        val lastApplied = AtomicLong(5)

        var called = 0
        StatusBarNetworkSpeedDispatcher.dispatch(
            payload,
            seq = 3,
            lastApplied = lastApplied,
            registry = registry,
            applier = { _, _ -> called++ }
        )

        assertEquals(0, called)
        assertEquals(5, lastApplied.get())
    }

    @Test
    fun newerSequenceReplacesOldAndOldPayloadIsIgnored() {
        val registry = StatusBarDisplayRegistry<FakeOwner, FakeRow>()
        val owner = FakeOwner()
        registry.bind(owner, 0)

        val lastApplied = AtomicLong(0)

        val oldPayload = StatusBarNetworkSpeedDispatcher.NetworkSpeedPayload(1, "A", true)
        val newPayload = StatusBarNetworkSpeedDispatcher.NetworkSpeedPayload(2, "B", true)

        val applied = mutableListOf<Int>()
        // Older seq fires first.
        StatusBarNetworkSpeedDispatcher.dispatch(oldPayload, seq = 1, lastApplied, registry) { _, p ->
            applied.add(p.number as Int)
        }
        assertEquals(listOf(1), applied)

        // Newer seq fires second.
        StatusBarNetworkSpeedDispatcher.dispatch(newPayload, seq = 2, lastApplied, registry) { _, p ->
            applied.add(p.number as Int)
        }
        assertEquals(listOf(1, 2), applied)

        // Old seq (3 > 2? no, let's use 1) fires again and is dropped.
        StatusBarNetworkSpeedDispatcher.dispatch(oldPayload, seq = 1, lastApplied, registry) { _, p ->
            applied.add(p.number as Int)
        }
        assertEquals(listOf(1, 2), applied)
    }

    @Test
    fun snapshotDoesNotConcurrentlyModify() {
        val registry = StatusBarDisplayRegistry<FakeOwner, FakeRow>()
        val owner = FakeOwner()
        registry.bind(owner, 0)

        val applied = mutableListOf<Int>()
        StatusBarNetworkSpeedDispatcher.dispatch(
            payload,
            seq = 1,
            lastApplied = AtomicLong(0),
            registry = registry,
            applier = { _, _ ->
                applied.add(1)
                // Re-entrantly binding a new owner must not appear in this dispatch.
                registry.bind(FakeOwner(), 1)
            }
        )

        assertEquals(1, applied.size)
    }
}
