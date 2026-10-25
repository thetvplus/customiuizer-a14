package tv.withaibuild.customiuizer.mods.island

import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.ImageView
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/** Frozen CN/TW HyperOS 1 contract, resolved only when notification integration is enabled. */
internal class IslandNotificationAccess internal constructor(entryClass: Class<*>, bindingClass: Class<*>,
    private val privacy: IslandNotificationPrivacy? = null) {
    private val diagnostics = IslandDiagnostics()
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", loader),
        XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.coordinator.HeadsUpCoordinator\$bindForAsyncHeadsUp\$1", loader),
        IslandNativeNotificationPrivacy(loader))
    val onPrivacyUpdated get() = (privacy as? IslandNativeNotificationPrivacy)?.updated
    private val coordinator = XposedHelpers.findField(bindingClass, "\$tmp0")
    private val bindings = XposedHelpers.findField(coordinator.type, "mEntriesBindingUntil")
    private val binder = XposedHelpers.findField(coordinator.type, "mHeadsUpViewBinder")
    private val unbind = binder.type.getDeclaredMethod("unbindHeadsUpView", entryClass).apply { isAccessible = true }
    private val sbn = XposedHelpers.findField(entryClass, "mSbn")
    private val ranking = XposedHelpers.findField(entryClass, "mRanking")
    private val row = XposedHelpers.findField(entryClass, "row")
    private val rowEntry = XposedHelpers.findField(row.type, "mEntry")
    private val rowRemoved = XposedHelpers.findField(row.type, "mRemoved")
    private val sensitive = XposedHelpers.findField(entryClass, "mSensitive")
    private val appLocked = XposedHelpers.findField(entryClass, "hideSensitiveByAppLock")
    private val interrupted = XposedHelpers.findField(entryClass, "interruption")
    private val icons = XposedHelpers.findField(entryClass, "mIcons")
    private val statusIcon = XposedHelpers.findField(icons.type, "mStatusBarIcon")
    val onBindFinished = bindingClass.getDeclaredMethod("onBindFinished", entryClass)

    data class Bound(val notification: StatusBarNotification, val ranking: Ranking,
        val icon: Drawable?, val open: () -> Boolean, val hidden: (() -> Boolean)? = null)

    private fun hidden(entry: Any, notification: StatusBarNotification): Boolean = try {
        privacy?.hidden(entry, notification) ?: (sensitive.getBoolean(entry) || appLocked.getBoolean(entry))
    } catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        diagnostics.report("notification-policy-unavailable")
        true
    }

    fun read(entry: Any): Bound? {
        val notification = sbn.get(entry) as? StatusBarNotification ?: return null
        if (hidden(entry, notification)) {
            diagnostics.report("notification-private"); return null
        }
        val nativeRow = row.get(entry) as? View ?: return null
        if (rowRemoved.getBoolean(nativeRow) || rowEntry.get(nativeRow) !== entry) {
            diagnostics.report("notification-stale-row"); return null
        }
        if (!nativeRow.hasOnClickListeners()) { diagnostics.report("notification-click-not-ready"); return null }
        val rank = ranking.get(entry) as? Ranking ?: return null
        val icon = icons.get(entry)?.let { statusIcon.get(it) as? ImageView }?.drawable
        val weakRow = WeakReference(nativeRow)
        val weakEntry = WeakReference(entry)
        // No strong row/controller escapes the call. Rebinding/removal invalidates the click.
        val open = {
            val current = weakRow.get()
            val currentEntry = current?.let(rowEntry::get)
            if (current == null || currentEntry == null || currentEntry !== weakEntry.get() ||
                row.get(currentEntry) !== current || rowRemoved.getBoolean(current) || sbn.get(currentEntry) !== notification) false
            else current.performClick()
        }
        val isHidden = {
            val current = weakEntry.get()
            current == null || sbn.get(current) !== notification || row.get(current) !== weakRow.get() ||
                hidden(current, notification)
        }
        return Bound(notification, rank, icon, open, isHidden)
    }

    @Suppress("UNCHECKED_CAST")
    fun bindingMap(callback: Any): MutableMap<String, Any?> =
        bindings.get(coordinator.get(callback)) as MutableMap<String, Any?>

    /** Prepare the shell first; only this still-current bind may commit the native tail. */
    fun take(callback: Any, entry: Any, present: (Bound) -> Boolean, rollback: (Bound) -> Unit): Boolean {
        diagnostics.report("notification-native-bind")
        val bound = read(entry) ?: return false
        val key = bound.notification.key
        val map = bindingMap(callback)
        if (!map.containsKey(key)) { diagnostics.report("notification-bind-not-current"); return false }
        val binding = map[key]
        val nativeRow = row.get(entry)
        var committed = false
        try {
            if (!present(bound)) { diagnostics.report("notification-presentation-declined"); return false }
            // Window insertion can synchronously re-enter native code. An update,
            // removal, Row replacement or newer bind invalidates this transaction.
            if (bindingMap(callback) !== map || !map.containsKey(key) || map[key] !== binding ||
                sbn.get(entry) !== bound.notification || row.get(entry) !== nativeRow ||
                rowEntry.get(nativeRow) !== entry || rowRemoved.getBoolean(nativeRow) ||
                hidden(entry, bound.notification)) return false
            complete(callback, entry, map, key)
            committed = true
            diagnostics.report("notification-taken")
            return true
        } finally {
            if (!committed) rollback(bound)
        }
    }

    /** The skipped presentation tail owns only this bind key and the native interruption bit. */
    fun complete(callback: Any, entry: Any, map: MutableMap<String, Any?>, key: String) {
        val nativeBinder = binder.get(coordinator.get(callback))
        val wasInterrupted = interrupted.getBoolean(entry)
        val binding = map[key]
        interrupted.setBoolean(entry, true)
        try { map.remove(key) }
        catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            interrupted.setBoolean(entry, wasInterrupted)
            if (!map.containsKey(key)) map[key] = binding
            throw error
        }
        // The binder removes its in-flight callback before calling onBindFinished.
        // Reuse native unbinding to free only the unused heads-up content (flag 4).
        // This is after commit: cleanup failure must never replay the native alert.
        ModuleHelper.guarded { unbind.invoke(nativeBinder, entry) }
    }
}
