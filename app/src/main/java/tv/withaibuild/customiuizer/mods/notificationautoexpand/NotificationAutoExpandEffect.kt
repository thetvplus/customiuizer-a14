package tv.withaibuild.customiuizer.mods.notificationautoexpand

import android.service.notification.StatusBarNotification
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.InvocationTargetException

internal class NotificationAutoExpandEffect(
    private val abi: NotificationAutoExpandAbi?,
    private val prefs: PrefMap,
) {
    fun intercept(chain: XposedInterface.Chain): Any? {
        val values = prefs.getAll()
        val mode = (values["system_expandnotifs"] as? String)?.toIntOrNull()
        if (mode != 2 && mode != 3) return chain.proceed()

        val row = chain.thisObject
        val resolved = abi?.takeIf { row?.javaClass === it.resolutionRootClass }
        val onKeyguard = if (resolved != null) {
            reflect { resolved.mOnKeyguardField.getBoolean(row) }
        } else {
            XposedHelpers.getBooleanField(row, "mOnKeyguard")
        }
        if (onKeyguard) return chain.proceed()

        val entry = if (resolved != null) reflect { resolved.getEntryMethod.invoke(row) }
            else XposedHelpers.callMethod(row, "getEntry")
        val notification = XposedHelpers.getObjectField(entry, "mSbn")
        // Invoke the ROM override on ExpandedNotification through the public framework type.
        val pkg: String = if (notification is StatusBarNotification) notification.packageName
            else XposedHelpers.callMethod(notification, "getPackageName") as String

        val selected = (values["system_expandnotifs_apps"] as? Set<*>)?.contains(pkg) == true
        val allowList = mode == 3
        if (selected == allowList) {
            if (resolved != null) reflect { resolved.setSystemExpandedMethod.invoke(row, true) }
            else XposedHelpers.callMethod(row, "setSystemExpanded", true)
        }
        return chain.proceed()
    }

    private inline fun <T> reflect(action: () -> T): T {
        try {
            return action()
        } catch (error: IllegalAccessException) {
            XposedHelpers.log(error)
            throw IllegalAccessError(error.message)
        } catch (error: InvocationTargetException) {
            throw XposedHelpers.InvocationTargetError(error.cause ?: error)
        }
    }
}
