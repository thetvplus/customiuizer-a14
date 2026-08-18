package tv.withaibuild.customiuizer.mods

import android.graphics.Outline
import android.graphics.Rect
import android.view.Gravity
import android.view.TouchDelegate
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.IslandRecallHit
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicReference

/**
 * StrongToast presentation hooks for HyperOS 1.
 *
 * Both mutating modes reshape the ROM row in place from `getWindowParam` and share one restore
 * baseline: MATCH_STATUS_BAR_HEIGHT collapses it into the status bar, DYNAMIC_ISLAND narrows it into
 * a floating pill. Neither owns a window, repaints ROM content or replaces ROM animators.
 */
object SystemUIStrongToastHooks {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val STRONG_TOAST_CLASS = "com.android.systemui.toast.MIUIStrongToast"
    private const val STRONG_TOAST_CONTROL_CLASS = "com.android.systemui.toast.MIUIStrongToastControl"
    private const val BATTERY_CALLBACK_CLASS = "com.android.systemui.toast.MIUIStrongToastControl\$6"
    private const val KEYGUARD_STATE_CLASS = "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl"
    private const val MESSAGE_CONTAINER_ID = "cl_strong_toast_msg"
    private const val FOREHEAD_BOTTOM_ID = "strong_toast_bottom_view"
    private const val RUNTIME_SNAPSHOT_FIELD = "customiuizer_strong_toast_runtime_snapshot"
    private const val MATCH_BASELINE_FIELD = "customiuizer_match_mode_baseline"
    private const val RECALL_HIT_FIELD = "customiuizer_island_recall_hit"

    /** The island's own size comes from the ROM, so it tracks each ROM's density and layout. */
    private const val ISLAND_WIDTH_DIMEN = "strong_toast_width"
    private const val ISLAND_HEIGHT_DIMEN = "strong_toast_height"

    @JvmField internal var snapshotRef: AtomicReference<StrongToastRuntimeSnapshot>? = null
    @JvmField internal var installed = false

    internal fun currentSnapshot(): StrongToastRuntimeSnapshot? = snapshotRef?.get()

    internal fun storeSnapshot(view: Any, snapshot: StrongToastRuntimeSnapshot) {
        XposedHelpers.setAdditionalInstanceField(view, RUNTIME_SNAPSHOT_FIELD, snapshot)
    }

    internal fun resolveSnapshot(view: Any?): StrongToastRuntimeSnapshot? {
        val stored = view?.let {
            XposedHelpers.getAdditionalInstanceField(it, RUNTIME_SNAPSHOT_FIELD)
                as? StrongToastRuntimeSnapshot
        }
        return stored ?: currentSnapshot()
    }

    @JvmStatic
    internal fun install(
        lpparam: PackageReadyParam,
        snapshotRef: AtomicReference<StrongToastRuntimeSnapshot>,
    ) {
        this.snapshotRef = snapshotRef
        if (installed) return
        installed = true
        installHeightMatch(lpparam)
        installLifecycleHooks(lpparam)
        installControlClassHooks(lpparam)
        installStatusBarContentsCapture(lpparam)
    }

