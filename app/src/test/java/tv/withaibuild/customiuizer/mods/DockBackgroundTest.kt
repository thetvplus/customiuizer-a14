package tv.withaibuild.customiuizer.mods

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Path
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp",
    shadows = [DockWindowManagerShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockBackgroundTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
    private val host = FrameLayout(activity.get()).apply { clipChildren = false }
    private val materialConstructor = NativeMaterial::class.java.getConstructor(Context::class.java)

    init { activity.get().setContentView(host) }

    @Test fun nativeActivationWaitsForStableGeometryAndPublishesReusedBoundsBeforeVisibility() {
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor, visualEvents = true)
        val panel = host.getChildAt(0) as DockBackgroundView
        val material = panel.getChildAt(0) as NativeMaterial
        assertFalse("Attachment only prepares the native instance", material.appliedEnabled)
        measureAndLayout()
        assertFalse("Layout callbacks cannot eagerly activate blur", material.appliedEnabled)
        assertFalse("The first pass requests the final panel layout", panel.onPreDraw())
        assertFalse(material.appliedEnabled)
        repeat(4) { layout(); host.viewTreeObserver.dispatchOnPreDraw() }
        assertTrue(material.appliedEnabled)
        val drawable = object : ColorDrawable(Color.WHITE) {
            var boundsWrites = 0
            override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
                boundsWrites++
                super.setBounds(left, top, right, bottom)
            }
        }
        material.background = drawable
        panel.scheduleVisualSync(); host.viewTreeObserver.dispatchOnPreDraw()
        panel.setSceneVisible(false)
        assertFalse(material.appliedEnabled)
        assertEquals(0, drawable.alpha)
        panel.setSceneVisible(true)
        repeat(20) { panel.onVisibilityAggregated(true) }
        assertFalse("Visibility only requests a commit", material.appliedEnabled)
        repeat(4) {
            host.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 320, 80); host.viewTreeObserver.dispatchOnPreDraw()
        }
        assertTrue(material.appliedEnabled)
        assertSame(material, panel.getChildAt(0))
        assertSame("The native SDK already retains its Drawable", drawable, material.background)
        assertEquals(Rect(0, 0, material.width, material.height), drawable.bounds)
        assertEquals(View.VISIBLE, material.visibility)
        val enables = material.enableCalls
        val corners = material.cornerCalls
        val bounds = drawable.boundsWrites
        repeat(120) { panel.scheduleVisualSync(); host.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(enables, material.enableCalls)
        assertEquals(corners, material.cornerCalls)
        assertEquals(bounds, drawable.boundsWrites)
        assertNull(ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
        repeat(100) { panel.onApplyWindowInsets(android.view.WindowInsets.Builder().build()) }
        assertNull("Unchanged Insets do not start geometry work", ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
    }

    @Test fun nativeVisualEventsReleasePreDrawAtRestAndWakeOnlyForChanges() {
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor, visualEvents = true)
        repeat(5) { layout(); host.viewTreeObserver.dispatchOnPreDraw() }
        val panel = host.getChildAt(0) as DockBackgroundView
        val material = panel.getChildAt(0) as NativeMaterial
        assertNull(ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
        val submissions = material.alphaSubmissions
        repeat(120) { host.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(submissions, material.alphaSubmissions)
        panel.alpha = .5f
        assertNotNull(ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(115, material.writtenAlpha)
        assertNull(ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
        host.alpha = .4f
        panel.scheduleVisualSync() // The scoped native scene callback, not a global View hook.
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(46, material.writtenAlpha)
        assertNull(ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
    }

    @Test fun systemBlurOffUsesOnlyTheTintAndRestoresNativeMaterialOnTheEvent() {
        DockWindowManagerShadow.change(false)
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor, visualEvents = true)
        repeat(4) { layout(); host.viewTreeObserver.dispatchOnPreDraw() }
        val panel = host.getChildAt(0) as DockBackgroundView
        assertEquals(0, panel.childCount)
        assertEquals(1, DockWindowManagerShadow.listenerCount())
        DockWindowManagerShadow.change(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, panel.childCount)
        val material = panel.getChildAt(0) as NativeMaterial
        DockWindowManagerShadow.change(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, panel.childCount)
        assertFalse(material.appliedEnabled)
        host.removeView(panel)
        assertEquals(0, DockWindowManagerShadow.listenerCount())
    }

    @Test fun materialEnabledAfterParentLayoutCommitsWhenItsOwnLayoutArrives() {
        DockWindowManagerShadow.change(false)
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor, visualEvents = true)
        repeat(4) { layout(); host.viewTreeObserver.dispatchOnPreDraw() }
        val panel = host.getChildAt(0) as DockBackgroundView
        // Policy can arrive between the parent's layout and PreDraw. The new child
        // has not been measured yet, while the parent's geometry is already final.
        DockWindowManagerShadow.change(true)
        XposedHelpers.callMethod(panel, "refreshBlurPolicy")
        val material = panel.getChildAt(0) as NativeMaterial
        assertEquals(0, material.width)
        host.viewTreeObserver.dispatchOnPreDraw()
        assertFalse(material.appliedEnabled)
        measureAndLayout()
        host.viewTreeObserver.dispatchOnPreDraw()
        assertTrue("The child's settled layout must wake the finite commit", material.appliedEnabled)
        assertEquals(Rect(0, 0, panel.width, panel.height), material.background.bounds)
        assertNull("No continuous sampling after activation", ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
    }

    @Test fun transparentVisibleHotseatRetiresItsGoogleRegionAndRestoresOnTheNextNativeEvent() {
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor, visualEvents = true)
        repeat(4) { layout(); host.viewTreeObserver.dispatchOnPreDraw() }
        val panel = host.getChildAt(0) as DockBackgroundView
        val material = panel.getChildAt(0) as NativeMaterial
        host.alpha = 0f // Recents can leave HotSeats VISIBLE after its native fade.
        panel.scheduleVisualSync(); host.viewTreeObserver.dispatchOnPreDraw()
        assertFalse(material.appliedEnabled)
        assertEquals(View.INVISIBLE, material.visibility)
        assertNull(ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
        val submissions = material.alphaSubmissions
        repeat(120) { host.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(submissions, material.alphaSubmissions)
        host.alpha = .4f
        panel.scheduleVisualSync(); host.viewTreeObserver.dispatchOnPreDraw()
        assertTrue(material.appliedEnabled)
        assertEquals(92, material.writtenAlpha)
        assertSame(material, panel.getChildAt(0))
        assertNull(ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
    }

    @After
    fun close() {
        activity.pause().stop().destroy()
        android.provider.Settings.Global.putInt(host.context.contentResolver, "disable_window_blurs", 0)
        NativeMaterial.supported = true
        NativeMaterial.failure = null
        NativeMaterial.googleSupported = false
        NativeMaterial.inheritedAlphaGetter = false
        BlendLayer.failure = null
        DockWindowManagerShadow.resetBlur()
    }

    @Test
    fun fullOpacityRestoresAfterUnlockWithTheNativeInheritedAlphaGetter() {
        NativeMaterial.googleSupported = true
        NativeMaterial.inheritedAlphaGetter = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2, opacity = 100), materialConstructor)
        layout(); host.viewTreeObserver.dispatchOnPreDraw()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        fun window(value: Int) = ReflectionHelpers.callInstanceMethod<Unit>(decoration,
            "onWindowVisibilityChanged", ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, value))
        repeat(3) {
            window(View.GONE)
            assertEquals(0, material.writtenAlpha)
            window(View.VISIBLE)
            host.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("The framework getter cannot verify its private blur alpha", 255, material.background.alpha)
            assertEquals("Unlock must issue the opaque write even when getAlpha already returns 255", 255, material.writtenAlpha)
            val writes = material.alphaSubmissions
            repeat(120) { decoration.onPreDraw() }
            assertEquals("Settled frames do not resubmit alpha", writes, material.alphaSubmissions)
        }
        host.removeView(decoration)
        assertNull(ReflectionHelpers.getField<Any?>(decoration, "opacityDrawable"))
    }

    @Test
    fun nativeWindowVisibilityRestoresBlurWithoutAnAggregatedVisibilityNotification() {
        NativeMaterial.googleSupported = true
        for (style in listOf(2)) {
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = style), materialConstructor)
            layout(); host.viewTreeObserver.dispatchOnPreDraw()
            val decoration = host.getChildAt(0) as DockBackgroundView
            val material = decoration.getChildAt(0) as NativeMaterial
            val radius = material.appliedBlurRadius
            assertTrue(material.appliedEnabled)
            fun windowVisibility(value: Int) = ReflectionHelpers.callInstanceMethod<Unit>(decoration,
                "onWindowVisibilityChanged", ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, value))
            windowVisibility(View.GONE)
            assertFalse("A hidden window must release its native blur", material.appliedEnabled)
            assertNull(material.background)
            windowVisibility(View.VISIBLE)
            host.viewTreeObserver.dispatchOnPreDraw()
            assertTrue(material.appliedEnabled)
            assertEquals(radius, material.appliedBlurRadius)
            assertNotNull(material.background)
            val calls = material.enableCalls
            repeat(20) { windowVisibility(View.VISIBLE); host.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals("Repeated visible callbacks cannot re-register blur", calls, material.enableCalls)
            host.removeView(decoration)
            assertFalse(material.appliedEnabled)
            val detachedCalls = material.enableCalls
            windowVisibility(View.VISIBLE)
            assertEquals("A detached owner cannot restore blur", detachedCalls, material.enableCalls)
            assertNull(material.parent)
        }
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
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val first = decoration.getChildAt(0) as NativeMaterial
        assertEquals((35 * host.resources.displayMetrics.density).roundToInt(), first.appliedBlurRadius)
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
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
            layout()
            val decoration = host.getChildAt(0) as DockBackgroundView
            assertEquals(0, decoration.childCount)
            assertNotNull(decoration.foreground)
            host.removeView(decoration)
        }
    }

    @Test
    fun contentReplacementReleasesTheOldOwnerAndKeepsTheNewIcons() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val old = host.getChildAt(0) as DockBackgroundView
        val material = old.getChildAt(0)
        host.removeAllViews()
        val replacementIcon = View(host.context)
        host.addView(replacementIcon)
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        assertNotSame(old, host.getChildAt(0))
        assertSame(replacementIcon, host.getChildAt(1))
        assertEquals(0, old.childCount)
        assertNull(material.parent)
    }

    @Test
    fun drawableRetirementFailureDoesNotPreventNativeOwnerRemoval() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
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
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        })
    }

    @Test
    fun solidPanelNeverCreatesBlurOrRegistersBlurPolicyListeners() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 0), materialConstructor, visualEvents = true)
        repeat(5) { layout(); host.viewTreeObserver.dispatchOnPreDraw() }
        val panel = host.getChildAt(0) as DockBackgroundView
        assertEquals(0, panel.childCount)
        assertEquals(0, DockWindowManagerShadow.listenerCount())
        assertNull(ReflectionHelpers.getField<Any?>(panel, "pendingObserver"))
        assertTrue(panel.foreground.alpha > 0)
        panel.onVisibilityAggregated(false)
        panel.onVisibilityAggregated(true)
        assertEquals(0, panel.childCount)
    }

    @Test
    fun powerSavingRetiresBlurAndRestoresItOnTheSystemEvent() {
        val power = activity.get().getSystemService(android.os.PowerManager::class.java)
        val shadow = shadowOf(power)
        try {
            LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor, visualEvents = true)
            repeat(5) { layout(); host.viewTreeObserver.dispatchOnPreDraw() }
            val panel = host.getChildAt(0) as DockBackgroundView
            val old = panel.getChildAt(0) as NativeMaterial
            shadow.setIsPowerSaveMode(true)
            activity.get().sendBroadcast(android.content.Intent(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, panel.childCount)
            assertFalse(old.appliedEnabled)
            shadow.setIsPowerSaveMode(false)
            activity.get().sendBroadcast(android.content.Intent(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, panel.childCount)
            assertNotSame(old, panel.getChildAt(0))
        } finally { shadow.setIsPowerSaveMode(false) }
    }

    @Test
    fun zeroOpacityDoesNotCreateNativeMaterial() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2, opacity = 0), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        assertEquals(0, decoration.childCount)
        assertEquals(0, decoration.foreground.alpha)
    }

    @Test
    fun hiddenHotseatRetiresNativeBlurAndRestoresItWithoutRecreatingTheMaterial() {
        for (style in intArrayOf(2)) {
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
            layout()
            assertEquals(230, drawable.alpha)
            assertSame(material, decoration.getChildAt(0))
            host.removeView(decoration)
            assertNull(material.background)
        }
    }

    @Test
    fun materialAttachedWhileHiddenRestoresItsPathAfterAHiddenResize() {
        host.visibility = View.INVISIBLE
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
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
        layout()
        assertEquals(230, drawable.alpha)
        assertTrue(drawable.pathReady)
        assertEquals(Rect(0, 0, material.width, material.height), drawable.bounds)
    }

    @Test
    fun folderFadeAndInterruptedRecentsFollowNativeAlphaWithoutWaitingForAnEndEvent() {
        for (google in booleanArrayOf(false, true)) {
            NativeMaterial.googleSupported = google
            val native = nativeHost()
            LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2), materialConstructor,
                DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java))
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            val decoration = native.getChildAt(0) as DockBackgroundView
            val material = decoration.getChildAt(0) as NativeMaterial
            val icon = (native.mContent.getChildAt(0) as ItemIconFixture).mIconImageView
            val reads = icon.boundsReads
            val toggles = material.enableCalls
            val bounds = Rect(decoration.left, decoration.top, decoration.right, decoration.bottom)
            // Folder open/close, reversal while the finger remains down, then rapid cancellation.
            for (alpha in floatArrayOf(1f, .6f, 0f, .1f, .8f, .3f, 0f, 1f)) {
                native.alpha = alpha
                native.viewTreeObserver.dispatchOnPreDraw()
                assertEquals(View.VISIBLE, decoration.visibility)
                if (google && alpha > 0f) assertEquals((255 * .9f * alpha).roundToInt(), material.background.alpha)
                if (google && alpha == 0f) assertNull("The zero-alpha region must be removed", material.background)
                if (google) assertEquals(alpha > 0f, material.appliedEnabled)
                assertEquals(bounds, Rect(decoration.left, decoration.top, decoration.right, decoration.bottom))
            }
            assertEquals(reads, icon.boundsReads)
            assertEquals(toggles + if (google) 4 else 0, material.enableCalls)
            assertSame(material, decoration.getChildAt(0))
            native.removeView(decoration)
            host.removeView(native)
        }
    }

    @Test
    fun effectiveBlurOpacityIncludesAncestorsAndRetiredViewsStopObserving() {
        NativeMaterial.googleSupported = true
        val native = nativeHost()
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2), materialConstructor)
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        val decoration = native.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        val drawable = object : ColorDrawable(Color.WHITE) {
            var writes = 0
            private var opacity = 255
            override fun getAlpha() = opacity
            override fun setAlpha(value: Int) { writes++; opacity = value; super.setAlpha(value) }
        }
        material.background = drawable
        native.alpha = .5f
        host.alpha = .4f
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals((255 * .9f * .5f * .4f).roundToInt(), drawable.alpha)
        val writes = drawable.writes
        repeat(100) { native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(writes, drawable.writes)
        native.removeView(decoration)
        val retiredWrites = drawable.writes
        native.alpha = 1f
        host.alpha = 1f
        repeat(10) { native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(retiredWrites, drawable.writes)
        assertNull(material.background)
    }

    @Test
    fun editingBlurUsesTheExactNativeTransformationWithoutAdvancingOrReplacingItsAnimation() {
        NativeMaterial.googleSupported = true
        val native = nativeHost()
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2), materialConstructor)
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        val decoration = native.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        val animation = CountingEditingAnimation().apply {
            addAnimation(android.view.animation.TranslateAnimation(0f, 0f, 40f, 0f))
            addAnimation(android.view.animation.AlphaAnimation(0f, 1f))
            duration = 370
        }
        var starts = 0
        var ends = 0
        animation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation) { starts++ }
            override fun onAnimationEnd(animation: android.view.animation.Animation) { ends++ }
            override fun onAnimationRepeat(animation: android.view.animation.Animation) { error("Unexpected repeat") }
        })
        native.startAnimation(animation)
        decoration.bindEditingAnimation(animation, showing = true)
        assertEquals(0, material.background.alpha)
        val transformation = android.view.animation.Transformation()
        for (time in longArrayOf(0, 185, 370, 371)) {
            val running = animation.getTransformation(time, transformation)
            decoration.onEditingFrame(animation, transformation.alpha, running)
            assertEquals((255 * .9f * transformation.alpha).roundToInt(), material.background.alpha)
        }
        assertEquals(4, animation.frames)
        assertEquals(1, starts)
        assertEquals(1, ends)
        assertSame(animation, native.animation)
    }

    @Test
    fun anUnownedNativeAnimationDoesNotReadTransformationArguments() {
        val animation = android.view.animation.AnimationSet(false)
        var calls = 0; var reads = 0; var receiverReads = 0
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
            when (method.name) {
                "proceed" -> { assertTrue(args.isNullOrEmpty()); calls++; true }
                "getThisObject" -> { receiverReads++; animation }
                "getArg" -> { reads++; android.view.animation.Transformation() }
                else -> error("Unexpected chain read: ${method.name}")
            }
        } as XposedInterface.Chain
        val hook = LauncherDockBackground.editingTransformationHook()
        repeat(120) { assertEquals(true, hook.intercept(chain)) }
        assertEquals(120, calls)
        assertEquals(0, reads)
        assertEquals("An idle Dock must not look up any animation owner", 0, receiverReads)
    }

    @Test fun editingOwnerHandoffIgnoresStaleReleaseAndDoesNotUseGlobalAdditionalFields() {
        NativeMaterial.googleSupported = true
        val native = nativeHost()
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = native.getChildAt(0) as DockBackgroundView
        val animation = CountingEditingAnimation()
        native.startAnimation(animation)
        decoration.bindEditingAnimation(animation, showing = true)
        assertNull("Dock frame ownership must not occupy the shared field table",
            XposedHelpers.getAdditionalInstanceField(animation, "customiuizer_dock_editing_owner"))
        val other = DockBackgroundView(activity.get(), DockBackgroundConfig(), null)
        LauncherDockBackground.clearEditingOwner(animation, other)
        val output = android.view.animation.Transformation().apply { alpha = .4f }
        var reads = 0
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "proceed" -> true
                "getThisObject" -> animation
                "getArg" -> { reads++; output }
                else -> error("Unexpected chain read: ${method.name}")
            }
        } as XposedInterface.Chain
        val hook = LauncherDockBackground.editingTransformationHook()
        hook.intercept(chain)
        assertEquals(1, reads)
        assertEquals(92, decoration.getChildAt(0).background.alpha)
        native.removeView(decoration) // Detach must also remove the frame owner.
        hook.intercept(chain)
        assertEquals(1, reads)
    }

    @Test
    fun releasingOneEditingOwnerPreservesOtherAnimationsAndTheLastReleaseReturnsToIdle() {
        val first = android.view.animation.AnimationSet(false)
        val second = android.view.animation.AnimationSet(false)
        val firstOwner = DockBackgroundView(activity.get(), DockBackgroundConfig(), null)
        val secondOwner = DockBackgroundView(activity.get(), DockBackgroundConfig(), null)
        var current = first
        var reads = 0; var receiverReads = 0; var calls = 0
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "proceed" -> { calls++; false }
                "getThisObject" -> { receiverReads++; current }
                "getArg" -> { reads++; android.view.animation.Transformation() }
                else -> error("Unexpected chain read: ${method.name}")
            }
        } as XposedInterface.Chain
        val hook = LauncherDockBackground.editingTransformationHook()
        try {
            LauncherDockBackground.setEditingOwner(first, firstOwner)
            LauncherDockBackground.setEditingOwner(second, secondOwner)
            assertEquals(false, hook.intercept(chain))
            assertEquals(1, reads)
            LauncherDockBackground.clearEditingOwner(first, firstOwner)
            hook.intercept(chain)
            assertEquals("A released animation cannot acquire another owner's output", 1, reads)
            current = second
            hook.intercept(chain)
            assertEquals("Releasing one owner must preserve the other", 2, reads)
            LauncherDockBackground.clearEditingOwner(second, secondOwner)
            receiverReads = 0
            repeat(120) { assertEquals(false, hook.intercept(chain)) }
            assertEquals(0, receiverReads)
            assertEquals(2, reads)
            assertEquals(123, calls)
        } finally {
            LauncherDockBackground.clearEditingOwner(first, firstOwner)
            LauncherDockBackground.clearEditingOwner(second, secondOwner)
        }
    }

    @Test
    fun layoutHooksProceedOnceWithoutArgumentCopiesAndPreserveFailureBoundaries() {
        val row = FrameLayout(activity.get()).also { host.addView(it) }
        for ((hook, target) in listOf(LauncherDockBackground.hotseatMeasureHook() to host,
            LauncherDockBackground.contentLayoutHook() to row)) {
            val expected = Any()
            var calls = 0
            var failure: Throwable? = null
            var ownerFailure: Throwable? = null
            var ownerReads = 0
            val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
                arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
                when (method.name) {
                    "proceed" -> { assertTrue(args.isNullOrEmpty()); calls++; failure?.let { throw it }; expected }
                    "getThisObject" -> { ownerReads++; ownerFailure?.let { throw it }; target }
                    else -> error("Unexpected argument or member read: ${method.name}")
                }
            } as XposedInterface.Chain
            assertSame(expected, hook.intercept(chain))
            assertEquals(1, calls)
            assertEquals(1, ownerReads)
            val original = IllegalStateException("native layout")
            failure = original
            assertSame(original, assertThrows(IllegalStateException::class.java) { hook.intercept(chain) })
            assertEquals("Failed native layout does not notify the decoration", 1, ownerReads)
            val fatal = OutOfMemoryError("native layout")
            failure = java.lang.reflect.InvocationTargetException(fatal)
            assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { hook.intercept(chain) })
            failure = null
            ownerFailure = IllegalStateException("decoration update")
            assertSame(expected, hook.intercept(chain))
            ownerFailure = java.lang.reflect.InvocationTargetException(fatal)
            assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { hook.intercept(chain) })
            assertEquals(5, calls)
        }
    }

    @Test
    fun editingTransformationHookProceedsOnceWithoutCopyingArgumentsAndPreservesNativeFailures() {
        NativeMaterial.googleSupported = true
        val native = nativeHost()
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = native.getChildAt(0) as DockBackgroundView
        val animation = CountingEditingAnimation()
        native.startAnimation(animation)
        decoration.bindEditingAnimation(animation, showing = true)
        val output = android.view.animation.Transformation()
        var calls = 0
        var failure: Throwable? = null
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
            when (method.name) {
                "proceed" -> { assertTrue(args.isNullOrEmpty()); calls++; failure?.let { throw it }; output.alpha = .4f; true }
                "getThisObject" -> animation
                "getArg" -> { assertEquals(1, args!![0]); output }
                else -> error("Unexpected chain read: ${method.name}")
            }
        } as XposedInterface.Chain
        val hook = LauncherDockBackground.editingTransformationHook()
        assertEquals(true, hook.intercept(chain))
        assertEquals(1, calls)
        assertEquals(92, decoration.getChildAt(0).background.alpha)
        val nativeError = IllegalStateException("native transformation failed")
        failure = nativeError
        assertSame(nativeError, assertThrows(IllegalStateException::class.java) { hook.intercept(chain) })
        val fatal = OutOfMemoryError("native transformation")
        failure = fatal
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { hook.intercept(chain) })
        assertEquals(3, calls)
        assertEquals(92, decoration.getChildAt(0).background.alpha)
    }

    @Test
    fun interruptedEditingAndHiddenExitRetireOnlyTheOwnedNativeAnimation() {
        NativeMaterial.googleSupported = true
        val native = nativeHost()
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2), materialConstructor)
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        val decoration = native.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        val animation = CountingEditingAnimation().apply { duration = 370 }
        native.startAnimation(animation)
        decoration.bindEditingAnimation(animation, showing = true)
        decoration.onEditingFrame(animation, .4f, true)
        assertEquals(92, material.background.alpha)
        native.clearAnimation()
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(230, material.background.alpha)
        decoration.onEditingFrame(animation, 0f, false) // stale end cannot hide the returned Dock
        assertEquals(230, material.background.alpha)
        native.startAnimation(animation)
        native.visibility = View.INVISIBLE // native exit still draws its running AnimationSet
        decoration.onVisibilityAggregated(false)
        decoration.bindEditingAnimation(animation, showing = false)
        native.viewTreeObserver.dispatchOnPreDraw()
        assertTrue(material.appliedEnabled)
        decoration.onEditingFrame(animation, .5f, true)
        assertEquals(115, material.background.alpha)
        decoration.onEditingFrame(animation, 0f, false)
        assertEquals("The native terminal draw is transparent before ownership is retired", 0, material.background.alpha)
        native.clearAnimation()
        native.viewTreeObserver.dispatchOnPreDraw()
        assertFalse(material.appliedEnabled)
        assertNull(material.background)
        native.visibility = View.VISIBLE
        decoration.onVisibilityAggregated(true)
        layout()
        assertEquals(230, material.background.alpha)
    }

    @Test
    fun opacityBackendFailureIsRetiredOnceAndWrappedFatalErrorsStillEscape() {
        NativeMaterial.googleSupported = true
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        var attempts = 0
        material.background = object : ColorDrawable(Color.WHITE) {
            override fun setAlpha(value: Int) { attempts++; throw IllegalStateException("retired region") }
        }
        host.alpha = .5f
        host.viewTreeObserver.dispatchOnPreDraw()
        assertNull(material.parent)
        assertNull(material.background)
        val retired = attempts
        repeat(30) { host.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(retired, attempts)
        host.removeView(decoration)
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val replacement = host.getChildAt(0) as DockBackgroundView
        val fatal = OutOfMemoryError("native alpha")
        replacement.getChildAt(0).background = object : ColorDrawable(Color.WHITE) {
            override fun setAlpha(value: Int) { throw java.lang.reflect.InvocationTargetException(fatal) }
        }
        host.alpha = .25f
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { host.viewTreeObserver.dispatchOnPreDraw() })
        replacement.getChildAt(0).background = null
    }

    @Test
    fun editingAnimationNeverDrawsTheBackgroundOutsideTheNativeHotseatFrame() {
        val native = nativeHost()
        val decoration = attachNative(native, withMaterial = true)
        val icon = (native.mContent.getChildAt(0) as ItemIconFixture).mIconImageView
        val iconFrame = Rect(icon.left, icon.top, icon.right, icon.bottom)
        val bounds = Rect(decoration.left, decoration.top, decoration.right, decoration.bottom)
        native.startAnimation(android.view.animation.TranslateAnimation(0f, 0f, 60f, 0f).apply { duration = 200 })
        for (scale in floatArrayOf(.8f, .9f, 1f)) {
            native.scaleX = scale
            native.scaleY = scale
            native.translationY = (1f - scale) * 60f
            decoration.scheduleGeometry()
            repeat(2) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals(bounds, Rect(decoration.left, decoration.top, decoration.right, decoration.bottom))
            assertTrue(decoration.top >= native.paddingTop)
            assertTrue(decoration.bottom <= native.height - native.paddingBottom)
            assertEquals(iconFrame, Rect(icon.left, icon.top, icon.right, icon.bottom))
        }
        native.clearAnimation()
    }

    @Test
    fun dockBackgroundAvoidsNavigationSamplingWithoutChangingNativeBarAppearance() {
        val native = nativeHost()
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2), materialConstructor,
            DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java))
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        val decoration = native.getChildAt(0) as DockBackgroundView
        val originalBottom = decoration.bottom
        val root = decoration.rootView as android.view.ViewGroup
        val hostRect = Rect(0, 0, native.width, native.height)
        root.offsetDescendantRectToMyCoords(native, hostRect)
        val decor = activity.get().window.decorView
        @Suppress("DEPRECATION")
        val flags = decor.systemUiVisibility
        val appearance = decor.windowInsetsController?.systemBarsAppearance
        // Move the navigation strip through our background, as with a lowered or expanded Dock.
        val navigationInset = root.height - hostRect.bottom + 22
        decoration.onApplyWindowInsets(WindowInsets.Builder()
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, navigationInset)).build())
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(22, (decoration.layoutParams as FrameLayout.LayoutParams).bottomMargin)
        val material = decoration.getChildAt(0)
        assertEquals(native.height - 22, decoration.bottom)
        assertEquals(decoration.height, material.height)
        @Suppress("DEPRECATION")
        assertEquals(flags, decor.systemUiVisibility)
        assertEquals(appearance, decor.windowInsetsController?.systemBarsAppearance)
        decoration.onApplyWindowInsets(WindowInsets.Builder().build())
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(originalBottom, decoration.bottom)
        assertEquals(flags, @Suppress("DEPRECATION") decor.systemUiVisibility)
    }

    @Test
    fun initiallyHiddenHotseatHasNoStickySceneFlagAfterGestureReversalOrReattachment() {
        NativeMaterial.googleSupported = true
        host.visibility = View.INVISIBLE
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        assertEquals(View.VISIBLE, decoration.visibility)
        assertFalse(material.appliedEnabled)
        val disabledCalls = material.enableCalls
        repeat(20) { decoration.onVisibilityAggregated(false) }
        assertEquals(disabledCalls, material.enableCalls)
        host.alpha = 0f
        host.visibility = View.VISIBLE
        decoration.onVisibilityAggregated(true)
        layout()
        assertFalse(material.appliedEnabled)
        assertNull("Zero-alpha visibility does not activate a native region", material.background)
        host.alpha = .5f
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(115, material.background.alpha)
        host.alpha = 1f
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(230, material.background.alpha)
        assertTrue(material.appliedEnabled)
        host.removeView(decoration)
        host.addView(decoration, 0)
        layout()
        assertEquals(View.VISIBLE, decoration.visibility)
        assertNotSame(material, decoration.getChildAt(0))
        assertNull(material.background)
    }

    @Test
    fun visibilityBackendFailureRetiresTheNativeOwnerAndDoesNotRetryEachFrame() {
        LauncherDockBackground.attach(host, DockBackgroundConfig(material = 2), materialConstructor)
        layout()
        val decoration = host.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        NativeMaterial.failure = IllegalStateException("retired native blur binder")
        val silhouette = org.robolectric.util.ReflectionHelpers.getField<Path>(decoration.foreground, "path").generationId
        decoration.visibility = View.INVISIBLE
        decoration.onVisibilityAggregated(false)
        assertEquals(0, decoration.childCount)
        assertNull(material.parent)
        assertNull(material.background)
        val attempts = material.enableCalls
        decoration.visibility = View.VISIBLE
        repeat(20) { decoration.onVisibilityAggregated(true); host.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(attempts, material.enableCalls)
        assertEquals(silhouette, org.robolectric.util.ReflectionHelpers.getField<Path>(decoration.foreground, "path").generationId)
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
            layout()
        })
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
            put("launcher_dock_background_material", "3")
        }).material)
        assertEquals(0, DockBackgroundConfig.read(PrefMap().apply {
            put("launcher_dock_background_material", "1")
        }).material)
    }

    @Test
    fun automaticCornersKeepMainPanelProportionsEvenWithLargeDisplayCorners() {
        assertEquals(28f, DockCornerGeometry.radius(80f, 0f, 28f, 100f), 0f)
        assertEquals(28f, DockCornerGeometry.radius(80f, 0f, 28f, 140f), 0f)
        assertEquals(25.6f, DockCornerGeometry.radius(80f, 0f, 28f, 40f), 0.00001f)
        assertEquals(10f, DockCornerGeometry.radius(10f, 0f, 28f, 40f), 0f)
        assertEquals(24f, DockCornerGeometry.radius(80f, 24f, 28f, 40f), 0f)
        assertEquals(25.6f, DockCornerGeometry.radius(0f, 0f, 28f, 40f), 0.00001f)
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
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2, sideInsetDp = 12),
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
        assertEquals(minOf(100f, 20f * decoration.resources.displayMetrics.density,
            minOf(decoration.width, decoration.height) * .32f), radius)
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
            assertEquals(iconCenterX(native), (decoration.left + decoration.right) * 0.5f, 0f)
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
            val expected = minOf(100f, 20f * decoration.resources.displayMetrics.density,
                minOf(decoration.width, decoration.height) * .32f)
            assertTrue(expected > 0f)
            assertEquals(expected, (decoration.foreground as DockBackgroundTint).radius, 0f)
            assertEquals(expected, material.appliedCornerRadius, 0f)
            assertFalse("Native glass owns its corners without a second clip", material.clipToOutline)
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
        layout()
        assertEquals(expected, material.appliedBlurRadius)
        assertSame(material, decoration.getChildAt(0))
    }

    @Test
    fun automaticSpacingScalesWithNativeIconsInsteadOfAddingAFixedFiveDp() {
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        var smallPadding = 0
        for (size in intArrayOf(40, 80)) {
            val native = nativeHost()
            (native.layoutParams as FrameLayout.LayoutParams).height = size * 2
            val row = native.mContent
            val item = row.getChildAt(0) as ItemIconFixture
            item.layoutParams = FrameLayout.LayoutParams(160, size * 2)
            item.mIconImageView.layoutParams = FrameLayout.LayoutParams(size, size).apply { topMargin = size / 2; leftMargin = 40 }
            LauncherDockBackground.attach(native, DockBackgroundConfig(), null, access)
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            val decoration = native.getChildAt(0) as DockBackgroundView
            val bounds = Rect()
            access.bounds(native, row, bounds, Rect())
            val padding = decoration.bottom - bounds.bottom
            assertEquals((size * .2f).roundToInt(), padding)
            if (size == 40) smallPadding = padding else assertEquals(smallPadding * 2, padding)
            assertTrue(decoration.top >= 0)
            assertTrue(decoration.bottom <= native.height)
            assertEquals(size, item.mIconImageView.width)
            assertEquals(0f, row.translationY)
            native.removeView(decoration)
            host.removeView(native)
        }
        for ((stored, expected) in listOf(17 to 0, 12 to -5, 22 to 5, Int.MIN_VALUE to -17, Int.MAX_VALUE to 17)) {
            assertEquals(expected, DockBackgroundConfig.read(PrefMap().apply {
                put("launcher_dock_background_shrink", stored)
            }).sizeAdjustmentDp)
        }
    }

    @Test
    fun seededNativeDragCancelAndRebuildSequencesAlwaysReturnToTheCurrentIconLayout() {
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        val referenceWidths = (0..6).associateWith { count ->
            val reference = nativeHost()
            setNativeIcons(reference.mContent, count)
            val width = attachNativeAuto(reference).width
            host.removeView(reference)
            width
        }
        for (seed in intArrayOf(0xA14, 0xD0C)) {
            val random = Random(seed)
            val native = nativeHost()
            var count = 4
            setNativeIcons(native.mContent, count)
            var decoration = attachNativeAuto(native)
            repeat(32) { step ->
                try {
                    val before = decoration.width
                    native.mDragController.mDragging = true
                    val next = random.nextInt(7)
                    setNativeIcons(native.mContent, next)
                    repeat(2) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
                    assertEquals(before, decoration.width)
                    if (random.nextBoolean()) count = next // otherwise native cancellation restores the old row
                    setNativeIcons(native.mContent, count)
                    native.mDragController.mDragging = false
                    LauncherDockBackground.onDragFinished(native.mDragController, access)
                    repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
                    assertEquals(referenceWidths.getValue(count), decoration.width)
                    assertEquals(0f, native.mContent.translationY)
                    if (random.nextInt(4) == 0) {
                        native.removeView(decoration)
                        LauncherDockBackground.onDragFinished(native.mDragController, access)
                        decoration = attachNativeAuto(native)
                        assertEquals(referenceWidths.getValue(count), decoration.width)
                    }
                } catch (error: AssertionError) {
                    throw AssertionError("Dock seed=$seed step=$step committedIcons=$count", error)
                }
            }
            host.removeView(native)
        }
    }

    @Test
    fun nativeDragCompletionShrinksFourFiveFourEvenWithoutAnotherHostMeasureOrRowFrameChange() {
        val native = nativeHost()
        val row = native.mContent
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        setNativeIcons(row, 4)
        LauncherDockBackground.attach(native, DockBackgroundConfig(), null, access)
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        val decoration = native.getChildAt(0) as DockBackgroundView
        val four = decoration.width
        val rowFrameChanges = row.frameChanges
        for (count in intArrayOf(5, 4, 6, 3, 5, 4)) {
            native.mDragController.mDragging = true
            setNativeIcons(row, count)
            // Native layoutImmediately updates icons inside the unchanged row, still dragging.
            row.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
            row.layout(0, 0, 360, 80)
            native.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(rowFrameChanges, row.frameChanges)
            // Native endDrag commits/rolls back first, then its after hook marks geometry dirty.
            native.mDragController.mDragging = false
            LauncherDockBackground.onDragFinished(native.mDragController, access)
            assertTrue(native.viewTreeObserver.dispatchOnPreDraw()) // Android returns whether drawing was cancelled
            repeat(2) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            val bounds = Rect()
            access.bounds(native, row, bounds, Rect())
            val expectedInset = minOf(bounds.left, native.width - bounds.right) -
                minOf((bounds.height() * .2f).roundToInt(), bounds.top, native.height - bounds.bottom)
            assertEquals(expectedInset, decoration.left)
            assertEquals(expectedInset, native.width - decoration.right)
            if (count == 4) assertEquals(four, decoration.width)
            if (count > 4) assertTrue(decoration.width > four)
            if (count < 4) assertTrue(decoration.width < four)
        }
        // Reordering / cancel back to the same set does not rewrite geometry or native material.
        val params = decoration.layoutParams
        val reads = (row.getChildAt(0) as ItemIconFixture).mIconImageView.boundsReads
        native.mDragController.mDragging = true
        row.getChildAt(0).translationX = 12f
        LauncherDockBackground.onContentLayout(row)
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(reads, (row.getChildAt(0) as ItemIconFixture).mIconImageView.boundsReads)
        native.mDragController.mDragging = false
        row.getChildAt(0).translationX = 0f
        LauncherDockBackground.onDragFinished(native.mDragController, access)
        assertFalse(native.viewTreeObserver.dispatchOnPreDraw())
        assertSame(params, decoration.layoutParams)
        assertEquals(four, decoration.width)
    }

    @Test
    fun childRelayoutTracksRemovalFolderAndEmptySlotsWithoutChangingTheRowFrame() {
        val native = nativeHost()
        val row = native.mContent
        setNativeIcons(row, 5)
        val decoration = attachNativeAuto(native)
        val five = decoration.width
        val frameChanges = row.frameChanges
        setNativeIcons(row, 4)
        // A folder is an ItemIcon; a drag placeholder / empty cell is not.
        val icon = row.getChildAt(1)
        val params = icon.layoutParams
        row.removeView(icon)
        row.addView(FolderIconFixture(host.context), 1, params)
        row.addView(View(host.context), FrameLayout.LayoutParams(360, 80))
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(frameChanges, row.frameChanges)
        assertTrue(decoration.width < five)
        val four = decoration.width
        row.getChildAt(1).visibility = View.INVISIBLE // Native folder/launch source is still occupied.
        LauncherDockBackground.onContentLayout(row)
        native.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(four, decoration.width)
        setNativeIcons(row, 1)
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertTrue(decoration.width < four)
        assertTrue(decoration.width >= 48)
        row.removeAllViews()
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(0, decoration.width)
        setNativeIcons(row, 5)
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(five, decoration.width)
    }

    @Test
    fun signedSizeAdjustmentMovesAllEdgesAndClampsTheWholeRectangleSymmetrically() {
        val native = nativeHost()
        setNativeIcons(native.mContent, 4)
        repeat(4) { i ->
            (native.mContent.getChildAt(i) as ItemIconFixture).mIconImageView.layoutParams =
                FrameLayout.LayoutParams(48, 48).apply { leftMargin = 21; topMargin = 16 }
        }
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        for (inset in intArrayOf(0, 12, 18)) {
            var base = Rect()
            for (adjustment in intArrayOf(0, 4, -4, Int.MAX_VALUE, Int.MIN_VALUE)) {
                LauncherDockBackground.attach(native, DockBackgroundConfig(sideInsetDp = inset, sizeAdjustmentDp = adjustment), null, access)
                repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
                val decoration = native.getChildAt(0) as DockBackgroundView
                if (adjustment == 0) base = Rect(decoration.left, decoration.top, decoration.right, decoration.bottom)
                val delta = base.left - decoration.left
                assertEquals(delta, decoration.right - base.right)
                if (adjustment in -4..4) {
                    assertEquals(delta, base.top - decoration.top)
                    assertEquals(delta, decoration.bottom - base.bottom)
                }
                if (adjustment > 0) assertTrue(delta > 0)
                if (adjustment < 0) assertTrue(delta < 0)
                assertTrue(decoration.width > 0 && decoration.height > 0)
                assertTrue(decoration.left >= native.paddingLeft)
                assertTrue(decoration.top >= native.paddingTop && decoration.bottom <= native.height - native.paddingBottom)
                assertEquals(0f, native.mContent.translationY)
                assertEquals(48, (native.mContent.getChildAt(0) as ItemIconFixture).mIconImageView.width)
                native.removeView(decoration)
            }
        }
    }

    @Test
    fun fullHeightIconsAtNavigationClampExpansionOnAllEdgesWithoutMovingTheirCenter() {
        val native = nativeHost()
        setNativeIcons(native.mContent, 4)
        repeat(4) { i ->
            (native.mContent.getChildAt(i) as ItemIconFixture).mIconImageView.layoutParams =
                FrameLayout.LayoutParams(48, 80).apply { leftMargin = 21 }
        }
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        var base = Rect()
        for (adjustment in intArrayOf(0, 4, -4)) {
            LauncherDockBackground.attach(native, DockBackgroundConfig(sizeAdjustmentDp = adjustment), null, access)
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            val decoration = native.getChildAt(0) as DockBackgroundView
            val root = decoration.rootView as android.view.ViewGroup
            val hostRect = Rect(0, 0, native.width, native.height)
            root.offsetDescendantRectToMyCoords(native, hostRect)
            decoration.onApplyWindowInsets(WindowInsets.Builder().setInsets(WindowInsets.Type.navigationBars(),
                Insets.of(0, 0, 0, root.height - hostRect.bottom + 22)).build())
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            if (adjustment == 0) base = Rect(decoration.left, decoration.top, decoration.right, decoration.bottom)
            val effective = minOf(adjustment, 0)
            assertEquals(base.left - effective, decoration.left)
            assertEquals(base.right + effective, decoration.right)
            assertEquals(base.top - effective, decoration.top)
            assertEquals(base.bottom + minOf(adjustment, 0), decoration.bottom)
            assertEquals(base.exactCenterX(), (decoration.left + decoration.right) * 0.5f, 0f)
            assertEquals(40f, (decoration.top + decoration.bottom) * 0.5f, 0f)
            assertTrue(decoration.bottom <= native.height - 22)
            assertEquals(80, (native.mContent.getChildAt(0) as ItemIconFixture).mIconImageView.height)
            native.removeView(decoration)
        }
    }

    private fun widthAnimator(decoration: DockBackgroundView): ValueAnimator? =
        ReflectionHelpers.getField(decoration, "widthAnimation")

    @Test
    fun fourFiveFourFiveEasesOnlyTheBackgroundAndRetargetsFromItsVisibleWidth() {
        NativeMaterial.googleSupported = true
        val native = nativeHost()
        setNativeIcons(native.mContent, 4)
        LauncherDockBackground.attach(native, DockBackgroundConfig(material = 2), materialConstructor,
            DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java))
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        val decoration = native.getChildAt(0) as DockBackgroundView
        val material = decoration.getChildAt(0) as NativeMaterial
        assertNull(widthAnimator(decoration)) // No animation or allocation for initial layout.
        for (count in intArrayOf(5, 4, 5)) {
            val visibleWidth = decoration.width * decoration.scaleX
            setNativeIcons(native.mContent, count)
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals(visibleWidth, decoration.width * decoration.scaleX, .001f)
            val animator = checkNotNull(widthAnimator(decoration))
            assertTrue(animator.isRunning)
            assertEquals(180L, animator.duration)
            val startScale = decoration.scaleX
            val targetWidth = decoration.width
            val params = decoration.layoutParams
            val icon = (native.mContent.getChildAt(0) as ItemIconFixture).mIconImageView
            val reads = icon.boundsReads
            val cornerCalls = material.cornerCalls
            for (time in longArrayOf(30, 60, 90)) {
                animator.currentPlayTime = time
                native.viewTreeObserver.dispatchOnPreDraw()
                assertTrue(decoration.scaleX in minOf(startScale, 1f)..maxOf(startScale, 1f))
                assertNotEquals(startScale, decoration.scaleX)
                assertNotEquals(1f, decoration.scaleX)
                assertEquals(targetWidth, decoration.width)
                assertSame(params, decoration.layoutParams)
                assertEquals(reads, icon.boundsReads)
                assertEquals(cornerCalls, material.cornerCalls)
                assertEquals(1f, native.mContent.scaleX)
                assertEquals(1f, icon.scaleX)
                assertEquals(48, icon.width)
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250))
        assertFalse(widthAnimator(decoration)!!.isRunning)
        assertEquals(1f, decoration.scaleX)
    }

    @Test
    fun widthAnimationIsCancelledForDragScenesVisibilityAndDetachment() {
        val native = nativeHost()
        setNativeIcons(native.mContent, 4)
        val decoration = attachNativeAuto(native)
        fun change(count: Int) {
            setNativeIcons(native.mContent, count)
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            assertTrue(widthAnimator(decoration)!!.isRunning)
        }
        change(5)
        native.mDragController.mDragging = true
        LauncherDockBackground.onContentLayout(native.mContent)
        native.viewTreeObserver.dispatchOnPreDraw()
        assertFalse(widthAnimator(decoration)!!.isRunning)
        assertEquals(1f, decoration.scaleX)
        native.mDragController.mDragging = false
        change(4)
        decoration.bindEditingAnimation(android.view.animation.AnimationSet(false), false)
        assertFalse(widthAnimator(decoration)!!.isRunning)
        assertEquals(1f, decoration.scaleX)
        change(5)
        decoration.onVisibilityAggregated(false)
        assertFalse(widthAnimator(decoration)!!.isRunning)
        assertEquals(1f, decoration.scaleX)
        decoration.onVisibilityAggregated(true)
        change(4)
        native.removeView(decoration)
        assertNull(widthAnimator(decoration))
        assertEquals(1f, decoration.scaleX)
    }

    @Test
    fun disabledSystemAnimationsCommitTheFinalDockWidthWithoutCreatingAnAnimator() {
        val previous = ReflectionHelpers.callStaticMethod<Float>(ValueAnimator::class.java, "getDurationScale")
        try {
            ReflectionHelpers.callStaticMethod<Unit>(ValueAnimator::class.java, "setDurationScale",
                ReflectionHelpers.ClassParameter.from(java.lang.Float.TYPE, 0f))
            val native = nativeHost()
            setNativeIcons(native.mContent, 4)
            val decoration = attachNativeAuto(native)
            val four = decoration.width
            setNativeIcons(native.mContent, 5)
            repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
            assertTrue(decoration.width > four)
            assertNull(widthAnimator(decoration))
            assertEquals(1f, decoration.scaleX)
        } finally {
            ReflectionHelpers.callStaticMethod<Unit>(ValueAnimator::class.java, "setDurationScale",
                ReflectionHelpers.ClassParameter.from(java.lang.Float.TYPE, previous))
        }
    }

    @Test
    fun completedNativeSceneDoesNotBlockTheNextIconCountAnimation() {
        val native = nativeHost()
        setNativeIcons(native.mContent, 4)
        val decoration = attachNativeAuto(native)
        val scene = android.view.animation.AlphaAnimation(1f, 1f).apply { duration = 1L }
        native.animation = scene
        scene.startTime = 0L
        val transformation = android.view.animation.Transformation()
        scene.getTransformation(0L, transformation)
        scene.getTransformation(2L, transformation)
        assertTrue(scene.hasEnded())
        setNativeIcons(native.mContent, 5)
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertTrue(widthAnimator(decoration)!!.isRunning)
        assertTrue(decoration.scaleX < 1f)
    }

    @Test
    fun automaticRadiusAndManualRadiusAreSharedByAllThreeMaterials() {
        val native = nativeHost()
        setNativeIcons(native.mContent, 4)
        val access = DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
        for (manual in intArrayOf(0, 12, 80)) {
            var first = -1f
            for (material in intArrayOf(2)) {
                LauncherDockBackground.attach(native, DockBackgroundConfig(material = material, radiusDp = manual), materialConstructor, access)
                repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
                val decoration = native.getChildAt(0) as DockBackgroundView
                decoration.onApplyWindowInsets(WindowInsets.Builder()
                    .setRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT,
                        RoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT, 118, 118, 118))
                    .setRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT,
                        RoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT, 118, 118, 118)).build())
                val actual = (decoration.foreground as DockBackgroundTint).radius
                if (first < 0f) first = actual
                assertEquals(first, actual, 0f)
                assertEquals(actual, (decoration.getChildAt(0) as NativeMaterial).appliedCornerRadius, 0f)
                if (manual == 0) {
                    assertTrue("A physical display corner must not inflate the native panel", actual <=
                        ReflectionHelpers.getField<Float>(decoration, "panelRadius"))
                    assertTrue("Automatic Dock corners retain main's flat sides", actual <= decoration.height * .32f)
                }
                native.removeView(decoration)
            }
        }
    }

    private fun setNativeIcons(row: ContentFixture, count: Int) {
        row.removeAllViews()
        if (count == 0) return
        val cellWidth = 360 / count
        repeat(count) { i ->
            val item = ItemIconFixture(host.context)
            (item.mIconImageView.layoutParams as FrameLayout.LayoutParams).leftMargin = (cellWidth - 48) / 2
            row.addView(item, FrameLayout.LayoutParams(cellWidth, 80).apply { leftMargin = i * cellWidth })
        }
    }

    private fun attachNativeAuto(native: HotSeatsFixture): DockBackgroundView {
        LauncherDockBackground.attach(native, DockBackgroundConfig(), null,
            DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java))
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        return native.getChildAt(0) as DockBackgroundView
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
        assertEquals(View.INVISIBLE, item.visibility)
        assertEquals(0f, item.mIconImageView.alpha)
        // Only the native transition owner restores its source. Geometry refresh
        // must preserve that restoration just as it preserved the temporary hide.
        item.visibility = View.VISIBLE
        item.mIconImageView.alpha = 1f
        decoration.scheduleGeometry()
        repeat(3) { layout(); native.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(View.VISIBLE, item.visibility)
        assertEquals(1f, item.mIconImageView.alpha)
        assertEquals(bounds, Rect(decoration.left, decoration.top, decoration.right, decoration.bottom))
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
        assertTrue(upper >= 0)
        assertTrue(lower >= 0)
        assertEquals(upper, lower)
        assertEquals(bounds.exactCenterX(), (decoration.left + decoration.right) * 0.5f, 0f)
    }

    private fun iconCenterX(native: HotSeatsFixture): Float = Rect().also {
        DockContentAccess(HotSeatsFixture::class.java, ItemIconFixture::class.java)
            .bounds(native, native.mContent, it, Rect())
    }.exactCenterX()

    class HotSeatsFixture(context: Context) : FrameLayout(context) {
        @JvmField var mContent = ContentFixture(context)
        @JvmField val mDragController = DragControllerFixture(LauncherFixture(this))
        init {
            // ROM layout_hotseats.xml: the native row and its background may overflow this frame.
            clipChildren = false
            clipToPadding = false
        }
    }

    class LauncherFixture(@JvmField var mHotSeats: HotSeatsFixture)

    class DragControllerFixture(@JvmField val mLauncher: LauncherFixture) { @JvmField var mDragging = false }

    class CountingEditingAnimation : android.view.animation.AnimationSet(true) {
        var frames = 0
        override fun getTransformation(time: Long, out: android.view.animation.Transformation): Boolean {
            frames++
            return super.getTransformation(time, out)
        }
    }

    class ContentFixture(context: Context) : FrameLayout(context) {
        var positionUpdates = 0
        var frameChanges = 0
        fun onTranslationChanged() { positionUpdates++ }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            if (changed) frameChanges++
            super.onLayout(changed, left, top, right, bottom)
            LauncherDockBackground.onContentLayout(this)
        }
    }

    open class ItemIconFixture(context: Context) : FrameLayout(context) {
        @JvmField val mIconImageView = NativeIcon(context)
        @JvmField val mTitleContainer = View(context)
        @JvmField var mIsHideTitle = true
        init {
            addView(mIconImageView, LayoutParams(48, 48).apply { topMargin = 6 })
            addView(mTitleContainer, LayoutParams(80, 12).apply { topMargin = 56 })
        }
    }

    class FolderIconFixture(context: Context) : ItemIconFixture(context)

    class NativeIcon(context: Context) : View(context) {
        var boundsReads = 0
        var boundsFailure: Throwable? = null
        override fun getDrawingRect(outRect: Rect) {
            boundsReads++
            boundsFailure?.let { throw it }
            super.getDrawingRect(outRect)
        }
    }

    private fun measureAndLayout() {
        host.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 360, 80)
    }

    private fun layout() {
        // A real traversal settles requested panel bounds before the next pre-draw.
        repeat(3) { measureAndLayout(); host.viewTreeObserver.dispatchOnPreDraw() }
    }

    /** ROM API fixture: no renderer or hooks are replaced in production. */
    class NativeMaterial(context: Context) : FrameLayout(context) {
        var appliedBlurRadius = 0
        var appliedEnabled = false
        var appliedCornerRadius = 0f
        var enableCalls = 0
        var cornerCalls = 0
        var alphaSubmissions = 0
        var writtenAlpha = 255
        @JvmField val mBlurLayerHolder = BlendLayer()
        init {
            // Most tests inspect exact writes. The A14 regression mode preserves Drawable.getAlpha == 255.
            background = object : ColorDrawable(Color.WHITE) {
                private var opacity = 255
                override fun getAlpha() = if (NativeMaterial.inheritedAlphaGetter) 255 else opacity
                override fun setAlpha(value: Int) { alphaSubmissions++; writtenAlpha = value; opacity = value; super.setAlpha(value) }
            }
        }
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
            var inheritedAlphaGetter = false
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
