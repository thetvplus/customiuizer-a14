package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import org.junit.Assert.*
import org.junit.Test

class MethodHookBoundaryTest {
    private class Chain(private val values: List<Any?> = emptyList(), val failure: Throwable? = null) : XposedInterface.Chain {
        var proceeds = 0
        var copiedProceeds = 0
        var listReads = 0
        var supplied: Array<Any?>? = null
        val result = Any()
        override fun getExecutable() = MethodHookBoundaryTest::class.java.getDeclaredMethod("target")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> { listReads++; return values }
        override fun getArg(index: Int): Any? = values[index]
        override fun proceed(): Any { proceeds++; failure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any { copiedProceeds++; supplied = args; return proceed() }
        override fun proceedWith(receiver: Any): Any = throw AssertionError("receiver changed")
        override fun proceedWith(receiver: Any, args: Array<Any?>): Any = throw AssertionError("receiver changed")
    }

    fun target() = Unit

    @Test fun readOnlyCallbacksKeepZeroCopyProceedAndOrder() {
        val calls = mutableListOf<String>()
        val chain = Chain(listOf("original"))
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                calls += "before"; assertEquals("original", callback.getArg(0))
            }
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                calls += "after"; assertSame(chain.result, callback.getResult())
            }
        }
        assertSame(chain.result, hook.intercept(chain))
        assertEquals(listOf("before", "after"), calls)
        assertEquals(1, chain.proceeds); assertEquals(0, chain.copiedProceeds); assertEquals(0, chain.listReads)
    }

    @Test fun argumentMutationUsesOneOwnedArrayForBeforeProceedAndAfter() {
        val chain = Chain(listOf("original"))
        var arguments: Array<Any?>? = null
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                arguments = callback.getArgs(); arguments!![0] = "changed"
                assertEquals("changed", callback.getArg(0))
            }
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                assertSame(arguments, callback.getArgs())
            }
        }
        assertSame(chain.result, hook.intercept(chain))
        assertSame(arguments, chain.supplied); assertEquals("changed", chain.supplied!![0])
        assertEquals(1, chain.proceeds); assertEquals(1, chain.copiedProceeds); assertEquals(1, chain.listReads)
        assertEquals("original", chain.getArg(0))
    }

    @Test fun emptyArgumentsKeepCallbackIdentityAndExplicitProceedOverload() {
        repeat(2) {
            val chain = Chain()
            val hook = object : HookerClassHelper.MethodHook() {
                override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                    val args = callback.getArgs(); assertEquals(0, args.size); assertSame(args, callback.getArgs())
                }
            }
            assertSame(chain.result, hook.intercept(chain))
            assertEquals(1, chain.proceeds); assertEquals(1, chain.copiedProceeds); assertEquals(1, chain.listReads)
        }
    }

    @Test fun ordinaryCalleeFailureCanStillBeRecoveredByAfter() {
        val failure = IllegalStateException("ordinary")
        val chain = Chain(failure = failure)
        val hook = object : HookerClassHelper.MethodHook() {
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                assertSame(failure, callback.getThrowable()); callback.setResult(chain.result)
            }
        }
        assertSame(chain.result, hook.intercept(chain)); assertEquals(1, chain.proceeds)
    }

    @Test fun fatalCalleeFailureCannotBeConsumedByAfter() {
        for (fatal in fatals()) for (failure in listOf(fatal, wrapped(fatal))) {
            val chain = Chain(failure = failure)
            var afters = 0
            val hook = object : HookerClassHelper.MethodHook() {
                override fun after(callback: HookerClassHelper.AfterHookCallback) { afters++; callback.setResult("recovered") }
            }
            assertFatal(fatal) { hook.intercept(chain) }
            assertEquals(1, chain.proceeds); assertEquals(0, afters)
        }
    }

    @Test fun fatalThrowAndSkipCannotBeConsumedByAfter() {
        for (fatal in fatals()) {
            val chain = Chain(); var afters = 0
            val hook = object : HookerClassHelper.MethodHook() {
                override fun before(callback: HookerClassHelper.BeforeHookCallback) { callback.throwAndSkip(wrapped(fatal)) }
                override fun after(callback: HookerClassHelper.AfterHookCallback) { afters++; callback.setResult("recovered") }
            }
            assertFatal(fatal) { hook.intercept(chain) }
            assertEquals(0, chain.proceeds); assertEquals(0, afters)
        }
    }

    @Test fun wrappedFatalFromBeforeEscapesWithoutCallingCallee() {
        for (fatal in fatals()) {
            val chain = Chain()
            val hook = object : HookerClassHelper.MethodHook() {
                override fun before(callback: HookerClassHelper.BeforeHookCallback) { throw wrapped(fatal) }
            }
            assertFatal(fatal) { hook.intercept(chain) }; assertEquals(0, chain.proceeds)
        }
    }

    @Test fun wrappedFatalFromAfterEscapesUnchanged() {
        for (fatal in fatals()) {
            val chain = Chain()
            val hook = object : HookerClassHelper.MethodHook() {
                override fun after(callback: HookerClassHelper.AfterHookCallback) { throw wrapped(fatal) }
            }
            assertFatal(fatal) { hook.intercept(chain) }; assertEquals(1, chain.proceeds)
        }
    }

    @Test fun ordinaryBeforeFailureStillContinuesAndOrdinarySkipCanBeRecovered() {
        val chain = Chain()
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) { throw IllegalStateException("ordinary") }
        }
        assertSame(chain.result, hook.intercept(chain)); assertEquals(1, chain.proceeds)
        val skipped = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) { callback.throwAndSkip(IllegalArgumentException("ordinary")) }
            override fun after(callback: HookerClassHelper.AfterHookCallback) { callback.setResult(chain.result) }
        }
        assertSame(chain.result, skipped.intercept(chain)); assertEquals(1, chain.proceeds)
    }

    private fun fatals() = listOf(OutOfMemoryError("sentinel"), ThreadDeath(), InternalError("sentinel"))
    private fun wrapped(t: Throwable) = UndeclaredThrowableException(InvocationTargetException(t))
    private fun assertFatal(expected: Throwable, call: () -> Unit) {
        try { call(); fail("Fatal error was consumed") } catch (actual: Throwable) { assertSame(expected, actual) }
    }
}
