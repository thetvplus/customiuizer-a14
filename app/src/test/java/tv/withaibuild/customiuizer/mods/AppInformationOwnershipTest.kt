package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppInformationOwnershipTest {
    class Row(val key: String, val title: String, val value: String)
    class Fragment(private val host: Activity, pkg: String, uid: Int) {
        @JvmField var info = PackageInfo().apply {
            packageName = pkg; setLongVersionCode(5_000_000_001L)
            applicationInfo = ApplicationInfo().apply { packageName = pkg; this.uid = uid; sourceDir = "/$pkg/base.apk"; dataDir = "/$pkg/data" }
        }
        val arguments = Bundle().apply { putString("am_app_pkgname", pkg); putInt("am_app_uid", uid) }
        var screen = Any(); val rows = linkedMapOf<String, Row>()
        fun getActivity() = host
        fun getPreferenceScreen() = screen
        fun findPreference(key: CharSequence) = rows[key.toString()]
        fun addRow(key: String, title: String, value: String) { rows[key] = Row(key, title, value) }
        fun onCreatePreferences(state: Bundle?, key: String?) = Unit
        fun onPreferenceTreeClick(row: Row) = false
    }
    private fun access() = AppInformationHooks.Access(Fragment::class.java, Activity::class.java)

    @Test fun twoPagesCopyTheirOwnPackageAndRepeatedCreationDoesNotAppendDuplicates() {
        Robolectric.buildActivity(Activity::class.java).setup().use { host ->
            val a = Fragment(host.get(), "first.app", 10001); val b = Fragment(host.get(), "second.app", 10002)
            val access = access(); access.decorate(a); access.decorate(b); access.decorate(a)
            assertEquals(7, a.rows.size); assertEquals("5000000001", a.rows.getValue("apk_versioncode").value)
            assertTrue(access.click(a, a.rows.getValue("apk_filename")))
            assertEquals("/first.app/base.apk", host.get().getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
            assertFalse(access.click(a, b.rows.getValue("apk_filename")))
            assertFalse(access.click(a, Row("apk_filename", "forged", "")))
        }
    }

    @Test fun replacedScreensChangedProfilesAndDestroyedActivitiesInvalidateOldRows() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val fragment = Fragment(host.get(), "first.app", 10001); val access = access(); access.decorate(fragment)
            val old = fragment.rows.getValue("apk_filename")
            fragment.screen = Any(); fragment.rows.clear(); access.decorate(fragment)
            assertFalse(access.click(fragment, old))
            val current = fragment.rows.getValue("apk_filename")
            fragment.arguments.putInt("am_app_uid", 1010001)
            assertFalse(access.click(fragment, current))
            fragment.arguments.putInt("am_app_uid", 10001)
            host.destroy(); assertFalse(access.click(fragment, current))
        } finally { if (!host.get().isDestroyed) host.destroy() }
    }
}
