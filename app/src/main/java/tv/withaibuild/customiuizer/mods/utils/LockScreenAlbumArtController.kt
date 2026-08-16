package tv.withaibuild.customiuizer.mods.utils

import android.app.WallpaperColors
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.LruCache
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.GlobalActions
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Lock-screen album art processor.
 *
 * One consumer, latest wins. Every request takes a generation number; the worker checks it
 * between the expensive stages and again before publishing, so skipping through a playlist
 * costs one visible result rather than one per track.
 *
 * The single-slot dispatcher is the whole concurrency model. The previous version had one
 * too, and then opened `withContext(Dispatchers.Default)` as the first statement inside it,
 * which handed the work straight back to the unbounded pool: two heavy passes really could
 * run at once, each allocating a full-screen ARGB_8888 frame. Nothing below may reintroduce
 * a dispatcher hop except the final one to Main, which does no work.
 *
 * Cancelling does not stop a running blur - fastBlur is a plain CPU loop with no suspension
 * points - so cancellation is treated as advisory and the generation check is what actually
 * keeps a stale frame off the screen.
 */
object LockScreenAlbumArtController {

    private const val BLUR_MAX_PIXELS = 512 * 512
    private const val APPLIED_DRAWABLE_FIELD = "custo_lock_screen_album_art_drawable"
    private const val LIFECYCLE_LISTENER_FIELD = "custo_lock_screen_album_art_lifecycle"

    private data class CacheKey(
        val sourceId: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val blur: Int,
        val rescale: Int,
        val grayscale: Boolean,
        val targetWidth: Int,
        val targetHeight: Int
    )

    private var albumArtCache: LruCache<CacheKey, Bitmap>? = null
    private var cacheBudgetBytes = 0

    private var miuiThemeUtilsClass: Class<*>? = null
    private var lastContextRef: WeakReference<Context>? = null
    private var lastViewRef: WeakReference<View>? = null
    private var isAod = false

    private var pendingSource: Bitmap? = null
    private var pendingBlur: Int = 0
    private var pendingRescale: Int = 1
    private var pendingGrayscale: Boolean = false

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawMatrix = Matrix()
    private val grayscaleFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    private val backgroundLifecycleListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = ModuleHelper.guarded {
            applyTo(view)
        }

