package tv.withaibuild.customiuizer.mods.utils

/**
 * Lifecycle phase at which a feature may be installed.
 *
 * Preferences are not guaranteed to be ready at `onModuleLoaded` or the first `onPackageReady`, so
 * each feature declares the earliest phase at which it may run.  The registry only calls a feature
 * when both its [FeatureTarget] and its [InstallPhase] match the current hook entry point.
 */
enum class InstallPhase {
    /** Module has loaded but preferences may not be available yet. */
    MODULE_LOADED,

    /** System server is starting; this is the first place the module can affect framework policy. */
    SYSTEM_SERVER_STARTING,

    /** A package the module scopes is being prepared.  The package name is known. */
    PACKAGE_READY,

    /** Preferences are confirmed ready (loaded or valid empty). */
    PREFS_READY,

    /** The package's `Application.attach` has run and a real Context is available. */
    APPLICATION_ATTACHED,

    /** SystemUI's status bar initialization hook has run. */
    SYSTEM_UI_INITIALIZED,

    /** The launcher's main activity / home application is ready. */
    LAUNCHER_READY,
}
