package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Api102HookInstallationTest {
    private val target = Target::class.java.getDeclaredMethod("init", Boolean::class.javaPrimitiveType)
    private val callback = object : HookerClassHelper.MethodHook(73) {}
    private var previousModule: XposedInterface? = null

    @Before
    fun prepare() {
        previousModule = XposedHelpers.moduleInst
        ShadowLog.clear()
    }

    @After
    fun restore() {
        XposedHelpers.moduleInst = previousModule
    }

    @Test
    fun ordinaryMethodUsesTheSameNativePolicyWithoutAnId() {
        val builder = bind()
        val unhooker = XposedHelpers.findAndHookMethod(Target::class.java, "init", Boolean::class.javaPrimitiveType, callback)
        assertNotNull(unhooker)
        assertEquals(listOf("priority", "mode", "intercept"), builder.calls)
        assertEquals(73, builder.priority)
        assertSame(XposedInterface.ExceptionMode.PASSTHROUGH, builder.mode)
        assertNull(builder.id)
        assertEquals(0, builder.getIdCalls)
        assertSame(callback, builder.callback)
        unhooker!!.unhook()
        assertEquals(1, builder.unhookCalls)
    }

    @Test
    fun constructorsKeepTheNativePriorityModeAndUnhookPolicy() {
        val constructor = Target::class.java.getDeclaredConstructor()
        val builder = bind(expected = constructor)
        val unhooker = XposedHelpers.findAndHookConstructor(Target::class.java, callback)
        assertNotNull(unhooker)
        assertEquals(listOf("priority", "mode", "intercept"), builder.calls)
        assertEquals(73, builder.priority)
        assertSame(XposedInterface.ExceptionMode.PASSTHROUGH, builder.mode)
        assertSame(callback, builder.callback)
        assertEquals(0, builder.getIdCalls)
        unhooker!!.unhook()
        assertEquals(1, builder.unhookCalls)
    }

    @Test
    fun api102PreservesPriorityModeCallbackAndUnhookWithStableId() {
        val builder = bind()
        val unhooker = install()
        assertNotNull(unhooker)
        assertEquals(listOf("priority", "mode", "id", "intercept"), builder.calls)
        assertEquals(73, builder.priority)
        assertSame(XposedInterface.ExceptionMode.PASSTHROUGH, builder.mode)
        assertEquals("systemui.init", builder.id)
        assertEquals(1, builder.getIdCalls)
        assertTrue(logMessages().any { it.contains("systemui.init (confirmed)") })
        assertSame(callback, builder.callback)
        unhooker!!.unhook()
        assertEquals(1, builder.unhookCalls)
    }

    @Test
    fun missingRequiredIdApiFailsBeforeIntercept() {
        val builder = bind()
        builder.idFailure = AbstractMethodError("missing implementation")
        assertNull(install())
        assertEquals(1, builder.calls.count { it == "id" })
        assertFalse(builder.calls.contains("intercept"))
        assertEquals(0, builder.getIdCalls)
    }

    @Test
    fun idReadbackMismatchDoesNotRetryOrDiscardInstalledHook() {
        for (reported in listOf(null, "different.id")) {
            ShadowLog.clear()
            val builder = bind()
            builder.overrideReportedId = true
            builder.reportedId = reported
            val unhooker = install()
            assertNotNull(unhooker)
            assertEquals(1, builder.calls.count { it == "intercept" })
            assertEquals(1, builder.getIdCalls)
            assertTrue(logMessages().any { it.contains("hook ID not confirmed: systemui.init") })
            assertFalse(logMessages().any { it.contains("stable hook ID installed:") })
            unhooker!!.unhook()
            assertEquals(1, builder.unhookCalls)
        }
    }

    @Test
    fun unavailableIdReadbackPreservesInstalledHookAndReportsUnconfirmed() {
        for (failure in listOf(AbstractMethodError("missing getId"), IllegalStateException("readback failed"))) {
            ShadowLog.clear()
            val builder = bind()
            builder.readbackFailure = failure
            val unhooker = install()
            assertNotNull(unhooker)
            assertSame(callback, builder.callback)
            assertEquals(1, builder.calls.count { it == "intercept" })
            assertTrue(logMessages().any { it.contains("hook ID readback unavailable") })
            assertFalse(logMessages().any { it.contains("stable hook ID installed:") })
            unhooker!!.unhook()
            assertEquals(1, builder.unhookCalls)
        }
    }

    @Test
    fun fatalIdReadbackErrorsPropagateWithoutRetryingInstalledHook() {
        for (fatal in listOf(OutOfMemoryError("readback"), ThreadDeath(), StackOverflowError("readback"))) {
            for (failure in listOf(fatal, InvocationTargetException(fatal))) {
                val builder = bind()
                builder.readbackFailure = failure
                try {
                    install()
                    fail("Fatal readback error must propagate")
                } catch (actual: Throwable) {
                    assertSame(fatal, actual)
                }
                assertEquals(1, builder.calls.count { it == "intercept" })
                assertEquals(0, builder.unhookCalls)
            }
        }
    }

    @Test
    fun rejectedIdDoesNotSilentlyInstallAnUnidentifiedHook() {
        val builder = bind()
        builder.idFailure = IllegalArgumentException("unsupported ID")
        assertNull(install())
        assertFalse(builder.calls.contains("intercept"))
    }

    @Test
    fun interceptFailureIsNotRetried() {
        val builder = bind()
        builder.installFailure = IllegalStateException("installation failed")
        assertNull(install())
        assertEquals(1, builder.calls.count { it == "intercept" })
    }

    @Test
    fun fatalIdErrorsPreventInstallationAndPropagate() {
        for (failure in listOf(OutOfMemoryError("test"), ThreadDeath(), StackOverflowError("test"))) {
            val builder = bind()
            builder.idFailure = failure
            try {
                install()
                fail("Fatal error must propagate")
            } catch (actual: Throwable) {
                assertSame(failure, actual)
            }
            assertFalse(builder.calls.contains("intercept"))
        }
    }

    @Test
    fun wrappedFatalIdErrorIsUnwrappedBeforeReportingFailure() {
        val builder = bind()
        val failure = OutOfMemoryError("wrapped test")
        builder.idFailure = InvocationTargetException(failure)
        try {
            install()
            fail("Wrapped fatal error must propagate")
        } catch (actual: Throwable) {
            assertSame(failure, actual)
        }
        assertFalse(builder.calls.contains("intercept"))
    }

    private fun install() = HookInstallerFacade.findAndHookMethodWithId(
        Target::class.java.name, Target::class.java.classLoader, "init", "systemui.init",
        Boolean::class.javaPrimitiveType, callback,
    )

    private fun logMessages() = ShadowLog.getLogsForTag("LSPosed-Bridge").map { it.msg }

    private fun bind(expected: Executable = target): Builder {
        val builder = Builder()
        val framework = Proxy.newProxyInstance(
            XposedInterface::class.java.classLoader, arrayOf(XposedInterface::class.java),
        ) { _, method, args ->
            when (method.name) {
                "hook" -> {
                    assertEquals(expected, args!![0])
                    builder
                }
                "getApiVersion" -> error("Native installation must not probe API capabilities")
                else -> throw UnsupportedOperationException(method.name)
            }
        }
        XposedHelpers.moduleInst = framework as XposedInterface
        return builder
    }

    private inner class Builder : XposedInterface.HookBuilder {
        val calls = ArrayList<String>()
        var priority = 0
        var mode: XposedInterface.ExceptionMode? = null
        var id: String? = null
        var callback: XposedInterface.Hooker? = null
        var idFailure: Throwable? = null
        var installFailure: Throwable? = null
        var unhookCalls = 0
        var getIdCalls = 0
        var overrideReportedId = false
        var reportedId: String? = null
        var readbackFailure: Throwable? = null
        private val handle = Proxy.newProxyInstance(
            XposedInterface.HookHandle::class.java.classLoader, arrayOf(XposedInterface.HookHandle::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "unhook" -> { unhookCalls++; null }
                "getExecutable" -> target
                "getId" -> {
                    getIdCalls++
                    readbackFailure?.let { throw it }
                    if (overrideReportedId) reportedId else id
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as XposedInterface.HookHandle

        override fun setPriority(priority: Int): XposedInterface.HookBuilder {
            calls.add("priority")
            this.priority = priority
            return this
        }

        override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder {
            calls.add("mode")
            this.mode = mode
            return this
        }

        override fun setId(id: String?): XposedInterface.HookBuilder {
            calls.add("id")
            idFailure?.let { throw it }
            this.id = id
            return this
        }

        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            calls.add("intercept")
            installFailure?.let { throw it }
            callback = hooker
            return handle
        }
    }

    private class Target { fun init(@Suppress("UNUSED_PARAMETER") restarting: Boolean) = Unit }
}
