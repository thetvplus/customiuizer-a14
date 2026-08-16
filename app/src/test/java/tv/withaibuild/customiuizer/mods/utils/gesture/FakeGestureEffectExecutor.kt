package tv.withaibuild.customiuizer.mods.utils.gesture

class FakeGestureEffectExecutor : GestureEffectExecutor {

    val brightnessApplied = mutableListOf<Float>()
    val brightnessCommitted = mutableListOf<Float>()
    val volumeAdjusted = mutableListOf<Boolean>()
    val doubleTaps = mutableListOf<DoubleTapPosition>()
    val doubleTapActions = mutableListOf<Int>()
    var longPresses = 0
    val longPressActions = mutableListOf<Int>()
    var resets = 0

    override fun execute(
        commands: List<GestureCommand>,
        dependencies: GestureDependencies,
        config: GestureConfig,
        context: Any?,
    ) {
        for (command in commands) {
            when (command) {
                is GestureCommand.ApplyTemporaryBrightness -> brightnessApplied.add(command.ratio)
                is GestureCommand.CommitBrightness -> brightnessCommitted.add(command.ratio)
                is GestureCommand.AdjustVolume -> volumeAdjusted.add(command.raise)
                is GestureCommand.TriggerDoubleTap -> {
                    doubleTaps.add(command.position)
                    doubleTapActions.add(command.actionId)
                }
                is GestureCommand.TriggerLongPress -> {
                    longPresses++
                    longPressActions.add(command.actionId)
                }
                is GestureCommand.Reset -> resets++
                else -> { /* PassThrough / BeginTracking have no side effect here */ }
            }
        }
    }
}
