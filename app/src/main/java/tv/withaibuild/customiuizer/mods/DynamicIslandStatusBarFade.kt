package tv.withaibuild.customiuizer.mods

import android.view.View
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/**
 * Fades HyperOS status-bar contents while a Dynamic Island event owns the top of the screen.
 *
 * The island keeps the ROM StrongToast window, geometry and animator. This only animates alpha on
 * the already-resolved `status_bar_contents` view. Ownership is the current island root: a later
 * event replaces the owner, and a stale detach cannot fade the icons back in.
 */
internal object DynamicIslandStatusBarFade {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val CONTENTS_ID = "status_bar_contents"
    private const val LEFT_CONTAINER_FIELD = "mStatusBarLeftContainer"

    internal fun interface AlphaRunner {
        fun run(view: View, target: Float, onEnd: (() -> Unit)?)
    }

    internal var alphaRunner: AlphaRunner = AlphaRunner { view, target, onEnd ->
        view.animate().cancel()
        view.animate().alpha(target).withEndAction(onEnd).start()
    }

    internal var contentsRef: WeakReference<View>? = null
    internal var ownerRef: WeakReference<Any>? = null
    internal var originalAlpha: Float? = null

    fun bindFromStatusBar(statusBar: View) {
        val contents = resolveContents(statusBar) ?: return
        bind(contents)
    }

    internal fun bind(contents: View) {
        val previous = contentsRef?.get()
        contentsRef = WeakReference(contents)
        if (ownerRef?.get() == null) return
        if (previous !== contents) originalAlpha = null
        fadeTo(0f, capturingOriginal = true)
    }

    fun acquire(owner: Any) {
        ownerRef = WeakReference(owner)
        fadeTo(0f, capturingOriginal = true)
    }

    fun release(owner: Any) {
        if (ownerRef?.get() !== owner) return
        ownerRef = null
        val target = originalAlpha ?: return
        fadeTo(target, capturingOriginal = false)
    }

    internal fun resolveContents(statusBar: View): View? {
        val id = statusBar.resources.getIdentifier(CONTENTS_ID, "id", SYSTEM_UI_PACKAGE)
        if (id != 0) {
            statusBar.findViewById<View>(id)?.let { return it }
        }
        return leftContainerParent(statusBar)
    }

    private fun leftContainerParent(statusBar: View): View? {
        val field = XposedHelpers.findFieldIfExists(statusBar.javaClass, LEFT_CONTAINER_FIELD) ?: return null
        return try {
            (field.get(statusBar) as? View)?.parent as? View
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log("DynamicIslandStatusBarFade", t)
            null
        }
    }

    private fun fadeTo(target: Float, capturingOriginal: Boolean) {
        val contents = contentsRef?.get() ?: return
        if (!contents.isAttachedToWindow) {
            if (!capturingOriginal) originalAlpha = null
            return
        }
        if (capturingOriginal && originalAlpha == null) originalAlpha = contents.alpha
        try {
            val onEnd = if (capturingOriginal) {
                null
            } else {
                {
                    if (ownerRef?.get() == null) originalAlpha = null
                }
            }
            alphaRunner.run(contents, target, onEnd)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log("DynamicIslandStatusBarFade", t)
        }
    }
}
