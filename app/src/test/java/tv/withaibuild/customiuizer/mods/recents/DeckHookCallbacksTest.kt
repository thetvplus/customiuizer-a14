package tv.withaibuild.customiuizer.mods.recents

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test

class DeckHookCallbacksTest {
    private class Chain(val failure: Throwable?=null): XposedInterface.Chain {
        val calls=ArrayList<String>()
        val result=Any()
        var proceeds=0
        var listReads=0
        var supplied: Array<Any?>?=null
        override fun getExecutable()=DeckHookCallbacksTest::class.java.getDeclaredMethod("target")
        override fun getThisObject(): Any?=null
        override fun getArgs(): List<Any?> { listReads++; return listOf("native") }
        override fun getArg(index: Int): Any?="native"
        override fun proceed(): Any {
            proceeds++; calls.add("native"); failure?.let { throw it }; return result
        }
        override fun proceed(args: Array<Any?>): Any { supplied=args; return proceed() }
        override fun proceedWith(receiver: Any): Any=throw AssertionError("receiver changed")
        override fun proceedWith(receiver: Any,args: Array<Any?>): Any=throw AssertionError("receiver changed")
    }
    fun target()=Unit

    @Test fun oneNativeCallPreservesPhaseOrderAndRewrittenArguments() {
        for(rewrite in listOf(false,true)) {
            val chain=Chain()
            var observed: Any?=null
            val hook=IosRecentsDeckHooks.paired(
                { chain.calls.add("before"); if(rewrite) it.getArgs()[0]="deck" },
                { chain.calls.add("after"); observed=it.getArg(0) })
            assertSame(chain.result,hook.intercept(chain))
            assertEquals(listOf("before","native","after"),chain.calls)
            assertEquals(if(rewrite) "deck" else "native",observed)
            assertEquals(1,chain.proceeds)
            assertEquals(if(rewrite) 1 else 0,chain.listReads)
            assertEquals(if(rewrite) "deck" else null,chain.supplied?.get(0))
        }
    }
    @Test fun ordinaryNativeFailureRunsOnlyUnconditionalCleanupAndKeepsItsIdentity() {
        val failure=IllegalStateException("native failure")
        for(always in listOf(false,true)) {
            val chain=Chain(failure)
            var observed: Throwable?=null
            val hook=IosRecentsDeckHooks.paired({}, { observed=it.getThrowable() },always)
            val thrown=runCatching { hook.intercept(chain) }.exceptionOrNull()
            assertSame(failure,thrown)
            assertSame(if(always) failure else null,observed)
            assertEquals(1,chain.proceeds)
        }
    }
    @Test fun retiredFrameSkipCannotEnterTheFormerInnerAfterPhase() {
        for(always in listOf(false,true)) {
            val chain=Chain()
            var afterCalls=0
            val hook=IosRecentsDeckHooks.paired({ it.returnAndSkip("retired") }, { afterCalls++ },always)
            assertEquals("retired",hook.intercept(chain))
            assertEquals(0,chain.proceeds)
            assertEquals(0,afterCalls)
        }
    }
    @Test fun fatalNativeFailureEscapesBeforeCleanupCanRun() {
        val failure=OutOfMemoryError("fatal")
        val chain=Chain(failure)
        var afterCalls=0
        val hook=IosRecentsDeckHooks.paired({}, { afterCalls++ },always=true)
        assertSame(failure,runCatching { hook.intercept(chain) }.exceptionOrNull())
        assertEquals(1,chain.proceeds)
        assertEquals(0,afterCalls)
    }
}
