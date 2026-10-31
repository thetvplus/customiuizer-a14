package tv.withaibuild.customiuizer

import android.app.Application
import android.os.IBinder
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowInputMethodManager
import tv.withaibuild.customiuizer.utils.Helpers

@Implements(InputMethodManager::class)
class RecordingHideInputShadow : ShadowInputMethodManager() {
    var token: IBinder? = null
    var flags = -1
    @Implementation override fun hideSoftInputFromWindow(windowToken: IBinder, flags: Int): Boolean {
        token = windowToken; this.flags = flags; return true
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, shadows = [RecordingHideInputShadow::class])
class KeyboardHideTest {
    @Test fun attachedWindowHidesAndDetachedInputDoesNotSendAnInvalidToken() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup().visible()
        val activity = controller.get()
        val input = EditText(activity)
        activity.setContentView(input)
        input.requestFocus()
        val shadow = Shadow.extract<RecordingHideInputShadow>(activity.getSystemService(InputMethodManager::class.java))
        Helpers.hideKeyboard(activity, input)
        assertEquals(input.windowToken, shadow.token)
        assertEquals(0, shadow.flags)
        shadow.token = null; shadow.flags = -1
        Helpers.hideKeyboard(null, EditText(activity))
        Helpers.hideKeyboard(activity, null)
        assertNull(shadow.token)
        assertEquals(-1, shadow.flags)
        controller.pause().stop().destroy()
    }
}
