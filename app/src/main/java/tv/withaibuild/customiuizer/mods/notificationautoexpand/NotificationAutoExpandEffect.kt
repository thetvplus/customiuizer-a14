package tv.withaibuild.customiuizer.mods.notificationautoexpand

import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicReference

/**
 * Hot-path effect for the Notification Auto-Expand hook.
 *
 * The effect holds only an immutable [NotificationAutoExpandAbi] and a snapshot reference. It
 * performs no per-callback ABI discovery, no [ClassLoader] lookups, no preference reads, and no
 * `findClass`/`findClassIfExists` calls. It does not retain runtime `ExpandableNotificationRow`,
 * entry, notification, [android.view.View], [android.content.Context], [android.app.Activity], or
 * [android.view.Window] instances beyond the hot method invocation.
 *
 * The FAST path uses the frozen `mOnKeyguard`, `getEntry`, and `setSystemExpanded` reflective
 * handles. The LEGACY helpers `mSbn` (via [XposedHelpers.getObjectField]) and `getPackageName`
 * (via [XposedHelpers.callMethod]) remain dynamic on FAST; they may use their normal cached
 * `findField`/`findMethodBestMatch` semantics, but no fresh ABI discovery is performed for the
 * three frozen members.
 *
 * Each callback selects exactly one execution mode at the start and keeps it for the entire
 * invocation: FAST uses the frozen [java.lang.reflect.Field] and [java.lang.reflect.Method]
 * handles; LEGACY uses the existing XposedHelpers helpers. Mixed access within a single callback
 * is forbidden, and there is no FAST-to-LEGACY retry after the FAST boundary.
 */
internal class NotificationAutoExpandEffect(
    val abi: NotificationAutoExpandAbi?,
    val snapshotRef: AtomicReference<NotificationAutoExpandSnapshot?>,
) {

    /**
     * Production entry point from the [MethodHook.intercept] callback.
     */
    fun intercept(chain: XposedInterface.Chain): Any? {
        val thisObject = chain.thisObject
        val a = abi
        val snapshot = snapshotRef.get()

        if (
            a != null &&
            thisObject != null &&
            thisObject.javaClass === a.resolutionRootClass &&
            snapshot != null
        ) {
            return processFast(thisObject, a, snapshot, chain)
        }
        return processLegacy(thisObject, chain)
    }

    /**
     * FAST path: the three frozen members (`mOnKeyguard`, `getEntry`, `setSystemExpanded`) use
     * frozen handles. `mSbn` and `getPackageName` remain dynamic LEGACY XposedHelpers helpers.
     */
    private fun processFast(
        thisObject: Any,
        a: NotificationAutoExpandAbi,
        snapshot: NotificationAutoExpandSnapshot,
        chain: XposedInterface.Chain,
    ): Any? {
        val mOnKeyguard = withLegacyFieldMapping { a.mOnKeyguardField.getBoolean(thisObject) }
        if (mOnKeyguard) {
            return chain.proceed()
        }

        val entry = withLegacyMethodMapping { a.getEntryMethod.invoke(thisObject) }
        val notification = XposedHelpers.getObjectField(entry, "mSbn")
        val pkgName = XposedHelpers.callMethod(notification, "getPackageName") as String

        val opt = Integer.parseInt(snapshot.modeRaw)
        val isSelected = snapshot.selectedApps.contains(pkgName)

        if ((opt == 2 && !isSelected) || (opt == 3 && isSelected)) {
            withLegacyMethodMapping { a.setSystemExpandedMethod.invoke(thisObject, true) }
        }

        return chain.proceed()
    }

    /**
     * LEGACY path: preserves the original dynamic XposedHelpers behavior.
     */
    private fun processLegacy(thisObject: Any?, chain: XposedInterface.Chain): Any? {
        val mOnKeyguard = XposedHelpers.getBooleanField(thisObject, "mOnKeyguard")
        if (!mOnKeyguard) {
            val notification = XposedHelpers.getObjectField(XposedHelpers.callMethod(thisObject, "getEntry"), "mSbn")
            val pkgName = XposedHelpers.callMethod(notification, "getPackageName") as String
            val opt = Integer.parseInt(MainModule.mPrefs.getString("system_expandnotifs", "1") ?: "1")
            val isSelected = MainModule.mPrefs.getStringSet("system_expandnotifs_apps").contains(pkgName)
            if ((opt == 2 && !isSelected) || (opt == 3 && isSelected)) {
                XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)
            }
        }
        return chain.proceed()
    }

    /**
     * Wraps a fast field operation to map [IllegalAccessException] to [IllegalAccessError],
     * matching the legacy XposedHelpers contract.
     */
    private inline fun <T> withLegacyFieldMapping(action: () -> T): T {
        return try {
            action()
        } catch (e: IllegalAccessException) {
            XposedHelpers.log(e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Wraps a fast method invocation to match the legacy `XposedHelpers.callMethod` exception
     * mapping:
     * - [IllegalAccessException] is logged and rethrown as [IllegalAccessError].
     * - [IllegalArgumentException] is rethrown unchanged.
     * - [InvocationTargetException] is rethrown as [XposedHelpers.InvocationTargetError] of its
     *   cause.
     *
     * Fatal errors (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) are not caught.
     */
    private inline fun <T> withLegacyMethodMapping(action: () -> T): T {
        return try {
            action()
        } catch (e: IllegalAccessException) {
            XposedHelpers.log(e)
            throw IllegalAccessError(e.message)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: InvocationTargetException) {
            throw XposedHelpers.InvocationTargetError(e.cause ?: e)
        }
    }
}
