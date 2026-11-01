package tv.withaibuild.customiuizer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tv.withaibuild.customiuizer.subs.System_WifiAutomatic
import tv.withaibuild.customiuizer.utils.AppHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PermissionContinuationTest {
    class HostActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.AppTheme)
            super.onCreate(savedInstanceState)
        }
    }
    @Test fun currentNetworkPermissionContinuesOnceAfterActivityRecreation() {
        val app = RuntimeEnvironment.getApplication()
        val previous = AppHelper.appPrefs
        AppHelper.appPrefs = app.getSharedPreferences("permission-continuation", 0)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
        val controller = Robolectric.buildActivity(HostActivity::class.java)
        controller.setup()
        try {
            val page = System_WifiAutomatic().apply { arguments = Bundle().apply {
                putInt("contentResId", R.id.prefs_system_wifiauto)
                putInt("settingsType", AppHelper.SettingsType.Preference.ordinal)
            } }
            controller.get().supportFragmentManager.beginTransaction().add(android.R.id.content, page).commitNow()
            page.findPreference<Preference>("pref_key_system_wifiauto_current")!!.performClick()
            assertNull(ShadowDialog.getLatestDialog())
            controller.recreate()
            val restored = controller.get().supportFragmentManager.fragments.filterIsInstance<System_WifiAutomatic>().single()
            shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
            // With no connected test network, continuing the action must show its
            // normal unavailable feedback, rather than silently losing the request.
            restored.onRequestPermissionsResult(771, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES), intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED))
            val feedback = ShadowDialog.getLatestDialog()
            assertNotNull("Restored permission must continue the original action", feedback)
            assertTrue(feedback.isShowing)
            feedback.dismiss()
            restored.onRequestPermissionsResult(771, emptyArray(), intArrayOf())
            assertFalse("The completed action cannot run again", feedback.isShowing)
            assertFalse(AppHelper.appPrefs!!.contains("pref_key_system_wifiauto_rules"))
        } finally {
            controller.pause().stop().destroy()
            AppHelper.appPrefs = previous
        }
    }
}
