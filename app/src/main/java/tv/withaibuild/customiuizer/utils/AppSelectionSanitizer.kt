package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * Removes package selections that cannot exist on the current device.
 *
 * Multi-app selectors use `*_apps` / `*_apps_black` string sets. Single-app and
 * activity selectors use `*_app` / `*_activity` strings containing either a package or
 * `package|component`. These storage contracts let one small cold-path pass cover current
 * and future selectors without maintaining a second hard-coded preference-key registry.
 */
object AppSelectionSanitizer {

    @JvmStatic
    internal fun isMultiAppSelectionKey(key: String): Boolean =
        key.endsWith("_apps") || key.endsWith("_apps_black")

    @JvmStatic
    internal fun isSingleAppSelectionKey(key: String): Boolean =
        key.endsWith("_app") || key.endsWith("_activity")

    @JvmStatic
    internal fun sanitizeSelection(
        selected: Set<String>,
        installedPackages: Set<String>,
    ): LinkedHashSet<String> {
        val sanitized = LinkedHashSet<String>(selected.size)
        for (identifier in selected) {
            val packageName = identifier.substringBefore('|')
            if (packageName in installedPackages) sanitized.add(identifier)
        }
        return sanitized
    }

    @JvmStatic
    internal fun sanitizeAvailableSelection(
        selected: Set<String>,
        availableIdentifiers: Set<String>,
        multiUser: Boolean,
    ): LinkedHashSet<String> {
        val sanitized = LinkedHashSet<String>(selected.size)
        for (identifier in selected) {
            if (multiUser) {
                val normalized = if ('|' in identifier) identifier else "$identifier|0"
                if (normalized in availableIdentifiers) sanitized.add(normalized)
            } else {
                val packageName = identifier.substringBefore('|')
                if (packageName in availableIdentifiers) sanitized.add(packageName)
            }
        }
        return sanitized
    }

    data class SanitizeResult(
        val entries: Map<String, Any?>,
        val changedPrimaryCount: Int,
    )

    @JvmStatic
    internal fun sanitizeRestoredEntries(
        entries: Map<String, Any?>,
        installedPackages: Set<String>,
    ): SanitizeResult {
        if (installedPackages.isEmpty()) return SanitizeResult(entries, 0)

        val sanitized = LinkedHashMap(entries)
        var changedPrimaryCount = 0
        for ((key, value) in entries) {
            when {
                isMultiAppSelectionKey(key) && value is Set<*> -> {
                    val selected = LinkedHashSet<String>()
                    for (item in value) if (item is String) selected.add(item)
                    val result = sanitizeSelection(selected, installedPackages)
                    sanitized[key] = result
                    if (result != selected || result.size != selected.size) {
                        changedPrimaryCount++
                    }
                }
                isSingleAppSelectionKey(key) && value is String -> {
                    val packageName = value.substringBefore('|')
                    if (packageName.isEmpty() || packageName !in installedPackages) {
                        sanitized.remove(key)
                        sanitized.remove(key + "_user")
                        changedPrimaryCount++
                    }
                }
            }
        }
        return SanitizeResult(sanitized, changedPrimaryCount)
    }

    @JvmStatic
    fun queryInstalledPackageNames(packageManager: PackageManager): Set<String> {
        val applications = packageManager.getInstalledApplications(
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        val packages = HashSet<String>(applications.size * 4 / 3 + 1)
        for (application in applications) packages.add(application.packageName)
        return packages
    }

    /** Runs off the main thread; returns the number of changed or removed preference keys. */
    @JvmStatic
    fun sanitizeStoredSelections(
        prefs: SharedPreferences,
        installedPackages: Set<String>,
    ): Int {
        if (installedPackages.isEmpty()) return 0

        val current = prefs.all
        val editor = prefs.edit()
        var changed = 0
        for ((key, value) in current) {
            when {
                isMultiAppSelectionKey(key) && value is Set<*> -> {
                    val selected = LinkedHashSet<String>()
                    for (item in value) if (item is String) selected.add(item)
                    val sanitized = sanitizeSelection(selected, installedPackages)
                    if (sanitized != selected || selected.size != value.size) {
                        editor.putStringSet(key, sanitized)
                        changed++
                    }
                }
                isSingleAppSelectionKey(key) && value is String -> {
                    val packageName = value.substringBefore('|')
                    if (packageName.isEmpty() || packageName !in installedPackages) {
                        editor.remove(key)
                        editor.remove(key + "_user")
                        changed++
                    }
                }
            }
        }
        if (changed > 0) editor.commit()
        return changed
    }

    /**
     * Prunes one open selector against the exact list it displays. SharedPreferences.apply
     * updates the in-memory value synchronously, so the adapter and outer count agree on the
     * same frame while disk I/O remains asynchronous.
     */
    @JvmStatic
    fun sanitizeOpenSelector(
        prefs: SharedPreferences,
        key: String,
        availableIdentifiers: Set<String>,
        multiUser: Boolean,
        blackAndWhite: Boolean,
    ) {
        val selected = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val sanitized = sanitizeAvailableSelection(selected, availableIdentifiers, multiUser)
        val editor = prefs.edit()
        var changed = false
        if (sanitized != selected) {
            editor.putStringSet(key, sanitized)
            changed = true
        }

        if (blackAndWhite) {
            val blackKey = key + "_black"
            val selectedBlack = prefs.getStringSet(blackKey, emptySet()) ?: emptySet()
            val sanitizedBlack = sanitizeAvailableSelection(
                selectedBlack,
                availableIdentifiers,
                multiUser = false,
            )
            if (sanitizedBlack != selectedBlack) {
                editor.putStringSet(blackKey, sanitizedBlack)
                changed = true
            }
        }

        if (changed) editor.apply()
    }
}
