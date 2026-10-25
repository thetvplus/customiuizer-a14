package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import org.junit.Assert.*
import org.junit.Test

class MethodHookBoundaryTest {
    private class Chain(
        private val values: List<Any?> = emptyList(),
        val failure: Throwable? = null,
        val duringProceed: () -> Unit = {},
    ) : XposedInterface.Chain {
        var proceeds = 0
        var copiedProceeds = 0
        var listReads = 0
        var supplied: Array<Any?>? = null
        val result = Any()
        override fun getExecutable() = MethodHookBoundaryTest::class.java.getDeclaredMethod("target")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> { listReads++; return values }
        override fun getArg(index: Int): Any? = values[index]
        override fun proceed(): Any { proceeds++; duringProceed(); failure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any { copiedProceeds++; supplied = args; return proceed() }
        override fun proceedWith(receiver: Any): Any = throw AssertionError("receiver changed")
        override fun proceedWith(receiver: Any, args: Array<Any?>): Any = throw AssertionError("receiver changed")
    }

    fun target() = Unit

    @Test fun readOnlyCallbacksKeepZeroCopyProceedAndOrder() {
        val calls = mutableListOf<String>()
        val chain = Chain(listOf("original"))
        var afterArgument: Any? = null
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                calls += "before"; assertEquals("original", callback.getArg(0))
            }
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                calls += "after"; assertSame(chain.result, callback.getResult())
                afterArgument = callback.getArg(0)
            }
        }
        assertSame(chain.result, hook.intercept(chain))
        assertEquals(listOf("before", "after"), calls)
        assertEquals("original", afterArgument)
        assertEquals(1, chain.proceeds); assertEquals(0, chain.copiedProceeds); assertEquals(0, chain.listReads)
    }

    @Test fun argumentMutationUsesOneOwnedArrayForBeforeProceedAndAfter() {
        val chain = Chain(listOf("original"))
        var arguments: Array<Any?>? = null
        var afterRead: Any? = null
        var rewrittenRead: Any? = null
        var afterArguments: Array<Any?>? = null
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                arguments = callback.getArgs(); arguments!![0] = "changed"
                assertEquals("changed", callback.getArg(0))
            }
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                afterRead = callback.getArg(0)
                afterArguments = callback.getArgs()
                callback.getArgs()[0] = "after"
                rewrittenRead = callback.getArg(0)
            }
        }
        assertSame(chain.result, hook.intercept(chain))
        assertEquals("changed", afterRead); assertEquals("after", rewrittenRead)
        assertSame(arguments, afterArguments)
        assertSame(arguments, chain.supplied); assertEquals("after", chain.supplied!![0])
        assertEquals(1, chain.proceeds); assertEquals(1, chain.copiedProceeds); assertEquals(1, chain.listReads)
        assertEquals("original", chain.getArg(0))
    }

    @Test fun afterOnlyReadsAvoidMaterializingArgumentsIncludingNulls() {
        val argument = Any()
        val chain = Chain(listOf(null, argument))
        val observed = mutableListOf<Any?>()
        var skipped = true
        val hook = object : HookerClassHelper.MethodHook() {
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                observed += callback.getArg(0)
                observed += callback.getArg(1)
                skipped = callback.isSkipped
            }
        }
        assertSame(chain.result, hook.intercept(chain))
        assertEquals(listOf(null, argument), observed)
        assertFalse(skipped)
        assertEquals(1, chain.proceeds); assertEquals(0, chain.copiedProceeds); assertEquals(0, chain.listReads)
    }

    @Test fun afterReadsObserveCalleeUpdatesUnlessBeforeOwnsArguments() {
        val values = mutableListOf<Any?>("before")
        val chain = Chain(values, duringProceed = { values[0] = "callee" })
        val observed = mutableListOf<Any?>()
        val hook = object : HookerClassHelper.MethodHook() {
            override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                observed += callback.getArg(0)
            }
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                observed += callback.getArg(0)
                observed += callback.getArgs()[0]
                observed += callback.getArg(0)
            }
        }
        assertSame(chain.result, hook.intercept(chain))
        assertEquals(listOf("before", "callee", "callee", "callee"), observed)
        assertEquals(1, chain.proceeds); assertEquals(0, chain.copiedProceeds); assertEquals(1, chain.listReads)
    }

    @Test fun skippedAndRecoveredCallsCanReadArgumentsWithoutCopying() {
        val argument = Any()
        val failure = IllegalArgumentException("ordinary")
        for (skip in listOf(false, true)) {
            val chain = Chain(listOf(argument), failure)
            var observedArgument: Any? = null
            var observedFailure: Throwable? = null
            var observedSkipped: Boolean? = null
            val hook = object : HookerClassHelper.MethodHook() {
                override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                    if (skip) callback.throwAndSkip(failure)
                }
                override fun after(callback: HookerClassHelper.AfterHookCallback) {
                    observedArgument = callback.getArg(0)
                    observedFailure = callback.getThrowable()
                    observedSkipped = callback.isSkipped
                    callback.setResult(chain.result)
                }
            }
            assertSame(chain.result, hook.intercept(chain))
            assertSame(argument, observedArgument)
            assertSame(failure, observedFailure)
            assertEquals(skip, observedSkipped)
            assertEquals(if (skip) 0 else 1, chain.proceeds)
            assertEquals(0, chain.copiedProceeds); assertEquals(0, chain.listReads)
        }
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
