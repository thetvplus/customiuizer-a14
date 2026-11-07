package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.hardware.HardwareBuffer
import android.os.Handler
import android.view.SurfaceControl
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import kotlin.math.sqrt

/** One short-lived, selected-window hardware texture in the existing Launcher window.
 * Native task/surface ownership and the ROM spring remain authoritative. Capture is
 * worker-only, secure/HDR results are declined, and no pixels are read back or saved. */
internal class LauncherWindowAbsorption internal constructor(private val api: NativeAccess, private val handler: Handler) {
    internal interface NativeAccess {
        fun current(element: Any, copy: Any): Boolean
        fun translucent(copy: Any): Boolean
        fun surface(element: Any, copy: Any): SurfaceControl?
        fun root(): View?
        fun capture(surface: SurfaceControl, width: Int, height: Int): Bitmap?
    }
    class Access(loader: ClassLoader) : NativeAccess {
        private val element = XposedHelpers.findClass("com.miui.home.recents.anim.WindowElement", loader)
        val update = element.getDeclaredMethod("updateElementValue", Float::class.javaPrimitiveType, RectF::class.java,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Rect::class.java, RectF::class.java)
        val cancellations = element.declaredMethods.filter { it.name in setOf("onFinishCompleted", "onAnimCancelByShellFinishedExecute",
            "cancelAnim", "cancelAnimWithoutEndAndEndListener", "cancelAnimWithoutEndListener") }
        private val current = element.getDeclaredField("currentRectFParams").apply { isAccessible = true }
        private val retained = element.getDeclaredField("remoteAnimationTargetSet").apply { isAccessible = true }
        private val params = XposedHelpers.findClass("com.miui.home.recents.anim.RectFParams", loader)
        private val targets = params.getDeclaredField("targetApps").apply { isAccessible = true }
        private val translucent = params.getDeclaredField("isUseTranslucentAnim").apply { isAccessible = true }
        private val apps = retained.type.getDeclaredField("apps").apply { isAccessible = true }
        private val remote = XposedHelpers.findClass("com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat", loader)
        private val leash = remote.getDeclaredField("leash").apply { isAccessible = true }
        private val nativeSurface = leash.type.getDeclaredField("mSurfaceControl").apply { isAccessible = true }
        private val launcher = XposedHelpers.findClass("com.miui.home.launcher.Application", loader).getDeclaredMethod("getLauncher")
        private val capture = Capture(loader)

        init { require(update.returnType == Void.TYPE && cancellations.isNotEmpty()) }
        override fun current(element: Any, copy: Any) = current.get(element) === copy
        override fun translucent(copy: Any) = translucent.getBoolean(copy)
        override fun surface(element: Any, copy: Any): SurfaceControl? {
            val set = targets.get(copy) ?: retained.get(element) ?: return null
            val target = (apps.get(set) as? Array<*>)?.singleOrNull() ?: return null
            return nativeSurface.get(leash.get(target)) as? SurfaceControl
        }
        override fun root(): View? = (launcher.invoke(null) as? Activity)?.window?.decorView
        override fun capture(surface: SurfaceControl, width: Int, height: Int) = capture.capture(surface, width, height)
    }

    internal class Capture(loader: ClassLoader) {
        private val captureType = Class.forName("android.window.ScreenCapture", false, loader)
        private val args = Class.forName("android.window.ScreenCapture\$LayerCaptureArgs", false, loader)
        private val builderType = Class.forName("android.window.ScreenCapture\$LayerCaptureArgs\$Builder", false, loader)
        private val builder = builderType.getConstructor(SurfaceControl::class.java)
        private val crop = builderType.getMethod("setSourceCrop", Rect::class.java)
        private val scale = builderType.getMethod("setFrameScale", Float::class.javaPrimitiveType)
        private val children = builderType.getMethod("setChildrenOnly", Boolean::class.javaPrimitiveType)
        private val secureCapture = builderType.getMethod("setCaptureSecureLayers", Boolean::class.javaPrimitiveType)
        private val build = builderType.getMethod("build")
        private val capture = captureType.getMethod("captureLayers", args)
        private val result = Class.forName("android.window.ScreenCapture\$ScreenshotHardwareBuffer", false, loader)
        private val buffer = result.getMethod("getHardwareBuffer")
        private val space = result.getMethod("getColorSpace")
        private val secure = result.getMethod("containsSecureLayers")
        private val hdr = result.getMethod("containsHdrLayers")

