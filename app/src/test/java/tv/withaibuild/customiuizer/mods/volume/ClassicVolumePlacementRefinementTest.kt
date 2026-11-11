package tv.withaibuild.customiuizer.mods.volume

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClassicVolumePlacementRefinementTest {
    @Test fun widthRelativeOffsetClampsToInsetsAndTheNativeFooter() {
        assertEquals(400f, ClassicVolumePlacement.verticalCenter(24f, 776f, 155f, 155f, 28f, 0), 0f)
        assertEquals(555f, ClassicVolumePlacement.verticalCenter(24f, 776f, 155f, 155f, 28f, 100), 0f)
        assertEquals(245f, ClassicVolumePlacement.verticalCenter(24f, 776f, 155f, 155f, 28f, -100), 0f)
        assertEquals(555f, ClassicVolumePlacement.verticalCenter(24f, 776f, 155f, 155f, 28f, Int.MAX_VALUE), 0f)
        assertEquals(166.5f, ClassicVolumePlacement.verticalCenter(30f, 272f, 155f, 155f, 28f, 100), 0f)
        assertEquals(107.5f, ClassicVolumePlacement.verticalCenter(30f, 272f, 155f, 155f, 28f, -100), 0f)
        assertEquals(95f, ClassicVolumePlacement.verticalCenter(30f, 160f, 155f, 155f, 28f, 100), 0f)
    }

    @Test fun samePlacementRecomputesWhenTheWindowChangesAndRestoresTheLatestNativePose() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val outer = FrameLayout(activity); val root = FrameLayout(activity); val track = View(activity)
            outer.addView(root, FrameLayout.LayoutParams(155, 155))
            root.addView(track, FrameLayout.LayoutParams(155, 155))
            activity.setContentView(outer)
            fun layoutWindow(width: Int, height: Int) {
                val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                val decor = activity.window.decorView
                decor.measure(widthSpec, heightSpec); decor.layout(0, 0, width, height)
                outer.measure(widthSpec, heightSpec); outer.layout(0, 0, width, height)
                assertEquals(height, decor.height)
                assertEquals(155, track.width); assertEquals(155, track.height)
            }
            layoutWindow(400, 800)
            root.layout(240, 90, 395, 245); track.layout(0, 0, 155, 155)
            val placement = ClassicVolumePlacement(root, track, 100)
            try {
                val position = IntArray(2)
                placement.update(); track.getLocationInWindow(position)
                assertEquals(555f, position[1] + 77.5f, 1f)
                val stable = root.translationY
                repeat(10) { placement.update() }
                assertEquals(stable, root.translationY, 0f)
                // FrameLayout lays children from measured dimensions. A bare
                // layout pass reverts these previously unmeasured children to 0x0,
                // causing placement's correct not-ready guard to retain the old pose.
                layoutWindow(800, 300)
                placement.update(); track.getLocationInWindow(position)
                assertEquals(222.5f, position[1] + 77.5f, 1f)
                root.translationY = 31f
                placement.update(); placement.close()
                assertEquals(31f, root.translationY, 0f)
            } finally { placement.close() }
        } finally { activity.finish() }
    }
}
