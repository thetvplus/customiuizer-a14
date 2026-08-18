package tv.withaibuild.customiuizer.mods

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import miui.process.ProcessManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.ArrayList

/**
 * Notification shade hooks that live in SystemUI rather than in the framework.
 * Dismiss view, access icon, empty-shade text, per-package limits, heads-up while
 * muted, and opening a notification in a floating window.
 */
object SystemUINotificationHooks {

    @Volatile
    private var openInFwWhitelist = false

    @Volatile
    private var openInFwApps: Set<String> = emptySet()

    private var openInFwObserverRegistered = false

    private fun refreshOpenInFwSnapshot() {
        val prefs = MainModule.mPrefs
        openInFwWhitelist = prefs.getBoolean("system_notify_openinfw_in_whitelist")
        openInFwApps = HashSet(prefs.getStringSet("system_notify_openinfw_apps"))
    }

    private fun installOpenInFwSnapshot() {
        refreshOpenInFwSnapshot()
        if (openInFwObserverRegistered) return
        openInFwObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key == "system_notify_openinfw_in_whitelist" || key == "system_notify_openinfw_apps") {
                    refreshOpenInFwSnapshot()
                }
            }
        })
    }

    @JvmStatic
    fun HideDismissViewHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "updateDismissView", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mDismissView = XposedHelpers.getObjectField(param.getThisObject(), "mDismissView") as View?
                if (mDismissView != null) {
                    mDismissView.visibility = View.GONE
                    param.returnAndSkip(null)
                }
            }
        })
    }

    @JvmStatic
    fun HideNoficationAccessIconHook(lpparam: PackageReadyParam) {
        val hideViewHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mShortCut = XposedHelpers.getObjectField(param.getThisObject(), "mShortCut") as View?
                if (mShortCut != null) {
                    mShortCut.visibility = View.GONE
                    param.returnAndSkip(null)
                }
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiQSHeaderView", lpparam.classLoader, "updateShortCutVisibility", hideViewHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiNotificationHeaderView", lpparam.classLoader, "updateShortCutVisibility", hideViewHook)
    }

    @JvmStatic
    fun HideNoNotificationsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", lpparam.classLoader, "updateEmptyShadeView", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.getArgs()[1] = 0
                param.getArgs()[2] = 0
                val mEmptyShadeView = XposedHelpers.getObjectField(param.getThisObject(), "mEmptyShadeView") as View
                mEmptyShadeView.setOnClickListener(null)
                XposedHelpers.callMethod(mEmptyShadeView, "setVisible", false, false)
                param.returnAndSkip(null)
            }
        })
    }

    private var clickNotifyOptions: Bundle? = null

    @JvmStatic
    fun OpenNotifyInFloatingWindowHook(lpparam: PackageReadyParam) {
        installOpenInFwSnapshot()
        ModuleHelper.hookAllMethods(PendingIntent::class.java, "sendAndReturnResult", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (param.getArgs().size != 7) return
                if (clickNotifyOptions != null) {
                    param.getArgs()[6] = clickNotifyOptions
                }
            }
            override fun after(param: AfterHookCallback) {
                if (param.getArgs().size != 7) return
                clickNotifyOptions = null
            }
        })
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter", lpparam.classLoader, "onNotificationClicked", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val notificationEntry = param.getArg(0)
                val mSbn = XposedHelpers.getObjectField(notificationEntry, "mSbn")
                val notify = XposedHelpers.callMethod(mSbn, "getNotification") as Notification
                val pendingIntent = notify.contentIntent ?: return
                val mKeyguardStateController = XposedHelpers.getObjectField(param.getThisObject(), "mKeyguardStateController")
                if (XposedHelpers.getBooleanField(mKeyguardStateController, "mShowing")) return

                val opPkg = XposedHelpers.callMethod(mSbn, "getOpPkg") as String?
                val mPkgName = XposedHelpers.callMethod(mSbn, "getPackageName") as String?
                val isSubstituteNotification = !TextUtils.equals(mPkgName, opPkg)
                val pkgName = (if (isSubstituteNotification) mPkgName else pendingIntent.creatorPackage) ?: return

                val foregroundInfo = ProcessManager.getForegroundInfo()
                if (foregroundInfo != null) {
                    val topPackage = foregroundInfo.mForegroundPackageName
                    if (pkgName == topPackage || "com.miui.home" == topPackage) {
                        return
                    }
                }
                val whitelist = openInFwWhitelist
                val appInList = pkgName in openInFwApps
                if (whitelist xor appInList) {
                    return
                }
                val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                clickNotifyOptions = ModuleHelper.getFreeformOptions(mContext, pkgName, pendingIntent, true)
            }
        })
    }

    @JvmStatic
    fun NotificationImportanceHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.NotificationIconAreaController", lpparam.classLoader, "updateStatusBarIcons", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mNotificationEntries = XposedHelpers.getObjectField(param.getThisObject(), "mNotificationEntries") as? List<Any> ?: return
                if (mNotificationEntries.isNotEmpty()) {
                    val arrayList = ArrayList<Any>()
                    for (item in mNotificationEntries) {
                        val notifyEntry = XposedHelpers.callMethod(item, "getRepresentativeEntry")
                        val importance = XposedHelpers.callMethod(notifyEntry, "getImportance") as Int
                        if (importance > 1) {
                            arrayList.add(item)
                        }
                    }
                    if (arrayList.size != mNotificationEntries.size) {
                        XposedHelpers.setObjectField(param.getThisObject(), "mNotificationEntries", arrayList)
                    }
                }
            }
        })
    }

    @JvmStatic
    fun RemovePackageNotificationsLimitHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.collection.coordinator.CountLimitCoordinator", lpparam.classLoader, "attach", HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun DisableFoldNotificationsHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.collection.coordinator.FoldCoordinator", lpparam.classLoader, "attach", HookerClassHelper.DO_NOTHING)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.NotificationUtil", lpparam.classLoader, "shouldSuppressFold", HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun DisableHeadsUpWhenMuteHook(lpparam: PackageReadyParam) {
        var mMuteVisible = false
        val disableHeadsUpHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (mMuteVisible) {
                    param.returnAndSkip(false)
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl", lpparam.classLoader, "canAlertAwakeCommon", disableHeadsUpHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarPolicy", lpparam.classLoader, "updateVolumeZen", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                mMuteVisible = XposedHelpers.getBooleanField(param.getThisObject(), "mMuteVisible")
            }
        })
    }

}
