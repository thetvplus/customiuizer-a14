package tv.withaibuild.customiuizer.mods.notificationautoexpand

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Frozen cold-resolved ABI for the Notification Auto-Expand hook.
 *
 * Holds only the resolution root and the three resolved members used on the FAST path:
 * the primitive-boolean `mOnKeyguard` [Field], the zero-argument `getEntry` [Method],
 * and the one-argument `setSystemExpanded` [Method] resolved with `Boolean.class`
 * parameter-type semantics.
 *
 * It does not hold runtime instances, a [ClassLoader], or mutable state.
 */
internal class NotificationAutoExpandAbi(
    val resolutionRootClass: Class<*>,
    val mOnKeyguardField: Field,
    val getEntryMethod: Method,
    val setSystemExpandedMethod: Method,
)
