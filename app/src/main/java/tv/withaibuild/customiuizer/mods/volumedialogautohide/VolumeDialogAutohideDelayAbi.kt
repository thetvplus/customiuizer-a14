package tv.withaibuild.customiuizer.mods.volumedialogautohide

import java.lang.reflect.Field

/**
 * Frozen cold-resolved ABI for the VolumeDialogAutohideDelay hook.
 *
 * Holds only the resolution root and the two primitive-boolean [Field] references.
 * It does not hold runtime instances, a [ClassLoader], or mutable state.
 *
 * A resolved [Field] may legitimately be declared in a superclass of the root;
 * the fast path only requires the runtime receiver class to equal the root.
 */
internal class VolumeDialogAutohideDelayAbi(
    val resolutionRootClass: Class<*>,
    val mHoveringField: Field,
    val mExpandedField: Field,
)
