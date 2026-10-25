package tv.withaibuild.customiuizer.mods.island

import android.service.notification.StatusBarNotification
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

internal fun interface IslandNotificationPrivacy {
    fun hidden(entry: Any, notification: StatusBarNotification): Boolean
}

/** Entry.mSensitive starts true and is only updated before list rendering, after heads-up
 * binding. Read the same native policies at that earlier boundary instead of the initial bit.
 * Two process-owned Dependency singletons are resolved lazily; no Context, history or polling. */
internal class IslandNativeNotificationPrivacy internal constructor(usersType: Class<*>, policyType: Class<*>,
    entryType: Class<*>, coordinatorType: Class<*>, usersProvider: () -> Any?, policyProvider: () -> Any?) : IslandNotificationPrivacy {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.android.systemui.statusbar.NotificationLockscreenUserManagerImpl", loader),
        XposedHelpers.findClass("com.android.systemui.statusbar.notification.policy.NotificationSensitiveController", loader),
        XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", loader),
        XposedHelpers.findClass("com.android.systemui.statusbar.notification.collection.coordinator.SensitiveContentCoordinatorImpl", loader),
        { ModuleHelper.getDepInstance(loader, "com.android.systemui.statusbar.NotificationLockscreenUserManager") },
        { ModuleHelper.getDepInstance(loader, "com.android.systemui.statusbar.notification.policy.NotificationSensitiveController") })
    private val publicMode = usersType.getDeclaredMethod("isLockscreenPublicMode", Int::class.javaPrimitiveType)
    private val currentUser = usersType.getDeclaredField("mCurrentUserId").apply { isAccessible = true }
    private val appLock = policyType.getDeclaredMethod("showSensitiveByAppLock", entryType)
    val updated = coordinatorType.getDeclaredMethod("onBeforeRenderList", List::class.java)
    private val users by lazy {
        usersProvider()
            ?.takeIf(usersType::isInstance) ?: error("Native notification user policy unavailable")
    }
    private val policy by lazy {
        policyProvider()?.takeIf(policyType::isInstance)
            ?: error("Native notification privacy policy unavailable")
    }

    init {
        require(publicMode.returnType == Boolean::class.javaPrimitiveType &&
            appLock.returnType == Boolean::class.javaPrimitiveType && currentUser.type == Int::class.javaPrimitiveType)
    }

    override fun hidden(entry: Any, notification: StatusBarNotification): Boolean {
        val user = currentUser.getInt(users)
        if (user < 0 || notification.uid < 0) return true
        // Public-mode entries stay native even if dynamic unlock would allow a temporary
        // exception. The controller separately requires a fully unlocked current user.
        val sourceUser = notification.uid / 100000
        return publicMode.invoke(users, user) != false ||
            sourceUser != user && publicMode.invoke(users, sourceUser) != false || appLock.invoke(policy, entry) != false
    }
}
