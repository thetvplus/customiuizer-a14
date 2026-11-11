package tv.withaibuild.customiuizer.mods.volume

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers.getField
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w414dp-h736dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicVolumeTest {
    @Test fun sixteenIndependentSegmentsFollowNativeFractionWithoutAMorphClock() {
        val track = IosVolumeStyle.Track(1f, true, true) { _, _, _, _ -> }
        val bitmap = Bitmap.createBitmap(155, 155, Bitmap.Config.ARGB_8888)
        try {
            track.setBounds(0, 0, 155, 155)
            for (pair in listOf(0 to 0, 624 to 0, 625 to 1, 5000 to 8, 9999 to 15, 10000 to 16)) {
                bitmap.eraseColor(Color.TRANSPARENT); track.level = pair.first; track.draw(Canvas(bitmap))
                val actual = (0..15).count { bitmap.getPixel(17 + 8 * it, 128) == 0xff242426.toInt() }
                assertEquals("native progress ${pair.first}", pair.second, actual)
            }
            assertTrue(Color.alpha(bitmap.getPixel(77, 0)) > 0)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            repeat(30) { track.form(7f, 134f, .4, 1.0); track.endpointKick(1f); track.releaseEndpoint() }
            assertFalse(track.isCompact); assertNull(getField<ValueAnimator?>(track, "clock"))
        } finally { track.stop(); bitmap.recycle() }
    }
    @Test fun nativeSeekAndMaterialSurviveClassicCloseAndHostReplacement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val root = FrameLayout(activity); val slider = SeekBar(activity)
            val fill = View(activity); val track = FrameLayout(activity); val icon = ImageView(activity)
            for (view in listOf(slider, fill, track, icon)) root.addView(view, FrameLayout.LayoutParams(58, 170))
            activity.setContentView(root)
            val nativeGlyph = GradientDrawable().apply { setColor(Color.BLACK); setSize(20, 20) }
            icon.setImageDrawable(nativeGlyph)
            val background = ColorDrawable(Color.BLUE); track.background = background
            val progress = slider.progressDrawable; val thumb = slider.thumb
            var changes = 0
            slider.max = 15; slider.progress = 3
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, value: Int, user: Boolean) { changes++ }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
            val binding = IosVolumeStyle.Binding(root, slider, fill, track, icon, startCompact = true, classic = true)
            val paint = getField<IosVolumeStyle.Track>(binding, "paint")
            slider.progress = 9; binding.refresh(); binding.noteInput(); binding.interact(true)
            assertEquals(1, changes); assertEquals(9, slider.progress); assertEquals(6000, paint.level)
            assertEquals(155, slider.layoutParams.width); assertEquals(155, track.layoutParams.height)
            root.layout(240, 90, 298, 273); track.layout(0, 0, 155, 155)
            icon.layout(0, 0, 20, 20)
            assertEquals("Classic glyphs render at their final resolution, without magnifying the View cache", 1f, icon.scaleX, .001f)
            assertEquals(ImageView.ScaleType.MATRIX, icon.scaleType)
            assertSame(nativeGlyph, icon.drawable)
            val matrix = FloatArray(9); icon.imageMatrix.getValues(matrix)
            assertEquals(2.6f, matrix[android.graphics.Matrix.MSCALE_X], .001f)
            val more = ImageView(activity)
            root.addView(more, FrameLayout.LayoutParams(58, 28)); more.layout(0, 155, 58, 183)
            val session = IosVolumeStyle.Session(root, root, null, listOf(binding), { 0 }, {}, {}, more)
            try {
                val region = android.graphics.Region(0, 0, 30, 30)
                session.includeTouchableRegion(region)
                assertFalse("The old untranslated edge cannot remain touchable", region.contains(10, 10))
                val position = IntArray(2); track.getLocationInWindow(position)
                assertTrue(region.contains(position[0] + 77, position[1] + 77))
                assertTrue("A narrow native parent cannot clip the slider touch region", region.contains(position[0] + 154, position[1] + 77))
                more.getLocationInWindow(position)
                assertTrue("The expanded-controls footer stays reachable", region.contains(position[0] + 57, position[1] + 27))
                assertEquals(48.5f, more.translationX, .01f)
            } finally { session.close() }
            assertEquals(0f, more.translationX, 0f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1400))
            assertNull(getField<ValueAnimator?>(paint, "clock"))
            binding.close(); binding.close()
            assertSame(background, track.background); assertSame(progress, slider.progressDrawable); assertSame(thumb, slider.thumb)
            assertSame(nativeGlyph, icon.drawable)
            assertEquals(ImageView.ScaleType.FIT_CENTER, icon.scaleType)
            assertEquals(58, slider.layoutParams.width); assertEquals(170, track.layoutParams.height)
            val replacement = IosVolumeStyle.Binding(root, slider, fill, track, icon, classic = true)
            replacement.close(); assertSame(background, track.background)
        } finally { activity.finish() }
    }
    @Test fun centerTracksTheNativeWindowAndReleasesOnlyItsOwnTranslation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val outer = FrameLayout(activity); val root = FrameLayout(activity); val track = View(activity)
            outer.addView(root, FrameLayout.LayoutParams(155, 155)); root.addView(track, FrameLayout.LayoutParams(155, 155))
            activity.setContentView(outer)
            activity.window.decorView.layout(0, 0, 400, 800); outer.layout(0, 0, 400, 800)
            root.layout(240, 90, 395, 245); track.layout(0, 0, 155, 155)
            val placement = ClassicVolumePlacement(root, track)
            try {
                placement.update()
                val location = IntArray(2); track.getLocationInWindow(location)
                assertEquals(200f, location[0] + 77.5f, 1f); assertEquals(400f, location[1] + 77.5f, 1f)
                val x = root.translationX; val y = root.translationY
                placement.update(); assertEquals(x, root.translationX, 0f); assertEquals(y, root.translationY, 0f)
                root.translationX = 80f // native show callback rewrites the side position
                placement.onPreDraw(); assertEquals(x, root.translationX, 1f)
                root.translationY = 123f // a newer owner must not be overwritten on retirement
                placement.close(); assertEquals(123f, root.translationY, 0f)
                assertEquals("Expansion must resume the latest native position, not the attachment snapshot", 80f, root.translationX, 0f)
            } finally { placement.close() }
        } finally { activity.finish() }
    }
}
