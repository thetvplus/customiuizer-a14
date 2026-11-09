package tv.withaibuild.customiuizer.mods.media

import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/** Tests the actual presentation owner; does not emulate MediaViewController. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaCardOutlineTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun root() = FrameLayout(context).apply {
        addView(ImageView(context).apply { id = 17 }, FrameLayout.LayoutParams(52, 52))
        measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(168, View.MeasureSpec.EXACTLY))
        layout(0, 0, 320, 168)
    }
    private fun style(root: ViewGroup): Any = Class.forName("tv.withaibuild.customiuizer.mods.media.IosMediaCardHooks\$Style")
        .getDeclaredConstructor(ViewGroup::class.java, Map::class.java).apply { isAccessible = true }
        .newInstance(root, mapOf("album_art" to 17))
    private fun invoke(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)

    @Test fun unchangedOutlinesReuseTheirPathAndSizeChangesReplaceItsGeometry() {
        val root = root(); val art = root.getChildAt(0)
        val owner = style(root)
        invoke(owner, "apply")
        for (view in listOf(root, art)) {
            val provider = view.outlineProvider
            val outline = Outline()
            provider.getOutline(view, outline)
            assertFalse(outline.isEmpty)
            val path = ReflectionHelpers.getField<Path>(provider, "path")
            val before = path.approximate(.1f)
            repeat(100) { provider.getOutline(view, outline) }
            assertSame(path, ReflectionHelpers.getField<Path>(provider, "path"))
            assertArrayEquals(before, path.approximate(.1f), 0f)
            view.layout(0, 0, view.width + 40, view.height + 20)
            provider.getOutline(view, outline)
            val bounds = RectF(); path.computeBounds(bounds, true)
            assertEquals(view.width.toFloat(), bounds.width(), .01f)
            assertEquals(view.height.toFloat(), bounds.height(), .01f)
        }
    }
    @Test fun detachRestoresNativeOutlinesAndLeavesAReplacementOwnerIntact() {
        val root = root(); val art = root.getChildAt(0)
        val rootNative = root.outlineProvider; val artNative = art.outlineProvider
        root.clipToOutline = false; art.clipToOutline = false
        val owner = style(root)
        invoke(owner, "apply")
        assertTrue(root.clipToOutline); assertTrue(art.clipToOutline)
        val listener = owner as View.OnAttachStateChangeListener
        listener.onViewDetachedFromWindow(root)
        assertSame(rootNative, root.outlineProvider); assertSame(artNative, art.outlineProvider)
        assertFalse(root.clipToOutline); assertFalse(art.clipToOutline)
        listener.onViewAttachedToWindow(root)
        val replacement = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRect(0, 0, view.width, view.height)
        }
        root.outlineProvider = replacement
        invoke(owner, "disable")
        assertSame(replacement, root.outlineProvider)
        assertSame(artNative, art.outlineProvider)
        assertFalse(art.clipToOutline)
    }
    @Test fun nativeActionRebindWhileDetachedRestoresTheLatestGlyphOnlyOnReattachment() {
        val root = root()
        val button = android.widget.ImageButton(context).apply { id = 18 }
        val nativePause = android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE)
        button.setImageDrawable(nativePause)
        root.addView(button, FrameLayout.LayoutParams(48, 48))
        val owner = Class.forName("tv.withaibuild.customiuizer.mods.media.IosMediaCardHooks\$Style")
            .getDeclaredConstructor(ViewGroup::class.java, Map::class.java).apply { isAccessible = true }
            .newInstance(root, mapOf("album_art" to 17, "actionPlayPause" to 18))
        val bind = owner.javaClass.getDeclaredMethod("bind", android.widget.ImageButton::class.java, Long::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val listener = owner as View.OnAttachStateChangeListener
        fun pixels(): IntArray {
            val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                button.drawable.setBounds(0, 0, 48, 48)
                button.drawable.draw(android.graphics.Canvas(bitmap))
                return IntArray(48 * 48).also { bitmap.getPixels(it, 0, 48, 0, 0, 48, 48) }
            } finally { bitmap.recycle() }
        }
        try {
            invoke(owner, "apply"); bind.invoke(owner, button, 2L)
            val glyph = button.drawable
            val pause = pixels()
            assertNotSame(nativePause, glyph)
            listener.onViewDetachedFromWindow(root)
            assertSame(nativePause, button.drawable)
            listener.onViewAttachedToWindow(root)
            assertSame("No new native bind is needed after a normal reparent", glyph, button.drawable)
            listener.onViewDetachedFromWindow(root)
            val nativePlay = android.graphics.drawable.ColorDrawable(android.graphics.Color.GREEN)
            button.setImageDrawable(nativePlay)
            var clicks = 0; button.setOnClickListener { clicks++ }
            bind.invoke(owner, button, 4L) // Native async bind finishes while offscreen.
            assertSame("Offscreen bind records semantics without taking ink", nativePlay, button.drawable)
            invoke(owner, "apply"); assertSame(nativePlay, button.drawable)
            listener.onViewAttachedToWindow(root)
            assertSame(glyph, button.drawable)
            assertFalse("Latest play state must replace the old pause silhouette", pause.contentEquals(pixels()))
            button.performClick(); assertEquals(1, clicks)
            listener.onViewDetachedFromWindow(root)
            assertSame("Release restores the latest native icon", nativePlay, button.drawable)
            bind.invoke(owner, button, 0L)
            listener.onViewAttachedToWindow(root)
            assertSame("Unknown action does not reuse old semantics", nativePlay, button.drawable)
            listener.onViewDetachedFromWindow(root)
            bind.invoke(owner, button, 2L)
            val unattributed = android.graphics.drawable.ColorDrawable(android.graphics.Color.YELLOW)
            button.setImageDrawable(unattributed) // A newer native/async owner, without this binding.
            listener.onViewAttachedToWindow(root)
            assertSame(unattributed, button.drawable)
            invoke(owner, "disable")
            bind.invoke(owner, button, 2L); invoke(owner, "apply")
            assertSame("Disabled owners stay passive", unattributed, button.drawable)
        } finally { invoke(owner, "disable") }
    }
    @Test fun lateNativeBindCannotReacquireAppearanceAfterDetachOrDisable() {
        val root = root(); val art = root.getChildAt(0) as ImageView
        art.setPadding(4, 5, 6, 7)
        val nativeOutline = root.outlineProvider
        val owner = style(root)
        val listener = owner as View.OnAttachStateChangeListener
        invoke(owner, "apply")
        assertEquals(0, art.paddingLeft)
        listener.onViewDetachedFromWindow(root)
        assertEquals(4, art.paddingLeft)
        invoke(owner, "apply") // The original bind callback may finish after detach.
        assertSame(nativeOutline, root.outlineProvider)
        assertEquals(4, art.paddingLeft)
        listener.onViewAttachedToWindow(root)
        assertEquals(0, art.paddingLeft)
        invoke(owner, "disable")
        invoke(owner, "apply")
        assertSame(nativeOutline, root.outlineProvider)
        assertEquals(4, art.paddingLeft)
    }
}
