package tv.withaibuild.customiuizer.mods

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
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
 * Expansion behaviour, heads-up placement, per-app importance and
 * blocking, the row menu, and the icon limit in the status bar.
 */
object SystemNotificationHooks {

    internal data class NotificationSnapshot(
        val expandHeadsUp: Int = 1,
        val expandHeadsUpApps: Set<String> = emptySet(),
        val maxSbIcons: Int = 0,
    )

    @Volatile
    internal var notificationConfig = NotificationSnapshot()

    private var notificationObserverRegistered = false

    private val NOTIFICATION_PREF_KEYS = setOf(
        "system_expandheadups",
        "system_expandheadups_apps",
        "system_maxsbicons",
    )

    internal fun refreshNotificationSnapshot() {
        val prefs = MainModule.mPrefs
        notificationConfig = NotificationSnapshot(
            expandHeadsUp = prefs.getStringAsInt("system_expandheadups", 1),
            expandHeadsUpApps = HashSet(prefs.getStringSet("system_expandheadups_apps")),
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
        val row = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.classLoader)
        val expansion = HeadsUpExpansion(row) { pkg ->
            val snapshot = notificationConfig
            (snapshot.expandHeadsUp == 2 && pkg !in snapshot.expandHeadsUpApps) ||
                (snapshot.expandHeadsUp == 3 && pkg in snapshot.expandHeadsUpApps)
        }
        ModuleHelper.findAndHookMethod(row, "setHeadsUp", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                ModuleHelper.guarded { expansion.update(chain.thisObject as View) }
                return result
            }
        })
        val cancel = object : MethodHook() {
            override fun before(callback: tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback) {
                expansion.cancel(callback.getThisObject() as View)
            }
        }
        ModuleHelper.findAndHookMethod(row, "setEntry",
            XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", lpparam.classLoader), cancel)
        ModuleHelper.findAndHookMethod(row, "setRemoved", cancel)
    }

    @JvmStatic
    fun NotificationRowMenuHook(lpparam: PackageReadyParam) {
        val menuItemClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow.MiuiNotificationMenuItem", lpparam.classLoader)
        val menuItemConstructor = XposedHelpers.findConstructorExact(
            menuItemClass, Context::class.java, Int::class.javaPrimitiveType!!,
            XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.NotificationSnooze", lpparam.classLoader),
            Int::class.javaPrimitiveType!!,
        )
        val appInfoIconResId = MainModule.resHooks.addFakeResource("ic_appinfo", R.drawable.ic_appinfo12, "drawable")
        val forceCloseIconResId = MainModule.resHooks.addFakeResource("ic_forceclose", R.drawable.ic_forceclose12, "drawable")
        val openInFwIconResId = MainModule.resHooks.addFakeResource("ic_openinfw", R.drawable.ic_openinfw, "drawable")
        val appInfoDescId = MainModule.resHooks.addFakeResource("miui_notification_menu_appinfo_title", R.string.system_notifrowmenu_appinfo, "string")
        val forceCloseDescId = MainModule.resHooks.addFakeResource("miui_notification_menu_forceclose_title", R.string.system_notifrowmenu_forceclose, "string")
        val openInFwDescId = MainModule.resHooks.addFakeResource("miui_notification_menu_openinfw_title", R.string.system_notifrowmenu_openinfw, "string")
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "notification_menu_icon_padding", 0)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "miui_notification_modal_menu_margin_left_right", 3)
        MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "miui_notification_modal_menu_icon_bg_size", 50)

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow", lpparam.classLoader, "createMenuViews", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    val thisObject = chain.thisObject

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val mMenuItems = XposedHelpers.getObjectField(thisObject, "mMenuItems") as ArrayList<Any>

                    var infoBtn: Any? = null
                    var forceCloseBtn: Any? = null
                    var openFwBtn: Any? = null
                    try {
                        infoBtn = menuItemConstructor.newInstance(mContext, appInfoDescId, null, appInfoIconResId)
                        forceCloseBtn = menuItemConstructor.newInstance(mContext, forceCloseDescId, null, forceCloseIconResId)
                        openFwBtn = menuItemConstructor.newInstance(mContext, openInFwDescId, null, openInFwIconResId)
                    } catch (t1: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t1)
                        XposedHelpers.log(t1)
                    }
                    if (infoBtn == null || forceCloseBtn == null || openFwBtn == null) { return result }
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
                        try {
                            if (view == null) return@OnClickListener
                            // A modal may outlive its binding. Never act on its old notification.
                            if (XposedHelpers.getObjectField(thisObject, "mSbn") !== notification ||
                                XposedHelpers.getObjectField(XposedHelpers.callMethod(expandNotifyRow, "getEntry"), "mSbn") !== notification) return@OnClickListener
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
                                val keyguard = mContext.getSystemService(KeyguardManager::class.java)
                                val user = uid / 100000
                                if (!view.isEnabled || keyguard == null || keyguard.isKeyguardLocked || !notifyIntent.isActivity ||
                                    user != ModuleHelper.getUserId() || notifyIntent.creatorUid / 100000 != user) return@OnClickListener
                                try {
                                    val options = ModuleHelper.getFreeformOptions(mContext, miniWindowPkg, notifyIntent, true)
                                        ?: return@OnClickListener
                                    view.isEnabled = false
                                    notifyIntent.send(mContext, 0, ModuleHelper.getFreeformIntent(miniWindowPkg), null, null, null, options)
                                } catch (e: PendingIntent.CanceledException) {
                                    view.isEnabled = true
                                    throw e
                                }
                            }
                            val ModalControllerForDep = "com.android.systemui.statusbar.notification.modal.ModalController"
                            val ModalController = ModuleHelper.getDepInstance(lpparam.classLoader, ModalControllerForDep)
                            XposedHelpers.callMethod(ModalController, "animExitModal", "OTHER")
                            val mCommandQueue = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.CommandQueue")
                            XposedHelpers.callMethod(mCommandQueue, "animateCollapsePanels", 0, false)
                        } catch (t: Throwable) {
                            FatalErrors.unwrapAndRethrowIfFatal(t)
                            XposedHelpers.log("NotificationRowMenu", t)
                        }
                    }
                    mInfoBtn.setOnClickListener(itemClick)
                    mOpenFwBtn.setOnClickListener(itemClick)
                    val layoutParams = LinearLayout.LayoutParams(-2, -2)
                    // Match the ROM's per-side margin; doubling it clips the sixth action.
                    layoutParams.leftMargin = menuMargin
                    layoutParams.rightMargin = menuMargin
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
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log("NotificationRowMenu", t)
                }
                return result
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

    /** UI-only MIUI restrictions, only for an ordinary app in the current user. */
    internal fun ordinaryNotificationTarget(uid: Int, flags: Int, user: Int): Boolean =
        uid % 100000 in 10000..19999 && uid / 100000 == user &&
            flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

    internal fun ordinaryNotificationTarget(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return ModuleHelper.guarded(false) {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            ordinaryNotificationTarget(info.uid, info.flags, ModuleHelper.getUserId())
        }
    }

    @JvmStatic
    fun DisableAnyNotificationHook(lpparam: PackageReadyParam) {
        // Never mutate NotificationChannel, forced-enabled lists, USE_WHITE_LISTS or NMS policy.
        val filter = XposedHelpers.findClass("miui.util.NotificationFilterHelper", lpparam.classLoader)
        val uiDecision = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val original = chain.proceed()
                val context = chain.getArg(0) as? Context ?: return original
                return if (ordinaryNotificationTarget(context, chain.getArg(1) as? String)) false else original
            }
        }
        ModuleHelper.findAndHookMethod(filter, "isNotificationForcedEnabled", Context::class.java, String::class.java, uiDecision)
        ModuleHelper.findAndHookMethod(filter, "isNotificationForcedEnabled", Context::class.java, String::class.java, String::class.java, uiDecision)
        ModuleHelper.findAndHookMethod(filter, "isNotificationForcedFor", Context::class.java, String::class.java, uiDecision)
    }

    @JvmStatic
    fun NotificationImportanceHook(lpparam: PackageReadyParam) {
        val loader = lpparam.classLoader
        val baseClass = XposedHelpers.findClass("com.android.settings.notification.BaseNotificationSettings", loader)
        val preferenceClass = XposedHelpers.findClass("androidx.preference.Preference", loader)
        val listenerClass = XposedHelpers.findClass("androidx.preference.Preference\$OnPreferenceChangeListener", loader)
        val listenerInterfaces = arrayOf(listenerClass)
        val getKey = XposedHelpers.findMethodExact(preferenceClass, "getKey", *emptyArray<Any>())
        val findPreference = XposedHelpers.findMethodBestMatch(baseClass, "findPreference", CharSequence::class.java)
        val setListener = XposedHelpers.findMethodExact(preferenceClass, "setOnPreferenceChangeListener", listenerClass)
        val importanceField = XposedHelpers.findField(baseClass, "mImportance")
        val backupImportanceField = XposedHelpers.findField(baseClass, "mBackupImportance")
        val channelField = XposedHelpers.findField(baseClass, "mChannel")
        val backendField = XposedHelpers.findField(baseClass, "mBackend")
        val packageField = XposedHelpers.findField(baseClass, "mPkg")
        val uidField = XposedHelpers.findField(baseClass, "mUid")
        val findIndex = XposedHelpers.findMethodExact(importanceField.type, "findSpinnerIndexOfValue", String::class.java)
        val setIndex = XposedHelpers.findMethodExact(importanceField.type, "setValueIndex", Int::class.javaPrimitiveType!!)
        val lockFields = XposedHelpers.findMethodExact(NotificationChannel::class.java, "lockFields", Int::class.javaPrimitiveType!!)
        val critical = XposedHelpers.findMethodExact(NotificationChannel::class.java, "isImportanceLockedByCriticalDeviceFunction", *emptyArray<Any>())
        val updateChannel = XposedHelpers.findMethodBestMatch(
            backendField.type, "updateChannel", String::class.java, Int::class.javaPrimitiveType!!, NotificationChannel::class.java,
        )
        // HyperOS 1 ships both implementations. The public Intent opens the .app page.
        // Hook only methods declared by each class so an inherited alias is not installed twice.
        val channelMethods = listOfNotNull(
            XposedHelpers.findClassIfExists("com.android.settings.notification.app.ChannelNotificationSettings", loader),
            XposedHelpers.findClassIfExists("com.android.settings.notification.ChannelNotificationSettings", loader),
        ).mapNotNull { channelClass ->
            XposedHelpers.findMethodExactIfExists(channelClass, "setupChannelDefaultPrefs")?.let { setup ->
                setup to XposedHelpers.findMethodExact(channelClass, "updateDependents", Boolean::class.javaPrimitiveType!!)
            }
        }
        check(channelMethods.isNotEmpty()) { "No supported channel notification settings implementation" }

        ModuleHelper.findAndHookMethod(baseClass, "setPrefVisible", preferenceClass, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val showImportance = try {
                    val pref = chain.getArg(0)
                    pref != null && getKey.invoke(pref) == "importance"
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log("NotificationImportance", t)
                    false
                }
                if (!showImportance) return chain.proceed()
                val args = XposedHelpers.getArgsArray(chain)
                args[1] = true
                return chain.proceed(args)
            }
        })
        for ((setup, updateDependents) in channelMethods) {
            ModuleHelper.hookMethod(setup, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val owner = chain.thisObject
                        val initialChannel = channelField.get(owner) as? NotificationChannel ?: return result
                        if (critical.invoke(initialChannel) == true) return result
                        val pref = findPreference.invoke(owner, "importance") ?: return result
                        importanceField.set(owner, pref)
                        val importance = backupImportanceField.getInt(owner)
                        if (importance <= 0) return result
                        val index = findIndex.invoke(pref, importance.toString()) as Int
                        if (index >= 0) setIndex.invoke(pref, index)
                        val handler = ImportanceChangeHandler { value ->
                            val channel = channelField.get(owner) as NotificationChannel
                            check(critical.invoke(channel) != true) { "Critical notification channel remains protected" }
                            check(uidField.getInt(owner) / 100000 == ModuleHelper.getUserId()) { "Notification user changed" }
                            val previous = channel.importance
                            try {
                                channel.importance = value
                                lockFields.invoke(channel, 4)
                                val saved = updateChannel.invoke(backendField.get(owner), packageField.get(owner), uidField.getInt(owner), channel)
                                check(saved != false) { "Notification backend rejected importance update" }
                            } catch (t: Throwable) {
                                FatalErrors.unwrapAndRethrowIfFatal(t)
                                channel.importance = previous
                                throw t
                            }
                            backupImportanceField.setInt(owner, value)
                            try {
                                updateDependents.invoke(owner, false)
                            } catch (t: Throwable) {
                                FatalErrors.unwrapAndRethrowIfFatal(t)
                                XposedHelpers.log("NotificationImportance", t)
                            }
                        }
                        setListener.invoke(pref, Proxy.newProxyInstance(loader, listenerInterfaces, handler))
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("NotificationImportance", t)
                    }
                    return result
                }
            })
        }
    }

    internal class ImportanceChangeHandler(private val update: (Int) -> Unit) : InvocationHandler {
        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> java.lang.System.identityHashCode(proxy)
                "toString" -> "CustoMIUIzer NotificationImportanceListener"
                "onPreferenceChange" -> {
                    val value = (args?.getOrNull(1) as? String)?.toIntOrNull()
                    if (value == null || value !in 1..4) return false
                    try {
                        update(value)
                        true
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("NotificationImportance", t)
                        false
                    }
                }
                else -> null
            }
        }
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

    internal fun expandedPopupMayAutoDismiss(expanded: Boolean, pinned: Boolean,
        remoteInput: Boolean, fullScreen: Boolean, guts: Boolean): Boolean =
        expanded && pinned && !remoteInput && !fullScreen && !guts

    @JvmStatic
    fun AutoDismissExpandedPopupsHook(lpparam: PackageReadyParam) {
        val loader = lpparam.classLoader
        val entryClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.HeadsUpManagerPhone\$HeadsUpEntryPhone", loader)
        val expanded = XposedHelpers.findField(entryClass, "expanded")
        val remoteInput = XposedHelpers.findField(entryClass, "remoteInputActive")
        val guts = XposedHelpers.findField(entryClass, "mGutsShownPinned")
        val entry = XposedHelpers.findField(entryClass, "mEntry")
        val sbn = XposedHelpers.findField(entry.type, "mSbn")
        val pinned = XposedHelpers.findMethodExact(entry.type, "isRowPinned", *emptyArray<Any>())
        val update = XposedHelpers.findMethodExact(entryClass, "updateEntry", Boolean::class.javaPrimitiveType!!)
        fun mayDismiss(owner: Any): Boolean {
            val current = entry.get(owner) ?: return false
            val notification = sbn.get(current) as? StatusBarNotification ?: return false
            return expandedPopupMayAutoDismiss(expanded.getBoolean(owner), pinned.invoke(current) == true,
                remoteInput.getBoolean(owner), notification.notification.fullScreenIntent != null, guts.getBoolean(owner))
        }
        ModuleHelper.findAndHookMethod(entryClass, "isSticky", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val original = chain.proceed()
                return ModuleHelper.guarded(original) {
                    if (original == true && mayDismiss(chain.thisObject!!)) false else original
                }
            }
        })
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarNotificationPresenter", loader, "onExpandClicked", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                ModuleHelper.guarded {
                    if (chain.getArg(1) != true) return@guarded
                    val owner = chain.thisObject
                    val keyguard = XposedHelpers.getObjectField(owner, "mKeyguardStateController")
                    if (XposedHelpers.getBooleanField(keyguard, "mShowing")) return@guarded
                    val notification = chain.getArg(0) ?: return@guarded
                    val manager = XposedHelpers.getObjectField(owner, "mHeadsUpManager")
                    val alert = XposedHelpers.callMethod(manager, "getHeadsUpEntry", XposedHelpers.getObjectField(notification, "mKey"))
                        ?: return@guarded
                    if (entry.get(alert) === notification && mayDismiss(alert)) update.invoke(alert, false)
                }
                return result
            }
        })
        // Native updateEntry/reset/remote-input paths own the sole removal runnable and timeout.
    }

    internal fun shouldOpenNotificationChannel(channelId: String?, hybrid: Boolean): Boolean =
        !channelId.isNullOrBlank() && channelId != NotificationChannel.DEFAULT_CHANNEL_ID && !hybrid

    @JvmStatic
    fun NotificationChannelSettingsHook(lpparam: PackageReadyParam) {
        val loader = lpparam.classLoader
        val menuClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow", loader)
        val infoField = XposedHelpers.findField(menuClass, "mInfoItem")
        val iconField = XposedHelpers.findField(infoField.type, "mIcon")
        val parentField = XposedHelpers.findField(menuClass, "mParent")
        val contextField = XposedHelpers.findField(menuClass, "mContext")
        val getEntry = XposedHelpers.findMethodExact(parentField.type, "getEntry", *emptyArray<Any>())
        val getChannel = XposedHelpers.findMethodExact(getEntry.returnType, "getChannel", *emptyArray<Any>())
        val sbnField = XposedHelpers.findField(getEntry.returnType, "mSbn")
        val uidField = XposedHelpers.findField(sbnField.type, "mAppUid")
        val notificationUtil = XposedHelpers.findClass("com.android.systemui.statusbar.notification.NotificationUtil", loader)
        val isHybrid = XposedHelpers.findMethodExact(notificationUtil, "isHybrid", StatusBarNotification::class.java)
        val getListenerInfo = XposedHelpers.findMethodExact(View::class.java, "getListenerInfo", *emptyArray<Any>())
        val clickListenerField = XposedHelpers.findField(getListenerInfo.returnType, "mOnClickListener")
        val startActivity = XposedHelpers.findMethodExact(Context::class.java, "startActivityAsUser", Intent::class.java, UserHandle::class.java)
        val modalClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.modal.ModalController", loader)
        val exitModal = XposedHelpers.findMethodExact(modalClass, "animExitModal", Long::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, String::class.java, Boolean::class.javaPrimitiveType!!)
        val queueClass = XposedHelpers.findClass("com.android.systemui.statusbar.CommandQueue", loader)
        val collapsePanels = XposedHelpers.findMethodExact(queueClass, "animateCollapsePanels", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)

        // This HyperOS 1 ABI has no onClickInfoItem. Its info icon owns the real listener.
        ModuleHelper.findAndHookMethod(menuClass, "createMenuViews", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    val menu = chain.thisObject
                    val info = infoField.get(menu) ?: return result
                    val icon = iconField.get(info) as? ImageView ?: return result
                    val original = clickListenerField.get(getListenerInfo.invoke(icon)) as? View.OnClickListener
                    icon.setOnClickListener(View.OnClickListener { clicked ->
                        ModuleHelper.guarded {
                            val opened = try {
                                run {
                                    // Resolve the live entry on click; do not retain a channel or Context in the hook.
                                    val entry = getEntry.invoke(parentField.get(menu)) ?: return@run false
                                    val channel = getChannel.invoke(entry) as? NotificationChannel ?: return@run false
                                    val notification = sbnField.get(entry) as? StatusBarNotification ?: return@run false
                                    if (!shouldOpenNotificationChannel(channel.id, isHybrid.invoke(null, notification) as Boolean)) return@run false
                                    val packageName = notification.packageName
                                    val uid = uidField.getInt(notification)
                                    if (packageName.isNullOrBlank() || uid < 0) return@run false
                                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                        .setPackage("com.android.settings")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
                                    startActivity.invoke(contextField.get(menu), intent, UserHandle.getUserHandleForUid(uid))
                                    true
                                }
                            } catch (t: Throwable) {
                                FatalErrors.unwrapAndRethrowIfFatal(t)
                                XposedHelpers.log("NotificationChannelSettings", t)
                                false
                            }
                            if (!opened) {
                                original?.onClick(clicked)
                                return@OnClickListener
                            }
                            // A cleanup failure must not launch a second settings page.
                            try {
                                exitModal.invoke(ModuleHelper.getDepInstance(loader, modalClass.name), 50L, true, "MORE", false)
                                collapsePanels.invoke(ModuleHelper.getDepInstance(loader, queueClass.name), 0, false)
                            } catch (t: Throwable) {
                                FatalErrors.unwrapAndRethrowIfFatal(t)
                                XposedHelpers.log("NotificationChannelSettings", t)
                            }
                        }
                    })
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log("NotificationChannelSettings", t)
                }
                return result
            }
        })
    }

    @JvmStatic
    fun MuteVisibleNotificationsHook(lpparam: PackageReadyParam) {
        NotificationAlertHook.install(lpparam.classLoader)
    }

    @JvmStatic
    fun BetterPopupsCenteredHook(lpparam: PackageReadyParam) {
        val coreUnhooker = ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManagerInjector", lpparam.classLoader, "miuiHeadsUpInset", Context::class.java, object : MethodHook() {
            private var mHeadsUpPaddingTop = 0
            private var mHeadsUpHeight = 0
            private var dimensionsConfiguration: Configuration? = null
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
                    if (dimensionsConfiguration != resources.configuration) {
                        val dimId = HookUtils.getResId(resources, "heads_up_status_bar_padding", "dimen", "com.android.systemui")
                        mHeadsUpPaddingTop = resources.getDimensionPixelSize(dimId)
                        mHeadsUpHeight = resources.getDimensionPixelSize(HookUtils.getResId(resources, "notification_max_heads_up_height", "dimen", "com.android.systemui"))
                        dimensionsConfiguration = Configuration(resources.configuration)
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
