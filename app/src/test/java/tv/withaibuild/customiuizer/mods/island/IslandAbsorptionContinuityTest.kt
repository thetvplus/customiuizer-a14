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
    @Test fun existingReceiveExcursionRetainsHorizontalRecoilAroundItsRestingWidth() {
        val response=IslandAcceptanceResponse(240f,40f,240f,40f,0f,0f,1f,40f,260f,16f,80f)
        var peak=240f;var trough=240f
        for(ms in 0..1600 step 4) {
            response.sample(ms/1000f)
            peak=maxOf(peak,response.width);trough=minOf(trough,response.width)
        }
        assertTrue("The recovered receive excursion expands",peak>241f)
        assertTrue("Accepted's damped width may recoil past rest without being flattened",trough<239.9f)
        assertTrue(response.settled(1.6f))
    }
    @Test fun finiteReceiveBudgetContainsItsTailAtBothRefreshRatesAndHighDensity() {
        for(density in listOf(1f,3f,4f)) for(hz in listOf(60,120)) {
            val response=IslandAcceptanceResponse(154f*density,40f*density,240f*density,40f*density,
                90f*density,12f*density,density,40f*density,280f*density,16f*density,80f*density)
            var peak=0f
            for(frame in 0..(response.durationMillis*hz/1000).toInt()) {
                response.sample(frame/hz.toFloat())
                peak=maxOf(peak,response.height)
            }
            assertTrue(peak>46f*density)
            response.sample(response.durationMillis/1000f)
            assertTrue("A finite clock must contain the physical receive tail",response.settled(response.durationMillis/1000f))
            assertEquals(240f*density,response.width,.25f)
            assertTrue(kotlin.math.abs(response.velocityWidth)<2f)
        }
    }
    @Test fun criticalAxisDeadlineDoesNotDivideByTheUnderdampedFrequency() {
        val profile=IslandMotion.Profile(.513,1.0)
        val clock=profile.horizon(-400f,1200f)
        assertTrue(clock in 600..1800)
        val spring=IslandMotion.Spring().apply { setVelocity(1200f); retarget(0f,400f,profile,true) }
        assertEquals(400f,spring.sample(clock/1000f),.25f)
        assertTrue(kotlin.math.abs(spring.velocity)<2f)
    }
    @Test fun anEmergingIslandContainsTheHeadFromItsFirstContactWhileAnExistingIslandCanBeWider() {
        val shell = RectF(300f, 20f, 700f, 60f)
        val target = RectF(376f, 31.2f, 624f, 48.8f)
        val source = RectF(100f, 400f, 900f, 1800f)
        val seedLeft = 475f; val seedRight = 525f
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        var contacts = 0
        for (step in 0..900) {
            IslandAbsorptionMesh.fill(vertices, source, target, step / 1000f, intakeY = shell.centerY(),
                occlusion = shell, contactLeft = seedLeft, contactRight = seedRight)
            if (vertices[1] <= shell.bottom) {
                contacts++
                assertTrue(vertices[0] >= seedLeft)
                assertTrue(vertices[2] <= seedRight)
            }
        }
        assertTrue(contacts > 0)
        val seedWidth = vertices[2] - vertices[0]
        IslandAbsorptionMesh.fill(vertices, source, target, .2f, intakeY = shell.centerY(), occlusion = shell,
            contactLeft = shell.left, contactRight = shell.right)
        assertTrue(vertices[2] - vertices[0] > seedWidth * 3f)
    }
    @Test fun theEntireNeckAndItsEdgeGuardFitInsideTheActualIslandAtEverySupportedHeight() {
        for (height in listOf(32f, 36f, 40f, 64f, 100f, 192f)) {
            val shell = RectF(300f, 20f, 700f, 20f + height)
            val frame = IslandMusicReturnSignal.Frame("chosen.music", android.os.Process.myUserHandle(),
                shell.left, shell.top, shell.right, shell.bottom, 1080, 2400)
            val landing = frame.landingRectangle()
            assertTrue(landing.height() < frame.rectangle().height() * .6f)
            assertEquals(shell.centerY(), landing.centerY(), .0001f)
            val intake = IslandAbsorptionMesh.intakeY(shell, landing)
            val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
            val bounds = RectF()
            IslandAbsorptionMesh.fill(vertices, RectF(100f, 400f, 900f, 1800f), landing, 1f, bounds, intake)
            assertEquals(shell.centerX(), bounds.centerX(), .0001f)
            assertTrue(landing.contains(bounds))
            assertTrue(bounds.bottom < shell.bottom - 1f)
            assertEquals(shell.centerY(), intake, .0001f)
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

    @Test fun nativeReceiveStagesAreTwoDimensionalAndContinuousAcrossRefreshRates() {
        for(hz in listOf(60,90,120)) {
            val response=IslandAcceptanceResponse(154f,40f,240f,40f,0f,0f,1f,40f,280f,16f,80f)
            var last=154f; var tallest=40f
            for(i in 0..hz*2) {
                val time=i/hz.toFloat(); response.sample(time)
                assertTrue(response.width in 40f..280f)
                assertTrue(kotlin.math.abs(response.width-last)<12f)
                assertTrue(response.height>=40f-.001f)
                assertTrue(response.y<=.001f)
                tallest=maxOf(tallest,response.height); last=response.width
            }
            assertTrue("Receiving height must not be truncated by the old 1.14 cap",tallest>46f)
            assertTrue(response.settled(2f))
            assertEquals(240f,response.width,.25f); assertEquals(40f,response.height,.25f)
        }
    }

    @Test fun growingFromACameraSeedHasOneBoundedVisibleHorizontalRecoil() {
        val response = IslandAcceptanceResponse(24f, 40f, 240f, 40f, 0f, 0f, 1f, 24f, 300f, 16f, 80f)
        var peak = 24f
        var recoil = 0f
        for (ms in 0..1600) {
            response.sample(ms / 1000f)
            peak = maxOf(peak, response.width)
            recoil = maxOf(recoil, peak - response.width)
        }
        assertTrue("The new arrival must visibly expand within its receive excursion: $peak", peak in 244f..256f)
        assertTrue("The visible expansion must retract on the same clock", recoil >= 3f)
        assertTrue(response.settled(1.6f))
    }

    @Test fun aFullWidthIslandReceivesVerticallyInsteadOfInventingAnInwardGrip() {
        val response=IslandAcceptanceResponse(240f,40f,240f,40f,0f,0f,1f,40f,240f,16f,80f)
        response.sample(0f); assertEquals(240f,response.width,0f)
        response.sample(.1f)
        assertEquals(240f,response.width,0f); assertTrue(response.height>44f); assertTrue(response.y<0f)
        val height=response.height; val velocity=response.velocityHeight
        response.sample(.1f); assertEquals(height,response.height,.0001f); assertEquals(velocity,response.velocityHeight,.0001f)
        assertFalse(response.settled(.1f))
        response.sample(2f); assertTrue(response.settled(2f))
    }

    @Test fun thePageLeadingEdgeUsesAWideCenteredMouthInTheFirstSixteenPercentOfTravel() {
        val shell = RectF(300f, 20f, 700f, 60f)
        val frame = IslandMusicReturnSignal.Frame("chosen.music", android.os.Process.myUserHandle(),
            shell.left, shell.top, shell.right, shell.bottom, 1080, 2400)
        val landing = frame.landingRectangle()
        val intake = IslandAbsorptionMesh.intakeY(shell, landing)
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        IslandAbsorptionMesh.fill(vertices, RectF(100f, 400f, 900f, 1800f), landing, .16f, intakeY = intake)
        assertTrue("The leading edge is already at the island, not halfway through the page", vertices[1] < shell.bottom)
        assertTrue("An existing island can accept a broader mouth", vertices[2] - vertices[0] > shell.width() * .45f)
        assertEquals(shell.centerY() - landing.height() * .04f, vertices[1], .0001f)
        assertTrue("The trailing body still has volume", vertices.last() > 1700f)
    }

    @Test fun lateAdoptionStillStartsAtTheCurrentVisibleFrameAndKeepsRealReceivePhases() {
        val response=IslandAcceptanceResponse(180f,32f,240f,40f,90f,12f,1f,40f,280f,16f,80f)
        response.sample(0f); assertEquals(180f,response.width,0f); assertEquals(32f,response.height,0f)
        assertEquals(90f,response.velocityWidth,.0001f)
        response.sample(.12f); val w=response.width; val h=response.height
        response.sample(.120001f)
        assertEquals(w,response.width,.001f); assertEquals(h,response.height,.001f)
        response.sample(.20f); val accepted=response.width
        response.sample(.200001f); assertEquals(accepted,response.width,.001f)
        response.sample(2f); assertTrue(response.settled(2f))
    }

    @Test fun cleanMeshKeepsItsBodyOpaqueWithoutAColourBuffer() {
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        val texture = Bitmap.createBitmap(60, 140, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val image = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888)
        try {
            IslandAbsorptionMesh.fill(vertices, RectF(50f, 400f, 650f, 1800f), RectF(350f, 20f, 650f, 100f), .4f)
            Canvas(image).drawBitmapMesh(texture, 1, IslandAbsorptionMesh.ROWS, vertices, 0, null, 0,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            assertEquals(255, Color.alpha(image.getPixel(500, 300)))
            assertEquals(255, Color.alpha(image.getPixel(500, 900)))
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