        fun capture(surface: SurfaceControl, width: Int, height: Int): Bitmap? {
            if (!surface.isValid || width <= 0 || height <= 0) return null
            val factor = captureScale(width, height)
            val request = builder.newInstance(surface)
            crop.invoke(request, Rect(0, 0, width, height)); scale.invoke(request, factor)
            children.invoke(request, true); secureCapture.invoke(request, false)
            val captured = capture.invoke(null, build.invoke(request)) ?: return null
            val hardware = buffer.invoke(captured) as? HardwareBuffer ?: return null
            return try {
                if (secure.invoke(captured) == true || hdr.invoke(captured) == true ||
                    hardware.usage and HardwareBuffer.USAGE_PROTECTED_CONTENT != 0L ||
                    hardware.width > MAX_CAPTURE_WIDTH + 1 ||
                    hardware.width.toLong() * hardware.height > MAX_CAPTURE_PIXELS) null
                else Bitmap.wrapHardwareBuffer(hardware, space.invoke(captured) as? ColorSpace)
            } finally { hardware.close() }
        }
    }

    @Volatile private var session: Session? = null
    private var epoch = 0L
    private var captureJob: Job? = null
    private val captureMutex = Mutex()
    private val diagnostics = IslandDiagnostics()

    fun begin(scope: CoroutineScope, element: Any, copy: Any, frame: IslandMusicReturnSignal.Frame, instant: Boolean = false): IslandReturnProgress.Producer? {
        if (!handler.looper.isCurrentThread) return null
        cancel()
        val root = api.root()?.takeIf { it.isAttachedToWindow && it.isHardwareAccelerated && it.isShown &&
            it.width == frame.screenWidth && it.height == frame.screenHeight && it.scaleX == 1f && it.scaleY == 1f &&
            it.alpha == 1f && it.display?.displayId == android.view.Display.DEFAULT_DISPLAY } ?: run {
                diagnostics.report("native-return-mesh-root-not-ready"); return null
            }
        val mesh = !instant && !api.translucent(copy)
        val surface = if (mesh || instant) {
            if (mesh && root.context.checkSelfPermission("android.permission.READ_FRAME_BUFFER") != PackageManager.PERMISSION_GRANTED) {
                diagnostics.report("native-return-mesh-capture-denied"); return null
            }
            api.surface(element, copy)?.takeIf { it.isValid } ?: return null
        } else null
        val active = Session(element, copy, root, surface, frame.landingRectangle(), if (mesh) frame.rectangle() else null, mesh, instant)
        active.contactLeft = frame.contactLeft; active.contactRight = frame.contactRight
        session = active
        val generation = epoch
        if (mesh && surface != null) captureJob = scope.launch(Dispatchers.Default) {
            var texture: Bitmap? = null
            try {
                texture = captureMutex.withLock {
                    ensureActive()
                    IslandAbsorptionMesh.prepare()
                    api.capture(surface, frame.screenWidth, frame.screenHeight)
                }
                withContext(Dispatchers.Main.immediate) {
                    if (generation == epoch && session === active && api.current(element, copy)) {
                        if (texture != null && active.progress < .55f) {
                            active.texture = texture; texture = null
                        } else {
                            diagnostics.report(if (texture == null) "native-return-mesh-no-safe-texture" else "native-return-mesh-texture-too-late")
                            // Only the optional texture failed. Keep the admitted native
                            // progress channel alive through its normal completion/cancel.
                        }
                    }
                }
            } finally { texture?.recycle() }
        }
        handler.postDelayed(active.timeout, IslandMotion.RETURN_TIMEOUT)
        return active.progressChannel
    }

