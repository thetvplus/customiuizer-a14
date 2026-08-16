package tv.withaibuild.customiuizer.mods.clock

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Narrow, subsystem-specific publication of the frozen [ClockEffect].
 *
 * The publication holds only the immutable [ClockAbi] and the published [ClockEffect].  It never
 * retains a [Context], clock [View], controller, or calendar instance.  The first successful
 * calibration is cached; per-target failures are remembered so that repeated callbacks on a failed
 * target do not retry expensive runtime reflection.
 */
internal class ClockEffectPublication(
    private val abi: ClockAbi,
) {

    @Volatile
    private var effect: ClockEffect? = abi.calendarCold?.let { ClockEffect(abi, it) }

    private val failedTargetMask = AtomicInteger(0)

    /**
     * Reads `mClockListeners` from a controller instance using the frozen
     * [ControllerCapability.clockListenersField].
     *
     * Returns the value only when its runtime type is [List].  No `XposedHelpers`, `ClassLoader`
     * lookup, or member discovery is performed.  Fatal errors are rethrown with exact identity.
     */
    fun readClockListeners(controller: Any): List<*>? {
        val field = abi.controller.clockListenersField
        if (!field.declaringClass.isInstance(controller)) return null
        return try {
            val value = field.get(controller)
            if (value is List<*>) value else null
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    /**
     * Number of times the slow calibration path has run. Diagnostic counter; not read by the hot
     * production path.
     */
    internal var calibrationAttempts: Int = 0
        private set

    /**
     * Direct volatile read of the already-published effect.  No synchronization, target scan, or
     * allocation is performed.
     */
    fun currentEffect(): ClockEffect? = effect

    /**
     * Returns the published [ClockEffect] for [clock].
     *
     * Fast path: return a previously published effect.
     * Slow path: select the most-specific frozen target, read the controller and calendar, perform
     * a one-time runtime calendar calibration if needed, and publish the effect.
     *
     * Nonfatal calibration failures mark the selected target as failed and return `null`.  Fatal
     * errors are rethrown with exact original identity.
     */
    fun resolveForClock(clock: Any, actualContextClass: Class<*>): ClockEffect? {
        val published = effect
        if (published != null) return published

        val targetIndex = selectTargetIndex(clock)
        if (targetIndex < 0) return null

        val mask = 1 shl targetIndex
        if (failedTargetMask.get() and mask != 0) return null

        synchronized(this) {
            val doubleCheck = effect
            if (doubleCheck != null) return doubleCheck
            if (failedTargetMask.get() and mask != 0) return null

            calibrationAttempts++

            return try {
                val target = abi.targets[targetIndex]

                val controller = target.controllerField.get(clock)
                if (controller == null) {
                    markFailed(mask)
                    return null
                }

                val calendarObject = abi.controller.calendarField.get(controller)
                if (calendarObject == null) {
                    markFailed(mask)
                    return null
                }

                val calendarCapability = abi.calendarCold
                    ?: ClockResolver.resolveCalendarFromRuntime(calendarObject, actualContextClass)
                    ?: run {
                        markFailed(mask)
                        return null
                    }

                val newEffect = ClockEffect(abi, calendarCapability)
                effect = newEffect
                newEffect
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                markFailed(mask)
                null
            }
        }
    }

    /**
     * Selects the index of the most-specific target class for [clock].
     *
     * Bounded array scan with no temporary collections.  Order-independent semantics match
     * [ClockEffect.selectTarget]: duplicate or incomparable matching targets fail closed.
     */
    private fun selectTargetIndex(clock: Any): Int {
        var selectedIndex = -1
        var selectedClass: Class<*>? = null

        var i = 0
        val size = abi.targets.size
        while (i < size) {
            val candidate = abi.targets[i]
            if (candidate.targetClass.isInstance(clock)) {
                val current = selectedClass
                if (current == null) {
                    selectedIndex = i
                    selectedClass = candidate.targetClass
                } else if (current == candidate.targetClass) {
                    return -1
                } else if (current.isAssignableFrom(candidate.targetClass)) {
                    selectedIndex = i
                    selectedClass = candidate.targetClass
                } else if (!candidate.targetClass.isAssignableFrom(current)) {
                    return -1
                }
            }
            i++
        }

        return selectedIndex
    }

    private fun markFailed(mask: Int) {
        failedTargetMask.updateAndGet { it or mask }
    }
}
