package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Executable

/**
 * Behavioral tests for ForceDark temporary state cleanup before fatal propagation.
 */
class ForceDarkCleanupBeforeFatalTest {

    @Test
    fun normalRunSetsAndRestoresFlag() {
        val chain = FakeChain(proceedValue = 0)

        val result = SystemDisplayHooks.withTemporaryStaticBoolean(
            FakeBuild::class.java,
            "IS_INTERNATIONAL_BUILD",
            true,
            chain
        )

        assertEquals(0, result)
        assertEquals(1, chain.proceedCount)
        assertTrue("original must see temporary flag during proceed", chain.sawValue)
        assertFalse("flag must be restored after callback", FakeBuild.IS_INTERNATIONAL_BUILD)
    }

    @Test
    fun runtimeExceptionIsPropagatedAfterCleanup() {
        val error = RuntimeException("original failed")
        val chain = FakeChain(proceedThrow = error)

        try {
            SystemDisplayHooks.withTemporaryStaticBoolean(
                FakeBuild::class.java,
                "IS_INTERNATIONAL_BUILD",
                true,
                chain
            )
            assertSame(error, null)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertFalse("flag must be restored before RuntimeException propagates", FakeBuild.IS_INTERNATIONAL_BUILD)
        assertTrue("original must have seen temporary flag", chain.sawValue)
    }

    @Test
    fun outOfMemoryErrorIsPropagatedAfterCleanup() {
        val error = OutOfMemoryError("original OOM")
        val chain = FakeChain(proceedThrow = error)

        try {
            SystemDisplayHooks.withTemporaryStaticBoolean(
                FakeBuild::class.java,
                "IS_INTERNATIONAL_BUILD",
                true,
                chain
            )
            assertSame(error, null)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertFalse("flag must be restored before OOM propagates", FakeBuild.IS_INTERNATIONAL_BUILD)
        assertTrue("original must have seen temporary flag", chain.sawValue)
    }

    private class FakeChain(
        val proceedValue: Any? = null,
        val proceedThrow: Throwable? = null,
    ) : XposedInterface.Chain {

        var proceedCount = 0
        var sawValue = false

        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = null

        override fun proceed(): Any? {
            proceedCount++
            sawValue = FakeBuild.IS_INTERNATIONAL_BUILD
            proceedThrow?.let { throw it }
            return proceedValue
        }

        override fun proceed(args: Array<Any?>): Any? = error("not used")
        override fun proceedWith(p0: Any): Any? = error("not used")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used")
    }

    @Suppress("unused")
    class FakeBuild {
        companion object {
            @JvmStatic
            var IS_INTERNATIONAL_BUILD: Boolean = false
        }
    }
}