    fun hides(element: Any) = session?.let { it.element.get() === element && it.presented && api.current(element, it.copy) } == true
    fun owns(element: Any) = session?.element?.get() === element

    fun frame(element: Any, rect: RectF, progress: Float, radius: Float, alpha: Float) {
        val active = session ?: return
        if (active.element.get() !== element) return
        val root = active.root.get()
        if (!api.current(element, active.copy) || root?.isAttachedToWindow != true || !root.isShown ||
            root.width != active.rootWidth || root.height != active.rootHeight || root.scaleX != 1f || root.scaleY != 1f ||
            root.alpha != 1f || root.display?.rotation != android.view.Surface.ROTATION_0 ||
            !progress.isFinite() || !alpha.isFinite() || !radius.isFinite() || rect.isEmpty ||
            !rect.left.isFinite() || !rect.top.isFinite() || !rect.right.isFinite() || !rect.bottom.isFinite()) { cancel(); return }
        // reset() temporarily leaves native width at zero until the first frame.
        // Latch the first actual return frame, never query that uninitialized width
        // or measure the gesture's earlier progress against a fullscreen baseline.
        if (!active.atCommit.isFinite()) {
            if (progress >= .96f || rect.width() <= active.target.width() * 1.05f) { cancel(); return }
            active.atCommit = progress.coerceIn(0f, 1f)
        }
        active.progress = IslandAbsorptionMesh.returnProgress(progress, active.atCommit)
        active.nativeAlpha = alpha.coerceIn(0f, 1f)
        // The receiving shell must react from the first native frames, even while
        // its optional texture is still being prepared. Tail compression is local
        // mesh geometry, not a second clock for the island's physical response.
        active.progressChannel.write(active.progress)
        if (active.instant) {
            if (!active.presented) {
                val surface = active.surface?.takeIf { it.isValid } ?: run { cancel(); return }
                // Hide only the admitted closing page. The existing lease restores it on cancellation.
                try {
                    active.queuedHide = true
                    SurfaceControl.Transaction().use { it.setAlpha(surface, 0f).apply() }
                    active.presented = true
                    root.addOnAttachStateChangeListener(active)
                } catch (error: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(error)
                    cancel()
                    throw error
                }
            }
            return
        }
        val drawing = active.drawing ?: return
        if (active.texture == null) {
            if (active.progress >= .55f) {
                // A queued request can no longer produce an equivalent takeover frame.
                // Cancellation also recycles an in-flight result without posting it to Main.
                captureJob?.cancel(); captureJob = null
            }
            return
        }
        if (!active.installed) {
            if (!IslandAbsorptionMesh.mayReplaceNative(active.progress, active.nativeAlpha)) {
                diagnostics.report("native-return-mesh-no-equivalent-frame"); cancel(); return
            }
            active.installed = true
            root.overlay.add(active)
            root.addOnAttachStateChangeListener(active)
        }
        root.getLocationOnScreen(drawing.location)
        drawing.rect.set(rect); drawing.rect.offset(-drawing.location[0].toFloat(), -drawing.location[1].toFloat())
        val remaining = drawing.handoff.progress(active.progress, drawing.rect, active.presented)
        drawing.leading = IslandAbsorptionMesh.leadingProgress(remaining)
        drawing.destination.set(active.target); drawing.destination.offset(-drawing.location[0].toFloat(), -drawing.location[1].toFloat())
        drawing.shellDestination.set(checkNotNull(active.shellTarget))
        drawing.shellDestination.offset(-drawing.location[0].toFloat(), -drawing.location[1].toFloat())
        val intakeY = IslandAbsorptionMesh.intakeY(drawing.shellDestination, drawing.destination)
        active.radius = radius.coerceAtLeast(0f)
        IslandAbsorptionMesh.fill(drawing.vertices, drawing.handoff.source, drawing.destination, remaining, drawing.rect, intakeY,
            occlusion = if (active.presented) drawing.shellDestination else null,
            contactLeft = active.contactLeft - drawing.location[0], contactRight = active.contactRight - drawing.location[0])
        // Keep release opacity until the entire page lies inside the island. Then
        // reveal the real island underneath, rather than leave a separately coloured dot.
        drawing.paint.alpha = (255f * active.nativeAlpha * IslandAbsorptionMesh.mergeOpacity(drawing.rect, drawing.shellDestination)).toInt().coerceIn(0, 255)
        active.setBounds(0, 0, root.width, root.height)
        drawing.clipReady = false
        active.invalidateSelf()
        if (!active.presented && active.committed == null) {
            // Leave the native window visible until an equivalent mesh frame is submitted.
            // No queued hide transaction can outlive cancellation or a new native owner.
            val committed = Runnable { handler.post {
                ModuleHelper.guarded {
                    // A later mesh frame does not invalidate an earlier submitted one.
                    // Re-arming per frame can starve this handoff during a fast return.
                    if (session === active && active.installed && active.surface?.isValid == true &&
                        active.element.get()?.let { api.current(it, active.copy) } == true && root.isAttachedToWindow) {
                        try {
                            active.queuedHide = true
                            SurfaceControl.Transaction().use { it.setAlpha(active.surface, 0f).apply() }
                            active.presented = true
                            diagnostics.report("native-return-mesh-presented")
                        } catch (error: Throwable) {
                            FatalErrors.unwrapAndRethrowIfFatal(error)
                            cancel()
                            throw error
                        }
                    }
                    active.committed = null
                }
            } }
            active.committed = committed
            root.viewTreeObserver.registerFrameCommitCallback(committed)
        }
    }

