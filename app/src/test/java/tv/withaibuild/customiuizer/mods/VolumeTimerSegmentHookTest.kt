package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolumeTimerSegmentHookTest {
    private val hook = SystemUIControlCenterHooks.VolumeTimerSegmentHook(TimerHelper::class.java)

    @Test
    fun nestedMuteAndDndUpdatesRestoreTheirOwnSegments() {
        val mute = TimerHelper(2, 2)
        val dnd = TimerHelper(8, 8)
        val inner = Chain(dnd)
        val outer = Chain(mute) {
            assertEquals(0, mute.mCurrentSegment)
            hook.intercept(inner)
        }
        hook.intercept(outer)
        assertEquals(2, mute.mCurrentSegment)
        assertEquals(8, dnd.mCurrentSegment)
        assertEquals(1, outer.proceedCount)
        assertEquals(1, inner.proceedCount)
    }

    @Test
    fun sameHelperReentryRestoresTheOuterSegment() {
        val helper = TimerHelper(2, 2)
        val inner = Chain(helper)
        val outer = Chain(helper) { hook.intercept(inner) }
        hook.intercept(outer)
        assertEquals(2, helper.mCurrentSegment)
        assertEquals(1, inner.proceedCount)
        assertEquals(1, outer.proceedCount)
    }

    @Test
    fun onlyTheEstablishedCrowdedSegmentsAreAdjustedDuringDrawing() {
        for ((current, determined, drawn) in listOf(
            Triple(0, 0, 0), Triple(1, 1, 0), Triple(2, 2, 0), Triple(3, 3, 0),
            Triple(3, 4, 3), Triple(4, 4, 4), Triple(10, 10, 10),
        )) {
            val helper = TimerHelper(current, determined)
            val chain = Chain(helper) { assertEquals(drawn, helper.mCurrentSegment) }
            hook.intercept(chain)
            assertEquals(current, helper.mCurrentSegment)
            assertEquals(determined, helper.mDeterminedSegment)
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun anUnknownNegativeSegmentKeepsNativeBehavior() {
        val helper = TimerHelper(-1, -1)
        val chain = Chain(helper) { assertEquals(-1, helper.mCurrentSegment) }
        hook.intercept(chain)
        assertEquals(-1, helper.mCurrentSegment)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun temporarySegmentIsRestoredAfterAnOrdinaryNativeFailure() {
        val helper = TimerHelper(2, 2)
        val error = IllegalStateException("native draw failure")
        val chain = Chain(helper) { throw error }
        assertSame(error, assertThrows(Throwable::class.java) { hook.intercept(chain) })
        assertEquals(2, helper.mCurrentSegment)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun fatalNativeFailuresRestoreTheSegmentBeforeEscaping() {
        for (error in listOf(OutOfMemoryError("native"), StackOverflowError("native"), ThreadDeath())) {
            val helper = TimerHelper(2, 2)
            val chain = Chain(helper) { throw error }
            assertSame(error, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertEquals(2, helper.mCurrentSegment)
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun wrappedFatalFailuresKeepTheExistingFatalBoundary() {
        for (segment in listOf(2, 8)) {
            for (fatal in listOf(OutOfMemoryError("native"), StackOverflowError("native"), ThreadDeath())) {
                val helper = TimerHelper(segment, segment)
                val chain = Chain(helper) { throw InvocationTargetException(ExecutionException(fatal)) }
                assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
                assertEquals(segment, helper.mCurrentSegment)
                assertEquals(1, chain.proceedCount)
            }
        }
    }

    @Test
    fun ordinaryWrappedFailuresRemainUnchanged() {
        val helper = TimerHelper(2, 2)
        val error = InvocationTargetException(IllegalStateException("native"))
        val chain = Chain(helper) { throw error }
        assertSame(error, assertThrows(Throwable::class.java) { hook.intercept(chain) })
        assertEquals(2, helper.mCurrentSegment)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun aNewNativeSegmentIsNotOverwrittenByCleanup() {
        val helper = TimerHelper(2, 2)
        val chain = Chain(helper) { helper.mCurrentSegment = 6 }
        hook.intercept(chain)
        assertEquals(6, helper.mCurrentSegment)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun originalResultIsReturnedWithoutRepeatingTheDraw() {
        val result = Any()
        val helper = TimerHelper(1, 1)
        val chain = Chain(helper, result)
        assertSame(result, hook.intercept(chain))
        assertEquals(1, helper.mCurrentSegment)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun failedAdaptationKeepsTheNativeResultAndException() {
        val result = Any()
        val success = Chain(Any(), result)
        assertSame(result, hook.intercept(success))
        assertEquals(1, success.proceedCount)
        val error = IllegalArgumentException("native")
        val failure = Chain(Any()) { throw error }
        assertSame(error, assertThrows(Throwable::class.java) { hook.intercept(failure) })
        assertEquals(1, failure.proceedCount)
    }

    private class TimerHelper(
        @JvmField var mCurrentSegment: Int,
        @JvmField var mDeterminedSegment: Int,
    )

    private class Chain(
        private val helper: Any,
        private val result: Any? = null,
        private val draw: () -> Unit = {},
    ) : XposedInterface.Chain {
        var proceedCount = 0
        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any = helper
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = error("not used")
        override fun proceed(): Any? {
            proceedCount++
            draw()
            return result
        }
        override fun proceed(args: Array<Any?>): Any? = error("not used")
        override fun proceedWith(receiver: Any): Any? = error("not used")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("not used")
    }
}
