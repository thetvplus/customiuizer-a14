package tv.withaibuild.customiuizer.mods.volumedialogautohide

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field

/**
 * Cold resolver for the VolumeDialogAutohideDelay frozen ABI.
 *
 * Reflection and class discovery happens once during hook installation. The output
 * is an immutable [VolumeDialogAutohideDelayAbi]. Hot paths must not perform any
 * further discovery, classloader lookups, or reflection resolution.
 *
 * The resolver returns `null` (expected miss / ordinary failure) to select the
 * complete legacy XposedHelpers path. Fatal errors are propagated immediately.
 */
internal object VolumeDialogAutohideDelayResolver {

    private const val VOLUME_DIALOG_CLASS = "com.android.systemui.miui.volume.MiuiVolumeDialogImpl"
    private const val METHOD_COMPUTE_TIMEOUT = "computeTimeoutH"
    private const val FIELD_HOVERING = "mHovering"
    private const val FIELD_EXPANDED = "mExpanded"

    /**
     * Resolves the frozen ABI from [classLoader].
     *
     * @return A frozen [VolumeDialogAutohideDelayAbi] if the root class, the
     *         zero-explicit-parameter computeTimeoutH surface, and the two
     *         primitive-boolean fields resolve, or `null` to select the complete
     *         legacy XposedHelpers fallback.
     */
    @JvmStatic
    fun resolve(classLoader: ClassLoader?): VolumeDialogAutohideDelayAbi? =
        resolveInternal(classLoader, VOLUME_DIALOG_CLASS)

    /**
     * Testable overload that allows the caller to specify the resolution root class.
     */
    @JvmStatic
    fun resolve(resolutionRootClass: Class<*>): VolumeDialogAutohideDelayAbi? =
        resolveFromClass(resolutionRootClass)

    private fun resolveInternal(classLoader: ClassLoader?, targetClassName: String): VolumeDialogAutohideDelayAbi? {
        return try {
            val resolutionRootClass = XposedHelpers.findClassIfExists(targetClassName, classLoader)
                ?: run { missing("target class not found: $targetClassName"); return null }
            resolveFromClass(resolutionRootClass)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    private fun resolveFromClass(resolutionRootClass: Class<*>): VolumeDialogAutohideDelayAbi? {
        return try {
            // Verify the zero-explicit-parameter computeTimeoutH surface using the
            // same method-resolution semantics as XposedHelpers.findAndHookMethod.
            // The Method object is not retained; return type is intentionally not inspected.
            XposedHelpers.findMethodExactIfExists(resolutionRootClass, METHOD_COMPUTE_TIMEOUT)
                ?: run { missing("method $METHOD_COMPUTE_TIMEOUT not found on ${resolutionRootClass.name}"); return null }

            val mHoveringField = resolvePrimitiveBooleanField(resolutionRootClass, FIELD_HOVERING)
                ?: run { missing("$FIELD_HOVERING not a primitive boolean on ${resolutionRootClass.name}"); return null }
            val mExpandedField = resolvePrimitiveBooleanField(resolutionRootClass, FIELD_EXPANDED)
                ?: run { missing("$FIELD_EXPANDED not a primitive boolean on ${resolutionRootClass.name}"); return null }

            VolumeDialogAutohideDelayAbi(resolutionRootClass, mHoveringField, mExpandedField)
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
        return if (field.type == java.lang.Boolean.TYPE) {
            field.isAccessible = true
            field
        } else {
            missing("$fieldName has type ${field.type.name}, not boolean, on ${resolutionRootClass.name}")
            null
        }
    }

    private fun missing(message: String) {
        XposedHelpers.log("VolumeDialogAutohideDelayResolver: $message")
    }
}
