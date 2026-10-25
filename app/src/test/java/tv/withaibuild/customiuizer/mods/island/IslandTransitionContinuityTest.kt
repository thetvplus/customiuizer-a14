package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField
import org.robolectric.util.ReflectionHelpers.setField

/** Numerical state/transform checks only: no rendering, screenshots or device claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandTransitionContinuityTest {
    @Test fun albumHandoffCompletesOpacityAndRotationBeforeItsFiniteClockEnds() {
        label("Track A", true)
        shell.transition(184, 40, 184, 40, false)
        motion().end()
        for (scale in listOf(1f, .65f)) {
            shell.motionScale = scale
            label("Track $scale", true)
            shell.transition(184, 40, 184, 40, false)
            val clock = motion()
            assertTrue(clock.duration >= (IslandMotion.ALBUM * scale).toLong())
            clock.currentPlayTime = clock.duration - 1
            assertTrue(shell.getChildAt(0).alpha > .98f)
            assertEquals(0f, getField<android.widget.ImageView>(shell, "icon").rotationY, 1f)
            clock.end()
        }
    }
    @Test fun similarlySizedActivitiesExchangeContentWithoutAnyElasticSizeChange() {
        label("Copy")
        shell.transition(184, 40, 184, 40, false, secondary = true)
        val clock = motion()
        assertEquals(IslandMotion.CONTENT, clock.duration)
        for (time in 0..220 step 8) {
            clock.currentPlayTime = time.toLong()
            assertEquals(184f, shell.width * shell.scaleX, .001f)
            assertEquals(0f, shell.translationX, .001f)
        }
        clock.end()
    }
    private lateinit var activity: Activity
    private lateinit var shell: IslandActivityView
    private val title get() = getField<TextView>(shell, "title")
    private fun motion() = getField<ValueAnimator>(shell, "motion")
    private fun label(value: String, media: Boolean = false) = shell.bind(value, null,
        activity.getDrawable(android.R.drawable.ic_dialog_info), media, media, media, 0, false)

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(184, 40))
        label("Old")
        layout(184, 40)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
    }

    @After fun cleanup() {
        ShadowChoreographer.setPaused(false)
        activity.finish()
    }

    private fun layout(width: Int, height: Int, alignment: Int = 1, maximum: Int = 400) {
        shell.geometry(width, height, 0, maximum, alignment)
        shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        shell.layout(0, 0, width, height)
        shell.cameraAnchor(height / 2f, 16f)
    }

    @Test fun shortToLongFadesTheOutgoingContentBeforeCommittingTheLatestLabel() {
        label("New")
        layout(280, 40)
        shell.transition(184, 40, 280, 40, false, secondary = true)
        val clock = motion()
        assertEquals("Old", title.text.toString())
        clock.currentPlayTime = 32
        assertEquals("Old", title.text.toString())
        assertTrue(shell.getChildAt(0).alpha in .1f.. .9f)
        assertTrue(shell.width * shell.scaleX in 184f..280f)
        clock.currentPlayTime = 60
        assertEquals("New", title.text.toString())
        assertEquals(0f, shell.getChildAt(0).alpha, .001f)
        clock.currentPlayTime = 200
        assertTrue(shell.getChildAt(0).alpha > .5f)
        clock.end()
        assertEquals(280f, shell.width * shell.scaleX, .001f)
        assertEquals(1f, shell.getChildAt(0).alpha, .001f)
    }

    @Test fun aReplacementCarriesTheRemainingFadeAndOldClockCannotCommitStaleContent() {
        label("Superseded")
        shell.transition(184, 40, 184, 40, false, secondary = true)
        val old = motion()
        old.currentPlayTime = 32
        val opacity = shell.getChildAt(0).alpha
        label("Latest")
        shell.transition(184, 40, 184, 40, false, secondary = true)
        assertEquals(opacity, shell.getChildAt(0).alpha, .001f)
        val current = motion()
        old.end()
        assertEquals("Old", title.text.toString())
        assertSame(current, motion())
        current.currentPlayTime = 32
        assertEquals("Latest", title.text.toString())
        current.end()
        assertNull(getField<Any?>(shell, "pendingContent"))
        assertEquals("Latest", title.text.toString())
    }

    @Test fun logicalMediaAndAccessibilityAreCurrentBeforeTheVisualSwap() {
        label("Current track", media = true)
        assertTrue(shell.isMedia)
        assertTrue(shell.contentDescription.toString().contains("Current track"))
        assertEquals("Old", title.text.toString())
        shell.transition(184, 40, 184, 40, false)
        motion().end()
        assertEquals("Hidden compact labels must not schedule text layout", "", title.text.toString())
        assertEquals(View.GONE, shell.getChildAt(1).visibility)
        assertTrue(shell.contentDescription.toString().contains("Current track"))
        shell.bind("Current track", null, activity.getDrawable(android.R.drawable.ic_dialog_info),
            true, true, true, 0, false, expanded = true)
        shell.expanded = true
        shell.transition(184, 40, 300, 40, false)
        motion().end()
        assertEquals("Current track", title.text.toString())
        assertEquals(View.VISIBLE, shell.getChildAt(1).visibility)
    }

    @Test fun cancellationDiscardsPendingContentAndAStaleCompletionCannotReapplyIt() {
        label("Cancelled")
        shell.transition(184, 40, 184, 40, false)
        val old = motion()
        old.currentPlayTime = 16
        var exits = 0
        shell.exit { exits++ }
        val exit = motion()
        assertNull(getField<Any?>(shell, "pendingContent"))
        old.end()
        assertSame(exit, motion())
        exit.end()
        assertEquals(1, exits)
        activity.setContentView(FrameLayout(activity))
        label("Fresh")
        assertEquals("Fresh", title.text.toString())
        assertNull(getField<Any?>(shell, "pendingContent"))
    }

    @Test fun unchangedStatusStillUsesFreshTextFitAfterWidthChanges() {
        val art = activity.getDrawable(android.R.drawable.ic_dialog_info)
        val text = "Power saving enabled"
        shell.contentHeight(40)
        shell.bind(text, null, art, false, false, false, 0, false, status = true)
        shell.transition(184, 40, 184, 40, false)
        motion().end()
        // Supply the fresh layout result directly: this checks binding invalidation,
        // without relying on legacy Host font measurement to simulate a real font.
        val fit = IslandStatusLayout.Fit(11f, 16)
        setField(shell, "statusFit", fit)
        assertFalse(shell.bind(text, null, art, false, false, false, 0, false, status = true))
        assertEquals(fit.size, title.textSize, .001f)
    }

    @Test fun secondaryIdentityWithoutAVisualChangeDoesNotBlankTheContent() {
        shell.transition(184, 40, 184, 40, false, secondary = true)
        motion().currentPlayTime = 32
        assertEquals("Old", title.text.toString())
        assertEquals(1f, shell.getChildAt(0).alpha, .001f)
        motion().end()
    }

    @Test fun simplifiedModeBindsImmediatelyWithoutAnAnimator() {
        label("During handoff")
        shell.transition(184, 40, 184, 40, false)
        motion().currentPlayTime = 60
        shell.simplified = true
        label("Immediate")
        shell.transition(184, 40, 184, 40, false, secondary = true)
        assertEquals("Immediate", title.text.toString())
        assertNull(getField<Any?>(shell, "motion"))
        assertEquals(1f, shell.alpha, .001f)
        assertEquals(1f, shell.getChildAt(1).alpha, .001f)
    }

    @Test fun sideLongToShortAndTallToShortDoNotClampToTheTargetOnTheFirstFrame() {
        layout(280, 64, 0, 280)
        layout(96, 32, 0, 96)
        shell.transition(280, 64, 96, 32, false)
        val clock = motion()
        clock.currentPlayTime = 0
        assertEquals(280f, shell.width * shell.scaleX, .001f)
        assertEquals(64f, shell.height * shell.scaleY, .001f)
        clock.currentPlayTime = 80
        assertTrue(shell.width * shell.scaleX > 96f)
        assertTrue(shell.height * shell.scaleY > 32f)
        clock.end()
        assertEquals(96f, shell.width * shell.scaleX, .001f)
        assertEquals(32f, shell.height * shell.scaleY, .001f)
    }

    @Test fun originAndPivotRetargetFromTheVisiblePositionWithoutASecondAnimator() {
        layout(184, 40, 0)
        layout(280, 40, 2)
        shell.transition(184, 40, 280, 40, false, visualLeft = -100f, visualTop = -20f)
        fun left(windowLeft: Float) = windowLeft + shell.pivotX * (1f - shell.scaleX) + shell.translationX
        assertEquals(0f, left(100f), .001f)
        val first = motion()
        first.currentPlayTime = 80
        val before = left(100f)
        val top = shell.pivotY * (1f - shell.scaleY) + shell.translationY
        val physical = shell.width * shell.scaleX
        layout(184, 40, 1)
        shell.transition(280, 40, 184, 40, false, visualLeft = before - 50f, visualTop = top)
        assertEquals(before, left(50f), .001f)
        assertEquals(physical, shell.width * shell.scaleX, .001f)
        val current = motion()
        first.end()
        assertSame(current, motion())
        current.end()
        assertEquals(50f, left(50f), .001f)
        assertEquals(0f, shell.translationY, .001f)
    }
}
