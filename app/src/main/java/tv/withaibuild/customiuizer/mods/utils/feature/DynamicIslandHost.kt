package tv.withaibuild.customiuizer.mods.utils.feature

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/**
 * Module-owned Dynamic Island window host.
 *
 * Architecture reference (ideas only, no code copied):
 * - HyperIsland (MIT): LSPosed-side island presentation separate from stock notification chrome.
 * - Common SystemUI overlay pattern: privileged WindowManager host inside SystemUI, no
 *   AccessibilityService / NotificationListenerService / polling / app-overlay permission.
 *
 * The host owns the Surface. ROM MIUIStrongToast is only an event / content source; its window
 * is suppressed while the island is shown so status-bar inset crop cannot clip the capsule.
 */
internal class DynamicIslandHost {

    private var windowManagerRef: WeakReference<WindowManager>? = null
    private var rootRef: WeakReference<FrameLayout>? = null
    private var capsuleRef: WeakReference<DynamicIslandCapsuleView>? = null
    private var attachedEvent: DynamicIslandEvent? = null
    private var attached = false

    val isAttached: Boolean
        get() = attached && rootRef?.get()?.isAttachedToWindow == true

    val activeEvent: DynamicIslandEvent?
        get() = attachedEvent

    val capsule: DynamicIslandCapsuleView?
        get() = capsuleRef?.get()

    val root: FrameLayout?
        get() = rootRef?.get()

    /**
     * Creates (or reuses) the module window and returns the capsule that should host content.
     * Geometry uses [DynamicIslandWindowEnvelope] only — never statusBars inset as a hard crop.
     */
    fun attach(
        context: Context,
        event: DynamicIslandEvent,
        visualWidthPx: Int,
        visualHeightPx: Int,
        topMarginPx: Int,
        bottomClearancePx: Int,
        maxEdgeTravelPx: Int,
    ): DynamicIslandCapsuleView? {
        if (visualWidthPx <= 0 || visualHeightPx <= 0) return null
        val envelope = DynamicIslandWindowEnvelope.forTop(
            visualHeightPx,
            topMarginPx,
            bottomClearancePx,
            maxEdgeTravelPx,
        )
        val wm = context.getSystemService(WindowManager::class.java) ?: return null
        windowManagerRef = WeakReference(wm)

        val existingRoot = rootRef?.get()
        val existingCapsule = capsuleRef?.get()
        if (attached && existingRoot != null && existingCapsule != null && existingRoot.isAttachedToWindow) {
            attachedEvent = event
            applyCapsuleSize(existingCapsule, visualWidthPx, visualHeightPx, envelope)
            return existingCapsule
        }

        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            clipChildren = false
            clipToPadding = false
        }
        val capsule = DynamicIslandCapsuleView(context)
        applyCapsuleSize(capsule, visualWidthPx, visualHeightPx, envelope)
        root.addView(capsule)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            envelope.requiredHostHeightPx.coerceAtLeast(visualHeightPx + topMarginPx + bottomClearancePx),
            resolveWindowType(),
            WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            title = WINDOW_TITLE
            // Never let statusBars / navigationBars insets shrink this Surface.
            try {
                setFitInsetsTypes(0)
            } catch (_: Exception) {
            }
        }

        return try {
            wm.addView(root, lp)
            rootRef = WeakReference(root)
            capsuleRef = WeakReference(capsule)
            attachedEvent = event
            attached = true
            capsule
        } catch (t: Exception) {
            XposedHelpers.log("DynamicIslandHostAttach", t)
            null
        }
    }

    fun updateLayout(
        visualWidthPx: Int,
        visualHeightPx: Int,
        topMarginPx: Int,
        bottomClearancePx: Int,
        maxEdgeTravelPx: Int,
    ) {
        val root = rootRef?.get() ?: return
        val capsule = capsuleRef?.get() ?: return
        val wm = windowManagerRef?.get() ?: return
        val envelope = DynamicIslandWindowEnvelope.forTop(
            visualHeightPx,
            topMarginPx,
            bottomClearancePx,
            maxEdgeTravelPx,
        )
        applyCapsuleSize(capsule, visualWidthPx, visualHeightPx, envelope)
        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
        lp.height = envelope.requiredHostHeightPx
        try {
            wm.updateViewLayout(root, lp)
        } catch (t: Exception) {
            XposedHelpers.log("DynamicIslandHostUpdate", t)
        }
    }

    fun detachImmediate() {
        val root = rootRef?.get()
        val wm = windowManagerRef?.get()
        attached = false
        attachedEvent = null
        if (root != null && wm != null) {
            try {
                wm.removeViewImmediate(root)
            } catch (t: Exception) {
                try {
                    wm.removeView(root)
                } catch (inner: Exception) {
                    XposedHelpers.log("DynamicIslandHostDetach", inner)
                }
            }
        }
        rootRef?.clear()
        capsuleRef?.clear()
        windowManagerRef?.clear()
        rootRef = null
        capsuleRef = null
        windowManagerRef = null
    }

    private fun applyCapsuleSize(
        capsule: DynamicIslandCapsuleView,
        visualWidthPx: Int,
        visualHeightPx: Int,
        envelope: DynamicIslandWindowEnvelope,
    ) {
        val lp = (capsule.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(visualWidthPx, visualHeightPx)
        lp.width = visualWidthPx
        lp.height = visualHeightPx
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.topMargin = envelope.shellTopMarginPx
        lp.bottomMargin = envelope.shellBottomMarginPx
        capsule.layoutParams = lp
    }

    companion object {
        private const val WINDOW_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        private const val WINDOW_TITLE = "CustoMIUIzerDynamicIsland"

        /**
         * HyperOS exposes STATUS_BAR_SUB_PANEL as a hidden framework constant in its runtime
         * jar but it is absent from the SDK 34 compile stubs. Resolve it once on the cold path.
         * The SystemUI-owned overlay fallback does not require application overlay permission.
         */
        private fun resolveWindowType(): Int = try {
            WindowManager.LayoutParams::class.java
                .getField("TYPE_STATUS_BAR_SUB_PANEL")
                .getInt(null)
        } catch (_: Exception) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        /** Shared process-scoped host. One renderer for MUTE / DND / CHARGING / OTHER. */
        @JvmField
        val shared: DynamicIslandHost = DynamicIslandHost()

        @JvmStatic
        fun rendererToken(): String = DynamicIslandEvent.SHARED_RENDERER_TOKEN
    }
}
