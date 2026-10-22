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

/** Worker-only contract. The crop is in DEFAULT DISPLAY coordinates, never Launcher pixels. */
internal fun interface DockWallpaperSampler {
    fun capture(crop: Rect, scale: Float): Bitmap?
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
        val args = builder.newInstance()
        setCrop.invoke(args, crop)
        setScale.invoke(args, scale)
        setUid.invoke(args, Process.myUid().toLong())
        val result = capture.invoke(null, build.invoke(args)) ?: return null
        val buffer = hardwareBuffer.invoke(result) as? HardwareBuffer ?: return null
        return try {
            // Do not reinterpret protected or HDR content as an SDR wallpaper texture.
            if (secure.invoke(result) == true || hdr.invoke(result) == true) null
            else Bitmap.wrapHardwareBuffer(buffer, colorSpace.invoke(result) as? ColorSpace)
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
