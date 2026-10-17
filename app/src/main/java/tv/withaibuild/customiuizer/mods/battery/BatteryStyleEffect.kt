package tv.withaibuild.customiuizer.mods.battery

import android.view.ViewGroup
import android.widget.TextView
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field

/**
 * Minimal effect for the three Battery style child-view field reads.
 *
 * For each helper invocation the effect selects exactly one access mode:
 * - FAST: frozen [Field.get] when the runtime owner class is exactly the resolution root.
 * - LEGACY_FALLBACK: [XposedHelpers.getObjectField] for any subclass or when no ABI exists.
 *
 * All three child views are read with the same mode. There is no mixed access, no
 * runtime ABI cache, and no per-call allocation.
 */
internal class BatteryStyleEffect(val abi: BatteryStyleAbi?) {

    private companion object {
        const val FIELD_DIGIT = "mBatteryTextDigitView"
        const val FIELD_PERCENT = "mBatteryPercentView"
        const val FIELD_MARK = "mBatteryPercentMarkView"
    }

    /**
     * Returns `true` if the frozen ABI fast path may be used for [parent].
     */
    fun useFastPath(parent: ViewGroup): Boolean {
        val a = abi ?: return false
        return parent.javaClass === a.resolutionRootClass
    }

    fun readDigitView(parent: ViewGroup, useFast: Boolean): TextView? =
        if (useFast) readFast(abi!!.digitField, parent) else readLegacy(parent, FIELD_DIGIT)

    fun readPercentView(parent: ViewGroup, useFast: Boolean): TextView? =
        if (useFast) readFast(abi!!.percentField, parent) else readLegacy(parent, FIELD_PERCENT)

    fun readMarkView(parent: ViewGroup, useFast: Boolean): TextView? =
        if (useFast) readFast(abi!!.markField, parent) else readLegacy(parent, FIELD_MARK)

    private fun readFast(field: Field, parent: ViewGroup): TextView? {
        return try {
            field.get(parent) as? TextView
        } catch (e: IllegalAccessException) {
            // Preserve the legacy XposedHelpers.getObjectField contract for IllegalAccessException:
            // log once and throw IllegalAccessError.
            XposedHelpers.log(e)
            throw IllegalAccessError(e.message)
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }

    private fun readLegacy(parent: ViewGroup, fieldName: String): TextView? =
        XposedHelpers.getObjectField(parent, fieldName) as? TextView
}
