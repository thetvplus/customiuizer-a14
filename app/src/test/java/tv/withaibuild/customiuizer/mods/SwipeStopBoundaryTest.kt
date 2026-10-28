package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test

class SwipeStopBoundaryTest {
    @Test fun onlyACompletedLeftOrRightSideActionRunsAfterNativeSuccess() {
        val actions = mutableListOf<Int>()
        val hook = LauncherGestureHooks.swipeStopResultHook { actions.add(it) }
        for ((completed, direction) in listOf(false to 0, true to -1, true to 2, true to 99)) {
            val chain = Chain(completed, direction)
            assertSame(chain.result, hook.intercept(chain)); assertEquals(1, chain.nativeCalls)
            assertTrue(actions.isEmpty())
        }
        for (side in 0..1) {
            val chain = Chain(true, side)
            assertSame(chain.result, hook.intercept(chain)); assertEquals(1, chain.nativeCalls)
        }
        assertEquals(listOf(0, 1), actions)
    }
    @Test fun failedNativeGestureCannotLaunchAnActionOrReadItsOwnerArguments() {
        var actions = 0
        val hook = LauncherGestureHooks.swipeStopResultHook { actions++ }
        val failure = IllegalStateException("native gesture failed")
        val chain = Chain(true, 0, failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { hook.intercept(chain) })
        assertEquals(1, chain.nativeCalls); assertEquals(0, chain.argumentReads); assertEquals(0, actions)
    }
    @Test fun ordinaryActionFailureKeepsTheNativeResultAndFatalStillPropagates() {
        val chain = Chain(true, 0)
        val hook = LauncherGestureHooks.swipeStopResultHook { error("unavailable action") }
        assertSame(chain.result, hook.intercept(chain)); assertEquals(1, chain.nativeCalls)
        val fatal = OutOfMemoryError("fatal")
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            LauncherGestureHooks.swipeStopResultHook { throw fatal }.intercept(Chain(true, 1))
        })
    }
    private class Chain(private val completed: Boolean, private val direction: Int,
        private val failure: Throwable? = null) : XposedInterface.Chain {
        val result = Any(); var nativeCalls = 0; var argumentReads = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any = error("result callback must not read an arbitrary owner")
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? { argumentReads++; return if (index == 1) completed else direction }
        override fun proceed(): Any? { nativeCalls++; failure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
