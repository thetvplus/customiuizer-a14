package tv.withaibuild.customiuizer.mods

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowWifiManager
import org.robolectric.shadows.ShadowSubscriptionManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandSystemActivitiesTest {
    @Test fun firstSamplesRepeatsAndResetsNeverReplayOldActivities() {
        val edges = IslandSystemEdges()
        for (kind in IslandSystemKind.entries) {
            assertNull(edges.observe(kind, 0))
            assertNull(edges.observe(kind, 0))
            assertEquals(IslandSystemActivity(kind, 1), edges.observe(kind, 1))
            assertNull(edges.observe(kind, 1))
        }
        edges.seed(IslandSystemKind.DND, 0, replace = true) // A fresh sticky baseline does not emit.
        assertEquals(0, edges.current(IslandSystemKind.DND))
        assertEquals(IslandSystemActivity(IslandSystemKind.DND, 1), edges.observe(IslandSystemKind.DND, 1))
        edges.clear()
        assertNull(edges.current(IslandSystemKind.DND))
        assertNull(edges.observe(IslandSystemKind.DND, 1))
    }

    @Test fun broadcastsAcceptOnlyExplicitStableStates() {
        assertNull(IslandSystemActivities.decode(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)))
        assertEquals(IslandSystemActivity(IslandSystemKind.AIRPLANE, 1),
            IslandSystemActivities.decode(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", true)))
        val sources = listOf(
            Triple(WifiManager.WIFI_STATE_CHANGED_ACTION, WifiManager.EXTRA_WIFI_STATE, IslandSystemKind.WIFI),
            Triple(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED, NfcAdapter.EXTRA_ADAPTER_STATE, IslandSystemKind.NFC),
            Triple(IslandSystemActivities.HOTSPOT_ACTION, "wifi_state", IslandSystemKind.HOTSPOT),
            Triple(BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothAdapter.EXTRA_STATE, IslandSystemKind.BLUETOOTH),
        )
        for ((action, extra, kind) in sources) {
            val off = if (kind == IslandSystemKind.HOTSPOT) 11 else if (kind == IslandSystemKind.BLUETOOTH) 10 else 1
            assertNull(IslandSystemActivities.decode(Intent(action)))
            assertNull(IslandSystemActivities.decode(Intent(action).putExtra(extra, off + 1)))
            assertEquals(IslandSystemActivity(kind, 0), IslandSystemActivities.decode(Intent(action).putExtra(extra, off)))
            assertEquals(IslandSystemActivity(kind, 1), IslandSystemActivities.decode(Intent(action).putExtra(extra, off + 2)))
        }
        assertNull(IslandSystemActivities.decode(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION)))
        assertEquals(IslandSystemActivity(IslandSystemKind.RINGER, AudioManager.RINGER_MODE_VIBRATE),
            IslandSystemActivities.decode(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION)
                .putExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_VIBRATE)))
    }

    @Test @Config(shadows = [HotspotWifiShadow::class])
    fun registrationBelongsToOwnerAndDisposeRejectsQueuedCallbacks() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val base = RuntimeEnvironment.getApplication()
        var adds = 0
        var removes = 0
        var registered: BroadcastReceiver? = null
        val context = object : ContextWrapper(base) {
            override fun getSystemService(name: String): Any? = if (name == Context.CAMERA_SERVICE) null else super.getSystemService(name)
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, permission: String?,
                handler: Handler?, flags: Int): Intent? {
                registered = receiver; adds++
                assertEquals(Context.RECEIVER_EXPORTED, flags)
                assertTrue(filter.hasAction(IslandSystemActivities.HOTSPOT_ACTION))
                return null
            }
            override fun unregisterReceiver(receiver: BroadcastReceiver) { assertSame(registered, receiver); removes++ }
        }
        Settings.Global.putInt(context.contentResolver, "zen_mode", 0)
        val events = mutableListOf<IslandSystemActivity>()
        val source = IslandSystemActivities(context, scope, dispatcher) { events.add(it) }
        fun drain() { scheduler.runCurrent(); shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent() }
        try {
            source.start(); source.start(); drain()
            assertEquals(1, adds)
            assertTrue(events.isEmpty())
            assertEquals(0, source.current(IslandSystemKind.HOTSPOT))
            val content = shadowOf(context.contentResolver)
            for (key in listOf("zen_mode", "mobile_data", "multi_sim_data_call"))
                assertEquals(1, content.getContentObservers(Settings.Global.getUriFor(key)).size)
            assertTrue(content.getContentObservers(Settings.Global.CONTENT_URI).isEmpty())
            val receiver = registered!!
            // A14 sends a non-sticky hotspot broadcast. The cold baseline must make
            // the very first user toggle visible, without waiting for another cycle.
            receiver.onReceive(context, Intent(IslandSystemActivities.HOTSPOT_ACTION).putExtra("wifi_state", 13))
            assertEquals(listOf(IslandSystemActivity(IslandSystemKind.HOTSPOT, 1)), events)
            events.clear()
            val silent = Intent(AudioManager.RINGER_MODE_CHANGED_ACTION)
                .putExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_SILENT)
            receiver.onReceive(context, silent)
            assertEquals(listOf(IslandSystemActivity(IslandSystemKind.RINGER, AudioManager.RINGER_MODE_SILENT)), events)
            receiver.onReceive(context, silent)
            assertEquals(1, events.size)
            Settings.Global.putInt(context.contentResolver, "zen_mode", 1); drain()
            assertTrue(events.contains(IslandSystemActivity(IslandSystemKind.DND, 1)))
            source.resetForUserSwitch(); drain()
            val before = events.size
            source.configurationChanged(Configuration(context.resources.configuration))
            assertEquals(before, events.size)
            source.dispose(); drain()
            assertEquals(1, removes)
            for (key in listOf("zen_mode", "mobile_data", "multi_sim_data_call"))
                assertTrue(content.getContentObservers(Settings.Global.getUriFor(key)).isEmpty())
            receiver.onReceive(context, Intent(AudioManager.RINGER_MODE_CHANGED_ACTION)
                .putExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL))
            Settings.Global.putInt(context.contentResolver, "zen_mode", 0); drain()
            assertEquals(before, events.size)
            source.start(); drain()
            assertEquals(1, adds)
        } finally {
            source.dispose(); scope.cancel(); scheduler.runCurrent(); Dispatchers.resetMain()
        }
    }

    @Implements(WifiManager::class)
    class HotspotWifiShadow : ShadowWifiManager() {
        @Implementation protected fun getWifiApState(): Int = 11
    }

    @Test @Config(shadows = [HotspotWifiShadow::class])
    fun settingsUpdateCannotCancelTheFullBaselineAfterUserSwitch() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val base = RuntimeEnvironment.getApplication()
        var registered: BroadcastReceiver? = null
        val context = object : ContextWrapper(base) {
            override fun getSystemService(name: String): Any? = if (name == Context.CAMERA_SERVICE) null else super.getSystemService(name)
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, permission: String?,
                handler: Handler?, flags: Int): Intent? { registered = receiver; return null }
            override fun unregisterReceiver(receiver: BroadcastReceiver) = Unit
        }
        context.getSystemService(AudioManager::class.java)!!.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val events = mutableListOf<IslandSystemActivity>()
        val source = IslandSystemActivities(context, scope, dispatcher) { events.add(it) }
        fun drain() { scheduler.runCurrent(); shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent() }
        try {
            source.start(); drain()
            assertEquals(AudioManager.RINGER_MODE_NORMAL, source.current(IslandSystemKind.RINGER))
            source.resetForUserSwitch()
            // A coalesced narrow observer read arrives before the full reset baseline publishes.
            ReflectionHelpers.getField<ContentObserver>(source, "observer")
                .onChange(false, Settings.Global.getUriFor("zen_mode"))
            drain()
            assertTrue(events.isEmpty())
            assertEquals(AudioManager.RINGER_MODE_NORMAL, source.current(IslandSystemKind.RINGER))
            registered!!.onReceive(context, Intent(AudioManager.RINGER_MODE_CHANGED_ACTION)
                .putExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_SILENT))
            assertEquals(listOf(IslandSystemActivity(IslandSystemKind.RINGER, AudioManager.RINGER_MODE_SILENT)), events)
        } finally {
            source.dispose(); scope.cancel(); scheduler.runCurrent(); Dispatchers.resetMain()
        }
    }

    @Test @Config(shadows = [HotspotWifiShadow::class])
    fun deniedDataReadStaysUnknownOrRetainsLastStateWithoutDisablingOtherSources() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val base = RuntimeEnvironment.getApplication()
        var registered: BroadcastReceiver? = null
        val context = object : ContextWrapper(base) {
            override fun getSystemService(name: String): Any? = if (name == Context.CAMERA_SERVICE) null else super.getSystemService(name)
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, permission: String?,
                handler: Handler?, flags: Int): Intent? { registered = receiver; return null }
            override fun unregisterReceiver(receiver: BroadcastReceiver) = Unit
        }
        val phone = context.getSystemService(TelephonyManager::class.java)!!
        val phoneShadow = shadowOf(phone)
        val subscription = 42
        ShadowSubscriptionManager.setDefaultDataSubscriptionId(subscription)
        phoneShadow.setTelephonyManagerForSubscriptionId(subscription, phone)
        phoneShadow.setDataEnabled(true)
        phoneShadow.setReadPhoneStatePermission(false)
        context.getSystemService(AudioManager::class.java)!!.ringerMode = AudioManager.RINGER_MODE_NORMAL
        Settings.Global.putInt(context.contentResolver, "zen_mode", 0)
        val events = mutableListOf<IslandSystemActivity>()
        val source = IslandSystemActivities(context, scope, dispatcher) { events.add(it) }
        fun drain() { scheduler.runCurrent(); shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent() }
        try {
            source.start(); drain()
            assertNull(source.current(IslandSystemKind.DATA))
            assertEquals(0, source.current(IslandSystemKind.DND))
            assertEquals(AudioManager.RINGER_MODE_NORMAL, source.current(IslandSystemKind.RINGER))
            assertTrue(events.isEmpty())
            Settings.Global.putInt(context.contentResolver, "zen_mode", 1); drain()
            registered!!.onReceive(context, Intent(AudioManager.RINGER_MODE_CHANGED_ACTION)
                .putExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_SILENT))
            assertEquals(listOf(IslandSystemActivity(IslandSystemKind.DND, 1),
                IslandSystemActivity(IslandSystemKind.RINGER, AudioManager.RINGER_MODE_SILENT)), events)
            assertNull(source.current(IslandSystemKind.DATA))

            // The first authorized sample establishes a baseline without inventing an ON edge.
            phoneShadow.setReadPhoneStatePermission(true)
            Settings.Global.putInt(context.contentResolver, "mobile_data$subscription", 1); drain()
            assertEquals(1, source.current(IslandSystemKind.DATA))
            assertTrue(events.none { it.kind == IslandSystemKind.DATA })

            // A revoked permission is unavailable, even if the unreadable real value changed to OFF.
            phoneShadow.setReadPhoneStatePermission(false)
            phoneShadow.setDataEnabled(false)
            Settings.Global.putInt(context.contentResolver, "mobile_data$subscription", 0); drain()
            assertEquals(1, source.current(IslandSystemKind.DATA))
            assertTrue(events.none { it.kind == IslandSystemKind.DATA })
            Settings.Global.putInt(context.contentResolver, "zen_mode", 0); drain()
            assertEquals(IslandSystemActivity(IslandSystemKind.DND, 0), events.last())
        } finally {
            source.dispose(); scope.cancel(); scheduler.runCurrent(); Dispatchers.resetMain()
            phoneShadow.setReadPhoneStatePermission(true)
            ShadowSubscriptionManager.setDefaultDataSubscriptionId(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun allModesSupplySeparateLocalizedLabelsAndBoundedWhiteGlyphs() {
        val context = RuntimeEnvironment.getApplication()
        for (kind in IslandSystemKind.entries) {
            val content = IslandSystemActivity(kind, 1).content(context)
            assertTrue(content.status)
            assertTrue(content.text.isNotBlank())
            assertTrue(content.detail!!.isNotBlank())
            assertNull(content.notificationKey)
            assertNull(content.launch)
            val glyph = content.icon!!
            assertTrue(glyph.intrinsicWidth > 0 && glyph.intrinsicHeight > 0)
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            glyph.setBounds(0, 0, 48, 48)
            glyph.draw(Canvas(bitmap))
            var painted = 0
            var contrasting = 0
            for (y in 0 until 48) for (x in 0 until 48) if (bitmap.getPixel(x, y) ushr 24 != 0) {
                painted++
                // Ignore coverage-rounding at translucent antialiased edges.
                val color = bitmap.getPixel(x, y)
                if (color ushr 24 >= 192 && color and 255 >= 250 &&
                    color shr 8 and 255 >= 250 && color shr 16 and 255 >= 250) contrasting++
            }
            assertTrue("Empty $kind glyph", painted > 30)
            assertTrue("Native $kind glyph must contrast with the capsule", contrasting > 30)
            assertTrue("Unbounded $kind glyph", painted < 48 * 48)
            bitmap.recycle()
        }
        val off = IslandSystemActivity(IslandSystemKind.DND, 0).content(context)
        val on = IslandSystemActivity(IslandSystemKind.DND, 1).content(context)
        assertNotEquals(off.text, on.text)
        assertEquals(off.detail, on.detail)
    }
}
