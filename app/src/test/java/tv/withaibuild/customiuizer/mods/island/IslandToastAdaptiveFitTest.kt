package tv.withaibuild.customiuizer.mods.island

import android.content.res.Configuration
import android.media.session.PlaybackState
import android.util.TypedValue
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers.getField

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandToastAdaptiveFitTest {
    @Test fun twoLineFeedbackUsesTheSameReadableFontForAdmissionAndRenderingAtAutomaticHeight() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = 1.2f })
        val shell = IslandActivityView(context)
        shell.contentHeight(37); shell.geometry(308, 37, 40)
        val value = "A14 acceptance feedback"
        assertTrue(shell.fitsToast(value, 308, 40, 37, expanded = true))
        shell.bind(value, null, null, false, false, false, 0, false,
            playbackState = PlaybackState.STATE_NONE, expanded = true, photo = false)
        val title = getField<TextView>(shell, "title")
        assertTrue(title.textSize >= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics))
        assertEquals(2, title.maxLines)
        assertTrue(title.paint.fontSpacing * 2 <= 33)
        assertFalse(shell.fitsToast("W".repeat(400), 308, 40, 37, expanded = true))
    }
}
