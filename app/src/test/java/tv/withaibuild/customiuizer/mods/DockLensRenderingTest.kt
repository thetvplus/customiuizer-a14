package tv.withaibuild.customiuizer.mods

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native HWUI/Skia host pixels, not proof of an Adreno device or wallpaper-capture permission. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, shadows = [DockRuntimeShaderShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockLensRenderingTest {
    private fun hardwarePixels(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap {
        val node = RenderNode("dock-lens-pixel-test")
        node.setPosition(0, 0, width, height)
        val recording = node.beginRecording(width, height)
        try { draw(recording) } finally { node.endRecording() }
        try {
            val method = HardwareRenderer::class.java.getDeclaredMethod("createHardwareBitmap",
                RenderNode::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            method.isAccessible = true
            val image = method.invoke(null, node, width, height) as Bitmap
            return image.copy(Bitmap.Config.ARGB_8888, false).also { image.recycle() }
        } finally { node.discardDisplayList() }
    }

    private fun lens(source: Bitmap, rect: RectF): RuntimeShader = RuntimeShader(DockGlassOptics.LENS_SOURCE).apply {
        setInputShader("content", BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        setInputBuffer("cornerField", DockCornerField.shader)
        setFloatUniform("origin", rect.left, rect.top)
        setFloatUniform("size", rect.width(), rect.height())
        setFloatUniform("radius", 24f)
        setFloatUniform("density", 1f)
        setFloatUniform("sampleSize", source.width.toFloat(), source.height.toFloat())
        setFloatUniform("sampleX", 1f, 0f, rect.left)
        setFloatUniform("sampleY", 0f, 1f, rect.top)
        setFloatUniform("motion", 0f)
    }

    @Test
    fun realTextureFeaturesBendAtTheEdgeAndAReplacedInputChangesTheTransmittedImage() {
        val source = Bitmap.createBitmap(320, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        val paint = Paint()
        for (x in 0 until 320 step 6) {
            paint.color = if ((x / 6) % 2 == 0) Color.WHITE else Color.BLACK
            canvas.drawRect(x.toFloat(), 0f, x + 6f, 100f, paint)
        }
        val rect = RectF(0f, 0f, 320f, 100f)
        val shader = lens(source, rect)
        val result = hardwarePixels(320, 100) { it.drawPath(Path().apply {
            tv.withaibuild.customiuizer.utils.G4Geometry.build(this, rect.width(), rect.height(), 24f)
            offset(rect.left, rect.top)
        }, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }) }
        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
        assertTrue("inward lens samples a different stripe, rather than adding a bright rim",
            Color.red(result.getPixel(1, 50)) < 80)
        assertTrue(Color.red(source.getPixel(1, 50)) > 240)
        assertTrue("the flat center still transmits its original stripe", Color.red(result.getPixel(160, 50)) > 220)
        val dark = Bitmap.createBitmap(320, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(18, 22, 26)) }
        shader.setInputShader("content", BitmapShader(dark, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        val changed = hardwarePixels(320, 100) { it.drawPath(Path().apply {
            tv.withaibuild.customiuizer.utils.G4Geometry.build(this, rect.width(), rect.height(), 24f)
            offset(rect.left, rect.top)
        }, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }) }
        assertTrue(Color.red(changed.getPixel(160, 50)) < 50)
        assertEquals(0, Color.alpha(changed.getPixel(0, 0)))
        source.recycle(); result.recycle(); dark.recycle(); changed.recycle()
    }

    @Test
    fun uniformLightWallpaperHasNoBlackInnerContourOrOpaqueCorner() {
        val source = Bitmap.createBitmap(320, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(185, 202, 192))
        }
        val rect = RectF(0f, 0f, 320f, 100f)
        val shader = lens(source, rect)
        val image = hardwarePixels(320, 100) { it.drawPath(Path().apply {
            tv.withaibuild.customiuizer.utils.G4Geometry.build(this, rect.width(), rect.height(), 24f)
            offset(rect.left, rect.top)
        }, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }) }
        assertEquals(0, Color.alpha(image.getPixel(0, 0)))
        for (x in 24 until 296) for (y in 2 until 98) {
            val pixel = image.getPixel(x, y)
            assertTrue("no dark artifact at $x,$y: $pixel", Color.red(pixel) > 140)
            assertTrue(Color.green(pixel) > 140)
            assertTrue(Color.blue(pixel) > 140)
        }
        source.recycle(); image.recycle()
    }

    @Test
    fun compareSolidFrostedAndLiquidOnLightDarkAndDetailedWallpaper() {
        val width = 1080
        val height = 1040
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val palettes = arrayOf(intArrayOf(0xffe9ece4.toInt(), 0xffc3d9cd.toInt()),
            intArrayOf(0xff142a3e.toInt(), 0xff304659.toInt()),
            intArrayOf(0xffa7b9ba.toInt(), 0xff477a82.toInt()),
            intArrayOf(0xff758590.toInt(), 0xffd0cab8.toInt()))
        for (row in palettes.indices) for (column in 0..2) {
            val left = column * 360f
            val top = 40f + row * 250
            paint.shader = LinearGradient(left, top, left + 360, top + 250,
                palettes[row], null, Shader.TileMode.CLAMP)
            canvas.drawRect(left, top, left + 360, top + 250, paint)
            paint.shader = null
            if (row >= 2) {
                paint.strokeWidth = 4f
                for (i in -6..14) {
                    paint.color = if (i % 2 == 0) 0xffecdbc0.toInt() else 0xff294d51.toInt()
                    canvas.drawLine(left + i * 30, top, left + i * 30 + 110, top + 250, paint)
                }
            } else {
                paint.color = if (row == 0) 0xffa4b7a0.toInt() else 0xff6c92a8.toInt()
                canvas.drawOval(left + 170, top + 70, left + 330, top + 230, paint)
            }
            paint.color = if (row == 1) 0xffa4c0c7.toInt() else 0xff374d4a.toInt()
            paint.textSize = 18f
            canvas.drawText("Wallpaper details 0123", left + 60, top + 148, paint)
        }
        val blurNodes = floatArrayOf(35f, 18f).map { radius ->
            RenderNode("dock-material-comparison").apply {
                setPosition(0, 0, width, height)
                setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
                val recording = beginRecording(width, height)
                try { recording.drawBitmap(source, 0f, 0f, null) } finally { endRecording() }
            }
        }
        val preview = hardwarePixels(width, height) { output ->
            output.drawBitmap(source, 0f, 0f, null)
            for (row in palettes.indices) for (column in 0..2) {
                val left = column * 360f + 24
                val top = 40f + row * 250 + 80
                val rect = RectF(left, top, left + 312, top + 96)
                val path = Path().apply {
                    tv.withaibuild.customiuizer.utils.G4Geometry.build(this, rect.width(), rect.height(), 24f)
                    offset(rect.left, rect.top)
                }
                val opacity = if (row == 3) .65f else .9f
                if (column == 1) {
                    val saved = output.save()
                    output.clipPath(path)
                    output.drawRenderNode(blurNodes[if (column == 1) 0 else 1])
                    output.restoreToCount(saved)
                }
                paint.shader = null
                paint.color = if (row == 1) 0xff182028.toInt() else Color.WHITE
                paint.alpha = (if (column == 0) 225 * opacity else 35 * opacity).toInt()
                output.drawRoundRect(rect, 24f, 24f, paint)
                if (column == 2) {
                    paint.shader = lens(source, rect)
                    paint.alpha = (255 * opacity).toInt()
                    output.drawPath(path, paint)
                    paint.shader = null
                }
                // Native icons are foreground content, never part of the lens input texture.
                for (i in 0..3) {
                    paint.color = intArrayOf(0xffffc349.toInt(), 0xffe95765.toInt(), 0xff2e999a.toInt(), 0xff384a59.toInt())[i]
                    paint.alpha = 255
                    val x = left + 22 + i * 72
                    output.drawRoundRect(x, top + 22, x + 52, top + 74, 13f, 13f, paint)
                    paint.color = Color.WHITE
                    output.drawCircle(x + 26, top + 48, 9f, paint)
                }
            }
            paint.shader = null
            paint.color = 0xff333333.toInt()
            paint.textSize = 22f
            for ((i, title) in listOf("Solid", "Frosted", "Liquid").withIndex()) output.drawText(title, i * 360f + 30, 28f, paint)
        }
        assertNotEquals(preview.getPixel(365 + 24, 40 + 80 + 48), preview.getPixel(725 + 24, 40 + 80 + 48))
        java.lang.System.getenv("CUSTOMIUIZER_DOCK_LENS_PREVIEW")?.let { destination ->
            java.io.File(destination).outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        blurNodes.forEach { it.discardDisplayList() }
        source.recycle(); preview.recycle()
    }
}
