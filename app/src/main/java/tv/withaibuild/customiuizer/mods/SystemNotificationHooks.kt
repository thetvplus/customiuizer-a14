package tv.withaibuild.customiuizer.mods

import android.app.ActivityManager
import android.app.MiuiNotification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.os.UserHandle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.notificationautoexpand.NotificationAutoExpandHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.ArrayList
import java.util.HashSet
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Notification shade and heads-up popup hooks.
 * Expansion behaviour, heads-up lifetime and placement, per-app importance and
 * blocking, the row menu, and the icon limit in the status bar.
 */
object SystemNotificationHooks {

    internal data class NotificationSnapshot(
        val expandHeadsUp: Int = 1,
        val expandHeadsUpApps: Set<String> = emptySet(),
        val betterPopupsDelayMs: Int = 5000,
        val maxSbIcons: Int = 0,
    )

    @Volatile
    internal var notificationConfig = NotificationSnapshot()

    private var notificationObserverRegistered = false

    private val NOTIFICATION_PREF_KEYS = setOf(
        "system_expandheadups",
        "system_expandheadups_apps",
        "system_betterpopups_delay",
        "system_maxsbicons",
    )

    internal fun refreshNotificationSnapshot() {
        val prefs = MainModule.mPrefs
        var delay = prefs.getInt("system_betterpopups_delay", 0) * 1000
        if (delay == 0) delay = 5000
        notificationConfig = NotificationSnapshot(
            expandHeadsUp = prefs.getStringAsInt("system_expandheadups", 1),
            expandHeadsUpApps = HashSet(prefs.getStringSet("system_expandheadups_apps")),
            betterPopupsDelayMs = delay,
            maxSbIcons = prefs.getStringAsInt("system_maxsbicons", 0),
        )
    }

