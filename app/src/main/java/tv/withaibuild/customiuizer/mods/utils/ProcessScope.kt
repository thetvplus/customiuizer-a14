package tv.withaibuild.customiuizer.mods.utils

/**
 * Canonical process scope for a package ready callback.
 *
 * Replaces scattered package-name string checks in [tv.withaibuild.customiuizer.MainModule]
 * with a single table-driven decision.  The scope is resolved from the package name and,
 * when necessary, the reported process name.
 */
enum class ProcessScope {
    SYSTEM_SERVER,
    SYSTEM_UI,
    SYSTEM_UI_PLUGIN,
    LAUNCHER,
    SETTINGS_MAIN,
    SETTINGS_REMOTE,
    SECURITY_CENTER_MAIN,
    SECURITY_CENTER_REMOTE,
    SECURITY_CENTER_BOOTAWARE,
    POWER_KEEPER,
    WALLPAPER,
    MEDIA,
    PHONE,
    GUARD_PROVIDER,
    PACKAGE_INSTALLER,
    PERMISSION_CONTROLLER,
    INPUT_METHOD,
    GENERIC_APP,
    NETWORK_STACK,
    UNSUPPORTED;

    /** True if this scope can host module hooks in A14. */
    val isInstallable: Boolean
        get() = this != UNSUPPORTED
            && this != SYSTEM_UI_PLUGIN
            && this != SETTINGS_REMOTE
            && this != SECURITY_CENTER_REMOTE
            && this != SECURITY_CENTER_BOOTAWARE
            && this != NETWORK_STACK
}
