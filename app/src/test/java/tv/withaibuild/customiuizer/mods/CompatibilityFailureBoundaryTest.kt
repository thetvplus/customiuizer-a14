package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CompatibilityFailureBoundaryTest {
    @Test fun unrelatedAlarmSettingsDoNotCopyArgumentsAndLegacyMappingPreservesResolverAndUser() {
        val resolver = Any()
        for (key in listOf("other_setting", "next_alarm_formatted")) {
            var copies = 0
            var calls = 0
            val result = Any()
            val original = listOf(resolver, key, 10)
            val chain = java.lang.reflect.Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
                arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
                when (method.name) {
                    "getArg" -> original[args!![0] as Int]
                    "getArgs" -> { copies++; original }
                    "proceed" -> {
                        calls++
                        if (key == "next_alarm_formatted") {
                            val rewritten = args!![0] as Array<*>
                            assertSame(resolver, rewritten[0]); assertEquals("next_alarm_clock_formatted", rewritten[1])
                            assertEquals(10, rewritten[2])
                        } else assertTrue(args.isNullOrEmpty())
                        result
                    }
                    else -> error("Unexpected setting operation: ${method.name}")
                }
            } as XposedInterface.Chain
            assertSame(result, Various.alarmCompatSettingHook().intercept(chain))
            assertEquals(1, calls)
            assertEquals(if (key == "next_alarm_formatted") 1 else 0, copies)
            assertEquals(key, original[1])
        }
    }
    class Request
    class Logs(var failure: Throwable? = null) {
        var declined = 0
        fun declineRequest(request: Request) { failure?.let { throw it }; declined++ }
    }
    @Test fun successfulLogDenialDoesNotGrantAccessOrRunTheNativePrompt() {
        val owner = Logs(); val chain = Chain(owner, Request())
        assertNull(SystemSecurityHooks.noAccessDeviceLogsRequestHook().intercept(chain))
        assertEquals(1, owner.declined); assertEquals(0, chain.nativeCalls)
    }
    @Test fun incompatibleLogDenialFallsBackToNativeOnce() {
        for (owner in listOf(Logs(IllegalStateException("missing request")), Any())) {
            val chain = Chain(owner, Request())
            assertSame(chain.result, SystemSecurityHooks.noAccessDeviceLogsRequestHook().intercept(chain))
            assertEquals(1, chain.nativeCalls)
        }
    }
    @Test fun nativeLogFailureIsNotSwallowedAndFatalModuleFailureDoesNotBecomeApproval() {
        val error = IllegalStateException("native prompt failed")
        val chain = Chain(Any(), Request(), error)
        assertSame(error, assertThrows(IllegalStateException::class.java) {
            SystemSecurityHooks.noAccessDeviceLogsRequestHook().intercept(chain)
        })
        assertEquals(1, chain.nativeCalls)
        val fatal = OutOfMemoryError("fatal")
        val module = Chain(Logs(fatal), Request())
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            SystemSecurityHooks.noAccessDeviceLogsRequestHook().intercept(module)
        })
        assertEquals(0, module.nativeCalls)
    }
    @Test fun alarmCompatibilityCannotPublishTheOwnerSourceIntoAnotherUser() {
        assertTrue(Various.alarmCompatUserAllowed(10001, 0))
        for ((uid, user) in listOf(10001 to 10, 1010001 to 10, 1010001 to 0, -1 to 0, 10001 to -2))
            assertFalse("uid=$uid requestedUser=$user", Various.alarmCompatUserAllowed(uid, user))
    }
    @Test fun anAlarmNativeFailureCannotBeReplacedByTheCompatibilityCache() {
        val error = SecurityException("native rejected query")
        val chain = Chain(null, 0, error)
        assertSame(error, assertThrows(SecurityException::class.java) { Various.alarmCompatResultHook().intercept(chain) })
        assertEquals(1, chain.nativeCalls); assertEquals(0, chain.ownerReads)
    }
    @Test fun otherAlarmUsersKeepTheirNativeResultWithoutAccessingTheCacheOwner() {
        val chain = Chain(null, 10)
        assertSame(chain.result, Various.alarmCompatResultHook().intercept(chain))
        assertEquals(1, chain.nativeCalls); assertEquals(0, chain.ownerReads)
    }
    private class Chain(private val owner: Any?, private val arg: Any?, private val failure: Throwable? = null) : XposedInterface.Chain {
        val result = Any(); var nativeCalls = 0; var ownerReads = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any { ownerReads++; return owner ?: error("wrong user/failure accessed cache owner") }
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? = arg
        override fun proceed(): Any? { nativeCalls++; failure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
