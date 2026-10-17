package tv.withaibuild.customiuizer.mods.utils

/**
 * The process or apk in which a feature must be installed.
 *
 * Keeping the target explicit prevents a feature from being installed in the wrong process and
 * makes it possible to scan the registry for all features that belong to the current package.
 */
enum class FeatureTarget {
    /** The feature lives in the settings app, not in a hooked process. */
    SETTINGS_APP,

    /** The feature lives in the Android system package (`android`). */
    SYSTEM_PACKAGE,

    /** The feature lives in `com.android.systemui` or the MIUI SystemUI plugin. */
    SYSTEM_UI,

    /** The feature lives in `com.miui.home` (or another supported launcher). */
    LAUNCHER,

    /** The feature lives in `system_server`. */
    SYSTEM_SERVER,

    /** The feature is process-agnostic and can be installed anywhere the registry is applied. */
    ANY,
}
