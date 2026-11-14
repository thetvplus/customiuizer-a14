package tv.withaibuild.customiuizer.mods.recents

import android.app.Activity
import android.content.pm.ActivityInfo
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "port")
class DeckWindowOrientationTest {
    @Test fun matchingGestureRequestsItsDirectionAndRestoresTheLatestNativeRequest() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val nav = View(activity)
        val calls = mutableListOf<Int>()
        lateinit var holder: DeckWindowOrientation
        val request: (Activity, Int) -> Unit = { launcher, orientation ->
            calls.add(holder.interceptRequest(launcher, orientation))
        }
        holder = DeckWindowOrientation(request, request)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        holder.capture(activity, nav, Surface.ROTATION_270, false)
        holder.commit(activity)
        assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE), calls)
        assertFalse(holder.landscapeWindow)
        assertTrue(holder.configuration(activity, Configuration.ORIENTATION_LANDSCAPE))
        val container = Any()
        holder.bindLandscapeContainer(activity, container)
        assertTrue(holder.ownsLandscapeContainer(container))
        assertFalse(holder.ownsLandscapeContainer(Any()))
        assertFalse(holder.configuration(activity, Configuration.ORIENTATION_LANDSCAPE))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            holder.interceptRequest(activity, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED))
        holder.exit(activity)
        assertFalse(holder.landscapeWindow)
        assertFalse(holder.ownsLandscapeContainer(container))
        assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED), calls)
    }

    @Test fun portraitAndCancelledOrUnrelatedGesturesLeaveTheWindowAlone() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val nav = View(activity)
        val calls = mutableListOf<Int>()
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val request: (Activity, Int) -> Unit = { _, orientation -> calls.add(orientation) }
        val holder = DeckWindowOrientation(request, request)
        holder.capture(activity, nav, Surface.ROTATION_90, false)
        val other = Robolectric.buildActivity(Activity::class.java).setup().get()
        holder.commit(other)
        holder.finishGesture(nav)
        holder.commit(activity)
        holder.capture(activity, nav, Surface.ROTATION_0, false)
        holder.commit(activity)
        assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT), calls)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            DeckWindowOrientation.orientationFor(Surface.ROTATION_90, true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            DeckWindowOrientation.orientationFor(Surface.ROTATION_270, true))
    }
}
