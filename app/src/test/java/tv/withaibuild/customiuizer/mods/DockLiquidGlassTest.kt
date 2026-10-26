package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RenderNode
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [DockWindowManagerShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockLiquidGlassTest {
    @Test
    fun coatingCompilesAndNearStraightG4EdgesStaySmoothAndWithinTheirBounds() {
        assertNotNull(DockGlassOptics.create())
        val path = Path()
        DockCornerGeometry.buildSoft(path, 320f, 80f, 20f, 0.3f)
        val measure = PathMeasure(path, true)
        val point = FloatArray(2)
        val before = FloatArray(2)
        val after = FloatArray(2)
        measure.getPosTan(70f, point, null)
        assertTrue("near-straight edge has a bounded subpixel bow", point[1] in 0.01f..0.3f)
        // Path.computeBounds includes Bezier control points outside a shallow arc. Inspect
        // positions on the actual outline instead of mistaking its control hull for overflow.
        measure.getPosTan(140f, point, null)
        assertEquals(0f, point[1], 0.001f)
        measure.getPosTan(279.99f, null, before)
        measure.getPosTan(280.01f, null, after)
        assertTrue("corner and edge tangents agree", before[0] * after[0] + before[1] * after[1] > 0.9999f)
        for (i in 0..1000) {
            measure.getPosTan(measure.length * i / 1000f, point, after)
            assertTrue(point[0].isFinite() && point[1].isFinite())
            assertTrue(point[0] in -0.001f..320.001f && point[1] in -0.001f..80.001f)
        }
    }

    @Test
    fun hardwareRecordingUsesTheCompiledCoatingAndSoftwareDrawsKeepTheirFallback() {
        val tint = DockBackgroundTint(withRim = true).apply {
            color = Color.WHITE
            alpha = 18
            radius = 20f
            rounded = true
            setBounds(0, 0, 320, 80)
        }
        val node = RenderNode("dock-glass-test")
        node.setPosition(0, 0, 320, 80)
        try {
            val recording = node.beginRecording(320, 80)
            assertTrue(recording.isHardwareAccelerated)
            try { tint.draw(recording) } finally { node.endRecording() }
            assertTrue(node.hasDisplayList())
            tint.draw(Canvas(Bitmap.createBitmap(320, 80, Bitmap.Config.ARGB_8888)))
            val again = node.beginRecording(320, 80)
            try { tint.draw(again) } finally { node.endRecording() }
            assertTrue(node.hasDisplayList())
        } finally {
            node.discardDisplayList()
        }
    }

    @Test
    fun liquidMaterialWithoutACaptureSourceKeepsNativeLiveBlurAndBlend() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            DockBackgroundTest.NativeMaterial.googleSupported = true
            val host = FrameLayout(activity.get())
            activity.get().setContentView(host)
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = 3),
                DockBackgroundTest.NativeMaterial::class.java.getConstructor(Context::class.java))
            host.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 360, 80)
            val decoration = host.getChildAt(0) as DockBackgroundView
            val material = decoration.getChildAt(0) as DockBackgroundTest.NativeMaterial
            assertEquals(1, decoration.childCount)
            assertEquals((18 * host.resources.displayMetrics.density).roundToInt(), material.appliedBlurRadius)
            assertTrue(material.mBlurLayerHolder.enabled)
            assertTrue(material.appliedEnabled)
            assertEquals(18, decoration.foreground.alpha)
            host.removeView(decoration)
            assertNull(material.background)
            assertEquals(0, decoration.childCount)
        } finally {
            activity.pause().stop().destroy()
            DockBackgroundTest.NativeMaterial.googleSupported = false
        }
    }

    @Test
    fun systemBlurAndPowerEventsDowngradeToAnOpaquePanelAndReleaseTheirListeners() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val power = activity.get().getSystemService(android.os.PowerManager::class.java)
        try {
            DockBackgroundTest.NativeMaterial.googleSupported = true
            val host = FrameLayout(activity.get())
            activity.get().setContentView(host)
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = 3),
                DockBackgroundTest.NativeMaterial::class.java.getConstructor(Context::class.java))
            host.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 360, 80)
            val decoration = host.getChildAt(0) as DockBackgroundView
            val material = decoration.getChildAt(0) as DockBackgroundTest.NativeMaterial
            assertEquals(1, DockWindowManagerShadow.listenerCount())
            assertTrue(material.appliedEnabled)
            val radius = (decoration.foreground as DockBackgroundTint).radius
            fun assertPanel() {
                val bitmap = Bitmap.createBitmap(decoration.width, decoration.height, Bitmap.Config.ARGB_8888)
                decoration.draw(Canvas(bitmap))
                assertEquals(View.INVISIBLE, material.visibility)
                assertFalse(material.appliedEnabled)
                assertEquals(radius, (decoration.foreground as DockBackgroundTint).radius, 0f)
                assertEquals(radius, material.appliedCornerRadius, 0f)
                assertTrue("Every unavailable material must still draw a visible panel",
                    Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 180)
                bitmap.recycle()
            }
            DockWindowManagerShadow.change(false)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertPanel()
            DockWindowManagerShadow.change(true)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(material.appliedEnabled)
            assertEquals(radius, (decoration.foreground as DockBackgroundTint).radius, 0f)
            assertEquals(radius, material.appliedCornerRadius, 0f)
            org.robolectric.Shadows.shadowOf(power).setIsPowerSaveMode(true)
            activity.get().sendBroadcast(android.content.Intent(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertPanel()
            host.removeView(decoration)
            assertEquals(0, DockWindowManagerShadow.listenerCount())
        } finally {
            org.robolectric.Shadows.shadowOf(power).setIsPowerSaveMode(false)
            activity.pause().stop().destroy()
            DockBackgroundTest.NativeMaterial.googleSupported = false
        }
    }

    @Test
    fun glassRimAndDepthLeaveTheLiveSampleVisibleAndCornersTransparent() {
        val tint = DockBackgroundTint(withRim = true).apply {
            color = Color.WHITE
            alpha = 18
            radius = 28f
            rounded = true
            setBounds(0, 0, 320, 100)
        }
        fun render(): Bitmap = Bitmap.createBitmap(320, 100, Bitmap.Config.ARGB_8888).also {
            tint.draw(Canvas(it))
        }
        val light = render()
        assertEquals(0, Color.alpha(light.getPixel(0, 0)))
        assertTrue(Color.alpha(light.getPixel(160, 50)) in 18..30)
        assertTrue(Color.alpha(light.getPixel(160, 0)) > Color.alpha(light.getPixel(160, 50)))
        assertNotEquals(light.getPixel(160, 12), light.getPixel(160, 87))
        tint.color = Color.rgb(24, 26, 30)
        val dark = render()
        assertTrue(Color.red(dark.getPixel(160, 50)) < Color.red(light.getPixel(160, 50)))
        assertEquals("theme color changes preserve opacity", 18, tint.alpha)
        tint.alpha = 0
        val hidden = render()
        assertEquals(0, Color.alpha(hidden.getPixel(160, 50)))
        assertEquals(0, Color.alpha(hidden.getPixel(160, 0)))
    }

    @Test
    fun coatingHasDepthAndEdgeColorWithoutObscuringTheLiveBackground() {
        val tint = DockBackgroundTint(withRim = true).apply {
            color = Color.WHITE
            alpha = 18
            radius = 20f
            rounded = true
            setBounds(0, 0, 320, 80)
        }
        val bitmap = Bitmap.createBitmap(320, 80, Bitmap.Config.ARGB_8888)
        tint.draw(Canvas(bitmap))
        assertTrue(Color.alpha(bitmap.getPixel(160, 40)) in 18..30)
        assertTrue(Color.alpha(bitmap.getPixel(160, 1)) > Color.alpha(bitmap.getPixel(160, 40)))
        assertTrue("directional light reaches the inset G4 bevel", (2..6).any {
            bitmap.getPixel(it, 40) != bitmap.getPixel(319 - it, 40)
        })
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        java.lang.System.getenv("CUSTOMIUIZER_DOCK_PREVIEW")?.let { destination ->
            val preview = Bitmap.createBitmap(720, 360, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(preview)
            val paint = Paint()
            for (y in 0 until 360 step 24) for (x in 0 until 720 step 24) {
                paint.color = if ((x / 24 + y / 24) % 2 == 0) Color.rgb(48, 112, 148) else Color.rgb(130, 172, 159)
                canvas.drawRect(x.toFloat(), y.toFloat(), x + 24f, y + 24f, paint)
            }
            for ((index, dark) in listOf(false, true).withIndex()) {
                tint.setBounds(40, 40 + index * 160, 680, 160 + index * 160)
                tint.radius = 32f
                tint.color = if (dark) Color.rgb(24, 26, 30) else Color.WHITE
                tint.draw(canvas)
            }
            java.io.File(destination).outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
