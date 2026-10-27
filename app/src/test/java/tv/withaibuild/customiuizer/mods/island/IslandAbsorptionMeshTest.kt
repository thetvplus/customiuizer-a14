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
class IslandAbsorptionMeshTest {
    @Test fun aFastLateOrAlreadyTranslucentWindowStaysNativeInsteadOfFlashingIntoAMesh() {
        assertTrue(IslandAbsorptionMesh.mayReplaceNative(.04f, 1f))
        assertTrue(IslandAbsorptionMesh.mayReplaceNative(.3f, .99f))
        assertFalse(IslandAbsorptionMesh.mayReplaceNative(.04f, .8f))
        assertFalse(IslandAbsorptionMesh.mayReplaceNative(.6f, 1f))
        assertFalse(IslandAbsorptionMesh.mayReplaceNative(Float.NaN, 1f))
    }
    @Test fun pageRetainsATrailingBodyWhileTheLeadingEdgeArrivesAndRowsNeverFold() {
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        val rect = RectF(100f, 80f, 900f, 1680f)
        val target = RectF(380f, 10f, 620f, 100f)
        for (step in 0..100) {
            IslandAbsorptionMesh.fill(vertices, rect, target, step / 100f)
            var y = -Float.MAX_VALUE
            for (row in 0..IslandAbsorptionMesh.ROWS) {
                val i = row * 4
                assertTrue(vertices[i] < vertices[i + 2])
                assertTrue(vertices[i + 1] > y); y = vertices[i + 1]
                assertEquals(vertices[i + 1], vertices[i + 3], 0f)
            }
            assertTrue(vertices[1] in target.top..rect.top)
            assertTrue(vertices[IslandAbsorptionMesh.ROWS * 4 + 1] in target.top..rect.bottom)
        }
        IslandAbsorptionMesh.fill(vertices, rect, target, .5f)
        assertTrue(vertices[2] - vertices[0] < rect.width() * .45f)
        assertTrue(vertices[IslandAbsorptionMesh.ROWS * 4 + 2] - vertices[IslandAbsorptionMesh.ROWS * 4] > rect.width() * .8f)
        assertTrue(vertices[IslandAbsorptionMesh.ROWS * 4 + 1] - vertices[1] > rect.height() * .65f)
        IslandAbsorptionMesh.fill(vertices, rect, target, 0f)
        assertEquals(rect.left, vertices[0], .001f)
        assertEquals(rect.right, vertices[2], .001f)
        for (t in listOf(.14f, .3f, .6f, 1f)) {
            IslandAbsorptionMesh.fill(vertices, rect, target, t)
            assertEquals(target.centerY() - target.height() * .04f, vertices[1], .001f)
            assertEquals(target.centerX(), (vertices[0] + vertices[2]) * .5f, .001f)
            assertTrue(vertices[2] - vertices[0] < target.width() * .4f)
        }
        for (row in 0..IslandAbsorptionMesh.ROWS) {
            assertTrue(target.contains(vertices[row * 4], vertices[row * 4 + 1]))
            assertTrue(target.contains(vertices[row * 4 + 2], vertices[row * 4 + 3]))
        }
    }

    @Test fun thePageRemainsReadableThroughIntakeThenFadesContinuouslyIntoTheBlackIsland() {
        assertEquals(1f, IslandAbsorptionMesh.opacity(0f), 0f)
        assertEquals(.96f, IslandAbsorptionMesh.opacity(.14f), .00001f)
        assertEquals(.96f, IslandAbsorptionMesh.opacity(.6f), .00001f)
        var previous = 1f
        for (i in 0..100) {
            val next = IslandAbsorptionMesh.opacity(i / 100f)
            assertTrue(next <= previous && previous - next < .045f)
            previous = next
        }
        assertTrue(previous in .04f.. .06f)
    }

    @Test fun admissionUsesTheAcceptedReturnProgressRatherThanTheDistanceAlreadyDragged() {
        for (atRelease in listOf(0f, .3f, .6f, .8f)) {
            val first = IslandAbsorptionMesh.returnProgress(atRelease, atRelease)
            assertEquals(0f, first, .00001f)
            assertTrue(IslandAbsorptionMesh.mayReplaceNative(first, 1f))
            val progress = atRelease + (1f - atRelease) * .3f
            assertEquals(.3f, IslandAbsorptionMesh.returnProgress(progress, atRelease), .00001f)
            assertTrue(IslandAbsorptionMesh.mayReplaceNative(IslandAbsorptionMesh.returnProgress(progress, atRelease), 1f))
            assertEquals(1f, IslandAbsorptionMesh.returnProgress(1f, atRelease), .00001f)
        }
    }

    @Test fun actualCanvasMeshDrawsAnOpaqueCurvedWindowInsteadOfFadingARectangle() {
        val source = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val output = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888)
        try {
            val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
            IslandAbsorptionMesh.fill(vertices, RectF(50f, 20f, 250f, 420f), RectF(110f, 0f, 190f, 36f), .5f)
            Canvas(output).drawBitmapMesh(source, 1, IslandAbsorptionMesh.ROWS, vertices, 0, null, 0, Paint(Paint.ANTI_ALIAS_FLAG))
            fun span(y: Int) = (0 until output.width).count { Color.alpha(output.getPixel(it, y)) >= 250 }
            assertTrue("The leading edge must visibly neck inward", span(30) < span(280) * .75f)
            assertEquals(255, Color.alpha(output.getPixel(150, 200)))
            assertEquals(Color.RED, output.getPixel(150, 200))
        } finally { source.recycle(); output.recycle() }
    }
}
