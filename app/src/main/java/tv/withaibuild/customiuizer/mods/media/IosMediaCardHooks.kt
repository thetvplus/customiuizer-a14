package tv.withaibuild.customiuizer.mods.media

import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Presentation on the native PLAYER. No session, notification, input or animation owner. */
internal object IosMediaCardHooks {
    private const val OWNER = "a14.media.card.presentation"
    private const val CONTROLLER = "a14.media.card.controller"

    fun install(param: PackageReadyParam): FeatureInstallResult {
        if (param.packageName != "com.android.systemui") return FeatureInstallResult.FAILED_PERMANENT
        val api = Api(param.classLoader)
        return PreparedHookBatch().install(api.hooks())
    }

    private class Api(private val loader: ClassLoader) {
        private fun type(name: String) = Class.forName(name, false, loader)
        private val controller = type("com.android.systemui.media.controls.ui.MediaViewController")
        private val layout = type("com.android.systemui.util.animation.TransitionLayout")
        private val kind = type("com.android.systemui.media.controls.ui.MediaViewController\$TYPE")
        private val panel = type("com.android.systemui.media.controls.ui.MediaControlPanel")
        private val miuiPanel = type("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaControlPanel")
        private val data = type("com.android.systemui.media.controls.models.player.MediaData")
        private val sets = type("androidx.constraintlayout.widget.ConstraintSet")
        private val attach = method(controller, "attach", layout, kind)
        private val load = method(controller, "loadLayoutForType", kind)
        private val refresh = method(controller, "refreshState")
        private val bind = method(miuiPanel, "bindPlayer", data, String::class.java)
        private val context = field(controller, "context", Context::class.java)
        private val collapsed = field(controller, "collapsedLayout", sets)
        private val expanded = field(controller, "expandedLayout", sets)
        private val panelController = field(panel, "mMediaViewController", controller)
        private val presentation = MediaCardLayout(sets)
        // Names and types, never sample-specific integer IDs. Keep even the native
        // effects and guts controls: their constructor/listener contracts still run.
        private val required = linkedMapOf(
            "album_art" to ImageView::class.java,
            "media_bg" to type("com.android.systemui.statusbar.notification.mediacontrol.PlayerTwoCircleView"),
            "icon" to ImageView::class.java,
            "touch_ripple_view" to type("com.android.systemui.surfaceeffects.ripple.MultiRippleView"),
            "turbulence_noise_view" to type("com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseView"),
            "header_title" to TextView::class.java, "header_artist" to TextView::class.java,
            "media_explicit_indicator" to type("com.android.internal.widget.CachingIconView"),
            "media_seamless" to ViewGroup::class.java, "media_seamless_image" to ImageView::class.java,
            "media_seamless_text" to TextView::class.java, "media_seamless_button" to View::class.java,
            "media_progress_bar" to android.widget.SeekBar::class.java,
            "media_elapsed_time" to TextView::class.java, "media_total_time" to TextView::class.java,
            "media_scrubbing_elapsed_time" to TextView::class.java, "media_scrubbing_total_time" to TextView::class.java,
            "actionPlayPause" to android.widget.ImageButton::class.java,
            "actionNext" to android.widget.ImageButton::class.java, "actionPrev" to android.widget.ImageButton::class.java,
            "action0" to android.widget.ImageButton::class.java, "action1" to android.widget.ImageButton::class.java,
            "action2" to android.widget.ImageButton::class.java, "action3" to android.widget.ImageButton::class.java,
            "action4" to android.widget.ImageButton::class.java, "remove_text" to TextView::class.java,
            "cancel" to View::class.java, "cancel_text" to TextView::class.java,
            "dismiss" to ViewGroup::class.java, "dismiss_text" to TextView::class.java,
            "settings" to android.widget.ImageButton::class.java,
            "media_action_barrier_top" to type("androidx.constraintlayout.widget.Barrier"),
        )

        init {
            require(ViewGroup::class.java.isAssignableFrom(layout) && panel.isAssignableFrom(miuiPanel) && kind.isEnum)
            require(requireNotNull(kind.enumConstants).any { (it as Enum<*>).name == "PLAYER" })
            require(!Modifier.isFinal(collapsed.modifiers) && !Modifier.isFinal(expanded.modifiers))
        }

