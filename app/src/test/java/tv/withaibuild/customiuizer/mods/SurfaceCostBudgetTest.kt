package tv.withaibuild.customiuizer.mods

import android.graphics.Path
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.island.*
import kotlin.math.ceil

/** User-approved decorative budgets; gesture/native-window clocks are deliberately separate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SurfaceCostBudgetTest {
    @Test fun dockHasNoWallpaperCaptureShaderOrSamplingScheduler() {
        val source = tv.withaibuild.customiuizer.testsupport.repositorySource(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherDockBackground.kt")
        for (forbidden in listOf("DockWallpaper", "DockGlassBackdrop", "RuntimeShader", "Bitmap", "postDelayed"))
            assertFalse(forbidden, source.contains(forbidden))
    }

    @Test fun fullPageTextureIsBoundedInPortraitLandscapeAndSmallWindows() {
        for ((width, height) in listOf(1080 to 2400, 1440 to 3200, 2400 to 1080,
            3200 to 1440, 720 to 1600, 100 to 200, 1 to 1)) {
            val scale = LauncherWindowAbsorption.captureScale(width, height)
            assertTrue(scale > 0f && scale <= 1f)
            val outWidth = ceil(width.toDouble() * scale).toLong()
            val outHeight = ceil(height.toDouble() * scale).toLong()
            assertTrue("$width x $height => $outWidth x $outHeight", outWidth <= 769)
            assertTrue("$width x $height => $outWidth x $outHeight", outWidth * outHeight <= 524_288)
        }
    }

    @Test fun pageMeshUsesAtMost34VerticesWithoutAnExtraColumn() {
        assertEquals(16, IslandAbsorptionMesh.ROWS)
        assertEquals(68, IslandAbsorptionMesh.FLOATS)
    }

    @Test fun capsuleUses16SegmentsPerCapAndRetainsItsContactJoin() {
        class Counted : Path() {
            var cubics = 0
            override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
                cubics++
                super.cubicTo(x1, y1, x2, y2, x3, y3)
            }
        }
        val plain = Counted().also { IslandCapsuleGeometry.build(it, 240f, 40f) }
        val contact = Counted().also { IslandCapsuleGeometry.build(it, 240f, 40f, 1f) }
        assertEquals(32, plain.cubics)
        assertEquals(34, contact.cubics)
    }

    @Test fun waveformCaptureIsBoundedSeparatelyFromDisplayVsync() {
        assertEquals(10_000, IslandAudioSpectrum.REQUESTED_CAPTURE_RATE)
        assertEquals(100L, IslandRecorderMeter.SAMPLE_INTERVAL_MS)
        val wave = tv.withaibuild.customiuizer.testsupport.repositorySource(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/island/IslandActivityView.kt")
            .substringAfter("internal class IslandWaveView")
        assertTrue(wave.contains("postOnAnimation(frame)"))
        assertFalse("Do not cap visible waveform rendering with a timer", wave.contains("postDelayed"))
    }
}
