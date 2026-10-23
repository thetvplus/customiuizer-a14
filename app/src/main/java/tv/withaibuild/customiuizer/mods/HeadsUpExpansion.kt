package tv.withaibuild.customiuizer.mods

import android.service.notification.StatusBarNotification
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/** Per-row, one-shot work. The additional-instance-field value cannot strongly retain its key. */
internal class HeadsUpExpansion(rowClass: Class<*>, private val selected: (String) -> Boolean) {
    private val entry = XposedHelpers.findField(rowClass, "mEntry")
    private val sbn = XposedHelpers.findField(entry.type, "mSbn")
    private val onKeyguard = XposedHelpers.findField(rowClass, "mOnKeyguard")
    private val headsUp = XposedHelpers.findField(rowClass, "mIsHeadsUp")
    private val removed = XposedHelpers.findField(rowClass, "mRemoved")
    private val userLocked = XposedHelpers.findField(rowClass, "mUserLocked")
    private val content = XposedHelpers.findField(rowClass, "mPrivateLayout")
    private val remoteInput = XposedHelpers.findField(content.type, "mRemoteInputVisible")
    private val expanded = XposedHelpers.findMethodExact(rowClass, "isExpanded", Boolean::class.javaPrimitiveType!!)
    private val click = XposedHelpers.findField(rowClass, "mExpandClickListener")

    fun cancel(row: View) {
        (XposedHelpers.removeAdditionalInstanceField(row, KEY) as? Task)?.release(row)
    }

    fun update(row: View) {
        val currentEntry = entry.get(row)
        val notification = currentEntry?.let(sbn::get) as? StatusBarNotification
        if (currentEntry == null || notification == null || !eligible(row, notification)) {
            cancel(row)
            return
        }
        val previous = XposedHelpers.getAdditionalInstanceField(row, KEY) as? Task
        if (previous?.matches(currentEntry, notification) == true) return
        cancel(row)
        val task = Task(row, currentEntry, notification)
        XposedHelpers.setAdditionalInstanceField(row, KEY, task)
        row.addOnAttachStateChangeListener(task)
        if (!row.postDelayed(task, 60)) cancel(row)
    }

    private fun eligible(row: View, notification: StatusBarNotification): Boolean =
        headsUp.getBoolean(row) && !onKeyguard.getBoolean(row) && !removed.getBoolean(row) &&
            !userLocked.getBoolean(row) && !remoteInput.getBoolean(content.get(row)) &&
            expanded.invoke(row, false) != true && selected(notification.packageName)

    private inner class Task(row: View, currentEntry: Any, notification: StatusBarNotification) :
        Runnable, View.OnAttachStateChangeListener {
        private val rowRef = WeakReference(row)
        private val entryRef = WeakReference(currentEntry)
        private val notificationRef = WeakReference(notification)

        fun matches(current: Any, notification: StatusBarNotification) =
            entryRef.get() === current && notificationRef.get() === notification

        fun release(row: View) {
            row.removeCallbacks(this)
            row.removeOnAttachStateChangeListener(this)
        }

        override fun run() = ModuleHelper.guarded {
            val row = rowRef.get() ?: return@guarded
            if (XposedHelpers.getAdditionalInstanceField(row, KEY) !== this) return@guarded
            cancel(row) // Clear ownership before invoking a possibly reentrant native listener.
            val currentEntry = entry.get(row) ?: return@guarded
            val notification = sbn.get(currentEntry) as? StatusBarNotification ?: return@guarded
            if (row.isAttachedToWindow && matches(currentEntry, notification) && eligible(row, notification)) {
                (click.get(row) as? View.OnClickListener)?.onClick(row)
            }
        }

        override fun onViewDetachedFromWindow(v: View) = ModuleHelper.guarded { cancel(v) }
        override fun onViewAttachedToWindow(v: View) = Unit
    }

    private companion object { const val KEY = "a14HeadsUpExpansion" }
}
