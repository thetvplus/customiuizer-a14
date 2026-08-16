package tv.withaibuild.customiuizer.mods

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.View
import java.util.ArrayList
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Workspace layout hooks.
 * Grid limits, dock geometry, cell and widget spacing, page indicator, and
 * infinite scroll.
 */
object LauncherLayoutHooks {

    @JvmStatic
    fun HideSeekPointsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.pageindicators.AllAppsIndicator", lpparam.classLoader, "shouldHide", HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.pageindicators.AllAppsIndicator", lpparam.classLoader, "hideAllAppsArrow", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.getThisObject()

                    val mLauncher = XposedHelpers.getObjectField(thisObject, "mLauncher")
                    if (mLauncher == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val workspace = XposedHelpers.getObjectField(mLauncher, "mWorkspace") as View
                    val isInEditingMode = XposedHelpers.callMethod(workspace, "isInNormalEditingMode") as Boolean
                    val mContext = workspace.context
                    var mHandler = XposedHelpers.getAdditionalInstanceField(workspace, "mHandlerEx") as Handler?
                    if (mHandler == null) {
                        mHandler = Handler(mContext.mainLooper, object : Handler.Callback {
                            override fun handleMessage(msg: Message): Boolean {
                                ModuleHelper.guarded {
                                    val seekBar = msg.obj as? View
                                    if (seekBar != null) {
                                        seekBar.animate().alpha(0.0f).setDuration(300).withEndAction {
                                            ModuleHelper.guarded { seekBar.visibility = View.GONE }
                                        }
                                    }
                                }
                                return true
                            }
                        })
                        XposedHelpers.setAdditionalInstanceField(workspace, "mHandlerEx", mHandler)
                    }
                    if (mHandler.hasMessages(666)) mHandler.removeMessages(666)
                    val mScreenSeekBar = XposedHelpers.getObjectField(thisObject, "mScreenIndicator") as View
                    mScreenSeekBar.animate().cancel()
                    if (!isInEditingMode && MainModule.mPrefs.getBoolean("launcher_hideseekpoints_edit")) {
                        mScreenSeekBar.alpha = 0.0f
                        mScreenSeekBar.visibility = View.GONE
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    mScreenSeekBar.visibility = View.VISIBLE
                    mScreenSeekBar.animate().alpha(1.0f).setDuration(300)
                    if (!isInEditingMode) {
                        val msg = Message.obtain(mHandler, 666)
                        msg.obj = mScreenSeekBar
                        mHandler.sendMessageDelayed(msg, 600)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun InfiniteScrollHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ScreenView", lpparam.classLoader, "getSnapToScreenIndex", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.getThisObject()
                    val args = chain.args

                    if (args[0] != result) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val screenCount = XposedHelpers.callMethod(thisObject, "getScreenCount") as Int
                    if (args[2] as Int == -1 && args[0] as Int == 0)
                    { result = screenCount; throwable = null }
                    else if (args[2] as Int == 1 && args[0] as Int == screenCount - 1)
                    { result = 0; throwable = null }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ScreenView", lpparam.classLoader, "getSnapUnitIndex", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.getThisObject()

                    val mCurrentScreenIndex = XposedHelpers.getIntField(thisObject, if (lpparam.packageName == "com.miui.home") "mCurrentScreenIndex" else "mCurrentScreen")
                    if (mCurrentScreenIndex != result as Int) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val screenCount = XposedHelpers.callMethod(thisObject, "getScreenCount") as Int
                    if (result as Int == 0)
                    { result = screenCount; throwable = null }
                    else if (result as Int == screenCount - 1)
                    { result = 0; throwable = null }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun UnlockGridsRes() {
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "integer", "config_cell_count_x", 3)
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "integer", "config_cell_count_y", 4)
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "integer", "config_cell_count_x_min", 3)
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "integer", "config_cell_count_y_min", 4)
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "integer", "config_cell_count_x_max", 8)
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "integer", "config_cell_count_y_max", 10)
    }

    @JvmStatic
    fun UnlockGridsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onCreate", Bundle::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.getThisObject()

                    XposedHelpers.callMethod(XposedHelpers.getObjectField(thisObject, "mScreenCellsConfig"), "setVisible", true)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        val DeviceConfigClass = XposedHelpers.findClass("com.miui.home.launcher.DeviceConfig", lpparam.classLoader)
        ModuleHelper.findAndHookMethod(DeviceConfigClass, "loadCellsCountConfig", Context::class.java, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val sCellCountY = XposedHelpers.getStaticObjectField(DeviceConfigClass, "sCellCountY") as Int
                    if (sCellCountY > 6) {
                        val cellHeight = XposedHelpers.callStaticMethod(DeviceConfigClass, "getCellHeight") as Int
                        XposedHelpers.setStaticObjectField(DeviceConfigClass, "sFolderCellHeight", cellHeight)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ScreenUtils", lpparam.classLoader, "getScreenCellsSizeOptions", Context::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val arrayList = ArrayList<CharSequence>()
                    var cellCountXMin = 3
                    val cellCountXMax = 8
                    var cellCountYMin = 4
                    val cellCountYMax = 10
                    while (cellCountXMin <= cellCountXMax) {
                        for (i in cellCountYMin..cellCountYMax) {
                            arrayList.add(cellCountXMin.toString() + "x" + i)
                        }
                        cellCountXMin++
                    }
                    skipped = true
                    result = arrayList
                    throwable = null

                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                return try { chain.proceed() } catch (t: Throwable) { XposedHelpers.throwOrReturn(t, null) }
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.compat.LauncherCellCountCompatNoWord", lpparam.classLoader, "setLoadResCellConfig", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    args[0] = true

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        // isNoWordModel() is overridden permanently and only answers false for the thread that
        // is currently inside isCellSizeChangedByTheme, so the hook topology never changes at
        // runtime and concurrent callers cannot unhook each other.
        installUnlockGridsNoWordScope(lpparam.classLoader)
        ModuleHelper.hookAllMethods(DeviceConfigClass, "isCellSizeChangedByTheme", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                unlockGridsNoWordScope.enter()
                return try {
                    chain.proceed()
                } finally {
                    unlockGridsNoWordScope.exit()
                }
            }
        })
    }

    @JvmStatic
    fun HorizontalSpacingRes() {
        val opt = MainModule.mPrefs.getInt("launcher_horizmargin", 0) - 21
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "dimen", "workspace_cell_padding_side", opt)
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "dimen", "workspace_cell_padding_side_no_word", opt)
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "dimen", "workspace_cell_padding_side_rotatable", opt)
    }

    @JvmStatic
    fun IndicatorHeightRes() {
        val opt = MainModule.mPrefs.getInt("launcher_indicatorheight", 9)
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "dimen", "slide_bar_height", opt)
    }

    @JvmStatic
    fun DockMarginTopHook(lpparam: PackageReadyParam) {
        val opt = MainModule.mPrefs.getInt("launcher_dock_topmargin", 0)
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "calcHotSeatsMarginTop", Context::class.java, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    skipped = true
                    result = Math.round(HookUtils.dp2px(opt.toFloat()))
                    throwable = null

                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                return try { chain.proceed() } catch (t: Throwable) { XposedHelpers.throwOrReturn(t, null) }
            }
        })
    }

    @JvmStatic
    fun DockMarginBottomHook(lpparam: PackageReadyParam) {
        val opt = MainModule.mPrefs.getInt("launcher_dock_bottommargin", 0)
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "calcHotSeatsMarginBottom", Context::class.java, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    skipped = true
                    result = Math.round(HookUtils.dp2px(opt.toFloat()))
                    throwable = null

                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                return try { chain.proceed() } catch (t: Throwable) { XposedHelpers.throwOrReturn(t, null) }
            }
        })
    }

    @JvmStatic
    fun DockHeightHook(lpparam: PackageReadyParam) {
        val dockHeight = MainModule.mPrefs.getInt("launcher_dock_height", 60)
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "calcHotSeatsHeight", Context::class.java, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    skipped = true
                    result = Math.round(HookUtils.dp2px(dockHeight.toFloat()))
                    throwable = null

                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                return try { chain.proceed() } catch (t: Throwable) { XposedHelpers.throwOrReturn(t, null) }
            }
        })
    }

    @JvmStatic
    fun WorkspaceCellPaddingTopHook(lpparam: PackageReadyParam) {
        val opt = MainModule.mPrefs.getInt("launcher_topmargin", 0) - 21
        val hook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    skipped = true
                    result = Math.round(HookUtils.dp2px(opt.toFloat()))
                    throwable = null

                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                return try { chain.proceed() } catch (t: Throwable) { XposedHelpers.throwOrReturn(t, null) }
            }
        }

        val newLauncher = ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "getWorkspaceCellPaddingTop", Context::class.java, hook)
        if (!newLauncher) {
            ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "getWorkspaceCellPaddingTop", hook)
        }
    }

    @JvmStatic
    fun IndicatorMarginTopHook(lpparam: PackageReadyParam) {
        val opt = MainModule.mPrefs.getInt("launcher_indicator_topmargin", 0) - 21
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "dimen", "slide_bar_margin_top", opt)
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.util.DimenUtils1X", lpparam.classLoader, "getDimensionPixelSize", Context::class.java, String::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val resKey = chain.getArg(1) as String
                    if ("slide_bar_margin_top" == resKey) {
                        skipped = true
                        result = Math.round(HookUtils.dp2px(opt.toFloat()))
                        throwable = null
                    }

                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                return try { chain.proceed() } catch (t: Throwable) { XposedHelpers.throwOrReturn(t, null) }
            }
        })
    }

    @JvmStatic
    fun HorizontalWidgetSpacingHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "getMiuiWidgetSizeSpec", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val args = chain.args

                    if (args.size < 4) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val spec = result as Long
                    var width = spec shr 32
                    var height = spec - ((spec shr 32) shl 32)
                    val opt = Math.round((MainModule.mPrefs.getInt("launcher_horizwidgetmargin", 0) - 21) * Resources.getSystem().displayMetrics.density) * 2
                    width -= opt.toLong()
                    result = (width shl 32) or height

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethods("com.miui.home.launcher.MIUIWidgetUtil", lpparam.classLoader, "getMiuiWidgetPadding", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    result = Rect()
                    throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun NoWidgetOnlyHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.CellLayout", lpparam.classLoader, "setScreenType", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    args[0] = 0

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun MaxHotseatIconsCountHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "getHotseatMaxCount", HookerClassHelper.returnConstant(666))
    }

    @JvmStatic
    fun ResizableWidgetsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("android.appwidget.AppWidgetHostView", lpparam.classLoader, "getAppWidgetInfo", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val widgetInfo = result as AppWidgetProviderInfo?
                    if (widgetInfo == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    widgetInfo.resizeMode = AppWidgetProviderInfo.RESIZE_VERTICAL or AppWidgetProviderInfo.RESIZE_HORIZONTAL
                    widgetInfo.minHeight = 0
                    widgetInfo.minWidth = 0
                    widgetInfo.minResizeHeight = 0
                    widgetInfo.minResizeWidth = 0
                    result = widgetInfo
                    throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

}
