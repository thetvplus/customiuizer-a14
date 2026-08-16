package tv.withaibuild.customiuizer.mods.statusbariconvisibility

import java.lang.reflect.Field

/**
 * Frozen cold-resolved ABI for the HideIconsSignal hook.
 *
 * Holds exactly two resolution roots and the resolved [Field] references for the
 * StatusBarMobileView and MobileIconState field ABI. It does not hold runtime
 * instances, [ClassLoader] references, or mutable state.
 *
 * A resolved [Field] may legitimately be declared in a superclass of its
 * resolution root; the fast path only requires the runtime receiver class to
 * equal the resolution root.
 */
internal class StatusBarIconVisibilityAbi(
    val statusBarMobileViewResolutionRootClass: Class<*>,
    val mobileIconStateResolutionRootClass: Class<*>,
    val mStateField: Field,
    val wifiAvailableField: Field,
    val subIdField: Field,
    val visibleField: Field,
    val roamingField: Field,
    val volteField: Field,
    val speechHdField: Field,
)
