package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.RelativeLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrongToastBaselineTest {
    @Test fun nativeOutlineAndClipOwnershipRestoreExactlyToTheirCapturedValues() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = android.widget.LinearLayout(activity)
        val capsule = FrameLayout(activity)
        root.addView(capsule, android.widget.LinearLayout.LayoutParams(200, 40))
        val original = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) { outline.setEmpty() }
        }
        capsule.outlineProvider = original
        capsule.clipToOutline = true
        val baseline = SystemUIStrongToastHooks.captureMatchModeBaseline(capsule, root, null)!!
        capsule.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        capsule.clipToOutline = false
        capsule.layoutParams.height = 1
        SystemUIStrongToastHooks.restoreMatchModeBaseline(root, capsule, root, null, baseline)
        assertSame(original, capsule.outlineProvider)
        assertTrue(capsule.clipToOutline)
        assertEquals(40, capsule.layoutParams.height)
        activity.finish()
    }
}
