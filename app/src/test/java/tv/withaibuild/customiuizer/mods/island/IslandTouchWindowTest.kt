package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandTouchWindowTest {
    @Test fun boundedWindowAcceptsRealEdgeStartsAndReleaseWithoutAnyMove() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val shell = IslandActivityView(host).apply { simplified = true }
            val root = IslandTouchWindow(shell)
            host.setContentView(root)
            var dismissals = 0; var opens = 0
            shell.onDismiss = { dismissals++ }; shell.onOpen = { opens++ }
            fun px(dp: Int) = (dp * host.resources.displayMetrics.density + .5f).toInt()
            val width = px(180)
            for (alignment in 0..2) for (heightDp in listOf(38, 40, 44)) {
                val height = px(heightDp)
                shell.geometry(width, height, if (alignment == 1) px(40) else 0, alignment = alignment)
                root.geometry(width, height)
                layout(root, width + 2 * root.margin, height + 2 * root.margin)
                val slop = ViewConfiguration.get(host).scaledTouchSlop
                val x = root.margin + px(4).toFloat() // Begin beside the rounded end cap, in the real padded window.
                val y = root.margin + height + px(4).toFloat()
                touch(root, MotionEvent.ACTION_DOWN, x, y, 0)
                touch(root, MotionEvent.ACTION_UP, x, y - slop * 1.8f, 500)
            }
            assertEquals(9, dismissals)
            assertEquals(0, opens)
            assertFalse(touch(root, MotionEvent.ACTION_DOWN, -20f, 20f, 0))
            val box = root.hitBounds()
            assertTrue(box.width <= width + 2 * root.margin)
        } finally { host.finish() }
    }

    @Test @Config(qualifiers = "xxhdpi")
    fun highDensityEdgesRetainTheSamePhysicalGestureContract() = boundedWindowAcceptsRealEdgeStartsAndReleaseWithoutAnyMove()

    @Test fun changingDisplayDensityRefreshesTheProductionInputMarginAndThreshold() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val shell = IslandActivityView(host)
            val root = IslandTouchWindow(shell)
            val previous = root.margin
            org.robolectric.RuntimeEnvironment.setQualifiers("xxhdpi")
            shell.refreshConfiguration(); root.refreshConfiguration()
            assertTrue(host.resources.displayMetrics.density > 1f)
            assertTrue(root.margin > previous)
            assertEquals(root.margin, shell.touchMargin)
            assertEquals(ViewConfiguration.get(host).scaledTouchSlop,
                org.robolectric.util.ReflectionHelpers.getField<Int>(shell, "slop"))
        } finally { host.finish() }
    }

    @Test fun windowManagerReceivesOnlyTheExpandedAnimatedCapsuleRegion() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val shell = IslandActivityView(host)
            val root = IslandTouchWindow(shell)
            host.setContentView(root)
            shell.geometry(240, 40, 0, alignment = 2)
            root.geometry(240, 40); layout(root, 240 + 2 * root.margin, 40 + 2 * root.margin)
            shell.scaleX = .5f
            val type = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val info = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            val method = android.view.ViewTreeObserver::class.java.getDeclaredMethod("dispatchOnComputeInternalInsets", type)
                .apply { isAccessible = true }
            method.invoke(root.viewTreeObserver, info)
            val region = type.getField("touchableRegion").get(info) as android.graphics.Region
            val box = root.hitBounds()
            assertEquals(android.graphics.Rect(box.left, box.top, box.right, box.bottom), region.bounds)
            assertTrue(region.bounds.width() < root.width)
        } finally { host.finish() }
    }

    @Test fun animationHitRegionFollowsTheChosenEdgeAndDoesNotClaimAStatusBarBand() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val shell = IslandActivityView(host)
            val root = IslandTouchWindow(shell)
            host.setContentView(root)
            for (alignment in listOf(0, 2)) {
                shell.geometry(240, 40, 0, alignment = alignment)
                root.geometry(240, 40); layout(root, 240 + 2 * root.margin, 40 + 2 * root.margin)
                shell.scaleX = .25f
                val box = root.hitBounds()
                assertTrue(box.width <= 60 + 2 * root.margin)
                if (alignment == 0) assertEquals(0, box.left)
                else assertEquals(root.width, box.right)
            }
        } finally { host.finish() }
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
    private fun touch(view: View, action: Int, x: Float, y: Float, elapsed: Long): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now + elapsed, action, x, y, 0)
        return try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }
}
