package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField
import java.io.File
import kotlin.math.roundToInt

/** Render the real capsule path and compensated children. Host Skia evidence, not a device FPS test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandMotionFramesTest {
    @Test fun expandedMetadataGlyphsStayInTheirOwnPhysicalLaneDuringTheWholeMorph() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(280, 40))
        ShadowChoreographer.setPaused(true)
        try {
            for (gap in listOf(0, 40)) for (artwork in listOf(false, true)) {
                shell.geometry(280, 40, gap, 288)
                shell.bind("Summer evening concert", "The Band", if (artwork) android.graphics.drawable.ColorDrawable(Color.CYAN) else null,
                    true, true, false, 0, false, expanded = true)
                shell.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
                shell.layout(0, 0, 280, 40)
                shell.prepareEntry(); shell.transition(160, 40, 280, 40, false)
                val clock = getField<ValueAnimator>(shell, "motion")
                val identity = getField<android.widget.TextView>(shell, "identityTitle")
                val title = getField<android.widget.TextView>(shell, "title")
                val leading = getField<View>(shell, "left"); val trailing = getField<View>(shell, "text")
                fun ink(view: android.widget.TextView): android.graphics.RectF {
                    val layout = requireNotNull(view.layout)
                    return android.graphics.RectF(layout.getLineLeft(0), 0f, layout.getLineRight(0), view.height.toFloat()).also {
                        val matrix = android.graphics.Matrix(); view.transformMatrixToGlobal(matrix); matrix.mapRect(it)
                    }
                }
                for (at in 0L..clock.duration step 8) {
                    clock.currentPlayTime = at
                    val center = floatArrayOf(140f, 20f)
                    val matrix = android.graphics.Matrix(); shell.transformMatrixToGlobal(matrix); matrix.mapPoints(center)
                    if (identity.alpha * leading.alpha > .01f)
                        assertTrue("Leading text crosses its lane at $at ms, gap=$gap artwork=$artwork",
                            ink(identity).right <= center[0] - gap * .5f + .5f)
                    if (trailing.alpha > .01f)
                        assertTrue("Right-aligned text crosses its lane at $at ms, gap=$gap artwork=$artwork",
                            ink(title).left >= center[0] + gap * .5f - .5f)
                }
                clock.end()
            }
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    @Test fun equalSizeContentArrivalExpandsOnceFromTheCurrentShellAndSettlesWithinTheSameClock() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(160, 40))
        shell.geometry(160, 40, 40, 168); shell.layout(0, 0, 160, 40)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
        try {
            shell.prepareContentArrivalFromCurrent()
            shell.transition(160, 40, 160, 40, false)
            val clock = getField<ValueAnimator>(shell, "motion")
            clock.currentPlayTime = 0
            assertEquals(160f, shell.width * shell.scaleX, .001f)
            clock.currentPlayTime = clock.duration / 2
            assertTrue("An already-present shell must acknowledge the arrival", shell.width * shell.scaleX > 163f)
            assertTrue(shell.width * shell.scaleX <= 168f)
            assertEquals(40f, shell.height * shell.scaleY, .001f)
            clock.end()
            assertEquals(1f, shell.scaleX, .001f)
            assertEquals(1f, shell.alpha, .001f)
            assertNull(getField<ValueAnimator?>(shell, "motion"))
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }
    @Test fun contractingTheShellKeepsTheWholeArtworkInsideItsPhysicalEdgeMargin() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(280, 40))
        shell.bind("Notice", null, android.graphics.drawable.ColorDrawable(Color.RED), false, false, false, 0, false)
        fun layout(width: Int) {
            shell.geometry(width, 40, 40, 300)
            shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
            shell.layout(0, 0, width, 40); shell.cameraAnchor(20f, 16f)
        }
        layout(280)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
        try {
            layout(184)
            shell.transition(280, 40, 184, 40, false)
            val clock = getField<ValueAnimator>(shell, "motion")
            for (time in listOf(0L, 16L, 48L, 96L, 160L)) {
                clock.currentPlayTime = time
                val bitmap = Bitmap.createBitmap(320, 40, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.translate(-shell.pivotX * (1f - shell.scaleX) - shell.translationX, 0f)
                    canvas.concat(shell.matrix)
                    shell.draw(canvas)
                    val redPixels = (0 until bitmap.width).filter { x ->
                        val pixel = bitmap.getPixel(x, 20)
                        Color.red(pixel) > 240 && Color.green(pixel) < 16 && Color.alpha(pixel) > 240
                    }
                    assertEquals("The full 24 px cover must survive contraction at $time ms", 24, redPixels.size)
                    assertEquals(16, redPixels.first())
                } finally { bitmap.recycle() }
            }
            clock.end()
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    @Test fun sixtyHzSequenceAndMidFlightReversal() = frames(60)
    @Test fun oneHundredTwentyHzSequenceAndMidFlightReversal() = frames(120)
    @Test fun earlyReversalBrakesWithinTheActualSafeWidthInsteadOfStopping() = reverseAt(32L)
    @Test fun laterReversalRetainsPositiveVelocityUntilItHasBraked() = reverseAt(80L)

    @Test fun secondaryActivityUsesASlowerLayeredHandoffAndReturnsWithTheSameHeight() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(184, 40))
        fun layout(width: Int) {
            shell.geometry(width, 40, 40, 280)
            shell.layout(0, 0, width, 40); shell.cameraAnchor(20f, 16f)
        }
        layout(184)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
        try {
            shell.bind("Copied", null, activity.getDrawable(android.R.drawable.ic_dialog_info),
                false, false, false, 0, false)
            layout(240); shell.transition(184, 40, 240, 40, false, secondary = true)
            val entrance = getField<ValueAnimator>(shell, "motion")
            assertTrue(entrance.duration in (IslandMotion.CONTENT + 1)..IslandMotion.SECONDARY)
            assertTrue(entrance.duration > IslandMotion.morphDuration(184f, 40f, 240f, 40f, false))
            entrance.currentPlayTime = 32
            assertEquals(1f, shell.getChildAt(0).alpha, .001f)
            entrance.currentPlayTime = 200
            assertTrue(shell.getChildAt(0).alpha > .5f)
            entrance.end()
            shell.bind("Music", null, activity.getDrawable(android.R.drawable.ic_media_play),
                true, true, true, 0, false)
            layout(184); shell.transition(240, 40, 184, 40, false, secondary = true)
            val back = getField<ValueAnimator>(shell, "motion")
            assertEquals(entrance.duration, back.duration)
            for (time in 0L..back.duration step 8L) {
                back.currentPlayTime = time
                assertEquals(40f, shell.height * shell.scaleY, .001f)
                assertEquals(0f, shell.translationY, 0f)
            }
            back.end()
            assertEquals(184f, shell.width * shell.scaleX, .001f)
            assertEquals(0f, shell.translationY, .001f)
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    @Test fun interruptedSecondaryExitCannotKeepTheNextActivityContentInvisible() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity).apply { entranceStyle = IslandEntranceStyle.CENTER }
        activity.setContentView(shell, FrameLayout.LayoutParams(240, 40))
        shell.geometry(240, 40, 40, 280); shell.cameraAnchor(14f, 16f)
        shell.layout(0, 0, 240, 40)
        shell.bind("Copied", null, activity.getDrawable(android.R.drawable.ic_dialog_info), false, false, false, 0, false)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
        try {
            shell.transition(240, 40, 240, 40, false, secondary = true)
            getField<ValueAnimator>(shell, "motion").currentPlayTime = 32
            shell.exit {}
            getField<ValueAnimator>(shell, "motion").currentPlayTime = 16
            shell.resumeEntry()
            shell.transition(240, 40, 240, 40, false)
            val resumed = getField<ValueAnimator>(shell, "motion")
            resumed.currentPlayTime = 80
            assertTrue(shell.getChildAt(0).alpha > .1f)
            assertEquals(0f, shell.translationY, 0f)
            assertEquals(40f, shell.height * shell.scaleY, .001f)
            resumed.end()
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    @Test fun expandedArrivalsUseOneFlightToTheFinalFormWithoutAShortFormWaypoint() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
        try {
            for (style in IslandEntranceStyle.entries) {
                val shell = IslandActivityView(activity).apply {
                    entranceStyle = style; windowTop = 8
                }
                activity.setContentView(shell, FrameLayout.LayoutParams(300, 40))
                shell.geometry(300, 40, 40, 300); shell.layout(0, 0, 300, 40); shell.cameraAnchor(20f, 16f)
                shell.bind("Alice", "Message body", null, false, false, false, 0, false, expanded = true)
                shell.prepareEntry()
                assertEquals(if (style == IslandEntranceStyle.TOP) 300f else 40f,
                    shell.width * shell.scaleX, .001f)
                shell.transition(300, 40, 300, 40, true)
                val flight = getField<ValueAnimator>(shell, "motion")
                assertEquals(IslandMotion.ENTER, flight.duration)
                for (time in 0L until flight.duration step 16L) {
                    flight.currentPlayTime = time
                    assertSame(flight, getField<ValueAnimator>(shell, "motion"))
                    if (style == IslandEntranceStyle.TOP) assertEquals(300f, shell.width * shell.scaleX, .001f)
                    assertEquals(40f, shell.height * shell.scaleY, .001f)
                }
                flight.end()
                assertEquals(300f, shell.width * shell.scaleX, .001f)
                assertEquals(1f, shell.alpha, .001f)
            }
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    @Test fun verticalStyleEntersAndExitsVerticallyWithoutCameraWidthCollapse() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity).apply { entranceStyle = IslandEntranceStyle.TOP; windowTop = 8 }
        activity.setContentView(shell, FrameLayout.LayoutParams(240, 40))
        shell.geometry(240, 40, 40, 280); shell.layout(0, 0, 240, 40); shell.cameraAnchor(20f, 16f)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
        try {
            shell.prepareEntry()
            assertEquals(-56f, shell.translationY, .001f)
            shell.transition(240, 40, 240, 40, true)
            val entrance = getField<ValueAnimator>(shell, "motion")
            for (time in 0L until entrance.duration step 16L) {
                entrance.currentPlayTime = time
                assertEquals(240f, shell.width * shell.scaleX, .001f)
                assertEquals(40f, shell.height * shell.scaleY, .001f)
                assertTrue(shell.translationY in -56f..0f)
            }
            entrance.end()
            var completed = 0
            shell.exit { completed++ }
            val exit = getField<ValueAnimator>(shell, "motion")
            for (time in 0L until exit.duration step 16L) {
                exit.currentPlayTime = time
                assertEquals(240f, shell.width * shell.scaleX, .001f)
                assertEquals(40f, shell.height * shell.scaleY, .001f)
                assertTrue(shell.translationY in -56f..0f)
            }
            assertTrue(shell.translationY < -40f)
            exit.end()
            assertEquals(-56f, shell.translationY, .001f); assertEquals(1f, shell.scaleX, .001f)
            assertEquals(1, completed)
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    @Test fun interruptedVerticalExitReturnsFromTheSamePositionAndRejectsItsStaleCompletion() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity).apply { entranceStyle = IslandEntranceStyle.TOP; windowTop = 8 }
        activity.setContentView(shell, FrameLayout.LayoutParams(240, 40))
        shell.geometry(240, 40, 40, 280); shell.layout(0, 0, 240, 40)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
        try {
            var completed = 0
            shell.exit { completed++ }
            val exit = getField<ValueAnimator>(shell, "motion")
            exit.currentPlayTime = 80
            val before = shell.translationY
            assertTrue(before < 0f)
            shell.resumeEntry(); shell.transition(240, 40, 240, 40, true)
            assertEquals(before, shell.translationY, .001f)
            val resumed = getField<ValueAnimator>(shell, "motion")
            exit.end(); assertEquals(0, completed)
            resumed.end()
            assertEquals(0f, shell.translationY, .001f); assertEquals(1f, shell.scaleX, .001f)
            assertEquals(1f, shell.alpha, .001f)
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    private fun reverseAt(time: Long) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(280, 40))
        fun layout(width: Int) {
            shell.geometry(width, 40, 40, 280)
            shell.layout(0, 0, width, 40)
            shell.cameraAnchor(20f, 16f)
        }
        layout(184)
        shadowOf(Looper.getMainLooper()).idle()
        layout(184)
        ShadowChoreographer.setPaused(true)
        try {
            layout(280); shell.transition(184, 40, 280, 40, false)
            getField<ValueAnimator>(shell, "motion").currentPlayTime = time
            val before = shell.width * shell.scaleX
            val velocity = getField<IslandMotion.Spring>(shell, "breadth").velocity + getField<Float>(shell, "breadthExtraVelocity")
            assertTrue(velocity > 0f)
            layout(184); shell.transition(280, 40, 184, 40, false)
            assertEquals(before, shell.width * shell.scaleX, .001f)
            assertEquals(velocity, getField<IslandMotion.Spring>(shell, "breadth").velocity, .01f)
            val motion = getField<ValueAnimator>(shell, "motion")
            motion.currentPlayTime = 8L
            assertTrue("A reversal must brake in the remaining safe space", shell.width * shell.scaleX > before)
            for (frame in 2..40) {
                motion.currentPlayTime = frame * 8L
                assertTrue(shell.width * shell.scaleX in 184f..280f)
            }
            motion.end()
            assertEquals(184f, shell.width * shell.scaleX, .001f)
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    private fun frames(hz: Int) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell = IslandActivityView(activity).apply { entranceStyle = IslandEntranceStyle.CENTER }
        activity.setContentView(shell, FrameLayout.LayoutParams(280, 40))
        shell.bind("Enabled", "Do Not Disturb", activity.getDrawable(android.R.drawable.ic_dialog_info), false, false, false, 0, false)
        fun layout(width: Int) {
            shell.geometry(width, 40, 40, 280)
            shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
            shell.layout(0, 0, width, 40)
            shell.cameraAnchor(20f, 16f)
        }
        layout(184)
        shadowOf(Looper.getMainLooper()).idle()
        layout(184)
        ShadowChoreographer.setPaused(true)
        val directory = java.lang.System.getenv("ISLAND_PREVIEW_DIR")?.let(::File)?.also { it.mkdirs() }
        val strip = if (directory != null) Bitmap.createBitmap(1600, 600, Bitmap.Config.ARGB_8888) else null
        val canvas = strip?.let(::Canvas)?.apply { drawColor(0xffdce3eb.toInt()) }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
        val samples = JSONArray()
        try {
            fun sample(row: Int, name: String, end: Boolean = true) {
                val animator = getField<ValueAnimator>(shell, "motion")
                val duration = if (end) animator.duration else animator.duration / 3
                val count = maxOf(1, (duration * hz / 1000).toInt())
                for (frame in 0..count) {
                    animator.currentPlayTime = duration * frame / count
                    val width = shell.width * shell.scaleX
                    assertTrue(width >= 40f - .001f && width <= 280f + .001f)
                    assertEquals(40f, shell.height * shell.scaleY, .001f)
                    samples.put(JSONObject().put("phase", name).put("timeMs", animator.currentPlayTime)
                        .put("width", width).put("height", shell.height * shell.scaleY)
                        .put("velocity", getField<IslandMotion.Spring>(shell, "breadth").velocity)
                        .put("alpha", shell.alpha))
                    if (canvas != null && frame in listOf(0, count / 4, count / 2, count * 3 / 4, count)) {
                        val column = when (frame) { 0 -> 0; count / 4 -> 1; count / 2 -> 2; count * 3 / 4 -> 3; else -> 4 }
                        canvas.drawText("$name ${animator.currentPlayTime}ms", column * 320f + 12, row * 100f + 20, ink)
                        val save = canvas.save()
                        canvas.translate(column * 320f + 160 - shell.width * .5f, row * 100f + 40)
                        canvas.concat(shell.matrix)
                        canvas.clipPath(getField<IslandShape>(shell, "shape").path)
                        // View.draw is normally called by a parent that applies root alpha.
                        // Reproduce that layer here so hidden entry/exit frames stay hidden.
                        canvas.saveLayerAlpha(null, (shell.alpha * 255).roundToInt().coerceIn(0, 255))
                        shell.draw(canvas)
                        canvas.restoreToCount(save)
                    }
                }
                if (end) animator.end()
            }
            shell.prepareEntry(); shell.transition(184, 40, 184, 40, true)
            sample(0, "enter")
            layout(280); shell.transition(184, 40, 280, 40, false)
            sample(1, "lengthen")
            layout(184); shell.transition(280, 40, 184, 40, false)
            sample(2, "shorten")
            layout(280); shell.transition(184, 40, 280, 40, false)
            sample(3, "interrupt", end = false)
            val before = shell.width * shell.scaleX
            val velocity = getField<IslandMotion.Spring>(shell, "breadth").velocity + getField<Float>(shell, "breadthExtraVelocity")
            layout(184); shell.transition(280, 40, 184, 40, false)
            assertEquals(before, shell.width * shell.scaleX, .001f)
            assertEquals(velocity, getField<IslandMotion.Spring>(shell, "breadth").velocity, .01f)
            sample(4, "reverse")
            var completions = 0
            shell.exit { completions++ }
            sample(5, "exit")
            assertEquals(1, completions)
            assertEquals(40f, shell.width * shell.scaleX, .001f)
            if (directory != null) {
                File(directory, "motion-$hz.json").writeText(samples.toString(2))
                File(directory, "motion-$hz.png").outputStream().use { strip!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { strip?.recycle(); ShadowChoreographer.setPaused(false); activity.finish() }
    }
}
