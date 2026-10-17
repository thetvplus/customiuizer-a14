package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Interface for resolving the runtime objects and geometry for a specific gesture owner.
 *
 * Implementations must be safe to call from background preparation code.  They must not
 * publish a partially-built [GestureDependencies] instance and must propagate
 * [OutOfMemoryError], [ThreadDeath] and [VirtualMachineError] unchanged.
 */
interface GestureDependenciesResolver {
    /**
     * Resolve dependencies for the given [ownerId] and [classLoaderIdentity].
     *
     * [context] is the Android [Context] supplied by the hook caller; it is typed as [Any]
     * to keep the pure model package free of a direct `android.content.Context` import.
     *
     * Returns [GestureDependenciesResult.NotReady] when the prerequisites are not yet
     * available and the caller should retry later.  Returns [GestureDependenciesResult.FailedTransient]
     * for ordinary reflection or preference failures that should disable only the
     * affected gesture features.
     */
    fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult
}
