package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RenderNode
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
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
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp",
    shadows = [DockRuntimeShaderShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockGlassBackdropTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
    private val host = FrameLayout(activity.get())
    private val view = View(activity.get())
    private val controllers = ArrayList<DockGlassBackdrop>()
    private var captures = 0
    private var lastCrop = Rect()
    private var lastScale = 0f
    private var lastBitmap: Bitmap? = null
    private val sampler = DockWallpaperSampler { crop, scale ->
        captures++
        lastCrop.set(crop)
        lastScale = scale
        Bitmap.createBitmap((crop.width() * scale).toInt().coerceAtLeast(1),
            (crop.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { lastBitmap = it }
    }

    init {
        activity.get().setContentView(host)
        host.addView(view, FrameLayout.LayoutParams(300, 80).apply { leftMargin = 23; topMargin = 650 })
        host.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 360, 800)
    }

    @After
    fun close() {
        controllers.forEach { it.release() }
        activity.pause().stop().destroy()
    }

    private fun controller(live: Boolean = false, source: DockWallpaperSampler = sampler) =
        DockGlassBackdrop(view, source, 90, live, Dispatchers.Unconfined).also { controllers.add(it) }

    @Test
    fun appAreaMetricsCannotTruncateTheWallpaperDisplayOrItsLowerDockCrop() {
        val metrics = view.resources.displayMetrics
        val original = metrics.heightPixels
        val full = activity.get().getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
        val viewport = IntArray(2)
        val source = object : DockWallpaperSampler {
            override fun capture(crop: Rect, scale: Float): Bitmap? = error("Full-display proof is required")
            override fun capture(crop: Rect, scale: Float, viewportWidth: Int, viewportHeight: Int): Bitmap? {
                viewport[0] = viewportWidth; viewport[1] = viewportHeight
                return sampler.capture(crop, scale)
            }
        }
        try {
            metrics.heightPixels = 700 // Activity resources can exclude the system bars.
            val controller = controller(source = source)
            controller.onFrame(true)
            assertArrayEquals(intArrayOf(full.width(), full.height()), viewport)
            assertTrue("The lower dock remains inside its actual wallpaper crop", lastCrop.bottom > 700)
            assertFalse(lastBitmap!!.isRecycled)
            controller.release()
        } finally { metrics.heightPixels = original }
    }

    @Test
    fun ownCompletionDrawCannotStartAStaticCaptureLoopAndNativeFramesStillRefresh() {
        val controller = controller()
        controller.onFrame(true)
        assertEquals(1, captures)
        assertFalse(lastBitmap!!.isRecycled)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        controller.onFrame(true) // the accepted texture's own invalidation
        assertEquals(1, captures)
        controller.onFrame(true) // a later native frame
        assertEquals(2, captures)
        val position = IntArray(2).also { view.getLocationOnScreen(it) }
        assertTrue("crop=$lastCrop position=${position.contentToString()}",
            lastCrop.contains(Rect(position[0], position[1], position[0] + 300, position[1] + 80)))
        assertTrue(lastBitmap!!.width <= DockGlassBackdrop.MAX_WIDTH)
        assertTrue(lastBitmap!!.height <= DockGlassBackdrop.MAX_HEIGHT)
        assertTrue(lastBitmap!!.width.toLong() * lastBitmap!!.height <= DockGlassBackdrop.MAX_PIXELS)
        controller.release()
        assertTrue(lastBitmap!!.isRecycled)
        controller.onFrame(true)
        assertEquals(2, captures)
    }

    @Test
    fun settledStaticWallpaperIgnoresUnrelatedNativeDrawsUntilItsSourceChanges() {
        val controller = controller()
        controller.onFrame(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        repeat(60) { controller.onFrame(true) }
        assertEquals(1, captures)
        controller.invalidateSource()
        controller.onFrame(true)
        assertEquals(2, captures)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        repeat(60) { controller.onFrame(true) }
        assertEquals(2, captures)
    }

    @Test
    fun hiddenLiveWallpaperStopsRefreshingAndReturnedOldGenerationsAreReleased() {
        val controller = controller(live = true)
        controller.onFrame(true)
        val first = lastBitmap!!
        controller.onFrame(false)
        assertTrue(first.isRecycled)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(1, captures)
        val oldFrame = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        controller.accept(oldFrame, Rect(0, 0, 100, 50), 0, 1f)
        assertTrue(oldFrame.isRecycled)
        controller.onFrame(true)
        assertEquals(2, captures)
        controller.onFrame(false)
        assertTrue(lastBitmap!!.isRecycled)
    }

    @Test fun nativeZeroAlphaPausesSamplingWithoutReplacingTheCurrentMaterial() {
        val controller = controller(live = true)
        controller.onFrame(true)
        val first = lastBitmap!!
        controller.onFrame(true, rendering = false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertFalse("A transparent animation frame is not a detach", first.isRecycled)
        assertEquals(1, captures)
        controller.onFrame(true)
        assertEquals(1, captures)
        controller.onFrame(false)
        assertTrue(first.isRecycled)
    }

    @Test
    fun unavailableFirstFrameHasABoundedCompositorRecoveryWindow() {
        val controller = controller(source = DockWallpaperSampler { _, _ -> captures++; null })
        controller.onFrame(true)
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
            controller.onFrame(true)
        }
        assertEquals(4, captures)
        assertFalse(controller.draw(Canvas(Bitmap.createBitmap(300, 80, Bitmap.Config.ARGB_8888)), Rect(0, 0, 300, 80), 20f, 1f))
        controller.invalidateSource()
        controller.onFrame(true)
        assertEquals(5, captures)
    }

    @Test fun aTransientCaptureFailureRecoversWithoutRetiringTheLensOrStartingAnIdleLoop() {
        var attempts = 0
        val controller = controller(source = DockWallpaperSampler { crop, scale ->
            if (++attempts == 1) throw IllegalStateException("compositor frame pending")
            sampler.capture(crop, scale)
        })
        controller.onFrame(true)
        assertEquals(1, attempts)
        assertFalse(ReflectionHelpers.getField(controller, "broken"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(64))
        controller.onFrame(true)
        assertEquals(2, attempts)
        assertFalse(lastBitmap!!.isRecycled)
        assertFalse(ReflectionHelpers.getField(controller, "broken"))
        controller.onFrame(false)
        assertTrue(lastBitmap!!.isRecycled)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(2, attempts)
    }

    @Test fun powerSaverReleasesTheTextureAndStopsLiveCaptureUntilItChangesAgain() {
        val controller = controller(live = true)
        controller.onFrame(true)
        val first = lastBitmap!!
        controller.setPowerSave(true)
        assertTrue(first.isRecycled)
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
            controller.onFrame(true)
        }
        assertEquals(1, captures)
        controller.setPowerSave(false)
        controller.onFrame(true)
        assertEquals(2, captures)
    }

    @Test
    fun identicalDrawsSkipUniformWritesButSubpixelChangesAndNewShadersRefreshThem() {
        val controller = controller()
        controller.onFrame(true)
        val node = RenderNode("dock-uniform-cache-test").apply { setPosition(0, 0, 300, 80) }
        val canvas = node.beginRecording()
        val bounds = Rect(0, 0, 300, 80)
        try {
            DockRuntimeShaderShadow.uniformWrites = 0
            assertTrue(controller.draw(canvas, bounds, 20f, 1f))
            assertEquals(7, DockRuntimeShaderShadow.uniformWrites)
            repeat(12) { assertTrue(controller.draw(canvas, bounds, 20f, 1f)) }
            assertEquals(7, DockRuntimeShaderShadow.uniformWrites)
            assertTrue(controller.draw(canvas, bounds, 20.001f, 1f))
            assertEquals(14, DockRuntimeShaderShadow.uniformWrites)
            controller.invalidateSource()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
            controller.onFrame(true)
            DockRuntimeShaderShadow.uniformWrites = 0
            assertTrue(controller.draw(canvas, bounds, 20.001f, 1f))
            assertEquals(7, DockRuntimeShaderShadow.uniformWrites)
            assertTrue(controller.draw(canvas, bounds, 20.001f, 1f))
            assertEquals(7, DockRuntimeShaderShadow.uniformWrites)
        } finally { node.endRecording(); node.discardDisplayList() }
    }

    @Test
    fun obsoleteCaptureFailureCannotRetireAReplacementWallpaperOrAReshownOwner() {
        for (hide in listOf(false, true)) {
            val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
            var attempts = 0
            val source = DockWallpaperSampler { crop, scale ->
                if (++attempts == 1) throw IllegalStateException("obsolete wallpaper capture")
                Bitmap.createBitmap((crop.width() * scale).toInt(), (crop.height() * scale).toInt(), Bitmap.Config.ARGB_8888)
            }
            val controller = DockGlassBackdrop(view, source, 90, false,
                kotlinx.coroutines.test.StandardTestDispatcher(scheduler)).also(controllers::add)
            controller.onFrame(true)
            if (hide) controller.onFrame(false) else controller.invalidateSource()
            scheduler.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
            controller.onFrame(true)
            scheduler.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("hide=$hide: the current generation can still capture", 2, attempts)
            assertNotNull(ReflectionHelpers.getField<Bitmap?>(controller, "bitmap"))
            controller.release()
        }
    }

    @Test
    fun wallpaperReplacementDropsTheOldTextureAndCropMismatchesNeverBecomeAStretchedWallpaper() {
        val controller = controller()
        controller.onFrame(true)
        val old = lastBitmap!!
        controller.invalidateSource()
        assertTrue(old.isRecycled)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
        controller.onFrame(true)
        assertEquals(2, captures)
        val bad = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        controller.accept(bad, Rect(lastCrop), 1, lastScale)
        assertTrue(bad.isRecycled)
        assertFalse("A valid aligned texture survives a transient bad replacement", lastBitmap!!.isRecycled)
        assertFalse(ReflectionHelpers.getField(controller, "broken"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(64))
        controller.onFrame(true)
        assertEquals(3, captures)
        assertFalse(lastBitmap!!.isRecycled)
    }

    @Test fun nativePinchMidpointKeepsRealWallpaperPixelsWhileTheNextCaptureIsPending() {
        val controller = controller()
        controller.onFrame(true)
        val first = lastBitmap!!
        val node = RenderNode("dock-native-pinch-midpoint").apply { setPosition(0, 0, 300, 80) }
        val canvas = node.beginRecording()
        try {
            // The current draw receives a legacy matrix after pre-draw. A replacement
            // compositor frame cannot have completed yet; the retained crop must cover it.
            for (dy in listOf(-40f, -80f, -120f, -320f, -520f, -320f, -80f, 0f)) {
                controller.setLegacyTransform(Matrix().apply { setTranslate(0f, dy) })
                assertTrue("Native editing midpoint dy=$dy", controller.prepare(canvas, Rect(0, 0, 300, 80), 20f, 1f))
                assertSame(first, lastBitmap)
                assertFalse(first.isRecycled)
            }
            assertEquals("No synchronous capture is needed during drawing", 1, captures)
        } finally { node.endRecording(); controller.release() }
    }

    @Test fun badReplacementFramesCannotEvictCoveredWallpaperOrRestartRecoveryOnUnrelatedDraws() {
        for (live in listOf(false, true)) {
            view.translationY = 0f
            var attempts = 0
            val controller = controller(live = live, source = DockWallpaperSampler { crop, scale ->
                if (++attempts == 1) sampler.capture(crop, scale)
                else Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            })
            controller.onFrame(true)
            assertEquals(1, attempts)
            view.translationY = -320f // leave the old fixed crop during native editing
            val node = RenderNode("dock-exhausted-recovery").apply { setPosition(0, 0, 300, 80) }
            val canvas = node.beginRecording()
            try {
                repeat(60) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                    controller.onFrame(true)
                    assertTrue("A bad replacement must not switch a covered lens to native blur",
                        controller.prepare(canvas, Rect(0, 0, 300, 80), 20f, 1f))
                }
                assertEquals("One failure and three bounded retries; drawing cannot replenish them", 5, attempts)
                assertFalse(ReflectionHelpers.getField(controller, "broken"))
            } finally { node.endRecording(); controller.onFrame(false); controller.release() }
            assertTrue(lastBitmap!!.isRecycled)
        }
    }

    @Test
    fun onePendingCaptureHasNoQueueAndDetachDisposesTheLateWorkerResult() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val frame = AtomicReference<Bitmap>()
        val worker = AtomicReference<Thread>()
        val controller = DockGlassBackdrop(view, DockWallpaperSampler { crop, scale ->
            worker.set(Thread.currentThread())
            captures++
            entered.countDown()
            check(finish.await(3, TimeUnit.SECONDS))
            Bitmap.createBitmap((crop.width() * scale).toInt(), (crop.height() * scale).toInt(), Bitmap.Config.ARGB_8888)
                .also { frame.set(it); returned.countDown() }
        }, 90, false).also { controllers.add(it) }
        try {
            controller.onFrame(true)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertNotSame(Looper.getMainLooper().thread, worker.get())
            repeat(20) { controller.onFrame(true) }
            assertEquals(1, captures)
            controller.release()
            finish.countDown()
            assertTrue(returned.await(2, TimeUnit.SECONDS))
            val deadline = java.lang.System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!frame.get().isRecycled && java.lang.System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(1)
            }
            assertTrue("late result has no surviving owner", frame.get().isRecycled)
            assertEquals(1, captures)
        } finally { finish.countDown() }
    }

    @Test
    fun wallpaperInvalidationInsideTheRateLimitStillSchedulesItsSingleReplacement() {
        val controller = controller()
        controller.onFrame(true)
        val first = lastBitmap!!
        controller.invalidateSource()
        controller.onFrame(true)
        assertTrue(first.isRecycled)
        assertEquals(1, captures)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(35))
        controller.onFrame(true)
        assertEquals(2, captures)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        controller.onFrame(true)
        assertEquals(2, captures)
    }

    @Test
    fun visibleLiveWallpaperRefreshesButHiddenOwnersCancelTheirClock() {
        val controller = controller(live = true)
        controller.onFrame(true)
        controller.onFrame(true)
        assertEquals(1, captures)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(35))
        controller.onFrame(true)
        assertEquals(2, captures)
        controller.onFrame(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(2, captures)
    }

    @Test
    fun cropUsesTheNativePanelTransformIncludingTheAlreadyComputedEditingMatrix() {
        val controller = controller()
        val position = IntArray(2).also { view.getLocationOnScreen(it) }
        controller.setLegacyTransform(Matrix().apply { setTranslate(0f, -120f) })
        controller.onFrame(true)
        assertTrue("crop=$lastCrop position=${position.contentToString()}", lastCrop.contains(Rect(position[0], position[1] - 120, position[0] + 300, position[1] - 40)))
        assertTrue("The real native position and its next sweep share one retained crop",
            lastCrop.contains(Rect(position[0], position[1], position[0] + 300, position[1] + 80)))
        val values = ReflectionHelpers.getField<Matrix>(controller, "world")
        val mapped = floatArrayOf(150f, 40f)
        values.mapPoints(mapped)
        assertEquals(position[0] + 150f, mapped[0], .001f)
        assertEquals(position[1] - 80f, mapped[1], .001f)
    }
}
