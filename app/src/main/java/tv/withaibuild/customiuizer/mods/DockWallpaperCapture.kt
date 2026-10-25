package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Process
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.roundToInt

/** Worker-only contract. The crop is in DEFAULT DISPLAY coordinates, never Launcher pixels. */
internal fun interface DockWallpaperSampler {
    fun capture(crop: Rect, scale: Float): Bitmap?
    fun capture(crop: Rect, scale: Float, viewportWidth: Int, viewportHeight: Int): Bitmap? = capture(crop, scale)
}

/** Some A14 wallpaper extensions ignore sourceCrop and return the whole wallpaper.
 * Accept that only when its dimensions prove the supplied default-display viewport. */
internal object DockWallpaperFrame {
    fun crop(width: Int, height: Int, region: Rect, scale: Float, viewportWidth: Int, viewportHeight: Int): Rect? {
        if (!scale.isFinite() || scale <= 0f || region.isEmpty) return null
        if (abs(width - region.width() * scale) <= 1f && abs(height - region.height() * scale) <= 1f)
            return Rect(0, 0, width, height)
        if (viewportWidth <= 0 || viewportHeight <= 0 || region.left < 0 || region.top < 0 ||
            region.right > viewportWidth || region.bottom > viewportHeight ||
            abs(width - viewportWidth * scale) > 1f || abs(height - viewportHeight * scale) > 1f) return null
        return Rect((region.left * scale).roundToInt(), (region.top * scale).roundToInt(),
            (region.right * scale).roundToInt().coerceAtMost(width),
            (region.bottom * scale).roundToInt().coerceAtMost(height)).takeUnless { it.isEmpty }
    }
}

/**
 * fuxi A14's WallpaperManager.Globals.getCurrentWallpaperSnapshot uses this exact wallpaper-only
 * compositor API. It includes live wallpaper; it does not capture Launcher, icons or other apps.
 * No wallpaper-file/preview fallback: those do not prove compositor crop/zoom/offset alignment.
 * Metadata is resolved once per Launcher installation. Capture (including its native bounded
 * fence wait) runs on the owning decoration's coroutine worker, never the UI/render callback.
 */
internal class DockWallpaperCapture private constructor(
    private val builder: Constructor<*>,
    private val setCrop: Method,
    private val setScale: Method,
    private val setUid: Method,
    private val build: Method,
    private val capture: Method,
    private val hardwareBuffer: Method,
    private val colorSpace: Method,
    private val secure: Method,
    private val hdr: Method,
) : DockWallpaperSampler {
    override fun capture(crop: Rect, scale: Float): Bitmap? {
        return capture(crop, scale, 0, 0)
    }

    override fun capture(crop: Rect, scale: Float, viewportWidth: Int, viewportHeight: Int): Bitmap? {
        val args = builder.newInstance()
        setCrop.invoke(args, crop)
        setScale.invoke(args, scale)
        setUid.invoke(args, Process.myUid().toLong())
        val result = capture.invoke(null, build.invoke(args)) ?: return null
        val buffer = hardwareBuffer.invoke(result) as? HardwareBuffer ?: return null
        return try {
            // Do not reinterpret protected or HDR content as an SDR wallpaper texture.
            if (secure.invoke(result) == true || hdr.invoke(result) == true) null
            else {
                val full = Bitmap.wrapHardwareBuffer(buffer, colorSpace.invoke(result) as? ColorSpace) ?: return null
                val region = DockWallpaperFrame.crop(full.width, full.height, crop, scale, viewportWidth, viewportHeight)
                if (region == null) { full.recycle(); return null }
                if (region.left == 0 && region.top == 0 && region.width() == full.width && region.height() == full.height) full
                else try {
                    // API 34 hardware Bitmap subsets stay on the GPU (verified on fuxi).
                    Bitmap.createBitmap(full, region.left, region.top, region.width(), region.height())
                } finally { full.recycle() }
            }
        } finally {
            // wrapHardwareBuffer retains its own native reference; release the capture's handle.
            buffer.close()
        }
    }

    companion object {
        fun resolve(loader: ClassLoader): DockWallpaperSampler? = try {
            val captureType = Class.forName("android.window.ScreenCapture", false, loader)
            val args = Class.forName("android.window.ScreenCapture\$WallpaperCaptureArgs", false, loader)
            val builder = Class.forName("android.window.ScreenCapture\$WallpaperCaptureArgs\$Builder", false, loader)
            val result = Class.forName("android.window.ScreenCapture\$ScreenshotHardwareBuffer", false, loader)
            DockWallpaperCapture(builder.getConstructor(), builder.getMethod("setSourceCrop", Rect::class.java),
                builder.getMethod("setFrameScale", Float::class.javaPrimitiveType),
                builder.getMethod("setUid", Long::class.javaPrimitiveType), builder.getMethod("build"),
                captureType.getMethod("captureWallpaperLayer", args), result.getMethod("getHardwareBuffer"),
                result.getMethod("getColorSpace"), result.getMethod("containsSecureLayers"),
                result.getMethod("containsHdrLayers"))
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Dock lens: wallpaper compositor capture unavailable", error)
            null
        }

        fun permitted(context: Context): Boolean =
            context.checkSelfPermission("android.permission.READ_FRAME_BUFFER") == PackageManager.PERMISSION_GRANTED
    }
}
