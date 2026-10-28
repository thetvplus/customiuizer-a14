package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.ViewConfiguration
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import tv.withaibuild.customiuizer.mods.utils.OwnedHandlerCallback

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VolumeMediaKeyBoundaryTest {
    private val saved = Controls.controlsConfig
    private val context get() = RuntimeEnvironment.getApplication()
    class Policy(context: Context) {
        @JvmField val mContext = context
        @JvmField val mPowerManager = context.getSystemService(PowerManager::class.java)
        @JvmField val mHandler = Handler(Looper.getMainLooper())
        @JvmField val mBroadcastWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "test-volume")
        fun interceptKeyBeforeQueueing(event: KeyEvent, flags: Int): Int = 41
    }
    private fun hook() = Controls.volumeMediaButtonsHook(requireNotNull(VolumePolicyAccess.resolve(Policy::class.java)))
    class IncompletePolicy { fun interceptKeyBeforeQueueing(event: KeyEvent, flags: Int): Int = 41 }
    @Test fun aMissingPolicyContractDoesNotInstallAPartialHandler() {
        assertNull(VolumePolicyAccess.resolve(Any::class.java))
        assertNull(VolumePolicyAccess.resolve(IncompletePolicy::class.java))
        assertNotNull(VolumePolicyAccess.resolve(Policy::class.java))
    }
    @Before fun setup() {
        for (name in listOf("isPowerPressed", "isVolumePressed", "isVolumeLongPressed", "isWaitingForVolumeLongPressed"))
            ReflectionHelpers.setStaticField(Controls::class.java, name, false)
        Controls.controlsConfig = Controls.ControlsSnapshot(volumeMediaUp = 87)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
    }
    @After fun clean() { Controls.controlsConfig = saved }
    private fun event(code: Int = KeyEvent.KEYCODE_VOLUME_UP, action: Int = KeyEvent.ACTION_DOWN,
        repeat: Int = 0, time: Long = 100, device: Int = 3, flags: Int = KeyEvent.FLAG_FROM_SYSTEM) =
        KeyEvent(time, time + repeat * 10 + action, action, code, repeat, 0, device, 0, flags)

    @Test fun releasingOurPressKeepsUnrelatedPolicyMessagesQueued() {
        val owner = Policy(context); val hook = hook()
        var foreign = 0
        owner.mHandler.postDelayed({ foreign++ }, 100)
        assertEquals(0, hook.intercept(Chain(owner, event())))
        assertEquals(0, hook.intercept(Chain(owner, event(action = KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_CANCELED))))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(1, foreign)
        assertFalse(owner.mBroadcastWakeLock.isHeld)
    }
    @Test fun unconfiguredDirectionAndOrphanUpProceedOnceWithoutReadingThePolicy() {
        val hook = hook()
        for (e in listOf(event(KeyEvent.KEYCODE_VOLUME_DOWN), event(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP),
            event(action = KeyEvent.ACTION_UP))) {
            val chain = Chain(null, e)
            assertEquals(41, hook.intercept(chain)); assertEquals(1, chain.nativeCalls)
        }
    }
    @Test fun repeatedDownNeverRestartsTheTimeoutOrClearsALongPressResult() {
        val owner = Policy(context); val hook = hook()
        hook.intercept(Chain(owner, event()))
        hook.intercept(Chain(owner, event(repeat = 1)))
        ReflectionHelpers.setStaticField(Controls::class.java, "isVolumeLongPressed", true)
        hook.intercept(Chain(owner, event(repeat = 2)))
        assertTrue(ReflectionHelpers.getStaticField(Controls::class.java, "isVolumeLongPressed"))
        hook.intercept(Chain(owner, event(action = KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_CANCELED)))
    }
    @Test fun becomingInteractiveCancelsOnlyTheOwnedTaskAndStillConsumesItsUp() {
        val owner = Policy(context); val hook = hook()
        hook.intercept(Chain(owner, event()))
        shadowOf(owner.mPowerManager).setIsInteractive(true)
        val up = Chain(owner, event(action = KeyEvent.ACTION_UP))
        assertEquals(0, hook.intercept(up)); assertEquals(0, up.nativeCalls)
        assertFalse(ReflectionHelpers.getStaticField(Controls::class.java, "isWaitingForVolumeLongPressed"))
        val next = Chain(owner, event(time = 200))
        assertEquals(41, hook.intercept(next)); assertEquals(1, next.nativeCalls)
    }
    @Test fun aForeignDeviceOrOldReleaseCannotCancelTheCurrentTask() {
        val owner = Policy(context); val hook = hook()
        hook.intercept(Chain(owner, event()))
        for (e in listOf(event(action = KeyEvent.ACTION_UP, device = 9), event(action = KeyEvent.ACTION_UP, time = 99))) {
            val chain = Chain(null, e)
            assertEquals(41, hook.intercept(chain)); assertEquals(1, chain.nativeCalls)
        }
        assertTrue(ReflectionHelpers.getStaticField(Controls::class.java, "isWaitingForVolumeLongPressed"))
        hook.intercept(Chain(owner, event(action = KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_CANCELED)))
    }
    @Test fun aNewStrokeRepairsAMissingReleaseAndDoesNotLeaveAnOldCallback() {
        val owner = Policy(context); val hook = hook()
        hook.intercept(Chain(owner, event()))
        assertEquals(0, hook.intercept(Chain(owner, event(time = 200))))
        assertEquals(0, hook.intercept(Chain(owner, event(action = KeyEvent.ACTION_UP, time = 200,
            flags = KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_CANCELED))))
        assertFalse(ReflectionHelpers.getStaticField(Controls::class.java, "isWaitingForVolumeLongPressed"))
    }
    @Test fun virtualAndNonVolumeEventsStayNativeAndDoNotReadTheOwner() {
        val hook = hook()
        for (e in listOf(event(KeyEvent.KEYCODE_ENTER), event(flags = KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY),
            event(flags = 0), event(repeat = 1))) {
            val chain = Chain(null, e)
            assertEquals(41, hook.intercept(chain)); assertEquals(1, chain.nativeCalls)
        }
    }
    @Test fun callbackOwnershipRejectsStaleDeliveryAndNeverClearsOtherHandlerWork() {
        val handler = Handler(Looper.getMainLooper()); val slot = OwnedHandlerCallback()
        var calls = 0
        lateinit var old: Runnable
        old = Runnable { if (slot.take(old)) calls += 100 }
        lateinit var current: Runnable
        current = Runnable { if (slot.take(current)) calls++ }
        assertTrue(slot.post(handler, old, 50))
        assertTrue(slot.post(handler, current, 50))
        old.run()
        handler.postDelayed({ calls += 10 }, 50)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        assertEquals(11, calls)
        assertFalse(slot.take(current))
    }
    private class Chain(private val owner: Any?, private val event: KeyEvent) : XposedInterface.Chain {
        var nativeCalls = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any = owner ?: error("Unrelated event accessed the policy owner")
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? = if (index == 0) event else 0
        override fun proceed(): Any? { nativeCalls++; return 41 }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
