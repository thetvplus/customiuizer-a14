package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.ExecutionException

class FatalErrorsTest {

    @Test(expected = OutOfMemoryError::class)
    fun rethrowsOutOfMemoryError() {
        FatalErrors.rethrowIfFatal(OutOfMemoryError("oom"))
    }

    @Test(expected = ThreadDeath::class)
    fun rethrowsThreadDeath() {
        FatalErrors.rethrowIfFatal(ThreadDeath())
    }

    @Test(expected = InternalError::class)
    fun rethrowsVirtualMachineError() {
        FatalErrors.rethrowIfFatal(InternalError("vm error"))
    }

    @Test(expected = StackOverflowError::class)
    fun rethrowsStackOverflowError() {
        FatalErrors.rethrowIfFatal(StackOverflowError("stack overflow"))
    }

    @Test
    fun doesNotRethrowOrdinaryException() {
        val t = IllegalStateException("ordinary")
        FatalErrors.rethrowIfFatal(t)
    }

    @Test(expected = OutOfMemoryError::class)
    fun unwrapsInvocationTargetCauseAndRethrowsIfFatal() {
        val cause = OutOfMemoryError("wrapped oom")
        val wrapped = java.lang.reflect.InvocationTargetException(cause)
        FatalErrors.unwrapAndRethrowIfFatal(wrapped)
    }

    @Test(expected = ThreadDeath::class)
    fun unwrapsInvocationTargetCauseAndRethrowsThreadDeath() {
        val cause = ThreadDeath()
        val wrapped = java.lang.reflect.InvocationTargetException(cause)
        FatalErrors.unwrapAndRethrowIfFatal(wrapped)
    }

    @Test(expected = StackOverflowError::class)
    fun unwrapsInvocationTargetCauseAndRethrowsVirtualMachineError() {
        val cause = StackOverflowError("wrapped soe")
        val wrapped = java.lang.reflect.InvocationTargetException(cause)
        FatalErrors.unwrapAndRethrowIfFatal(wrapped)
    }

    @Test(expected = InternalError::class)
    fun unwrapsExecutionExceptionCauseAndRethrowsIfFatal() {
        val cause = InternalError("wrapped vm error")
        val wrapped = ExecutionException("wrapped", cause)
        FatalErrors.unwrapAndRethrowIfFatal(wrapped)
    }

    @Test(expected = ThreadDeath::class)
    fun unwrapsExecutionExceptionCauseAndRethrowsThreadDeath() {
        val cause = ThreadDeath()
        val wrapped = ExecutionException("wrapped", cause)
        FatalErrors.unwrapAndRethrowIfFatal(wrapped)
    }

    @Test
    fun returnsOriginalWhenNotFatal() {
        val original = IllegalArgumentException("ordinary")
        val result = FatalErrors.unwrapAndRethrowIfFatal(original)
        assertSame(original, result)
    }

    @Test
    fun doesNotLoopInfinitelyOnCircularCause() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        try {
            FatalErrors.unwrapAndRethrowIfFatal(a, maxDepth = 10)
        } catch (_: StackOverflowError) {
            fail("Must not recurse infinitely")
        }
    }
}