    fun lease(element: Any): Any? = session?.takeIf { it.element.get() === element }
    fun end(element: Any, lease: Any? = session, completed: Boolean = false) {
        if (lease != null && session === lease && session?.element?.get() === element) {
            // Native completion can follow a rounded final progress sample. Preserve
            // completion across unmapping so SystemUI can finish its own recoil.
            if (completed) session?.progressChannel?.write(1f)
            cancel(restoreNative = !completed)
        }
    }
    fun cancel(restoreNative: Boolean = true) {
        epoch++
        captureJob?.cancel(); captureJob = null
        val old = session ?: return
        session = null
        handler.removeCallbacks(old.timeout)
        try {
            ModuleHelper.guarded {
                old.root.get()?.let { root ->
                    if (root.viewTreeObserver.isAlive) old.committed?.let { root.viewTreeObserver.unregisterFrameCommitCallback(it) }
                    root.removeOnAttachStateChangeListener(old)
                    if (old.installed) root.overlay.remove(old)
                }
            }
            if (restoreNative && old.queuedHide && old.surface?.isValid == true && old.element.get()?.let { api.current(it, old.copy) } == true)
                SurfaceControl.Transaction().use { it.setAlpha(old.surface, old.nativeAlpha).apply() }
        } finally { old.progressChannel.close(); old.texture?.recycle(); old.texture = null }
    }

    /** Drawing state is owned only by an actual absorption session. */
    private class Mesh {
        val handoff = IslandAbsorptionHandoff()
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        val location = IntArray(2)
        val rect = RectF(); val destination = RectF(); val shellDestination = RectF()
        val path = Path()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var clipReady = false
        var clipPresented = false
        var leading = 0f