        fun hooks(): List<Pair<Method, MethodHook>> = listOf(
            attach to object : MethodHook() {
                override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                    val native = callback.getThisObject()!!
                    style(native)?.disable()
                    XposedHelpers.removeAdditionalInstanceField(native, CONTROLLER)
                    if ((callback.getArg(1) as? Enum<*>)?.name != "PLAYER") return@guarded
                    val root = callback.getArg(0) as ViewGroup
                    if (root.resources.configuration.fontScale > 1.3f) return@guarded
                    val ids = required.mapValues { (name, expected) ->
                        val id = root.resources.getIdentifier(name, "id", "com.android.systemui")
                        require(id != 0 && expected.isInstance(root.findViewById<View>(id))) { "Unsupported media control: $name" }
                        id
                    }
                    val previous = (XposedHelpers.getAdditionalInstanceField(root, OWNER) as? WeakReference<*>)?.get() as? Style
                    val existing = previous?.takeIf { it.matches(root) }
                    if (previous !== existing) previous?.disable()
                    val style = existing ?: Style(root, ids).also {
                        root.addOnAttachStateChangeListener(it)
                        XposedHelpers.setAdditionalInstanceField(root, OWNER, WeakReference(it))
                    }
                    style.dirty = true
                    XposedHelpers.setAdditionalInstanceField(native, CONTROLLER, WeakReference(style))
                    try { style.apply() }
                    catch (error: Throwable) {
                        XposedHelpers.removeAdditionalInstanceField(native, CONTROLLER)
                        style.disable()
                        throw error
                    }
                }
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    if (callback.getThrowable() != null) {
                        val native = callback.getThisObject()!!
                        style(native)?.disable()
                        XposedHelpers.removeAdditionalInstanceField(native, CONTROLLER)
                    }
                }
            },
            load to object : MethodHook() {
                override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                    val native = callback.getThisObject()!!
                    val style = style(native) ?: return@guarded
                    if (!style.supported()) {
                        style.disable()
                        XposedHelpers.removeAdditionalInstanceField(native, CONTROLLER)
                        // The original load now reinstates its current native XML
                        // sets, before its own measurement/cache refresh.
                    } else style.dirty = true
                }
            },
            refresh to object : MethodHook() {
                override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                    val native = callback.getThisObject()!!
                    val style = style(native) ?: return@guarded
                    if (!style.dirty) return@guarded
                    val first = collapsed.get(native)!!; val second = expanded.get(native)!!
                    try {
                        val density = (context.get(native) as Context).resources.displayMetrics.density
                        val newFirst = presentation.prepare(first, style.ids, density, compact = true)
                        val newSecond = presentation.prepare(second, style.ids, density)
                        style.apply()
                        // Both preparations succeeded. Native refresh then clears its
                        // measured states before any setCurrentState consumes them.
                        collapsed.set(native, newFirst)
                        try { expanded.set(native, newSecond) }
                        catch (error: Throwable) { collapsed.set(native, first); throw error }
                        style.dirty = false
                    } catch (error: Throwable) {
                        XposedHelpers.removeAdditionalInstanceField(native, CONTROLLER)
                        style.disable()
                        throw error
                    }
                }
            },
            bind to object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    if (callback.getThrowable() == null) style(panelController.get(callback.getThisObject()))?.apply()
                }
            },
        )

        private fun style(native: Any?) = (XposedHelpers.getAdditionalInstanceField(native, CONTROLLER) as? WeakReference<*>)?.get() as? Style

        private fun method(owner: Class<*>, name: String, vararg args: Class<*>) = owner.getDeclaredMethod(name, *args).apply {
            require(returnType == Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true
        }
        private fun field(owner: Class<*>, name: String, expected: Class<*>) = XposedHelpers.findField(owner, name).apply {
            require(expected.isAssignableFrom(type) && !Modifier.isStatic(modifiers)); isAccessible = true
        }
    }

    private class Style(root: ViewGroup, val ids: Map<String, Int>) : View.OnAttachStateChangeListener {
        private val root = WeakReference(root)
        private val outline = root.outlineProvider
        private val clipped = root.clipToOutline
        private val art = root.findViewById<View>(ids.getValue("album_art"))
        private val artOutline = art.outlineProvider
        private val artClipped = art.clipToOutline
        private var radius = 24f * root.resources.displayMetrics.density
        private val rounded = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        private val artwork = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius / 2f)
        }
        var dirty = true
        fun supported() = root.get()?.resources?.configuration?.fontScale?.let { it <= 1.3f } == true
        fun matches(view: ViewGroup) = root.get() === view && view.findViewById<View>(ids.getValue("album_art")) === art
        fun apply() {
            val view = root.get() ?: return
            val next = 24f * view.resources.displayMetrics.density
            val changed = next != radius
            radius = next
            if (view.outlineProvider !== rounded) view.outlineProvider = rounded
            if (!view.clipToOutline) view.clipToOutline = true
            if (art.outlineProvider !== artwork) art.outlineProvider = artwork
            if (!art.clipToOutline) art.clipToOutline = true
            if (changed) { view.invalidateOutline(); art.invalidateOutline() }
        }
        fun disable() {
            root.get()?.let { it.removeOnAttachStateChangeListener(this); XposedHelpers.removeAdditionalInstanceField(it, OWNER) }
            restore()
        }
        fun restore() {
            root.get()?.let { if (it.outlineProvider === rounded) { it.outlineProvider = outline; it.clipToOutline = clipped } }
            if (art.outlineProvider === artwork) { art.outlineProvider = artOutline; art.clipToOutline = artClipped }
        }
        override fun onViewAttachedToWindow(view: View) = apply()
        override fun onViewDetachedFromWindow(view: View) = restore()
    }
}
