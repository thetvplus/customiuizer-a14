package tv.withaibuild.customiuizer.mods

import tv.withaibuild.customiuizer.mods.island.DynamicIslandActivities
import tv.withaibuild.customiuizer.mods.island.DynamicIslandStatusBarFade
import tv.withaibuild.customiuizer.mods.island.IslandActivityState
import tv.withaibuild.customiuizer.mods.island.IslandSizing
import tv.withaibuild.customiuizer.mods.island.IslandToastContent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Insets
import android.os.Bundle
import android.text.Spanned
import android.view.Gravity
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.ImageView
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.json.JSONObject
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal enum class ToastPosition(val value: Int, val gravity: Int) {
    SYSTEM(0, 0), TOP(1, Gravity.TOP), CENTER(2, Gravity.CENTER_VERTICAL), BOTTOM(3, Gravity.BOTTOM);

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

internal data class ToastPresentationConfig(val position: ToastPosition = ToastPosition.SYSTEM,
    val offsetDp: Int = 0, val capsule: Boolean = false,
    val minimumTimeoutMs: Long = 7000) {
    val enabled: Boolean get() = position != ToastPosition.SYSTEM || capsule
    companion object {
        fun read(prefs: PrefMap): ToastPresentationConfig {
            val bottom = prefs.getInt("system_toast_bottom", 0).coerceIn(0, 800)
            // Preserve the preceding bottom-only setting until a position is explicitly selected.
            val position = ToastPosition.fromValue(prefs.getStringAsInt("system_toast_position", if (bottom > 0) 3 else 0))
            val offset = when (position) {
                ToastPosition.TOP -> prefs.getInt("system_toast_top", 24).coerceIn(0, 800)
                ToastPosition.CENTER -> prefs.getInt("system_toast_center", 400).coerceIn(0, 800) - 400
                ToastPosition.BOTTOM -> bottom
                ToastPosition.SYSTEM -> 0
            }
            return ToastPresentationConfig(position, offset, prefs.getBoolean("system_toast_capsule"),
                maxOf(7000L, 5500L + (prefs.getInt("system_toasttime", 0).coerceIn(0, 19) - 4) * 1000L))
        }
    }
}

/** Keep ToastPresenter's queue, callbacks, accessibility event and token release unchanged. */
internal object SystemUIToastPresentation {
    private const val TEXT = "customiuizer_plain_toast_text"
    private const val LEASE = "customiuizer_plain_toast_capsule"
    private const val SOURCE = "customiuizer_plain_toast_source"
    private var volumeCapsule: NativeToastCapsule? = null

    fun showVolumeCapsule(text: String, displayId: Int): ToastCapsuleLease? =
        volumeCapsule?.show(text, "com.android.systemui", 2400L, displayId, allowDefault = true)
    fun updateVolumeCapsule(lease: ToastCapsuleLease, text: String): Boolean = volumeCapsule?.update(lease, text) == true
    fun ownsVolumeCapsule(lease: ToastCapsuleLease): Boolean = volumeCapsule?.owns(lease) == true
    fun releaseVolumeCapsule(lease: ToastCapsuleLease) { volumeCapsule?.release(lease) }

    fun install(lpparam: PackageReadyParam, config: ToastPresentationConfig, volume: Boolean = false) {
        val bridge = if (config.capsule || volume) installCapsule(lpparam) else null
        volumeCapsule = bridge
        if (!config.enabled) return
        val presenter = XposedHelpers.findClass("android.widget.ToastPresenter", lpparam.classLoader)
        val viewField = XposedHelpers.findField(presenter, "mView")
        val paramsField = XposedHelpers.findField(presenter, "mParams")
        val packageField = XposedHelpers.findField(presenter, "mPackageName")
        val timeoutField = if (bridge != null)
            XposedHelpers.findFieldIfExists(WindowManager.LayoutParams::class.java, "hideTimeoutMilliseconds") else null
        // Plugin/custom views are not safe to reinterpret as a plain text Toast.
        ModuleHelper.hookAllConstructors("com.android.systemui.toast.SystemUIToast", lpparam.classLoader,
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val args = callback.getArgs()
                    if (args.size < 4 || args[3] != null) return
                    val text = args[2] as? CharSequence ?: return
                    if (text is Spanned && text.getSpans(0, text.length, Any::class.java).isNotEmpty()) return
                    val view = XposedHelpers.callMethod(callback.getThisObject(), "getView") as? View ?: return
                    XposedHelpers.setAdditionalInstanceField(view, TEXT, text.toString())
                    // SystemUIToast receives the caller package and a user-aware native icon.
                    // ToastPresenter's own context package is not a trustworthy caller identity.
                    val pkg = (args.getOrNull(4) as? String)?.takeIf { it.isNotBlank() }
                    val icon = view.findViewById<ImageView>(android.R.id.icon)?.drawable?.constantState
                        ?.newDrawable(view.resources)?.mutate()
                    XposedHelpers.setAdditionalInstanceField(view, SOURCE, IslandToastContent(text.toString(), pkg, icon))
                }
            })
        ModuleHelper.findAndHookMethod(presenter, "addToastView", object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                val owner = callback.getThisObject() ?: return
                val view = viewField.get(owner) as? View ?: return
                val text = XposedHelpers.getAdditionalInstanceField(view, TEXT) as? String ?: return
                val params = paramsField.get(owner) as WindowManager.LayoutParams
                applyPosition(view, params, config.position, config.offsetDp)
                if (!config.capsule) return
                // The ROM forehead belongs to the default display; keep other Toast windows local.
                @Suppress("DEPRECATION")
                val displayId = view.context.getSystemService(WindowManager::class.java)?.defaultDisplay?.displayId ?: return
                val source = XposedHelpers.getAdditionalInstanceField(view, SOURCE) as? IslandToastContent
                    ?: IslandToastContent(text, null, null)
                val lease: Any = (if (SystemUIStrongToastHooks.currentSnapshot()?.isDynamicIsland == true) {
                    DynamicIslandActivities.showToast(source,
                        timeoutField?.getLong(params)?.takeIf { it > 0 } ?: config.minimumTimeoutMs, displayId)
                } else bridge?.show(text, source.packageName ?: packageField.get(owner) as String,
                    maxOf(config.minimumTimeoutMs, timeoutField?.getLong(params) ?: 7000L), displayId))
                    ?: return
                XposedHelpers.setAdditionalInstanceField(owner, LEASE, lease)
                // Skip only ordinary window insertion. Native show/hide still finish normally.
                callback.returnAndSkip(null)
            }
        })
        if (config.position != ToastPosition.SYSTEM) {
            // SystemUIToast.onOrientationChange updates the existing native window through this
            // method. Recompute against the new display and measured text before native relayout.
            ModuleHelper.findAndHookMethod(presenter, "updateLayoutParams", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, object : MethodHook() {
                    override fun before(callback: BeforeHookCallback) {
                        val owner = callback.getThisObject() ?: return
                        val view = viewField.get(owner) as? View ?: return
                        if (XposedHelpers.getAdditionalInstanceField(view, TEXT) == null) return
                        val params = paramsField.get(owner) as WindowManager.LayoutParams
                        applyPosition(view, params, config.position, config.offsetDp)
                        callback.getArgs()[1] = params.y
                        callback.getArgs()[3] = params.verticalMargin
                        // Preserve the horizontal gravity requested by the orientation update.
                        callback.getArgs()[4] = ((callback.getArg(4) as Int) and Gravity.VERTICAL_GRAVITY_MASK.inv()) or config.position.gravity
                    }
                })
        }
        ModuleHelper.hookAllMethods(presenter, "hide", object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                val owner = callback.getThisObject() ?: return
                when (val lease = XposedHelpers.removeAdditionalInstanceField(owner, LEASE)) {
                    is ToastCapsuleLease -> bridge?.release(lease)
                    is IslandActivityState.Lease -> DynamicIslandActivities.releaseToast(lease)
                }
                (viewField.get(owner) as? View)?.let {
                    XposedHelpers.removeAdditionalInstanceField(it, TEXT)
                    XposedHelpers.removeAdditionalInstanceField(it, SOURCE)
                }
            }
        })
    }

    private fun installCapsule(lpparam: PackageReadyParam): NativeToastCapsule? = try {
        val controller = XposedHelpers.findClass("com.android.systemui.toast.MIUIStrongToastControl", lpparam.classLoader)
        val root = XposedHelpers.findClass("com.android.systemui.toast.MIUIStrongToast", lpparam.classLoader)
        val bridge = NativeToastCapsule(controller, root) { SystemUIStrongToastHooks.currentSnapshot()?.mode }
        ModuleHelper.hookAllConstructors(controller, object : MethodHook() {
            override fun after(callback: AfterHookCallback) { bridge.capture(callback.getThisObject() ?: return) }
        })
        ModuleHelper.hookAllMethods(root, "showCustomStrongToast", object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                if (!bridge.onNativeShow(callback.getThisObject() as View)) callback.returnAndSkip(null)
            }
        })
        ModuleHelper.hookAllMethods(root, "onDetachedFromWindow", object : MethodHook() {
            override fun after(callback: AfterHookCallback) { bridge.onDetached(callback.getThisObject() as View) }
        })
        bridge
    } catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        XposedHelpers.log("Toast capsule: native contract unavailable; keeping ordinary Toast", error)
        null
    }

    @SuppressLint("WrongConstant") // The complement is an intermediate mask, preserving all nonvertical Gravity flags.
    internal fun applyPosition(view: View, params: WindowManager.LayoutParams, position: ToastPosition, offsetDp: Int) {
        if (position == ToastPosition.SYSTEM) return
        val manager = view.context.getSystemService(WindowManager::class.java) ?: return
        val metrics = manager.currentWindowMetrics
        // ToastPresenter fits bars ignoring visibility. Match that frame without adding insets twice.
        val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        val insets = Insets.max(metrics.windowInsets.getInsetsIgnoringVisibility(types), metrics.windowInsets.getInsets(types))
        val width = (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(1)
        val height = (metrics.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        val maximum = (height - view.measuredHeight).coerceAtLeast(0)
        val offset = (offsetDp * view.resources.displayMetrics.density).roundToInt()
        params.gravity = (params.gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or position.gravity
        params.verticalMargin = 0f
        params.verticalWeight = 0f
        params.y = if (position == ToastPosition.CENTER) {
            val above = maximum / 2
            offset.coerceIn(-above, maximum - above)
        } else offset.coerceIn(0, maximum)
    }
}

internal class ToastCapsuleLease(val root: WeakReference<View>, val token: Any, val timeoutMs: Long = 7000L)

/** Metadata is scoped to the SystemUI ClassLoader; the native controller and leases are weak. */
internal class NativeToastCapsule(controllerClass: Class<*>, rootClass: Class<*>,
    private val mode: () -> StrongToastPresentationMode?) {
    private val nativeRoot = XposedHelpers.findField(controllerClass, "mMiuiStrongToast")
    private val context = XposedHelpers.findField(controllerClass, "mContext")
    private val show = controllerClass.getMethod("showCustomStrongToast", Bundle::class.java)
    private val hide = rootClass.getMethod("hideStrongToast")
    private val textView = XposedHelpers.findField(rootClass, "mLeftTextView")
    private val category = XposedHelpers.findField(rootClass, "mCurrentToastCategory")
    private val handler = XposedHelpers.findFieldIfExists(rootClass, "mShowStrongToastHandler")
    private val hideRunnable = XposedHelpers.findFieldIfExists(rootClass, "mHideStrongToastRunnable")
    private val busy = arrayOf("mStrongToastShow", "mCheckStartAnimation", "mCheckInOutStrongToasting")
        .map { XposedHelpers.findField(rootClass, it) }
    private var controller = WeakReference<Any>(null)
    private data class Pending(val token: Any, val text: String, val room: Float, val height: Float,
        val mode: StrongToastPresentationMode)
    private var pending: Pending? = null

    fun capture(owner: Any) { controller = WeakReference(owner) }

    fun onDetached(root: View) { XposedHelpers.removeAdditionalInstanceField(root, TOKEN) }

    fun onNativeShow(root: View): Boolean {
        XposedHelpers.removeAdditionalInstanceField(root, TOKEN)
        val request = pending ?: return true
        val label = textView.get(root) as? TextView ?: return false
        val height = if (request.mode == StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT) {
            val manager = root.context.getSystemService(WindowManager::class.java) ?: return false
            val status = manager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            val id = root.resources.getIdentifier("strong_toast_bottom_view", "id", "com.android.systemui")
            val chin = if (id != 0) root.findViewById<View>(id) else null
            val chinHeight = if (chin == null || chin.visibility == View.GONE) 0
                else maxOf(chin.measuredHeight, chin.background?.intrinsicHeight ?: 0)
            SystemUIStrongToastHooks.resolveMatchContentHeightPx(status, chinHeight).toFloat()
        } else request.height
        if (label.paint.measureText(request.text) > request.room ||
            label.paint.fontSpacing > height) return false
        XposedHelpers.setAdditionalInstanceField(root, TOKEN, request.token)
        return true
    }

    fun show(text: String, packageName: String, timeout: Long, displayId: Int = Display.DEFAULT_DISPLAY,
        allowDefault: Boolean = false): ToastCapsuleLease? {
        if (displayId != Display.DEFAULT_DISPLAY) return null
        val currentMode = mode() ?: return null
        // Island mode suppresses this native category before window creation. Do not route a
        // volume fallback back through that suppression path or interrupt another island activity.
        if (allowDefault && currentMode == StrongToastPresentationMode.DYNAMIC_ISLAND) return null
        if (currentMode != StrongToastPresentationMode.DYNAMIC_ISLAND &&
            currentMode != StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT &&
            !(allowDefault && currentMode == StrongToastPresentationMode.SYSTEM_DEFAULT)) return null
        if (allowDefault && (handler == null || hideRunnable == null)) return null
        if (text.isBlank() || text.length > 2048 || text.any { it == '\n' || it == '\r' }) return null
        val owner = controller.get() ?: return null
        val existing = nativeRoot.get(owner) as? View
        if (existing != null && (existing.parent != null || busy.any { it.getBoolean(existing) })) return null
        val nativeContext = context.get(owner) as Context
        val label = existing?.let { textView.get(it) as? TextView } ?: TextView(nativeContext)
        val resources = nativeContext.resources
        val widthId = resources.getIdentifier("strong_toast_width", "dimen", "com.android.systemui")
        val heightId = resources.getIdentifier("strong_toast_height", "dimen", "com.android.systemui")
        if (widthId == 0 || heightId == 0) return null
        // Native text_bitmap owns half the row. Fit completely; never rely on marquee or clipping.
        val density = resources.displayMetrics.density
        val room = if (currentMode == StrongToastPresentationMode.DYNAMIC_ISLAND) {
            val metrics = nativeContext.getSystemService(WindowManager::class.java)?.currentWindowMetrics ?: return null
            val gap = maxOf((40 * density).roundToInt(), (metrics.windowInsets.displayCutout?.boundingRectTop?.width() ?: 0) + (16 * density).roundToInt())
            val snapshot = SystemUIStrongToastHooks.currentSnapshot()
            val width = IslandSizing.collapsed(density, metrics.bounds.width(),
                DynamicIslandStatusBarFade.compactClearance(metrics.bounds.width(), (IslandSizing.SHORT_DP * density).roundToInt()),
                gap, label.paint.measureText(text), romMaximum = resources.getDimensionPixelSize(widthId),
                shortDp = snapshot?.shortWidthDp ?: IslandSizing.SHORT_DP,
                longDp = snapshot?.longWidthDp ?: IslandSizing.LONG_MAX_DP,
                manual = snapshot?.automaticWidth == false)
            (width - gap) * .5f - IslandSizing.EDGE_DP * density
        } else resources.getDimensionPixelSize(widthId) * 0.5f - 22 * density
        // Dynamic mode derives its wing width from this paint. The native-show check
        // still validates the actual label/font before suppressing the ordinary Toast.
        val widthAllowance = if (currentMode == StrongToastPresentationMode.DYNAMIC_ISLAND) 1f else 1.15f
        if (label.paint.measureText(text) * widthAllowance > room) return null
        val token = Any()
        pending = Pending(token, text, room, if (currentMode == StrongToastPresentationMode.DYNAMIC_ISLAND)
            (SystemUIStrongToastHooks.currentSnapshot()?.islandHeightDp ?: IslandSizing.COLLAPSED_HEIGHT_DP) * density
            else resources.getDimensionPixelSize(heightId).toFloat(), currentMode)
        try {
            val accessibility = nativeContext.getSystemService(AccessibilityManager::class.java)
            val requested = timeout.coerceIn(if (allowDefault) 1L else 7000L, Int.MAX_VALUE.toLong())
            val duration = accessibility?.getRecommendedTimeoutMillis(requested.toInt(),
                AccessibilityManager.FLAG_CONTENT_TEXT)?.toLong() ?: requested
            val parameters = JSONObject().put("left", JSONObject().put("textParams", JSONObject()
                .put("text", text).put("textColor", Color.WHITE))).put("right", JSONObject())
            val bundle = Bundle().apply {
                putString("package_name", packageName)
                putString("strong_toast_category", CATEGORY)
                putLong("duration", duration)
                putString("param", parameters.toString())
            }
            show.invoke(owner, bundle)
            val root = nativeRoot.get(owner) as? View ?: return null
            val lease = ToastCapsuleLease(WeakReference(root), token, duration)
            if (root.parent != null && busy.first().getBoolean(root) &&
                XposedHelpers.getAdditionalInstanceField(root, TOKEN) === token &&
                category.get(root) == CATEGORY &&
                (textView.get(root) as? TextView)?.text?.toString() == text) {
                if (allowDefault && !update(lease, text)) { release(lease); return null }
                return lease
            }
            release(lease)
            if (root.parent == null) releaseUnattached(root)
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            (nativeRoot.get(owner) as? View)?.let { root ->
                ModuleHelper.guarded { release(ToastCapsuleLease(WeakReference(root), token)) }
                if (root.parent == null) ModuleHelper.guarded { releaseUnattached(root) }
            }
            XposedHelpers.log("Toast capsule: keeping ordinary Toast", error)
        } finally { pending = null }
        return null
    }

    fun release(lease: ToastCapsuleLease) {
        val root = lease.root.get() ?: return
        if (XposedHelpers.getAdditionalInstanceField(root, TOKEN) !== lease.token) return
        XposedHelpers.removeAdditionalInstanceField(root, TOKEN)
        if (category.get(root) == CATEGORY && busy.first().getBoolean(root) && !busy[2].getBoolean(root)) hide.invoke(root)
    }

    fun owns(lease: ToastCapsuleLease): Boolean {
        val currentMode = mode()
        if (currentMode != StrongToastPresentationMode.SYSTEM_DEFAULT &&
            currentMode != StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT) return false
        val root = lease.root.get() ?: return false
        return root.parent != null && busy.first().getBoolean(root) && !busy[2].getBoolean(root) && category.get(root) == CATEGORY &&
            XposedHelpers.getAdditionalInstanceField(root, TOKEN) === lease.token
    }

    fun update(lease: ToastCapsuleLease, text: String): Boolean {
        if (!owns(lease)) return false
        val root = lease.root.get() ?: return false
        val label = textView.get(root) as? TextView ?: return false
        val nativeHandler = handler?.get(root) as? android.os.Handler ?: return false
        val hide = hideRunnable?.get(root) as? Runnable ?: return false
        if (label.text.toString() != text) label.text = text
        nativeHandler.removeCallbacks(hide)
        nativeHandler.postDelayed(hide, lease.timeoutMs)
        return true
    }

    private fun releaseUnattached(root: View) {
        SystemUIStrongToastHooks.resetMatchModeCapsule(root)
        DynamicIslandStatusBarFade.release(root)
    }

    companion object {
        private const val TOKEN = "customiuizer_toast_capsule_token"
        private const val CATEGORY = "text_bitmap"
    }
}
