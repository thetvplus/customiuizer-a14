package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Result of an attempt to prepare runtime dependencies for a gesture owner.
 */
sealed class GestureDependenciesResult {
    data class Ready(val dependencies: GestureDependencies) : GestureDependenciesResult()
    object NotReady : GestureDependenciesResult()
    data class FailedTransient(val reason: String) : GestureDependenciesResult()
}
