package tv.withaibuild.customiuizer.mods

import android.content.Intent
import android.os.Bundle
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Native Settings models only; no views, subscriptions or package mutations. */
internal object SettingsEntryHooks {
    fun removeSystemAppUpdaterHeaders(headers: MutableList<*>) {
        val iterator = headers.iterator()
        while (iterator.hasNext()) {
            val header = iterator.next() ?: continue
            val intent = XposedHelpers.getObjectField(header, "intent") as? Intent ?: continue
            if (intent.component?.packageName == "com.xiaomi.discover" &&
                intent.component?.className == "com.xiaomi.market.ui.UpdateAppsActivity") iterator.remove()
        }
    }

    fun hideEnterpriseMode(param: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.settings.personal.OtherPersonalSettings",
            param.classLoader, "onCreate", Bundle::class.java, object : MethodHook() {
                override fun after(callback: AfterHookCallback): Unit = ModuleHelper.guarded {
                    if (callback.getThrowable() == null) removeEnterprisePreference(callback.getThisObject()!!)
                }
            })
    }

    fun hideSearchEntries(param: PackageReadyParam, hideUpdater: Boolean, hideEnterprise: Boolean) {
        val tree = XposedHelpers.findClassIfExists("com.android.settingslib.search.SettingsTree", param.classLoader) ?: return
        val cursor = XposedHelpers.findClassIfExists("com.android.settingslib.search.RankedCursor", param.classLoader) ?: return
        ModuleHelper.findAndHookMethod(tree, "getColumnValues", cursor,
            Double::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, object : MethodHook() {
                override fun before(callback: BeforeHookCallback): Unit = ModuleHelper.guarded {
                    val node = callback.getThisObject() ?: return@guarded
                    val resource = XposedHelpers.getObjectField(node, "mResource") as? String
                    if (resource != "enterprise_mode" && resource != "system_apps_updater") return@guarded
                    if (XposedHelpers.callMethod(node, "getPackage") != "com.android.settings") return@guarded
                    if (hideSearchEntry(resource,
                            XposedHelpers.getObjectField(node, "mPreferenceKey") as? String,
                            XposedHelpers.getObjectField(node, "mIntent") as? Intent,
                            hideUpdater, hideEnterprise)) callback.returnAndSkip(null)
                }
            })
    }

    // Runs only for native search matches, before row/Intent allocation. Never changes
    // the persisted tree: disabling the option and restarting Settings restores results.
    fun hideSearchEntry(resource: String?, key: String?, intent: Intent?,
                        hideUpdater: Boolean, hideEnterprise: Boolean): Boolean =
        (hideEnterprise && resource == "enterprise_mode" &&
            (key == "enterprise_mode_settings" || key == "ex_func_enterprise_mode")) ||
        (hideUpdater && resource == "system_apps_updater" &&
            intent?.component?.packageName == "com.xiaomi.discover" &&
            intent.component?.className == "com.xiaomi.market.ui.UpdateAppsActivity")

    fun removeEnterprisePreference(fragment: Any) {
        val preference = XposedHelpers.callMethod(fragment, "findPreference", "enterprise_mode_settings") ?: return
        val parent = XposedHelpers.callMethod(preference, "getParent") ?: return
        XposedHelpers.callMethod(parent, "removePreference", preference)
        // This ROM shares the category with account sync/developer options. Only remove
        // its wrapper on variants where the identified item was its last child.
        if (XposedHelpers.callMethod(parent, "getPreferenceCount") == 0) {
            val outer = XposedHelpers.callMethod(parent, "getParent") ?: return
            XposedHelpers.callMethod(outer, "removePreference", parent)
        }
    }
}
