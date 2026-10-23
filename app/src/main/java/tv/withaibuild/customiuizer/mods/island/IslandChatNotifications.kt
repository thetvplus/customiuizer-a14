package tv.withaibuild.customiuizer.mods.island

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import kotlin.math.max

/** Uses only notifications already accepted by SystemUI. No app data or message database access. */
internal object IslandChatNotifications {
    fun prepare(context: Context, sbn: StatusBarNotification, importance: Int,
        interruptionAllowed: Boolean, suppressed: Int,
        nativeIcon: Drawable? = null): IslandToastContent? {
        val n = sbn.notification
        if (sbn.packageName == "com.android.systemui" || sbn.user != Process.myUserHandle() ||
            importance < NotificationManager.IMPORTANCE_DEFAULT || !interruptionAllowed ||
            suppressed and (NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR) != 0 ||
            n.visibility == Notification.VISIBILITY_SECRET || n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || sbn.isOngoing ||
            n.fullScreenIntent != null || n.contentView != null || n.bigContentView != null || n.headsUpContentView != null) return null
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val messageNotification = style != null || n.category == Notification.CATEGORY_MESSAGE
        val pictureNotification = n.extras.getString(Notification.EXTRA_TEMPLATE) == Notification.BigPictureStyle::class.java.name
        // Generic standard styles carry content semantics. An arbitrary app branding icon
        // alone does not turn every notification into a new activity.
        if (!messageNotification && !pictureNotification) return null
        val message = style?.messages?.lastOrNull()
        val person = message?.person
        val title = title(person?.name) ?: title(n.extras.getCharSequence(Notification.EXTRA_TITLE)) ?: return null
        val detail = detail(message?.text ?: n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            ?.takeUnless { it == title }
        val avatar = person?.icon?.let { icon -> ModuleHelper.guarded<Drawable?>(null) {
            if (icon.type == IconCompat.TYPE_BITMAP || icon.type == IconCompat.TYPE_ADAPTIVE_BITMAP)
                icon.loadDrawable(context) else null
        } }
        val source = avatar ?: ModuleHelper.guarded<Drawable?>(null) {
            val picture = if (pictureNotification) {
                n.extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)?.let { BitmapDrawable(context.resources, it) }
            } else null
            picture
        }
        // Native status icons retain their View callback. Copy their pixels too, never
        // transfer a drawable owned by SystemUI to the island ImageView.
        val candidate = source ?: nativeIcon?.takeIf { messageNotification }
        val icon = candidate?.let { drawable -> ModuleHelper.guarded<Drawable?>(null) { thumbnail(context, drawable) } }
        if (!messageNotification && icon == null) return null
        return IslandToastContent(title, sbn.packageName, icon, notificationKey = sbn.key,
            detail = detail, avatar = avatar != null && icon != null)
    }

    private fun title(value: CharSequence?): String? = value?.take(80)?.toString()
        ?.takeUnless { it.isBlank() || it.any(Char::isISOControl) }

    private fun detail(value: CharSequence?): String? {
        if (value == null) return null
        val text = buildString {
            for (index in 0 until minOf(160, value.length)) {
                val char = value[index]
                append(if (char == '\n' || char == '\r' || char == '\t') ' ' else char)
            }
        }.trim()
        return text.takeUnless { it.isBlank() || it.any(Char::isISOControl) }
    }

    /** One 128px thumbnail per accepted activity; preserve aspect ratio and provider bounds. */
    private fun thumbnail(context: Context, drawable: Drawable): Drawable {
        val image = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = max(128f / width, 128f / height)
        val bounds = Rect(drawable.bounds)
        canvas.translate((128 - width * scale) / 2, (128 - height * scale) / 2)
        canvas.scale(scale, scale)
        try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        } finally { drawable.bounds = bounds }
        return BitmapDrawable(context.resources, image)
    }
}
