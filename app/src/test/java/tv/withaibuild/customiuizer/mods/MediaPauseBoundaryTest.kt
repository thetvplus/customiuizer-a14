package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test

class MediaPauseBoundaryTest {
    @Test fun aNativePauseMustSucceedBeforePublishingItsPauseHint() {
        val chain = Chain()
        var updates = 0
        val hook = Controls.mediaPauseHook { assertTrue(chain.nativeCalls == 1); assertSame(chain.owner, it); updates++ }
        assertSame(chain.result, hook.intercept(chain))
        assertEquals(1, updates); assertEquals(1, chain.nativeCalls)
    }
    @Test fun failedNativePauseCannotReadTheOwnerOrPublishAFalsePause() {
        val failure = IllegalStateException("native pause failed")
        val chain = Chain(failure)
        var updates = 0
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            Controls.mediaPauseHook { updates++ }.intercept(chain)
        })
        assertEquals(1, chain.nativeCalls); assertEquals(0, chain.ownerReads); assertEquals(0, updates)
    }
    @Test fun optionalHintFailureCannotBreakPlaybackAndFatalStillPropagates() {
        val chain = Chain()
        assertSame(chain.result, Controls.mediaPauseHook { error("optional stream getter unavailable") }.intercept(chain))
        assertEquals(1, chain.nativeCalls)
        val fatal = OutOfMemoryError("fatal")
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            Controls.mediaPauseHook { throw fatal }.intercept(Chain())
        })
    }
    private class Chain(private val failure: Throwable? = null) : XposedInterface.Chain {
        val owner = Any(); val result = Any(); var nativeCalls = 0; var ownerReads = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any { ownerReads++; return owner }
        override fun getArgs(): List<Any?> = error("no arguments")
        override fun getArg(index: Int): Any? = error("no arguments")
        override fun proceed(): Any? { nativeCalls++; failure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
