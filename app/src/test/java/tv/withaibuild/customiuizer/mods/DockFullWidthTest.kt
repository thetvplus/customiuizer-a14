package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp")
class DockFullWidthTest {
    @Test fun stripOwnsOnlyTheBackgroundAndTracksItsNativeHostUntilDetach() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val root = FrameLayout(activity.get())
        activity.get().setContentView(root)
        val host = FrameLayout(activity.get())
        val icon = View(activity.get())
        host.addView(icon, FrameLayout.LayoutParams(48, 48).apply { leftMargin = 20; topMargin = 10 })
        root.addView(host, FrameLayout.LayoutParams(320, 80).apply { leftMargin = 20; topMargin = 600 })
        val windowRoot = host.rootView as FrameLayout
        fun layout() {
            windowRoot.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            windowRoot.layout(0, 0, 360, 800)
        }
        layout()
        LauncherDockBackground.attach(host, DockBackgroundConfig(fullWidth = true), null)
        val layer = (0 until windowRoot.childCount).map { windowRoot.getChildAt(it) }.filterIsInstance<DockFullWidthContainer>().single()
        val strip = layer.getChildAt(0) as DockBackgroundView
        strip.onPreDraw(); layout(); strip.onPreDraw(); layout()
        val params = strip.layoutParams as FrameLayout.LayoutParams
        assertEquals(windowRoot.width, params.width)
        assertEquals(0, params.leftMargin)
        assertEquals(windowRoot.height, params.topMargin + params.height)
        assertEquals(1, host.childCount); assertSame(icon, host.getChildAt(0))
        assertEquals(20, icon.left); assertEquals(10, icon.top)
        assertFalse(strip.isClickable); assertFalse(strip.isFocusable)
        host.alpha = 0.4f; strip.onPreDraw()
        assertEquals(0.4f, layer.alpha, 0.001f)
        assertEquals(1f, strip.alpha, 0.001f)
        host.visibility = View.INVISIBLE; strip.onPreDraw()
        assertEquals(0f, layer.alpha, 0.001f)
        root.removeView(host)
        assertNull(strip.parent)
        assertNull(layer.parent)
        activity.pause().stop().destroy()
    }
}
