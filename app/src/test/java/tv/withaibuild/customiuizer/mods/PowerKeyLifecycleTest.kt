package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.ViewConfiguration
import io.github.libxposed.api.XposedInterface
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
import tv.withaibuild.customiuizer.utils.HookUtils

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class PowerKeyLifecycleTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val saved=Controls.controlsConfig
    class Policy(context: Context) {
        @JvmField val mContext=context
        @JvmField val mPowerManager=context.getSystemService(PowerManager::class.java)
        @JvmField val mHandler=Handler(Looper.getMainLooper())
        @JvmField val mBroadcastWakeLock=mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"test-power-chord")
        fun interceptKeyBeforeQueueing(event: KeyEvent,flags: Int): Int=41
    }
    private lateinit var policy: Policy
    private val delay get()=(ViewConfiguration.getLongPressTimeout()+500).toLong()
    @Before fun setup() {
        policy=Policy(context)
        Controls.controlsConfig=Controls.ControlsSnapshot()
        for(name in listOf("isPowerPressed","isPowerLongPressed","isVolumePressed"))
            ReflectionHelpers.setStaticField(Controls::class.java,name,false)
        shadowOf(policy.mPowerManager).setIsInteractive(false)
    }
    @After fun cleanup() {
        screenOn()
        policy.mHandler.removeCallbacksAndMessages(null)
        HookUtils.mWakeLock?.let { if(it.isHeld) it.release() }
        HookUtils.mWakeLock=null
        Controls.controlsConfig=saved
    }
    @Test fun quickReleaseAndRepressCannotBorrowThePreviousDeadline() {
        val hook=Controls.powerKeyHook()
        assertEquals(0,hook.intercept(Chain(event(10))))
        idle(100)
        hook.intercept(Chain(event(10,KeyEvent.ACTION_UP)))
        shadowOf(policy.mPowerManager).setIsInteractive(false)
        assertEquals(0,hook.intercept(Chain(event(110))))
        idle(delay-100)
        assertEquals("the first press must not fire for the second press",0,torchRequests())
        idle(100)
        assertEquals(1,torchRequests())
    }
    @Test fun screenOnCancelsPendingPowerWorkWithoutRemovingOtherPolicyMessages() {
        val hook=Controls.powerKeyHook()
        var foreign=0
        policy.mHandler.postDelayed({ foreign++ },delay)
        hook.intercept(Chain(event(10)))
        screenOn()
        idle(delay)
        assertEquals(0,torchRequests())
        assertEquals(1,foreign)
    }
    @Test fun repeatedDownCannotRestartALongPressThatAlreadyFired() {
        val hook=Controls.powerKeyHook()
        hook.intercept(Chain(event(10)))
        idle(delay)
        assertEquals(1,torchRequests())
        hook.intercept(Chain(event(10,repeat=2)))
        idle(delay)
        assertEquals(1,torchRequests())
        val up=Chain(event(10,KeyEvent.ACTION_UP))
        assertEquals(41,hook.intercept(up))
        assertEquals(1,up.nativeCalls)
    }
    @Test fun canceledPressDoesNotWakeTheDeviceOrRunItsTimeout() {
        val hook=Controls.powerKeyHook()
        hook.intercept(Chain(event(10)))
        val canceled=KeyEvent(10,20,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_POWER,0,0,3,0,
            KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_CANCELED)
        assertEquals(0,hook.intercept(Chain(canceled)))
        idle(delay)
        assertEquals(0,torchRequests())
        assertFalse(policy.mPowerManager.isInteractive)
    }
    @Test fun aPhysicalVolumeChordCancelsPowerTimeoutButUnrelatedInputDoesNot() {
        val power=Controls.powerKeyHook()
        val volume=Controls.volumeMediaButtonsHook(requireNotNull(VolumePolicyAccess.resolve(Policy::class.java)))
        power.intercept(Chain(event(10)))
        // Both hooks share the policy entry. Non-volume input cannot retire power ownership.
        volume.intercept(Chain(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME)))
        volume.intercept(Chain(KeyEvent(20,20,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN,0,0,3,0,
            KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY)))
        assertTrue(ReflectionHelpers.getStaticField(Controls::class.java,"isPowerPressed"))
        var foreign=0
        policy.mHandler.postDelayed({ foreign++ },delay)
        val chord=Chain(KeyEvent(20,20,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN,0,0,3,0,KeyEvent.FLAG_FROM_SYSTEM))
        assertEquals(41,volume.intercept(chord))
        assertEquals(1,chord.nativeCalls)
        idle(delay)
        assertEquals("a volume chord must retire the pending torch action",0,torchRequests())
        assertEquals(1,foreign)
    }
    @Test fun nativeFailureEscapesUnchangedAndUnrelatedKeysProceedOnce() {
        val failure=IllegalStateException("native")
        val hook=Controls.powerKeyHook()
        val native=Chain(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME),failure)
        assertSame(failure,runCatching { hook.intercept(native) }.exceptionOrNull())
        assertEquals(1,native.nativeCalls)
        val unowned=Chain(event(10,repeat=1))
        assertEquals(41,hook.intercept(unowned))
        assertEquals(1,unowned.nativeCalls)
        idle(delay)
        assertEquals(0,torchRequests())
    }
    private fun event(time: Long,action: Int=KeyEvent.ACTION_DOWN,repeat: Int=0)=
        KeyEvent(time,time+action,action,KeyEvent.KEYCODE_POWER,repeat,0,3,0,KeyEvent.FLAG_FROM_SYSTEM)
    private fun idle(ms: Long)=shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun torchRequests()=shadowOf(context).broadcastIntents.count { it.action=="miui.intent.action.TOGGLE_TORCH" }
    private fun screenOn() {
        val receiver=ReflectionHelpers.getStaticField<android.content.BroadcastReceiver>(Controls::class.java,"mScreenOnReceiver")
        receiver.onReceive(context,Intent(Intent.ACTION_SCREEN_ON))
    }
    private inner class Chain(private val event: KeyEvent,private val failure: Throwable?=null): XposedInterface.Chain {
        var nativeCalls=0
        override fun getExecutable()=policy.javaClass.getDeclaredMethod("interceptKeyBeforeQueueing",KeyEvent::class.java,Int::class.javaPrimitiveType)
        override fun getThisObject(): Any=policy
        override fun getArgs(): List<Any?> = listOf(event,0)
        override fun getArg(index: Int): Any=if(index==0) event else 0
        override fun proceed(): Any { nativeCalls++; failure?.let { throw it }; return 41 }
        override fun proceed(args: Array<Any?>): Any=proceed()
        override fun proceedWith(receiver: Any): Any=throw AssertionError("receiver changed")
        override fun proceedWith(receiver: Any,args: Array<Any?>): Any=throw AssertionError("receiver changed")
    }
}
