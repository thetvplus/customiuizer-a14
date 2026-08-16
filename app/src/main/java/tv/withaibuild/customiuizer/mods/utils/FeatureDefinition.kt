package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Declaration of one feature that can be installed by the module.
 *
 * A feature is the unit of "one preference, one hook site, one process".  The [id] is its typed
 * identity; [name] is only for diagnostics. The registry handles ordering and process-level
 * installation idempotency.
 *
 * A [FeatureDefinition] is also a [FeatureSpec] whose [create] returns itself.  This lets the
 * install registry accept either a ready-to-install [FeatureDefinition] or a lazy [FeatureSpec]
 * that defers construction.
 */
interface FeatureDefinition : FeatureSpec {

    override fun create(): FeatureDefinition = this

    /** Typed identity.  Equality is identity-based (usually an `object` or `data object`). */
    override val id: FeatureId

    /** Human-readable name for diagnostics and late-preference tracking. */
    override val name: String

    /** The preference key that enables this feature, or null if it is always enabled. */
    override val preferenceKey: String?

    /** The process/apk in which this feature must be installed. */
    override val target: FeatureTarget

    /** The earliest lifecycle phase at which this feature may be installed. */
    override val phase: InstallPhase

    /**
     * Whether the feature should be installed now, based on the current preference snapshot.
     * The registry calls this before [install] so disabled features cost one map lookup.
     */
    override fun isEnabled(prefs: PrefMap): Boolean

    /**
     * Attempt to install the feature. Ordinary failures are isolated by [FeatureInstallRegistry];
     * [OutOfMemoryError] is rolled back to a transient state and rethrown.
     */
    fun install(): FeatureInstallResult

}
