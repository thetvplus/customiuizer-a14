package tv.withaibuild.customiuizer.mods.island

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandNotificationPrivacyTest {
    class Entry
    class Users {
        @JvmField var mCurrentUserId = 0
        val public = HashSet<Int>()
        fun isLockscreenPublicMode(user: Int) = user in public
    }
    class Policy {
        var hidden = false
        fun showSensitiveByAppLock(entry: Entry) = hidden
    }
    class Coordinator { fun onBeforeRenderList(entries: List<Any>) {} }
    private fun notification(uid: Int = Process.myUid()): StatusBarNotification {
        val context = RuntimeEnvironment.getApplication()
        return StatusBarNotification(context.packageName, context.packageName, 1, "message", uid, 0, 0,
            Notification.Builder(context, "chat").build(), Process.myUserHandle(), 1)
    }

    @Test fun nativePublicModeAppLockAndUserChangesAreReadWithoutCachingTheirDecisions() {
        val users = Users(); val policy = Policy()
        var resolutions = 0
        val reader = IslandNativeNotificationPrivacy(Users::class.java, Policy::class.java,
            Entry::class.java, Coordinator::class.java, { resolutions++; users }, { resolutions++; policy })
        assertEquals(0, resolutions)
        val entry = Entry()
        assertFalse(reader.hidden(entry, notification()))
        assertEquals(2, resolutions)
        policy.hidden = true; assertTrue(reader.hidden(entry, notification()))
        policy.hidden = false; users.public.add(0); assertTrue(reader.hidden(entry, notification()))
        users.public.clear(); users.public.add(10)
        assertTrue(reader.hidden(entry, notification(1012345)))
        users.public.clear(); users.mCurrentUserId = 10
        assertFalse(reader.hidden(entry, notification(1012345)))
        users.mCurrentUserId = -1; assertTrue(reader.hidden(entry, notification()))
        assertEquals("Only receivers are cached, never a user's privacy decision", 2, resolutions)
    }
}
