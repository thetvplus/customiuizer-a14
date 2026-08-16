package tv.withaibuild.customiuizer.mods

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.view.MotionEvent
import android.view.View
import java.util.HashMap
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import miui.security.SecurityManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Launcher hooks that belong to no larger surface.
 *
 * Gestures, icons and labels, folders, workspace layout and animations live in their
 * own `Launcher*Hooks` objects. What is left here is the recents screen, the navigation
 * bar, and [setupLauncher], the entry point MainModule calls once the Launcher
 * application object is ready.
 */
object Launcher {

    private const val MIUI_HOME_PACKAGE = "com.miui.home"

    @JvmStatic
    fun HideNavBarHook(lpparam: PackageReadyParam) {
        val showNavBar = booleanArrayOf(true)
        ModuleHelper.findAndHookMethod("com.miui.home.recents.NavStubView", lpparam.classLoader, "onSystemUiFlagsChanged", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    val flags = args[0] as Int
                    val newState = (flags and 2) == 0
                    if (newState != showNavBar[0]) {
                        showNavBar[0] = newState
                    }
                    args[0] = flags and -3

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.home.recents.views.RecentsContainer", lpparam.classLoader, "showLandscapeOverviewGestureView", Boolean::class.javaPrimitiveType!!, HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("com.miui.home.recents.NavStubView", lpparam.classLoader, "isImmersive", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    skipped = true
                    result = !showNavBar[0]
                    throwable = null

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.home.recents.NavStubView", lpparam.classLoader, "onPointerEvent", MotionEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    val mIsInFsMode = XposedHelpers.getBooleanField(thisObject, "mIsInFsMode")
                    if (!mIsInFsMode) {
                        val motionEvent = chain.getArg(0) as MotionEvent
                        if (motionEvent.action == 0) {
                            XposedHelpers.setObjectField(thisObject, "mHideGestureLine", true)
                        }
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.home.recents.NavStubView", lpparam.classLoader, "updateScreenSize", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    XposedHelpers.setObjectField(thisObject, "mHideGestureLine", false)

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
    fun FixAppInfoLaunchHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.miui.home.launcher.shortcuts.ShortcutMenuManager", lpparam.classLoader, "startAppDetailsActivity", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {

                    val component = XposedHelpers.callMethod(args[0], "getComponentName") as ComponentName?
                    if (component == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val view = args[1] as View?
                    if (view == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val userHandle = XposedHelpers.callMethod(args[0], "getUserHandle") as UserHandle?
                    ModuleHelper.openAppInfo(view.context, component.packageName, userHandle?.hashCode() ?: 0)
                    skipped = true
                    result = null
                    throwable = null

                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                result = chain.proceed()
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    @SuppressLint("SourceLockedOrientationActivity")
    fun ReverseLauncherPortraitHook(lpparam: PackageReadyParam) {
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

                    val act = thisObject as Activity
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun HideFromRecentsHook(lpparam: PackageReadyParam) {
        val ActivityManagerWrapper = XposedHelpers.findClassIfExists("com.android.systemui.shared.recents.system.ActivityManagerWrapper", lpparam.classLoader)
        val TaskInfoCompat = XposedHelpers.findClassIfExists("com.android.systemui.shared.recents.model.GroupedRecentTaskInfoCompat", lpparam.classLoader)
        if (TaskInfoCompat == null) {
            XposedHelpers.log("HideFromRecentsHook", "hook failed")
            return
        }
        ModuleHelper.findAndHookMethod(ActivityManagerWrapper!!, "needRemoveTask", TaskInfoCompat, object : MethodHook() {
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

                    if (chain.getArg(0) != null) {
                        val mainTask = XposedHelpers.getObjectField(chain.getArg(0), "mMainTaskInfo")
                        var componentName = XposedHelpers.getObjectField(mainTask, "topActivity") as ComponentName?
                        var pkgName: String? = null
                        if (componentName != null) {
                            pkgName = componentName.packageName
                        } else {
                            val baseIntent = XposedHelpers.getObjectField(mainTask, "baseIntent") as Intent?
                            if (baseIntent != null && baseIntent.component != null) {
                                pkgName = baseIntent.component!!.packageName
                            }
                        }
                        if (pkgName != null) {
                            val selectedApps = MainModule.mPrefs.getStringSet("system_hidefromrecents_apps")
                            if (selectedApps.contains(pkgName)) {
                                result = true
                                throwable = null
                            }
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
    fun RecentsBlurRatioHook(lpparam: PackageReadyParam) {
        val utilsClass = XposedHelpers.findClassIfExists("com.miui.home.launcher.common.BlurUtils", lpparam.classLoader)
        if (utilsClass == null) {
            XposedHelpers.log("RecentsBlurRatioHook", "Cannot find blur utility class")
            return
        }

        ModuleHelper.hookAllMethods(utilsClass, "fastBlurWhenEnterRecents", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {

                    val mIsFromFsGesture = XposedHelpers.getBooleanField(args[1], "mIsFromFsGesture")
                    if (!mIsFromFsGesture) {
                        val launcher = args[0] as Activity
                        val blurRatio = MainModule.mPrefs.getInt("system_recents_blur", 100) / 100f
                        XposedHelpers.callStaticMethod(utilsClass, "fastBlur", blurRatio, launcher.window, args[2])
                        skipped = true
                        result = null
                        throwable = null
                    }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllMethods(utilsClass, "fastBlurWhenGestureResetTaskView", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    XposedHelpers.setAdditionalStaticField(utilsClass, "customBlurRatio", true)

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethods(utilsClass, "fastBlur", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    if (args.size == 3) {
                        if (XposedHelpers.getAdditionalStaticField(utilsClass, "customBlurRatio") != null) {
                            val blurRatio = MainModule.mPrefs.getInt("system_recents_blur", 100) / 100f
                            args[0] = blurRatio
                            XposedHelpers.removeAdditionalStaticField(utilsClass, "customBlurRatio")
                        }
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

    @JvmStatic
    fun HideStatusBarInRecentsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.common.DeviceLevelUtils", lpparam.classLoader, "isHideStatusBarWhenEnterRecents", HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "keepStatusBarShowingForBetterPerformance", HookerClassHelper.returnConstant(false))
    }

    @JvmStatic
    fun isRecentsHideAppNameEnabled(prefs: PrefMap): Boolean {
        if (prefs.getBoolean("system_recents_card_style")) return true
        return prefs.getStringAsInt("system_recents_card_style", 0) == 1
    }

    @JvmStatic
    fun RecentsHideAppNameHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(
            "com.miui.home.recents.views.TaskView",
            lpparam.classLoader,
            "onFinishInflate",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val taskView = callback.getThisObject() as? View ?: return
                    try {
                        val resources = taskView.resources
                        val titleId = resources.getIdentifier("title", "id", MIUI_HOME_PACKAGE)
                        if (titleId != 0) taskView.findViewById<View>(titleId)?.visibility = View.GONE
                    } catch (t: Throwable) {
                        tv.withaibuild.customiuizer.mods.utils.FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("RecentsHideAppName", t)
                    }
                }
            }
        )
    }

    @JvmStatic
    fun DisableLauncherLogHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.miui.home.launcher.AnalyticalDataCollectorJobService", lpparam.classLoader, "onStartJob", HookerClassHelper.returnConstant(false))
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.AnalyticalDataCollector", lpparam.classLoader, "canTrackLaunchAppEvent", HookerClassHelper.returnConstant(false))
        val OneTrackInterfaceUtils = XposedHelpers.findClassIfExists("com.miui.home.launcher.common.OneTrackInterfaceUtils", lpparam.classLoader)
        if (OneTrackInterfaceUtils != null) {
            XposedHelpers.setStaticObjectField(OneTrackInterfaceUtils, "IS_ENABLE", false)
        }
    }

    @JvmStatic
    fun setupLauncher(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "registerBroadcastReceivers", object : MethodHook() {
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

                    val act = thisObject as Activity
                    val intentFilter = IntentFilter()
                    intentFilter.addAction(GlobalActions.EVENT_PREFIX + "FETCHAPPCONFIG")

                    val fetchAppConfigReceiver = ModuleHelper.registerOwnedReceiver(
                        act,
                        act,
                        "fetchAppConfigReceiver",
                        intentFilter,
                        Context.RECEIVER_EXPORTED,
                        GlobalActions.BROADCAST_PERMISSION
                    ) { receiver, _, context, intent ->
                        ModuleHelper.guarded {
                            if (intent.action == null) {
                                if (receiver.isOrderedBroadcast) receiver.setResultCode(GlobalActions.ACTION_FAILED)
                            } else if (ModuleHelper.isTrustedBroadcast(receiver, Helpers.modulePkg, rejectionResultCode = GlobalActions.ACTION_FAILED)) {
                                if ((GlobalActions.EVENT_PREFIX + "FETCHAPPCONFIG") == intent.action) {
                                    val pushIntent = Intent(GlobalActions.EVENT_PREFIX + "PUSHAPPCONFIG")
                                    pushIntent.setPackage(Helpers.modulePkg)
                                    val datatype = intent.getStringExtra("DATATYPE")
                                    pushIntent.putExtra("DATATYPE", datatype)
                                    if ("privacy" == datatype) {
                                        @Suppress("WrongConstant")
                                        val mSecurityManager = context.getSystemService("security") as SecurityManager
                                        val privacyAppsMap = HashMap<Int, MutableList<String>>()
                                        privacyAppsMap[0] = mSecurityManager.getAllPrivacyApps(0) as MutableList<String>
                                        privacyAppsMap[999] = mSecurityManager.getAllPrivacyApps(999) as MutableList<String>
                                        pushIntent.putExtra("privacyAppsMap", privacyAppsMap)
                                        ModuleHelper.sendBroadcastWithIdentity(context, pushIntent)
                                    } else if ("privacy_change" == datatype) {
                                        val userId = intent.getIntExtra("userId", 0)
                                        val pkgName = intent.getStringExtra("app")
                                        val privacy = intent.getBooleanExtra("privacy", false)
                                        @Suppress("WrongConstant")
                                        val mSecurityManager = context.getSystemService("security") as SecurityManager
                                        if (pkgName != null) mSecurityManager.setPrivacyApp(pkgName, userId, privacy)
                                        context.contentResolver.notifyChange(Uri.parse("content://com.miui.securitycenter.provider/update_privacyapps_icon"), null)
                                    }
                                }
                                if (receiver.isOrderedBroadcast) receiver.setResultCode(GlobalActions.ACTION_HANDLED)
                            }
                        }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onDestroy", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val act = chain.getThisObject() as Activity
                    ModuleHelper.unregisterOwnedReceiver(act, "secretCodeReceiver")
                    ModuleHelper.unregisterOwnedReceiver(act, "fetchAppConfigReceiver")
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }
}
