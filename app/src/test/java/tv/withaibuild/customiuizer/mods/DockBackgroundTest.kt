package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
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
import kotlin.math.roundToInt

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
        NativeMaterial.googleSupported = false
        BlendLayer.failure = null
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
        assertEquals(0.9f, first.alpha)
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
    fun gaussianUsesNativeBackgroundBlurAndRetiresOnlyItsGlassBlendLayers() {
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        assertEquals((28 * host.resources.displayMetrics.density).roundToInt(), material.appliedBlurRadius)
        assertFalse(material.mBlurLayerHolder.enabled)
        assertTrue(material.appliedEnabled)
        assertEquals(0.9f, material.alpha)
        host.removeView(decoration)
        host.addView(decoration)
        layout()
        val replacement = decoration.getChildAt(0) as NativeMaterial
        assertNotSame(material, replacement)
        assertFalse(replacement.mBlurLayerHolder.enabled)
        assertTrue(replacement.appliedEnabled)
    }

    @Test
    fun optionalGlassBlendFailureKeepsTheNativeBlur() {
        NativeMaterial.googleSupported = true
        BlendLayer.failure = IllegalStateException("different SDK blend holder")
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        assertTrue((decoration.getChildAt(0) as NativeMaterial).appliedEnabled)
    }

    @Test
    fun fatalGlassBlendFailureIsNotSwallowed() {
        NativeMaterial.googleSupported = true
        val fatal = OutOfMemoryError("native blend")
        BlendLayer.failure = fatal
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        })
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
        assertEquals(65, DockBackgroundConfig.read(PrefMap().apply {
            put("launcher_dock_background_opacity", 65)
        }).opacity) // explicit existing choices survive a default change
        assertEquals(2, DockBackgroundConfig.read(PrefMap().apply {
            put("launcher_dock_background_material", "2")
        }).material)
    }

    @Test
    fun automaticCornersFollowTheDeviceBottomRadiusAndInsetWithoutHeightRatios() {
        assertEquals(64f, DockCornerGeometry.radius(80f, 16f, 0f, 28f, 100f), 0f)
        assertEquals(64f, DockCornerGeometry.radius(80f, 16f, 0f, 28f, 140f), 0f)
        assertEquals(40f, DockCornerGeometry.radius(80f, 16f, 0f, 28f, 40f), 0f)
        assertEquals(0f, DockCornerGeometry.radius(10f, 16f, 0f, 28f, 40f), 0f)
        assertEquals(24f, DockCornerGeometry.radius(80f, 16f, 24f, 28f, 40f), 0f)
        assertEquals(28f, DockCornerGeometry.radius(0f, 16f, 0f, 28f, 40f), 0f)
    }

    private fun nativeHost(hidden: Boolean = true): HotSeatsFixture {
        val native = HotSeatsFixture(host.context)
        val icon = ItemIconFixture(host.context).apply { mIsHideTitle = hidden }
        native.mContent.addView(icon, FrameLayout.LayoutParams(100, 80))
        native.addView(native.mContent, FrameLayout.LayoutParams(-1, -1))
        host.addView(native, FrameLayout.LayoutParams(360, 80))
        return native
    }

    private fun attachNative(native: HotSeatsFixture, material: Int = 1): DockBackgroundView {
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = material, sideInsetDp = 12),
            if (material == 1) null else materialConstructor,
            DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java))
        layout()
        native.viewTreeObserver.dispatchOnPreDraw()
        return native.getChildAt(0) as DockBackgroundView
    }

    @Test
    fun nativeIconsAreCenteredWithoutChangingTheirOwnAnimationPropertiesOrTouch() {
        val native = nativeHost()
        layout()
        val icon = native.mContent.getChildAt(0) as ItemIconFixture
        assertEquals(30, icon.mIconImageView.bottom / 2 + icon.mIconImageView.top / 2)
        val row = native.mContent
        row.alpha = 0.8f
        val decoration = attachNative(native)
        assertEquals(10, row.top) // hidden title space formerly left the icon 10px above center
        assertEquals(0.8f, row.alpha)
        assertEquals(0f, row.translationY)
        assertEquals(1f, row.scaleY)
        var clicks = 0
        icon.mIconImageView.setOnClickListener { clicks++ }
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 10, action, 24f, 40f, 0)
            try { native.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, clicks)
        repeat(100) { decoration.scheduleGeometry() }
        native.viewTreeObserver.dispatchOnPreDraw()
        repeat(100) { native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(10, row.top)
        assertEquals(1, row.positionUpdates)
        native.removeView(decoration)
        assertEquals(0, row.top)
    }

    @Test
    fun visibleTitlesAndParentPaddingParticipateInTheActualCenteringBounds() {
        val native = nativeHost(hidden = false)
        native.setPadding(0, 4, 0, 8)
        val decoration = attachNative(native)
        val bounds = Rect()
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        access.bounds(native, native.mContent, bounds, Rect())
        assertTrue(kotlin.math.abs((decoration.top + decoration.bottom) - (bounds.top + bounds.bottom)) <= 1)
        assertTrue(bounds.top >= decoration.top)
        assertTrue(bounds.bottom <= decoration.bottom)
    }

    @Test
    fun nativeRelayoutAndDetachRestoreOnlyTheOwnedOffset() {
        val native = nativeHost()
        val decoration = attachNative(native)
        val row = native.mContent
        row.layout(0, 3, 360, 83) // a new native base position, independent of our previous offset
        // The native row listener schedules realignment without a new parent measurement.
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(10, row.top)
        native.removeView(decoration)
        assertEquals(3, row.top)
        // A pending one-shot callback must not keep or move a detached owner.
        decoration.scheduleGeometry()
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(3, row.top)
    }

    @Test
    fun animationTransformsAndTransientMissingInsetsCannotFlattenTheDock() {
        val native = nativeHost()
        val decoration = attachNative(native, material = 0)
        host.rootView.layout(0, 0, 360, 80)
        val insets = WindowInsets.Builder().setRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT,
            RoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT, 100, 100, 100)).build()
        decoration.onApplyWindowInsets(insets)
        native.viewTreeObserver.dispatchOnPreDraw()
        decoration.onApplyWindowInsets(insets)
        val radius = (decoration.foreground as DockBackgroundTint).radius
        assertEquals(40f, radius)
        val material = decoration.getChildAt(0) as NativeMaterial
        val params = decoration.layoutParams as FrameLayout.LayoutParams
        val margin = params.leftMargin
        for (offset in floatArrayOf(-400f, 120f, 0f)) {
            native.translationY = offset
            native.translationX = 70f
            native.scaleX = 0.8f
            native.scaleY = 0.8f
            decoration.scheduleGeometry()
            native.viewTreeObserver.dispatchOnPreDraw()
            decoration.onApplyWindowInsets(insets)
            assertEquals(radius, material.appliedCornerRadius)
            decoration.onApplyWindowInsets(WindowInsets.Builder().build())
            assertEquals(radius, (decoration.foreground as DockBackgroundTint).radius)
            assertEquals(margin, params.leftMargin)
            assertEquals(margin, params.rightMargin)
            assertEquals(10, native.mContent.top)
        }
    }

    @Test
    fun automaticSideSpacingStaysNarrowWhenBottomSpacingChanges() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 1), null)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val expected = (12 * host.resources.displayMetrics.density).roundToInt()
        for (bottom in intArrayOf(80, 500)) {
            host.rootView.layout(0, 0, 360, bottom)
            decoration.scheduleGeometry()
            host.viewTreeObserver.dispatchOnPreDraw()
            val params = decoration.layoutParams as FrameLayout.LayoutParams
            assertEquals(expected, params.leftMargin)
            assertEquals(expected, params.rightMargin)
        }
    }

    @Test
    fun replacementContentRetiresTheOldOffsetAndCentersOnlyTheNewNativeRow() {
        val native = nativeHost()
        val decoration = attachNative(native)
        val old = native.mContent
        native.removeView(old)
        native.mContent = ContentFixture(host.context).apply {
            addView(ItemIconFixture(context), FrameLayout.LayoutParams(100, 80))
        }
        native.addView(native.mContent, FrameLayout.LayoutParams(-1, -1))
        layout()
        decoration.scheduleGeometry()
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(0, old.top)
        assertEquals(10, native.mContent.top)
        val updates = native.mContent.positionUpdates
        old.layout(0, 20, 360, 100)
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(updates, native.mContent.positionUpdates)
        native.removeView(decoration)
        assertEquals(0, native.mContent.top)
    }

    @Test
    fun nativePositionCallbackFailureRestoresTheRowAndStopsRetrying() {
        val native = nativeHost()
        native.mContent.failure = IllegalStateException("native content retired")
        val decoration = attachNative(native)
        assertEquals(0, native.mContent.top)
        val attempts = native.mContent.positionUpdates
        repeat(10) { decoration.scheduleGeometry(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(attempts, native.mContent.positionUpdates)
        assertEquals(View.VISIBLE, decoration.visibility)
    }

    class HotSeatsFixture(context: Context) : FrameLayout(context) {
        @JvmField var mContent = ContentFixture(context)
    }

    class ContentFixture(context: Context) : FrameLayout(context) {
        var positionUpdates = 0
        var failure: Throwable? = null
        fun onTranslationChanged() { positionUpdates++; failure?.let { throw it } }
    }

    class ItemIconFixture(context: Context) : FrameLayout(context) {
        @JvmField val mIconImageView = View(context)
        @JvmField val mTitleContainer = View(context)
        @JvmField var mIsHideTitle = true
        init {
            addView(mIconImageView, LayoutParams(48, 48).apply { topMargin = 6 })
            addView(mTitleContainer, LayoutParams(80, 12).apply { topMargin = 56 })
        }
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
        var appliedCornerRadius = 0f
        @JvmField val mBlurLayerHolder = BlendLayer()
        init { background = ColorDrawable(Color.WHITE) }
        fun isBackdropBlurSupported() = supported
        fun isGoogleBlurSupported() = googleSupported
        fun setBlurRadius(value: Int) { appliedBlurRadius = value }
        fun setCornerRadius(value: Float) { require(value >= 0f); appliedCornerRadius = value }
        fun setBlurEnabled(value: Boolean) {
            failure?.let { throw it }
            appliedEnabled = value
        }
        companion object {
            var supported = true
            var failure: Throwable? = null
            var googleSupported = false
        }
    }

    class BlendLayer {
        var enabled = true
            private set
        fun setEnable(value: Boolean) {
            failure?.let { throw it }
            enabled = value
        }
        companion object { var failure: Throwable? = null }
    }
}
