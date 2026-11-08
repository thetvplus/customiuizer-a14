package tv.withaibuild.customiuizer.mods.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
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
import tv.withaibuild.customiuizer.utils.PrefMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeviceInfoMonitorLifecycleTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val handlers=ArrayList<Handler>()
    private val state get()=field("monitorState").get(null) as DeviceInfoMonitorState
    private val monitor=200021
    private val update=100021

    @Before fun setup() {
        call("stopMonitoring")
        configure(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
    }
    @After fun cleanup() {
        handlers.forEach { it.removeCallbacksAndMessages(null) }
        call("stopMonitoring")
        field("config").set(null,null)
    }
    @Test fun refreshDuringAnInFlightTickKeepsOneImmediateWakeup() {
        var ticks=0
        val handler=object: Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) { ticks++ }
        }.also(handlers::add)
        val id=state.startNewGeneration()
        field("activeBgHandler").set(null,handler)
        // A settings/screen event has already queued a fresh read while an old
        // read completes. Its finally block must not add another periodic chain.
        handler.sendEmptyMessage(monitor)
        repeat(32) { call("scheduleNextTick",id) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,ticks)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals("no duplicate delayed wakeups may survive the refresh",1,ticks)
    }
    @Test fun screenOffAndStopClearUiMessagesOnTheirOwningHandler() {
        start()
        val main=field("activeMainHandler").get(null) as Handler
        val bg=field("activeBgHandler").get(null) as Handler
        main.sendEmptyMessage(update)
        receiver().onReceive(context,Intent(Intent.ACTION_SCREEN_OFF))
        assertFalse(main.hasMessages(update))
        assertFalse(bg.hasMessages(monitor))
        val data=Class.forName(DeviceInfoMonitor::class.java.name+"\$DeviceData").declaredConstructors.single()
            .apply { isAccessible=true }.newInstance(false,"",true,"reading")
        call("publishReadings",field("config").get(null),data,state.activeBgHandlerId)
        assertFalse("a read finishing after screen-off cannot enqueue UI work",main.hasMessages(update))
        main.sendEmptyMessage(update)
        call("stopMonitoring")
        assertFalse(main.hasMessages(update))
        assertFalse(bg.hasMessages(monitor))
        assertNull(field("activeContext").get(null))
        assertNull(field("screenReceiver").get(null))
    }
    @Test fun disabledFeatureCannotRestartWhenTheNativeControllerIsRebuilt() {
        configure(false)
        start()
        assertNull(field("activeBgHandler").get(null))
        assertNull(field("activeMainHandler").get(null))
        assertNull(field("screenReceiver").get(null))
    }
    @Test fun initiallySleepingHostDoesNotScheduleASysfsRead() {
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        start()
        val bg=field("activeBgHandler").get(null) as Handler
        assertFalse(state.screenOn)
        assertFalse(bg.hasMessages(monitor))
        receiver().onReceive(context,Intent(Intent.ACTION_SCREEN_ON))
        assertTrue(bg.hasMessages(monitor))
    }
    @Test fun registrationFailureReleasesPartialInitializationAndPropagatesTheCause() {
        for(failure in listOf(IllegalStateException("registration failed"),OutOfMemoryError("fatal"))) {
            val broken=object: android.content.ContextWrapper(context) {
                override fun registerReceiver(receiver: android.content.BroadcastReceiver?,filter: android.content.IntentFilter?,flags: Int): Intent? = throw failure
            }
            val thrown=runCatching { call("startMonitoring",broken,Looper.getMainLooper()) }.exceptionOrNull()
            assertSame(failure,thrown?.cause)
            assertNull(field("activeBgHandler").get(null))
            assertNull(field("activeMainHandler").get(null))
            assertNull(field("activeContext").get(null))
            assertNull(field("screenReceiver").get(null))
        }
    }
    @Test fun replacedReceiverCannotWakeTheNewOwner() {
        start()
        val old=receiver()
        start()
        val bg=field("activeBgHandler").get(null) as Handler
        receiver().onReceive(context,Intent(Intent.ACTION_SCREEN_OFF))
        old.onReceive(context,Intent(Intent.ACTION_SCREEN_ON))
        assertFalse(state.screenOn)
        assertFalse(bg.hasMessages(monitor))
    }
    private fun configure(enabled: Boolean) {
        val prefs=PrefMap().apply { put("system_statusbar_showdevicetemperature",enabled) }
        field("config").set(null,call("buildConfig",prefs))
    }
    private fun start() {
        call("startMonitoring",context,Looper.getMainLooper())
        for(name in listOf("activeMainHandler","activeBgHandler"))
            (field(name).get(null) as? Handler)?.let(handlers::add)
    }
    private fun receiver()=field("screenReceiver").get(null) as android.content.BroadcastReceiver
    private fun field(name: String)=DeviceInfoMonitor::class.java.getDeclaredField(name).apply { isAccessible=true }
    private fun call(name: String,vararg args: Any?): Any? = DeviceInfoMonitor::class.java.declaredMethods
        .single { it.name==name }.apply { isAccessible=true }.invoke(DeviceInfoMonitor,*args)
}
