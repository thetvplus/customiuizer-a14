package tv.withaibuild.customiuizer.mods.island

import android.os.IBinder
import android.os.RemoteException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandLiveOwnersTest {
    private class Remote {
        var alive = true
        var failLink = false
        var linked = 0
        var unlinked = 0
        var death: IBinder.DeathRecipient? = null
        val binder: IBinder = Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { proxy, method, args ->
            when (method.name) {
                "isBinderAlive" -> alive
                "linkToDeath" -> { if (failLink) throw RemoteException("gone"); linked++; death = args!![0] as IBinder.DeathRecipient; null }
                "unlinkToDeath" -> { unlinked++; true }
                "equals" -> proxy === args!![0]
                "hashCode" -> java.lang.System.identityHashCode(proxy)
                "toString" -> "SourceLifetime"
                else -> error("Unexpected binder operation: ${method.name}")
            }
        } as IBinder
        fun die() { alive = false; death!!.binderDied() }
    }
    @Test fun lateDeathCannotClearReplacementAndOnlyTheDeadPackageEnds() {
        val queued = ArrayDeque<() -> Unit>()
        val ended = ArrayList<String>()
        val owners = IslandLiveOwners({ queued.addLast(it) }, ended::add)
        val clock = IslandLiveKind.CLOCK_PACKAGE
        val recorder = IslandLiveKind.RECORDER_PACKAGE
        val old = Remote(); val replacement = Remote(); val recording = Remote()
        assertTrue(owners.accept(clock, old.binder)); assertTrue(owners.accept(recorder, recording.binder))
        assertTrue(owners.accept(clock, old.binder)); assertEquals(1, old.linked)
        old.die()
        assertTrue(owners.accept(clock, replacement.binder)); assertEquals(1, old.unlinked)
        queued.removeFirst().invoke(); assertTrue(ended.isEmpty())
        assertFalse(owners.accept(clock, old.binder)); assertTrue(ended.isEmpty())
        replacement.die(); queued.removeFirst().invoke()
        assertEquals(listOf(clock), ended)
        recording.die(); owners.close(); owners.close()
        queued.removeFirst().invoke(); assertEquals(listOf(clock), ended)
        assertEquals(1, recording.unlinked)
        assertFalse(owners.accept(clock, Remote().binder))
    }
    @Test fun failedDeathRegistrationFallsBackWithoutRetainingAnUnwatchableSource() {
        val ended = ArrayList<String>()
        val owners = IslandLiveOwners({ it() }, ended::add)
        val source = Remote().apply { failLink = true }
        assertFalse(owners.accept(IslandLiveKind.CLOCK_PACKAGE, source.binder))
        assertEquals(listOf(IslandLiveKind.CLOCK_PACKAGE), ended)
        assertFalse(owners.accept("arbitrary.app", Remote().binder))
        owners.close()
        assertEquals(0, source.unlinked)
    }
}
