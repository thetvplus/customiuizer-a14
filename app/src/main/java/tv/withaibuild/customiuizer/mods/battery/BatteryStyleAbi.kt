package tv.withaibuild.customiuizer.mods.battery

import java.lang.reflect.Field

/**
 * Frozen cold-resolved metadata for the three Battery style child view fields.
 *
 * Holds only the target class and the resolved [Field] references. It does not hold
 * runtime instances, view state, preferences, or the [ClassLoader] used to resolve it.
 */
internal class BatteryStyleAbi(
    val resolutionRootClass: Class<*>,
    val digitField: Field,
    val percentField: Field,
    val markField: Field,
)