    private fun installHeightMatch(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(
            STRONG_TOAST_CLASS, lpparam.classLoader, "getWindowParam", object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    try {
                        val layoutParams = callback.getResult() as? WindowManager.LayoutParams ?: return
                        val strongToast = callback.getThisObject() as? View ?: return
                        val snapshot = resolveSnapshot(strongToast) ?: return
                        storeSnapshot(strongToast, snapshot)
                        when (snapshot.mode) {
                            StrongToastPresentationMode.SYSTEM_DEFAULT,
                            StrongToastPresentationMode.HIDE -> Unit
                            StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT -> {
                                val statusBarHeightPx = currentStatusBarInsetPx(strongToast)
                                if (statusBarHeightPx <= 0) return
                                val chinHeightPx = foreheadChinHeightPx(strongToast)
                                if (applyMatchStatusBarHeight(
                                        strongToast,
                                        resolveMatchContentHeightPx(statusBarHeightPx, chinHeightPx),
                                        matchModeHidesChin(statusBarHeightPx, chinHeightPx),
                                    )
                                ) {
                                    layoutParams.height = resolveMatchWindowHeightPx(statusBarHeightPx)
                                    layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                }
                            }
                            StrongToastPresentationMode.DYNAMIC_ISLAND -> {
                                val capsuleHeightPx = strongToastDimensionPx(strongToast, ISLAND_HEIGHT_DIMEN)
                                val topMarginPx = resolveIslandTopMarginPx(
                                    displayCutoutTopPx(strongToast),
                                    layoutParams.height,
                                    capsuleHeightPx,
                                    dpToPx(strongToast, snapshot.islandOffsetDp.toFloat()),
                                )
                                if (applyDynamicIslandCapsule(strongToast, capsuleHeightPx, topMarginPx)) {
                                    layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                    layoutParams.height = resolveIslandWindowHeightPx(
                                        layoutParams.height, capsuleHeightPx, topMarginPx,
                                    )
                                    // The status bar height feature rewrites the statusBars
                                    // InsetsSource, and a window that fits that inset gets cropped
                                    // to the customised height. The island floats free of it.
                                    layoutParams.setFitInsetsTypes(0)
                                    layoutParams.flags = layoutParams.flags or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                    DynamicIslandStatusBarFade.acquire(strongToast)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        XposedHelpers.log("StrongToastPresentation", e)
                    }
                }
            }
        )
    }

    private fun installLifecycleHooks(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS, lpparam.classLoader, "onDetachedFromWindow", object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    val root = callback.getThisObject() as? View ?: return
                    try {
                        // Shared by both mutating modes; a no-op when neither stored a baseline.
                        resetMatchModeCapsule(root)
                    } finally {
                        DynamicIslandStatusBarFade.release(root)
                        XposedHelpers.removeAdditionalInstanceField(root, RUNTIME_SNAPSHOT_FIELD)
                    }
                }
            }
        )
    }

    /**
     * Reshapes the ROM forehead into a floating capsule in place.
     *
     * The ROM row is a full-width black bar - `strong_toast_bg` is a stretched 1x1 black square, so
     * the ROM contributes no rounding - sitting flush against the top edge above a concave shoulder
     * (`strong_toast_down`). That combination is the forehead. The island is the same row narrowed
     * to the ROM's own `strong_toast_width` x `strong_toast_height`, pushed off the edge by a
     * margin, pill-clipped, with the shoulder hidden.
     *
     * Every change stays inside the ROM window and view tree so `MIUIStrongToast` keeps measuring
     * and animating its own content. Re-parenting the subtree into a module-owned window made that
     * measurement read zero, which left the bar full-width and the entrance animation stepping.
     */
    private fun applyDynamicIslandCapsule(root: View, capsuleHeightPx: Int, topMarginPx: Int): Boolean {
        val capsule = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID) ?: return false
        val parent = capsule.parent as? ViewGroup
        val chin = findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)
        val widthPx = strongToastDimensionPx(root, ISLAND_WIDTH_DIMEN)
        if (widthPx <= 0 || capsuleHeightPx <= 0) return false
        if (XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD) == null) {
            val baseline = captureMatchModeBaseline(capsule, parent, chin) ?: return false
            XposedHelpers.setAdditionalInstanceField(root, MATCH_BASELINE_FIELD, baseline)
        }
        capsule.layoutParams?.let { lp ->
            lp.width = widthPx
            lp.height = capsuleHeightPx
            (lp as? ViewGroup.MarginLayoutParams)?.topMargin = topMarginPx
            (lp as? LinearLayout.LayoutParams)?.gravity = Gravity.CENTER_HORIZONTAL
            capsule.layoutParams = lp
        }
        capsule.outlineProvider = IslandPillOutline
        capsule.clipToOutline = true
        chin?.visibility = View.GONE
        // The ROM pads this container by the status bar height so the forehead lands under the bar.
        // The island is anchored to the cutout instead, and leaving the padding in place would let
        // the module's own status bar height feature push the pill down off the camera.
        parent?.setPadding(0, 0, 0, 0)
        (parent as? LinearLayout)?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        (root as? ViewGroup)?.let { applyIslandRecallHit(it, capsule) }
        return true
    }

    /**
     * Expands the touchable area around the already-laid-out capsule without changing
     * its visual size. The host is the StrongToast root so the extra margin is not
     * clipped to a wrap-content parent. The ROM StrongToast gesture stays in charge
     * of swipe distance.
     */
    private fun applyIslandRecallHit(host: ViewGroup, capsule: View) {
        if (XposedHelpers.getAdditionalInstanceField(host, RECALL_HIT_FIELD) != null) {
            refreshIslandRecallHit(host, capsule)
            return
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshIslandRecallHit(host, capsule)
        }
        host.addOnLayoutChangeListener(listener)
        capsule.addOnLayoutChangeListener(listener)
        XposedHelpers.setAdditionalInstanceField(host, RECALL_HIT_FIELD, listener)
        refreshIslandRecallHit(host, capsule)
    }

    private fun refreshIslandRecallHit(host: ViewGroup, capsule: View) {
        val childRect = Rect()
        capsule.getDrawingRect(childRect)
        host.offsetDescendantRectToMyCoords(capsule, childRect)
        val slop = ViewConfiguration.get(host.context).scaledTouchSlop
        val margin = IslandRecallHit.extraMarginPx(host.resources.displayMetrics.density, slop)
        val box = IslandRecallHit.delegateRect(
            host.width,
            host.height,
            childRect.left,
            childRect.top,
            childRect.width(),
            childRect.height(),
            margin,
        )
        host.touchDelegate = if (box.isEmpty) {
            null
        } else {
            TouchDelegate(Rect(box.left, box.top, box.right, box.bottom), capsule)
        }
    }

    private fun clearIslandRecallHit(host: ViewGroup?, capsule: View?) {
        if (host == null) return
        val listener = XposedHelpers.getAdditionalInstanceField(host, RECALL_HIT_FIELD)
            as? View.OnLayoutChangeListener
        if (listener != null) {
            host.removeOnLayoutChangeListener(listener)
            capsule?.removeOnLayoutChangeListener(listener)
            XposedHelpers.removeAdditionalInstanceField(host, RECALL_HIT_FIELD)
        }
        host.touchDelegate = null
    }

    /**
     * Where the island's top edge sits.
     *
     * The pill is anchored to the display cutout, top edge flush with the cutout's, so the camera
     * sits inside the pill instead of the pill reading as a bar pinned to the screen edge.
     *
     * Status bar height is deliberately not an input. The island is a strong prompt and is taller
     * than the status bar by design, so the bar is neither a meaningful anchor nor a container for
     * it - and keeping it out means the status bar height feature cannot move the island.
     *
     * Displays reporting no cutout have nothing to anchor to, so the pill is centred in the vertical
     * band the ROM itself reserved for the strong toast.
     *
     * [userOffsetPx] is the signed manual correction. Panels differ in how faithfully the declared
     * cutout matches the visible camera hole, so the derived anchor is a starting point rather than
     * the final word.
     */
    @JvmStatic
    internal fun resolveIslandTopMarginPx(
        cutoutTopPx: Int,
        romWindowHeightPx: Int,
        capsuleHeightPx: Int,
        userOffsetPx: Int,
    ): Int {
        val anchor = when {
            cutoutTopPx >= 0 -> cutoutTopPx
            romWindowHeightPx <= 0 -> 0
            else -> (romWindowHeightPx - capsuleHeightPx) / 2
        }
        return (anchor + userOffsetPx).coerceAtLeast(0)
    }

    /** Grows the ROM window only when the anchored island would not fit in what the ROM asked for. */
    @JvmStatic
    internal fun resolveIslandWindowHeightPx(
        romWindowHeightPx: Int,
        capsuleHeightPx: Int,
        topMarginPx: Int,
    ): Int {
        if (romWindowHeightPx <= 0) return romWindowHeightPx
        return maxOf(romWindowHeightPx, topMarginPx.coerceAtLeast(0) + capsuleHeightPx.coerceAtLeast(0))
    }

    /**
     * Top edge of the camera hole itself, or -1 when the display reports no top cutout.
     *
     * Read from `currentWindowMetrics` rather than the View: `getWindowParam` runs before the
     * StrongToast is attached, so its `rootWindowInsets` is still null. `boundingRectTop` is a single
     * Rect, so no bounding-rect list is allocated.
     *
     * The rect's own top cannot be used. HyperOS declares the cutout as a slot running from the
     * screen edge down past the camera - 68x104px around a 68px hole on this panel - so `top` is the
     * screen edge and anchoring to it puts the island flush against it. A punch hole is circular, so
     * its height equals its width and it starts that far above the rect's bottom. Displays that
     * report the hole itself, or a wide notch, are unaffected: for both, the shorter side is already
     * the height, which yields the rect's own top.
     */
    private fun displayCutoutTopPx(view: View): Int {
        val wm = view.context?.getSystemService(WindowManager::class.java) ?: return -1
        val cutout = wm.currentWindowMetrics.windowInsets.displayCutout ?: return -1
        val rect = cutout.boundingRectTop
        if (rect.isEmpty) return -1
        return rect.bottom - minOf(rect.width(), rect.height())
    }

    /**
     * Pill shape for the island. Outline clipping is used rather than a replacement rounded
     * background because HyperOS 1 enables `persist.sys.support_view_smoothcorner` and substitutes
     * its own smooth-corner implementation for platform rounded-rect drawables, which does not
     * reproduce the measured View bounds exactly and drops pixels at one edge.
     */
    private object IslandPillOutline : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (view.width <= 0 || view.height <= 0) return
            outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
        }
    }

    private fun installStatusBarContentsCapture(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            lpparam.classLoader,
            "onFinishInflate",
            statusBarCaptureHook(),
        )
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            lpparam.classLoader,
            "onAttachedToWindow",
            statusBarCaptureHook(),
        )
    }

    private fun statusBarCaptureHook() = object : MethodHook() {
        override fun after(callback: AfterHookCallback) {
            ModuleHelper.guarded {
                val statusBar = callback.getThisObject() as? View ?: return@guarded
                DynamicIslandStatusBarFade.bindFromStatusBar(statusBar)
            }
        }
    }

    private fun installControlClassHooks(lpparam: PackageReadyParam) {
        try {
            val controlClass = XposedHelpers.findClassIfExists(STRONG_TOAST_CONTROL_CLASS, lpparam.classLoader)
            val keyguardClass = XposedHelpers.findClassIfExists(KEYGUARD_STATE_CLASS, lpparam.classLoader)
            val controllerField = if (controlClass != null && keyguardClass != null) {
                XposedHelpers.findFieldIfExists(controlClass, "mKeyguardStateController")
            } else null
            val showingField = keyguardClass?.let { XposedHelpers.findFieldIfExists(it, "mShowing") }
            ModuleHelper.hookAllMethods(
                STRONG_TOAST_CONTROL_CLASS, lpparam.classLoader, "showCustomStrongToast",
                StrongToastControlHook(controllerField, showingField, null, true),
            )
            val batteryClass = XposedHelpers.findClassIfExists(BATTERY_CALLBACK_CLASS, lpparam.classLoader)
            val outerField = batteryClass?.let { XposedHelpers.findFieldIfExists(it, "this\$0") }
            if (batteryClass != null && outerField != null) {
                ModuleHelper.hookAllMethods(
                    batteryClass, "onRefreshBatteryInfo",
                    StrongToastControlHook(controllerField, showingField, outerField, false),
                )
            }
        } catch (e: Exception) {
            XposedHelpers.log("StrongToastControlClassHooks", e)
        }
    }

    private data class LockscreenGateToken(val keyguardState: Any, val wasShowing: Boolean)

    internal class StrongToastControlHook(
        private val controllerField: Field?,
        private val showingField: Field?,
        private val outerControlField: Field?,
        private val allowHide: Boolean,
    ) : MethodHook() {
        @Throws(Throwable::class)
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val snapshot = currentSnapshot() ?: return chain.proceed()
            if (snapshot.mode == StrongToastPresentationMode.HIDE && allowHide) return null
            if (!snapshot.isDynamicIsland) return chain.proceed()
            val receiver = BeforeHookCallback(chain).getThisObject() ?: return chain.proceed()
            val control = try {
                outerControlField?.get(receiver) ?: receiver
            } catch (e: Exception) {
                XposedHelpers.log("StrongToastControlResolve", e)
                return chain.proceed()
            }
            val token = openLockscreenGate(control, controllerField, showingField)
            return try {
                chain.proceed()
            } finally {
                if (token != null) closeLockscreenGate(token, showingField)
            }
        }
    }

    private fun openLockscreenGate(control: Any?, controllerField: Field?, showingField: Field?): LockscreenGateToken? {
        if (control == null || controllerField == null || showingField == null) return null
        val keyguard = try { controllerField.get(control) } catch (e: Exception) {
            XposedHelpers.log("StrongToastLockscreenRead", e); return null
        } ?: return null
        val showing = try { showingField.getBoolean(keyguard) } catch (e: Exception) {
            XposedHelpers.log("StrongToastLockscreenRead", e); return null
        }
        if (!showing) return null
        return try {
            showingField.setBoolean(keyguard, false)
            LockscreenGateToken(keyguard, true)
        } catch (e: Exception) {
            XposedHelpers.log("StrongToastLockscreenSet", e); null
        }
    }

    private fun closeLockscreenGate(token: LockscreenGateToken, showingField: Field?) {
        try {
            showingField?.setBoolean(token.keyguardState, token.wasShowing)
        } catch (e: Exception) {
            XposedHelpers.log("StrongToastLockscreenRestore", e)
        }
    }

    internal data class MatchModeBaseline(
        val width: Int, val height: Int, val topMargin: Int, val bottomMargin: Int,
        val layoutGravity: Int, val capsuleGravity: Int,
        val parentPaddingLeft: Int, val parentPaddingTop: Int,
        val parentPaddingRight: Int, val parentPaddingBottom: Int,
        val parentGravity: Int, val parentWidth: Int, val parentHeight: Int,
        val parentTopMargin: Int, val parentBottomMargin: Int, val parentLayoutGravity: Int,
        val bottomViewVisibility: Int,
    )

    internal fun captureMatchModeBaseline(capsule: View, parent: ViewGroup?, bottomView: View?): MatchModeBaseline? {
        val lp = capsule.layoutParams ?: return null
        val parentLp = parent?.layoutParams
        return MatchModeBaseline(
            lp.width, lp.height,
            (lp as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0,
            (lp as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0,
            (lp as? LinearLayout.LayoutParams)?.gravity ?: 0,
            (capsule as? LinearLayout)?.gravity ?: 0,
            parent?.paddingLeft ?: 0, parent?.paddingTop ?: 0, parent?.paddingRight ?: 0, parent?.paddingBottom ?: 0,
            (parent as? LinearLayout)?.gravity ?: 0, parentLp?.width ?: 0, parentLp?.height ?: 0,
            (parentLp as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0,
            (parentLp as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0,
            (parentLp as? LinearLayout.LayoutParams)?.gravity ?: 0, bottomView?.visibility ?: View.VISIBLE,
        )
    }

    internal fun applyMatchModeBaselineToViews(
        root: View, capsule: View, parent: ViewGroup?, bottomView: View?,
        targetContentHeightPx: Int, hideChin: Boolean,
    ): Boolean {
        if (XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD) == null) {
            val baseline = captureMatchModeBaseline(capsule, parent, bottomView) ?: return false
            XposedHelpers.setAdditionalInstanceField(root, MATCH_BASELINE_FIELD, baseline)
        }
        capsule.layoutParams?.let { it.height = targetContentHeightPx; capsule.layoutParams = it }
        bottomView?.visibility = if (hideChin) View.GONE else View.VISIBLE
        parent?.setPadding(0, 0, 0, 0)
        (parent as? LinearLayout)?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        return true
    }

    internal fun applyMatchStatusBarHeight(root: View, targetContentHeightPx: Int, hideChin: Boolean): Boolean {
        val capsule = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID) ?: return false
        return applyMatchModeBaselineToViews(
            root, capsule, capsule.parent as? ViewGroup,
            findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID), targetContentHeightPx, hideChin,
        )
    }

    internal fun restoreMatchModeBaseline(
        root: View, capsule: View, parent: ViewGroup?, bottomView: View?, baseline: MatchModeBaseline,
    ) {
        capsule.layoutParams?.let {
            it.width = baseline.width; it.height = baseline.height
            (it as? ViewGroup.MarginLayoutParams)?.apply {
                topMargin = baseline.topMargin; bottomMargin = baseline.bottomMargin
            }
            (it as? LinearLayout.LayoutParams)?.gravity = baseline.layoutGravity
            capsule.layoutParams = it
        }
        (capsule as? LinearLayout)?.gravity = baseline.capsuleGravity
        parent?.layoutParams?.let {
            it.width = baseline.parentWidth; it.height = baseline.parentHeight
            (it as? ViewGroup.MarginLayoutParams)?.apply {
                topMargin = baseline.parentTopMargin; bottomMargin = baseline.parentBottomMargin
            }
            (it as? LinearLayout.LayoutParams)?.gravity = baseline.parentLayoutGravity
            parent.layoutParams = it
        }
        parent?.setPadding(baseline.parentPaddingLeft, baseline.parentPaddingTop, baseline.parentPaddingRight, baseline.parentPaddingBottom)
        (parent as? LinearLayout)?.gravity = baseline.parentGravity
        bottomView?.visibility = baseline.bottomViewVisibility
    }

    internal fun resetMatchModeBaselineToViews(
        root: View, capsule: View?, parent: ViewGroup?, bottomView: View?, baseline: MatchModeBaseline,
    ) {
        try {
            if (capsule != null) restoreMatchModeBaseline(root, capsule, parent, bottomView, baseline)
        } finally {
            XposedHelpers.removeAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        }
    }

    internal fun resetMatchModeCapsule(root: View) {
        val capsule = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID)
        clearIslandRecallHit(root as? ViewGroup, capsule)
        val baseline = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD) as? MatchModeBaseline ?: return
        if (capsule?.outlineProvider === IslandPillOutline) {
            // Back to what the ROM layout declares: no clip, outline follows the background.
            capsule.clipToOutline = false
            capsule.outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        resetMatchModeBaselineToViews(root, capsule, capsule?.parent as? ViewGroup, findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID), baseline)
    }

    private fun currentStatusBarInsetPx(view: View?): Int {
        val wm = view?.context?.getSystemService(WindowManager::class.java) ?: return 0
        return wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
    }

    private fun foreheadChinHeightPx(root: View): Int {
        val view = findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID) ?: return 0
        return if (view.visibility == View.GONE) 0 else maxOf(view.measuredHeight, view.background?.intrinsicHeight ?: 0)
    }

    private fun findViewBySystemUiId(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", SYSTEM_UI_PACKAGE)
        return if (id == 0) null else root.findViewById(id)
    }

    private fun strongToastDimensionPx(root: View, name: String): Int {
        val id = root.resources.getIdentifier(name, "dimen", SYSTEM_UI_PACKAGE)
        return if (id == 0) 0 else root.resources.getDimensionPixelSize(id)
    }

    private fun dpToPx(view: View, dp: Float): Int = (dp * view.resources.displayMetrics.density + 0.5f).toInt()

    @JvmStatic internal fun resolveMatchContentHeightPx(statusBarHeightPx: Int, chinHeightPx: Int): Int {
        val status = statusBarHeightPx.coerceAtLeast(0)
        return if (chinHeightPx <= 0 || matchModeHidesChin(status, chinHeightPx)) status else status - chinHeightPx
    }

    @JvmStatic internal fun matchModeHidesChin(statusBarHeightPx: Int, chinHeightPx: Int): Boolean =
        statusBarHeightPx > 0 && chinHeightPx > statusBarHeightPx / 2

    @JvmStatic internal fun resolveMatchWindowHeightPx(statusBarHeightPx: Int): Int = statusBarHeightPx.coerceAtLeast(0)
}
