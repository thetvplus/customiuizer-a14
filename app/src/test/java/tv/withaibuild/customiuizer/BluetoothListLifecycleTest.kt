package tv.withaibuild.customiuizer

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.subs.BTList
import tv.withaibuild.customiuizer.utils.AppHelper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BluetoothListLifecycleTest {
    @Test fun emptyResponseTimeoutDoesNotPollAndPauseReleasesTheReceiver() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        shadowOf(app.getSystemService(BluetoothManager::class.java).adapter).setEnabled(true)
        BTList::class.java.getDeclaredField("lastRequest").apply { isAccessible = true }.set(null, null)
        val oldPrefs = AppHelper.appPrefs
        AppHelper.appPrefs = app.getSharedPreferences("bt-lifecycle", Context.MODE_PRIVATE)
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        val page = BTList().apply { arguments = Bundle().apply {
            putString("key", "test_bt"); putInt("contentResId", R.id.prefs_system)
            putInt("settingsType", AppHelper.SettingsType.Edit.ordinal)
        } }
        fun requests() = shadowOf(app).broadcastIntents.count { it.action == GlobalActions.ACTION_PREFIX + "FetchCachedDevices" }
        val before = requests()
        try {
            controller.get().supportFragmentManager.beginTransaction().add(android.R.id.content, page).commitNow()
            assertEquals(before + 1, requests())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
            assertEquals(before + 1, requests())
            val loading = BTList::class.java.getDeclaredMethod("getLoading").apply { isAccessible = true }
            assertEquals(false, loading.invoke(page))
            controller.pause()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
            assertEquals(before + 1, requests())
            controller.resume()
            assertEquals(before + 2, requests())
            controller.pause().resume()
            assertEquals(before + 2, requests())
        } finally {
            controller.pause().stop().destroy()
            AppHelper.appPrefs = oldPrefs
        }
    }
}
