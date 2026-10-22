package tv.withaibuild.customiuizer.mods

import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.ImageView
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/** Frozen CN/TW HyperOS 1 contract, resolved only when notification integration is enabled. */
internal class IslandNotificationAccess internal constructor(entryClass: Class<*>, bindingClass: Class<*>) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", loader),
        XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.coordinator.HeadsUpCoordinator\$bindForAsyncHeadsUp\$1", loader))
    private val coordinator = XposedHelpers.findField(bindingClass, "\$tmp0")
    private val bindings = XposedHelpers.findField(coordinator.type, "mEntriesBindingUntil")
    private val binder = XposedHelpers.findField(coordinator.type, "mHeadsUpViewBinder")
    private val unbind = binder.type.getDeclaredMethod("unbindHeadsUpView", entryClass).apply { isAccessible = true }
    private val sbn = XposedHelpers.findField(entryClass, "mSbn")
    private val ranking = XposedHelpers.findField(entryClass, "mRanking")
    private val row = XposedHelpers.findField(entryClass, "row")
    private val rowEntry = XposedHelpers.findField(row.type, "mEntry")
    private val sensitive = XposedHelpers.findField(entryClass, "mSensitive")
    private val appLocked = XposedHelpers.findField(entryClass, "hideSensitiveByAppLock")
    private val interrupted = XposedHelpers.findField(entryClass, "interruption")
    private val icons = XposedHelpers.findField(entryClass, "mIcons")
    private val statusIcon = XposedHelpers.findField(icons.type, "mStatusBarIcon")
    val onBindFinished = bindingClass.getDeclaredMethod("onBindFinished", entryClass)

    data class Bound(val notification: StatusBarNotification, val ranking: Ranking,
        val icon: Drawable?, val open: () -> Boolean)

    fun read(entry: Any): Bound? {
        if (sensitive.getBoolean(entry) || appLocked.getBoolean(entry)) return null
        val notification = sbn.get(entry) as? StatusBarNotification ?: return null
        val nativeRow = row.get(entry) as? View ?: return null
        if (!nativeRow.hasOnClickListeners()) return null
        val rank = ranking.get(entry) as? Ranking ?: return null
        val icon = icons.get(entry)?.let { statusIcon.get(it) as? ImageView }?.drawable
        val weakRow = WeakReference(nativeRow)
        // No strong row/controller escapes the call. Rebinding/removal invalidates the click.
        val open = {
            val current = weakRow.get()
            val currentEntry = current?.let(rowEntry::get)
            if (current == null || currentEntry == null || sbn.get(currentEntry) !== notification) false
            else current.performClick()
        }
        return Bound(notification, rank, icon, open)
    }

    @Suppress("UNCHECKED_CAST")
    fun bindingMap(callback: Any): MutableMap<String, Any?> =
        bindings.get(coordinator.get(callback)) as MutableMap<String, Any?>

    /** The skipped presentation tail owns only this bind key and the native interruption bit. */
    fun complete(callback: Any, entry: Any, map: MutableMap<String, Any?>, key: String) {
        interrupted.setBoolean(entry, true)
        map.remove(key)
        // The binder removes its in-flight callback before calling onBindFinished.
        // Reuse native unbinding to free only the unused heads-up content (flag 4).
        ModuleHelper.guarded { unbind.invoke(binder.get(coordinator.get(callback)), entry) }
    }
}
