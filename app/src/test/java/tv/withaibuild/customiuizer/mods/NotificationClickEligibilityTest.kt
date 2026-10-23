package tv.withaibuild.customiuizer.mods

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationClickEligibilityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val user get() = Process.myUid() / 100000
    private fun activity(id: Int) = PendingIntent.getActivity(context, id, Intent("notification.$id"), PendingIntent.FLAG_IMMUTABLE)
    private fun posted(n: Notification, owner: UserHandle = Process.myUserHandle()) = StatusBarNotification(
        context.packageName, context.packageName, 1, null, Process.myUid(), 0, 0, n, owner, 1)
    private fun eligible(sbn: StatusBarNotification, intent: PendingIntent, showing: Boolean = false, over: Boolean = false) =
        NotificationClickOptionsHook.eligible(sbn, intent, user, showing, over)

    @Test fun onlyTheContentActivityFromThisNotificationIsEligible() {
        val a = activity(1); val b = activity(2)
        val n = Notification.Builder(context, "test").setContentIntent(a).build()
        assertTrue(eligible(posted(n), a))
        assertFalse(eligible(posted(n), b))
        val broadcast = PendingIntent.getBroadcast(context, 3, Intent("trampoline"), PendingIntent.FLAG_IMMUTABLE)
        n.contentIntent = broadcast
        assertFalse(eligible(posted(n), broadcast))
    }

    @Test fun lockscreenFullScreenOtherUsersAndProfilesKeepTheNativeLaunch() {
        val a = activity(1)
        val n = Notification.Builder(context, "test").setContentIntent(a).build()
        assertFalse(eligible(posted(n), a, showing = true))
        assertFalse(eligible(posted(n), a, over = true))
        assertFalse(eligible(posted(n, UserHandle.getUserHandleForUid((user + 10) * 100000)), a))
        assertFalse(NotificationClickOptionsHook.eligible(posted(n), a, -1, false, false))
        n.fullScreenIntent = a
        assertFalse(eligible(posted(n), a))
    }
}
