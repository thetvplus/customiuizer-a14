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
    @Test fun leadingEdgeNarrowsFirstAndTheMeshNeverFoldsOrChangesOpacity() {
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
            assertEquals(rect.top, vertices[1], .001f)
            assertEquals(rect.bottom, vertices[IslandAbsorptionMesh.ROWS * 4 + 1], .001f)
        }
        IslandAbsorptionMesh.fill(vertices, rect, target, .5f)
        assertTrue(vertices[2] - vertices[0] < rect.width() * .4f)
        assertEquals(rect.width(), vertices[IslandAbsorptionMesh.ROWS * 4 + 2] - vertices[IslandAbsorptionMesh.ROWS * 4], .001f)
        for (end in listOf(0f, 1f)) {
            IslandAbsorptionMesh.fill(vertices, rect, target, end)
            assertEquals(rect.left, vertices[0], .001f)
            assertEquals(rect.right, vertices[2], .001f)
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
            assertTrue("The leading edge must visibly neck inward", span(30) < span(390) * .6f)
            assertEquals(255, Color.alpha(output.getPixel(150, 200)))
            assertEquals(Color.RED, output.getPixel(150, 200))
        } finally { source.recycle(); output.recycle() }
    }
}
