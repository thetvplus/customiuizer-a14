package tv.withaibuild.customiuizer.mods.utils

import com.sun.management.ThreadMXBean
import io.github.libxposed.api.XposedInterface
import java.lang.management.ManagementFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Host allocation comparison of the old and indexed reads through the real hook adapter. */
class HookArgumentAllocationTest {
    @Volatile private var callbackSink: Any? = null
    @Volatile private var valueSink: Any? = null

    private class Chain : XposedInterface.Chain {
        val values = listOf(Any(), Any(), Any(), Any())
        var listReads = 0
        var copiedProceeds = 0
        override fun getExecutable() = error("No member lookup expected")
        override fun getThisObject(): Any? = null
        // Reuse the list: the comparison counts only the adapter's copying, not native costs.
        override fun getArgs(): List<Any?> { listReads++; return values }
        override fun getArg(index: Int): Any = values[index]
        override fun proceed(): Any = values[0]
        override fun proceed(args: Array<Any?>): Any { copiedProceeds++; return args[0]!! }
        override fun proceedWith(receiver: Any): Any = error("Receiver changed")
        override fun proceedWith(receiver: Any, args: Array<Any?>): Any = error("Receiver changed")
    }

    @Test fun indexedBeforeReadsAvoidArrayCopyAndArgumentRemarshalling() = compare(after = false)

    @Test fun indexedAfterReadsAvoidArrayCopy() = compare(after = true)

    private fun compare(after: Boolean) {
        val meter = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        assumeTrue("Host thread allocation counter unavailable", meter?.isThreadAllocatedMemorySupported == true)
        meter!!
        meter.isThreadAllocatedMemoryEnabled = true
        val chain = Chain()
        fun hook(indexed: Boolean): HookerClassHelper.MethodHook = if (after) {
            object : HookerClassHelper.MethodHook() {
                override fun after(callback: HookerClassHelper.AfterHookCallback) {
                    valueSink = if (indexed) callback.getArg(3) else callback.getArgs()[3]
                    callbackSink = callback // Keep both wrappers observable to the Host JIT.
                }
            }
        } else {
            object : HookerClassHelper.MethodHook() {
                override fun before(callback: HookerClassHelper.BeforeHookCallback) {
                    valueSink = if (indexed) callback.getArg(3) else callback.getArgs()[3]
                    callbackSink = callback
                }
            }
        }
        val old = hook(indexed = false)
        val indexed = hook(indexed = true)
        repeat(20_000) { old.intercept(chain); indexed.intercept(chain) }
        val iterations = 100_000
        val thread = Thread.currentThread().threadId()
        fun allocated(hook: HookerClassHelper.MethodHook): Long {
            val before = meter.getThreadAllocatedBytes(thread)
            repeat(iterations) { hook.intercept(chain) }
            return meter.getThreadAllocatedBytes(thread) - before
        }
        val oldBytes = LongArray(5)
        val indexedBytes = LongArray(5)
        for (sample in oldBytes.indices) {
            // Alternate measurement order; assert allocations rather than a timing threshold.
            if (sample % 2 == 0) {
                oldBytes[sample] = allocated(old); indexedBytes[sample] = allocated(indexed)
            } else {
                indexedBytes[sample] = allocated(indexed); oldBytes[sample] = allocated(old)
            }
        }
        oldBytes.sort(); indexedBytes.sort()
        val oldPerCall = oldBytes[2].toDouble() / iterations
        val indexedPerCall = indexedBytes[2].toDouble() / iterations
        println("Hook argument Host allocation (${if (after) "after" else "before"}, four args, $iterations calls x 5): " +
            "old=$oldPerCall B/call, indexed=$indexedPerCall B/call")
        assertTrue("The four-argument copy must be observable: old=$oldPerCall indexed=$indexedPerCall",
            oldPerCall - indexedPerCall >= 16.0)
        chain.listReads = 0; chain.copiedProceeds = 0
        repeat(100) { indexed.intercept(chain) }
        assertEquals(0, chain.listReads)
        assertEquals(0, chain.copiedProceeds)
        assertEquals(chain.values[3], valueSink)
        callbackSink = null; valueSink = null
    }
}
