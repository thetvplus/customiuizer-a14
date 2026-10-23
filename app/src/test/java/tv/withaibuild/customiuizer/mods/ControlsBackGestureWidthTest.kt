package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ControlsBackGestureWidthTest {
    private val saved = Controls.controlsConfig
    private val owner = Stub()
    private var calls = 0
    private var failure: Throwable? = null
    private var requested = 54
    private val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
        arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
        when (method.name) {
            "proceed" -> {
                calls++
                failure?.let { throw it }
                if (args.isNullOrEmpty()) { owner.mGestureStubDefaultSize = 54; owner.mGestureStubSize = 54 }
                else {
                    val size = (args[0] as Array<*>)[0] as Int
                    owner.mGestureStubDefaultSize = size
                    owner.mGestureStubSize = size
                }
                null
            }
            "getThisObject" -> owner
            "getArg" -> requested
            "getArgs" -> listOf(requested)
            else -> error("Unexpected chain access ${method.name}")
        }
    } as XposedInterface.Chain

    @After fun restore() { Controls.controlsConfig = saved }

    @Test fun eachNativeInitializationScalesFreshRomValuesAndDefaultSetSizeIsNotScaledTwice() {
        Controls.controlsConfig = saved.copy(fsgWidth = 150)
        repeat(3) {
            Controls.backGestureWidthInitHook().intercept(chain)
            assertEquals(81, owner.mGestureStubDefaultSize)
            assertEquals(81, owner.mGestureStubSize)
        }
        assertEquals(3, calls)
        requested = 81
        // A default request uses the already-scaled native value; no argument rewrite.
        val defaultChain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
            when (method.name) {
                "getThisObject" -> owner
                "getArg" -> requested
                "proceed" -> {
                    assertTrue(args.isNullOrEmpty()); calls++
                    owner.mGestureStubDefaultSize = requested; owner.mGestureStubSize = requested; null
                }
                else -> error("Unexpected chain access ${method.name}")
            }
        } as XposedInterface.Chain
        Controls.backGestureSetSizeHook().intercept(defaultChain)
        assertEquals(81, owner.mGestureStubSize)
        requested = 40
        Controls.backGestureSetSizeHook().intercept(chain)
        assertEquals(60, owner.mGestureStubSize)
        assertEquals(60, owner.mGestureStubDefaultSize)
        Controls.controlsConfig = saved.copy(fsgWidth = 100)
        Controls.backGestureWidthInitHook().intercept(chain)
        assertEquals(54, owner.mGestureStubDefaultSize)
        assertEquals(54, owner.mGestureStubSize)
    }

    @Test fun failedNativeInitializationPreservesItsExceptionAndDoesNotTouchOldFields() {
        Controls.controlsConfig = saved.copy(fsgWidth = 150)
        for (error in listOf(IllegalStateException("native failed"), OutOfMemoryError("native fatal"))) {
            failure = error
            val actual = assertThrows(error.javaClass) { Controls.backGestureWidthInitHook().intercept(chain) }
            assertSame(error, actual)
            assertEquals(54, owner.mGestureStubDefaultSize)
            assertEquals(54, owner.mGestureStubSize)
        }
        assertEquals(2, calls)
    }

    class Stub {
        @JvmField var mGestureStubDefaultSize = 54
        @JvmField var mGestureStubSize = 54
    }
}
