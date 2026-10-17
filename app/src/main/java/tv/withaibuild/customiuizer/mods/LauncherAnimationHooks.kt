package tv.withaibuild.customiuizer.mods

import android.animation.ValueAnimator
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Launcher animation and wallpaper hooks.
 * Spring stiffness, unlock and zoom animations, the legacy launch animation, and
 * wallpaper scale and colour mode.
 */
object LauncherAnimationHooks {

    private fun scaleStiffness(`val`: Float, scale: Float): Float {
        return (if (scale < 1.0f) 2f / scale else 1.0f / scale) * `val`
    }

    @JvmStatic
    fun FixAnimHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.miui.home.launcher.animate.SpringAnimator", lpparam.classLoader, "getSpringForce", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    var scale = ValueAnimator.getDurationScale()
                    if (scale == 1.0f) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (scale == 0f) scale = 0.01f
                    val args = XposedHelpers.getArgsArray(chain)
                    args[2] = scaleStiffness(args[2] as Float, scale)

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        val hook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    var scale = ValueAnimator.getDurationScale()
                    if (scale == 1.0f) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (scale == 0f) scale = 0.01f
                    XposedHelpers.setFloatField(thisObject, "mCenterXStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mCenterXStiffness"), scale))
                    XposedHelpers.setFloatField(thisObject, "mCenterYStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mCenterYStiffness"), scale))
                    XposedHelpers.setFloatField(thisObject, "mWidthStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mWidthStiffness"), scale))
                    XposedHelpers.setFloatField(thisObject, "mRadiusStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mRadiusStiffness"), scale))
                    XposedHelpers.setFloatField(thisObject, "mAlphaStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mAlphaStiffness"), scale))
                    try {
                        XposedHelpers.setFloatField(thisObject, "mRatioStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mRatioStiffness"), scale))
                    } catch (t: Throwable) {
                        XposedHelpers.setFloatField(thisObject, "mRadioStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mRadioStiffness"), scale))
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

        if (!ModuleHelper.hookAllMethodsSilently("com.miui.home.recents.util.RectFSpringAnim", lpparam.classLoader, "start", hook))
            ModuleHelper.hookAllMethods("com.miui.home.recents.util.RectFSpringAnim", lpparam.classLoader, "initAllAnimations", hook)
    }

    @JvmStatic
    fun NoUnlockAnimationHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.miui.launcher.utils.MiuiSettingsUtils", lpparam.classLoader, "isSystemAnimationOpen", HookerClassHelper.returnConstant(false))
    }

    @JvmStatic
    fun NoZoomAnimationHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.miui.home.recents.util.SpringAnimationUtils", lpparam.classLoader, "startShortcutMenuLayerFadeOutAnim", HookerClassHelper.DO_NOTHING)
        ModuleHelper.hookAllMethods("com.miui.home.recents.util.SpringAnimationUtils", lpparam.classLoader, "startShortcutMenuLayerFadeInAnim", HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun UseOldLaunchAnimationHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.miui.home.recents.QuickstepAppTransitionManagerImpl", lpparam.classLoader, "hasControlRemoteAppTransitionPermission", HookerClassHelper.returnConstant(false))
    }

    private const val PREF_LAUNCHER_WALLPAPER_SCALE = "launcher_disable_wallpaperscale"
    private const val PREF_RECENTS_WALLPAPER_SCALE = "system_recents_disable_wallpaperscale"
    private const val PREF_WALLPAPER_COLOR_MODE = "launcher_wallpaper_colormode"

    /**
     * Wallpaper zoom is applied from recents and app-open/close callbacks. Snapshot the two
     * preference keys so those paths do not hit the preference map, and so toggling one key
     * cannot leave the other key's intent stuck in a recents-session flag.
     */
    @Volatile
    private var suppressLauncherWallpaperZoom = false

    @Volatile
    private var disableRecentsDimLayer = false

    @Volatile
    private var wallpaperColorMode = 1

    @Volatile
    private var wallpaperZoomObserverRegistered = false

    @Volatile
    private var wallpaperZoomClassLoader: ClassLoader? = null

    @JvmStatic
    fun DisableUnlockWallpaperScale(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.miwallpaper.manager.WallpaperServiceController", lpparam.classLoader, "noNeedDesktopWallpaperScaleAnim", HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun DisableLauncherWallpaperScale(lpparam: PackageReadyParam) {
        wallpaperZoomClassLoader = lpparam.classLoader
        installWallpaperZoomSnapshot()
        applyWallpaperZoomEnabledFlag(lpparam.classLoader, !suppressLauncherWallpaperZoom)

        val clampZoomOut = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    if (!suppressLauncherWallpaperZoom) {
                        return XposedHelpers.proceedOrThrow(chain, null)
                    }
                    val args = XposedHelpers.getArgsArray(chain)
                    var changed = false
                    for (i in args.indices) {
                        val value = args[i]
                        if (value is Float && value != 0f) {
                            args[i] = wallpaperZoomOutValue(value, true)
                            changed = true
                        }
                    }
                    result = if (changed) chain.proceed(args) else chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        val skipZoomAnim = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    if (shouldSkipWallpaperZoomAnimation(suppressLauncherWallpaperZoom)) {
                        return null
                    }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        val homeZoomOut = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    if (suppressLauncherWallpaperZoom) {
                        return 0f
                    }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

        val classLoader = lpparam.classLoader
        ModuleHelper.hookAllMethodsSilently("android.app.WallpaperManager", classLoader, "setWallpaperZoomOut", clampZoomOut)
        ModuleHelper.hookAllMethodsSilently("android.view.IWindowSession\$Stub\$Proxy", classLoader, "setWallpaperZoomOut", clampZoomOut)
        for (zoomClassName in WALLPAPER_ZOOM_MANAGER_CLASSES) {
            ModuleHelper.hookAllMethodsSilently(zoomClassName, classLoader, "setWallpaperZoomOut", clampZoomOut)
            ModuleHelper.hookAllMethodsSilently(zoomClassName, classLoader, "animateWallpaperZoom", skipZoomAnim)
            ModuleHelper.hookAllMethodsSilently(zoomClassName, classLoader, "startWallpaperZoomAnim", skipZoomAnim)
        }
        ModuleHelper.hookAllMethodsSilently("com.miui.home.launcher.WallpaperUtils", classLoader, "setWallpaperZoomOut", clampZoomOut)
        ModuleHelper.hookAllMethodsSilently("com.miui.home.launcher.WallpaperUtils", classLoader, "animateWallpaperZoom", skipZoomAnim)
        ModuleHelper.hookAllMethodsSilently("com.miui.home.recents.OverviewState", classLoader, "getWallpaperZoomOut", homeZoomOut)
        ModuleHelper.hookAllMethodsSilently("com.android.quickstep.views.RecentsView", classLoader, "getWallpaperZoomOut", homeZoomOut)
        ModuleHelper.hookAllMethodsSilently("com.android.launcher3.Launcher", classLoader, "getWallpaperZoomOut", homeZoomOut)

        ModuleHelper.hookAllMethodsSilently("com.miui.home.recents.DimLayer", classLoader, "isSupportDim", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    if (disableRecentsDimLayer) {
                        return false
                    }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    internal fun shouldDisableLauncherWallpaperZoomPermanently(launcherPref: Boolean): Boolean = launcherPref

    @JvmStatic
    internal fun shouldSuppressLauncherWallpaperZoom(recentsPref: Boolean, launcherPref: Boolean): Boolean =
        recentsPref || launcherPref

    @JvmStatic
    internal fun shouldKeepRecentsWallpaperZoomDisabled(recentsPref: Boolean, launcherPref: Boolean): Boolean =
        shouldSuppressLauncherWallpaperZoom(recentsPref, launcherPref)

    @JvmStatic
    internal fun wallpaperZoomOutValue(requested: Float, suppress: Boolean): Float =
        if (suppress) 0f else requested

    @JvmStatic
    internal fun shouldSkipWallpaperZoomAnimation(suppress: Boolean): Boolean = suppress

    @JvmStatic
    internal fun installWallpaperZoomSnapshot() {
        refreshWallpaperZoomPreferences()
        if (wallpaperZoomObserverRegistered) return
        wallpaperZoomObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key == PREF_LAUNCHER_WALLPAPER_SCALE || key == PREF_RECENTS_WALLPAPER_SCALE || key == PREF_WALLPAPER_COLOR_MODE) {
                    refreshWallpaperZoomPreferences()
                    applyWallpaperZoomEnabledFlag(wallpaperZoomClassLoader, !suppressLauncherWallpaperZoom)
                }
            }
        }, this)
    }

    private fun refreshWallpaperZoomPreferences() {
        val launcherPref = MainModule.mPrefs.getBoolean(PREF_LAUNCHER_WALLPAPER_SCALE)
        val recentsPref = MainModule.mPrefs.getBoolean(PREF_RECENTS_WALLPAPER_SCALE)
        suppressLauncherWallpaperZoom = shouldSuppressLauncherWallpaperZoom(recentsPref, launcherPref)
        disableRecentsDimLayer = shouldDisableLauncherWallpaperZoomPermanently(launcherPref)
        wallpaperColorMode = MainModule.mPrefs.getStringAsInt(PREF_WALLPAPER_COLOR_MODE, 1)
    }

    private val WALLPAPER_ZOOM_MANAGER_CLASSES = arrayOf(
        "com.miui.home.launcher.wallpaper.WallpaperZoomManagerKt",
        "com.miui.home.launcher.wallpaper.WallpaperZoomManager",
    )

    private fun applyWallpaperZoomEnabledFlag(classLoader: ClassLoader?, enabled: Boolean) {
        if (classLoader == null) return
        for (className in WALLPAPER_ZOOM_MANAGER_CLASSES) {
            val zoomClass = XposedHelpers.findClassIfExists(className, classLoader) ?: continue
            setWallpaperZoomEnabled(zoomClass, enabled)
            val companionClass = XposedHelpers.findClassIfExists("$className\$Companion", classLoader)
            setWallpaperZoomEnabled(companionClass, enabled)
            try {
                val companion = XposedHelpers.getStaticObjectField(zoomClass, "Companion")
                if (companion != null) {
                    val field = XposedHelpers.findFieldIfExists(companion.javaClass, "ZOOM_ENABLED")
                    field?.setBoolean(companion, enabled)
                }
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
            }
        }
    }

    private fun setWallpaperZoomEnabled(zoomClass: Class<*>?, enabled: Boolean) {
        if (zoomClass == null) return
        try {
            val field = XposedHelpers.findFieldIfExists(zoomClass, "ZOOM_ENABLED") ?: return
            field.setBoolean(null, enabled)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
        }
    }

    @JvmStatic
    fun WallpaperColorModeHook(lpparam: PackageReadyParam) {
        installWallpaperZoomSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "setCurrentStatusBarAreaColorMode", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    val v = wallpaperColorMode
                    if (v > 1) {
                        args[0] = if (v == 2) 2 else 0
                    }

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "setCurrentWallpaperColorMode", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    val v = wallpaperColorMode
                    if (v > 1) {
                        args[0] = if (v == 2) 2 else 0
                    }

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

}
