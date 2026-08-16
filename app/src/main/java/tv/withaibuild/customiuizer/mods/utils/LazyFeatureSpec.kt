package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Minimal [FeatureSpec] that defers [FeatureDefinition] construction to [create].
 *
 * The [enabled] and [factory] lambdas are typically small captures such as the preference key
 * or the package name.  They must not create business installers, hook objects, receivers or
 * context holders.
 */
internal data class LazyFeatureSpec(
    override val id: FeatureId,
    override val name: String,
    override val preferenceKey: String?,
    override val target: FeatureTarget,
    override val phase: InstallPhase,
    private val enabled: (PrefMap) -> Boolean,
    private val factory: () -> FeatureDefinition,
) : FeatureSpec {

    override fun isEnabled(prefs: PrefMap): Boolean = enabled(prefs)

    override fun create(): FeatureDefinition = factory()
}
