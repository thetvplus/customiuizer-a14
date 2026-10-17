package tv.withaibuild.customiuizer.mods.battery

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Cold resolver for the Battery style frozen ABI.
 *
 * Resolves the three exact child-view field names on the target
 * `com.android.systemui.statusbar.views.MiuiBatteryMeterView` class. It does not validate
 * the declared field type and does not hold any runtime instance or view state.
 */
internal object BatteryStyleResolver {

    private const val TARGET_CLASS_NAME = "com.android.systemui.statusbar.views.MiuiBatteryMeterView"
    private const val FIELD_DIGIT = "mBatteryTextDigitView"
    private const val FIELD_PERCENT = "mBatteryPercentView"
    private const val FIELD_MARK = "mBatteryPercentMarkView"

    /**
     * Resolves the target class and its three child-view fields.
     *
     * @return A frozen [BatteryStyleAbi] if all fields resolve, or `null` to select the legacy
     *         `XposedHelpers.getObjectField` fallback. Ordinary failures are logged once.
     *         Fatal errors ([OutOfMemoryError], [ThreadDeath], [VirtualMachineError]) are
     *         propagated immediately and are never swallowed or converted into a fallback.
     */
    @JvmStatic
    fun resolve(classLoader: ClassLoader?): BatteryStyleAbi? =
        resolve(classLoader, TARGET_CLASS_NAME)

    /**
     * Testable overload that allows the caller to specify the target class name.
     * Production code uses the overload without [targetClassName].
     */
    @JvmStatic
    fun resolve(classLoader: ClassLoader?, targetClassName: String): BatteryStyleAbi? {
        return try {
            val targetClass = XposedHelpers.findClassIfExists(targetClassName, classLoader)
                ?: return missing("target class not found: $targetClassName")

            val digitField = XposedHelpers.findFieldIfExists(targetClass, FIELD_DIGIT)
                ?: return missing("field not found: $FIELD_DIGIT on $targetClassName")
            val percentField = XposedHelpers.findFieldIfExists(targetClass, FIELD_PERCENT)
                ?: return missing("field not found: $FIELD_PERCENT on $targetClassName")
            val markField = XposedHelpers.findFieldIfExists(targetClass, FIELD_MARK)
                ?: return missing("field not found: $FIELD_MARK on $targetClassName")

            BatteryStyleAbi(
                resolutionRootClass = targetClass,
                digitField = digitField,
                percentField = percentField,
                markField = markField,
            )
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    private fun missing(reason: String): BatteryStyleAbi? {
        XposedHelpers.log("BatteryStyleResolver: $reason; using legacy fallback")
        return null
    }
}
