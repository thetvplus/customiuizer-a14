package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandStatusBarCompactTest {
    @Test fun frameChecksShareTheWindowOffsetAndStillFollowNativeIconTransforms() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val clock = View(activity); val battery = View(activity)
        class Icon : View(activity) {
            var coordinateReads = 0
            override fun getLocationOnScreen(out: IntArray) { coordinateReads++; super.getLocationOnScreen(out) }
            override fun getLocationInWindow(out: IntArray) { coordinateReads++; super.getLocationInWindow(out) }
        }
        val covered = Icon().apply { alpha = .6f }
        val clear = Icon().apply { alpha = .8f }
        val icons = FrameLayout(activity)
        root.addView(clock); root.addView(battery); root.addView(icons)
        icons.addView(covered); icons.addView(clear)
        activity.setContentView(root)
        root.layout(0, 0, 400, 50); icons.layout(0, 0, 400, 50)
        clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50)
        covered.layout(140, 0, 160, 50); clear.layout(300, 0, 320, 50)
        val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
        try {
            compact.show(180)
            covered.coordinateReads = 0; clear.coordinateReads = 0
            repeat(120) { root.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals("Per-icon screen/window queries duplicate the window's common offset", 0,
                covered.coordinateReads + clear.coordinateReads)
            assertEquals(0f, covered.alpha); assertEquals(.8f, clear.alpha)
            // RenderNode translation does not require a layout callback. Keep checking it each frame.
            icons.translationX = 160f
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(.6f, covered.alpha)
            icons.translationX = 0f
            root.translationY = 100f
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(.6f, covered.alpha)
            root.translationY = 0f
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(0f, covered.alpha)
        } finally { compact.restore(); activity.finish() }
    }

    @Test fun suppressedEssentialsRestoreTheLatestNativeAlphaAndAvoidUnchangedWrites() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val clock = object : View(activity) {
            var writes = 0
            override fun setAlpha(value: Float) { writes++; super.setAlpha(value) }
        }.apply { alpha = .7f }
        val battery = View(activity)
        root.addView(clock); root.addView(battery); activity.setContentView(root)
        root.layout(0, 0, 400, 50); clock.layout(8, 0, 60, 50); battery.layout(340, 0, 392, 50)
        val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
        try {
            val origin = IntArray(2); root.getLocationOnScreen(origin)
            compact.show(Rect(origin[0] + 20, origin[1], origin[0] + 380, origin[1] + 50))
            val writes = clock.writes
            repeat(120) { root.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals("Stable suppression needs no alpha resubmission", writes, clock.writes)
            clock.alpha = .4f // Native animation changes its target while the island owns suppression.
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(0f, clock.alpha)
            compact.restore()
            assertEquals("Teardown must restore the latest native value", .4f, clock.alpha)
        } finally { compact.restore(); activity.finish() }
    }

    @Test fun shortIslandKeepsNativeEssentialsClearAndRestoresEveryOwnedProperty() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity).apply { setPadding(12, 0, 12, 0) }
        activity.setContentView(root)
        val left = FrameLayout(activity)
        val right = FrameLayout(activity)
        val clock = View(activity)
        val battery = View(activity)
        val notification = View(activity).apply { alpha = .65f }
        val signal = View(activity).apply { alpha = .8f }
        root.addView(left); root.addView(right)
        left.addView(clock); left.addView(notification)
        right.addView(battery); right.addView(signal)
        root.layout(0, 0, 400, 50)
        left.layout(0, 0, 200, 50); right.layout(200, 0, 400, 50)
        clock.layout(110, 0, 180, 50); battery.layout(0, 0, 50, 50)
        notification.layout(120, 0, 145, 50); signal.layout(100, 0, 130, 50)
        val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
        compact.show(180)
        assertEquals(1f, root.alpha)
        assertEquals(1f, clock.alpha); assertEquals(1f, battery.alpha)
        assertEquals(0f, notification.alpha); assertEquals(.8f, signal.alpha)
        assertTrue(clock.right + clock.translationX <= 110 - 8)
        assertTrue(200 + battery.left + battery.translationX >= 290 + 8)
        val translation = clock.translationX
        repeat(20) { root.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(translation, clock.translationX)
        compact.restore()
        assertEquals(.65f, notification.alpha); assertEquals(.8f, signal.alpha)
        assertEquals(0f, clock.translationX); assertEquals(0f, battery.translationX)
        assertTrue(root.clipChildren); assertTrue(left.clipChildren)
        activity.finish()
    }

    @Test fun movingBetweenSafeEdgesHidesOnlyPhysicalIntersectionsAndVerticalAvoidanceRestoresThem() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val root = FrameLayout(activity)
            val clock = View(activity); val battery = View(activity)
            val leftIcon = View(activity).apply { alpha = .7f }
            val rightIcon = View(activity).apply { alpha = .8f }
            listOf(clock, battery, leftIcon, rightIcon).forEach(root::addView)
            activity.setContentView(root)
            root.layout(0, 0, 400, 50)
            clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50)
            leftIcon.layout(90, 0, 110, 50); rightIcon.layout(285, 0, 315, 50)
            val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
            val origin = IntArray(2); root.getLocationOnScreen(origin)
            fun bounds(left: Int, top: Int, right: Int, bottom: Int) =
                Rect(left + origin[0], top + origin[1], right + origin[0], bottom + origin[1])
            compact.show(bounds(0, 0, 160, 40))
            assertEquals(0f, clock.alpha); assertEquals(0f, leftIcon.alpha)
            assertEquals(1f, battery.alpha); assertEquals(.8f, rightIcon.alpha)
            compact.show(bounds(240, 0, 400, 40))
            assertEquals(1f, clock.alpha); assertEquals(.7f, leftIcon.alpha)
            assertEquals(0f, battery.alpha); assertEquals(0f, rightIcon.alpha)
            compact.show(bounds(240, 60, 400, 100))
            assertEquals(1f, battery.alpha); assertEquals(.8f, rightIcon.alpha)
            compact.restore()
        } finally { activity.finish() }
    }

    @Test fun contentReplacementAndLateBranchesDoNotLeaveHiddenIconsOrDetachedListeners() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val clock = View(activity)
        val battery = View(activity)
        root.addView(clock); root.addView(battery)
        activity.setContentView(root)
        root.layout(0, 0, 400, 50); clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50)
        val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
        compact.show(180)
        val late = View(activity).apply { alpha = .4f }
        root.addView(late)
        late.layout(150, 0, 175, 50)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(0f, late.alpha)
        compact.restore()
        late.alpha = .7f
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(.7f, late.alpha)
        activity.finish()
    }

    @Test fun alphaFallbackAnimatesBothWaysAndQuickSuccessorCannotBeRestoredByStaleCompletion() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val clock = View(activity)
        val battery = View(activity)
        val signal = View(activity).apply { alpha = .8f }
        root.addView(clock); root.addView(battery); root.addView(signal)
        activity.setContentView(root)
        root.layout(0, 0, 400, 50); clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50)
        signal.layout(180, 0, 210, 50)
        val compact = IslandStatusBarCompact(root, clock, battery,
            animationsEnabled = { true }, blurEnabled = { false })
        assertTrue(root.isAttachedToWindow); assertTrue(root.isShown)
        ShadowChoreographer.setPaused(true)
        try {
            compact.show(180)
            val hide: ValueAnimator = ReflectionHelpers.getField(compact, "motion")
            assertEquals(320L, hide.duration)
            hide.currentPlayTime = 80L
            assertTrue(signal.alpha > 0f && signal.alpha < .8f)
            assertEquals(1f, clock.alpha); assertEquals(1f, battery.alpha)
            // Width updates do not cancel or restart an in-flight hide.
            compact.show(190)
            assertSame(hide, ReflectionHelpers.getField(compact, "motion"))
            hide.end()
            assertEquals(0f, signal.alpha)
            compact.restore(animate = true)
            val reveal: ValueAnimator = ReflectionHelpers.getField(compact, "motion")
            assertEquals(400L, reveal.duration)
            reveal.currentPlayTime = 80L
            assertTrue(signal.alpha > 0f && signal.alpha < .8f)
            compact.show(180)
            val successor: ValueAnimator = ReflectionHelpers.getField(compact, "motion")
            successor.end()
            reveal.end() // A stale completion cannot restore icons under the successor.
            assertEquals(0f, signal.alpha)
            compact.restore(animate = true)
            ReflectionHelpers.getField<ValueAnimator>(compact, "motion").end()
            assertEquals(.8f, signal.alpha)
            signal.alpha = .5f
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(.5f, signal.alpha)
        } finally {
            compact.restore()
            ShadowChoreographer.setPaused(false)
            activity.finish()
        }
    }

    @Test fun immediateTeardownStopsRevealAndKeepsLatestNativeAlpha() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val clock = View(activity)
        val battery = View(activity)
        val signal = View(activity).apply { alpha = .8f }
        root.addView(clock); root.addView(battery); root.addView(signal)
        activity.setContentView(root)
        root.layout(0, 0, 400, 50); clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50)
        signal.layout(180, 0, 210, 50)
        val compact = IslandStatusBarCompact(root, clock, battery,
            animationsEnabled = { true }, blurEnabled = { false })
        compact.show(180)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        signal.alpha = .4f // A native controller updates this branch while hidden.
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(0f, signal.alpha)
        compact.restore(animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        compact.restore()
        assertEquals(.4f, signal.alpha)
        signal.alpha = .7f
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(.7f, signal.alpha)
        assertTrue(root.clipChildren)
        activity.finish()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun ownedBlurSnapshotIsBoundedAndReleasedWithoutOverwritingNativeEffects() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = object : FrameLayout(activity) {
            override fun isHardwareAccelerated() = true
        }
        val clock = View(activity)
        val battery = View(activity)
        val signal = object : View(activity) {
            var assigned: RenderEffect? = null
            override fun setRenderEffect(effect: RenderEffect?) { assigned = effect; super.setRenderEffect(effect) }
        }.apply { alpha = .8f }
        val nativeEffect = RenderEffect.createBlurEffect(1f, 1f, Shader.TileMode.CLAMP)
        signal.setRenderEffect(nativeEffect)
        root.addView(clock); root.addView(battery); root.addView(signal)
        activity.setContentView(root)
        root.layout(0, 0, 400, 50)
        clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50); signal.layout(300, 0, 330, 50)
        signal.layout(180, 0, 210, 50)
        val compact = IslandStatusBarCompact(root, clock, battery,
            animationsEnabled = { true }, blurEnabled = { true })
        assertTrue(root.isAttachedToWindow); assertTrue(root.isShown)
        compact.show(180)
        val hiding: View = ReflectionHelpers.getField(compact, "snapshot")
        val hidePixels: Bitmap = ReflectionHelpers.getField(hiding, "pixels")
        assertEquals(400 * 50 * 4, hidePixels.allocationByteCount)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertNull(ReflectionHelpers.getField<View?>(compact, "snapshot"))
        assertTrue(hidePixels.isRecycled)
        compact.restore(animate = true)
        val revealing: View = ReflectionHelpers.getField(compact, "snapshot")
        val revealPixels: Bitmap = ReflectionHelpers.getField(revealing, "pixels")
        compact.restore() // Root/mode replacement must not leave GPU decoration behind.
        assertTrue(revealPixels.isRecycled)
        assertNull(ReflectionHelpers.getField<View?>(compact, "snapshot"))
        assertSame(nativeEffect, signal.assigned)
        assertEquals(.8f, signal.alpha)

        root.layout(0, 0, 5000, 100) // Over 1 MiB: use the alpha-only path.
        signal.layout(180, 0, 210, 50)
        val origin = IntArray(2)
        root.getLocationOnScreen(origin)
        compact.show(Rect(110 + origin[0], origin[1], 290 + origin[0], 50 + origin[1]))
        assertNull(ReflectionHelpers.getField<View?>(compact, "snapshot"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(0f, signal.alpha)
        compact.restore()
        assertSame(nativeEffect, signal.assigned)
        assertEquals(.8f, signal.alpha)
        activity.finish()
    }
    @Test fun nestedIconsOutsideTheActualIslandRemainUntouchedDuringMorphAndVerticalSeparation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val icons = FrameLayout(activity).apply { alpha = .9f }
        val clock = View(activity)
        val battery = View(activity)
        val covered = View(activity).apply { alpha = .6f }
        val clear = View(activity).apply { alpha = .8f }
        activity.setContentView(root)
        root.addView(clock); root.addView(battery); root.addView(icons)
        icons.addView(covered); icons.addView(clear)
        root.layout(0, 0, 400, 50); icons.layout(0, 0, 400, 50)
        clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50)
        covered.layout(120, 0, 145, 50); clear.layout(300, 0, 330, 50)
        val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
        val origin = IntArray(2)
        root.getLocationOnScreen(origin)
        fun bounds(left: Int, top: Int, right: Int, bottom: Int) =
            Rect(left + origin[0], top + origin[1], right + origin[0], bottom + origin[1])
        compact.show(bounds(110, 0, 290, 50))
        assertEquals(0f, covered.alpha); assertEquals(.8f, clear.alpha); assertEquals(.9f, icons.alpha)
        assertEquals(0f, clock.translationX); assertEquals(0f, battery.translationX)
        assertTrue(root.clipChildren); assertTrue(icons.clipChildren)
        compact.show(bounds(160, 0, 240, 50))
        assertEquals(.6f, covered.alpha); assertEquals(.8f, clear.alpha)
        compact.show(bounds(110, 80, 290, 120))
        assertEquals(.6f, covered.alpha); assertEquals(.8f, clear.alpha)
        compact.restore()
        activity.finish()
    }

    @Test fun aWideManualIslandLetsTheClockAndBatteryYieldAndRestoresThemOnShrink() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val clock = View(activity).apply { alpha = .7f }
        val battery = View(activity).apply { alpha = .8f }
        root.addView(clock); root.addView(battery); activity.setContentView(root)
        root.layout(0, 0, 400, 50); clock.layout(8, 0, 60, 50); battery.layout(340, 0, 392, 50)
        val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
        val origin = IntArray(2); root.getLocationOnScreen(origin)
        compact.show(Rect(origin[0] + 20, origin[1], origin[0] + 380, origin[1] + 50))
        assertEquals(0f, clock.alpha); assertEquals(0f, battery.alpha)
        compact.show(Rect(origin[0] + 110, origin[1], origin[0] + 290, origin[1] + 50))
        assertEquals(.7f, clock.alpha); assertEquals(.8f, battery.alpha)
        compact.restore(); activity.finish()
    }

    @Test fun aSmallSafetyMarginHidesNearEdgeIconsWithoutChangingTheIslandOrClearIcons() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val clock = View(activity); val battery = View(activity)
        val near = View(activity).apply { alpha = .6f }
        val far = View(activity).apply { alpha = .8f }
        root.addView(clock); root.addView(battery); root.addView(near); root.addView(far)
        activity.setContentView(root)
        root.layout(0, 0, 400, 50); clock.layout(10, 0, 60, 50); battery.layout(350, 0, 390, 50)
        val padding = kotlin.math.ceil(4f * root.resources.displayMetrics.density).toInt()
        near.layout(200 + padding / 2, 0, 220 + padding / 2, 50)
        far.layout(225 + padding, 0, 245 + padding, 50)
        val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
        val origin = IntArray(2); root.getLocationOnScreen(origin)
        val bounds = Rect(origin[0] + 100, origin[1], origin[0] + 200, origin[1] + 50)
        compact.show(bounds)
        assertEquals(0f, near.alpha)
        assertEquals(.8f, far.alpha)
        assertEquals(Rect(origin[0] + 100, origin[1], origin[0] + 200, origin[1] + 50), bounds)
        compact.restore()
        assertEquals(.6f, near.alpha)
        activity.finish()
    }

    @Test fun longRunningMusicPrunesReplacedIconsBeforeApplyingTheActiveEntryBound() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        val clock = View(activity)
        val battery = View(activity)
        root.addView(clock); root.addView(battery)
        activity.setContentView(root)
        root.layout(0, 0, 400, 50); clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50)
        val compact = IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false })
        compact.show(180)
        var previous: View? = null
        repeat(150) {
            previous?.let { old -> root.removeView(old) }
            val current = View(activity).apply { alpha = .7f }
            root.addView(current); current.layout(150, 0, 180, 50)
            root.viewTreeObserver.dispatchOnGlobalLayout()
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(0f, current.alpha)
            previous?.let { old -> assertEquals(.7f, old.alpha) }
            assertTrue(ReflectionHelpers.getField<List<*>>(compact, "hidden").size <= 128)
            previous = current
        }
        compact.restore()
        assertEquals(.7f, previous!!.alpha)
        activity.finish()
    }

}
