package tv.withaibuild.customiuizer.mods.statusicons

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatteryDigitContrastTest {
    @Test fun nativeTextKeepsItsContentAndFontWhileTheActualFillEdgeChoosesContrast() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val root = FrameLayout(activity); val icon = View(activity); val text = TextView(activity)
            root.addView(icon, FrameLayout.LayoutParams(88, 46))
            root.addView(text, FrameLayout.LayoutParams(88, 46))
            activity.setContentView(root)
            root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 400, 800)
            icon.layout(20, 20, 108, 66); text.layout(20, 20, 108, 66)
            text.setTextColor(Color.WHITE)
            val font = text.typeface
            val original = LinearGradient(0f, 0f, 88f, 0f, Color.RED, Color.BLUE, Shader.TileMode.CLAMP)
            text.paint.shader = original
            val owner = BatteryDigitContrast(icon, text)
            val bitmap = Bitmap.createBitmap(88, 1, Bitmap.Config.ARGB_8888)
            fun pixel(x: Int): Int {
                bitmap.eraseColor(Color.TRANSPARENT)
                Canvas(bitmap).drawRect(0f, 0f, 88f, 1f, Paint(text.paint))
                return bitmap.getPixel(x, 0)
            }
            try {
                for (content in listOf("5", "50", "100")) {
                    text.text = content
                    owner.update(50, Color.WHITE, Color.WHITE)
                    assertEquals(Color.BLACK, pixel(20))
                    assertEquals(Color.WHITE, pixel(60))
                    assertEquals(content, text.text.toString())
                    assertSame(font, text.typeface)
                    assertEquals(Color.WHITE, text.currentTextColor)
                    val stable = text.paint.shader
                    repeat(50) { owner.update(50, Color.WHITE, Color.WHITE) }
                    assertSame("Stable input reuses the shader", stable, text.paint.shader)
                }
                owner.update(0, Color.WHITE, Color.WHITE)
                assertEquals(Color.WHITE, pixel(20)); assertEquals(Color.WHITE, pixel(60))
                owner.update(100, Color.WHITE, Color.WHITE)
                assertEquals(Color.BLACK, pixel(20)); assertEquals(Color.BLACK, pixel(60))
                owner.update(50, Color.BLACK, Color.BLACK)
                assertEquals(Color.WHITE, pixel(20)); assertEquals(Color.BLACK, pixel(60))
                owner.update(50, Color.WHITE, Color.WHITE)
                assertEquals(Color.WHITE, pixel(43))
                icon.layout(25, 20, 113, 66)
                assertEquals("Native geometry moves the shader edge", Color.BLACK, pixel(43))
            } finally {
                owner.close(); bitmap.recycle()
            }
            assertSame(original, text.paint.shader)
            val replacement = BatteryDigitContrast(icon, text)
            replacement.update(50, Color.WHITE, Color.WHITE)
            val newer = LinearGradient(0f, 0f, 88f, 0f, Color.GREEN, Color.GREEN, Shader.TileMode.CLAMP)
            text.paint.shader = newer
            replacement.onViewDetachedFromWindow(text)
            assertSame("Detach cannot overwrite another owner", newer, text.paint.shader)
        } finally { activity.finish() }
    }
}
