package tv.withaibuild.customiuizer.mods

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.github.libxposed.api.XposedInterface
import miui.process.ProcessManager
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** CN/TW HyperOS 1: the deferred starter asks MiuiMultiWindowUtils for its own options. */
internal object NotificationClickOptionsHook {
    internal fun eligible(notification: StatusBarNotification, intent: PendingIntent, user: Int,
        showing: Boolean, overLockscreen: Boolean): Boolean =
        user in 0..(Int.MAX_VALUE / 100000) && !showing && !overLockscreen && intent.isActivity &&
            notification.notification.contentIntent == intent && notification.notification.fullScreenIntent == null &&
            notification.user == android.os.UserHandle.getUserHandleForUid(user * 100000) && intent.creatorUid / 100000 == user

    private data class Launch(val context: Context, val creator: String, val target: String,
        val pending: PendingIntent, val valid: () -> Boolean)

    fun install(loader: ClassLoader, selected: (String) -> Boolean) {
        val callback = XposedHelpers.findClass(
            "com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter\$\$ExternalSyntheticLambda3", loader)
        val starter = XposedHelpers.findField(callback, "f\$0")
        val entry = XposedHelpers.findField(callback, "f\$1")
        val row = XposedHelpers.findField(callback, "f\$2")
        val pending = XposedHelpers.findField(callback, "f\$3")
        val overLockscreen = XposedHelpers.findField(callback, "f\$6")
        val context = XposedHelpers.findField(starter.type, "mContext")
        val keyguard = XposedHelpers.findField(starter.type, "mKeyguardStateController")
        val keyguardClass = XposedHelpers.findClass("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", loader)
        val showing = XposedHelpers.findField(keyguardClass, "mShowing")
        val users = XposedHelpers.findField(starter.type, "mLockscreenUserManager")
        val userClass = XposedHelpers.findClass("com.android.systemui.statusbar.NotificationLockscreenUserManagerImpl", loader)
        val currentUser = XposedHelpers.findField(userClass, "mCurrentUserId")
        val sbn = XposedHelpers.findField(entry.type, "mSbn")
        val rowEntry = XposedHelpers.findField(row.type, "mEntry")
        val removed = XposedHelpers.findField(row.type, "mRemoved")
        val scope = NotificationLaunchScope<Launch>()
        val fromBundle = XposedHelpers.findMethodExact(ActivityOptions::class.java, "fromBundle", Bundle::class.java)

        // Resolve all required ABI before installing either hook. No speculative fallback hooks.
        val run = XposedHelpers.findMethodExact(callback, "run", *emptyArray<Any>())
        val options = XposedHelpers.findMethodExact(
            XposedHelpers.findClass("android.util.MiuiMultiWindowUtils", loader),
            "getActivityOptions", Context::class.java, String::class.java)
        ModuleHelper.hookMethod(options, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val original = chain.proceed()
                val launch = scope.consumeIf { it.context === chain.getArg(0) && it.creator == chain.getArg(1) }
                    ?: return original
                if (original != null) return original // Preserve an existing native freeform decision.
                return ModuleHelper.guarded(original) {
                    if (!launch.valid() || !selected(launch.target)) return@guarded original
                    val top = ProcessManager.getForegroundInfo()?.mForegroundPackageName
                    if (top == launch.target || top == "com.miui.home") return@guarded original
                    ModuleHelper.getFreeformOptions(launch.context, launch.target, launch.pending, true)
                        ?.let { fromBundle.invoke(null, it) } ?: original
                }
            }
        })
        ModuleHelper.hookMethod(run, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val launch = ModuleHelper.guarded<Launch?>(null) {
                    val call = chain.thisObject ?: return@guarded null
                    val owner = starter.get(call)
                    val currentEntry = entry.get(call)
                    val nativeRow = row.get(call)
                    val notification = sbn.get(currentEntry) as? StatusBarNotification ?: return@guarded null
                    val intent = pending.get(call) as? PendingIntent ?: return@guarded null
                    val n: Notification = notification.notification
                    if (!intent.isActivity || n.contentIntent != intent || n.fullScreenIntent != null || overLockscreen.getBoolean(call)) return@guarded null
                    val creator = intent.creatorPackage ?: return@guarded null
                    val target = if (notification.packageName != notification.opPkg) notification.packageName else creator
                    Launch(context.get(owner) as Context, creator, target, intent) {
                        val user = currentUser.getInt(users.get(owner))
                        // Cross-profile / all-user notifications retain the complete native path.
                        eligible(notification, intent, user, showing.getBoolean(keyguard.get(owner)), overLockscreen.getBoolean(call)) &&
                            rowEntry.get(nativeRow) === currentEntry &&
                            sbn.get(currentEntry) === notification && !removed.getBoolean(nativeRow)
                    }
                }
                return scope.within(launch) { chain.proceed() }
            }
        })
    }
}
