package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicInteger

/** The 5.39.40.11641 / .11739 ABI has its own folder gate. Do not scope the old
 * isBlurSupported(): its callers, theme policy and material signature all changed. */
internal object PocoFolderIconBlur {
    fun install(loader: ClassLoader, context: Context) {
        fun type(name: String) = XposedHelpers.findClass("com.miui.home.launcher.$name", loader)
        val blur = type("common.BlurUtilities")
        val device = type("DeviceConfig")
        val folder = type("FolderIcon")
        val launcher = type("Launcher")
        val small = type("folder.FolderIcon1x1")
        val large = type("folder.FolderIcon2x2")
        val image = type("folder.LauncherFolder2x2IconImageView")
        require(ViewGroup::class.java.isAssignableFrom(folder) && folder.isAssignableFrom(small) &&
            folder.isAssignableFrom(large) && View::class.java.isAssignableFrom(image))
        val boolean = Boolean::class.javaPrimitiveType!!
        val int = Int::class.javaPrimitiveType!!
        val gate = checkedFolderBlurMethod(blur, "isFolderBlurSupported", boolean, true, boolean)
        val publish = checkedFolderBlurMethod(blur, "setBackgroundBlurEnabled", Void.TYPE, true, launcher)
        val render = checkedFolderBlurMethod(blur, "setFolderIconBlur", Void.TYPE, true,
            View::class.java, *Array(10) { int })
        val defaultFolder = checkedFolderBlurMethod(device, "isUseDefaultFolderIcon", boolean, true, boolean)
        val supported = blur.getDeclaredField("IS_BACKGROUND_BLUR_SUPPORTED").apply { isAccessible = true }
        val enabled = blur.getDeclaredField("IS_BACKGROUND_BLUR_ENABLED").apply { isAccessible = true }
        require(listOf(supported, enabled).all { it.type == boolean && Modifier.isStatic(it.modifiers) })
        val publicationCaller = pocoFolderBlurPublicationCaller(launcher) {
            XposedHelpers.findClass("org.aspectj.lang.JoinPoint", loader)
        }

        // Exact direct callers from both identified APKs. ART may have inlined the
        // short gate into these methods, including the 2x2 alpha constructor.
        // Deoptimize only these callers once, never the Launcher as a whole.
        val callers = listOf<Executable>(
            checkedFolderBlurMethod(folder, "setBlur", Void.TYPE, false),
            checkedFolderBlurMethod(folder, "lambda\$updateBackground\$3", Void.TYPE, false, folder, Drawable::class.java),
            checkedFolderBlurMethod(small, "drawChild", boolean, false, Canvas::class.java, View::class.java, Long::class.javaPrimitiveType!!),
            checkedFolderBlurMethod(large, "drawChild", boolean, false, Canvas::class.java, View::class.java, Long::class.javaPrimitiveType!!),
            checkedFolderBlurMethod(small, "refreshBackground", Void.TYPE, false),
            checkedFolderBlurMethod(large, "refreshBackground", Void.TYPE, false),
            checkedFolderBlurMethod(small, "updateFolderIconBg\$lambda-3", Void.TYPE, true, small, Drawable::class.java),
            checkedFolderBlurMethod(large, "updateFolderIconBg\$lambda-2", Void.TYPE, true, large, Drawable::class.java),
            checkedFolderBlurMethod(type("folder.FolderIcon4x4NormalBackgroundDrawable"), "initPaint", Void.TYPE, false),
            checkedFolderBlurMethod(type("folder.LauncherFolder2x2IconImageView\$onDragContainerBgAnimAlpha\$1\$1"),
                "onUpdate", Void.TYPE, false, Any::class.java, Collection::class.java),
            image.getDeclaredConstructor(Context::class.java, AttributeSet::class.java, int),
            checkedFolderBlurMethod(image, "getDrawableAlpha", Float::class.javaObjectType, false, Drawable::class.java),
            checkedFolderBlurMethod(image, "setAnimState", Void.TYPE, false, boolean),
            checkedFolderBlurMethod(type("BaseProgressShortcutIcon"), "showFolderCreateBackground", Void.TYPE, false, boolean),
            checkedFolderBlurMethod(type("DragView"), "calcAndStartAnimate", Void.TYPE, false, boolean),
            checkedFolderBlurMethod(type("DragView"), "setBlur", Void.TYPE, false, View::class.java, Boolean::class.javaObjectType),
            checkedFolderBlurMethod(type("FolderCling"), "initAnimFolderIcon", Void.TYPE, false, type("FolderInfo")),
            checkedFolderBlurMethod(type("anim.FolderCreateBgAnim\$1"), "onBegin", Void.TYPE, false, Any::class.java),
            checkedFolderBlurMethod(type("anim.FolderCreateBgAnim\$2"), "onComplete", Void.TYPE, false, Any::class.java),
            checkedFolderBlurMethod(type("anim.FolderCreateBgAnim"), "showFolderCreateBackground", Void.TYPE, false,
                boolean, boolean, IntArray::class.java),
            publicationCaller,
        )
        val framework = checkNotNull(XposedHelpers.moduleInst)
        val nativeDefault = framework.getInvoker(defaultFolder)
        val container = preparePocoFolderBlurContainer(loader, context, true) {
            supported.getBoolean(null) && enabled.getBoolean(null)
        }
        installPocoFolderBlurHooks(gate, publish, render, callers,
            readCapability = { FolderBlurCapability(supported.getBoolean(null), enabled.getBoolean(null)) },
            defaultFolder = { nativeDefault.invoke(null, it) == true },
            isFolderBackground = { isFolderIconBackground(it, folder) },
            container = container,
        )
        folderIconBlurLog("installed profile=poco-folder-gate hooks=6 callers=${callers.size} nativeContainer=true")
    }
}

