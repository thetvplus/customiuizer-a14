package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceInfoIconHookTest {
    @Test
    fun stockIconReadsOnlyTheHolderAndKeepsTheNativeResult() {
        val result = Any()
        val stockIcon = IconHolder(1)
        val type = IconHolder::class.java.getDeclaredField("mType").apply { isAccessible = true }
        var nativeCalls = 0
        var holderReads = 0
        val chain = object : XposedInterface.Chain {
            override fun getExecutable(): Executable = error("unexpected member lookup")
            override fun getThisObject(): Any = error("unexpected icon manager lookup")
            override fun getArgs(): List<Any?> = error("the stock path must not materialize arguments")
            override fun getArg(index: Int): Any {
                assertEquals(3, index)
                holderReads++
                return stockIcon
            }

            override fun proceed(): Any {
                nativeCalls++
                return result
            }

            override fun proceed(args: Array<Any?>): Any? = error("unexpected argument rewrite")
            override fun proceedWith(receiver: Any): Any? = error("unexpected receiver rewrite")
            override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unexpected rewrite")
        }

        assertSame(result, DeviceInfoMonitor.createIconHolderHook(type).intercept(chain))
        assertEquals(1, holderReads)
        assertEquals(1, nativeCalls)
    }

    private class IconHolder(@JvmField val mType: Int)
}
