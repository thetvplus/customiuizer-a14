package tv.withaibuild.customiuizer.mods.island

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandAbsorptionContinuityTest {
    @Test fun theEntireNeckAndItsEdgeGuardFitInsideTheActualIslandAtEverySupportedHeight() {
        for (height in listOf(32f, 36f, 40f, 64f, 100f, 192f)) {
            val shell = RectF(300f, 20f, 700f, 20f + height)
            val landing = RectF(shell).apply { inset(shell.width() * .08f, height * .07f) }
            val intake = IslandAbsorptionMesh.intakeY(shell, landing)
            val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
            val bounds = RectF()
            IslandAbsorptionMesh.fill(vertices, RectF(100f, 400f, 900f, 1800f), landing, 1f, bounds, intake)
            assertEquals(shell.centerX(), bounds.centerX(), .0001f)
            assertTrue(landing.contains(bounds))
            assertTrue(bounds.bottom < shell.bottom - 1f)
            assertTrue(intake < shell.top + height * .9f)
            assertEquals(0f, IslandAbsorptionMesh.mergeOpacity(bounds, shell), .00001f)
        }
    }

    @Test fun theNeckCannotLeaveAnOpaqueEndpoint() {
        val shell = RectF(300f, 20f, 700f, 120f)
        val landing = RectF(332f, 27f, 668f, 113f)
        val intake = IslandAbsorptionMesh.intakeY(shell, landing)
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        val bounds = RectF()
        IslandAbsorptionMesh.fill(vertices, RectF(100f, 400f, 900f, 1800f), landing, 1f, bounds, intake)
        assertEquals(shell.centerX(), bounds.centerX(), .0001f)
        assertEquals(intake, bounds.centerY(), .0001f)
        assertTrue(shell.contains(bounds))
        assertEquals("The lower entry cannot leave an opaque endpoint", 0f,
            IslandAbsorptionMesh.mergeOpacity(bounds, shell), .00001f)
    }
    @Test fun uncommittedFramesMirrorTheMovingNativeWindowBeforeStartingTheSameMesh() {
        val handoff = IslandAbsorptionHandoff()
        val target = RectF(360f, 20f, 640f, 100f)
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        val bounds = RectF()
        for (step in 0..4) {
            val native = RectF(80f + step * 10, 400f - step * 20, 920f - step * 10, 1900f - step * 40)
            val progress = handoff.progress(step * .05f, native, presented = false)
            IslandAbsorptionMesh.fill(vertices, handoff.source, target, progress, bounds)
            assertEquals(0f, progress, 0f)
            assertEquals(native, bounds)
        }
        val committed = RectF(handoff.source)
        val progress = handoff.progress(.3f, RectF(180f, 200f, 820f, 1300f), presented = true)
        assertEquals(RectF(180f, 200f, 820f, 1300f), handoff.source)
        assertEquals(.125f, progress, .00001f)
        IslandAbsorptionMesh.fill(vertices, handoff.source, target, progress, bounds)
        assertTrue(bounds.top < committed.top)
        assertTrue(bounds.bottom > target.bottom)
    }

    @Test fun theFirstDeformingFrameKeepsTheNativePagesReleaseTravelInsteadOfStoppingAtAFrozenRect() {
        val handoff = IslandAbsorptionHandoff()
        val target = RectF(360f, 20f, 640f, 100f)
        handoff.progress(.2f, RectF(80f, 400f, 920f, 1800f), false)
        val native = RectF(85f, 385f, 915f, 1760f)
        val p = handoff.progress(.21f, native, true)
        val bounds = RectF()
        IslandAbsorptionMesh.fill(FloatArray(IslandAbsorptionMesh.FLOATS), handoff.source, target, p, bounds)
        assertTrue("The trailing edge must keep the native frame's motion", bounds.bottom <= native.bottom)
        assertTrue(bounds.bottom < 1770f)
    }

    @Test fun theRecoilStartsOnlyAfterThePageDisappearsAndUsesTheSharedWeightedCatchAtDifferentRefreshRates() {
        for (hz in listOf(60, 90, 120)) {
            val response = IslandMotion.AbsorptionResponse(154f, 240f, 40f, 248f, 6f, 0f, .8f)
            var maximum = 0f
            var maximumProgress = 0f
            var last = 154f
            for (i in 0..(hz * 1.2f).toInt()) {
                val time = i / hz.toFloat()
                val page = (time / .8f).coerceAtMost(1f)
                val width = response.sample(page, time)
                assertTrue(width in 40f..248f)
                assertTrue("No release-frame jump at $hz Hz", kotlin.math.abs(width - last) < 12f)
                if (width > maximum) { maximum = width; maximumProgress = page }
                last = width
                if (page < 1f) assertTrue("No outward bounce before swallowing", width <= 240.001f)
                if (page == 1f && time < 1.04f) assertFalse(response.settled(time))
            }
            assertEquals("The page must be fully swallowed before the peak at $hz Hz", 1f, maximumProgress, 0f)
            assertEquals(246f, maximum, .15f)
            response.sample(1f, 1.2f)
            assertTrue(response.settled(1.2f)); assertEquals(240f, response.value, 0f)
            assertEquals(0f, response.velocity, 0f)
        }
    }

    @Test fun aLateChannelStartsAtTheAlreadyVisibleWidthInsteadOfReplayingAnEntireArrival() {
        val response = IslandMotion.AbsorptionResponse(180f, 240f, 40f, 248f, 6f, 0f, .8f, .98f)
        assertEquals(180f, response.sample(1f, 0f), 0f)
        assertTrue(response.sample(1f, .1f) in 180f..240f)
        assertEquals(240f, response.sample(1f, .3f), 0f)
        assertTrue(response.settled(.3f))
    }

    @Test fun theMeshSoftensOnlyItsContactEdgeAndKeepsThePageBodyOpaque() {
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        val colors = IntArray((IslandAbsorptionMesh.ROWS + 1) * 2)
        val source = RectF(50f, 400f, 650f, 1800f)
        val target = RectF(350f, 20f, 650f, 100f)
        for (p in listOf(0f, .05f, .14f, .4f, .7f)) {
            IslandAbsorptionMesh.fill(vertices, source, target, p)
            IslandAbsorptionMesh.contactColors(colors, vertices, target, p)
            for (row in 0..IslandAbsorptionMesh.ROWS) {
                val alpha = Color.alpha(colors[row * 2])
                assertTrue(alpha in 239..255)
                if (p == 0f || row >= 8) assertEquals(255, alpha)
            }
        }
        val texture = Bitmap.createBitmap(60, 140, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val image = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888)
        try {
            IslandAbsorptionMesh.fill(vertices, source, target, .4f)
            IslandAbsorptionMesh.contactColors(colors, vertices, target, .4f)
            Canvas(image).drawBitmapMesh(texture, 1, IslandAbsorptionMesh.ROWS, vertices, 0, colors, 0,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val alphas = (0 until image.height).map { y -> Color.alpha(image.getPixel(500, y)) }
            assertTrue("The rendered neck is only slightly translucent", alphas.any { it in 239..254 })
            assertTrue("The rendered body stays solid", alphas.count { it == 255 } > 100)
        } finally { texture.recycle(); image.recycle() }
    }

    @Test fun softCompressionHasAContinuousInverseAndTheCorrectPhysicalVelocity() {
        for (minimum in listOf(0f, .8f, .99f)) for (delta in listOf(-.01f, -.03f, .02f)) {
            val compressed = IslandMotion.compressedScale(1f, delta, minimum)
            assertEquals(1f, IslandMotion.compressionOrigin(compressed, delta, minimum), .00001f)
            assertTrue(compressed >= minimum)
            val d = .0001f
            val derivative = (IslandMotion.compressedScale(1f, delta + d, minimum) -
                IslandMotion.compressedScale(1f, delta - d, minimum)) / (2f * d)
            assertEquals(derivative, IslandMotion.compressionVelocity(1f, delta, minimum), .001f)
        }
    }
}
