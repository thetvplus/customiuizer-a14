package tv.withaibuild.customiuizer.mods.utils

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

/**
 * Shared fatal-error boundary.
 *
 * Rethrows the errors that must never be swallowed by generic `catch (Throwable)` blocks:
 * `OutOfMemoryError`, `ThreadDeath` and any `VirtualMachineError`.  Wrapped fatal errors are
 * unwrapped so an outer `catch (Throwable)` cannot accidentally convert them into a non-fatal
 * failure state.
 */
object FatalErrors {

    /**
     * If [t] is a fatal error, rethrow it immediately without modification.
     *
     * Call this as the first statement inside every `catch (Throwable)` that is not already
     * dedicated to fatal propagation.
     */
    @JvmStatic
    fun rethrowIfFatal(t: Throwable) {
        when (t) {
            is OutOfMemoryError -> throw t
            is ThreadDeath -> throw t
            is VirtualMachineError -> throw t
        }
    }

    /**
     * Unwrap [InvocationTargetException] or [ExecutionException] and rethrow if the root cause
     * is fatal.  Returns the original throwable if no fatal cause was found, so the caller can
     * still log or handle it.
     */
    @JvmStatic
    @JvmOverloads
    fun unwrapAndRethrowIfFatal(t: Throwable, maxDepth: Int = 4): Throwable {
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < maxDepth) {
            rethrowIfFatal(current)
            current = when (current) {
                is InvocationTargetException -> current.cause
                is ExecutionException -> current.cause
                is XposedHelpers.InvocationTargetError -> current.cause
                else -> null
            }
            depth++
        }
        return t
    }
}
