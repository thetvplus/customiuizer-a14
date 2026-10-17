package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook

class LauncherWallpaperColorHotPathTest {

    private val modeField = LauncherAnimationHooks::class.java
        .getDeclaredField("wallpaperColorMode").apply { isAccessible = true }

    // Exercise both actual callbacks without installing Launcher hooks on the host JVM.
    private fun callbacks(): List<MethodHook> = (1..2).map { index ->
        val name = "${LauncherAnimationHooks::class.java.name}\$WallpaperColorModeHook\$$index"
        Class.forName(name).getDeclaredConstructor().apply { isAccessible = true }
            .newInstance() as MethodHook
    }

    private fun withMode(mode: Int, action: () -> Unit) {
        val previous = modeField.getInt(null)
        try {
            modeField.setInt(null, mode)
            action()
        } finally {
            modeField.setInt(null, previous)
        }
    }

    @Test
    fun passThroughDoesNotMaterializeArguments() {
        for (hook in callbacks()) {
            for (mode in intArrayOf(-1, 0, 1)) withMode(mode) {
                val chain = ColorChain(7)
                assertSame(chain.result, hook.intercept(chain))
                assertEquals(1, chain.proceedCount)
                assertEquals(0, chain.argumentListReads)
                assertEquals(0, chain.argumentReads)
                assertNull(chain.replacementArgs)
            }
        }
    }

    @Test
    fun unchangedForcedColorDoesNotMaterializeArguments() {
        for (hook in callbacks()) {
            for (mode in intArrayOf(2, 3, 4, Int.MAX_VALUE)) withMode(mode) {
                val requested = if (mode == 2) 2 else 0
                val chain = ColorChain(requested)
                assertSame(chain.result, hook.intercept(chain))
                assertEquals(1, chain.proceedCount)
                assertEquals(0, chain.argumentListReads)
                assertNull(chain.replacementArgs)
            }
        }
    }

    @Test
    fun changedColorCopiesArgumentsOnceAndKeepsOriginalInput() {
        for (hook in callbacks()) {
            for (mode in intArrayOf(2, 3, 4, Int.MAX_VALUE)) withMode(mode) {
                val chain = ColorChain(7)
                assertSame(chain.result, hook.intercept(chain))
                assertEquals(1, chain.proceedCount)
                assertEquals(1, chain.argumentListReads)
                assertEquals(if (mode == 2) 2 else 0, chain.replacementArgs!![0])
                assertEquals(7, chain.originalColor)
            }
        }
    }

    @Test
    fun originalExceptionsPropagateWithoutRetryingProceed() {
        val failures = listOf(RuntimeException("host failure"), OutOfMemoryError("host OOM"),
            ThreadDeath(), InternalError("host VM error"))
        for (hook in callbacks()) {
            for (mode in intArrayOf(1, 2, 3)) withMode(mode) {
                for (failure in failures) {
                    val chain = ColorChain(7, failure)
                    try {
                        hook.intercept(chain)
                        fail("Expected the original exception")
                    } catch (actual: Throwable) {
                        assertSame(failure, actual)
                    }
                    assertEquals(1, chain.proceedCount)
                }
            }
        }
    }

    private class ColorChain(
        val originalColor: Int,
        private val failure: Throwable? = null,
    ) : XposedInterface.Chain {
        val result = Any()
        private val originalArgs: List<Any?> = listOf(originalColor)
        var argumentListReads = 0
        var argumentReads = 0
        var proceedCount = 0
        var replacementArgs: Array<Any?>? = null

        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> {
            argumentListReads++
            return originalArgs
        }
        override fun getArg(index: Int): Any? {
            argumentReads++
            return originalArgs[index]
        }
        override fun proceed(): Any? {
            proceedCount++
            failure?.let { throw it }
            return result
        }
        override fun proceed(args: Array<Any?>): Any? {
            replacementArgs = args
            return proceed()
        }
        override fun proceedWith(thisObject: Any): Any? = error("not used")
        override fun proceedWith(thisObject: Any, args: Array<Any>): Any? = error("not used")
    }
}