        fun clip(presented: Boolean, radius: Float): Path {
            if (clipReady && clipPresented == presented) return path
            path.rewind()
            if (!presented) {
                path.addRoundRect(rect, minOf(radius, rect.width() * .5f), minOf(radius, rect.height() * .5f), Path.Direction.CW)
            } else {
                // Mesh-edge clipping retains native rounded end caps without a rectangular
                // screenshot outline appearing while the middle is visibly deforming.
                val topLeft = vertices[0]; val topRight = vertices[2]
                // Native page corners flatten as the leading edge reaches the
                // island's middle; a rounded neck must not become an oval cap.
                val corner = minOf(radius * (1f - leading), (topRight - topLeft) * .5f, rect.height() * .2f)
                path.moveTo(topLeft + corner, rect.top)
                path.lineTo(topRight - corner, rect.top); path.quadTo(topRight, rect.top, topRight, rect.top + corner)
                val bottomLeft = vertices[IslandAbsorptionMesh.ROWS * 4]
                val bottomRight = vertices[IslandAbsorptionMesh.ROWS * 4 + 2]
                val bottomCorner = minOf(radius, (bottomRight - bottomLeft) * .5f, rect.height() * .2f)
                for (row in 1 until IslandAbsorptionMesh.ROWS) {
                    if (vertices[row * 4 + 3] > rect.top + corner && vertices[row * 4 + 3] < rect.bottom - bottomCorner)
                        path.lineTo(vertices[row * 4 + 2], vertices[row * 4 + 3])
                }
                path.lineTo(bottomRight, rect.bottom - bottomCorner)
                path.quadTo(bottomRight, rect.bottom, bottomRight - bottomCorner, rect.bottom)
                path.lineTo(bottomLeft + bottomCorner, rect.bottom)
                path.quadTo(bottomLeft, rect.bottom, bottomLeft, rect.bottom - bottomCorner)
                for (row in IslandAbsorptionMesh.ROWS - 1 downTo 1) {
                    if (vertices[row * 4 + 1] < rect.bottom - bottomCorner && vertices[row * 4 + 1] > rect.top + corner)
                        path.lineTo(vertices[row * 4], vertices[row * 4 + 1])
                }
                path.lineTo(topLeft, rect.top + corner); path.quadTo(topLeft, rect.top, topLeft + corner, rect.top); path.close()
            }
            clipReady = true; clipPresented = presented
            return path
        }
    }

    private inner class Session(element: Any, val copy: Any, root: View, val surface: SurfaceControl?,
        val target: RectF, val shellTarget: RectF?, mesh: Boolean, val instant: Boolean) : Drawable(), View.OnAttachStateChangeListener {
        val element = WeakReference(element)
        val rootWidth = root.width
        val rootHeight = root.height
        val root = WeakReference(root)
        val progressChannel = IslandReturnProgress.Producer()
        val drawing = if (mesh) Mesh() else null
        var contactLeft = target.left; var contactRight = target.right
        var texture: Bitmap? = null
        var progress = 0f; var radius = 0f
        var atCommit = Float.NaN
        var nativeAlpha = 1f
        var installed = false; var queuedHide = false
        @Volatile var presented = false
        var committed: Runnable? = null
        val timeout = Runnable { ModuleHelper.guarded { if (session === this) cancel() } }
        override fun draw(canvas: Canvas) {
            val bitmap = texture ?: return
            val drawing = drawing ?: return
            if (session !== this || drawing.paint.alpha == 0 || !canvas.isHardwareAccelerated || drawing.rect.isEmpty) return
            val clip = drawing.clip(presented, radius)
            val save = canvas.save()
            try {
                canvas.clipPath(clip)
                canvas.drawBitmapMesh(bitmap, 1, IslandAbsorptionMesh.ROWS, drawing.vertices, 0, null, 0, drawing.paint)
            } finally { canvas.restoreToCount(save) }
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Required by Drawable") override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = ModuleHelper.guarded { if (session === this) cancel() }
    }

    companion object {
        internal const val MAX_CAPTURE_WIDTH = 640
        internal const val MAX_CAPTURE_PIXELS = 320 * 1024
        internal fun captureScale(width: Int, height: Int): Float =
            minOf(1f, MAX_CAPTURE_WIDTH.toFloat() / width,
                // Leave headroom for compositor rounding while enforcing the hard result cap.
                sqrt((MAX_CAPTURE_PIXELS - 4096).toDouble() / (width.toDouble() * height)).toFloat())

        fun resolve(loader: ClassLoader, handler: Handler): Pair<Access, LauncherWindowAbsorption>? = try {
            val api = Access(loader)
            api to LauncherWindowAbsorption(api, handler)
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Island native window mesh unavailable; retaining native window flight", error)
            null
        }
    }
}
