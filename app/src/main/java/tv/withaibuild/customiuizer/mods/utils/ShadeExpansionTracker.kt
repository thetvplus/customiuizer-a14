package tv.withaibuild.customiuizer.mods.utils

/**
 * Pure state machine that records whether a continuous value has crossed a fixed threshold.
 *
 * It is intentionally Android-free so it can be unit-tested on the JVM.
 */
class ShadeExpansionTracker(private val threshold: Float) {

    enum class State { BELOW, ABOVE }

    private var lastState: State = State.BELOW

    /**
     * Call with the latest value. Returns `true` when the value crosses the threshold
     * (either direction) from the previous sample. The internal state is always updated.
     */
    fun update(value: Float): Boolean {
        val newState = if (value > threshold) State.ABOVE else State.BELOW
        val changed = newState != lastState
        lastState = newState
        return changed
    }

    /** Current side of the threshold, exposed for diagnostics. */
    fun currentState(): State = lastState
}
