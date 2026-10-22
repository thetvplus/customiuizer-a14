package tv.withaibuild.customiuizer.mods

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

    class Row(context: Context) : FrameLayout(context) { @JvmField var mEntry: Entry? = null }
    class Icons(context: Context) { @JvmField val mStatusBarIcon = ImageView(context) }
    class Entry(context: Context, notification: StatusBarNotification) {
        @JvmField var mSbn = notification
        @JvmField val mRanking = Ranking()
        @JvmField val row = Row(context).also { it.mEntry = this }
        @JvmField val mIcons = Icons(context)
        @JvmField var mSensitive = false
        @JvmField var hideSensitiveByAppLock = false
        @JvmField var interruption = false
        @JvmField var mIsAlerting = false
    }
    class Binder {
        val unbound = mutableListOf<Entry>()
        fun unbindHeadsUpView(entry: Entry) { unbound.add(entry) }
    }
    class Coordinator {
        @JvmField val mEntriesBindingUntil = android.util.ArrayMap<String, Any?>()
        @JvmField val mHeadsUpViewBinder = Binder()
    }
    class Binding {
        @JvmField val `$tmp0` = Coordinator()
        @Suppress("UNUSED_PARAMETER") fun onBindFinished(entry: Entry) = Unit
    }
}
