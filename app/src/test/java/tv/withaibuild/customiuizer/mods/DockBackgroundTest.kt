package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.withaibuild.customiuizer.utils.PrefMap
import kotlin.math.PI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockBackgroundTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
    private val host = FrameLayout(activity.get()).apply { clipChildren = false }
    private val materialConstructor = NativeMaterial::class.java.getConstructor(Context::class.java)

    init { activity.get().setContentView(host) }

    @After
    fun close() {
        activity.pause().stop().destroy()
        NativeMaterial.supported = true
        NativeMaterial.failure = null
    }

    @Test
    fun attachmentIsIdempotentAndLeavesNativeContentAndTouchIntact() {
        val icon = View(host.context).apply {
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(48, 48)
        }
        var clicks = 0
        icon.setOnClickListener { clicks++ }
        host.addView(icon)
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 1), null)
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 1), null)
        layout()
        assertEquals(2, host.childCount)
        assertSame(icon, host.getChildAt(1))
        assertFalse(host.clipChildren)
        assertFalse(host.getChildAt(0).isClickable)
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 10, action, 20f, 20f, 0)
            try { host.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, clicks)
        host.alpha = 0.25f
        host.visibility = View.INVISIBLE
        assertSame(host, host.getChildAt(0).parent)
        assertEquals(1f, icon.alpha)
        assertEquals(View.VISIBLE, host.getChildAt(0).visibility)
    }

    @Test
    fun nativeMaterialIsReleasedOnDetachAndRecreatedForTheNewAttachment() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val first = decoration.getChildAt(0) as NativeMaterial
        assertEquals(12, first.appliedBlurRadius)
        assertTrue(first.appliedEnabled)
        assertEquals(0.65f, first.alpha)
        host.removeView(decoration)
        assertNull(first.parent)
        assertNull(first.background)
        assertEquals(0, decoration.childCount)
        host.addView(decoration)
        layout()
        assertEquals(1, decoration.childCount)
        assertNotSame(first, decoration.getChildAt(0))
    }

    @Test
    fun unsupportedOrFailingNativeMaterialFallsBackWithoutLeavingAChild() {
        for (failure in listOf(null, IllegalStateException("material unavailable"))) {
            NativeMaterial.supported = failure != null
            NativeMaterial.failure = failure
            LauncherDockBackground.attach(host, DockBackgroundConfig(), materialConstructor)
            layout()
            val decoration = host.getChildAt(0) as DockBackgroundView
            assertEquals(0, decoration.childCount)
            assertNotNull(decoration.foreground)
            host.removeView(decoration)
        }
    }

    @Test
    fun contentReplacementReleasesTheOldOwnerAndKeepsTheNewIcons() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(), materialConstructor)
        layout()
        val old = host.getChildAt(0) as DockBackgroundView
        val material = old.getChildAt(0)
        host.removeAllViews()
        val replacementIcon = View(host.context)
        host.addView(replacementIcon)
        LauncherDockBackground.attach(host, DockBackgroundConfig(), materialConstructor)
        layout()
        assertNotSame(old, host.getChildAt(0))
        assertSame(replacementIcon, host.getChildAt(1))
        assertEquals(0, old.childCount)
        assertNull(material.parent)
    }

    @Test
    fun drawableRetirementFailureDoesNotPreventNativeOwnerRemoval() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0)
        material.background = object : ColorDrawable(Color.WHITE) {
            override fun setAlpha(alpha: Int) { throw IllegalStateException("old root already retired") }
        }
        host.removeView(decoration)
        assertNull(material.parent)
        assertNull(material.background)
        assertEquals(0, decoration.childCount)
    }

    @Test
    fun wrappedFatalMaterialFailureEscapesTheViewCallbackBoundary() {
        val fatal = OutOfMemoryError("native material")
        NativeMaterial.failure = fatal
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            LauncherDockBackground.attach(host, DockBackgroundConfig(), materialConstructor)
        })
    }

    @Test
    fun zeroOpacityDoesNotCreateNativeMaterial() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(opacity = 0), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        assertEquals(0, decoration.childCount)
        assertEquals(0, decoration.foreground.alpha)
    }

    @Test
    fun g4HeadingIsSymmetricMonotoneAndFlatToFourthOrderAtTheStraightEdge() {
        assertEquals(0.0, DockCornerGeometry.heading(0.0), 0.0)
        assertEquals(PI / 4, DockCornerGeometry.heading(0.5), 1e-12)
        assertEquals(PI / 2, DockCornerGeometry.heading(1.0), 1e-12)
        var previous = 0.0
        for (i in 0..100) {
            val t = i / 100.0
            val heading = DockCornerGeometry.heading(t)
            assertTrue(heading >= previous)
            assertEquals(PI / 2, heading + DockCornerGeometry.heading(1 - t), 1e-12)
            previous = heading
        }
        assertTrue(DockCornerGeometry.heading(0.001) < 1e-10)
    }

    @Test
    fun g4RasterIsSymmetricClampedAndContainsNoCutouts() {
        for (radius in floatArrayOf(0f, 24f, 1000f)) {
            val bitmap = Bitmap.createBitmap(240, 80, Bitmap.Config.ARGB_8888)
            try {
                val nativeShape = DockBackgroundTint().apply {
                    color = Color.WHITE
                    this.radius = radius
                    rounded = true
                    setBounds(0, 0, 240, 80)
                }
                nativeShape.draw(Canvas(bitmap))
                var nativeDelta = 0
                for (y in 0 until 80) for (x in 0 until 240) {
                    val alpha = Color.alpha(bitmap.getPixel(x, y))
                    nativeDelta = maxOf(nativeDelta,
                        kotlin.math.abs(alpha - Color.alpha(bitmap.getPixel(239 - x, y))),
                        kotlin.math.abs(alpha - Color.alpha(bitmap.getPixel(x, 79 - y))))
                }
                bitmap.eraseColor(Color.TRANSPARENT)
                val shape = DockBackgroundTint().apply {
                    color = Color.WHITE
                    this.radius = radius
                    setBounds(0, 0, 240, 80)
                }
                shape.draw(Canvas(bitmap))
                assertEquals(Color.WHITE, bitmap.getPixel(120, 40))
                var g4Delta = 0
                for (y in 0 until 80) {
                    var entered = false
                    var exited = false
                    for (x in 0 until 240) {
                        val alpha = Color.alpha(bitmap.getPixel(x, y))
                        val mirrorX = Color.alpha(bitmap.getPixel(239 - x, y))
                        val mirrorY = Color.alpha(bitmap.getPixel(x, 79 - y))
                        g4Delta = maxOf(g4Delta, kotlin.math.abs(alpha - mirrorX), kotlin.math.abs(alpha - mirrorY))
                        if (alpha > 0) {
                            assertFalse(exited)
                            entered = true
                        } else if (entered) exited = true
                    }
                }
                // Skia's analytic AA is direction-sensitive even for its own round rectangles.
                // Compare against that native baseline rather than assuming exact pixel alpha.
                assertTrue("r=$radius G4 AA difference=$g4Delta native=$nativeDelta", g4Delta <= nativeDelta)
            } finally { bitmap.recycle() }
        }
        val path = Path()
        DockCornerGeometry.build(path, 240f, 80f, 1000f)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        assertEquals(RectF(0f, 0f, 240f, 80f), bounds)
    }

    @Test
    fun invalidPersistedValuesStayWithinTheSupportedGeometryAndOpacity() {
        assertEquals(DockBackgroundConfig(), DockBackgroundConfig.read(PrefMap()))
        val config = DockBackgroundConfig.read(PrefMap().apply {
            put("launcher_dock_background_opacity", -1)
            put("launcher_dock_background_radius", Int.MAX_VALUE)
            put("launcher_dock_background_inset", -20)
        })
        assertEquals(0, config.opacity)
        assertEquals(80, config.radiusDp)
        assertEquals(0, config.sideInsetDp)
    }

    private fun layout() {
        host.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 360, 80)
    }

    /** ROM API fixture: no renderer or hooks are replaced in production. */
    class NativeMaterial(context: Context) : FrameLayout(context) {
        var appliedBlurRadius = 0
        var appliedEnabled = false
        init { background = ColorDrawable(Color.WHITE) }
        fun isBackdropBlurSupported() = supported
        fun isGoogleBlurSupported() = false
        fun setBlurRadius(value: Int) { appliedBlurRadius = value }
        fun setCornerRadius(value: Float) { require(value >= 0f) }
        fun setBlurEnabled(value: Boolean) {
            failure?.let { throw it }
            appliedEnabled = value
        }
        companion object {
            var supported = true
            var failure: Throwable? = null
        }
    }
}
