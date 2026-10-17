package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Lifecycle holder for the Control Center gesture runtime.
 *
 * It guarantees that only one [GestureMachine] is active at a time per [PhysicalGestureArbiter],
 * that a new ClassLoader clears the previous machine, and that the same ClassLoader does not
 * create duplicated hooks.
 */
internal class ControlCenterGestureRuntimeHolder(
    private val configPublisher: GestureConfigPublisher,
    private val effectExecutor: GestureEffectExecutor,
    private val arbiter: PhysicalGestureArbiter,
    private val dependenciesResolver: GestureDependenciesResolver,
    private val installHooks: (ClassLoader, GestureMachine) -> Unit,
) {

    data class ControlCenterGestureRuntime(
        val classLoader: ClassLoader,
        val machine: GestureMachine,
    )

    private var activeRuntime: ControlCenterGestureRuntime? = null
    private var nextRuntimeId = 0L

    /**
     * Bind a Control Center gesture runtime to the supplied [classLoader].
     *
     * If the same loader is already bound, the existing runtime is returned.
     * If a different loader is supplied, the old machine is cleared and a new runtime is created.
     */
    fun bind(classLoader: ClassLoader): ControlCenterGestureRuntime {
        val existing = activeRuntime
        if (existing?.classLoader === classLoader) {
            return existing
        }

        existing?.machine?.clear()
        nextRuntimeId++
        val identity = "cc-$nextRuntimeId"
        val machine = GestureMachine(
            classLoaderIdentity = identity,
            configResolver = { configPublisher.get() },
            depsResolver = dependenciesResolver,
            effectExecutor = effectExecutor,
            arbiter = arbiter,
        )
        val runtime = ControlCenterGestureRuntime(classLoader, machine)
        try {
            installHooks(classLoader, machine)
            activeRuntime = runtime
        } catch (e: Throwable) {
            machine.clear()
            throw e
        }
        return runtime
    }

    /**
     * Explicitly detach the current runtime, e.g. when the host plugin/View is destroyed.
     *
     * This is the symmetric cleanup for [bind] and prevents stale gesture state from
     * outliving the owner.
     */
    fun unbind() {
        activeRuntime?.machine?.clear()
        activeRuntime = null
    }

    /** Expose the current runtime for diagnostics and tests. */
    fun activeRuntime(): ControlCenterGestureRuntime? = activeRuntime
}
