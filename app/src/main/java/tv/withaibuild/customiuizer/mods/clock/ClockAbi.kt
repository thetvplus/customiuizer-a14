package tv.withaibuild.customiuizer.mods.clock

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Frozen capability for `com.android.systemui.statusbar.policy.MiuiStatusBarClockController`.
 *
 * No Android owner instances are retained; only the resolved [Class], [Field] and [Method]
 * references.  `mContext` is deliberately excluded from the core periodic ABI.
 */
internal data class ControllerCapability(
    val controllerClass: Class<*>,
    val calendarField: Field,
    val clockListenersField: Field,
    val is24Field: Field,
)

/**
 * Frozen capability for a single clock hook target.
 *
 * Targets are resolved independently because `MiuiStatusBarClock` may re-declare or inherit
 * `mMiuiStatusBarClockController` and `updateTime()` differently from `MiuiClock`.
 */
internal data class ClockTargetCapability(
    val targetClass: Class<*>,
    val controllerField: Field,
    val updateTimeMethod: Method,
)

/**
 * Frozen calendar method capability.
 *
 * May be resolved from the cold declared type of `mCalendar` or from a one-time runtime
 * calibration using a real calendar object.  Only [Class] and [Method] references are kept.
 */
internal data class CalendarCapability(
    val calendarClass: Class<*>,
    val setTimeInMillisMethod: Method,
    val formatMethod: Method,
)

/**
 * Aggregate frozen ABI for the SystemClock Architecture C core.
 *
 * @property controller the resolved controller capability; always present when the ABI is built.
 * @property targets zero or more resolved clock hook targets; each non-null element is fully
 *   resolved for its target class.
 * @property calendarCold the calendar capability resolved from the declared `mCalendar` type, or
 *   `null` if the declared type does not contain the required methods.  A missing cold calendar
 *   does not invalidate the controller or target capabilities; it can be supplied later by a
 *   one-time runtime calibration.
 */
internal data class ClockAbi(
    val controller: ControllerCapability,
    val targets: Array<ClockTargetCapability>,
    val calendarCold: CalendarCapability?,
)
