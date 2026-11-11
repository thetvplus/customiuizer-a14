package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Executable
import java.lang.reflect.Modifier

/** Opt in to the ROM's existing folder-only blur path. No launcher-identity spoof,
 * global property change, bitmap sampling, extra material or animation owner. */
internal object LauncherFolderIconBlur {
    fun installPoco(loader: ClassLoader, version: String?, context: Context) {
        folderIconBlurLog("requested POCO version=$version")
        try {
            val blur = XposedHelpers.findClass("com.miui.home.launcher.common.BlurUtilities", loader)
            if (hasPocoFolderBlurGate(blur)) {
                PocoFolderIconBlur.install(loader, context)
                installFolderBlurFade(loader)
            } else {
                install(loader, context)
                folderIconBlurLog("installed profile=legacy-folder-scope process=POCO")
            }
            folderIconBlurLog("POCO version=$version")
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            folderIconBlurLog("install-failed ${error.javaClass.name}: ${error.message}")
            throw error
        }
    }

    fun install(loader: ClassLoader, pocoContext: Context? = null) {
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
        require(ViewGroup::class.java.isAssignableFrom(folder) && folder.isAssignableFrom(small) &&
            folder.isAssignableFrom(large) && View::class.java.isAssignableFrom(image))
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
            enabled.type == Boolean::class.javaPrimitiveType && defaultIcon.returnType == Boolean::class.javaPrimitiveType &&
            Modifier.isStatic(gate.modifiers) && Modifier.isStatic(defaultIcon.modifiers) &&
            Modifier.isStatic(supported.modifiers) && Modifier.isStatic(enabled.modifiers))
        val returns = listOf(folder, Void.TYPE, Void.TYPE, Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Void.TYPE, Void.TYPE, Void.TYPE)
        for ((index, entry) in entries.withIndex()) {
            require(entry.returnType == returns[index] && Modifier.isStatic(entry.modifiers) ==
                (index == 0 || index == 2 || (index == 5 && entry.declaringClass == small)))
        }
        val allowed = {
            // Keep hardware, system blur and theme restrictions. Only the native
            // isMiuiLauncher product-name restriction is omitted inside folder work.
            supported.getBoolean(null) && enabled.getBoolean(null) && defaultIcon.invoke(null) == true
        }
        val container = pocoContext?.let { preparePocoFolderBlurContainer(loader, it, false, allowed) }
        val extras = container?.let { it.hooks() + (it.publish to it.controller.publicationHook()) }.orEmpty()
        val deoptimize = if (container == null) emptyList() else entries + container.publicationCaller
        installFolderIconBlurHooks(gate, entries, extras, deoptimize) {
            (container?.controller?.usable != false) && allowed()
        }
        installFolderBlurFade(loader)
    }
}

/** The transition bitmap contains only the preview items. The actual material stays on the
 * workspace icon, so its existing alpha track must remain visible over several frames. */
private fun installFolderBlurFade(loader: ClassLoader) {
    try {
        fun type(name: String) = XposedHelpers.findClass("com.miui.home.launcher.$name", loader)
        val folder = type("FolderIcon")
        val owner = type("folder.FolderAnimHelper")
        val image = folder.getDeclaredField("mIconImageView").apply { isAccessible = true }
        require(ImageView::class.java.isAssignableFrom(image.type) && !Modifier.isStatic(image.modifiers))
        val blurMode = checkedFolderBlurMethod(View::class.java, "getMiViewBlurMode", Int::class.javaPrimitiveType!!, false)
        val iconFade = checkedFolderBlurMethod(owner, "prepareFolderIconAnim", Void.TYPE, false,
            ImageView::class.java, type("FolderInfo"), Boolean::class.javaPrimitiveType!!)
        val alphaTrack = checkedFolderBlurMethod(owner, "prepareAlphaObjectAnim", Void.TYPE, false,
            View::class.java, Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, Interpolator::class.java)
        check(checkNotNull(XposedHelpers.moduleInst).deoptimize(iconFade)) { "Cannot deoptimize folder icon fade caller" }
        checkNotNull(ModuleHelper.hookMethod(alphaTrack, folderBlurFadeHook(folder) { view ->
            val background = image.get(view) as? ImageView
            background != null && (blurMode.invoke(background) as Int) != 0
        }))
    } catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        folderIconBlurLog("native-fade-unavailable ${error.javaClass.simpleName}")
    }
}

internal fun folderBlurFadeHook(folder: Class<*>, hasBlur: (View) -> Boolean): MethodHook {
    val curve = AccelerateDecelerateInterpolator()
    return object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val target = chain.getArg(0)
            if (target !is View || !folder.isInstance(target)) return chain.proceed()
            val from = chain.getArg(1) as Float
            val to = chain.getArg(2) as Float
            val nativeDuration = chain.getArg(3) as Long
            val duration = when (to) {
                0f -> 96L
                1f -> 144L
                else -> return chain.proceed()
            }
            if (from == to || nativeDuration <= 0L || nativeDuration >= duration ||
                !ModuleHelper.guarded(false) { hasBlur(target) }) return chain.proceed()
            val args = chain.args.toTypedArray()
            args[3] = duration
            args[5] = curve
            return chain.proceed(args)
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
internal fun installFolderIconBlurHooks(gate: Method, entries: List<Method>,
    extraHooks: List<Pair<Method, MethodHook>> = emptyList(), deoptimize: List<Executable> = emptyList(),
    allowed: () -> Boolean) {
    val scope = FolderIconBlurScope()
    val hooks = ArrayList<CustomMethodUnhooker>(entries.size + extraHooks.size + 1)
    var installed = false
    var failure: Throwable? = null
    try {
        for (caller in deoptimize) check(checkNotNull(XposedHelpers.moduleInst).deoptimize(caller)) {
            "Cannot deoptimize legacy folder blur caller: $caller"
        }
        for ((method, hook) in extraHooks) hooks.add(checkNotNull(ModuleHelper.hookMethod(method, hook)))
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
