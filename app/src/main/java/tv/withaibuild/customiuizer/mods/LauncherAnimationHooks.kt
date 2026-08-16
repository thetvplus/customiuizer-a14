package tv.withaibuild.customiuizer.mods

import android.animation.ValueAnimator
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
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

    @JvmStatic
    fun DisableUnlockWallpaperScale(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.miwallpaper.manager.WallpaperServiceController", lpparam.classLoader, "noNeedDesktopWallpaperScaleAnim", HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun DisableLauncherWallpaperScale(lpparam: PackageReadyParam) {
        val WallpaperZoomManagerKtClass = XposedHelpers.findClassIfExists("com.miui.home.launcher.wallpaper.WallpaperZoomManagerKt", lpparam.classLoader)
        if (MainModule.mPrefs.getBoolean("launcher_disable_wallpaperscale")) {
            XposedHelpers.setStaticBooleanField(WallpaperZoomManagerKtClass, "ZOOM_ENABLED", false)
            ModuleHelper.findAndHookMethod("com.miui.home.recents.DimLayer", lpparam.classLoader, "isSupportDim", HookerClassHelper.returnConstant(false))
            return
        }
        ModuleHelper.hookAllMethods("com.miui.home.recents.OverviewState", lpparam.classLoader, "onStateEnabled", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    if (WallpaperZoomManagerKtClass != null) {
                        XposedHelpers.setStaticBooleanField(WallpaperZoomManagerKtClass, "ZOOM_ENABLED", false)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }

                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    if (WallpaperZoomManagerKtClass != null) {
                        XposedHelpers.setStaticBooleanField(WallpaperZoomManagerKtClass, "ZOOM_ENABLED", true)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun WallpaperColorModeHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "setCurrentStatusBarAreaColorMode", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    val v = MainModule.mPrefs.getStringAsInt("launcher_wallpaper_colormode", 1)
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

                    val v = MainModule.mPrefs.getStringAsInt("launcher_wallpaper_colormode", 1)
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