    @JvmStatic
    internal fun installNotificationSnapshot() {
        refreshNotificationSnapshot()
        if (notificationObserverRegistered) return
        notificationObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in NOTIFICATION_PREF_KEYS) refreshNotificationSnapshot()
            }
        })
    }

    @JvmStatic
    fun ExpandNotificationsHook(lpparam: PackageReadyParam) {
        NotificationAutoExpandHook.install(lpparam.classLoader)
    }

    @JvmStatic
    fun ExpandHeadsUpHook(lpparam: PackageReadyParam) {
        installNotificationSnapshot()
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.classLoader, "setHeadsUp", object : MethodHook() {
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

                    val mOnKeyguard = XposedHelpers.getBooleanField(thisObject, "mOnKeyguard")
                    val showHeadsUp = chain.getArg(0) as Boolean
                    if (!mOnKeyguard && showHeadsUp) {
                        val notifyRow = thisObject as View
                        val notification = XposedHelpers.getObjectField(XposedHelpers.callMethod(thisObject, "getEntry"), "mSbn")
                        val pkgName = XposedHelpers.callMethod(notification, "getPackageName") as String
                        val opt = notificationConfig.expandHeadsUp
                        val isSelected = pkgName in notificationConfig.expandHeadsUpApps
                        if ((opt == 2 && !isSelected) || (opt == 3 && isSelected)) {
                            val oldExpandNotify = XposedHelpers.getAdditionalInstanceField(thisObject, "expandNotifyRunnable") as Runnable?
                            if (oldExpandNotify != null) notifyRow.removeCallbacks(oldExpandNotify)
                            val expandNotify = Runnable {
                                ModuleHelper.guarded {
                                    val mExpandClickListener = XposedHelpers.getObjectField(thisObject, "mExpandClickListener") as View.OnClickListener
                                    mExpandClickListener.onClick(notifyRow)
                                }
                            }
                            XposedHelpers.setAdditionalInstanceField(thisObject, "expandNotifyRunnable", expandNotify)
                            notifyRow.postDelayed(expandNotify, 60)
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
    fun BetterPopupsHideDelayHook(lpparam: PackageReadyParam) {
        // Preflight: resolve required ROM class and fields before installing
        // any hook. If the ROM contract is missing, throw so FeatureInstallRegistry
        // catches and marks the feature FAILED_TRANSIENT instead of silently
        // installing zero hooks (false success).
        installNotificationSnapshot()
        val headsUpManagerClass = XposedHelpers.findClass("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader)
        XposedHelpers.findField(headsUpManagerClass, "mMinimumDisplayTime")
        XposedHelpers.findField(headsUpManagerClass, "mHeadsUpNotificationDecay")

        ModuleHelper.findAndHookMethodSilently(MiuiNotification::class.java, "getFloatTime", HookerClassHelper.returnConstant(0))
        ModuleHelper.hookAllConstructors(headsUpManagerClass, object : MethodHook() {
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

                    val delay = notificationConfig.betterPopupsDelayMs
                    XposedHelpers.setIntField(thisObject, "mMinimumDisplayTime", delay)
                    XposedHelpers.setIntField(thisObject, "mHeadsUpNotificationDecay", delay)
                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) = ModuleHelper.guarded {
                            if (key == "system_betterpopups_delay") {
                                refreshNotificationSnapshot()
                                val delay2 = notificationConfig.betterPopupsDelayMs
                                XposedHelpers.setIntField(thisObject, "mMinimumDisplayTime", delay2)
                                XposedHelpers.setIntField(thisObject, "mHeadsUpNotificationDecay", delay2)
                            }
                        }
                    }, thisObject)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun BetterPopupsNoHideHook(lpparam: PackageReadyParam) {
        // Preflight: resolve all required ROM classes and methods before installing
        // any hook. If the ROM contract is missing, throw so FeatureInstallRegistry
        // catches and marks the feature FAILED_TRANSIENT instead of silently
        // installing zero hooks (false success).
        XposedHelpers.findClass("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader)
        XposedHelpers.findClass("com.android.systemui.statusbar.policy.HeadsUpManager\$HeadsUpEntry", lpparam.classLoader)

        XposedHelpers.findMethodExact("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "removeHeadsUpNotification")
        XposedHelpers.findMethodExact("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "removeOldHeadsUpNotification")
        XposedHelpers.findMethodExact("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "onExpandingFinished")
        XposedHelpers.findMethodExact("com.android.systemui.statusbar.policy.HeadsUpManager\$HeadsUpEntry", lpparam.classLoader, "updateEntry", Boolean::class.javaPrimitiveType!!)

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "removeHeadsUpNotification", HookerClassHelper.DO_NOTHING)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "removeOldHeadsUpNotification", HookerClassHelper.DO_NOTHING)

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManager\$HeadsUpEntry", lpparam.classLoader, "updateEntry", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    XposedHelpers.setObjectField(thisObject, "mRemoveHeadsUpRunnable", Runnable { })

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "onExpandingFinished", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    XposedHelpers.setBooleanField(thisObject, "mReleaseOnExpandFinish", true)

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
    fun NotificationRowMenuHook(lpparam: PackageReadyParam) {
        val appInfoIconResId = MainModule.resHooks.addFakeResource("ic_appinfo", R.drawable.ic_appinfo12, "drawable")
        val forceCloseIconResId = MainModule.resHooks.addFakeResource("ic_forceclose", R.drawable.ic_forceclose12, "drawable")
        val openInFwIconResId = MainModule.resHooks.addFakeResource("ic_openinfw", R.drawable.ic_openinfw, "drawable")
        val appInfoDescId = MainModule.resHooks.addFakeResource("miui_notification_menu_appinfo_title", R.string.system_notifrowmenu_appinfo, "string")
        val forceCloseDescId = MainModule.resHooks.addFakeResource("miui_notification_menu_forceclose_title", R.string.system_notifrowmenu_forceclose, "string")
        val openInFwDescId = MainModule.resHooks.addFakeResource("miui_notification_menu_openinfw_title", R.string.system_notifrowmenu_openinfw, "string")
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "notification_menu_icon_padding", 0)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "miui_notification_modal_menu_margin_left_right", 3)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "miui_notification_modal_menu_icon_bg_size", 50)

        val MiuiNotificationMenuItem = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow.MiuiNotificationMenuItem", lpparam.classLoader)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow", lpparam.classLoader, "createMenuViews", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
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
                    val mMenuItems = XposedHelpers.getObjectField(thisObject, "mMenuItems") as ArrayList<Any>

                    var infoBtn: Any? = null
                    var forceCloseBtn: Any? = null
                    var openFwBtn: Any? = null
                    val MenuItem = MiuiNotificationMenuItem.constructors[0]
                    try {
                        infoBtn = MenuItem.newInstance(mContext, appInfoDescId, null, appInfoIconResId)
                        forceCloseBtn = MenuItem.newInstance(mContext, forceCloseDescId, null, forceCloseIconResId)
                        openFwBtn = MenuItem.newInstance(mContext, openInFwDescId, null, openInFwIconResId)
                    } catch (t1: Throwable) {
                        XposedHelpers.log(t1)
                    }
                    if (infoBtn == null || forceCloseBtn == null || openFwBtn == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val notification = XposedHelpers.getObjectField(thisObject, "mSbn")
                    mMenuItems.add(infoBtn)
                    mMenuItems.add(forceCloseBtn)
                    mMenuItems.add(openFwBtn)
                    val menuMargin = XposedHelpers.getObjectField(thisObject, "mMenuMargin") as Int
                    val mMenuContainer = XposedHelpers.getObjectField(thisObject, "mMenuContainer") as LinearLayout
                    val pkgName = XposedHelpers.callMethod(notification, "getPackageName") as String
                    val mInfoBtn = XposedHelpers.callMethod(infoBtn, "getMenuView") as View
                    var mForceCloseBtn: View? = null
                    if (pkgName != "android") {
                        mForceCloseBtn = XposedHelpers.callMethod(forceCloseBtn, "getMenuView") as View
                    }
                    val mOpenFwBtn = XposedHelpers.callMethod(openFwBtn, "getMenuView") as View
                    val expandNotifyRow = XposedHelpers.getObjectField(thisObject, "mParent")
                    val itemClick = View.OnClickListener { view ->
                        ModuleHelper.guarded {
                            if (view == null) return@OnClickListener
                            val uid = XposedHelpers.getIntField(notification, "mAppUid")

                            if (view == mInfoBtn || view == mForceCloseBtn) {
                                val user = resolveNotificationUserId {
                                    XposedHelpers.callStaticMethod(UserHandle::class.java, "getUserId", uid) as Int
                                } ?: return@OnClickListener

                                if (view == mInfoBtn) {
                                    ModuleHelper.openAppInfo(mContext, pkgName, user)
                                } else {
                                    val am = mContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                                    if (user != 0)
                                        XposedHelpers.callMethod(am, "forceStopPackageAsUser", pkgName, user)
                                    else
                                        XposedHelpers.callMethod(am, "forceStopPackage", pkgName)
                                    ModuleHelper.guarded {
                                        val appName = mContext.packageManager.getApplicationLabel(mContext.packageManager.getApplicationInfo(pkgName, 0))
                                        Toast.makeText(mContext, ModuleHelper.getModuleRes(mContext).getString(R.string.force_closed, appName), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else if (view == mOpenFwBtn) {
                                val miniWindowPkg = XposedHelpers.callMethod(expandNotifyRow, "getMiniWindowTargetPkg") as String
                                val notifyIntent = XposedHelpers.callMethod(expandNotifyRow, "getPendingIntent") as PendingIntent
                                try {
                                    val options = ModuleHelper.getFreeformOptions(mContext, miniWindowPkg, notifyIntent, true)
                                    notifyIntent.send(mContext, 0, ModuleHelper.getFreeformIntent(miniWindowPkg), null, null, null, options)
                                } catch (e: PendingIntent.CanceledException) {
                                    throw RuntimeException(e)
                                }
                            }
                            val ModalControllerForDep = "com.android.systemui.statusbar.notification.modal.ModalController"
                            val ModalController = ModuleHelper.getDepInstance(lpparam.classLoader, ModalControllerForDep)
                            XposedHelpers.callMethod(ModalController, "animExitModal", "OTHER")
                            val mCommandQueue = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.CommandQueue")
                            XposedHelpers.callMethod(mCommandQueue, "animateCollapsePanels", 0, false)
                        }
                    }
                    mInfoBtn.setOnClickListener(itemClick)
                    mOpenFwBtn.setOnClickListener(itemClick)
                    val layoutParams = LinearLayout.LayoutParams(-2, -2)
                    layoutParams.leftMargin = menuMargin * 2
                    layoutParams.rightMargin = menuMargin * 2
                    mMenuContainer.addView(mInfoBtn)
                    if (mForceCloseBtn != null) {
                        mForceCloseBtn.setOnClickListener(itemClick)
                        mMenuContainer.addView(mForceCloseBtn)
                    }
                    mMenuContainer.addView(mOpenFwBtn)
                    val titleId = HookUtils.getResId(mContext.resources, "modal_menu_title", "id", "com.android.systemui")
                    val panelWidth = mContext.resources.displayMetrics.widthPixels
                    val menuWidth = (panelWidth / mMenuItems.size) - (menuMargin * 2)
                    mMenuItems.forEach { obj ->
                        val menuView = XposedHelpers.callMethod(obj, "getMenuView") as View
                        menuView.layoutParams = layoutParams
                        menuView.findViewById<TextView>(titleId)?.maxWidth = menuWidth
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    /**
     * Resolves a notification's user id for cross-user actions.
     *
     * Fatal errors are unwrapped and re-thrown. Non-fatal resolution failures
     * are logged and returned as `null` so the caller can abort the action
     * instead of falling back to user 0.
     */
    internal fun resolveNotificationUserId(resolver: () -> Int): Int? {
        return try {
            resolver()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    @JvmStatic
    fun DisableAnyNotificationBlockHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("android.app.NotificationChannel", lpparam.classLoader, "isBlockable", HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("android.app.NotificationChannel", lpparam.classLoader, "setBlockable", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
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
    }

    @JvmStatic
    fun DisableAnyNotificationBlockHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("android.app.NotificationChannel", lpparam.classLoader, "isBlockable", HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("android.app.NotificationChannel", lpparam.classLoader, "setBlockable", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
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
    }

    @JvmStatic
    fun DisableAnyNotificationHook(lpparam: PackageReadyParam) {
        if (lpparam.packageName.contains("systemui")) {
            val NotifyManagerCls = XposedHelpers.findClass("com.android.systemui.statusbar.notification.NotificationSettingsManager", lpparam.classLoader)
            XposedHelpers.setStaticBooleanField(NotifyManagerCls, "USE_WHITE_LISTS", false)
        }
        ModuleHelper.hookAllMethods("miui.util.NotificationFilterHelper", lpparam.classLoader, "isNotificationForcedEnabled", HookerClassHelper.returnConstant(false))
        ModuleHelper.findAndHookMethod("miui.util.NotificationFilterHelper", lpparam.classLoader, "isNotificationForcedFor", Context::class.java, String::class.java, HookerClassHelper.returnConstant(false))
        ModuleHelper.findAndHookMethod("miui.util.NotificationFilterHelper", lpparam.classLoader, "canSystemNotificationBeBlocked", String::class.java, HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("miui.util.NotificationFilterHelper", lpparam.classLoader, "containNonBlockableChannel", String::class.java, HookerClassHelper.returnConstant(false))
        ModuleHelper.findAndHookMethod("miui.util.NotificationFilterHelper", lpparam.classLoader, "getNotificationForcedEnabledList", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    skipped = true
                    result = HashSet<String>()
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
    }

    /**
     * Android 14 Settings disables its master switch in BlockPreferenceController by calling
     * these four local policy methods. HyperOS' NotificationFilterHelper hooks alone therefore
     * cannot unlock the visible switch for a system app. Keep the override inside Settings and
     * behind the existing "disable any notification" preference.
     */
    @JvmStatic
    fun UnlockSettingsNotificationControlsHook(lpparam: PackageReadyParam) {
        val controller = XposedHelpers.findClassIfExists(
            "com.android.settings.notification.app.NotificationPreferenceController",
            lpparam.classLoader
        ) ?: return
        val allow = HookerClassHelper.returnConstant(true)
        ModuleHelper.hookAllMethods(controller, "isAppBlockable", allow)
        ModuleHelper.hookAllMethods(controller, "isChannelBlockable", allow)
        ModuleHelper.hookAllMethods(controller, "isChannelConfigurable", allow)
        ModuleHelper.hookAllMethods(controller, "isChannelGroupBlockable", allow)

        ModuleHelper.hookAllMethods(
            "com.android.settings.notification.app.AppNotificationSettings",
            lpparam.classLoader,
            "setupBlock",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    try {
                        val settings = callback.getThisObject() ?: return
                        val block = XposedHelpers.getObjectField(settings, "mBlock") ?: return
                        // Settings owns its own androidx.preference classes. Never cast that
                        // object to the module APK's same-named class across ClassLoaders.
                        XposedHelpers.callMethod(block, "setEnabled", true)
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("UnlockSettingsNotificationControls", t)
                    }
                }
            }
        )
    }

    @JvmStatic
    fun NotificationImportanceHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.settings.notification.BaseNotificationSettings", lpparam.classLoader, "setPrefVisible", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    val pref = args[0]
                    if (pref != null) {
                        val prefKey = XposedHelpers.callMethod(pref, "getKey") as String?
                        if (prefKey == "importance") {
                            args[1] = true
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
        ModuleHelper.findAndHookMethod("com.android.settings.notification.ChannelNotificationSettings", lpparam.classLoader, "setupChannelDefaultPrefs", object : MethodHook() {
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

                    val pref = XposedHelpers.callMethod(thisObject, "findPreference", "importance")
                    XposedHelpers.setObjectField(thisObject, "mImportance", pref)
                    val mBackupImportance = XposedHelpers.getObjectField(thisObject, "mBackupImportance") as Int
                    if (mBackupImportance > 0) {
                        val index = XposedHelpers.callMethod(pref, "findSpinnerIndexOfValue", mBackupImportance.toString()) as Int
                        if (index > -1) {
                            XposedHelpers.callMethod(pref, "setValueIndex", index)
                        }
                        val ImportanceListener = XposedHelpers.findClassIfExists("androidx.preference.Preference\$OnPreferenceChangeListener", lpparam.classLoader)
                            ?: return XposedHelpers.throwOrReturn(throwable, result)
                        val handler = InvocationHandler { _, method, args2 ->
                            if (method.name == "onPreferenceChange") {
                                val mBackupImportance2 = Integer.parseInt(args2[1] as String)
                                XposedHelpers.setObjectField(thisObject, "mBackupImportance", mBackupImportance2)
                                val mChannel = XposedHelpers.getObjectField(thisObject, "mChannel") as NotificationChannel
                                mChannel.importance = mBackupImportance2
                                XposedHelpers.callMethod(mChannel, "lockFields", 4)
                                val mBackend = XposedHelpers.getObjectField(thisObject, "mBackend")
                                val mPkg = XposedHelpers.getObjectField(thisObject, "mPkg") as String
                                val mUid = XposedHelpers.getObjectField(thisObject, "mUid") as Int
                                XposedHelpers.callMethod(mBackend, "updateChannel", mPkg, mUid, mChannel)
                                XposedHelpers.callMethod(thisObject, "updateDependents", false)
                            }
                            true
                        }
                        val mImportanceListener = Proxy.newProxyInstance(
                            lpparam.classLoader,
                            arrayOf(ImportanceListener),
                            handler
                        )
                        XposedHelpers.callMethod(pref, "setOnPreferenceChangeListener", mImportanceListener)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun MaxNotificationIconsHook(lpparam: PackageReadyParam) {
        installNotificationSnapshot()
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.NotificationIconContainer", lpparam.classLoader, "resetViewStates", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    var opt = notificationConfig.maxSbIcons
                    val maxIcons = XposedHelpers.getIntField(thisObject, "mMaxStaticIcons")
                    opt = if (opt == -1) 999 else opt
                    if (opt != maxIcons && maxIcons != 0) {
                        XposedHelpers.setIntField(thisObject, "mMaxStaticIcons", opt)
                        XposedHelpers.setIntField(thisObject, "mMaxIconsOnLockscreen", opt)
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
    fun AutoDismissExpandedPopupsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.HeadsUpManagerPhone\$HeadsUpEntryPhone", lpparam.classLoader, "updateEntry", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
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

                    val headsUpEntry = thisObject
                    val expanded = XposedHelpers.getBooleanField(headsUpEntry, "expanded")
                    val remoteInputActive = XposedHelpers.getBooleanField(headsUpEntry, "remoteInputActive")
                    val mEntry = XposedHelpers.getObjectField(headsUpEntry, "mEntry")
                    val rowPinned = XposedHelpers.callMethod(mEntry, "isRowPinned") as Boolean
                    if (expanded && rowPinned && !remoteInputActive) {
                        val headsUpManagerPhone = XposedHelpers.getSurroundingThis(headsUpEntry)
                        val mHandler = XposedHelpers.getObjectField(headsUpManagerPhone, "mHandler") as Handler
                        val mRemoveAlertRunnable = XposedHelpers.getObjectField(headsUpEntry, "mRemoveAlertRunnable") as Runnable
                        val extended = XposedHelpers.getBooleanField(headsUpEntry, "extended")
                        mHandler.removeCallbacks(mRemoveAlertRunnable)
                        mHandler.postDelayed(mRemoveAlertRunnable, if (extended) 10000L else 4500L)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarNotificationPresenter", lpparam.classLoader, "onExpandClicked", object : MethodHook() {
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
                    val args = chain.args

                    val expanded = args[1] as Boolean
                    val mKeyguardStateController = XposedHelpers.getObjectField(thisObject, "mKeyguardStateController")
                    val mShowing = XposedHelpers.getBooleanField(mKeyguardStateController, "mShowing")
                    if (expanded && !mShowing) {
                        val headsUpManagerPhone = XposedHelpers.getObjectField(thisObject, "mHeadsUpManager")
                        val headsUpEntry = XposedHelpers.callMethod(headsUpManagerPhone, "getHeadsUpEntry", XposedHelpers.getObjectField(args[0], "mKey"))
                        if (headsUpEntry != null) {
                            val isRowPinned = XposedHelpers.callMethod(args[0], "isRowPinned") as Boolean
                            if (isRowPinned) {
                                val mHandler = XposedHelpers.getObjectField(headsUpManagerPhone, "mHandler") as Handler
                                val mRemoveAlertRunnable = XposedHelpers.getObjectField(headsUpEntry, "mRemoveAlertRunnable") as Runnable
                                mHandler.removeCallbacks(mRemoveAlertRunnable)
                                mHandler.postDelayed(mRemoveAlertRunnable, 4500L)
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
    fun MinimalNotificationViewHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBar", lpparam.classLoader, "updateNotification", object : MethodHook() {
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
                    val args = chain.args

                    if (args.size != 3) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val expandableRow = XposedHelpers.getObjectField(args[0], "row")
                    val mNotificationData = XposedHelpers.getObjectField(thisObject, "mNotificationData")
                    val newLowPriority = XposedHelpers.callMethod(mNotificationData, "isAmbient", XposedHelpers.callMethod(args[1], "getKey")) as Boolean && !(XposedHelpers.callMethod(XposedHelpers.callMethod(args[1], "getNotification"), "isGroupSummary") as Boolean)
                    val hasEntry = XposedHelpers.callMethod(mNotificationData, "get", XposedHelpers.getObjectField(args[0], "key")) != null
                    val isLowPriority = XposedHelpers.callMethod(expandableRow, "isLowPriority") as Boolean
                    XposedHelpers.callMethod(expandableRow, "setIsLowPriority", newLowPriority)
                    val hasLowPriorityChanged = hasEntry && isLowPriority != newLowPriority
                    XposedHelpers.callMethod(expandableRow, "setLowPriorityStateUpdated", hasLowPriorityChanged)
                    XposedHelpers.callMethod(expandableRow, "updateNotification", args[0])

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun NotificationChannelSettingsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow", lpparam.classLoader, "createMenuViews", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
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

                    val entry = XposedHelpers.callMethod(XposedHelpers.getObjectField(thisObject, "mParent"), "getEntry")
                    val channelId = XposedHelpers.callMethod(XposedHelpers.callMethod(entry, "getChannel"), "getId") as String
                    if ("miscellaneous" == channelId) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val notification = XposedHelpers.getObjectField(entry, "mSbn")
                    val nuCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.NotificationUtil", lpparam.classLoader)
                    val isHybrid = if (nuCls != null) XposedHelpers.callStaticMethod(nuCls, "isHybrid", notification) as Boolean else false
                    if (isHybrid) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mInfoItem = XposedHelpers.getObjectField(thisObject, "mInfoItem")
                    val mIcon = XposedHelpers.getObjectField(mInfoItem, "mIcon") as ImageView
                    mIcon.setOnClickListener(View.OnClickListener {
                        ModuleHelper.guarded {
                            val bundle = Bundle()
                            bundle.putString("android.provider.extra.CHANNEL_ID", channelId)
                            val pkgName = XposedHelpers.callMethod(notification, "getPackageName") as String
                            bundle.putString("package", pkgName)
                            val appUid = XposedHelpers.getIntField(notification, "mAppUid")
                            bundle.putInt("uid", appUid)
                            bundle.putString("miui.targetPkg", pkgName)
                            val intent = Intent("android.intent.action.MAIN")
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.putExtra(":android:show_fragment", "com.android.settings.notification.ChannelNotificationSettings")
                            intent.putExtra(":android:show_fragment_args", bundle)
                            intent.setClassName("com.android.settings", "com.android.settings.SubSettings")
                            XposedHelpers.callMethod(mContext, "startActivityAsUser", intent, XposedHelpers.getStaticObjectField(UserHandle::class.java, "CURRENT"))
                            val modalController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.notification.modal.ModalController")
                            XposedHelpers.callMethod(modalController, "animExitModal", 50L, true, "MORE", false)
                            val statusBar = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.CommandQueue")
                            XposedHelpers.callMethod(statusBar, "animateCollapsePanels", 0, false)
                        }
                    })

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun MuteVisibleNotificationsHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.policy.MiuiAlertManager", lpparam.classLoader, "buzzBeepBlink", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val powerMgr = mContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (powerMgr.isInteractive) {
                        skipped = true; result = null; throwable = null
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
    }

    @JvmStatic
    fun BetterPopupsCenteredHook(lpparam: PackageReadyParam) {
        val coreUnhooker = ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManagerInjector", lpparam.classLoader, "miuiHeadsUpInset", Context::class.java, object : MethodHook() {
            private var mHeadsUpPaddingTop = 0
            private var mHeadsUpHeight = 0
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

                    val context = chain.getArg(0) as Context
                    val resources = context.resources
                    if (mHeadsUpPaddingTop == 0) {
                        val dimId = HookUtils.getResId(resources, "heads_up_status_bar_padding", "dimen", "com.android.systemui")
                        mHeadsUpPaddingTop = resources.getDimensionPixelSize(dimId)
                        mHeadsUpHeight = resources.getDimensionPixelSize(HookUtils.getResId(resources, "notification_max_heads_up_height", "dimen", "com.android.systemui"))
                    }
                    if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                        val mHeadsUpInset = result as Int
                        val mStatusBarHeight = mHeadsUpInset - mHeadsUpPaddingTop
                        val topMargin = (context.resources.displayMetrics.heightPixels + mStatusBarHeight - mHeadsUpHeight) / 2
                        result = topMargin; throwable = null
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        checkNotNull(coreUnhooker) {
            "Required BetterPopupsCentered hook was not installed: HeadsUpManagerInjector.miuiHeadsUpInset"
        }
    }

}
