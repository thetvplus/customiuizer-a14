package tv.withaibuild.customiuizer.mods.notificationautoexpand

/**
 * Immutable snapshot of the notification auto-expand preferences.
 *
 * [modeRaw] is the raw string value of the `system_expandnotifs` preference. It is parsed
 * inside the callback so that a malformed value throws [NumberFormatException] at the exact
 * same point as the legacy oracle and prevents `chain.proceed()` from being reached.
 *
 * [selectedApps] is a copy-owned, unmodifiable set of package names built from a single
 * captured [PrefMap] generation.
 */
internal data class NotificationAutoExpandSnapshot(
    val modeRaw: String,
    val selectedApps: Set<String>,
)
