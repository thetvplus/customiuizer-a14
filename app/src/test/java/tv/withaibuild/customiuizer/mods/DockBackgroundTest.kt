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
        LauncherDockBackground.attach(host, DockBackgroundConfig(), null)
        LauncherDockBackground.attach(host, DockBackgroundConfig(), null)
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
    fun hiddenHotseatRetiresNativeBlurAndRestoresItWithoutRecreatingTheMaterial() {
        for (style in intArrayOf(0, 2)) {
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = style), materialConstructor)
            layout()
            val decoration = host.getChildAt(0) as DockBackgroundView
            val material = decoration.getChildAt(0) as NativeMaterial
            val drawable = material.background
            host.visibility = View.INVISIBLE // native AlphaUpdateListener at the end of Recents entry
            decoration.onVisibilityAggregated(false)
            assertEquals(0, drawable.alpha)
            repeat(10) { decoration.scheduleGeometry(); host.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals(0, drawable.alpha)
            host.alpha = 0f // native return animation makes the host visible before alpha increases
            host.visibility = View.VISIBLE
            decoration.onVisibilityAggregated(true)
            host.alpha = 1f
            assertEquals(230, drawable.alpha)
            assertSame(material, decoration.getChildAt(0))
            host.removeView(decoration)
            assertNull(material.background)
        }
    }

    @Test
    fun materialAttachedWhileHiddenRestoresItsPathAfterAHiddenResize() {
        host.visibility = View.INVISIBLE
        LauncherDockBackground.attach(host, DockBackgroundConfig(), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        assertEquals(0, material.background.alpha)
        // AOSP BackgroundBlurDrawable omits its path when bounds/corners change at alpha == 0.
        val drawable = object : ColorDrawable(Color.WHITE) {
            var pathReady = false
            override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
                super.setBounds(left, top, right, bottom)
                pathReady = alpha != 0 && right > left && bottom > top
            }
        }
        drawable.alpha = 0
        material.background = drawable
        drawable.setBounds(0, 0, 280, 72)
        assertFalse(drawable.pathReady)
        host.visibility = View.VISIBLE
        decoration.onVisibilityAggregated(true)
        assertEquals(230, drawable.alpha)
        assertTrue(drawable.pathReady)
    }

    @Test
    fun overviewRetiresBothBlurBackendsWhileNativeAncestorsRemainVisible() {
        for (google in booleanArrayOf(false, true)) {
            NativeMaterial.googleSupported = google
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
            layout()
            val decoration = host.getChildAt(0) as DockBackgroundView
            val material = decoration.getChildAt(0) as NativeMaterial
            val drawable = material.background
            host.alpha = 0f // full-screen gesture fades ShortcutMenuLayer without hiding HotSeats
            assertTrue(decoration.isShown)
            // The native overview state must explicitly hide only our decoration.
            decoration.visibility = View.INVISIBLE
            decoration.onVisibilityAggregated(false)
            assertEquals(View.VISIBLE, host.visibility)
            assertEquals(0, drawable.alpha)
            assertFalse(material.appliedEnabled)
            host.alpha = 1f
            decoration.visibility = View.VISIBLE
            decoration.onVisibilityAggregated(true)
            assertTrue(material.appliedEnabled)
            assertEquals((35 * host.resources.displayMetrics.density).roundToInt(), material.appliedBlurRadius)
            if (google) assertFalse(material.mBlurLayerHolder.enabled)
            assertSame(drawable, material.background)
            assertSame(material, decoration.getChildAt(0))
            host.removeView(decoration)
        }
    }

    @Test
    fun attachmentDuringOverviewAndRepeatedVisibilityEventsReuseTheMaterialAndRadius() {
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor, visible = false)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        decoration.onVisibilityAggregated(false)
        assertEquals(View.INVISIBLE, decoration.visibility)
        assertFalse(material.appliedEnabled)
        assertNull(material.background)
        val toggles = material.enableCalls
        repeat(100) { decoration.onVisibilityAggregated(false); decoration.scheduleGeometry() }
        assertEquals(toggles, material.enableCalls)
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        decoration.onVisibilityAggregated(true)
        assertSame(material, decoration.getChildAt(0))
        assertTrue(material.appliedEnabled)
        assertEquals((35 * host.resources.displayMetrics.density).roundToInt(), material.appliedBlurRadius)
        assertFalse(material.mBlurLayerHolder.enabled)
        val activeCalls = material.enableCalls
        repeat(100) { decoration.onVisibilityAggregated(true) }
        assertEquals(activeCalls, material.enableCalls)
        assertEquals(1, host.childCount)
    }

    @Test
    fun visibilityBackendFailureRetiresTheNativeOwnerAndDoesNotRetryEachFrame() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        NativeMaterial.failure = IllegalStateException("retired native blur binder")
        decoration.visibility = View.INVISIBLE
        decoration.onVisibilityAggregated(false)
        assertEquals(0, decoration.childCount)
        assertNull(material.parent)
        assertNull(material.background)
        val attempts = material.enableCalls
        decoration.visibility = View.VISIBLE
        repeat(20) { decoration.onVisibilityAggregated(true); host.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(attempts, material.enableCalls)
        assertFalse((decoration.foreground as DockBackgroundTint).rounded)
    }

    @Test
    fun gaussianUsesNativeBackgroundBlurAndRetiresOnlyItsGlassBlendLayers() {
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        assertEquals((35 * host.resources.displayMetrics.density).roundToInt(), material.appliedBlurRadius)
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
        assertEquals(0, DockBackgroundConfig.read(PrefMap().apply {
            put("launcher_dock_background_material", "1")
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

    private fun attachNative(native: HotSeatsFixture, withMaterial: Boolean = false): DockBackgroundView {
        LauncherDockBackground.attach(native, DockBackgroundConfig(sideInsetDp = 12),
            if (withMaterial) materialConstructor else null,
            DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java))
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        return native.getChildAt(0) as DockBackgroundView
    }

    @Test
    fun nativeIconsAreCenteredWithoutChangingTheirFramesAnimationPropertiesOrTouch() {
        val native = nativeHost()
        layout()
        val icon = native.mContent.getChildAt(0) as ItemIconFixture
        assertEquals(30, icon.mIconImageView.bottom / 2 + icon.mIconImageView.top / 2)
        val row = native.mContent
        row.alpha = 0.8f
        val decoration = attachNative(native)
        assertEquals(0, row.top)
        assertEquals(0f, row.translationY)
        assertEquals(0.8f, row.alpha)
        assertEquals(0f, icon.translationY)
        assertEquals(1f, icon.scaleY)
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
        assertEquals(0, row.top)
        assertEquals(0f, row.translationY)
        assertEquals(0, row.positionUpdates)
        native.removeView(decoration)
        assertEquals(0, row.top)
        assertEquals(0f, row.translationY)
    }

    @Test
    fun visibleTitlesAndParentPaddingParticipateInTheActualCenteringBounds() {
        val native = nativeHost(hidden = false)
        native.setPadding(0, 4, 0, 8)
        val decoration = attachNative(native)
        val bounds = Rect()
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        access.bounds(native, native.mContent, bounds, Rect())
        assertOpticalCenter(decoration, bounds)
        assertTrue(bounds.top >= decoration.top)
        assertTrue(bounds.bottom <= decoration.bottom)
    }

    @Test
    fun repeatedNativeLayoutsDoNotDisableTheNextIconReorderAnimation() {
        val native = nativeHost()
        layout()
        val row = native.mContent
        val nativeFrameChanges = row.frameChanges
        val decoration = attachNative(native)
        repeat(5) {
            row.requestLayout()
            layout()
            decoration.scheduleGeometry()
            native.viewTreeObserver.dispatchOnPreDraw()
        }
        // ROM HotSeatsScreenViewContent.setFrame skips icon animation whenever this changes.
        assertEquals(nativeFrameChanges, row.frameChanges)
        assertEquals(0, row.top)
        assertEquals(0f, row.translationY)
    }

    @Test
    fun returnedAppIconSharesTheNativeAnimationLandingCoordinates() {
        val native = nativeHost()
        val decoration = attachNative(native)
        val row = native.mContent
        val icon = (row.getChildAt(0) as ItemIconFixture).mIconImageView
        val nativeLanding = Rect()
        icon.getDrawingRect(nativeLanding)
        native.offsetDescendantRectToMyCoords(icon, nativeLanding)
        // ROM FloatingIconView2 computes this target with matrix mapping disabled.
        assertEquals(nativeLanding.exactCenterY(), nativeLanding.exactCenterY() + row.translationY, 0f)
        repeat(20) {
            row.translationY = 0f // native animation releases its floating icon at the layout target
            decoration.scheduleGeometry()
            native.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(0f, row.translationY)
        }
        assertEquals(0, row.positionUpdates)
    }

    @Test
    fun nativeAnimationTranslationIsNeverOverwrittenByGeometryOrDetach() {
        val native = nativeHost()
        val row = native.mContent
        row.translationY = 2.5f
        var decoration = attachNative(native)
        assertEquals(2.5f, row.translationY)
        repeat(20) { decoration.scheduleGeometry(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(2.5f, row.translationY)
        native.removeView(decoration)
        assertEquals(2.5f, row.translationY)
        decoration = attachNative(native)
        row.translationY = -3f
        decoration.scheduleGeometry()
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(-3f, row.translationY)
        native.removeView(decoration)
        assertEquals(-3f, row.translationY)
    }

    @Test
    fun nativeRelayoutRepositionsOnlyTheBackgroundAndReleasesTheRowListenerOnDetach() {
        val native = nativeHost()
        val decoration = attachNative(native)
        val row = native.mContent
        row.layout(0, 3, 360, 83) // a new native base position, independent of our previous offset
        // The native row listener schedules realignment without a new parent measurement.
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(3, row.top)
        assertEquals(0f, row.translationY)
        native.removeView(decoration)
        assertEquals(3, row.top)
        assertEquals(0f, row.translationY)
        // A pending one-shot callback must not keep or move a detached owner.
        decoration.scheduleGeometry()
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(3, row.top)
    }

    @Test
    fun animationTransformsAndTransientMissingInsetsCannotFlattenTheDock() {
        val native = nativeHost()
        val decoration = attachNative(native, withMaterial = true)
        host.rootView.layout(0, 0, 360, 80)
        val insets = WindowInsets.Builder().setRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT,
            RoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT, 100, 100, 100)).build()
        decoration.onApplyWindowInsets(insets)
        native.viewTreeObserver.dispatchOnPreDraw()
        decoration.onApplyWindowInsets(insets)
        layout()
        native.viewTreeObserver.dispatchOnPreDraw()
        val radius = (decoration.foreground as DockBackgroundTint).radius
        assertEquals(minOf(decoration.width, decoration.height) * 0.5f, radius)
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
            assertEquals(0, native.mContent.top)
            assertEquals(0f, native.mContent.translationY)
        }
    }

    @Test
    fun automaticSideSpacingStaysNarrowWhenBottomSpacingChanges() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(), null)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val expected = (14 * host.resources.displayMetrics.density).roundToInt()
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
    fun raisingTheDockForNativeIconCenteringDoesNotFlattenDeviceCorners() {
        val native = nativeHost()
        val decoration = attachNative(native, withMaterial = true)
        host.rootView.layout(0, 0, 360, 900)
        val insets = WindowInsets.Builder().setRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT,
            RoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT, 100, 100, 100)).build()
        val material = decoration.getChildAt(0) as NativeMaterial
        for (top in intArrayOf(650, 420)) {
            native.layout(0, top, 360, top + 80)
            decoration.onApplyWindowInsets(insets)
            native.viewTreeObserver.dispatchOnPreDraw()
            val expected = minOf(100f - decoration.left, minOf(decoration.width, decoration.height) * 0.5f)
            assertTrue(expected > 0f)
            assertEquals(expected, (decoration.foreground as DockBackgroundTint).radius, 0f)
            assertEquals(expected, material.appliedCornerRadius, 0f)
            val outline = android.graphics.Outline()
            material.outlineProvider.getOutline(material, outline)
            assertEquals(expected, outline.radius, 0f)
            assertEquals(0f, native.mContent.translationY)
        }
    }

    @Test
    fun gaussianRadiusIsOneAndAQuarterOfThePrevious28DpOnAttachAndResume() {
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        val expected = (35 * host.resources.displayMetrics.density).roundToInt()
        assertEquals(expected, material.appliedBlurRadius)
        decoration.visibility = View.INVISIBLE
        decoration.onVisibilityAggregated(false)
        assertEquals(0, material.appliedBlurRadius)
        decoration.visibility = View.VISIBLE
        decoration.onVisibilityAggregated(true)
        assertEquals(expected, material.appliedBlurRadius)
        assertSame(material, decoration.getChildAt(0))
    }

    @Test
    fun signedShrinkAdjustsAllFourEdgesEquallyAndPreservesNativeIconCenters() {
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        var baseline = Rect()
        for (amount in intArrayOf(0, 1, -4, 4, -12, 12)) {
            val native = nativeHost()
            val row = native.mContent
            row.removeAllViews()
            repeat(4) { index ->
                val item = ItemIconFixture(host.context)
                (item.mIconImageView.layoutParams as FrameLayout.LayoutParams).leftMargin = 21
                row.addView(item, FrameLayout.LayoutParams(90, 80).apply { leftMargin = index * 90 })
            }
            LauncherDockBackground.attach(native, DockBackgroundConfig(sideInsetDp = 12, shrinkDp = amount), null, access)
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            val decoration = native.getChildAt(0) as DockBackgroundView
            val panel = Rect(decoration.left, decoration.top, decoration.right, decoration.bottom)
            if (amount == 0) baseline = panel
            val applied = panel.left - baseline.left
            assertEquals(applied, baseline.right - panel.right)
            assertEquals(applied, panel.top - baseline.top)
            assertEquals(applied, baseline.bottom - panel.bottom)
            assertEquals(baseline.exactCenterX(), panel.exactCenterX(), 0f)
            assertEquals(baseline.exactCenterY(), panel.exactCenterY(), 0f)
            if (amount == 1) assertEquals(host.resources.displayMetrics.density.roundToInt(), applied)
            if (amount < 0) assertTrue(applied < 0)
            if (amount > 0) assertTrue(applied > 0)
            val icons = Rect()
            access.bounds(native, row, icons, Rect())
            assertOpticalCenter(decoration, icons)
            assertTrue(panel.contains(icons))
            assertEquals(0f, row.translationY)
            assertEquals(0, row.positionUpdates)
            repeat(4) { index ->
                val item = row.getChildAt(index) as ItemIconFixture
                assertEquals(index * 90, item.left)
                assertEquals(48, item.mIconImageView.width)
                assertEquals(0f, item.translationY)
            }
            repeat(30) { decoration.scheduleGeometry(); native.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals(panel, Rect(decoration.left, decoration.top, decoration.right, decoration.bottom))
            native.removeView(decoration)
            host.removeView(native)
        }
        assertEquals(1, DockBackgroundConfig.read(PrefMap()).shrinkDp)
        for (stored in intArrayOf(-100, 0, 12, 13, 24, 100)) {
            assertEquals(stored.coerceIn(0, 24) - 12, DockBackgroundConfig.read(PrefMap().apply {
                put("launcher_dock_background_shrink", stored)
            }).shrinkDp)
        }
    }

    @Test
    fun compactBackgroundFitsFourFiveAndSixNativeIconsWithoutResizingThemOrBreathingDuringDrag() {
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        for (count in 4..6) {
            val native = nativeHost()
            val row = native.mContent
            row.removeAllViews()
            val cellWidth = 360 / count
            repeat(count) { index ->
                val item = ItemIconFixture(host.context)
                (item.mIconImageView.layoutParams as FrameLayout.LayoutParams).leftMargin = (cellWidth - 48) / 2
                row.addView(item, FrameLayout.LayoutParams(cellWidth, 80).apply { leftMargin = index * cellWidth })
            }
            LauncherDockBackground.attach(native, DockBackgroundConfig(), null, access)
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            val decoration = native.getChildAt(0) as DockBackgroundView
            val params = decoration.layoutParams as FrameLayout.LayoutParams
            val bounds = Rect()
            access.bounds(native, row, bounds, Rect())
            assertTrue(decoration.height < native.height)
            assertOpticalCenter(decoration, bounds)
            assertTrue(params.leftMargin <= bounds.left)
            assertTrue(params.rightMargin <= native.width - bounds.right)
            assertEquals(77, decoration.foreground.alpha)
            repeat(count) { index ->
                val item = row.getChildAt(index) as ItemIconFixture
                assertEquals(index * cellWidth, item.left)
                assertEquals(48, item.mIconImageView.width)
                assertEquals(1f, item.mIconImageView.scaleX)
                assertEquals(1f, item.mIconImageView.scaleY)
            }
            val sideInset = params.leftMargin
            val verticalInset = params.topMargin
            val bottomInset = params.bottomMargin
            val translation = row.translationY
            native.mDragController.mDragging = true
            row.getChildAt(0).visibility = View.INVISIBLE
            row.getChildAt(count - 1).visibility = View.INVISIBLE
            repeat(10) { decoration.scheduleGeometry(); native.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals(sideInset, params.leftMargin)
            assertEquals(verticalInset, params.topMargin)
            assertEquals(bottomInset, params.bottomMargin)
            assertEquals(translation, row.translationY)
            assertEquals(0, row.top)
            native.mDragController.mDragging = false
            row.getChildAt(0).visibility = View.VISIBLE
            row.getChildAt(count - 1).visibility = View.VISIBLE
            decoration.scheduleGeometry()
            repeat(2) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals(sideInset, params.leftMargin)
            native.removeView(decoration)
            assertEquals(0f, row.translationY)
            host.removeView(native)
        }
    }

    @Test
    fun hiddenFloatingIconSourcesAndIdleFramesDoNotChangeGeometryOrRewriteNativeState() {
        val native = nativeHost()
        val decoration = attachNative(native, withMaterial = true)
        val row = native.mContent
        val item = row.getChildAt(0) as ItemIconFixture
        val material = decoration.getChildAt(0) as NativeMaterial
        val bounds = Rect(decoration.left, decoration.top, decoration.right, decoration.bottom)
        item.visibility = View.INVISIBLE
        item.mIconImageView.alpha = 0f
        decoration.scheduleGeometry()
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(bounds, Rect(decoration.left, decoration.top, decoration.right, decoration.bottom))
        assertEquals(0f, row.translationY)
        val reads = item.mIconImageView.boundsReads
        val toggles = material.enableCalls
        val corners = material.cornerCalls
        repeat(100) { native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(reads, item.mIconImageView.boundsReads)
        assertEquals(toggles, material.enableCalls)
        assertEquals(corners, material.cornerCalls)
        assertFalse(native.isLayoutRequested)
        assertEquals(0, row.positionUpdates)
    }

    @Test
    fun clippingHostConstrainsOnlyItsBackgroundAndPreservesNativeLayout() {
        val native = nativeHost()
        native.clipChildren = true
        native.clipToPadding = true
        val decoration = attachNative(native)
        val bounds = Rect()
        DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
            .bounds(native, native.mContent, bounds, Rect())
        assertOpticalCenter(decoration, bounds)
        assertTrue(decoration.top >= native.paddingTop)
        assertTrue(decoration.bottom <= native.height - native.paddingBottom)
        assertEquals(0f, native.mContent.translationY)
        assertTrue(native.clipChildren)
        assertTrue(native.clipToPadding)
    }

    @Test
    fun replacementContentRetiresTheOldListenerAndTracksOnlyTheNewNativeRow() {
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
        assertEquals(0f, old.translationY)
        assertEquals(0, native.mContent.top)
        assertEquals(0f, native.mContent.translationY)
        val reads = (native.mContent.getChildAt(0) as ItemIconFixture).mIconImageView.boundsReads
        old.layout(0, 20, 360, 100)
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(reads, (native.mContent.getChildAt(0) as ItemIconFixture).mIconImageView.boundsReads)
        native.removeView(decoration)
        assertEquals(0, native.mContent.top)
        assertEquals(0f, native.mContent.translationY)
    }

    @Test
    fun nativeBoundsFailureStopsRetryingAndLeavesNativeContentIntact() {
        val native = nativeHost()
        val icon = (native.mContent.getChildAt(0) as ItemIconFixture).mIconImageView
        icon.boundsFailure = IllegalStateException("native content retired")
        val decoration = attachNative(native)
        assertEquals(0, native.mContent.top)
        val attempts = icon.boundsReads
        repeat(10) { decoration.scheduleGeometry(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(attempts, icon.boundsReads)
        assertEquals(0f, native.mContent.translationY)
        assertEquals(View.VISIBLE, decoration.visibility)
    }

    private fun assertOpticalCenter(decoration: DockBackgroundView, bounds: Rect) {
        val upper = bounds.top - decoration.top
        val lower = decoration.bottom - bounds.bottom
        val trim = minOf(lower, (0.5f * host.resources.displayMetrics.density).roundToInt().coerceAtLeast(1))
        assertTrue(upper >= 0)
        assertTrue(lower >= 0)
        assertEquals(trim, lower - upper)
    }

    class HotSeatsFixture(context: Context) : FrameLayout(context) {
        @JvmField var mContent = ContentFixture(context)
        @JvmField val mDragController = DragControllerFixture()
        init {
            // ROM layout_hotseats.xml: the native row and its background may overflow this frame.
            clipChildren = false
            clipToPadding = false
        }
    }

    class DragControllerFixture { @JvmField var mDragging = false }

    class ContentFixture(context: Context) : FrameLayout(context) {
        var positionUpdates = 0
        var frameChanges = 0
        fun onTranslationChanged() { positionUpdates++ }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            if (changed) frameChanges++
            super.onLayout(changed, left, top, right, bottom)
        }
    }

    class ItemIconFixture(context: Context) : FrameLayout(context) {
        @JvmField val mIconImageView = NativeIcon(context)
        @JvmField val mTitleContainer = View(context)
        @JvmField var mIsHideTitle = true
        init {
            addView(mIconImageView, LayoutParams(48, 48).apply { topMargin = 6 })
            addView(mTitleContainer, LayoutParams(80, 12).apply { topMargin = 56 })
        }
    }

    class NativeIcon(context: Context) : View(context) {
        var boundsReads = 0
        var boundsFailure: Throwable? = null
        override fun getDrawingRect(outRect: Rect) {
            boundsReads++
            boundsFailure?.let { throw it }
            super.getDrawingRect(outRect)
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
        var enableCalls = 0
        var cornerCalls = 0
        @JvmField val mBlurLayerHolder = BlendLayer()
        init { background = ColorDrawable(Color.WHITE) }
        private var retainedBackground = background
        fun isBackdropBlurSupported() = supported
        fun isGoogleBlurSupported() = googleSupported
        fun setBlurRadius(value: Int) { appliedBlurRadius = value }
        fun setCornerRadius(value: Float) { require(value >= 0f); appliedCornerRadius = value; cornerCalls++ }
        fun setBlurEnabled(value: Boolean) {
            enableCalls++
            failure?.let { throw it }
            appliedEnabled = value
            if (googleSupported) {
                mBlurLayerHolder.enabled = value // native SDK call, distinct from reflective blend setup
                if (value) background = retainedBackground else {
                    retainedBackground = background ?: retainedBackground
                    appliedBlurRadius = 0
                    background = null
                }
            }
        }
        companion object {
            var supported = true
            var failure: Throwable? = null
            var googleSupported = false
        }
    }

    class BlendLayer {
        var enabled = true
        fun setEnable(value: Boolean) {
            failure?.let { throw it }
            enabled = value
        }
        companion object { var failure: Throwable? = null }
    }
}
