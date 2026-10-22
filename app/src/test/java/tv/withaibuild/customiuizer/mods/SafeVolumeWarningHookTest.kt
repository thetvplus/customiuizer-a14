package tv.withaibuild.customiuizer.mods

import android.os.Message
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Executable
import java.util.concurrent.ExecutionException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeVolumeWarningHookTest {
    private val hook = SystemUIControlCenterHooks.SafeVolumeWarningHook(Worker::class.java)

    @Test
    fun rearmedLimitsAreAcknowledgedEveryTimeWithoutPermanentlyHidingConfirmation() {
        val controller = Controller(Audio())
        val worker = Worker(controller)
        val audio = controller.mAudio!!
        repeat(2) {
            audio.limited = true // SoundDoseHelper.onCheckMusicActive re-arms the limit.
            val chain = Chain(worker, message(14, 1025))
            assertNull(hook.intercept(chain))
            assertFalse(audio.limited)
            assertTrue(controller.mShowSafetyWarning)
            assertEquals(0, chain.proceedCount)
        }
        assertEquals(2, audio.acknowledgements)
        assertTrue(controller.warnings.isEmpty())
    }

    @Test
    fun failedAcknowledgementsKeepNativeConfirmationAndCanRecoverOnTheNextWarning() {
        val audio = Audio().apply { failure = SecurityException("service unavailable") }
        val controller = Controller(audio)
        val worker = Worker(controller)
        val first = Chain(worker, message(14, 1025))
        val nativeResult = hook.intercept(first)
        assertSame(first.result, nativeResult)
        assertEquals(1, first.proceedCount)
        assertEquals(listOf(1025), controller.warnings)
        assertTrue(controller.mShowSafetyWarning)
        assertTrue(audio.limited)

        audio.failure = null
        val retry = Chain(worker, message(14, 513))
        hook.intercept(retry)
        assertEquals(0, retry.proceedCount)
        assertEquals(2, audio.acknowledgements)
        assertFalse(audio.limited)
        assertEquals(listOf(1025), controller.warnings)
    }

    @Test
    fun successfulAcknowledgementUsesNativePendingVolumeReplayOnlyOnce() {
        val audio = Audio().apply { pendingLevel = 11 }
        hook.intercept(Chain(Worker(Controller(audio)), message(14)))
        assertEquals(listOf(11), audio.appliedLevels)
        assertNull(audio.pendingLevel)
        assertEquals(1, audio.acknowledgements)
    }

    @Test
    fun aConsumedVolumeKeyIsNotInventedOrRepeatedByTheHook() {
        val audio = Audio()
        hook.intercept(Chain(Worker(Controller(audio)), message(14)))
        assertFalse(audio.limited)
        assertTrue(audio.appliedLevels.isEmpty())
        assertEquals(1, audio.acknowledgements)
    }

    @Test
    fun nativeWarningGatesArePreserved() {
        val audio = Audio()
        val controller = Controller(audio).apply { mShowSafetyWarning = false }
        val chain = Chain(Worker(controller), message(14))
        assertSame(chain.result, hook.intercept(chain))
        assertEquals(1, chain.proceedCount)
        assertEquals(0, audio.acknowledgements)
        assertTrue(controller.warnings.isEmpty())
    }

    @Test
    fun otherMessagesIncludingCsdKeepTheirArgumentsResultsAndNativeDispatch() {
        val audio = Audio().apply { failure = AssertionError("must not acknowledge unrelated messages") }
        val controller = Controller(audio)
        val worker = Worker(controller)
        for (what in 0..30) {
            if (what == 14) continue
            val message = message(what, 42, 73)
            val payload = Any()
            message.obj = payload
            val chain = Chain(worker, message)
            assertSame(chain.result, hook.intercept(chain))
            assertEquals(1, chain.proceedCount)
            assertEquals(what, message.what)
            assertEquals(42, message.arg1)
            assertEquals(73, message.arg2)
            assertSame(payload, message.obj)
        }
        assertEquals(listOf(42 to 73), controller.csdWarnings)
        assertEquals(0, audio.acknowledgements)
    }

    @Test
    fun eachWorkerUsesItsCurrentOwnerAndCurrentAudioManager() {
        val first = Controller(Audio())
        val second = Controller(Audio())
        val worker = Worker(first)
        hook.intercept(Chain(worker, message(14)))
        worker.`this$0` = second
        hook.intercept(Chain(worker, message(14)))
        val replacementAudio = Audio()
        second.mAudio = replacementAudio
        hook.intercept(Chain(Worker(second), message(14)))
        assertEquals(1, first.mAudio!!.acknowledgements)
        assertEquals(1, replacementAudio.acknowledgements)
        assertFalse(first.mAudio!!.limited)
        assertFalse(replacementAudio.limited)
    }

    @Test
    fun missingOwnersOrAudioManagersKeepTheNativePath() {
        for (worker in listOf(Worker(null), Worker(Controller(null)), Any())) {
            val chain = Chain(worker, message(14))
            assertSame(chain.result, hook.intercept(chain))
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun invalidOrUnreadableArgumentsKeepTheNativePath() {
        for (argument in listOf(null, "unknown")) {
            val chain = Chain(Worker(Controller(Audio())), argument)
            assertSame(chain.result, hook.intercept(chain))
            assertEquals(1, chain.proceedCount)
        }
        val chain = Chain(Worker(Controller(Audio())), message(14), argFailure = IllegalStateException("argument unavailable"))
        assertSame(chain.result, hook.intercept(chain))
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun nativeFailuresEscapeOnceAfterAFailedAcknowledgement() {
        for (what in listOf(14, 17)) {
            val audio = Audio().apply { failure = IllegalStateException("service unavailable") }
            val nativeFailure = IllegalArgumentException("native dispatch")
            val chain = Chain(Worker(Controller(audio)), message(what), nativeFailure = nativeFailure)
            assertSame(nativeFailure, assertThrows(IllegalArgumentException::class.java) { hook.intercept(chain) })
            assertEquals(1, chain.proceedCount)
            assertEquals(if (what == 14) 1 else 0, audio.acknowledgements)
        }
    }

    @Test
    fun wrappedFatalAcknowledgementFailuresEscapeWithoutDispatchOrStateMutation() {
        for (fatal in listOf(OutOfMemoryError("service"), StackOverflowError("service"), ThreadDeath())) {
            val audio = Audio().apply { failure = ExecutionException(fatal) }
            val controller = Controller(audio)
            val chain = Chain(Worker(controller), message(14))
            assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertEquals(0, chain.proceedCount)
            assertEquals(1, audio.acknowledgements)
            assertTrue(controller.mShowSafetyWarning)
            assertTrue(audio.limited)
        }
    }

    private fun message(what: Int, arg1: Int = 0, arg2: Int = 0): Message = Message.obtain().apply {
        this.what = what
        this.arg1 = arg1
        this.arg2 = arg2
    }

    class Audio {
        var limited = true
        var failure: Throwable? = null
        var acknowledgements = 0
        var pendingLevel: Int? = null
        val appliedLevels = mutableListOf<Int>()
        fun disableSafeMediaVolume() {
            acknowledgements++
            failure?.let { throw it }
            limited = false
            pendingLevel?.let { appliedLevels += it }
            pendingLevel = null
        }
    }
    class Controller(@JvmField var mAudio: Audio?) {
        @JvmField var mShowSafetyWarning = true
        val warnings = mutableListOf<Int>()
        val csdWarnings = mutableListOf<Pair<Int, Int>>()
    }
    class Worker(@JvmField var `this$0`: Controller?) {
        fun handleMessage(message: Message) {
            val controller = `this$0` ?: return
            if (message.what == 14 && controller.mShowSafetyWarning) controller.warnings += message.arg1
            if (message.what == 17) controller.csdWarnings += message.arg1 to message.arg2
        }
    }

    private class Chain(
        private val owner: Any,
        private val message: Any?,
        private val nativeFailure: Throwable? = null,
        private val argFailure: Throwable? = null,
    ) : XposedInterface.Chain {
        val result = Any()
        var proceedCount = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any = owner
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? { argFailure?.let { throw it }; return message }
        override fun proceed(): Any? {
            proceedCount++
            nativeFailure?.let { throw it }
            if (owner is Worker && message is Message) owner.handleMessage(message)
            return result
        }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
