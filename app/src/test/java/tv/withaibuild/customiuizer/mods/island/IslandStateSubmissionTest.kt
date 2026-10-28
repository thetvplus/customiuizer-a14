package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.session.PlaybackState
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers.getField
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "en-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandStateSubmissionTest {
    @Test fun compactTrackUpdatesDoNotResubmitIdenticalHiddenTextButRealTextAndClockChangesStillApply() {
        val shell = IslandActivityView(RuntimeEnvironment.getApplication()).apply { simplified = true }
        shell.bind("Track", "Artist", null, true, true, false, 0, false)
        shell.geometry(280, 40, 40)
        val identity = (shell.getChildAt(0) as LinearLayout).getChildAt(1) as TextView
        val detail = (shell.getChildAt(1) as LinearLayout).getChildAt(1) as TextView
        var textSubmissions = 0
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { textSubmissions++ }
        }
        identity.addTextChangedListener(watcher)
        detail.addTextChangedListener(watcher)
        fun settle() {
            shell.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
            shell.layout(0, 0, 280, 40)
        }
        settle()
        repeat(120) {
            shell.bind("Track $it", "Artist", null, true, true, false, 0, false)
            settle()
        }
        assertEquals("Unchanged hidden text must not be submitted again", 0, textSubmissions)
        shell.bind("Track 119", "Next artist", null, true, true, false, 0, false)
        assertEquals("Next artist", detail.text.toString())
        assertEquals(1, textSubmissions)
        shell.bind("Track 119", "Next artist", null, true, true, false, 0, false, expanded = true)
        assertEquals("Track 119", identity.text.toString())
        assertEquals(View.VISIBLE, identity.visibility)
        assertEquals(2, textSubmissions)
        shell.bind("Recorder", "Paused", null, false, false, false, 0, false,
            expanded = true, clock = IslandLiveClock(0, false, pausedMillis = 62_000))
        val clock = (shell.getChildAt(1) as LinearLayout).getChildAt(0) as TextView
        assertEquals(IslandLiveLabels.time(62_000), clock.text.toString())
        assertEquals("", identity.text.toString())
        assertEquals("Paused", detail.text.toString())
    }

    @Test fun unchangedGeometryAndCompactMetadataDoNotInvalidateTheMaskButShapeAndSizeStillDo() {
        val shell = IslandActivityView(RuntimeEnvironment.getApplication()).apply { simplified = true }
        shell.bind("Track", "Artist", null, true, true, false, 0, false)
        shell.geometry(280, 40, 40)
        val mask = getField<GradientDrawable>(shell, "iconMask")
        val callback = mask.callback
        var invalidations = 0
        mask.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) { invalidations++; callback?.invalidateDrawable(who) }
            override fun scheduleDrawable(who: Drawable, what: Runnable, at: Long) { callback?.scheduleDrawable(who, what, at) }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) { callback?.unscheduleDrawable(who, what) }
        }
        repeat(120) { shell.geometry(280, 40, 40) }
        assertEquals("Settled geometry must not dirty the mask again", 0, invalidations)
        repeat(120) { shell.bind("Track $it", "Artist", null, true, true, false, 0, false) }
        assertEquals("Invisible compact metadata must not dirty the same mask", 0, invalidations)

        val radius = mask.cornerRadius
        shell.geometry(280, 64, 40)
        assertTrue(mask.cornerRadius > radius)
        assertTrue(invalidations > 0)
        invalidations = 0
        shell.bind("Sender", "Message", null, false, false, false, 0, false, avatar = true)
        assertEquals(GradientDrawable.OVAL, mask.shape)
        assertTrue(invalidations > 0)
        invalidations = 0
        shell.geometry(280, 64, 40)
        assertEquals(0, invalidations)
        shell.bind("Saved", null, null, false, false, false, 0, false)
        assertEquals(GradientDrawable.RECTANGLE, mask.shape)
        assertTrue(invalidations > 0)
    }

    @Test fun repeatedBindingReusesAccessibilityPreparationAndRealPlaybackAndPendingTextChangesStillSubmit() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val source = ModuleHelper.getModuleRes(activity)
        @Suppress("DEPRECATION")
        val resources = object : Resources(source.assets, source.displayMetrics, source.configuration) {
            var playbackReads = 0
            override fun getString(id: Int): String {
                if (id == R.string.system_island_playing || id == R.string.system_island_paused ||
                    id == R.string.system_island_connecting) playbackReads++
                return super.getString(id)
            }
        }
        val oldResources = ModuleHelper.cachedModuleRes
        val oldConfig = ModuleHelper.cachedModuleConfig
        ModuleHelper.cachedModuleRes = resources
        ModuleHelper.cachedModuleConfig = Configuration(activity.resources.configuration)
        try {
            val shell = IslandActivityView(activity).apply { simplified = true }
            activity.setContentView(shell, FrameLayout.LayoutParams(280, 40))
            fun bind(label: String = "Track", detail: String? = "Artist", state: Int = PlaybackState.STATE_PLAYING,
                media: Boolean = true) = shell.bind(label, detail, null, state == PlaybackState.STATE_PLAYING,
                media, false, 0, false, playbackState = state, expanded = true)
            bind()
            assertEquals("Track, Playing, Artist", shell.contentDescription.toString())
            resources.playbackReads = 0
            repeat(120) { assertFalse(bind()) }
            assertEquals("Unchanged binding must not prepare the same playback label", 0, resources.playbackReads)
            bind(state = PlaybackState.STATE_PAUSED)
            assertEquals("Track, Paused, Artist", shell.contentDescription.toString())
            assertEquals(1, resources.playbackReads)
            shell.simplified = false
            bind("Next", "Other")
            assertNotNull(getField<Any?>(shell, "pendingContent"))
            assertEquals("Next, Playing, Other", shell.contentDescription.toString())
            val reads = resources.playbackReads
            repeat(120) { assertFalse(bind("Next", "Other")) }
            assertEquals(reads, resources.playbackReads)
            bind("Next", "Next")
            assertEquals("Next, Playing", shell.contentDescription.toString())
            bind("Saved", " ", media = false)
            assertEquals("Saved", shell.contentDescription.toString())
        } finally {
            activity.finish()
            ModuleHelper.cachedModuleRes = oldResources
            ModuleHelper.cachedModuleConfig = oldConfig
        }
    }

    @Test fun configurationRefreshUpdatesTheCurrentAccessibilityLanguageWithoutANewContentEvent() {
        val app = RuntimeEnvironment.getApplication()
        val shell = IslandActivityView(app).apply { simplified = true }
        shell.bind("Track", "Artist", null, true, true, false, 0, false, playbackState = PlaybackState.STATE_PLAYING)
        assertEquals("Track, Playing, Artist", shell.contentDescription.toString())
        RuntimeEnvironment.setQualifiers("zh-rCN-mdpi")
        shell.refreshConfiguration()
        assertEquals("Track, 播放中, Artist", shell.contentDescription.toString())
        assertFalse(shell.bind("Track", "Artist", null, true, true, false, 0, false,
            playbackState = PlaybackState.STATE_PLAYING))
        assertEquals("Track, 播放中, Artist", shell.contentDescription.toString())
    }
}
