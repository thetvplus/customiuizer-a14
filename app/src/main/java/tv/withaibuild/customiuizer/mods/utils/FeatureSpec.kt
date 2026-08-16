package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Lightweight declaration of a feature without creating its runtime [FeatureDefinition].
 *
 * A [FeatureSpec] holds only fixed metadata and two functions: one that decides whether the
 * feature is enabled, and one that creates the real [FeatureDefinition] only when enabled.
 * Disabled features therefore never allocate their installer closure, hook objects,
 * receivers, observers or context holders.
 */
interface FeatureSpec {

    /** Typed identity.  Equality is identity-based. */
    val id: FeatureId

    /** Human-readable name for diagnostics. */
    val name: String

    /** The preference key that enables this feature, or null if always enabled. */
    val preferenceKey: String?

    /** The process/apk in which this feature must be installed. */
    val target: FeatureTarget

    /** The earliest lifecycle phase at which this feature may be installed. */
    val phase: InstallPhase

    /**
     * Whether the feature should be installed now.  This is the gate: the registry calls this
     * before [create] so disabled features cost one map lookup and no [FeatureDefinition].
     */
    fun isEnabled(prefs: PrefMap): Boolean

    /** Create the runtime [FeatureDefinition].  Only called when [isEnabled] is true. */
    fun create(): FeatureDefinition
}
