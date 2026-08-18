package tv.withaibuild.customiuizer.mods

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.view.View
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ImageView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ResourceConstants
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.ArrayList
import java.util.List

/**
 * Window, rotation and free-form hooks.
 * Orientation and all-rotations behaviour, the free-form and floating-window
 * blacklists, multi-window entry points, overscroll and overlay suppression.
 */
object SystemWindowHooks {

    internal data class WindowSnapshot(
        val autoRotateState: Int = 0,
        val allowAllRotations: Int = 0,
        val floatApps: Set<String> = emptySet(),
        val floatAppsBlack: Set<String> = emptySet(),
    )

    @Volatile
    internal var windowConfig = WindowSnapshot()

    private var windowObserverRegistered = false

    private fun refreshWindowSnapshot() {
        val prefs = MainModule.mPrefs
        windowConfig = WindowSnapshot(
            autoRotateState = prefs.getInt("qs_autorotate_state", 0),
            allowAllRotations = if (prefs.getStringAsInt("system_allrotations2", 1) == 2) 1 else 0,
            floatApps = HashSet(prefs.getStringSet("system_betterpopups_allowfloat_apps")),
            floatAppsBlack = HashSet(prefs.getStringSet("system_betterpopups_allowfloat_apps_black")),
        )
    }

    private fun installWindowSnapshot() {
        refreshWindowSnapshot()
        if (windowObserverRegistered) return
        windowObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null ||
                    key == "qs_autorotate_state" ||
                    key == "system_allrotations2" ||
                    key == "system_betterpopups_allowfloat_apps" ||
                    key == "system_betterpopups_allowfloat_apps_black"
                ) refreshWindowSnapshot()
            }
        })
    }

    @JvmStatic
    fun OrientationLockHook(lpparam: SystemServerStartingParam) {
        installWindowSnapshot()
        val windowClass = "com.android.server.wm.DisplayRotation"
        val rotMethod = "rotationForOrientation"
        ModuleHelper.hookAllMethods(windowClass, lpparam.classLoader, rotMethod, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val args = chain.args

                    if ((args[0] as Int) == -1) {
                        val opt = windowConfig.autoRotateState
                        var prevOrient = args[1] as Int
                        val res = result as Int
                        if (opt == 1) {
                            if (prevOrient != 0 && prevOrient != 2) prevOrient = 0
                            if (res == 1 || res == 3) { result = prevOrient; throwable = null }
                        } else if (opt == 2) {
                            if (prevOrient != 1 && prevOrient != 3) prevOrient = 1
                            if (res == 0 || res == 2) { result = prevOrient; throwable = null }
                        }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun AllRotationsHook(lpparam: SystemServerStartingParam) {
        installWindowSnapshot()
        ModuleHelper.hookAllConstructors("com.android.server.wm.DisplayRotation", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    XposedHelpers.setIntField(thisObject, "mAllowAllRotations", windowConfig.allowAllRotations)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun NoOverscrollAppHook(lpparam: PackageReadyParam) {
        val hookParam = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    args[0] = false

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

        val sblCls = XposedHelpers.findClassIfExists("miuix.springback.view.SpringBackLayout", lpparam.classLoader)
        if (sblCls != null) {
            ModuleHelper.hookAllConstructors(sblCls, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any?
                    var throwable: Throwable? = null
                    try {
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    try {
                        val thisObject = chain.thisObject

                        try {
                            XposedHelpers.callMethod(thisObject, "setSpringBackEnable", false)
                        } catch (t: Throwable) {
                            try { XposedHelpers.setBooleanField(thisObject, "mSpringBackEnable", false) } catch (ignore: Throwable) {}
                        }

                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
            ModuleHelper.findAndHookMethodSilently(sblCls, "setSpringBackEnable", Boolean::class.javaPrimitiveType!!, hookParam)
        }

        val rrvCls = XposedHelpers.findClassIfExists("androidx.recyclerview.widget.RemixRecyclerView", lpparam.classLoader)
        if (rrvCls != null) {
            ModuleHelper.hookAllConstructors(rrvCls, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any?
                    var throwable: Throwable? = null
                    try {
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    try {
                        val thisObject = chain.thisObject

                        (thisObject as View).overScrollMode = View.OVER_SCROLL_NEVER
                        try {
                            XposedHelpers.callMethod(thisObject, "setSpringEnabled", false)
                        } catch (t: Throwable) {
                            try { XposedHelpers.setBooleanField(thisObject, "mSpringEnabled", false) } catch (ignore: Throwable) {}
                        }

                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
            ModuleHelper.findAndHookMethodSilently(rrvCls, "setSpringEnabled", Boolean::class.javaPrimitiveType!!, hookParam)
        }

        ModuleHelper.findAndHookMethod("android.widget.AbsListView", lpparam.classLoader, "initAbsListView", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    (thisObject as AbsListView).overScrollMode = View.OVER_SCROLL_NEVER

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun AllowAllFloatHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.ExpandedNotification", lpparam.classLoader, "isEnableFloat", HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.NotificationSettingsManager", lpparam.classLoader, "canFloat", Context::class.java, String::class.java, String::class.java, HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun TempHideOverlayAppHook(lpparam: SystemServerStartingParam) {
        val flagIndex = 2
        ModuleHelper.hookAllConstructors("com.android.server.wm.WindowSurfaceController", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val windowType = chain.getArg(4) as Int
                    if (windowType != WindowManager.LayoutParams.TYPE_PHONE
                        && windowType != WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                        && windowType != WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                        && windowType != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val args = XposedHelpers.getArgsArray(chain)
                    var flags = args[flagIndex] as Int
                    val skipFlag = 64
                    flags = flags or skipFlag
                    args[flagIndex] = flags

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
    fun BetterPopupsAllowFloatHook(lpparam: PackageReadyParam) {
        installWindowSnapshot()
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiExpandableNotificationRow", lpparam.classLoader, "updateMiniWindowBar", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val pkgName = XposedHelpers.callMethod(thisObject, "getMiniWindowTargetPkg") as String
                    val selectedApps = windowConfig.floatApps
                    val selectedAppsBlack = windowConfig.floatAppsBlack
                    val mAppMiniWindowManager = XposedHelpers.callMethod(thisObject, "getMAppMiniWindowManager")
                    val notificationSettingsManager = XposedHelpers.getObjectField(mAppMiniWindowManager, "notificationSettingsManager")
                    val mAllowNotificationSlide = XposedHelpers.getObjectField(notificationSettingsManager, "mAllowNotificationSlide") as List<String>
                    if (selectedApps?.contains(pkgName) == true) {
                        mAllowNotificationSlide.add(pkgName)
                    } else if (selectedAppsBlack?.contains(pkgName) == true) {
                        mAllowNotificationSlide.remove(pkgName)
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    private fun DisableFloatingWindowBlacklistHook(cl: ClassLoader) {
        val clearHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val blackList = result as List<String>?
                    if (blackList != null) {
                        blackList.clear()
                        blackList.add("com.android.camera")
                    }
                    result = blackList
                    throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.hookAllMethodsSilently("android.util.MiuiMultiWindowAdapter", cl, "getListFromCloudData", clearHook)
        ModuleHelper.hookAllMethodsSilently("android.util.MiuiMultiWindowAdapter", cl, "getStartFromFreeformBlackListFromCloud", clearHook)
        ModuleHelper.hookAllMethods("android.util.MiuiMultiWindowAdapter", cl, "getFreeformBlackList", clearHook)
        ModuleHelper.hookAllMethods("android.util.MiuiMultiWindowAdapter", cl, "getFreeformBlackListFromCloud", clearHook)
        ModuleHelper.hookAllMethods("android.util.MiuiMultiWindowAdapter", cl, "setFreeformBlackList", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    val blackList = ArrayList<String>()
                    blackList.add("com.android.camera")
                    args[0] = blackList

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("android.util.MiuiMultiWindowUtils", cl, "isForceResizeable", HookerClassHelper.returnConstant(true))
        ModuleHelper.hookAllMethodsSilently("android.util.MiuiMultiWindowUtils", cl, "isPkgMainActivityResizeable", HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun DisableSideBarSuggestionHook(lpparam: PackageReadyParam) {
        DisableFloatingWindowBlacklistHook(lpparam.classLoader)
    }

    @JvmStatic
    fun NoFloatingWindowBlacklistHook(lpparam: SystemServerStartingParam) {
        MainModule.resHooks.setThemeValueReplacement("android", "string-array", "freeform_black_list", ResourceConstants.module_resize_black_list)
        DisableFloatingWindowBlacklistHook(lpparam.classLoader)
        ModuleHelper.findAndHookMethod("com.android.server.wm.MiuiFreeformUtilImpl", lpparam.classLoader, "supportsFreeform", HookerClassHelper.returnConstant(true))
    }

    private var freeformCallingPackage: String? = "SkipCheck"

    private var nextFreeformPackage: String? = ModuleHelper.NOT_EXIST_SYMBOL

    private fun shouldOpenInFreeForm(intent: Intent?, callingPackage: String?): Boolean {
        if (intent == null || intent.component == null) return false
        val fwBlackList = ArrayList<String>()
        fwBlackList.add("com.miui.home")
        fwBlackList.add("com.android.camera")
        fwBlackList.add("com.android.systemui")
        val pkgName = intent.component!!.packageName
        if (fwBlackList.contains(pkgName)) return false
        var openInFw = false
        val openFwWhenShare = MainModule.mPrefs.getBoolean("system_fw_forcein_actionsend")
        val compClassName = intent.component!!.className
        if (openFwWhenShare) {
            val whitelist = MainModule.mPrefs.getBoolean("system_fw_forcein_actionsend_in_whitelist")
            val appInList = MainModule.mPrefs.getStringSet("system_fw_forcein_actionsend_apps")?.contains(pkgName) == true
            if (whitelist xor appInList) {
                return false
            }
            if ("com.miui.packageinstaller" == pkgName && compClassName.contains("InstallPrepareAlertActivity")) {
                return true
            }
            if (Intent.ACTION_SEND == intent.action && pkgName != callingPackage) {
                openInFw = true
            } else if ("com.tencent.mm" == pkgName && compClassName.contains(".plugin.base.stub.WXEntryActivity")) {
                openInFw = true
            } else if ("com.tencent.mobileqq" == pkgName && (
                compClassName.contains(".activity.JumpActivity")
                || compClassName.contains(".activity.LoginActivity")
                || compClassName.contains(".agent.AgentActivity")
            )) {
                openInFw = true
            }
        }
        val openSettingFromSystemUI = MainModule.mPrefs.getBoolean("system_cc_freeform_when_longclick")
        if (openSettingFromSystemUI && "com.android.systemui" == callingPackage
            && ("com.android.settings" == pkgName
                || ("com.android.phone" == pkgName && compClassName.contains(".settings.MobileNetworkSettings"))
            )
        ) {
            openInFw = true
        }
        if (!openInFw) {
            openInFw = pkgName == nextFreeformPackage
        }
        return openInFw
    }

    @JvmStatic
    fun OpenAppInFreeFormHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.wm.ActivityTaskManagerService", lpparam.classLoader, "onSystemReady", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val intentFilter = IntentFilter()
                    intentFilter.addAction(GlobalActions.ACTION_PREFIX + "SetFreeFormPackage")
                    val mReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                            val action = intent.action
                            if (action == null) return@guarded

                            if (action == GlobalActions.ACTION_PREFIX + "SetFreeFormPackage") {
                                nextFreeformPackage = intent.getStringExtra("package") ?: ModuleHelper.NOT_EXIST_SYMBOL
                            }
                        }
                    }
                    ModuleHelper.registerModuleReceiver(mContext, "setFreeFormPackageReceiver", mReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.server.SecurityManagerService\$LocalService", lpparam.classLoader, "checkGameBoosterPayPassAsUser", String::class.java, Intent::class.java, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val args = chain.args

                    if (freeformCallingPackage == null || "SkipCheck" == freeformCallingPackage) {
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    if ("com.miui.packageinstaller" != freeformCallingPackage && freeformCallingPackage == args[0]) {
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    var openInFw = result as Boolean
                    if (!openInFw) {
                        val intent = args[1] as Intent
                        openInFw = shouldOpenInFreeForm(intent, freeformCallingPackage)
                    }
                    if (openInFw) {
                        nextFreeformPackage = ModuleHelper.NOT_EXIST_SYMBOL
                    }
                    result = openInFw; throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethods("com.android.server.wm.ActivityStarterImpl", lpparam.classLoader, "checkStartActivityByFreeForm", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {

                    if (args[1] != null) {
                        val safeOptions = args[7]
                        if (safeOptions != null) {
                            val ao = XposedHelpers.getObjectField(safeOptions, "mOriginalOptions") as ActivityOptions?
                            if (ao != null && XposedHelpers.getIntField(ao, "mLaunchWindowingMode") == 5) {
                                freeformCallingPackage = "SkipCheck"
                                return XposedHelpers.proceedOrThrow(chain, throwable)
                            }
                        }
                        freeformCallingPackage = args[6] as String
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun MultiWindowPlusHook(lpparam: SystemServerStartingParam) {
        MainModule.resHooks.setThemeValueReplacement("android", "string-array", "miui_resize_black_list", ResourceConstants.module_resize_black_list)
        val AtmClass = XposedHelpers.findClassIfExists("com.android.server.wm.ActivityTaskManagerServiceImpl", lpparam.classLoader)
        if (AtmClass != null) {
            ModuleHelper.findAndHookMethod(AtmClass, "updateResizeBlackList", Context::class.java, HookerClassHelper.DO_NOTHING)
            ModuleHelper.findAndHookMethod(AtmClass, "getSplitScreenBlackListFromXml", HookerClassHelper.DO_NOTHING)
            ModuleHelper.hookAllMethods(AtmClass, "inResizeBlackList", HookerClassHelper.returnConstant(false))
        }
    }

    @JvmStatic
    fun MultiWindowPlusHook(lpparam: PackageReadyParam) {
        if (lpparam.packageName == "com.miui.home") {
            ModuleHelper.findAndHookMethodSilently("com.android.systemui.shared.recents.model.Task", lpparam.classLoader, "isSupportSplit", HookerClassHelper.returnConstant(true))
            ModuleHelper.hookAllMethods("com.miui.home.recents.views.RecentMenuView", lpparam.classLoader, "onMessageEvent", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any?
                    var throwable: Throwable? = null
                    try {
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    try {
                        val thisObject = chain.thisObject

                        val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler
                        val oldMultiWindowEnableRunnable = XposedHelpers.getAdditionalInstanceField(thisObject, "multiWindowEnableRunnable") as Runnable?
                        if (oldMultiWindowEnableRunnable != null) mHandler.removeCallbacks(oldMultiWindowEnableRunnable)
                        val multiWindowEnableRunnable = Runnable {
                            ModuleHelper.guarded {
                                val mMenuItemMultiWindow = XposedHelpers.getObjectField(thisObject, "mMenuItemMultiWindow") as ImageView
                                val mMenuItemSmallWindow = XposedHelpers.getObjectField(thisObject, "mMenuItemSmallWindow") as ImageView
                                mMenuItemMultiWindow.isEnabled = true
                                mMenuItemMultiWindow.imageAlpha = 255
                                mMenuItemSmallWindow.isEnabled = true
                                mMenuItemSmallWindow.imageAlpha = 255
                            }
                        }
                        XposedHelpers.setAdditionalInstanceField(thisObject, "multiWindowEnableRunnable", multiWindowEnableRunnable)
                        mHandler.postDelayed(multiWindowEnableRunnable, 200)

                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        }
    }

    @JvmStatic
    fun AllowUntrustedTouchHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.wm.WindowState", lpparam.classLoader, "getTouchOcclusionMode", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mode = result as Int
                    if (mode == 1) { result = 2; throwable = null }
                    else {
                        val mAttrs = XposedHelpers.getObjectField(thisObject, "mAttrs") as WindowManager.LayoutParams
                        if (mAttrs.type == WindowManager.LayoutParams.TYPE_TOAST) {
                            result = 2; throwable = null
                        }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

}
