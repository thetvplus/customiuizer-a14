package tv.withaibuild.customiuizer.mods

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarContentGeometry
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.StatusBarSafeGeometry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import kotlin.math.abs

/** View-layer geometry only. Native window visibility, Insets and animations keep their owners. */
object StatusBarContentGeometryHooks {
    private const val CONTENTS_ID = "status_bar_contents"
    private const val BIND_FIELD = "customiuizer_sb_content_geometry"
    private const val PACKAGE_SYSTEMUI = "com.android.systemui"

    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) { Handler(Looper.getMainLooper()) }
    private val attachedBars = ArrayList<WeakReference<View>>(2)
    private val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key != null &&
                key != StatusBarContentGeometry.PREF_KEY &&
                key != StatusBarHeightConfig.PREF_KEY &&
                key != StatusBarContentGeometry.DUAL_ROWS_PREF
            ) return
            mainHandler.post { applyToAttached() }
        }
    }

    @JvmStatic
    fun hook(lpparam: PackageReadyParam) {
        ModuleHelper.observePreferenceChange(preferenceObserver, this)
        val afterBind = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                (param.getThisObject() as? View)?.let { bind(it) }
            }
        }
        for (method in arrayOf("onFinishInflate", "onAttachedToWindow")) {
            ModuleHelper.findAndHookMethod(
                "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
                lpparam.classLoader, method, afterBind,
            )
        }
        // The ROM writes height here on configuration/cutout updates, even if it happens
        // to equal our last value. Capture this native write before applying our layout.
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            lpparam.classLoader, "updateStatusBarHeight", NativeHeightCallback(),
        )
    }

    internal class NativeHeightCallback : MethodHook() {
        override fun after(param: AfterHookCallback) {
            if (param.isSkipped || param.getThrowable() != null) return
            (param.getThisObject() as? View)?.let { nativeHeightChanged(it) }
        }
    }

    internal fun bind(statusBarView: View) = ModuleHelper.guarded {
        val binding = binding(statusBarView) ?: Binding(statusBarView).also {
            XposedHelpers.setAdditionalInstanceField(statusBarView, BIND_FIELD, it)
            statusBarView.addOnLayoutChangeListener(it)
            statusBarView.addOnAttachStateChangeListener(it)
        }
        binding.refresh()
    }

    internal fun applyToView(statusBarView: View) = ModuleHelper.guarded {
        binding(statusBarView)?.refresh()
    }

    internal fun nativeHeightChanged(statusBarView: View) = ModuleHelper.guarded {
        binding(statusBarView)?.nativeHeightChanged(statusBarView)
    }

    private fun binding(view: View): Binding? =
        XposedHelpers.getAdditionalInstanceField(view, BIND_FIELD) as? Binding

    /**
     * One record per status-bar View. Parent listeners retain only this record, whose View
     * references are weak. Detach restores still-owned values and discards all baselines.
     * No draw listener, posted layout task or delayed correction is needed.
     */
    private class Binding(statusBarView: View) :
        View.OnLayoutChangeListener, View.OnAttachStateChangeListener {
        private val owner = WeakReference(statusBarView)
        private var window: WeakReference<View>? = null
        private var content: WeakReference<View>? = null
        private var container: WeakReference<View>? = null
        private var active = false
        private var applying = false
        private var contentsId = 0
        private var contentsIdResolved = false
        private val ownerHeight = NativeLayoutValue()
        private val contentsHeight = NativeLayoutValue()
        private val contentsGravity = NativeLayoutValue()
        private val containerGravity = NativeLayoutValue()
        private var nativeTranslation = 0f
        private var writtenTranslation = 0f
        private var ownsTranslation = false
        private var cachedLayout: StatusBarSafeGeometry.Layout? = null
        private var cachedWindowHeight = 0
        private var cachedNaturalHeight = 0
        private var cachedOffset = 0f
        private var cachedTranslation = 0f

        override fun onLayoutChange(
            v: View, left: Int, top: Int, right: Int, bottom: Int,
            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
        ) {
            if (!active || (v !== owner.get() && v !== window?.get())) return
            if (right - left == oldRight - oldLeft && bottom - top == oldBottom - oldTop) return
            ModuleHelper.guarded { refresh() }
        }

        override fun onViewAttachedToWindow(v: View) {
            if (v === owner.get()) ModuleHelper.guarded { refresh() }
        }

        override fun onViewDetachedFromWindow(v: View) {
            if (v !== owner.get()) return
            ModuleHelper.guarded {
                active = false
                disconnectWindow()
                unregister(v)
                release(v)
            }
        }

        fun nativeHeightChanged(view: View) {
            if (!active || owner.get() !== view) return
            view.layoutParams?.let { ownerHeight.nativeWrite(it.height) }
            refresh()
        }

        fun refresh() {
            if (applying) return
            val view = owner.get() ?: run { disconnectWindow(); return }
            if (!view.isAttachedToWindow) return
            applying = true
            try {
                if (!active) {
                    active = true
                    register(view)
                }
                val nextWindow = findStatusBarWindowView(view)
                if (window?.get() !== nextWindow) {
                    disconnectWindow()
                    release(view)
                    window = nextWindow?.let { WeakReference(it) }
                    nextWindow?.addOnLayoutChangeListener(this)
                }
                // Unknown ancestors and unmeasured/temporarily empty windows fail open.
                val windowHeight = nextWindow?.height ?: return
                if (windowHeight <= 0) return
                val lp = view.layoutParams ?: return
                val offset = currentOffsetPx(view)
                val dualRows = MainModule.mPrefs.getBoolean(StatusBarContentGeometry.DUAL_ROWS_PREF)
                val customLayout = dualRows || abs(offset) >= .5f
                val customHeight = MainModule.mPrefs.getInt(
                    StatusBarHeightConfig.PREF_KEY, StatusBarHeightConfig.DEFAULT_SENTINEL,
                ) > StatusBarHeightConfig.DEFAULT_SENTINEL
                val nativeHeight = ownerHeight.read(lp.height)
                val targetHeight = if (customLayout || customHeight) {
                    ownerHeight.write(lp.height, StatusBarContentGeometry.ownerTargetHeightPx(
                        nativeHeight, windowHeight, dualRows, offset,
                    ))
                } else ownerHeight.restore(lp.height)
                if (lp.height != targetHeight) {
                    lp.height = targetHeight
                    view.layoutParams = lp
                }
                applyContainer(view, nextWindow, customLayout)
                // Use the new window input now; waiting for a child-height callback can
                // miss updates when the child's frame remains unchanged in this pass.
                applyContents(view, windowHeight, nativeHeight, dualRows, offset)
            } finally {
                applying = false
            }
        }

        private fun applyContainer(view: View, windowView: View, customLayout: Boolean) {
            val next = (view.parent as? View)?.takeUnless { it === windowView }
            if (container?.get() !== next) {
                releaseContainer()
                container = next?.let { WeakReference(it) }
            }
            val parent = next ?: return
            val lp = parent.layoutParams as? FrameLayout.LayoutParams ?: run {
                containerGravity.clear()
                return
            }
            val nativeGravity = containerGravity.read(lp.gravity)
            val target = if (customLayout) containerGravity.write(lp.gravity,
                verticalGravity(nativeGravity, Gravity.TOP)) else containerGravity.restore(lp.gravity)
            if (lp.gravity != target) {
                lp.gravity = target
                parent.layoutParams = lp
            }
        }

        private fun applyContents(
            view: View, windowHeight: Int, nativeOwnerHeight: Int, dualRows: Boolean, offset: Float,
        ) {
            if (!contentsIdResolved) {
                contentsId = view.resources.getIdentifier(CONTENTS_ID, "id", PACKAGE_SYSTEMUI)
                contentsIdResolved = true
            }
            val next = if (contentsId != 0) view.findViewById<View>(contentsId)
                else (view as? ViewGroup)?.getChildAt(0)
            if (content?.get() !== next) {
                releaseContents()
                content = next?.let { WeakReference(it) }
            }
            val contents = next ?: return
            val lp = contents.layoutParams as? FrameLayout.LayoutParams ?: run {
                // A new native layout kind ends our old geometry ownership. Translation
                // can still be released without touching the replacement LayoutParams.
                releaseContents()
                return
            }
            val nativeHeight = contentsHeight.read(lp.height)
            val nativeGravity = contentsGravity.read(lp.gravity)
            val translation = contents.translationY
            if (!ownsTranslation || translation != writtenTranslation) {
                nativeTranslation = translation
                ownsTranslation = false
            }
            if (abs(offset) < .5f) {
                val height = contentsHeight.restore(lp.height)
                val gravity = contentsGravity.restore(lp.gravity)
                if (height != lp.height || gravity != lp.gravity) {
                    lp.height = height
                    lp.gravity = gravity
                    contents.layoutParams = lp
                }
                restoreTranslation(contents)
                cachedLayout = null
                return
            }
            val intrinsic = StatusBarContentGeometry.naturalContentHeightPx(
                nativeOwnerHeight, windowHeight, dualRows,
            )
            val natural = if (nativeHeight > 0) minOf(intrinsic, nativeHeight) else intrinsic
            val layout = resolve(windowHeight, natural, offset)
            val height = contentsHeight.write(lp.height, layout.safeContentHeightPx)
            val gravity = contentsGravity.write(lp.gravity, verticalGravity(nativeGravity, Gravity.CENTER_VERTICAL))
            if (height != lp.height || gravity != lp.gravity) {
                lp.height = height
                lp.gravity = gravity
                contents.layoutParams = lp
            }
            if (contents.translationY != layout.effectiveOffsetPx) {
                writtenTranslation = layout.effectiveOffsetPx
                ownsTranslation = true
                contents.translationY = writtenTranslation
            }
        }

        private fun resolve(windowHeight: Int, natural: Int, offset: Float): StatusBarSafeGeometry.Layout {
            val previous = cachedLayout
            if (previous != null && cachedWindowHeight == windowHeight && cachedNaturalHeight == natural &&
                cachedOffset == offset && cachedTranslation == nativeTranslation) return previous
            return StatusBarSafeGeometry.resolve(windowHeight, natural, offset, nativeTranslation).also {
                cachedLayout = it
                cachedWindowHeight = windowHeight
                cachedNaturalHeight = natural
                cachedOffset = offset
                cachedTranslation = nativeTranslation
            }
        }

        private fun restoreTranslation(contents: View) {
            if (ownsTranslation && contents.translationY == writtenTranslation &&
                contents.translationY != nativeTranslation) contents.translationY = nativeTranslation
            ownsTranslation = false
        }

        private fun releaseContents() {
            content?.get()?.let { contents ->
                (contents.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    val height = contentsHeight.restore(lp.height)
                    val gravity = contentsGravity.restore(lp.gravity)
                    if (height != lp.height || gravity != lp.gravity) {
                        lp.height = height
                        lp.gravity = gravity
                        contents.layoutParams = lp
                    }
                }
                restoreTranslation(contents)
            }
            contentsHeight.clear()
            contentsGravity.clear()
            ownsTranslation = false
            content = null
            cachedLayout = null
        }

        private fun releaseContainer() {
            container?.get()?.let { view ->
                (view.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    val gravity = containerGravity.restore(lp.gravity)
                    if (gravity != lp.gravity) {
                        lp.gravity = gravity
                        view.layoutParams = lp
                    }
                }
            }
            containerGravity.clear()
            container = null
        }

        private fun release(view: View) {
            releaseContents()
            releaseContainer()
            view.layoutParams?.let { lp ->
                val height = ownerHeight.restore(lp.height)
                if (height != lp.height) {
                    lp.height = height
                    view.layoutParams = lp
                }
            }
            ownerHeight.clear()
        }

        private fun disconnectWindow() {
            window?.get()?.removeOnLayoutChangeListener(this)
            window = null
        }
    }

    /** Only the four integer layout properties above use this local ownership record. */
    private class NativeLayoutValue {
        private var initialized = false
        private var owns = false
        private var native = 0
        private var written = 0

        fun read(current: Int): Int {
            if (!initialized || !owns || current != written) nativeWrite(current)
            return native
        }
        fun nativeWrite(current: Int) { native = current; initialized = true; owns = false }
        fun write(current: Int, target: Int): Int {
            if (current != target) { written = target; owns = true }
            return target
        }
        fun restore(current: Int): Int {
            val result = if (owns && current == written) native else current
            clear()
            return result
        }
        fun clear() { initialized = false; owns = false }
    }

    private fun verticalGravity(native: Int, vertical: Int): Int =
        (if (native < 0) 0 else native and Gravity.VERTICAL_GRAVITY_MASK.inv()) or vertical

    private fun currentOffsetPx(view: View): Float = StatusBarContentGeometry.resolveOffsetPx(
        MainModule.mPrefs.getInt(StatusBarContentGeometry.PREF_KEY, StatusBarContentGeometry.RAW_DEFAULT),
        view.resources.displayMetrics.density,
    )

    private fun applyToAttached() {
        for (i in attachedBars.indices.reversed()) {
            val view = attachedBars[i].get()
            if (view == null || !view.isAttachedToWindow) attachedBars.removeAt(i)
            else applyToView(view)
        }
    }

    private fun register(view: View) {
        var found = false
        for (i in attachedBars.indices.reversed()) {
            val existing = attachedBars[i].get()
            if (existing == null) attachedBars.removeAt(i)
            else if (existing === view) found = true
        }
        if (!found) attachedBars.add(WeakReference(view))
    }

    private fun unregister(view: View) {
        for (i in attachedBars.indices.reversed()) {
            val existing = attachedBars[i].get()
            if (existing == null || existing === view) attachedBars.removeAt(i)
        }
    }

    private fun findStatusBarWindowView(start: View): View? {
        var current = start.parent as? View
        while (current != null) {
            if (current.javaClass.name.endsWith("StatusBarWindowView")) return current
            current = current.parent as? View
        }
        // A plain rootView is not evidence of a status-bar window owner.
        return null
    }
}
