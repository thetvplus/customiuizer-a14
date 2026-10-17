package tv.withaibuild.customiuizer.mods.utils

/**
 * Process-scoped storage of feature installation state.
 *
 * One [FeatureInstallState] object lives per process in the module's class loader.  It is keyed by
 * the stable integer [FeatureId.id] rather than by the [FeatureId] object.  This keeps lookups
 * fast and avoids the per-installer state maps that used to be created by each
 * [FeatureInstallRegistry].
 *
 * The current implementation uses a plain [HashMap] because it is available both on Android and in
 * the JVM unit-test runtime.  Once a SparseArray/BitSet implementation is available in both
 * runtimes, the storage can be switched without changing callers.
 */
object FeatureInstallState {

    private val states = HashMap<Int, FeatureState>()

    /** Initialize a feature without overwriting state recorded by an earlier registry. */
    @JvmStatic
    fun initialize(featureId: FeatureId) {
        synchronized(states) {
            if (!states.containsKey(featureId.id)) {
                states[featureId.id] = FeatureState.NOT_INSTALLED
            }
        }
    }

    /**
     * Atomically claim installation for a new or transiently failed feature.
     * Returns the state observed before the claim.
     */
    @JvmStatic
    fun beginInstall(featureId: FeatureId): FeatureState = synchronized(states) {
        val previous = states[featureId.id] ?: FeatureState.NOT_INSTALLED
        if (previous == FeatureState.NOT_INSTALLED || previous == FeatureState.FAILED_TRANSIENT) {
            states[featureId.id] = FeatureState.INSTALLING
        }
        previous
    }

    /** Return the current state for [featureId], defaulting to [FeatureState.NOT_INSTALLED]. */
    @JvmStatic
    fun get(featureId: Int): FeatureState = synchronized(states) {
        states[featureId] ?: FeatureState.NOT_INSTALLED
    }

    /** Set the current state for [featureId]. */
    @JvmStatic
    fun set(featureId: Int, state: FeatureState) = synchronized(states) {
        states[featureId] = state
    }

    /** Convenience: get the state for a [FeatureId]. */
    @JvmStatic
    fun get(featureId: FeatureId): FeatureState = get(featureId.id)

    /** Convenience: set the state for a [FeatureId]. */
    @JvmStatic
    fun set(featureId: FeatureId, state: FeatureState) = set(featureId.id, state)

}
