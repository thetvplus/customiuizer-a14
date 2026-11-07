package tv.withaibuild.customiuizer.mods.duostatusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DuoAirplaneTest {
    @Test fun visibleInkStaysAtRingCenterAndClearOfTopMarkerAndSignalDots() {
        val config = DuoConfig(true, 24)
        val plane = DuoIosGlyphs(config)
        val key = DuoCenter.key(DuoNetworkState.AIRPLANE, 0, config)
        plane.prepare(key, -1)
        for (size in intArrayOf(24, 40, 70, 120, 600)) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.scale(size / 120f, size / 120f)
                plane.draw(canvas, key, -1)
                var weight = 0.0
                var x = 0.0
                var y = 0.0
                var top = size
                var bottom = 0
                for (row in 0 until size) for (col in 0 until size) {
                    val alpha = bitmap.getPixel(col, row) ushr 24
                    weight += alpha
                    x += (col + .5) * alpha
                    y += (row + .5) * alpha
                    if (alpha > 0) {
                        top = minOf(top, row)
                        bottom = maxOf(bottom, row)
                    }
                }
                assertTrue(weight > 0)
                assertEquals(60.0 * size / 120, x / weight, .3)
                assertEquals(60.0 * size / 120, y / weight, .3)
                assertTrue(top * 120f / size >= 27f)
                assertTrue((bottom + 1) * 120f / size < 97f)
            } finally { bitmap.recycle() }
        }
    }
}
