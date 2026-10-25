package tv.withaibuild.customiuizer.mods.island

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandLiveSignalTest {
    @Test fun unauthenticatedSenderCannotReachPayloadAndClosedReceiverIsIdempotent() {
        val context = RuntimeEnvironment.getApplication()
        var reads = 0
        var changes = 0
        val message = object : Intent(IslandLiveSignal.STATE) {
            override fun getIntExtra(name: String?, defaultValue: Int): Int { reads++; error("untrusted payload accessed") }
        }
        val subscriber = IslandLiveSignal.Subscriber(context, android.os.Handler(android.os.Looper.getMainLooper()),
            mapOf(pkg to 10300)) { _, _ -> changes++ }
        val receiver = org.robolectric.util.ReflectionHelpers.getField<android.content.BroadcastReceiver>(subscriber, "receiver")
        try {
            receiver.onReceive(context, message)
            assertEquals(0, reads); assertEquals(0, changes)
        } finally { subscriber.close(); subscriber.close() }
    }
    @Test fun missingHostPhoneGrantNeverRequestsTelecomOrStartsAnActivity() {
        val context = object : android.content.ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun checkSelfPermission(permission: String) = android.content.pm.PackageManager.PERMISSION_DENIED
            override fun getSystemService(name: String): Any? = error("host grant must be checked first")
            override fun startActivity(intent: Intent) { error("no implicit dialing fallback") }
        }
        assertNull(IslandLiveCalls.state(context))
        IslandLiveCalls.open(context)
    }
    private val pkg = IslandLiveKind.CLOCK_PACKAGE
    private val now get() = SystemClock.uptimeMillis()
    private val elapsed get() = SystemClock.elapsedRealtime()
    private fun message() = IslandLiveSignal.encode(pkg, IslandLiveKind.TIMER,
        IslandLiveActivity(IslandLiveKind.TIMER, pkg, 10300, IslandLiveClock(elapsed + 10000, true), session = 3), 10, 20)
    private fun decode(intent: Intent) = IslandLiveSignal.decode(intent, pkg, 10300, now, elapsed)
    @Test fun onlyExactProducerKindFreshMonotonicPacketsAreAccepted() {
        val parsed = decode(message())!!
        assertEquals(3L, parsed.activity!!.session)
        assertTrue(parsed.acceptedBy(20, 9)); assertFalse(parsed.acceptedBy(21, 9)); assertFalse(parsed.acceptedBy(20, 10))
        assertTrue(parsed.copy(nonce = 0).acceptedBy(21, 9))
        assertNull(decode(message().setAction("different")))
        assertNull(decode(message().putExtra("source", "untrusted")))
        assertNull(decode(message().putExtra("kind", "recorder")))
        assertNull(decode(message().putExtra("sequence", 0L)))
        assertNull(decode(message().putExtra("nonce", -1L)))
        assertNull(IslandLiveSignal.decode(message(), pkg, 10300, now + 5001, elapsed))
        assertNull(IslandLiveSignal.decode(message(), "other", 10300, now, elapsed))
        assertNull(decode(message().putExtra("session", 0L)))
    }
    @Test fun malformedClockAndForeignOrNonActivityIntentsAreRejectedBeforePresentation() {
        assertNull(decode(message().putExtra("base", Long.MIN_VALUE)))
        assertNull(decode(message().putExtra("base", elapsed + IslandLiveTime.MAX_SPAN + 1)))
        assertNull(decode(message().putExtra("paused", -1L)))
        val context = RuntimeEnvironment.getApplication()
        val foreign = PendingIntent.getActivity(context, 1, Intent("foreign"), PendingIntent.FLAG_IMMUTABLE)
        assertNull(decode(message().putExtra("open", foreign)))
        val broadcast = PendingIntent.getBroadcast(context, 2, Intent("foreign"), PendingIntent.FLAG_IMMUTABLE)
        assertNull(decode(message().putExtra("open", broadcast)))
        val ended = IslandLiveSignal.encode(pkg, IslandLiveKind.TIMER, null, 11)
        assertNull(decode(ended)!!.activity)
        assertFalse(IslandLiveSignal.owns(IslandLiveKind.RECORDER_PACKAGE, IslandLiveKind.STOPWATCH))
        assertTrue(IslandLiveSignal.owns(IslandLiveKind.RECORDER_PACKAGE, IslandLiveKind.RECORDER))
    }
}
