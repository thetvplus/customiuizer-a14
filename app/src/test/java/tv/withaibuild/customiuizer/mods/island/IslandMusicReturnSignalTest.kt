package tv.withaibuild.customiuizer.mods.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandMusicReturnSignalTest {
    private val frame = IslandMusicReturnSignal.Frame("chosen.music", UserHandle.getUserHandleForUid(1000),
        280f, 15f, 800f, 125f, 1080, 2400)

    @Test fun explicitPacketsRetainOnlyTheUserSourceAndValidatedGeometryForFiveSeconds() {
        val message = IslandMusicReturnSignal.encode(frame, 1000, 12)
        assertEquals(IslandMusicReturnSignal.LAUNCHER, message.`package`)
        val decoded = IslandMusicReturnSignal.decode(message, 1010)!!
        assertEquals(frame, decoded.frame)
        assertTrue(decoded.fresh(6000)); assertFalse(decoded.fresh(6001))
        assertNull(IslandMusicReturnSignal.decode(message, 999))
        assertNull(IslandMusicReturnSignal.decode(message, 6001))
        assertNull(IslandMusicReturnSignal.decode(Intent(message).setAction("other"), 1000))
        assertNull(IslandMusicReturnSignal.decode(Intent(message).apply { removeExtra("user") }, 1000))
        assertFalse(message.hasExtra("title")); assertFalse(message.hasExtra("artwork")); assertFalse(message.hasExtra("audio"))
        assertNull(IslandMusicReturnSignal.decode(IslandMusicReturnSignal.encode(null, 1001, 13), 1001)!!.frame)
    }

    @Test fun malformedOffscreenAndLandscapeCoordinatesCannotReachTheLauncher() {
        for (bad in listOf(frame.copy(left = Float.NaN), frame.copy(top = -1f), frame.copy(right = 1081f),
            frame.copy(bottom = frame.top), frame.copy(screenWidth = 2500), frame.copy(packageName = ""))) {
            assertFalse(bad.valid())
            assertNull(IslandMusicReturnSignal.decode(IslandMusicReturnSignal.encode(bad, 1000, 1), 1000))
        }
    }

    @Test fun publisherCoalescesWorkAndUnregistersBeforeItsOwnerCanBeReused() {
        val tasks = TestScope()
        var registrations = 0; var removals = 0
        val sent = mutableListOf<Intent>()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?, flags: Int): Intent? {
                registrations++; return null
            }
            override fun unregisterReceiver(receiver: BroadcastReceiver) { removals++ }
            override fun sendBroadcast(intent: Intent, permission: String?, options: android.os.Bundle?) { sent.add(intent) }
        }
        val publisher = IslandMusicReturnSignal.Publisher(context, Handler(Looper.getMainLooper()), tasks,
            kotlinx.coroutines.test.StandardTestDispatcher(tasks.testScheduler)) { frame }
        publisher.publish(); publisher.publish(force = true); publisher.publish(force = true)
        tasks.testScheduler.runCurrent()
        assertEquals(1, registrations); assertEquals(1, sent.size)
        publisher.close(); publisher.close()
        assertEquals(1, removals); assertEquals(2, sent.size)
        assertFalse(sent.last().getBooleanExtra("active", true))
        publisher.publish(force = true); tasks.testScheduler.runCurrent()
        assertEquals(2, sent.size)
    }
}
