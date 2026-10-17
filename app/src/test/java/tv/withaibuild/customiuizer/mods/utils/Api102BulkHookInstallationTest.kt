package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Api102BulkHookInstallationTest {
    private var previousModule: XposedInterface? = null
    private val attempts = ArrayList<Attempt>()
    private val callback = object : HookerClassHelper.MethodHook(71) {}
    private var failSetter = false
    private var failSecondIntercept = false

    @Before
    fun saveModule() { previousModule = XposedHelpers.moduleInst }

    @After
    fun restoreModule() {
        XposedHelpers.moduleInst = previousModule
    }

    @Test
    fun api102IdsAndConfirmsEachOverloadWithoutChangingCallbackOrPriority() {
        bind()
        install()
        assertSelection()
        for (attempt in attempts) {
            assertEquals(listOf("setPriority", "setExceptionMode", "setId", "intercept", "getId"), attempt.calls)
            assertEquals("systemui.plugin.create", attempt.id)
        }
    }

    @Test
    fun requiredSetterFailureStopsBeforeInstallingAnyOverload() {
        bind()
        failSetter = true
        install()
        assertEquals(1, attempts.size)
        assertFalse(attempts.single().calls.contains("intercept"))
        assertFalse(attempts.single().calls.contains("getId"))
    }

    @Test
    fun partialInstallFailureDoesNotRetryEarlierOrFailedOverload() {
        bind()
        failSecondIntercept = true
        install()
        assertEquals(2, attempts.size)
        assertEquals(2, attempts.map { it.method }.toSet().size)
        assertTrue(attempts.all { it.calls.count { call -> call == "intercept" } == 1 })
    }

    @Test
    fun absentMethodNeverCreatesBuilder() {
        bind()
        install("missing")
        assertTrue(attempts.isEmpty())
    }

    @Test
    fun ordinaryBulkEntryDoesNotOptInToIdsOnApi102() {
        bind()
        ModuleHelper.hookAllMethods(Target::class.java.name, Target::class.java.classLoader, "createPlugin", callback)
        assertSelection()
        assertTrue(attempts.all { !it.calls.contains("setId") && !it.calls.contains("getId") })
    }

    private fun install(name: String = "createPlugin") = ModuleHelper.hookAllMethodsWithId(
        Target::class.java.name, Target::class.java.classLoader, name,
        HookInstallation.STABLE_ID_PLUGIN_CREATE, callback,
    )

    private fun assertSelection() {
        val expected = Target::class.java.declaredMethods.filter { it.name == "createPlugin" }.toSet()
        assertEquals(2, expected.size)
        assertEquals(expected, attempts.map { it.method }.toSet())
        assertEquals(2, attempts.size)
        attempts.forEach { assertEquals(71, it.priority); assertSame(callback, it.callback) }
    }

    private fun bind() {
        XposedHelpers.moduleInst = proxy(XposedInterface::class.java) { name, args ->
            check(name == "hook")
            val attempt = Attempt(args!![0] as Method)
            attempts.add(attempt)
            proxy(XposedInterface.HookBuilder::class.java) builderCall@{ call, values ->
                attempt.calls.add(call)
                when (call) {
                    "setPriority" -> attempt.priority = values!![0] as Int
                    "setExceptionMode" -> assertSame(XposedInterface.ExceptionMode.PASSTHROUGH, values!![0])
                    "setId" -> {
                        if (failSetter) throw AbstractMethodError("missing setter")
                        attempt.id = values!![0] as String
                    }
                    "intercept" -> {
                        attempt.callback = values!![0] as XposedInterface.Hooker
                        if (failSecondIntercept && attempts.size == 2) throw IllegalStateException("second install failed")
                        return@builderCall proxy(XposedInterface.HookHandle::class.java) { getter, _ ->
                            attempt.calls.add(getter)
                            check(getter == "getId")
                            attempt.id
                        }
                    }
                    else -> error(call)
                }
                // The invocation handler returns the same builder for configuration methods.
                null
            }
        }
    }

    private fun <T : Any> proxy(type: Class<T>, action: (String, Array<out Any?>?) -> Any?): T {
        return type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
            val result = action(method.name, args)
            if (method.returnType == type) self else result
        })
    }

    private class Attempt(val method: Method) {
        val calls = ArrayList<String>()
        var priority = 0
        var id: String? = null
        var callback: XposedInterface.Hooker? = null
    }

    private class Target {
        fun createPlugin() = Unit
        fun createPlugin(@Suppress("UNUSED_PARAMETER") value: Int) = Unit
        fun unrelated() = Unit
    }
}
