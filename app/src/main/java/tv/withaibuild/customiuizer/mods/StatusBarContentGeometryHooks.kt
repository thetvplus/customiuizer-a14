package tv.withaibuild.customiuizer.mods

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarContentGeometry
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.StatusBarSafeGeometry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * SystemUI view-layer vertical geometry for the status bar.
 *
 * Dual-row may size [MiuiPhoneStatusBarView] to the window. Single-row keeps the
 * inflated height unless a non-zero global offset needs window-space room.
 * [status_bar_contents] is the safe content block: reserved height, then
 * CENTER_VERTICAL, then translationY. No wrapper, no optical scan.
 */
object StatusBarContentGeometryHooks {

    private const val CONTENTS_ID = "status_bar_contents"
    private const val BIND_FIELD = "customiuizer_sb_content_geometry"
    private const val ORIGINAL_HEIGHT_FIELD = "customiuizer_sb_owner_orig_h"
    private const val CONTENTS_ORIG_HEIGHT_FIELD = "customiuizer_sb_contents_orig_h"
    private const val CONTENTS_ORIG_GRAVITY_FIELD = "customiuizer_sb_contents_orig_g"
    private const val SKIP_CONTENTS_FIELD = "customiuizer_sb_contents_skip"
    private const val PACKAGE_SYSTEMUI = "com.android.systemui"

    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) {
        Handler(Looper.getMainLooper())
    }
    private val attachedBars = ArrayList<WeakReference<View>>(2)

    private val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key != null &&
                key != StatusBarContentGeometry.PREF_KEY &&
                key != StatusBarHeightConfig.PREF_KEY &&
                key != StatusBarContentGeometry.DUAL_ROWS_PREF
            ) {
                return
            }
            mainHandler.post { applyToAttached() }
        }
    }

    @JvmStatic
    fun hook(lpparam: PackageReadyParam) {
        ModuleHelper.observePreferenceChange(preferenceObserver, this)
        val afterBind = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val sbView = param.getThisObject() as? View ?: return
                bind(sbView)
            }
        }
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            lpparam.classLoader,
            "onFinishInflate",
            afterBind,
        )
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            lpparam.classLoader,
            "onAttachedToWindow",
            afterBind,
        )
    }

    internal fun bind(statusBarView: View) {
        if (XposedHelpers.getAdditionalInstanceField(statusBarView, BIND_FIELD) == null) {
            XposedHelpers.setAdditionalInstanceField(statusBarView, BIND_FIELD, true)
            statusBarView.addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top == oldBottom - oldTop && (bottom - top) > 0) return@addOnLayoutChangeListener
                applyToView(v)
            }
            register(statusBarView)
        }
        applyToView(statusBarView)
    }

    internal fun applyToView(statusBarView: View) {
        try {
            val window = findStatusBarWindowView(statusBarView)
            val windowHeight = window?.height ?: 0
            val heightMutated = applyOwnerHeight(statusBarView, window, windowHeight)
            if (!heightMutated) {
                applySafeContents(statusBarView, windowHeight)
            }
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (td: ThreadDeath) {
            throw td
        } catch (vme: VirtualMachineError) {
            throw vme
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StatusBarContentGeometry: apply failed: ${t.javaClass.simpleName}")
        }
    }

    /**
     * @return true when owner height changed and a later layout pass must apply contents.
     */
    internal fun applyOwnerHeight(statusBarView: View, window: View?, windowHeight: Int): Boolean {
        val lp = statusBarView.layoutParams ?: return false
        captureOriginalHeight(statusBarView, lp.height)
        val dualRows = MainModule.mPrefs.getBoolean(StatusBarContentGeometry.DUAL_ROWS_PREF)
        val offsetPx = currentOffsetPx(statusBarView)
        val targetHeight = StatusBarContentGeometry.ownerTargetHeightPx(
            originalHeight(statusBarView, lp.height),
            windowHeight,
            dualRows,
            offsetPx,
        )
        var heightMutated = false
        if (lp.height != targetHeight) {
            lp.height = targetHeight
            statusBarView.layoutParams = lp
            heightMutated = true
        }
        restoreContainerTopGravity(statusBarView, window)
        return heightMutated
    }

    internal fun applySafeContents(statusBarView: View, windowHeight: Int) {
        if (windowHeight <= 0) return
        val contents = findContents(statusBarView) ?: return
        if (XposedHelpers.getAdditionalInstanceField(contents, SKIP_CONTENTS_FIELD) != null) {
            return
        }
        val flp = contents.layoutParams as? FrameLayout.LayoutParams
        if (flp == null) {
            if (XposedHelpers.getAdditionalInstanceField(contents, SKIP_CONTENTS_FIELD) == null) {
                XposedHelpers.setAdditionalInstanceField(contents, SKIP_CONTENTS_FIELD, true)
                XposedHelpers.log("StatusBarContentGeometry: skip contents, LayoutParams not FrameLayout")
            }
            return
        }
        captureContentsOriginal(contents, flp)
        val dualRows = MainModule.mPrefs.getBoolean(StatusBarContentGeometry.DUAL_ROWS_PREF)
        val offsetPx = currentOffsetPx(contents)
        val natural = StatusBarContentGeometry.naturalContentHeightPx(
            originalHeight(statusBarView, statusBarView.layoutParams?.height ?: 0),
            windowHeight,
            dualRows,
        )
        val layout = StatusBarSafeGeometry.resolve(windowHeight, natural, offsetPx)
        val nativePath = abs(offsetPx) < 0.5f
        if (nativePath) {
            restoreContentsOriginal(contents, flp)
            if (contents.translationY != 0f) contents.translationY = 0f
            return
        }
        val horizontal = flp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        val gravity = Gravity.CENTER_VERTICAL or horizontal
        var mutated = false
        if (flp.height != layout.safeContentHeightPx) {
            flp.height = layout.safeContentHeightPx
            mutated = true
        }
        if (flp.gravity != gravity) {
            flp.gravity = gravity
            mutated = true
        }
        if (mutated) contents.layoutParams = flp
        if (contents.translationY != layout.effectiveOffsetPx) {
            contents.translationY = layout.effectiveOffsetPx
        }
    }

    private fun currentOffsetPx(view: View): Float {
        val density = view.resources.displayMetrics.density
        val raw = MainModule.mPrefs.getInt(
            StatusBarContentGeometry.PREF_KEY,
            StatusBarContentGeometry.RAW_DEFAULT,
        )
        return StatusBarContentGeometry.resolveOffsetPx(raw, density)
    }

    private fun restoreContainerTopGravity(statusBarView: View, window: View?) {
        val container = statusBarView.parent as? View ?: return
        if (container === window) return
        val plp = container.layoutParams as? FrameLayout.LayoutParams ?: return
        val horizontal = plp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        val gravity = Gravity.TOP or horizontal
        if (plp.gravity == gravity) return
        plp.gravity = gravity
        container.layoutParams = plp
    }

    private fun captureOriginalHeight(statusBarView: View, currentLpHeight: Int) {
        if (XposedHelpers.getAdditionalInstanceField(statusBarView, ORIGINAL_HEIGHT_FIELD) != null) return
        XposedHelpers.setAdditionalInstanceField(statusBarView, ORIGINAL_HEIGHT_FIELD, currentLpHeight)
    }

    private fun originalHeight(statusBarView: View, fallback: Int): Int {
        return XposedHelpers.getAdditionalInstanceField(statusBarView, ORIGINAL_HEIGHT_FIELD) as? Int ?: fallback
    }

    private fun captureContentsOriginal(contents: View, flp: FrameLayout.LayoutParams) {
        if (XposedHelpers.getAdditionalInstanceField(contents, CONTENTS_ORIG_HEIGHT_FIELD) != null) return
        XposedHelpers.setAdditionalInstanceField(contents, CONTENTS_ORIG_HEIGHT_FIELD, flp.height)
        XposedHelpers.setAdditionalInstanceField(contents, CONTENTS_ORIG_GRAVITY_FIELD, flp.gravity)
    }

    private fun restoreContentsOriginal(contents: View, flp: FrameLayout.LayoutParams) {
        val origH = XposedHelpers.getAdditionalInstanceField(contents, CONTENTS_ORIG_HEIGHT_FIELD) as? Int ?: return
        val origG = XposedHelpers.getAdditionalInstanceField(contents, CONTENTS_ORIG_GRAVITY_FIELD) as? Int ?: 0
        var mutated = false
        if (flp.height != origH) {
            flp.height = origH
            mutated = true
        }
        if (flp.gravity != origG) {
            flp.gravity = origG
            mutated = true
        }
        if (mutated) contents.layoutParams = flp
    }

    private fun applyToAttached() {
        for (i in attachedBars.indices.reversed()) {
            val view = attachedBars[i].get()
            if (view == null || !view.isAttachedToWindow) {
                attachedBars.removeAt(i)
                continue
            }
            applyToView(view)
        }
    }

    private fun register(statusBarView: View) {
        for (i in attachedBars.indices.reversed()) {
            val existing = attachedBars[i].get()
            if (existing == null || existing === statusBarView) attachedBars.removeAt(i)
        }
        attachedBars.add(WeakReference(statusBarView))
    }

    private fun findContents(statusBarView: View): View? {
        val id = statusBarView.resources.getIdentifier(CONTENTS_ID, "id", PACKAGE_SYSTEMUI)
        if (id != 0) {
            val found = statusBarView.findViewById<View>(id)
            if (found != null) return found
        }
        return (statusBarView as? ViewGroup)?.getChildAt(0)
    }

    private fun findStatusBarWindowView(start: View): View? {
        var current: View? = start
        while (current != null) {
            if (current.javaClass.name.endsWith("StatusBarWindowView")) return current
            current = current.parent as? View
        }
        return start.rootView
    }
}
