package tv.withaibuild.customiuizer.mods.statusbariconvisibility

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Cold resolver for the HideIconsSignal Architecture C frozen ABI.
 *
 * Reflection and class discovery happens once during hook installation. The output
 * is an immutable [StatusBarIconVisibilityAbi]. Hot paths must not perform any
 * further discovery, classloader lookups, or reflection resolution.
 *
 * The resolver returns `null` (expected miss / ordinary failure) to select the
 * complete legacy XposedHelpers path. Fatal errors are propagated immediately.
 */
internal object StatusBarIconVisibilityResolver {

    private const val STATUS_BAR_MOBILE_VIEW_CLASS = "com.android.systemui.statusbar.StatusBarMobileView"

    private const val FIELD_MSTATE = "mState"
    private const val FIELD_WIFI_AVAILABLE = "wifiAvailable"
    private const val FIELD_SUB_ID = "subId"
    private const val FIELD_VISIBLE = "visible"
    private const val FIELD_ROAMING = "roaming"
    private const val FIELD_VOLTE = "volte"
    private const val FIELD_SPEECH_HD = "speechHd"

    private const val METHOD_APPLY_MOBILE_STATE = "applyMobileState"
    private const val METHOD_UPDATE_STATE = "updateState"

    /**
     * Resolves the frozen ABI from [classLoader].
     *
     * @return A frozen [StatusBarIconVisibilityAbi] if all required fields resolve,
     *         or `null` to select the complete legacy `XposedHelpers` fallback.
     */
    @JvmStatic
    fun resolve(classLoader: ClassLoader?): StatusBarIconVisibilityAbi? =
        resolveInternal(classLoader, STATUS_BAR_MOBILE_VIEW_CLASS)

    /**
     * Testable overload that allows the caller to specify the StatusBarMobileView
     * class name. Production code uses the overload without [targetClassName].
     */
    @JvmStatic
    fun resolve(classLoader: ClassLoader?, targetClassName: String): StatusBarIconVisibilityAbi? =
        resolveInternal(classLoader, targetClassName)

