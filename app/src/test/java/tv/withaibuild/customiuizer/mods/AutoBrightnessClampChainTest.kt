package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import java.lang.reflect.Executable

/**
 * Behavioral tests for the two clamp hook interceptors.
 */
class AutoBrightnessClampChainTest {

    @Test
    fun clampChainCallsProceedExactlyOnce() {
        val chain = FakeClampChain(proceedValue = 0.5f)

        SystemDisplayHooks.interceptClampScreenBrightness(chain)

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun clampChainReturnsConstrainedValue() {
        MainModule.mPrefs.clear()
        MainModule.mPrefs.put("system_autobrightness_limitmin", true)
        MainModule.mPrefs.put("system_autobrightness_limitmax", true)
        MainModule.mPrefs.put("system_autobrightness_min", 25)
        MainModule.mPrefs.put("system_autobrightness_max", 75)
        SystemDisplayHooks.backlightMaxLevel = 4095
        SystemDisplayHooks.mMinimumBacklight = 0f
        SystemDisplayHooks.mMaximumBacklight = 1f
        SystemDisplayHooks.refreshAutoBrightnessRangeSnapshot()

        val chain = FakeClampChain(proceedValue = 0.0f)

        val result = SystemDisplayHooks.interceptClampScreenBrightness(chain)

        assertEquals(SystemDisplayHooks.constrainValue(0.0f), result)
    }

    @Test
    fun clampChainPropagatesRuntimeException() {
        val error = RuntimeException("original clamp failed")
        val chain = FakeClampChain(proceedThrow = error)

        try {
            SystemDisplayHooks.interceptClampScreenBrightness(chain)
            assertSame(error, null)
        } catch (t: Throwable) {
            assertSame(error, t)
        }
    }

    @Test
    fun clampChainPropagatesOutOfMemoryError() {
        val error = OutOfMemoryError("clamp OOM")
        val chain = FakeClampChain(proceedThrow = error)

        try {
            SystemDisplayHooks.interceptClampScreenBrightness(chain)
            assertSame(error, null)
        } catch (t: Throwable) {
            assertSame(error, t)
        }
    }

    private class FakeClampChain(
        val proceedValue: Float? = null,
        val proceedThrow: Throwable? = null,
    ) : XposedInterface.Chain {

        var proceedCount = 0

        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = null

        override fun proceed(): Any? {
            proceedCount++
            proceedThrow?.let { throw it }
            return proceedValue
        }

        override fun proceed(args: Array<Any?>): Any? = error("not used")
        override fun proceedWith(p0: Any): Any? = error("not used")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used")
    }
}
