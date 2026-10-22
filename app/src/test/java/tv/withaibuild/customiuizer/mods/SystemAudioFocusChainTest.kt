package tv.withaibuild.customiuizer.mods

import android.media.AudioManager
import android.os.VibrationAttributes
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Executable
import java.util.Stack

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemAudioFocusChainTest {
    private val phoneClient = "AudioFocus_For_Phone_Ring_And_Calls"
    private val control = FakeFocusControl()
    private val service = FakeAudioService(control)
    private val access = SystemAudioHooks.CallAudioFocusAccess(
        FakeAudioService::class.java, FakeFocusControl::class.java, FakeRequester::class.java,
    )
    private lateinit var savedConfig: SystemAudioHooks.AudioHapticsSnapshot

    @Before
    fun setUp() {
        savedConfig = SystemAudioHooks.audioHapticsConfig
        SystemAudioHooks.audioHapticsConfig = savedConfig.copy(ignoreCallsApps = setOf("com.player"))
    }

    @After
    fun tearDown() {
        SystemAudioHooks.audioHapticsConfig = savedConfig
    }

    @Test
    fun vibrationScalingUsesNativeUsageWithoutChangingDurationOrRepeatingTheCall() {
        SystemAudioHooks.audioHapticsConfig = savedConfig.copy(ampRinger = .4f, ampNotif = .6f, ampOther = .8f)
        for ((usage, ratio) in listOf(VibrationAttributes.USAGE_RINGTONE to .4f,
            VibrationAttributes.USAGE_NOTIFICATION to .6f, VibrationAttributes.USAGE_ALARM to .8f)) {
            val original = ScalableEffect(120)
            val chain = FakeChain(args = listOf(Any(), usage), result = original)
            val result = SystemAudioHooks.interceptVibrationScale(chain, ScalableEffect::class.java.getMethod("scale", Float::class.javaPrimitiveType)) as ScalableEffect
            assertEquals(1, chain.proceedCount)
            assertEquals(120, result.duration)
            assertEquals(ratio, result.factor, 0f)
            assertEquals(1f, original.factor, 0f)
        }
    }

    @Test
    fun vibrationScaleDefaultsUnsupportedEffectsAndFailuresPreserveNativeResult() {
        val method = ScalableEffect::class.java.getMethod("scale", Float::class.javaPrimitiveType)
        val native = ScalableEffect(120)
        SystemAudioHooks.audioHapticsConfig = savedConfig.copy(ampRinger = 1f)
        assertSame(native, SystemAudioHooks.interceptVibrationScale(
            FakeChain(args = listOf(Any(), VibrationAttributes.USAGE_RINGTONE), result = native), method))
        SystemAudioHooks.audioHapticsConfig = savedConfig.copy(ampRinger = .5f)
        for (effect in listOf(native.apply { failure = IllegalStateException("unsupported") }, Any())) {
            val chain = FakeChain(args = listOf(Any(), VibrationAttributes.USAGE_RINGTONE), result = effect)
            assertSame(effect, SystemAudioHooks.interceptVibrationScale(chain, method))
            assertEquals(1, chain.proceedCount)
        }
        val readFailure = FakeChain(result = native, argFailure = IllegalStateException("usage unavailable"))
        assertSame(native, SystemAudioHooks.interceptVibrationScale(readFailure, method))
        assertEquals(1, readFailure.proceedCount)
    }

    @Test
    fun vibrationScalePreservesNativeAndWrappedFatalErrors() {
        val method = ScalableEffect::class.java.getMethod("scale", Float::class.javaPrimitiveType)
        val nativeFailure = IllegalArgumentException("native")
        val chain = FakeChain(failure = nativeFailure)
        assertSame(nativeFailure, assertThrows(IllegalArgumentException::class.java) {
            SystemAudioHooks.interceptVibrationScale(chain, method)
        })
        assertEquals(1, chain.proceedCount)
        SystemAudioHooks.audioHapticsConfig = savedConfig.copy(ampRinger = .5f)
        val fatal = OutOfMemoryError("scale")
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            SystemAudioHooks.interceptVibrationScale(FakeChain(
                args = listOf(Any(), VibrationAttributes.USAGE_RINGTONE),
                result = ScalableEffect(20).apply { failure = fatal }), method)
        })
    }

    @Test
    fun vibrationPeriodsIncludeStartExcludeEndAndWrapMidnight() {
        for (minute in 0..1439) {
            assertEquals(true, SystemAudioHooks.vibrationPeriodContains(0, 0, minute))
            assertEquals(minute in 480 until 1200, SystemAudioHooks.vibrationPeriodContains(480, 1200, minute))
            assertEquals(minute >= 1320 || minute < 420, SystemAudioHooks.vibrationPeriodContains(1320, 420, minute))
        }
    }

    class ScalableEffect(val duration: Int, val factor: Float = 1f) {
        var failure: Throwable? = null
        fun scale(factor: Float): ScalableEffect {
            failure?.let { throw it }
            return ScalableEffect(duration, factor)
        }
    }

    @Test
    fun selectiveVibrationUsesOpPackageAfterUidAndDeviceIdForBothListModes() {
        for (mode in 1..3) for (packageName in listOf("com.selected", "com.other")) {
            SystemAudioHooks.audioHapticsConfig = savedConfig.copy(
                vibrationMode = mode, vibrationApps = setOf("com.selected"))
            val result = Any()
            val effect = Any()
            val attributes = Any()
            val chain = FakeChain(args = listOf(10001, 7, packageName, effect, attributes, "reason", Any()), result = result)
            val blocked = (mode == 2 && packageName != "com.selected") || (mode == 3 && packageName == "com.selected")
            assertSame(if (blocked) null else result, SystemAudioHooks.interceptSelectiveVibration(chain))
            assertEquals(if (blocked) 0 else 1, chain.proceedCount)
            assertEquals(7, chain.getArg(1))
            assertSame(effect, chain.getArg(3))
            assertSame(attributes, chain.getArg(4))
        }
    }

    @Test
    fun selectiveVibrationLeavesUnknownPackagesAndReadFailuresToNativeValidationOnce() {
        SystemAudioHooks.audioHapticsConfig = savedConfig.copy(vibrationMode = 2)
        for (chain in listOf(
            FakeChain(args = listOf(10001, 0, null)),
            FakeChain(args = listOf(10001, 0, 12)),
            FakeChain(argFailure = IllegalStateException("unavailable arguments")),
        )) {
            SystemAudioHooks.interceptSelectiveVibration(chain)
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun selectiveVibrationPreservesTheNativeExceptionAndFatalReadFailures() {
        SystemAudioHooks.audioHapticsConfig = savedConfig.copy(vibrationMode = 3)
        val failure = IllegalArgumentException("native permission failure")
        val native = FakeChain(args = listOf(10001, 0, "com.other"), failure = failure)
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) {
            SystemAudioHooks.interceptSelectiveVibration(native)
        })
        assertEquals(1, native.proceedCount)
        for (error in listOf(OutOfMemoryError(), ThreadDeath(), object : VirtualMachineError() {})) {
            val unreadable = FakeChain(argFailure = error)
            assertSame(error, assertThrows(error.javaClass) { SystemAudioHooks.interceptSelectiveVibration(unreadable) })
            assertEquals(0, unreadable.proceedCount)
        }
    }

    @Test
    fun duckingIsHandledWithoutCallingTheNativeVolumeOperation() {
        val chain = FakeChain(args = listOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, Any(), false))
        assertEquals(true, SystemAudioHooks.interceptDucking(chain))
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun otherLossesAndMissingWinnerKeepNativeBehavior() {
        for (loss in listOf(AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, 0, 1)) {
            val result = Any()
            val chain = FakeChain(args = listOf(loss, Any(), false), result = result)
            assertSame(result, SystemAudioHooks.interceptDucking(chain))
            assertEquals(1, chain.proceedCount)
        }
        val chain = FakeChain(args = listOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, null, false), result = false)
        assertEquals(false, SystemAudioHooks.interceptDucking(chain))
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun onlyTheCurrentSelectedFocusOwnerSuppressesTheCall() {
        control.mFocusStack.push(FakeRequester("com.player"))
        val chain = callChain()
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, SystemAudioHooks.interceptCallAudioFocus(chain, access))
        assertEquals(0, chain.proceedCount)
        assertEquals(1, control.mFocusStack.size)
        assertFalse(control.mRingOrCallActive)
    }

    @Test
    fun abandonOrBinderDeathLeavesNoRememberedPackage() {
        control.mFocusStack.push(FakeRequester("com.player"))
        SystemAudioHooks.interceptCallAudioFocus(callChain(), access)
        control.mFocusStack.clear()
        assertNativeCall()
    }

    @Test
    fun replacingAndRestoringTheOwnerUsesTheLiveStack() {
        control.mFocusStack.push(FakeRequester("com.player"))
        control.mFocusStack.push(FakeRequester("com.other"))
        assertNativeCall()
        control.mFocusStack.pop()
        val chain = callChain()
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, SystemAudioHooks.interceptCallAudioFocus(chain, access))
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun delayedFailedAndExternalPolicyRequestsDoNotBecomeTheOwner() {
        control.mFocusStack.push(FakeRequester("com.other"))
        for (result in listOf(AudioManager.AUDIOFOCUS_REQUEST_FAILED, AudioManager.AUDIOFOCUS_REQUEST_DELAYED, 100)) {
            val chain = FakeChain(
                receiver = service, args = listOf(null, null, null, null, "player-client", "com.player"), result = result,
            )
            assertEquals(result, SystemAudioHooks.interceptCallAudioFocus(chain, access))
            assertEquals(1, chain.proceedCount)
            assertNativeCall()
        }
    }

    @Test
    fun lostFocusAndAnExistingPhoneOwnerKeepNativeBehavior() {
        val owner = FakeRequester("com.player")
        control.mFocusStack.push(owner)
        for (loss in listOf(AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) {
            owner.mFocusLossReceived = loss
            assertNativeCall()
        }
        owner.mFocusLossReceived = 0
        owner.mClientId = phoneClient
        assertNativeCall()
    }

    @Test
    fun externalPolicyMultipleOwnersAndActiveCallsKeepNativeBehavior() {
        control.mFocusStack.push(FakeRequester("com.player"))
        control.mFocusPolicy = Any()
        assertNativeCall()
        control.mFocusPolicy = null
        control.mMultiAudioFocusEnabled = true
        assertNativeCall()
        control.mMultiAudioFocusEnabled = false
        control.mRingOrCallActive = true
        assertNativeCall()
    }

    @Test
    fun selectionChangesApplyWithoutRememberingThePreviousDecision() {
        control.mFocusStack.push(FakeRequester("com.player"))
        SystemAudioHooks.interceptCallAudioFocus(callChain(), access)
        SystemAudioHooks.audioHapticsConfig = savedConfig.copy(ignoreCallsApps = emptySet())
        assertNativeCall()
    }

    @Test
    fun anotherServiceInstanceDoesNotInheritThePreviousOwner() {
        control.mFocusStack.push(FakeRequester("com.player"))
        SystemAudioHooks.interceptCallAudioFocus(callChain(), access)
        val chain = callChain(receiver = FakeAudioService(FakeFocusControl()))
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_FAILED, SystemAudioHooks.interceptCallAudioFocus(chain, access))
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun stackReadsUseTheNativeAudioFocusLock() {
        control.mFocusStack = object : Stack<Any?>() {
            override fun peek(): Any? {
                check(Thread.holdsLock(FakeFocusControl.mAudioFocusLock))
                return super.peek()
            }
        }.apply { push(FakeRequester("com.player")) }
        val chain = callChain()
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, SystemAudioHooks.interceptCallAudioFocus(chain, access))
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun unknownServiceOrOwnerFallsBackToNativeOnce() {
        val badService = callChain(receiver = Any())
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_FAILED, SystemAudioHooks.interceptCallAudioFocus(badService, access))
        assertEquals(1, badService.proceedCount)
        control.mFocusStack.push(Any())
        assertNativeCall()
        control.mFocusStack.clear()
        control.mFocusStack.push(FakeRequester(null))
        assertNativeCall()
        service.mMediaFocusControl = null
        assertNativeCall()
    }

    @Test
    fun firstVolumePressRetainsNativeBookkeepingBeforeChangingTheResult() {
        val controller = FakeVolumeController(Any())
        val chain = volumeChain(receiver = controller, onProceed = { controller.nativeUpdates++ })
        assertEquals(false, SystemAudioHooks.interceptFirstVolumePress(chain))
        assertEquals(1, chain.proceedCount)
        assertEquals(1, controller.nativeUpdates)
    }

    @Test
    fun muteOtherStreamsAndAbsentControllersRetainTheNativeResult() {
        for (chain in listOf(
            volumeChain(stream = AudioManager.STREAM_RING),
            volumeChain(mute = true),
            volumeChain(receiver = FakeVolumeController(null)),
            volumeChain(receiver = Any()),
        )) {
            assertEquals(true, SystemAudioHooks.interceptFirstVolumePress(chain))
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun originalsKeepTheirExceptionsAndRunOnlyOnce() {
        for (error in listOf(RuntimeException("native"), OutOfMemoryError("native"),
            StackOverflowError("native"), ThreadDeath())) {
            val ducking = FakeChain(args = listOf(AudioManager.AUDIOFOCUS_LOSS, Any(), false), failure = error)
            assertSame(error, assertThrows(Throwable::class.java) { SystemAudioHooks.interceptDucking(ducking) })
            assertEquals(1, ducking.proceedCount)
            val focus = callChain(failure = error)
            assertSame(error, assertThrows(Throwable::class.java) { SystemAudioHooks.interceptCallAudioFocus(focus, access) })
            assertEquals(1, focus.proceedCount)
            val volume = volumeChain(failure = error)
            assertSame(error, assertThrows(Throwable::class.java) { SystemAudioHooks.interceptFirstVolumePress(volume) })
            assertEquals(1, volume.proceedCount)
        }
    }

    @Test
    fun fatalAdaptationFailuresNeverTurnIntoFallbackResults() {
        for (error in listOf(OutOfMemoryError("argument access"), StackOverflowError("argument access"), ThreadDeath())) {
            val focus = callChain(argFailure = error)
            assertSame(error, assertThrows(Throwable::class.java) { SystemAudioHooks.interceptCallAudioFocus(focus, access) })
            assertEquals(0, focus.proceedCount)
            val ducking = FakeChain(argFailure = error)
            assertSame(error, assertThrows(Throwable::class.java) { SystemAudioHooks.interceptDucking(ducking) })
            assertEquals(0, ducking.proceedCount)
            val volume = FakeChain(result = true, argFailure = error)
            assertSame(error, assertThrows(Throwable::class.java) { SystemAudioHooks.interceptFirstVolumePress(volume) })
            assertEquals(1, volume.proceedCount)
        }
    }

    @Test
    fun ordinaryArgumentFailuresKeepNativeResults() {
        val failure = IllegalStateException("argument access")
        val focus = callChain(argFailure = failure)
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_FAILED, SystemAudioHooks.interceptCallAudioFocus(focus, access))
        assertEquals(1, focus.proceedCount)
        val ducking = FakeChain(result = false, argFailure = failure)
        assertEquals(false, SystemAudioHooks.interceptDucking(ducking))
        assertEquals(1, ducking.proceedCount)
        val volume = FakeChain(result = true, argFailure = failure)
        assertEquals(true, SystemAudioHooks.interceptFirstVolumePress(volume))
        assertEquals(1, volume.proceedCount)
    }

    private fun assertNativeCall() {
        val chain = callChain()
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_FAILED, SystemAudioHooks.interceptCallAudioFocus(chain, access))
        assertEquals(1, chain.proceedCount)
    }

    private fun callChain(receiver: Any = service, failure: Throwable? = null, argFailure: Throwable? = null) = FakeChain(
        receiver = receiver, args = listOf(null, null, null, null, phoneClient),
        result = AudioManager.AUDIOFOCUS_REQUEST_FAILED, failure = failure, argFailure = argFailure,
    )

    private fun volumeChain(
        stream: Int = AudioManager.STREAM_MUSIC, mute: Boolean = false,
        receiver: Any = FakeVolumeController(Any()), failure: Throwable? = null, onProceed: () -> Unit = {},
    ) = FakeChain(receiver, listOf(stream, 1, mute), true, failure, onProceed = onProceed)

    private class FakeAudioService(@JvmField var mMediaFocusControl: Any?)

    private class FakeFocusControl {
        @JvmField var mFocusStack = Stack<Any?>()
        @JvmField var mFocusPolicy: Any? = null
        @JvmField var mMultiAudioFocusEnabled = false
        @JvmField var mRingOrCallActive = false
        companion object {
            @JvmField val mAudioFocusLock = Any()
        }
    }

    private class FakeRequester(@JvmField var mPackageName: String?) {
        @JvmField var mClientId = "player-client"
        @JvmField var mFocusLossReceived = 0
    }

    private class FakeVolumeController(@JvmField var mController: Any?) {
        var nativeUpdates = 0
    }

    private class FakeChain(
        private val receiver: Any? = null,
        private val args: List<Any?> = emptyList(),
        private val result: Any? = null,
        private val failure: Throwable? = null,
        private val argFailure: Throwable? = null,
        private val onProceed: () -> Unit = {},
    ) : XposedInterface.Chain {
        var proceedCount = 0
        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? = receiver
        override fun getArgs(): List<Any?> = args
        override fun getArg(index: Int): Any? {
            argFailure?.let { throw it }
            return args[index]
        }
        override fun proceed(): Any? {
            proceedCount++
            failure?.let { throw it }
            onProceed()
            return result
        }
        override fun proceed(args: Array<Any?>): Any? = error("not used")
        override fun proceedWith(receiver: Any): Any? = error("not used")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("not used")
    }
}
