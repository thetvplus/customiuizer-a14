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
}
