package tv.withaibuild.customiuizer.mods.notificationautoexpand

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Cold resolver for the Notification Auto-Expand frozen ABI.
 *
 * Reflection and class discovery happens once during hook installation. The output is an
 * immutable [NotificationAutoExpandAbi]. Hot paths must not perform any further discovery or
 * resolution of the three frozen members (`mOnKeyguard`, `getEntry`, `setSystemExpanded`) and
 * must not perform [ClassLoader] lookups or `findClass`/`findClassIfExists` calls.
 *
 * The retained LEGACY helpers `mSbn` and `getPackageName` remain dynamic and may use their
 * normal XposedHelpers caching semantics; they are not resolved by this ABI.
 *
 * The resolver returns `null` (expected miss / ordinary failure) to select the complete
 * legacy XposedHelpers path. Fatal errors are propagated immediately.
 */
internal object NotificationAutoExpandResolver {

    private const val TARGET_CLASS = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"
    private const val FIELD_M_ON_KEYGUARD = "mOnKeyguard"
    private const val METHOD_GET_ENTRY = "getEntry"
    private const val METHOD_SET_SYSTEM_EXPANDED = "setSystemExpanded"

    /**
     * Resolves the frozen ABI from [classLoader].
     *
     * @return A frozen [NotificationAutoExpandAbi] if the root class, the primitive-boolean
     *         `mOnKeyguard` field, the zero-argument `getEntry` method, and the one-argument
     *         `setSystemExpanded` method resolve, or `null` to select the complete legacy
     *         XposedHelpers fallback.
     */
    @JvmStatic
    fun resolve(classLoader: ClassLoader?): NotificationAutoExpandAbi? {
        return try {
            val resolutionRootClass = XposedHelpers.findClassIfExists(TARGET_CLASS, classLoader)
                ?: run { missing("target class not found: $TARGET_CLASS"); return null }
            resolveFromClass(resolutionRootClass)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    /**
     * Testable overload that allows the caller to specify the resolution root class.
     */
    @JvmStatic
    fun resolve(resolutionRootClass: Class<*>): NotificationAutoExpandAbi? {
        return resolveFromClass(resolutionRootClass)
    }

    private fun resolveFromClass(resolutionRootClass: Class<*>): NotificationAutoExpandAbi? {
        return try {
            val mOnKeyguardField = resolvePrimitiveBooleanField(resolutionRootClass, FIELD_M_ON_KEYGUARD)
                ?: run { missing("$FIELD_M_ON_KEYGUARD is not primitive boolean on ${resolutionRootClass.name}"); return null }
            val getEntryMethod = resolveGetEntry(resolutionRootClass)
                ?: run { missing("$METHOD_GET_ENTRY not found on ${resolutionRootClass.name}"); return null }
            val setSystemExpandedMethod = resolveSetSystemExpanded(resolutionRootClass)
                ?: run { missing("$METHOD_SET_SYSTEM_EXPANDED not found on ${resolutionRootClass.name}"); return null }

            NotificationAutoExpandAbi(resolutionRootClass, mOnKeyguardField, getEntryMethod, setSystemExpandedMethod)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    private fun resolvePrimitiveBooleanField(resolutionRootClass: Class<*>, fieldName: String): Field? {
        val field = try {
            XposedHelpers.findField(resolutionRootClass, fieldName)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            return null
        }
        return if (field.type == Boolean::class.javaPrimitiveType) {
            field.isAccessible = true
            field
        } else {
            missing("$fieldName has type ${field.type.name}, not boolean, on ${resolutionRootClass.name}")
            null
        }
    }

    private fun resolveGetEntry(resolutionRootClass: Class<*>): Method? {
        return try {
            // Match the legacy zero-argument XposedHelpers.callMethod(thisObject, "getEntry").
            XposedHelpers.findMethodBestMatch(resolutionRootClass, METHOD_GET_ENTRY)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun resolveSetSystemExpanded(resolutionRootClass: Class<*>): Method? {
        return try {
            // Match the legacy XposedHelpers.callMethod(thisObject, "setSystemExpanded", true),
            // where the Kotlin literal `true` is boxed to java.lang.Boolean and resolved with
            // Boolean.class parameter-type semantics.
            XposedHelpers.findMethodBestMatch(resolutionRootClass, METHOD_SET_SYSTEM_EXPANDED, java.lang.Boolean::class.java)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun missing(message: String) {
        XposedHelpers.log("NotificationAutoExpandResolver: $message")
    }
}
