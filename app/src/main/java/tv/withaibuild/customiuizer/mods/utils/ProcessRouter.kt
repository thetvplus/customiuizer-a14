package tv.withaibuild.customiuizer.mods.utils

/**
 * Table-driven process scope resolution.
 *
 * This is the single place that maps `PackageReadyParam.getPackageName()` (and the
 * optional process name) to a [ProcessScope].  Installers receive the resolved scope
 * and no longer need to re-guess the package/process.
 */
object ProcessRouter {

    /** Exact package names known to be input method packages. */
    private val inputMethodExactPackages = setOf(
        "com.baidu.input",
        "com.baidu.input_mi",
        "com.iflytek.inputmethod",
        "com.iflytek.inputmethod.miui",
        "com.sohu.inputmethod.sogou",
        "com.sohu.inputmethod.sogou.xiaomi",
    )

    /** Package name prefixes used to identify other input method packages. */
    private val inputMethodPrefixes = listOf(
        "com.google.android.inputmethod",
        "com.touchtype.swiftkey",
        "com.tencent.wetype",
    )

    /** Package names handled by [tv.withaibuild.customiuizer.installers.MediaInstaller]. */
    private val mediaPackages = setOf(
        "com.miui.miwallpaper",
        "com.miui.screenshot",
        "com.miui.gallery",
    )

    /** Package names handled by package installer routing. */
    private val packageInstallerPackages = setOf(
        "com.miui.packageinstaller",
    )

    /** AOSP and Google A14 PermissionController package names. */
    private val permissionControllerPackages = setOf(
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    /**
     * Resolve a package/process pair to a canonical [ProcessScope].
     *
     * @param packageName the package name from `PackageReadyParam`
     * @param processName the process name if different from the package; null means the main process
     */
    @JvmStatic
    @JvmOverloads
    fun resolve(packageName: String, processName: String? = packageName): ProcessScope {
        val actualProcessName = processName ?: packageName
        return when (packageName) {
            "android" -> ProcessScope.SYSTEM_SERVER
            "com.android.systemui" -> if (packageName == actualProcessName) ProcessScope.SYSTEM_UI else ProcessScope.SYSTEM_UI_PLUGIN
            "com.miui.home" -> ProcessScope.LAUNCHER
            "com.android.settings" -> if (packageName == actualProcessName) ProcessScope.SETTINGS_MAIN else ProcessScope.SETTINGS_REMOTE
            "com.miui.securitycenter" -> when (actualProcessName) {
                "com.miui.securitycenter" -> ProcessScope.SECURITY_CENTER_MAIN
                "com.miui.securitycenter.bootaware" -> ProcessScope.SECURITY_CENTER_BOOTAWARE
                else -> ProcessScope.SECURITY_CENTER_REMOTE
            }
        "com.miui.powerkeeper" -> ProcessScope.POWER_KEEPER
        "com.miui.guardprovider" -> ProcessScope.GUARD_PROVIDER
        "com.miui.miwallpaper" -> ProcessScope.WALLPAPER
        in mediaPackages -> ProcessScope.MEDIA
        "com.android.incallui" -> ProcessScope.PHONE
        in packageInstallerPackages -> ProcessScope.PACKAGE_INSTALLER
        in permissionControllerPackages -> if (packageName == actualProcessName) {
            ProcessScope.PERMISSION_CONTROLLER
        } else {
            ProcessScope.UNSUPPORTED
        }
        in inputMethodExactPackages -> ProcessScope.INPUT_METHOD
        else -> when {
            packageName.startsWith("com.android.networkstack") -> ProcessScope.NETWORK_STACK
            packageName == "com.android.location.fused" -> ProcessScope.UNSUPPORTED
            inputMethodPrefixes.any { packageName.startsWith(it) } -> ProcessScope.INPUT_METHOD
            else -> ProcessScope.GENERIC_APP
        }
    }
}
}
