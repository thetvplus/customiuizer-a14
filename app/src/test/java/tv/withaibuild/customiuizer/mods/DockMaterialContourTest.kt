package tv.withaibuild.customiuizer.mods

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockMaterialContourTest {
    @Test fun drawnPanelsUseTheSamePhysicalOutlineAsTheNativeRoundedMaterial() {
        for (radius in listOf(0f, 24f, 60f, 100f)) {
            val expected = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
            try {
                Canvas(expected).drawRoundRect(0f, 0f, 400f, 200f, radius, radius,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                DockBackgroundTint().apply {
                    color = Color.WHITE
                    this.radius = radius
                    setBounds(0, 0, 400, 200)
                }.draw(Canvas(actual))
                for (y in 0 until 200) {
                    val nativeLeft = (0 until 200).firstOrNull { Color.alpha(expected.getPixel(it, y)) >= 128 }
                    val drawnLeft = (0 until 200).firstOrNull { Color.alpha(actual.getPixel(it, y)) >= 128 }
                    assertNotNull("native scanline $y", nativeLeft)
                    assertNotNull("drawn scanline $y", drawnLeft)
                    assertTrue("r=$radius y=$y native=$nativeLeft drawn=$drawnLeft", abs(nativeLeft!! - drawnLeft!!) <= 1)
                }
            } finally { expected.recycle(); actual.recycle() }
        }
    }
}
