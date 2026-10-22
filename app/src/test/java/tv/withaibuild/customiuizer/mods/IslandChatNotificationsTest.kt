package tv.withaibuild.customiuizer.mods

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandChatNotificationsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun notification() = NotificationCompat.Builder(context, "chat")
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
            .addMessage("Message", 1L, Person.Builder().setName("Alice")
                .setIcon(IconCompat.createWithBitmap(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(Color.CYAN) })).build()))
        .build()
    private fun posted(pkg: String = "com.tencent.mm", n: Notification = notification(),
        user: UserHandle = Process.myUserHandle()) = StatusBarNotification(pkg, pkg, 1, "chat", 10000, 0, 0, n, user, 1L)
    private fun prepare(sbn: StatusBarNotification, importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        allowed: Boolean = true, suppressed: Int = 0) =
        IslandChatNotifications.prepare(context, sbn, importance, allowed, suppressed)

    @Test fun customAndFullScreenNotificationsStayNativeAndNativeBindDoesNotLoadUriArtwork() {
        val custom = notification().apply { headsUpContentView = android.widget.RemoteViews(context.packageName, 1) }
        assertNull(prepare(posted(n = custom)))
        val fullScreen = notification().apply {
            fullScreenIntent = PendingIntent.getActivity(context, 1, Intent("island.fullscreen"), PendingIntent.FLAG_IMMUTABLE)
        }
        assertNull(prepare(posted(n = fullScreen)))
        val message = NotificationCompat.Builder(context, "chat").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
                .addMessage("Message", 1L, Person.Builder().setName("Alice")
                    .setIcon(IconCompat.createWithContentUri("content://unavailable/avatar")).build())).build()
        val fallback = android.graphics.drawable.ColorDrawable(Color.CYAN)
        val nativeView = android.widget.ImageView(context).apply { setImageDrawable(fallback) }
        fallback.setBounds(2, 3, 18, 19)
        val content = IslandChatNotifications.prepare(context, posted(n = message), NotificationManager.IMPORTANCE_HIGH,
            true, 0, nativeIcon = fallback)!!
        assertEquals("Alice", content.text)
        assertNotSame(fallback, content.icon)
        val bitmap = (content.icon as BitmapDrawable).bitmap
        assertEquals(128, bitmap.width); assertEquals(128, bitmap.height)
        assertEquals(Color.CYAN, bitmap.getPixel(64, 64))
        android.widget.ImageView(context).setImageDrawable(content.icon)
        assertSame(nativeView, fallback.callback)
        assertEquals(android.graphics.Rect(2, 3, 18, 19), fallback.bounds)
        assertFalse(content.avatar)
    }

    @Test fun standardMessagesFromAnyAppUseTheSenderDetailAndBoundedAvatar() {
        for (pkg in listOf("com.tencent.mm", "org.telegram.messenger", "com.tencent.mobileqq", "other.app")) {
            val sbn = posted(pkg)
            val content = prepare(sbn)!!
            assertEquals("Alice", content.text)
            assertEquals("Message", content.detail)
            assertTrue(content.avatar)
            assertEquals(sbn.key, content.notificationKey)
            val bitmap = (content.icon as BitmapDrawable).bitmap
            assertEquals(128, bitmap.width); assertEquals(128, bitmap.height)
            assertEquals(Color.CYAN, bitmap.getPixel(64, 64))
        }
    }

    @Test fun foreignUserPrivateSilentGroupedAndOngoingNotificationsStayNative() {
        assertNull(prepare(posted("com.android.systemui")))
        assertNull(prepare(posted(user = UserHandle.getUserHandleForUid(1010000))))
        assertNull(prepare(posted(), importance = NotificationManager.IMPORTANCE_LOW))
        assertNull(prepare(posted(), allowed = false))
        assertNull(prepare(posted(), suppressed = NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK))
        for (n in listOf(
            notification().apply { visibility = Notification.VISIBILITY_SECRET },
            notification().apply { flags = flags or Notification.FLAG_GROUP_SUMMARY },
            notification().apply { flags = flags or Notification.FLAG_ONGOING_EVENT })) assertNull(prepare(posted(n = n)))
    }

    @Test fun publicPictureContentUsesItsCoverWithoutStretchingOrBecomingAnAvatar() {
        val picture = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        Canvas(picture).apply {
            // Keep a guard band around the 50..150 center crop so native filtering
            // at its outermost pixel cannot sample a neighboring test color.
            drawRect(0f, 0f, 40f, 100f, Paint().apply { color = Color.RED })
            drawRect(160f, 0f, 200f, 100f, Paint().apply { color = Color.BLUE })
        }
        val n = NotificationCompat.Builder(context, "content").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New cover").setContentText("Public detail")
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture)).build()
        val content = prepare(posted("other.app", n))!!
        assertEquals("New cover", content.text)
        assertEquals("Public detail", content.detail)
        assertFalse(content.avatar)
        val thumbnail = (content.icon as BitmapDrawable).bitmap
        assertEquals(128, thumbnail.width); assertEquals(128, thumbnail.height)
        assertEquals(Color.CYAN, thumbnail.getPixel(0, 64))
        assertEquals(Color.CYAN, thumbnail.getPixel(127, 64))
        assertEquals(200, picture.width); assertFalse(picture.isRecycled)
    }

    @Test fun ordinaryBrandingOnlyNotificationsAndEmptyPictureStylesStayNative() {
        val icon = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val ordinary = NotificationCompat.Builder(context, "ordinary")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setLargeIcon(icon)
            .setContentTitle("Promotion").setContentText("Branding alone").build()
        assertNull(prepare(posted("other.app", ordinary)))
        val emptyPicture = NotificationCompat.Builder(context, "ordinary")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("No cover")
            .setStyle(NotificationCompat.BigPictureStyle()).build()
        assertNull(prepare(posted("other.app", emptyPicture)))
        assertNull(IslandChatNotifications.prepare(context, posted("other.app", emptyPicture),
            NotificationManager.IMPORTANCE_HIGH, true, 0, android.graphics.drawable.ColorDrawable(Color.CYAN)))
    }

    @Test fun fallbackMessageFieldsStayBoundedAndNeverBypassTheNativeClick() {
        val intent = PendingIntent.getActivity(context, 0, Intent("island.test"), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, "message").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setCategory(Notification.CATEGORY_MESSAGE).setContentTitle("T".repeat(100))
            .setContentText("First\nSecond\t" + "D".repeat(200)).setContentIntent(intent).build()
        val own = prepare(posted(context.packageName, n))!!
        assertEquals(80, own.text.length)
        assertEquals(160, own.detail!!.length)
        assertTrue(own.detail.startsWith("First Second "))
        assertFalse(own.avatar)
        assertNull(own.launch)
        assertNull(prepare(posted("other.app", n))!!.launch)
        n.extras.putCharSequence(Notification.EXTRA_TITLE, "Bad\u0001title")
        assertNull(prepare(posted("other.app", n)))
    }
}
