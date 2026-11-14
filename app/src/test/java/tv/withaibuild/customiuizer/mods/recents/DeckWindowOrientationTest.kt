package tv.withaibuild.customiuizer.mods.recents

import android.app.Activity
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.content.res.Configuration
import android.view.Surface
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "port")
class DeckWindowOrientationTest {
    @Test fun rapidReentryUsesTheNativeDisplayLockAndRestoresNativePolicy() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val nav = View(activity)
        val helper = Any()
        val calls = mutableListOf<Int>()
        lateinit var state: DeckWindowOrientation
        state = DeckWindowOrientation { calls.add(state.orientation(it, 3, SCREEN_ORIENTATION_PORTRAIT)) }

        state.begin(activity, nav, helper, Surface.ROTATION_90)
        assertEquals(SCREEN_ORIENTATION_PORTRAIT, state.orientation(Any(), 3, SCREEN_ORIENTATION_PORTRAIT))
        assertEquals(SCREEN_ORIENTATION_UNSPECIFIED, state.orientation(helper, 1, SCREEN_ORIENTATION_UNSPECIFIED))
        assertEquals(SCREEN_ORIENTATION_UNSPECIFIED, state.orientation(helper, 3, SCREEN_ORIENTATION_UNSPECIFIED))
        state.finishGesture(nav, inOverview = false)
        state.begin(activity, nav, helper, Surface.ROTATION_0)
        state.begin(activity, nav, helper, Surface.ROTATION_270)
        state.begin(activity, nav, helper, Surface.ROTATION_270)
        state.finishGesture(nav, inOverview = true)
        assertEquals(listOf(SCREEN_ORIENTATION_LOCKED, SCREEN_ORIENTATION_PORTRAIT, SCREEN_ORIENTATION_LOCKED), calls)
        state.exit(activity)
        assertEquals(SCREEN_ORIENTATION_PORTRAIT, calls.last())
    }

    @Test fun landscapeHandoffUsesCurrentConfigurationAndMatchingContainer() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val nav = View(activity)
        val helper = Any()
        val container = Any()
        val state = DeckWindowOrientation {}
        state.begin(activity, nav, helper, Surface.ROTATION_270)
        state.finishGesture(View(activity), inOverview = false)
        state.finishGesture(nav, inOverview = true)
        assertFalse(state.landscapeWindow)
        assertFalse(state.bindLandscapeContainer(activity, Configuration.ORIENTATION_PORTRAIT, container))
        RuntimeEnvironment.setQualifiers("land")
        assertTrue(state.landscapeWindow)
        assertTrue(state.bindLandscapeContainer(activity, Configuration.ORIENTATION_LANDSCAPE, container))
        assertTrue(state.ownsLandscapeContainer(container))
        assertFalse(state.ownsLandscapeContainer(Any()))
        state.exit(activity)
        assertFalse(state.ownsLandscapeContainer(container))
    }
}
