package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** Separates inherited HotSeats transforms from the decoration's native unlock properties. */
internal class DockFullWidthContainer(context: Context) : FrameLayout(context) {
    private val hostToRoot = Matrix()
    private val segment = Matrix()
    private val appliedMatrix = Matrix()
    private var hasAppliedMatrix = false

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun transformMatrixToGlobal(matrix: Matrix) {
        super.transformMatrixToGlobal(matrix)
        // View's coordinate helper includes scalar properties but omits the RenderNode's
        // separate animation matrix. Wallpaper sampling must use the actual rendered space.
        if (hasAppliedMatrix) matrix.preConcat(appliedMatrix)
    }

    fun sync(host: View, legacy: Matrix, legacyAlpha: Float, drawingAnimation: Boolean = false) {
        val root = parent as? ViewGroup ?: return
        hostToRoot.reset()
        var layoutX = 0f
        var layoutY = 0f
        var opacity = legacyAlpha
        var owner: View? = host
        while (owner != null && owner !== root) {
            val ancestor = owner.parent as? View ?: break
            val x = (owner.left - ancestor.scrollX).toFloat()
            val y = (owner.top - ancestor.scrollY).toFloat()
            layoutX += x; layoutY += y
            segment.setTranslate(x, y)
            // The native Canvas animation precedes this View's scalar/RenderNode matrix.
            if (owner === host) segment.preConcat(legacy)
            segment.preConcat(owner.matrix)
            hostToRoot.postConcat(segment)
            if (owner.visibility != VISIBLE && !(owner === host && drawingAnimation)) opacity = 0f
            opacity *= owner.alpha
            owner = ancestor
        }
        // Root-space decoration -> untransformed HotSeats space -> rendered root space.
        // C = hostToRoot * inverse(hostLayoutToRoot). The shared root is never applied twice.
        hostToRoot.preTranslate(-layoutX, -layoutY)
        if (hostToRoot.isIdentity) {
            if (hasAppliedMatrix) { animationMatrix = null; hasAppliedMatrix = false }
        } else if (!hasAppliedMatrix || hostToRoot != appliedMatrix) {
            animationMatrix = hostToRoot
            appliedMatrix.set(hostToRoot)
            hasAppliedMatrix = true
        }
        alpha = if (owner === root) opacity.coerceIn(0f, 1f) else 0f
    }
}
