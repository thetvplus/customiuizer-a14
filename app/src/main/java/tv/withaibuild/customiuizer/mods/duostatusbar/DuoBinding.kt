package tv.withaibuild.customiuizer.mods.duostatusbar

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.lang.ref.WeakReference

/** Keep source identity across native handoff; read its current row after configuration changes. */
internal fun duoGeometrySource(bindings: Array<WeakReference<DuoBinding>?>, displayId: Int,
    remembered: WeakReference<DuoBinding>?,
): WeakReference<DuoBinding>? {
    var visible: WeakReference<DuoBinding>? = null
    var fallback: WeakReference<DuoBinding>? = null
    var retained: WeakReference<DuoBinding>? = null
    var visibleHeight = Int.MAX_VALUE
    var fallbackHeight = Int.MAX_VALUE
    for (reference in bindings) {
        val source = reference?.get() ?: continue
        if (source.displayId != displayId) continue
        if (!source.primaryHost) {
            if (retained == null) retained = source.geometrySource
            continue
        }
        val row = source.view.rowHeightPx
        if (row <= 0) continue
        if (row < fallbackHeight) { fallback = reference; fallbackHeight = row }
        if (source.root.isShown && row < visibleHeight) { visible = reference; visibleHeight = row }
    }
    if (visible != null) return visible
    // Reuse an existing weak reference only while its binding remains registered on this display.
    var previous: WeakReference<DuoBinding>? = null
    for (reference in bindings) {
        if (reference !== remembered && reference !== retained) continue
        val source = reference?.get() ?: continue
        if (!source.primaryHost || source.displayId != displayId || source.view.rowHeightPx <= 0) continue
        if (reference === remembered) return reference
        previous = reference
    }
    return previous ?: fallback
}

/** Owned by the inserted child. The runtime holds only a weak reference to this binding. */
internal class DuoBinding(
    val owner: ViewGroup,
    val root: ViewGroup,
    val view: DuoStatusBarView,
    private val abi: DuoAbi,
) {
    val displayId = root.display?.displayId ?: -1
    val primaryHost = abi.statusBar.isInstance(root) || abi.keyguard.isInstance(root)
    var geometrySource: WeakReference<DuoBinding>? = null
    // The supported header roots have a fixed-height direct child containing the battery.
    private val headerRow = if (primaryHost) null else run {
        var row: View = owner
        while (row.parent !== root) row = row.parent as? View ?: return@run null
        row
    }
    private var originalHeaderHeight = 0
    private var appliedHeaderHeight = 0
    private var appliedHeaderParams: ViewGroup.LayoutParams? = null
    private class Original(val view: View) {
        val visibility = DuoVisibility(view.visibility)
        fun hide() {
            visibility.observe(view.visibility)
            visibility.hide()
            if (view.visibility != View.GONE) view.visibility = View.GONE
        }
        fun restore() {
            val original = visibility.release(view.visibility)
            if (view.visibility != original) view.visibility = original
        }
    }
    private val batteryChildren = ArrayList<Original>(owner.childCount)
    private val signals = ArrayList<Original>(4)
    private var active = false
    private var supported = true

    init {
        for (i in 0 until owner.childCount) {
            val child = owner.getChildAt(i)
            if (child !== view) batteryChildren.add(Original(child))
        }
        collectSignals(root)
        syncTypeface()
    }

    fun syncTypeface() {
        if (view.usesCellularText) view.syncTypeface((abi.percentView.get(owner) as? TextView)?.typeface)
    }

    /** Layout/visibility events only. Grow the header locally instead of shrinking the status icon. */
    fun shareHeight(height: Int) {
        view.shareHeight(if (primaryHost) 0 else height)
        val row = headerRow ?: return
        val params = row.layoutParams ?: return
        if (appliedHeaderHeight != 0 && (params !== appliedHeaderParams || params.height != appliedHeaderHeight)) {
            originalHeaderHeight = 0
            appliedHeaderHeight = 0 // A native configuration change owns the new height.
            appliedHeaderParams = null
        }
        if (!active || height <= 0) {
            restoreHeaderHeight()
            return
        }
        if (params.height <= 0) return // Preserve custom wrap-content/match-parent layouts.
        val original = if (appliedHeaderHeight != 0) originalHeaderHeight else params.height
        val desired = maxOf(original, view.requiredHeight(height))
        if (desired == params.height) return
        originalHeaderHeight = if (desired == original) 0 else original
        appliedHeaderHeight = if (desired == original) 0 else desired
        appliedHeaderParams = if (desired == original) null else params
        params.height = desired
        row.layoutParams = params
    }

    private fun restoreHeaderHeight() {
        if (appliedHeaderHeight == 0) return
        val row = headerRow
        val params = row?.layoutParams
        if (params != null && params === appliedHeaderParams && params.height == appliedHeaderHeight) {
            params.height = originalHeaderHeight
            row.layoutParams = params
        }
        originalHeaderHeight = 0
        appliedHeaderHeight = 0
        appliedHeaderParams = null
    }

    private fun collectSignals(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (abi.wifiView.isInstance(child) || abi.mobileView.isInstance(child)) {
                if (signals.size < 8) signals.add(Original(child)) else supported = false
            } else if (child is ViewGroup && child !== owner) collectSignals(child)
        }
    }

    fun restoreBattery() {
        for (i in batteryChildren.indices) batteryChildren[i].restore()
    }

    fun reconcile(level: Int, charging: Boolean, saver: Boolean, bits: Int, audio: Int) {
        val ready = supported && level in 0..100 &&
            bits and (DuoNetworkState.WIFI_READY or DuoNetworkState.MOBILE_READY) == 3
        if (!ready) {
            restore()
            return
        }
        val text = abi.percentView.get(owner) as TextView
        view.render(level, charging, saver, text.currentTextColor, bits, audio)
        view.visibility = View.VISIBLE
        val becameActive = !active
        active = true
        for (i in batteryChildren.indices) batteryChildren[i].hide()
        for (i in signals.indices) signals[i].hide()
        if (becameActive) view.refreshGeometry()
    }

    fun stockChanged(stock: View, restoreBeforeNative: Boolean) {
        var index = signals.size - 1
        while (index >= 0) {
            val previous = signals[index]
            if (previous.view !== stock && !previous.view.isAttachedToWindow) {
                previous.restore()
                signals.removeAt(index)
            }
            index--
        }
        for (i in signals.indices) {
            val original = signals[i]
            if (original.view !== stock) continue
            if (restoreBeforeNative) {
                original.restore()
                return
            }
            val wifi = abi.wifiView.isInstance(stock)
            val state = (if (wifi) abi.wifiViewState else abi.mobileViewState).get(stock)
            if (state == null || !(if (wifi) abi.wifiVisible else abi.mobileVisible).getBoolean(state)) {
                original.visibility.observeNativeGone()
            }
            if (active) original.hide() else original.visibility.observe(stock.visibility)
            return
        }
        // Native icon groups may be replaced while the root stays attached.
        if (signals.size < 8 && stock.isAttachedToWindow) {
            val original = Original(stock)
            signals.add(original)
            if (active) original.hide()
        } else if (signals.size >= 8) {
            supported = false
            restore()
        }
    }

    fun restore() {
        active = false
        restoreHeaderHeight()
        view.stopTransition()
        restoreBattery()
        for (i in signals.indices) signals[i].restore()
        view.visibility = View.GONE
    }

    fun release() {
        restore()
        geometrySource = null
        signals.clear()
        batteryChildren.clear()
        view.binding = null
        owner.removeView(view)
    }
}