        override fun onViewDetachedFromWindow(view: View) = ModuleHelper.guarded {
            clearViewBackground(view)
        }
    }

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1) + ModuleHelper.coroutineFailureHandler)
    private var generationJob: Job? = null

    /** Bumped by every new request; only the newest may publish. See [AlbumArtPolicy]. */
    private val requestGeneration = AtomicLong()

    @JvmStatic
    fun setMiuiThemeUtilsClass(cls: Class<*>) {
        miuiThemeUtilsClass = cls
    }

    @JvmStatic
    fun setAod(aod: Boolean) {
        if (isAod == aod) return
        isAod = aod
        if (!isAod) processPending()
    }

    /**
     * Drops everything held for the lock screen.
     *
     * Called when the feature cannot apply at all - no media, or a lock screen theme this
     * mod does not draw on. Without it the processed frames stayed in the cache for the rest
     * of the SystemUI process, which is the state the user spends most of the day in.
     */
    @JvmStatic
    fun clear() {
        requestGeneration.incrementAndGet()
        generationJob?.cancel()
        generationJob = null
        pendingSource = null
        albumArtCache?.evictAll()
        albumArtCache = null
        cacheBudgetBytes = 0
    }

    @JvmStatic
    fun updateMediaMetaData(context: Context, art: Bitmap?, blur: Int, rescale: Int, grayscale: Boolean) {
        lastContextRef = WeakReference(context)
        val cls = miuiThemeUtilsClass ?: return

        pendingSource = art
        pendingBlur = blur
        pendingRescale = rescale
        pendingGrayscale = grayscale

        val previousSource = XposedHelpers.getAdditionalStaticField(cls, "mAlbumArtSource") as Bitmap?
        if (art === previousSource && art != null) return
        if (art == null && previousSource == null) return

        XposedHelpers.setAdditionalStaticField(cls, "mAlbumArtSource", art)
        XposedHelpers.setAdditionalStaticField(cls, "mAlbumArt", null)

        if (art == null) {
            // Playback stopped. Nothing cached can be shown again, and every entry is a
            // full-screen frame, so let go of them here rather than at the next track.
            clear()
            sendUpdateBroadcast(context)
            return
        }

        if (isAod || !ScreenStateController.isScreenOn()) {
            // keep the raw source; process once the lock screen is visible
            return
        }

        val view = lastViewRef?.get()
        val targetW = view?.width ?: 0
        val targetH = view?.height ?: 0
        generate(context, art, blur, rescale, grayscale, targetW, targetH)
    }

    @JvmStatic
    fun applyTo(view: View): Boolean {
        ensureLifecycleListener(view)
        lastViewRef = WeakReference(view)
        val processed = getStaticAlbumArt()
        return if (processed != null && processed.width == view.width && processed.height == view.height && view.width > 0 && view.height > 0) {
            setViewBackground(view, processed)
            true
        } else if (pendingSource != null && view.width > 0 && view.height > 0) {
            val ctx = lastContextRef?.get() ?: view.context
            generate(ctx, pendingSource!!, pendingBlur, pendingRescale, pendingGrayscale, view.width, view.height)
            clearViewBackground(view)
            false
        } else {
            clearViewBackground(view)
            false
        }
    }

    private fun ensureLifecycleListener(view: View) {
        if (XposedHelpers.getAdditionalInstanceField(view, LIFECYCLE_LISTENER_FIELD) === backgroundLifecycleListener) return
        view.addOnAttachStateChangeListener(backgroundLifecycleListener)
        XposedHelpers.setAdditionalInstanceField(view, LIFECYCLE_LISTENER_FIELD, backgroundLifecycleListener)
    }

    private fun setViewBackground(view: View, bitmap: Bitmap) {
        val current = XposedHelpers.getAdditionalInstanceField(view, APPLIED_DRAWABLE_FIELD) as? BitmapDrawable
        if (current != null && view.background === current && current.bitmap === bitmap) {
            view.visibility = View.VISIBLE
            return
        }
        val drawable = BitmapDrawable(view.resources, bitmap)
        XposedHelpers.setAdditionalInstanceField(view, APPLIED_DRAWABLE_FIELD, drawable)
        view.background = drawable
        view.visibility = View.VISIBLE
    }

    private fun clearViewBackground(view: View) {
        val applied = XposedHelpers.removeAdditionalInstanceField(view, APPLIED_DRAWABLE_FIELD)
        if (applied != null && view.background === applied) {
            view.background = null
        }
    }

    private fun getStaticAlbumArt(): Bitmap? {
        val cls = miuiThemeUtilsClass ?: return null
        return try {
            XposedHelpers.getAdditionalStaticField(cls, "mAlbumArt") as Bitmap?
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (_: Throwable) {
            null
        }
    }

    private fun getStaticSource(): Bitmap? {
        val cls = miuiThemeUtilsClass ?: return null
        return try {
            XposedHelpers.getAdditionalStaticField(cls, "mAlbumArtSource") as Bitmap?
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (_: Throwable) {
            null
        }
    }

    private fun processPending() {
        val source = getStaticSource() ?: return
        pendingSource = source
        val context = lastContextRef?.get() ?: return
        val view = lastViewRef?.get()
        val targetW = view?.width ?: 0
        val targetH = view?.height ?: 0
        generate(context, source, pendingBlur, pendingRescale, pendingGrayscale, targetW, targetH)
    }

    /**
     * The cache for this target size, rebuilt when the size changes.
     *
     * Bounded by allocated bytes, not by entry count: three entries is three numbers on a
     * small screen and 31 MB on a tall one, and it was the tall one that mattered.
     */
    private fun cacheFor(targetWidth: Int, targetHeight: Int): LruCache<CacheKey, Bitmap>? {
        val budget = AlbumArtPolicy.cacheBudgetBytes(targetWidth, targetHeight)
        if (budget <= 0) return null

        val existing = albumArtCache
        if (existing != null && !AlbumArtPolicy.shouldRebuildCache(cacheBudgetBytes, budget)) {
            return existing
        }
        existing?.evictAll()
        cacheBudgetBytes = budget
        val rebuilt = object : LruCache<CacheKey, Bitmap>(budget) {
            override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.allocationByteCount
        }
        albumArtCache = rebuilt
        return rebuilt
    }

    private fun generate(context: Context, art: Bitmap, blur: Int, rescale: Int, grayscale: Boolean, targetWidth: Int, targetHeight: Int) {
        val generation = requestGeneration.incrementAndGet()
        generationJob?.cancel()
        generationJob = controllerScope.launch {
            val processed = process(context, art, blur, rescale, grayscale, targetWidth, targetHeight, generation)
            if (processed == null || !isCurrent(generation)) return@launch

            val wallpaperColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    WallpaperColors.fromBitmap(processed)
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
            if (!isCurrent(generation)) return@launch

            withContext(Dispatchers.Main) {
                if (isCurrent(generation)) applyResult(context, processed, wallpaperColors)
            }
        }
    }

    private fun isCurrent(generation: Long): Boolean =
        AlbumArtPolicy.shouldPublish(generation, requestGeneration.get())

    /**
     * Blur, scale and colour, checked for staleness between each stage.
     *
     * The checks are the only way out of a superseded request: each stage below is a plain
     * loop over pixels with nothing for a coroutine to cancel at.
     */
    private fun process(
        context: Context,
        art: Bitmap,
        blur: Int,
        rescale: Int,
        grayscale: Boolean,
        targetWidth: Int,
        targetHeight: Int,
        generation: Long
    ): Bitmap? {
        if (!isCurrent(generation)) return null

        val width = if (targetWidth > 0) targetWidth else getDisplayWidth(context)
        val height = if (targetHeight > 0) targetHeight else getDisplayHeight(context)
        if (width <= 0 || height <= 0) return null

        // Keyed on the source, not on the blurred intermediate. The old key hashed the
        // bitmap that came out of the blur - a new object every time - and recorded the blur
        // radius as a hard-coded 0, so it could never hit and the cache was pure cost.
        val key = CacheKey(
            sourceId = System.identityHashCode(art),
            sourceWidth = art.width,
            sourceHeight = art.height,
            blur = blur,
            rescale = rescale,
            grayscale = grayscale,
            targetWidth = width,
            targetHeight = height
        )
        val cache = cacheFor(width, height)
        cache?.get(key)?.let { return it }

        var blurred: Bitmap? = null
        var processed: Bitmap? = null
        try {
            if (!isCurrent(generation)) return null
            blurred = if (blur > 0) blurArt(art, blur) else art

            if (!isCurrent(generation)) return null
            processed = drawAlbumArt(blurred, rescale, grayscale, width, height) ?: return null

            if (!isCurrent(generation)) {
                recycleIntermediate(processed, art, null)
                processed = null
                return null
            }
            cache?.put(key, processed)
            return processed
        } finally {
            recycleIntermediate(blurred, art, processed)
        }
    }

    private fun blurArt(art: Bitmap, blur: Int): Bitmap? {
        val small = downsampleForBlur(art, BLUR_MAX_PIXELS)
        return try {
            val blurred = HookUtils.fastBlur(small, blur + 1)
            if (blurred != null) {
                recycleIntermediate(small, art, blurred)
                blurred
            } else {
                small
            }
        } catch (t: Throwable) {
            recycleIntermediate(small, art, null)
            throw t
        }
    }

    private fun recycleIntermediate(bitmap: Bitmap?, source: Bitmap, retained: Bitmap?) {
        if (bitmap != null && bitmap !== source && bitmap !== retained && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun downsampleForBlur(art: Bitmap, maxPixels: Int): Bitmap {
        val pixels = art.width.toLong() * art.height.toLong()
        if (pixels <= maxPixels) return art
        val ratio = sqrt(maxPixels.toDouble() / pixels).toFloat()
        val w = (art.width * ratio).toInt().coerceAtLeast(1)
        val h = (art.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(art, w, h, true)
    }

    private fun getDisplayWidth(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager? ?: return 1080
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.x
    }

    private fun getDisplayHeight(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager? ?: return 1920
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.y
    }

    /** Unchanged geometry: rescale 2 fits inside, everything else is CENTER_CROP. */
    private fun drawAlbumArt(bitmap: Bitmap?, rescale: Int, grayscale: Boolean, width: Int, height: Int): Bitmap? {
        if (bitmap == null) return null
        if (width <= 0 || height <= 0) return bitmap

        drawPaint.colorFilter = if (grayscale) grayscaleFilter else null
        drawMatrix.reset()

        val originalWidth = bitmap.width.toFloat()
        val originalHeight = bitmap.height.toFloat()
        // Default (rescale == 1) and cover (rescale == 3) use CENTER_CROP.
        val scale = if (rescale == 2) {
            Math.min(width / originalWidth, height / originalHeight)
        } else {
            Math.max(width / originalWidth, height / originalHeight)
        }
        val xTranslation = (width - originalWidth * scale) / 2.0f
        val yTranslation = (height - originalHeight * scale) / 2.0f
        drawMatrix.setScale(scale, scale)
        drawMatrix.preTranslate(xTranslation, yTranslation)

        val processed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(processed)
        canvas.drawBitmap(bitmap, drawMatrix, drawPaint)
        return processed
    }

    private fun applyResult(context: Context, processed: Bitmap?, wallpaperColors: WallpaperColors?) {
        val cls = miuiThemeUtilsClass ?: return
        XposedHelpers.setAdditionalStaticField(cls, "mAlbumArt", processed)
        sendUpdateBroadcast(context)

        if (processed != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wallpaperColors != null) {
            val updateFakeWallpaper = Intent("miui.intent.action.LOCK_WALLPAPER_CHANGED")
            updateFakeWallpaper.setPackage("com.android.systemui")
            val isWallpaperColorLight = (wallpaperColors.colorHints and 1) == 1
            updateFakeWallpaper.putExtra("is_wallpaper_color_light", isWallpaperColorLight)
            context.sendBroadcast(updateFakeWallpaper)
        }
    }

    private fun sendUpdateBroadcast(context: Context) {
        val updateAlbumWallpaper = Intent(GlobalActions.EVENT_PREFIX + "UPDATE_LS_ALBUM_ART")
        updateAlbumWallpaper.setPackage("com.android.systemui")
        context.sendBroadcast(updateAlbumWallpaper)
    }
}
