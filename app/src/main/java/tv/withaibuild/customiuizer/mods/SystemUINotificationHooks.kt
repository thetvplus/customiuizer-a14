package tv.withaibuild.customiuizer.mods

import android.view.View
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Notification shade hooks that live in SystemUI rather than in the framework.
 * Dismiss view, access icon, empty-shade text, per-package limits, heads-up while
 * muted, and opening a notification in a floating window.
 */
object SystemUINotificationHooks {

    private data class FloatingSnapshot(val enabled: Boolean, val whitelist: Boolean, val apps: Set<String>)

    @Volatile
    private var floatingSnapshot = FloatingSnapshot(false, false, emptySet())

    private var openInFwObserverRegistered = false

    private fun refreshOpenInFwSnapshot() {
        val prefs = MainModule.mPrefs
        floatingSnapshot = FloatingSnapshot(prefs.getBoolean("system_notify_openinfw"),
            prefs.getBoolean("system_notify_openinfw_in_whitelist"),
            HashSet(prefs.getStringSet("system_notify_openinfw_apps")))
    }

    private fun installOpenInFwSnapshot() {
        refreshOpenInFwSnapshot()
        if (openInFwObserverRegistered) return
        openInFwObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key == "system_notify_openinfw" || key == "system_notify_openinfw_in_whitelist" || key == "system_notify_openinfw_apps") {
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

    @JvmStatic
    fun OpenNotifyInFloatingWindowHook(lpparam: PackageReadyParam) {
        installOpenInFwSnapshot()
        NotificationClickOptionsHook.install(lpparam.classLoader) { pkg ->
            val snapshot = floatingSnapshot
            snapshot.enabled && !(snapshot.whitelist xor (pkg in snapshot.apps))
        }
    }

    @JvmStatic
    fun NotificationImportanceHook(lpparam: PackageReadyParam) {
        val loader = lpparam.classLoader
        val controller = XposedHelpers.findClass("com.android.systemui.statusbar.phone.NotificationIconAreaController", loader)
        val effect = NotificationIconImportance(controller,
            XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.ListEntry", loader),
            XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", loader))
        ModuleHelper.findAndHookMethod(controller, "updateStatusBarIcons", object : MethodHook() {
            override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? = effect.intercept(chain)
        })
    }

    @JvmStatic
    fun RemovePackageNotificationsLimitHook(lpparam: PackageReadyParam) {
        val entry = XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", lpparam.classLoader)
        // attach still owns mPipeline and native BindEventManager registration.
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.collection.coordinator.CountLimitCoordinator\$\$ExternalSyntheticLambda0", lpparam.classLoader, "onViewBound", entry, HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun DisableFoldNotificationsHook(lpparam: PackageReadyParam) {
        val entry = XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", lpparam.classLoader)
        val util = XposedHelpers.findClass("com.android.systemui.statusbar.notification.NotificationUtil", lpparam.classLoader)
        val clearFold = XposedHelpers.findMethodExact(util, "setFold", entry, Boolean::class.javaPrimitiveType!!)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.collection.coordinator.FoldCoordinator\$collectionListener\$1", lpparam.classLoader,
            "onEntryAdded", entry, object : MethodHook() {
                override fun before(param: BeforeHookCallback) {
                    // The suppressed/international branch omits this reset. Keep both native
                    // entry.isFold and the notification extra consistent, including on repost.
                    clearFold.invoke(null, param.getArg(0), false)
                }
            })
        // Preserve collection removal, user/configuration callbacks and pipeline initialization.
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.collection.coordinator.FoldCoordinator\$foldFilter\$1", lpparam.classLoader,
            "shouldFilterOut", entry, Long::class.javaPrimitiveType!!, HookerClassHelper.returnConstant(false))
        // onEntryUpdated schedules history folding even when shouldSuppressFold is true.
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.collection.coordinator.FoldCoordinator", lpparam.classLoader,
            "scheduleHistoryNotification", entry, HookerClassHelper.DO_NOTHING)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.NotificationUtil", lpparam.classLoader, "shouldSuppressFold", HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun DisableHeadsUpWhenMuteHook(lpparam: PackageReadyParam) {
        val state = object {
            @Volatile var muted = false
            @Volatile var enabled = MainModule.mPrefs.getBoolean("system_betterpopups_disablewhenmute")
        }
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key == "system_betterpopups_disablewhenmute") state.enabled = MainModule.mPrefs.getBoolean("system_betterpopups_disablewhenmute")
            }
        })
        val disableHeadsUpHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (state.enabled && state.muted) {
                    param.returnAndSkip(false)
                }
            }
        }
        val entry = XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", lpparam.classLoader)
        // AwakeCommon is also used by bubble decisions. Only suppress heads-up presentation.
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl", lpparam.classLoader,
            "canAlertHeadsUpCommon", entry, Boolean::class.javaPrimitiveType!!, disableHeadsUpHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarPolicy", lpparam.classLoader, "updateVolumeZen", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                state.muted = XposedHelpers.getBooleanField(param.getThisObject(), "mMuteVisible")
            }
        })
    }

}