/** Two verified native publication sites. Resolve before installing hooks so
 * cached flags cannot remain false when the publisher was inlined into onCreate. */
internal fun pocoFolderBlurPublicationCaller(launcher: Class<*>, joinPoint: () -> Class<*>): Method = try {
    checkedFolderBlurMethod(launcher, "onCreateBeforeSetCreatedFlag", Boolean::class.javaPrimitiveType!!, false)
} catch (_: NoSuchMethodException) {
    checkedFolderBlurMethod(launcher, "onCreate_aroundBody0", Void.TYPE, true,
        launcher, Bundle::class.java, joinPoint())
}

/** FolderCling, shortcut menus and creation overlays share the setter but keep
 * their own material. Only the image within a FolderIcon receives black tint. */
internal fun isFolderIconBackground(view: View, folder: Class<*>): Boolean {
    var parent = view.parent
    repeat(3) {
        if (folder.isInstance(parent)) return true
        parent = parent?.parent
    }
    return false
}

internal fun hasPocoFolderBlurGate(blur: Class<*>): Boolean = try {
    checkedFolderBlurMethod(blur, "isFolderBlurSupported", Boolean::class.javaPrimitiveType!!, true,
        Boolean::class.javaPrimitiveType!!)
    true
} catch (_: NoSuchMethodException) { false }

internal fun checkedFolderBlurMethod(owner: Class<*>, name: String, result: Class<*>, static: Boolean,
    vararg args: Class<*>): Method = owner.getDeclaredMethod(name, *args).also {
    require(it.returnType == result && Modifier.isStatic(it.modifiers) == static) { "Unsupported folder ABI: $it" }
}

internal data class FolderBlurCapability(val supported: Boolean, val enabled: Boolean) {
    val allowed get() = supported && enabled
}

/** Cache only native capability publication. Folder theme checks still invoke
 * the native size-specific policy, so a theme change is never frozen here. */
