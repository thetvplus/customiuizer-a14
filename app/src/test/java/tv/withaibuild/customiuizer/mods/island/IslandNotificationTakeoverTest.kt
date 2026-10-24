package tv.withaibuild.customiuizer.mods.island

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.widget.FrameLayout
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandNotificationTakeoverTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val access get() = IslandNotificationAccess(Entry::class.java, Binding::class.java)
    private fun notification(time: Long) = StatusBarNotification(context.packageName, context.packageName,
        1, "message", Process.myUid(), 0, 0, Notification.Builder(context, "chat").build(), Process.myUserHandle(), time)

    @Test fun boundClickUsesTheOriginalRowAndBecomesInvalidWhenThatRowIsRebound() {
        val port = access
        val entry = Entry(context, notification(1))
        var clicks = 0
        entry.row.setOnClickListener { clicks++ }
        val bound = port.read(entry)!!
        assertSame(entry.mSbn, bound.notification)
        assertTrue(bound.open())
        assertEquals(1, clicks)
        entry.mSbn = notification(2)
        assertFalse(bound.open())
        assertEquals(1, clicks)
    }

    @Test fun appLockSensitiveContentAndMissingClickTargetCannotBeTakenOver() {
        val entry = Entry(context, notification(1))
        assertNull(access.read(entry))
        entry.row.setOnClickListener { }
        entry.hideSensitiveByAppLock = true
        assertNull(access.read(entry))
        entry.hideSensitiveByAppLock = false; entry.mSensitive = true
        assertNull(access.read(entry))
        entry.mSensitive = false
        assertNotNull(access.read(entry))
    }

    @Test fun successfulBindCompletionRetiresOnlyItsOwnBindingWithoutMarkingANativeHeadsUp() {
        val entry = Entry(context, notification(1))
        val binding = Binding()
        val map = access.bindingMap(binding)
        map[entry.mSbn.key] = 100L
        map["another-notification"] = 200L
        access.complete(binding, entry, map, entry.mSbn.key)
        assertTrue(entry.interruption)
        assertFalse(entry.mIsAlerting)
        assertEquals(mapOf("another-notification" to 200L), map)
        assertEquals(listOf(entry), binding.`$tmp0`.mHeadsUpViewBinder.unbound)
        assertNotNull(entry.mSbn.notification) // The original notification is retained.
    }

    @Test fun removedRowsAndReplacementEntriesCannotLaunchAnOldAcceptedActivity() {
        val port = access
        val entry = Entry(context, notification(1))
        var clicks = 0
        entry.row.setOnClickListener { clicks++ }
        val bound = port.read(entry)!!
        entry.row.mRemoved = true
        assertFalse(bound.open())
        entry.row.mRemoved = false
        entry.row.mEntry = Entry(context, entry.mSbn)
        assertFalse(bound.open())
        assertEquals(0, clicks)
    }

    @Test fun aRemovedOrReboundRowCannotBeAcceptedAtTheBindBoundary() {
        val port = access
        val entry = Entry(context, notification(1))
        entry.row.setOnClickListener { }
        entry.row.mRemoved = true
        assertNull(port.read(entry))
        entry.row.mRemoved = false
        entry.row.mEntry = Entry(context, entry.mSbn)
        assertNull(port.read(entry))
    }

    @Test fun replacingTheEntryRowInvalidatesTheOldClickEvenWhenTheOldRowStillHasItsEntry() {
        val port = access
        val entry = Entry(context, notification(1))
        var oldClicks = 0
        entry.row.setOnClickListener { oldClicks++ }
        val old = port.read(entry)!!
        entry.row = Row(context).also { it.mEntry = entry; it.setOnClickListener { } }
        assertFalse(old.open())
        assertEquals(0, oldClicks)
        assertNotNull(port.read(entry))
    }

    @Test fun aCommittedBindIsConsumedOnceAndCleanupFailureCannotReplayNativePresentation() {
        val port = access
        val entry = Entry(context, notification(1)).apply { row.setOnClickListener { } }
        val binding = Binding()
        val map = port.bindingMap(binding)
        map[entry.mSbn.key] = 100L
        map["other"] = 200L
        binding.`$tmp0`.mHeadsUpViewBinder.reject = true
        var presented = 0
        var rolledBack = 0
        assertTrue(port.take(binding, entry, { presented++; true }, { rolledBack++ }))
        assertTrue(entry.interruption)
        assertFalse(entry.mIsAlerting)
        assertEquals(mapOf("other" to 200L), map)
        assertFalse(port.take(binding, entry, { presented++; true }, { rolledBack++ }))
        assertEquals(1, presented)
        assertEquals(0, rolledBack)
        assertEquals(listOf(entry), binding.`$tmp0`.mHeadsUpViewBinder.unbound)
    }

    @Test fun rejectionAndPreCommitExceptionsKeepTheNativeTailUntouched() {
        for (throws in listOf(false, true)) {
            val port = access
            val entry = Entry(context, notification(1)).apply { row.setOnClickListener { } }
            val binding = Binding()
            val map = port.bindingMap(binding)
            map[entry.mSbn.key] = 100L
            var rolledBack: IslandNotificationAccess.Bound? = null
            try {
                val taken = port.take(binding, entry, { if (throws) error("injected before commit") else false }, { rolledBack = it })
                assertFalse(taken)
                assertFalse(throws)
            } catch (_: IllegalStateException) { assertTrue(throws) }
            assertSame(entry.mSbn, rolledBack!!.notification)
            assertFalse(entry.interruption)
            assertEquals(mapOf(entry.mSbn.key to 100L), map)
            assertTrue(binding.`$tmp0`.mHeadsUpViewBinder.unbound.isEmpty())
        }
    }

    @Test fun aNativeMapFailureRestoresTheInterruptionBitAndBindingBeforeFallback() {
        val port = access
        val entry = Entry(context, notification(1)).apply { row.setOnClickListener { } }
        val binding = Binding()
        val values = linkedMapOf<String, Any?>(entry.mSbn.key to 100L)
        binding.`$tmp0`.mEntriesBindingUntil = object : MutableMap<String, Any?> by values {
            override fun remove(key: String): Any? { values.remove(key); error("injected during commit") }
        }
        var rolledBack = 0
        assertThrows(IllegalStateException::class.java) {
            port.take(binding, entry, { true }, { rolledBack++ })
        }
        assertFalse(entry.interruption)
        assertEquals(mapOf(entry.mSbn.key to 100L), values)
        assertEquals(1, rolledBack)
        assertTrue(binding.`$tmp0`.mHeadsUpViewBinder.unbound.isEmpty())
    }

    @Test fun reentrantRemovalUpdateRowReplacementAndNewBindingCannotCommitAnOldTakeover() {
        for (change in 0..5) {
            val port = access
            val entry = Entry(context, notification(1)).apply { row.setOnClickListener { } }
            val binding = Binding()
            val map = port.bindingMap(binding)
            val key = entry.mSbn.key
            val original = Any()
            map[key] = original
            var rolledBack: IslandNotificationAccess.Bound? = null
            assertFalse(port.take(binding, entry, {
                when (change) {
                    0 -> entry.row.mRemoved = true
                    1 -> entry.mSbn = notification(2)
                    2 -> entry.row = Row(context).also { it.mEntry = entry; it.setOnClickListener { } }
                    3 -> map[key] = Any()
                    4 -> map.remove(key)
                    5 -> entry.mSensitive = true
                }
                true
            }, { rolledBack = it }))
            assertNotNull(rolledBack)
            assertFalse(entry.interruption)
            assertTrue(binding.`$tmp0`.mHeadsUpViewBinder.unbound.isEmpty())
            if (change == 3) assertNotSame(original, map[key])
        }
    }

    class Row(context: Context) : FrameLayout(context) {
        @JvmField var mEntry: Entry? = null
        @JvmField var mRemoved = false
    }
    class Icons(context: Context) { @JvmField val mStatusBarIcon = ImageView(context) }
    class Entry(context: Context, notification: StatusBarNotification) {
        @JvmField var mSbn = notification
        @JvmField val mRanking = Ranking()
        @JvmField var row = Row(context).also { it.mEntry = this }
        @JvmField val mIcons = Icons(context)
        @JvmField var mSensitive = false
        @JvmField var hideSensitiveByAppLock = false
        @JvmField var interruption = false
        @JvmField var mIsAlerting = false
    }
    class Binder {
        val unbound = mutableListOf<Entry>()
        var reject = false
        fun unbindHeadsUpView(entry: Entry) { unbound.add(entry); check(!reject) }
    }
    class Coordinator {
        @JvmField var mEntriesBindingUntil: MutableMap<String, Any?> = android.util.ArrayMap()
        @JvmField val mHeadsUpViewBinder = Binder()
    }
    class Binding {
        @JvmField val `$tmp0` = Coordinator()
        @Suppress("UNUSED_PARAMETER") fun onBindFinished(entry: Entry) = Unit
    }
}
