package tv.withaibuild.customiuizer.mods

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method

/** Opt in to the ROM's existing folder-only blur path. No launcher-identity spoof,
 * global property change, bitmap sampling, extra material or animation owner. */
internal object LauncherFolderIconBlur {
    fun install(loader: ClassLoader) {
        val blur = XposedHelpers.findClass("com.miui.home.launcher.common.BlurUtilities", loader)
        val device = XposedHelpers.findClass("com.miui.home.launcher.DeviceConfig", loader)
        val folder = XposedHelpers.findClass("com.miui.home.launcher.FolderIcon", loader)
        val small = XposedHelpers.findClass("com.miui.home.launcher.folder.FolderIcon1x1", loader)
        val large = XposedHelpers.findClass("com.miui.home.launcher.folder.FolderIcon2x2", loader)
        val image = XposedHelpers.findClass("com.miui.home.launcher.folder.LauncherFolder2x2IconImageView", loader)
        val wallpaper = XposedHelpers.findClassIfExists("com.miui.home.launcher.folder.FolderIcon1x1\$onWallpaperColorChanged\$2", loader)
        val launcher = XposedHelpers.findClass("com.miui.home.launcher.Launcher", loader)
        val info = XposedHelpers.findClass("com.miui.home.launcher.FolderInfo", loader)
        val supported = blur.getDeclaredField("IS_BACKGROUND_BLUR_SUPPORTED").apply { isAccessible = true }
        val enabled = blur.getDeclaredField("IS_BACKGROUND_BLUR_ENABLED").apply { isAccessible = true }
        val defaultIcon = device.getDeclaredMethod("isDefaultIcon")
        val gate = blur.getDeclaredMethod("isBlurSupported")
        val entries = listOf(
            folder.getDeclaredMethod("fromXml", Int::class.javaPrimitiveType, launcher, ViewGroup::class.java, info),
            folder.getDeclaredMethod("onLayout", Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            folder.getDeclaredMethod("lambda\$updateBackground\$3", folder, Drawable::class.java),
            small.getDeclaredMethod("drawChild", Canvas::class.java, View::class.java, Long::class.javaPrimitiveType),
            large.getDeclaredMethod("drawChild", Canvas::class.java, View::class.java, Long::class.javaPrimitiveType),
            folderIconWallpaperBlurEntry(small, wallpaper),
            image.getDeclaredMethod("resetDrawableToNormalState"),
            image.getDeclaredMethod("settingDrawableToAnimState"))
        require(gate.returnType == Boolean::class.javaPrimitiveType && supported.type == Boolean::class.javaPrimitiveType &&
            enabled.type == Boolean::class.javaPrimitiveType && defaultIcon.returnType == Boolean::class.javaPrimitiveType)
        installFolderIconBlurHooks(gate, entries) {
            // Keep hardware, system blur and theme restrictions. Only the native
            // isMiuiLauncher product-name restriction is omitted inside folder work.
            supported.getBoolean(null) && enabled.getBoolean(null) && defaultIcon.invoke(null) == true
        }
    }
}

/** The CN/TW APK uses an in-class Kotlin lambda; munch uses a Consumer class.
 * These are the two verified declarations, resolved before any gate is installed. */
internal fun folderIconWallpaperBlurEntry(folder: Class<*>, consumer: Class<*>?): Method {
    val lambda = try {
        folder.getDeclaredMethod("onWallpaperColorChanged\$lambda-3", folder, Drawable::class.java)
    } catch (_: NoSuchMethodException) { null }
    return (lambda ?: requireNotNull(consumer) { "Missing verified folder wallpaper callback" }
        .getDeclaredMethod("accept", Drawable::class.java)).also { require(it.returnType == Void.TYPE) }
}

internal class FolderIconBlurScope {
    private val depth = ThreadLocal<Int>()
    fun active() = (depth.get() ?: 0) > 0
    fun enter() { depth.set((depth.get() ?: 0) + 1) }
    fun exit() {
        val current = depth.get() ?: return
        if (current <= 1) depth.remove() else depth.set(current - 1)
    }
}

/** Enable the gate last, after every native preparation/draw entry is scoped. A
 * partial installation must never hide the original background without its blur. */
internal fun installFolderIconBlurHooks(gate: Method, entries: List<Method>, allowed: () -> Boolean) {
    val scope = FolderIconBlurScope()
    val hooks = ArrayList<CustomMethodUnhooker>(entries.size + 1)
    var installed = false
    var failure: Throwable? = null
    try {
        val entryHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                scope.enter()
                return try { chain.proceed() } finally { scope.exit() }
            }
        }
        for (entry in entries) hooks.add(checkNotNull(ModuleHelper.hookMethod(entry, entryHook)))
        hooks.add(checkNotNull(ModuleHelper.hookMethod(gate, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? =
                if (scope.active() && ModuleHelper.guarded(false, allowed)) true else chain.proceed()
        })))
        installed = true
    } catch (error: Throwable) {
        failure = error
        FatalErrors.unwrapAndRethrowIfFatal(error)
        throw error
    } finally {
        if (!installed) for (hook in hooks.asReversed()) try { hook.unhook() } catch (cleanup: Throwable) {
            failure?.let { primary ->
                try { FatalErrors.unwrapAndRethrowIfFatal(primary) } catch (fatal: Throwable) {
                    // Preserve the original fatal identity during a failed rollback.
                    if (cleanup !== fatal) fatal.addSuppressed(cleanup)
                    throw fatal
                }
            }
            FatalErrors.unwrapAndRethrowIfFatal(cleanup)
            XposedHelpers.log(cleanup)
        }
    }
}
