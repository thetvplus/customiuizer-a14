package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers.callInstanceMethod

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandSystemIconsTest {
    @Test fun nativeTransparentPaddingCannotChangeTheVisibleGlyphSizeOrItsAspectRatio() {
        fun native(size: Int, ink: android.graphics.Rect) = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
            override fun getIntrinsicWidth() = size
            override fun getIntrinsicHeight() = size
            override fun draw(canvas: android.graphics.Canvas) {
                val save = canvas.save()
                canvas.scale(bounds.width().toFloat() / size, bounds.height().toFloat() / size)
                canvas.drawRect(ink, paint)
                canvas.restoreToCount(save)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        for (source in listOf(native(130, android.graphics.Rect(39, 39, 93, 93)),
            native(108, android.graphics.Rect(26, 14, 82, 94)))) {
            val ratio = if (source.intrinsicWidth == 130) 1f else 56f / 80
            val normalized = IslandSystemIcons.normalize(source)
            normalized.setBounds(0, 0, 72, 72)
            val pixels = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
            normalized.draw(android.graphics.Canvas(pixels))
            val ink = android.graphics.Rect(72, 72, 0, 0)
            for (y in 0 until 72) for (x in 0 until 72) {
                if (pixels.getPixel(x, y) ushr 24 > 0) ink.union(x, y, x + 1, y + 1)
            }
            assertEquals(60f, maxOf(ink.width(), ink.height()).toFloat(), 2f)
            assertEquals(ratio, ink.width().toFloat() / ink.height(), .04f)
            assertEquals(36f, ink.exactCenterX(), 1f); assertEquals(36f, ink.exactCenterY(), 1f)
            pixels.recycle()
        }
    }

    @Test fun invalidStatesCannotSelectAnIconAndFallbackInstancesDoNotShareMutableState() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        for (kind in IslandSystemKind.entries) {
            assertNull(IslandSystemIcons.resourceName(kind, -1))
            assertNull(IslandSystemIcons.load(activity, kind, 99))
        }
        val first = IslandSystemIcons.load(activity, IslandSystemKind.DND, 1)!!
        val next = IslandSystemIcons.load(activity, IslandSystemKind.DND, 1)!!
        assertNotSame(first, next)
        first.alpha = 37
        first.setBounds(4, 5, 6, 7)
        assertEquals(255, next.alpha)
        assertTrue(next.bounds.isEmpty)
        activity.finish()
    }

    @Test fun aWholeNativeGlyphFitsItsCellAndWaitsUntilItClearsTheCamera() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity)
        activity.setContentView(shell, FrameLayout.LayoutParams(184, 40))
        val glyph = BitmapDrawable(activity.resources, Bitmap.createBitmap(60, 20, Bitmap.Config.ARGB_8888))
        shell.bind("Enabled", "Bluetooth", glyph, false, false, false, 0, false, status = true)
        shell.geometry(184, 40, 40, 240)
        shell.measure(View.MeasureSpec.makeMeasureSpec(184, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        shell.layout(0, 0, 184, 40)
        val left = shell.getChildAt(0) as android.view.ViewGroup
        val icon = left.getChildAt(0) as ImageView
        assertEquals(ImageView.ScaleType.FIT_CENTER, icon.scaleType)
        assertFalse(icon.clipToOutline)
        val pixels = RectF(0f, 0f, 60f, 20f)
        icon.imageMatrix.mapRect(pixels)
        assertTrue(pixels.left >= 0 && pixels.top >= 0 && pixels.right <= 24 && pixels.bottom <= 24)
        assertEquals(24f, pixels.width(), .001f)
        assertEquals(8f, pixels.height(), .001f)
        shell.scaleX = 64f / 184
        callInstanceMethod<Any?>(shell, "updateContour")
        assertEquals(0f, left.alpha, .001f)
        shell.scaleX = 1f
        callInstanceMethod<Any?>(shell, "updateContour")
        assertEquals(1f, left.alpha, .001f)
        activity.finish()
    }
    @Test fun nativeFeedbackResourcesTakePriorityOverQuickSettingsResources() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requested = ArrayList<String>()
        val pluginResources = object : android.content.res.Resources(activity.assets,
            activity.resources.displayMetrics, activity.resources.configuration) {
            override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
                assertEquals("miui.systemui.plugin", defPackage)
                requested.add(name!!)
                return android.R.drawable.sym_def_app_icon
            }
            override fun getDrawable(id: Int, theme: android.content.res.Resources.Theme?) =
                android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
        }
        val plugin = object : android.content.ContextWrapper(activity) {
            override fun getResources() = pluginResources
        }
        val host = object : android.content.ContextWrapper(activity) {
            override fun createPackageContext(packageName: String?, flags: Int): android.content.Context {
                assertEquals("miui.systemui.plugin", packageName); assertEquals(0, flags)
                return plugin
            }
            override fun getResources(): android.content.res.Resources = error("Must select native feedback first")
        }
        assertNotNull(IslandSystemIcons.load(host, IslandSystemKind.DND, 1))
        assertNotNull(IslandSystemIcons.load(host, IslandSystemKind.RINGER, android.media.AudioManager.RINGER_MODE_NORMAL))
        assertEquals(listOf("miui_statusbar_dnd_mode_on", "miui_statuebar_silent_mode_off"), requested)
        val broken = object : android.content.ContextWrapper(activity) {
            override fun createPackageContext(packageName: String?, flags: Int): android.content.Context = throw OutOfMemoryError("fatal")
        }
        assertThrows(OutOfMemoryError::class.java) { IslandSystemIcons.load(broken, IslandSystemKind.DND, 1) }
        activity.finish()
    }

}
