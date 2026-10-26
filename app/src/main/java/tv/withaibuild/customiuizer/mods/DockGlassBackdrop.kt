package tv.withaibuild.customiuizer.mods

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.RuntimeShader
import android.os.SystemClock
import android.view.Display
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.floor

/** One decoration owns one texture, one shader and at most one pending wallpaper-only capture.
 * Static wallpaper follows native rendered frames, with the completion draw excluded to avoid
 * feedback. Live wallpaper alone needs a visible, 30 Hz maximum refresh; hidden/transparent
 * owners stop it. No full-display screenshot, CPU pixel readback/blur, file decode or capture
 * of the Launcher tree. The sampler returns only the cropped wallpaper compositor layer.
 */
internal class DockGlassBackdrop(
    view: View,
    private val sampler: DockWallpaperSampler,
    opacity: Int,
    private val liveWallpaper: Boolean,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val lensPath = Path()
    private val lensPathBounds = Rect()
    private var lensPathRadius = -1f
    private val owner = WeakReference(view)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + ModuleHelper.coroutineFailureHandler)
    private var shader: RuntimeShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (opacity * 2.55f).toInt().coerceIn(0, 255) }
    private val world = Matrix()
    private val rootMatrix = Matrix()
    private val hostMatrix = Matrix()
    private val inverseHost = Matrix()
    private val correction = Matrix()
    private val legacy = Matrix()
    private var hasLegacy = false
    private val values = FloatArray(9)
    private val frameMatrix = FloatArray(9) { Float.NaN }
    // Two fixed scalar buffers belong to this lens, never to the wallpaper or View tree.
    private val lensUniforms = FloatArray(13)
    private val nextLensUniforms = FloatArray(13)
    private var lensUniformsValid = false
    private val rootValues = FloatArray(9)
    private val points = FloatArray(8)
    private val location = IntArray(2)
    private var bitmap: Bitmap? = null
    private val crop = Rect()
    private var sampleScaleX = 1f
    private var sampleScaleY = 1f
    private var epoch = 0
    private var busy = false
    private var visible = false
    private var powerSave = false
    private var released = false
    private var broken = false
    private var sourceDirty = true
    private var viewportDirty = true
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var reportedTexture = false
    private var sourceUnavailable = false
    private var unavailableTries = 0
    private var completionDraw = false
    private var tickRequested = false
    private var lastRequest = Long.MIN_VALUE / 2
    private var nativeMotionUntil = 0L
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastWidth = 0
    private var lastHeight = 0
    private var motion = 0f
    private val tick = Runnable {
        ModuleHelper.guarded {
            if (visible && !released && !broken) {
                tickRequested = true
                owner.get()?.postInvalidateOnAnimation()
            }
        }
    }

    fun setLegacyTransform(matrix: Matrix?) {
        hasLegacy = matrix != null
        if (matrix == null) legacy.reset() else legacy.set(matrix)
    }

    fun setPowerSave(enabled: Boolean) {
        if (powerSave == enabled) return
        powerSave = enabled
        invalidateSource()
        owner.get()?.let { view -> view.removeCallbacks(tick); tickRequested = false; view.invalidate() }
    }

    fun invalidateSource() {
        nativeMotionUntil = SystemClock.uptimeMillis() + 450L
        epoch++
        sourceDirty = true
        viewportDirty = true
        sourceUnavailable = false
        unavailableTries = 0
        clearTexture()
    }

    fun onFrame(isVisible: Boolean) {
        val view = owner.get() ?: return
        if (visible != isVisible) {
            visible = isVisible
            if (!visible) {
                view.removeCallbacks(tick)
                invalidateSource()
                return
            }
        }
        if (!visible || powerSave || broken || released || !view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
        // The ROM extension has no display selector. Never substitute default-display pixels on
        // a secondary display. Unknown/non-affine transforms also keep the native material.
        if (view.display?.displayId != Display.DEFAULT_DISPLAY || !prepareMatrix(view)) return
        if (!values.contentEquals(frameMatrix) || lastWidth != view.width || lastHeight != view.height) {
            nativeMotionUntil = SystemClock.uptimeMillis() + 450L
            values.copyInto(frameMatrix)
            lastWidth = view.width
            lastHeight = view.height
            sourceDirty = true
            sourceUnavailable = false
            unavailableTries = 0
            view.invalidate() // follow a native transform, including RenderNode-only scaleX
        }
        val ownDraw = completionDraw
        completionDraw = false
        val now = SystemClock.uptimeMillis()
        if (sourceUnavailable && !sourceDirty && !tickRequested) return
        // Unrelated icon/clock draws do not invalidate a settled static wallpaper. Native
        // transforms and wallpaper changes retain a short bounded compositor catch-up.
        if (sourceDirty || tickRequested || !ownDraw && (liveWallpaper || now < nativeMotionUntil)) {
            if (!busy) {
                if (now - lastRequest >= FRAME_MS) {
                    tickRequested = false
                    view.removeCallbacks(tick)
                    if (!request(view, now)) return
                } else scheduleTick(view, FRAME_MS - (now - lastRequest))
            }
        }
        if (liveWallpaper && !busy) scheduleTick(view)
    }

    private fun prepareMatrix(view: View): Boolean {
        world.reset()
        view.transformMatrixToGlobal(world)
        if (hasLegacy) {
            val host = view.parent as? View ?: return false
            hostMatrix.reset()
            host.transformMatrixToGlobal(hostMatrix)
            if (!hostMatrix.invert(inverseHost)) return false
            correction.setConcat(hostMatrix, legacy)
            correction.preConcat(inverseHost)
            world.postConcat(correction)
        }
        // transformMatrixToGlobal maps to the root's global coordinates. Add the actual window
        // origin, retaining fractional transforms instead of rounding each icon/panel location.
        rootMatrix.reset()
        view.rootView.transformMatrixToGlobal(rootMatrix)
        rootMatrix.getValues(rootValues)
        view.rootView.getLocationOnScreen(location)
        world.postTranslate(location[0] - rootValues[Matrix.MTRANS_X], location[1] - rootValues[Matrix.MTRANS_Y])
        world.getValues(values)
        return values.all { it.isFinite() } && values[Matrix.MPERSP_0] == 0f &&
            values[Matrix.MPERSP_1] == 0f && values[Matrix.MPERSP_2] == 1f
    }

    private fun request(view: View, now: Long): Boolean {
        if (viewportDirty) {
            // Resources may describe the app area after system-bar insets. The wallpaper
            // compositor uses the full default display, including status/navigation bars.
            val viewport = ModuleHelper.guarded<Rect?>(null) {
                view.context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
            } ?: return false
            if (viewport.left != 0 || viewport.top != 0 || viewport.isEmpty) return false
            viewportWidth = viewport.width(); viewportHeight = viewport.height()
            viewportDirty = false
        }
        points[0] = 0f; points[1] = 0f
        points[2] = view.width.toFloat(); points[3] = 0f
        points[4] = view.width.toFloat(); points[5] = view.height.toFloat()
        points[6] = 0f; points[7] = view.height.toFloat()
        world.mapPoints(points)
        val margin = 32f * view.resources.displayMetrics.density
        val requestCrop = Rect(
            floor((minOf(points[0], points[2], points[4], points[6]) - margin).coerceAtLeast(0f) / 4f).toInt() * 4,
            floor((minOf(points[1], points[3], points[5], points[7]) - margin).coerceAtLeast(0f) / 4f).toInt() * 4,
            minOf(viewportWidth, ceil((maxOf(points[0], points[2], points[4], points[6]) + margin) / 4f).toInt() * 4),
            minOf(viewportHeight, ceil((maxOf(points[1], points[3], points[5], points[7]) + margin) / 4f).toInt() * 4))
        if (requestCrop.isEmpty || maxOf(points[0], points[2], points[4], points[6]) <= 0f ||
            minOf(points[0], points[2], points[4], points[6]) >= viewportWidth ||
            maxOf(points[1], points[3], points[5], points[7]) <= 0f ||
            minOf(points[1], points[3], points[5], points[7]) >= viewportHeight) return false
        val scale = minOf(1f, MAX_WIDTH.toFloat() / requestCrop.width(), MAX_HEIGHT.toFloat() / requestCrop.height())
        val requestEpoch = epoch
        busy = true
        lastRequest = now
        val target = WeakReference(this)
        val captureSource = sampler
        val captureWidth = viewportWidth
        val captureHeight = viewportHeight
        scope.launch {
            var captured: Bitmap? = null
            try {
                captured = captureSource.capture(requestCrop, scale, captureWidth, captureHeight)
                withContext(Dispatchers.Main.immediate) {
                    val current = target.get()
                    if (current != null) {
                        current.accept(captured, requestCrop, requestEpoch, scale)
                        captured = null
                    }
                }
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                if (error is CancellationException) throw error
                withContext(Dispatchers.Main.immediate) {
                    target.get()?.let { current ->
                        current.busy = false
                        // A wallpaper replacement, hide or release also invalidates failures.
                        // Only the current capture may retire this decoration's lens.
                        if (!current.released && !current.broken && current.visible && current.epoch == requestEpoch)
                            current.unavailable()
                        else if (!current.released && !current.broken && current.visible)
                            current.owner.get()?.postInvalidateOnAnimation()
                    }
                }
            } finally {
                captured?.recycle()
            }
        }
        return true
    }

    internal fun accept(frame: Bitmap?, frameCrop: Rect, requestEpoch: Int, scale: Float) {
        busy = false
        if (released || broken || !visible || epoch != requestEpoch) {
            frame?.recycle()
            if (!released && !broken && visible) owner.get()?.postInvalidateOnAnimation()
            return
        }
        if (frame == null) {
            unavailable()
            return
        }
        if (frame.isRecycled || frame.width > MAX_WIDTH + 1 || frame.height > MAX_HEIGHT + 1 || frameCrop.isEmpty ||
            kotlin.math.abs(frame.width - frameCrop.width() * scale) > 1f || kotlin.math.abs(frame.height - frameCrop.height() * scale) > 1f) {
            frame?.recycle()
            fail(IllegalStateException("wallpaper-only capture returned no usable SDR frame"))
            return
        }
        val lens = shader ?: DockGlassOptics.createLens()?.also { shader = it }
            ?: run { frame.recycle(); fail(IllegalStateException("lens shader unavailable")); return }
        val view = owner.get()
        if (view == null || !prepareMatrix(view) || !coveredBy(view, frameCrop)) {
            frame.recycle()
            sourceDirty = true
            view?.postInvalidateOnAnimation()
            return
        }
        try {
            val input = BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            }
            lens.setInputShader("content", input)
            lens.setFloatUniform("sampleSize", frame.width.toFloat(), frame.height.toFloat())
            val previous = bitmap
            bitmap = frame
            crop.set(frameCrop)
            sampleScaleX = frame.width.toFloat() / crop.width()
            sampleScaleY = frame.height.toFloat() / crop.height()
            sourceDirty = false
            sourceUnavailable = false
            unavailableTries = 0
            paint.shader = lens
            if (!reportedTexture) {
                reportedTexture = true
                XposedHelpers.log("Dock lens: wallpaper texture ${frame.width}x${frame.height} from ${viewportWidth}x${viewportHeight}")
            }
            // Native RenderThread recordings retain their own image references. The owner no
            // longer reads the previous Java Bitmap after replacing the shader input.
            previous?.recycle()
            completionDraw = true
            owner.get()?.let { view ->
                view.postInvalidateOnAnimation()
                if (liveWallpaper) scheduleTick(view)
            }
        } catch (error: Throwable) {
            frame.recycle()
            FatalErrors.unwrapAndRethrowIfFatal(error)
            fail(error)
        }
    }

    /** Compositor readiness is transient. One visible generation gets a finite
     * 64/160/400 ms recovery window, then waits for a genuine source change. */
    private fun unavailable() {
        busy = false
        sourceDirty = false
        sourceUnavailable = true
        val delay = when (unavailableTries++) { 0 -> 64L; 1 -> 160L; 2 -> 400L; else -> return }
        owner.get()?.let { scheduleTick(it, delay) }
    }

    private fun scheduleTick(view: View, delay: Long = FRAME_MS) {
        view.removeCallbacks(tick)
        view.postDelayed(tick, delay)
    }

    private fun fail(error: Throwable) {
        busy = false
        if (broken || released) return
        broken = true
        clearTexture()
        owner.get()?.let { it.removeCallbacks(tick); it.postInvalidateOnAnimation() }
        XposedHelpers.log("Dock lens: wallpaper capture/rendering retired; using native material", error)
    }

    fun prepare(canvas: Canvas, bounds: Rect, radius: Float, density: Float): Boolean {
        if (!canvas.isHardwareAccelerated || !visible || broken || bitmap == null || bounds.isEmpty) return false
        val view = owner.get() ?: return false
        val lens = shader ?: return false
        return try {
            if (!prepareMatrix(view)) return false
            // Require the WHOLE panel to be backed by real captured pixels. Never stretch the
            // last edge of an old crop across a moved Dock while a newer capture is pending.
            if (!coveredBy(view, crop)) {
                sourceDirty = true
                if (!busy) scheduleTick(view)
                return false
            }
            val x = values[Matrix.MTRANS_X]
            val y = values[Matrix.MTRANS_Y]
            motion = if (lastX.isFinite()) (kotlin.math.abs(x - lastX) + kotlin.math.abs(y - lastY)).div(maxOf(density * 24f, 1f)).coerceIn(0f, 1f) else 0f
            lastX = x; lastY = y
            updateLensUniforms(lens, bounds, radius, density)
            if (lensPathBounds != bounds || lensPathRadius != radius) {
                lensPathBounds.set(bounds); lensPathRadius = radius
                lensPath.rewind()
                DockCornerGeometry.outline(lensPath, bounds.width().toFloat(), bounds.height().toFloat(), radius)
                lensPath.offset(bounds.left.toFloat(), bounds.top.toFloat())
            }
            true
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            fail(error)
            false
        }
    }

    fun drawPrepared(canvas: Canvas): Boolean = try {
        canvas.drawPath(lensPath, paint)
        true
    } catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        fail(error)
        false
    }

    fun draw(canvas: Canvas, bounds: Rect, radius: Float, density: Float): Boolean =
        prepare(canvas, bounds, radius, density) && drawPrepared(canvas)

    private fun updateLensUniforms(lens: RuntimeShader, bounds: Rect, radius: Float, density: Float) {
        val next = nextLensUniforms
        next[0] = bounds.left.toFloat(); next[1] = bounds.top.toFloat()
        next[2] = bounds.width().toFloat(); next[3] = bounds.height().toFloat()
        next[4] = radius; next[5] = density
        next[6] = values[Matrix.MSCALE_X] * sampleScaleX
        next[7] = values[Matrix.MSKEW_X] * sampleScaleX
        next[8] = (values[Matrix.MTRANS_X] - crop.left) * sampleScaleX
        next[9] = values[Matrix.MSKEW_Y] * sampleScaleY
        next[10] = values[Matrix.MSCALE_Y] * sampleScaleY
        next[11] = (values[Matrix.MTRANS_Y] - crop.top) * sampleScaleY
        next[12] = motion
        // Exact equality preserves subpixel motion. A fresh shader must receive every value.
        if (lensUniformsValid && next.contentEquals(lensUniforms)) return
        lens.setFloatUniform("origin", next[0], next[1])
        lens.setFloatUniform("size", next[2], next[3])
        lens.setFloatUniform("radius", next[4])
        lens.setFloatUniform("density", next[5])
        lens.setFloatUniform("sampleX", next[6], next[7], next[8])
        lens.setFloatUniform("sampleY", next[9], next[10], next[11])
        lens.setFloatUniform("motion", next[12])
        next.copyInto(lensUniforms)
        lensUniformsValid = true
    }

    private fun coveredBy(view: View, area: Rect): Boolean {
        points[0] = 0f; points[1] = 0f
        points[2] = view.width.toFloat(); points[3] = 0f
        points[4] = view.width.toFloat(); points[5] = view.height.toFloat()
        points[6] = 0f; points[7] = view.height.toFloat()
        world.mapPoints(points)
        for (i in 0..6 step 2) if (points[i] < area.left || points[i] > area.right ||
            points[i + 1] < area.top || points[i + 1] > area.bottom) return false
        return true
    }

    private fun clearTexture() {
        paint.shader = null
        bitmap?.recycle()
        bitmap = null
        // A RuntimeShader retains its input shader too. Release it on invalidation; lazily
        // rebuild only when a fresh frame arrives, never leave an old wallpaper referenced.
        shader = null
        lensUniformsValid = false
    }

    fun release() {
        released = true
        visible = false
        epoch++
        owner.get()?.removeCallbacks(tick)
        scope.cancel()
        clearTexture()
        owner.clear()
    }

    companion object {
        private const val FRAME_MS = 34L
        internal const val MAX_WIDTH = 1024
        internal const val MAX_HEIGHT = 384
    }
}
