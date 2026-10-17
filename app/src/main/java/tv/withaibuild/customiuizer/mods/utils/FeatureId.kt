package tv.withaibuild.customiuizer.mods.utils

/**
 * Typed identity for a feature.
 *
 * A feature is identified by its [FeatureId] value, not by a plain string.  Implementations are
 * usually `object` or `data object` declarations so equality is identity-based, but any stable
 * implementation is allowed.
 */
/**
 * Typed identity for a feature.
 *
 * The [id] is a stable, compact integer used for process-scoped bit sets and state arrays.
 * It must be unique and should not change once assigned.
 */
interface FeatureId {

    /** Stable compact integer for arrays and bit sets. */
    val id: Int

    /** Human-readable name for diagnostics. */
    val name: String
}
