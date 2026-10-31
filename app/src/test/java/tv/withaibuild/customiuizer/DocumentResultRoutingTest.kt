package tv.withaibuild.customiuizer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class RestoreResultPage : PreferenceFragmentBase() {
    var restored: Uri? = null
    var viewCreations = 0
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): android.view.View {
        viewCreations++
        return super.onCreateView(inflater, container, state)
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }
    override fun doRestoreSettings(uri: Uri?) { restored = uri }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DocumentResultRoutingTest {
    @Test fun documentResultKeepsUriAndCancelDoesNotRestore() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        val activity = controller.get()
        val page = RestoreResultPage()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, page).commitNow()
        val initialView = page.requireView()
        page.reloadPreferences()
        assertNotSame(initialView, page.requireView())
        assertEquals(2, page.viewCreations)
        val uri = Uri.parse("content://example.test/backup")
        for (code in listOf(Activity.RESULT_CANCELED, Activity.RESULT_OK)) {
            page.restoreSettings(activity)
            val request = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
            assertEquals("application/octet-stream", request.intent.type)
            assertTrue(request.intent.hasCategory(Intent.CATEGORY_OPENABLE))
            activity.activityResultRegistry.dispatchResult(request.requestCode, code, Intent().setData(uri))
            if (code == Activity.RESULT_OK) assertEquals(uri, page.restored) else assertNull(page.restored)
        }
        page.backupSettings(activity)
        val export = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, export.intent.action)
        assertEquals("application/octet-stream", export.intent.type)
        assertTrue(export.intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertFalse(export.intent.getStringExtra(Intent.EXTRA_TITLE).isNullOrBlank())
        activity.activityResultRegistry.dispatchResult(export.requestCode, Activity.RESULT_CANCELED, null)
        controller.pause().stop().destroy()
    }
}
