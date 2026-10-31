package tv.withaibuild.customiuizer

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWifiManager
import tv.withaibuild.customiuizer.subs.WiFiList
import tv.withaibuild.customiuizer.ui.NetworkSelectionRow
import tv.withaibuild.customiuizer.utils.AppHelper
import java.time.Duration

@Implements(WifiManager::class)
class CountingScanWifiShadow : ShadowWifiManager() {
    var scans = 0
    @Implementation override fun startScan(): Boolean { scans++; return super.startScan() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, shadows = [CountingScanWifiShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class WifiListLifecycleTest {
    private lateinit var wifi: WifiManager
    private lateinit var shadow: CountingScanWifiShadow
    private val controller by lazy { Robolectric.buildActivity(AppCompatActivity::class.java) }
    private lateinit var page: WiFiList

    @Before fun prepare() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        AppHelper.appPrefs = app.getSharedPreferences("wifi-test", Context.MODE_PRIVATE)
        wifi = app.getSystemService(WifiManager::class.java)
        shadow = Shadow.extract(wifi)
        shadow.setWifiState(WifiManager.WIFI_STATE_ENABLED)
        shadow.setScanResults(emptyList())
        shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
    }
    @After fun close() {
        if (::page.isInitialized) controller.pause().stop().destroy()
        AppHelper.appPrefs = null
    }
    private fun launch() {
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        page = WiFiList().apply { arguments = Bundle().apply { putString("key", "test_networks"); putInt("contentResId", R.id.prefs_system); putInt("settingsType", AppHelper.SettingsType.Edit.ordinal) } }
        controller.get().supportFragmentManager.beginTransaction().add(android.R.id.content, page).commitNow()
    }
    private fun result(id: String) = ScanResult().apply { BSSID = id; timestamp = SystemClock.elapsedRealtime() * 1000 }
    private fun refresh() = WiFiList::class.java.getDeclaredMethod("requestScan").apply { isAccessible = true }.invoke(page)
    @Suppress("UNCHECKED_CAST")
    private fun rows() = WiFiList::class.java.getDeclaredMethod("getRows").apply { isAccessible = true }.invoke(page) as List<NetworkSelectionRow>
    private fun broadcast() {
        controller.get().sendBroadcast(Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun cachedResultsAvoidInitialScanAndSystemBroadcastUpdatesRows() {
        shadow.setScanResults(listOf(result("00:11:22:33:44:55")))
        launch()
        assertEquals(0, shadow.scans)
        assertEquals("00:11:22:33:44:55", rows().single().id)
        shadow.setScanResults(listOf(result("00:11:22:33:44:66")))
        broadcast()
        assertEquals("00:11:22:33:44:66", rows().single().id)
        assertEquals(0, shadow.scans)
    }
    @Test fun emptyCacheScansOnceManualRefreshDoesNotOverlapAndPauseStopsAllWork() {
        launch()
        assertEquals(1, shadow.scans)
        refresh(); refresh()
        assertEquals(1, shadow.scans)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
        assertEquals(1, shadow.scans)
        refresh()
        assertEquals(2, shadow.scans)
        controller.pause()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        refresh()
        assertEquals(2, shadow.scans)
        controller.resume()
        assertEquals(3, shadow.scans)
    }
    @Test fun deniedPermissionAndRejectedScanNeverLeaveAnInfiniteWait() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        launch()
        assertEquals(0, shadow.scans)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadow.setStartScanSucceeds(false)
        refresh(); refresh()
        assertEquals(2, shadow.scans)
    }
}
