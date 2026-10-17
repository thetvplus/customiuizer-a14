package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Sink for the commands produced by the gesture state machine.
 *
 * This interface keeps the pure state machine decoupled from Android calls.  The
 * concrete implementation is responsible for translating [GestureCommand] into
 * brightness, volume and global actions using the prepared [GestureDependencies].
 */
fun interface GestureEffectExecutor {
    fun execute(
        commands: List<GestureCommand>,
        dependencies: GestureDependencies,
        config: GestureConfig,
        context: Any?,
    )
}
