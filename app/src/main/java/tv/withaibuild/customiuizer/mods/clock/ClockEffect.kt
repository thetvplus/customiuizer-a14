package tv.withaibuild.customiuizer.mods.clock

import android.content.Context
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Hot-path effect for the Architecture C SystemClock core.
 *
 * Holds only frozen [Class] / [Field] / [Method] references resolved by [ClockResolver].  It never
 * performs runtime member discovery, generic reflection, or string-based lookups.  Android owners
 * (`Context`, clock [View], controller) are never retained as strong references; they only pass
 * through field and method invocations.
 */
internal class ClockEffect(
    internal val abi: ClockAbi,
    internal val calendar: CalendarCapability,
) {

    /** Reads `mCalendar` from a controller instance. */
    fun readCalendar(controller: Any): Any? {
        return readField(abi.controller.calendarField, controller)
    }

    /**
     * Reads `mClockListeners` from a controller instance.
     *
     * Returns the value only when its runtime type is [List].  No `ArrayList`-specific cast is
     * performed; H2 migration later decides whether to iterate by index.
     */
    fun readClockListeners(controller: Any): List<*>? {
        val field = abi.controller.clockListenersField
        if (!field.declaringClass.isInstance(controller)) return null
        return try {
            val value = field.get(controller)
            if (value is List<*>) value else null
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    /** Writes the primitive `boolean` `mIs24` field and returns `true` on success. */
    fun writeIs24(controller: Any, value: Boolean): Boolean {
        val field = abi.controller.is24Field
        if (!field.declaringClass.isInstance(controller)) return false
        if (field.type != Boolean::class.javaPrimitiveType) return false

        return try {
            field.setBoolean(controller, value)
            true
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    /**
     * Reads `mMiuiStatusBarClockController` from a clock view.
     *
     * The correct [ClockTargetCapability] is selected deterministically based on the most-specific
     * matching target class.
     */
    fun readController(clock: Any): Any? {
        val target = selectTarget(clock) ?: return null
        return readField(target.controllerField, clock)
    }

    /** Calls the frozen `updateTime()` method on a clock view. */
    fun invokeUpdateTime(clock: Any): Boolean {
        val target = selectTarget(clock) ?: return false
        val method = target.updateTimeMethod
        if (!method.declaringClass.isInstance(clock)) return false

        return try {
            method.invoke(clock)
            true
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    /** Calls the frozen `setTimeInMillis(long)` method on a calendar object. */
    fun setTimeInMillis(calendarObject: Any, millis: Long): Boolean {
        if (!calendar.calendarClass.isInstance(calendarObject)) return false

        return try {
            calendar.setTimeInMillisMethod.invoke(calendarObject, millis)
            true
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    /**
     * Calls the frozen `format` method on a calendar object.
     *
     * The calendar class is checked before invocation.  The frozen method was already validated by
     * the resolver, so no per-call parameter metadata read is performed here.  The return value is
     * not inspected; `Method.invoke` raises a nonfatal `Throwable` on argument mismatch.
     */
    fun format(
        calendarObject: Any,
        context: Context,
        out: StringBuilder,
        pattern: StringBuilder,
    ): Boolean {
        if (!calendar.calendarClass.isInstance(calendarObject)) return false

        return try {
            calendar.formatMethod.invoke(calendarObject, context, out, pattern)
            true
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    /**
     * Select the most-specific target capability for a real clock object.
     *
     * Bounded direct [Array] scan with zero temporary collections.  The algorithm is independent of
     * the target array order: if two matching target classes are comparable, the more specific one
     * wins; if they are incomparable, or if a duplicate target class is present, fail closed.
     */
    private fun selectTarget(clock: Any): ClockTargetCapability? {
        var selected: ClockTargetCapability? = null

        var i = 0
        val size = abi.targets.size
        while (i < size) {
            val candidate = abi.targets[i]
            if (candidate.targetClass.isInstance(clock)) {
                val current = selected
                if (current == null) {
                    selected = candidate
                } else if (current.targetClass == candidate.targetClass) {
                    // duplicate target class is a malformed ABI
                    return null
                } else if (current.targetClass.isAssignableFrom(candidate.targetClass)) {
                    // candidate is a strict subclass of current; it is more specific.
                    selected = candidate
                } else if (!candidate.targetClass.isAssignableFrom(current.targetClass)) {
                    // incomparable matching classes
                    return null
                }
            }
            i++
        }

        return selected
    }

    private fun readField(field: Field, target: Any): Any? {
        if (!field.declaringClass.isInstance(target)) return null
        return try {
            field.get(target)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }
}