internal fun installPocoFolderBlurHooks(
    gate: Method, publish: Method, render: Method, callers: List<Executable>,
    readCapability: () -> FolderBlurCapability,
    defaultFolder: (Boolean) -> Boolean,
    isFolderBackground: (View) -> Boolean,
    container: PocoFolderBlurContainerPlan? = null,
) {
    val capability = object { @Volatile var value = readCapability() }
    val diagnostic = FolderBlurDiagnostic()
    val hooks = ArrayList<CustomMethodUnhooker>(if (container == null) 3 else 6)
    var installed = false
    var failure: Throwable? = null
    try {
        for (caller in callers) check(checkNotNull(XposedHelpers.moduleInst).deoptimize(caller)) {
            "Cannot deoptimize folder blur caller: $caller"
        }
        for ((method, hook) in container?.hooks().orEmpty()) {
            hooks.add(checkNotNull(ModuleHelper.hookMethod(method, hook)))
        }
        hooks.add(checkNotNull(ModuleHelper.hookMethod(publish, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                capability.value = FolderBlurCapability(false, false)
                val result = try { chain.proceed() } catch (native: Throwable) {
                    container?.controller?.publicationFailed(native)
                    throw native
                }
                try {
                    capability.value = readCapability()
                    container?.controller?.stateChanged()
                    diagnostic.once(1) { "native-state supported=${capability.value.supported} enabled=${capability.value.enabled}" }
                } catch (error: Throwable) {
                    capability.value = FolderBlurCapability(false, false)
                    container?.controller?.publicationFailed(error)
                    FatalErrors.unwrapAndRethrowIfFatal(error)
                    XposedHelpers.log(error)
                }
                return result
            }
        })))
        hooks.add(checkNotNull(ModuleHelper.hookMethod(render, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val view = chain.getArg(0) as? View
                val tint = capability.value.allowed && container?.controller?.usable != false && view != null &&
                    ModuleHelper.guarded(false) { isFolderBackground(view) }
                val args = if (tint) chain.args.toTypedArray().apply {
                    // First native blend layer: black RGB with the ROM's own
                    // dark/light alpha. Keep blend modes, tonal layer, bounds,
                    // radius and animation blur mode exactly as requested.
                    this[2] = (this[2] as Int) and 0xff000000.toInt()
                    this[4] = (this[4] as Int) and 0xff000000.toInt()
                } else null
                val result = if (args == null) chain.proceed() else chain.proceed(args)
                if (tint && view.width > 0 && view.height > 0) diagnostic.once(2) {
                    "native-render-returned size=${view.width}x${view.height} attached=${view.isAttachedToWindow} " +
                        "hardware=${view.isHardwareAccelerated} material=black visible=unverified"
                }
                return result
            }
        })))
        // Last installation activates only the folder gate. All other uses of
        // isBlurSupported(), including the open-folder animation, remain native.
        hooks.add(checkNotNull(ModuleHelper.hookMethod(gate, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val small = chain.getArg(0) == true
                val state = capability.value
                val result = if (state.allowed && container?.controller?.usable != false) ModuleHelper.guarded(false) { defaultFolder(small) }
                    else chain.proceed()
                diagnostic.once(if (small) { if (result == true) 4 else 8 } else { if (result == true) 16 else 32 }) {
                    "gate size=${if (small) "1x1" else "2x2"} result=$result supported=${state.supported} " +
                        "enabled=${state.enabled} defaultFolder=${if (state.allowed) result else "unchecked"}"
                }
                return result
            }
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
                    if (cleanup !== fatal) fatal.addSuppressed(cleanup)
                    throw fatal
                }
            }
            FatalErrors.unwrapAndRethrowIfFatal(cleanup)
            XposedHelpers.log(cleanup)
        }
    }
}

/** At most six messages per enabled process; no listener, timer, View or log
 * collector is retained. Successful rendering requests are not pixel evidence. */
internal class FolderBlurDiagnostic {
    private val printed = AtomicInteger()
    inline fun once(bit: Int, message: () -> String) {
        var before = printed.get()
        while (before and bit == 0) {
            if (printed.compareAndSet(before, before or bit)) {
                folderIconBlurLog(message())
                return
            }
            before = printed.get()
        }
    }
}

/** Also include the bounded evidence in an LSPosed export, not only logcat. */
internal fun folderIconBlurLog(message: String) = ModuleHelper.guarded {
    val line = "FolderIconBlur $message"
    XposedHelpers.log(line)
    XposedHelpers.moduleInst?.log(Log.INFO, "CustoMIUIzer", line)
}
