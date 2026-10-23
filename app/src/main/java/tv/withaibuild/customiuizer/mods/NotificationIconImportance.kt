package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** A temporary presentation input; never replace the controller's canonical notification list. */
internal class NotificationIconImportance(controller: Class<*>, listEntry: Class<*>, entry: Class<*>) {
    private val entries = XposedHelpers.findField(controller, "mNotificationEntries")
    private val representative = XposedHelpers.findMethodExact(listEntry, "getRepresentativeEntry", *emptyArray<Any>())
    private val importance = XposedHelpers.findMethodExact(entry, "getImportance", *emptyArray<Any>())

    fun intercept(chain: XposedInterface.Chain): Any? {
        val owner = chain.thisObject ?: return chain.proceed()
        val original = ModuleHelper.guarded<List<*>?>(null) { entries.get(owner) as? List<*> } ?: return chain.proceed()
        val filtered = ModuleHelper.guarded<List<*>?>(null) {
            var replacement: ArrayList<Any?>? = null
            for (index in original.indices) {
                val item = original[index]
                val notification = item?.let(representative::invoke)
                val keep = notification == null || (importance.invoke(notification) as Int) > 1
                if (!keep && replacement == null) {
                    replacement = ArrayList(original.size)
                    for (previous in 0 until index) replacement.add(original[previous])
                }
                if (keep) replacement?.add(item)
            }
            replacement
        } ?: return chain.proceed()
        val changed = ModuleHelper.guarded(false) { entries.set(owner, filtered); true }
        if (!changed) return chain.proceed()
        return try { chain.proceed() } finally {
            ModuleHelper.guarded {
                if (entries.get(owner) === filtered) entries.set(owner, original)
            }
        }
    }
}