    /**
     * Test overload that allows the caller to provide already-loaded classes.
     * Used when the caller controls class loading and does not want [ClassLoader] discovery.
     */
    @JvmStatic
    fun resolve(statusBarMobileViewClass: Class<*>): StatusBarIconVisibilityAbi? {
        return try {
            val mobileIconStateClass = resolveMobileIconStateClass(statusBarMobileViewClass)
                ?: return missing("mobileIconState resolution root not resolvable from ${statusBarMobileViewClass.name}")
            resolveFromClasses(statusBarMobileViewClass, mobileIconStateClass)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    @JvmStatic
    fun resolve(
        statusBarMobileViewClass: Class<*>,
        mobileIconStateResolutionRootClass: Class<*>,
    ): StatusBarIconVisibilityAbi? {
        return try {
            resolveFromClasses(statusBarMobileViewClass, mobileIconStateResolutionRootClass)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    private fun resolveInternal(classLoader: ClassLoader?, targetClassName: String): StatusBarIconVisibilityAbi? {
        return try {
            val statusBarMobileViewClass = XposedHelpers.findClassIfExists(targetClassName, classLoader)
                ?: return missing("target class not found: $targetClassName")

            val mobileIconStateClass = resolveMobileIconStateClass(statusBarMobileViewClass)
                ?: return missing("mobileIconState resolution root not resolvable from $targetClassName")

            resolveFromClasses(statusBarMobileViewClass, mobileIconStateClass)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    /**
     * Determines the MobileIconState resolution root from the hook/member ABI.
     *
     * The first source is the parameter type of the single-parameter
     * `applyMobileState` / `updateState` methods declared directly on
     * [statusBarMobileViewClass]. If the methods are ambiguous or absent, the
     * type of the `mState` field is used as a fallback when it is a concrete,
     * non-Object class. If no concrete class can be determined, this is an
     * expected miss.
     */
    private fun resolveMobileIconStateClass(statusBarMobileViewClass: Class<*>): Class<*>? {
        val candidateMethods = try {
            statusBarMobileViewClass.declaredMethods.filter { method ->
                (method.name == METHOD_APPLY_MOBILE_STATE || method.name == METHOD_UPDATE_STATE) &&
                    isSingleNonPrimitiveParameter(method)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            return null
        }

        val paramTypes = candidateMethods.map { it.parameterTypes[0] }.distinct()
        if (paramTypes.size == 1) return paramTypes[0]

        val mStateField = try {
            XposedHelpers.findFieldIfExists(statusBarMobileViewClass, FIELD_MSTATE)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
        val mStateType = mStateField?.type
        if (mStateType != null && !mStateType.isPrimitive && mStateType != Any::class.java && mStateType != Object::class.java) {
            if (paramTypes.isEmpty() || paramTypes.any { it == mStateType }) return mStateType
        }

        return null
    }

    private fun isSingleNonPrimitiveParameter(method: Method): Boolean {
        val params = method.parameterTypes
        return params.size == 1 &&
            !params[0].isPrimitive &&
            params[0] != Any::class.java
    }

    private fun resolveFromClasses(
        statusBarMobileViewClass: Class<*>,
        mobileIconStateClass: Class<*>,
    ): StatusBarIconVisibilityAbi? {
        val mStateField = XposedHelpers.findFieldIfExists(statusBarMobileViewClass, FIELD_MSTATE)
            ?: return missing("field not found: $FIELD_MSTATE on ${statusBarMobileViewClass.name}")

        val wifiAvailableField = resolvePrimitiveBooleanField(mobileIconStateClass, FIELD_WIFI_AVAILABLE)
            ?: return missing("field not found or not primitive boolean: $FIELD_WIFI_AVAILABLE on ${mobileIconStateClass.name}")
        val subIdField = resolveIntOrIntegerField(mobileIconStateClass, FIELD_SUB_ID)
            ?: return missing("field not found or not int/Integer: $FIELD_SUB_ID on ${mobileIconStateClass.name}")

        val visibleField = resolveBooleanWritableField(mobileIconStateClass, FIELD_VISIBLE)
            ?: return missing("field not found or not boolean/Boolean: $FIELD_VISIBLE on ${mobileIconStateClass.name}")
        val roamingField = resolveBooleanWritableField(mobileIconStateClass, FIELD_ROAMING)
            ?: return missing("field not found or not boolean/Boolean: $FIELD_ROAMING on ${mobileIconStateClass.name}")
        val volteField = resolveBooleanWritableField(mobileIconStateClass, FIELD_VOLTE)
            ?: return missing("field not found or not boolean/Boolean: $FIELD_VOLTE on ${mobileIconStateClass.name}")
        val speechHdField = resolveBooleanWritableField(mobileIconStateClass, FIELD_SPEECH_HD)
            ?: return missing("field not found or not boolean/Boolean: $FIELD_SPEECH_HD on ${mobileIconStateClass.name}")

        return StatusBarIconVisibilityAbi(
            statusBarMobileViewResolutionRootClass = statusBarMobileViewClass,
            mobileIconStateResolutionRootClass = mobileIconStateClass,
            mStateField = mStateField,
            wifiAvailableField = wifiAvailableField,
            subIdField = subIdField,
            visibleField = visibleField,
            roamingField = roamingField,
            volteField = volteField,
            speechHdField = speechHdField,
        )
    }

    /**
     * Resolves a primitive `boolean` field only.
     *
     * [Field.getBoolean] requires the field type to be [Boolean.TYPE].
     * `java.lang.Boolean` wrapper fields are not fast-compatible and must force legacy.
     */
    private fun resolvePrimitiveBooleanField(clazz: Class<*>, fieldName: String): Field? {
        val field = XposedHelpers.findFieldIfExists(clazz, fieldName) ?: return null
        return if (field.type == Boolean::class.javaPrimitiveType) field else null
    }

    /**
     * Resolves an `int` or `java.lang.Integer` field for the `subId` read.
     */
    private fun resolveIntOrIntegerField(clazz: Class<*>, fieldName: String): Field? {
        val field = XposedHelpers.findFieldIfExists(clazz, fieldName) ?: return null
        return if (field.type == Int::class.javaPrimitiveType || field.type == Integer::class.java) field else null
    }

    /**
     * Resolves a `boolean` or `java.lang.Boolean` field for the boolean writes.
     *
     * [Field.set] with a [Boolean] boxed value works for both primitive and
     * wrapper fields, but it does not work for unrelated types.
     */
    private fun resolveBooleanWritableField(clazz: Class<*>, fieldName: String): Field? {
        val field = XposedHelpers.findFieldIfExists(clazz, fieldName) ?: return null
        return if (field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.java) field else null
    }

    private fun missing(reason: String): StatusBarIconVisibilityAbi? {
        XposedHelpers.log("StatusBarIconVisibilityResolver: $reason; using legacy fallback")
        return null
    }
}
