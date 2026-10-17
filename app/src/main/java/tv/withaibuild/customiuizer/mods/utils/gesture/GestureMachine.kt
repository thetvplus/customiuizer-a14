package tv.withaibuild.customiuizer.mods.utils.gesture

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Per-ClassLoader orchestrator that combines:
 *  - config snapshotting at [GestureAction.DOWN]
 *  - dependency preparation with `Ready / NotReady / FailedTransient`
 *  - the pure [GestureStateMachine]
 *  - the [GestureSideEffectGate]
 *  - a single [GestureEffectExecutor]
 *
 * The [context] passed to [dispatch] is forwarded to the resolver and the effect
 * executor.  The orchestrator itself does not call Android services, but it does
 * need the Android Context for the concrete resolver/executor supplied by the caller.
 */
class GestureMachine(
    private val classLoaderIdentity: String,
    private val configResolver: () -> GestureConfig,
    private val depsResolver: GestureDependenciesResolver,
    private val effectExecutor: GestureEffectExecutor,
    private val gate: GestureSideEffectGate = GestureSideEffectGate(),
    private val arbiter: PhysicalGestureArbiter? = null,
) {

    private val snapshots = mutableMapOf<Int, GestureSnapshot>()
    private val dependencies = mutableMapOf<Int, GestureDependencies>()
    private val configs = mutableMapOf<Int, GestureConfig>()

    /**
     * Prepare dependencies for the given owner at a safe lifecycle point.
     *
     * Returns `true` if dependencies are now ready.  This is the only place where
     * cold reflection / dependency resolution should happen.
     */
    fun prepare(ownerId: Int, context: Any): Boolean {
        return ensureDependencies(ownerId, context) != null
    }

    /**
     * Observe one [event] without changing the authoritative [GestureSnapshot].
     *
     * This is the entry point for [GestureEntry.STATUS_BAR_INTERCEPT]: it lets the
     * intercept path see what the state machine would do, but it must not advance
     * the real session, execute business side effects, or consume the physical event.
     */
    fun observe(event: GestureEvent, context: Any): List<GestureCommand> {
        val ownerId = event.ownerId

        val config = configResolver()

        val deps = dependencies[ownerId] ?: return emptyList()

        val current = snapshots[ownerId] ?: GestureSnapshot()
        val (candidate, commands) = GestureStateMachine.process(
            current,
            event,
            config,
            deps.toGeometry(),
        )

        return gate.filter(event.entry, ownerId, event, commands)
    }

    /**
     * Process one [event] and execute the allowed side-effects through [effectExecutor].
     *
     * This is the authoritative entry point and is only allowed for
     * [GestureEntry.STATUS_BAR_TOUCH] and [GestureEntry.CONTROL_CENTER_TOUCH].
     */
    fun dispatch(event: GestureEvent, context: Any) {
        require(event.entry != GestureEntry.STATUS_BAR_INTERCEPT) {
            "STATUS_BAR_INTERCEPT must use observe(), not dispatch()"
        }

        val ownerId = event.ownerId

        if (event.actionMasked == GestureAction.DOWN) {
            configs[ownerId] = configResolver()
        }

        val config = configs[ownerId] ?: return

        if (dependencies[ownerId] == null) {
            passThrough(event, config)
            return
        }
        val deps = dependencies[ownerId]!!

        val current = snapshots[ownerId] ?: GestureSnapshot()

        if (event.actionMasked == GestureAction.DOWN) {
            arbiter?.releaseOwner(ownerId)
            val currentBrightness = readBrightness(deps)
            val (candidate, commands) = GestureStateMachine.process(
                current,
                event,
                config,
                deps.toGeometry(currentBrightness),
            )
            val validDown = candidate.state != GestureState.IDLE && GestureCommand.BeginTracking in commands
            if (!validDown) {
                snapshots[ownerId] = GestureSnapshot()
                passThrough(event, config)
                return
            }
            if (arbiter != null && !arbiter.tryAcquireOnDown(ownerId, event)) {
                snapshots[ownerId] = GestureSnapshot()
                return
            }
            snapshots[ownerId] = candidate
            val allowed = gate.filter(event.entry, ownerId, event, commands)
            effectExecutor.execute(allowed, deps, config, event)
            return
        }

        if (arbiter != null && !arbiter.isOwner(ownerId, event)) {
            return
        }

        val (next, commands) = GestureStateMachine.process(
            current,
            event,
            config,
            deps.toGeometry(),
        )
        snapshots[ownerId] = next

        val allowed = gate.filter(event.entry, ownerId, event, commands)
        effectExecutor.execute(allowed, deps, config, event)

        if (arbiter != null && (GestureCommand.Reset in allowed || event.actionMasked == GestureAction.UP || event.actionMasked == GestureAction.CANCEL)) {
            arbiter.release(ownerId, event)
        }
    }

    private fun readBrightness(deps: GestureDependencies): Float {
        val method = deps.getBrightnessMethod ?: return -1f
        return invokePrepared(method, deps.displayManager, deps.displayId) as? Float ?: -1f
    }

    private fun passThrough(event: GestureEvent, config: GestureConfig) {
        val deps = dependencies[event.ownerId] ?: return
        val allowed = gate.filter(event.entry, event.ownerId, event, listOf(GestureCommand.PassThrough))
        effectExecutor.execute(allowed, deps, config, event)
    }

    private fun ensureDependencies(ownerId: Int, context: Any): GestureDependencies? {
        val existing = dependencies[ownerId]
        if (existing != null && existing.classLoaderIdentity == classLoaderIdentity) {
            return existing
        }

        return try {
            when (val result = depsResolver.prepare(ownerId, classLoaderIdentity, context)) {
                is GestureDependenciesResult.Ready -> {
                    dependencies[ownerId] = result.dependencies
                    result.dependencies
                }
                is GestureDependenciesResult.NotReady,
                is GestureDependenciesResult.FailedTransient -> null
            }
        } catch (err: Throwable) {
            when (err) {
                is OutOfMemoryError, is ThreadDeath, is VirtualMachineError -> throw err
                else -> null
            }
        }
    }

    private fun invokePrepared(method: Method?, receiver: Any?, vararg args: Any?): Any? {
        if (method == null) return null
        return try {
            method.invoke(receiver, *args)
        } catch (err: Throwable) {
            when (err) {
                is OutOfMemoryError, is ThreadDeath, is VirtualMachineError -> throw err
                is InvocationTargetException -> when (val cause = err.targetException) {
                    is OutOfMemoryError, is ThreadDeath, is VirtualMachineError -> throw cause
                    else -> null
                }
                else -> null
            }
        }
    }

    /** Expose the current snapshot for deterministic stress tests and diagnostics. */
    internal fun snapshot(ownerId: Int): GestureSnapshot = snapshots[ownerId] ?: GestureSnapshot()

    /** Expose the ClassLoader identity for runtime holder tests and diagnostics. */
    internal fun classLoaderIdentity(): String = classLoaderIdentity

    /** Expose the last resolved config for a given owner (null until the first DOWN). */
    internal fun resolvedConfig(ownerId: Int): GestureConfig? = configs[ownerId]

    /** Drop all per-owner state, arbiter tokens, fingerprints and the side-effect gate. */
    fun clear(ownerId: Int) {
        snapshots.remove(ownerId)
        dependencies.remove(ownerId)
        configs.remove(ownerId)
        gate.clearOwner(ownerId)
        arbiter?.releaseOwner(ownerId)
    }

    /** Reset the whole machine, e.g. when the ClassLoader is torn down. */
    fun clear() {
        snapshots.clear()
        dependencies.clear()
        configs.clear()
        gate.clear()
        arbiter?.releaseAll()
    }
}
