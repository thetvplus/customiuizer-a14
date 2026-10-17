package tv.withaibuild.customiuizer.utils

import android.content.Context
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import tv.withaibuild.customiuizer.R

/**
 * Settings-side contract for features that must hook the user-selected app process.
 *
 * With `staticScope=true` and a fixed `scope.list`, newly chosen ordinary apps also need
 * framework scope authorization, requested through the API 102 service. Lists that are only filters for
 * system_server / SystemUI / Launcher must not request scope.
 */
object DynamicAppScope {

    val REQUIRED_STORAGE_KEYS = setOf(
        "pref_key_system_statusbarcolor_apps",
        "pref_key_system_nooverscroll_apps",
        "pref_key_controls_mediaplayer_apps",
        "pref_key_various_alarmcompat_apps",
    )

    private val unavailableNotified = AtomicBoolean(false)

    @JvmStatic
    fun requiresTargetAppScope(preferenceKey: String?): Boolean {
        if (preferenceKey.isNullOrEmpty()) return false
        val storage = storagePreferenceKey(preferenceKey) ?: preferenceKey
        return storage in REQUIRED_STORAGE_KEYS
    }

    @JvmStatic
    fun packageNamesOf(selected: Collection<String>): List<String> {
        if (selected.isEmpty()) return emptyList()
        val packages = LinkedHashSet<String>(selected.size)
        for (identifier in selected) {
            if (identifier.isEmpty()) continue
            val cut = identifier.indexOf('|')
            val pkg = if (cut > 0) identifier.substring(0, cut) else identifier
            if (pkg.isNotEmpty()) packages.add(pkg)
        }
        return packages.toList()
    }

    @JvmStatic
    fun requestForSelection(
        context: Context?,
        preferenceKey: String?,
        selected: Collection<String>,
    ) {
        if (!requiresTargetAppScope(preferenceKey)) return
        val packages = packageNamesOf(selected)
        if (packages.isEmpty()) return
        requestPackages(context, packages)
    }

    private fun requestPackages(context: Context?, packages: List<String>) {
        val appContext = context?.applicationContext
        val requested = XposedServiceManager.requestScope(packages) { success, message ->
            if (!success) {
                AppHelper.log("DynamicAppScope", message ?: "scope request rejected")
                showToast(appContext, R.string.dynamic_app_scope_failed)
            }
        }
        if (!requested && unavailableNotified.compareAndSet(false, true)) {
            showToast(appContext, R.string.dynamic_app_scope_unavailable)
        }
    }

    private fun showToast(context: Context?, resId: Int) {
        val ctx = context?.applicationContext ?: return
        Toast.makeText(ctx, resId, Toast.LENGTH_LONG).show()
    }
}
