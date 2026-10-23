package tv.withaibuild.customiuizer.mods.island

import android.graphics.Rect
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/** One visible island lease; only individual status icons intersecting its screen bounds are affected. */
internal object DynamicIslandStatusBarFade {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val CONTENTS_ID = "status_bar_contents"
    private const val LEFT_CONTAINER_FIELD = "mStatusBarLeftContainer"
    internal var contentsRef: WeakReference<View>? = null
    internal var ownerRef: WeakReference<Any>? = null
    private val islandBounds = Rect()
    private var compact: IslandStatusBarCompact? = null

    fun compactClearance(screenWidth: Int, fallback: Int): Int {
        if (compact == null) contentsRef?.get()?.let { compact = IslandStatusBarCompact.resolve(it) }
        return compact?.maximumWidth(screenWidth) ?: fallback
    }

    fun bindFromStatusBar(statusBar: View) {
        val contents = resolveContents(statusBar) ?: return
        bind(contents)
    }

    internal fun bind(contents: View) {
        if (contentsRef?.get() !== contents) {
            compact?.restore()
            compact = null
        }
        contentsRef = WeakReference(contents)
        if (ownerRef?.get() != null) applyBounds()
    }

    fun acquire(owner: Any, bounds: Rect) {
        if (ownerRef?.get() === owner && islandBounds == bounds) return
        ownerRef = WeakReference(owner)
        islandBounds.set(bounds)
        applyBounds()
    }

    fun release(owner: Any) {
        if (ownerRef?.get() !== owner) return
        ownerRef = null
        islandBounds.setEmpty()
        // Keep the weak helper until its reveal ends, so a successor can cancel it.
        compact?.restore(animate = true)
    }

    private fun applyBounds() {
        val contents = contentsRef?.get() ?: return
        if (compact == null) compact = IslandStatusBarCompact.resolve(contents)
        // An unknown ROM hierarchy must never fall back to fading the whole status bar.
        compact?.show(islandBounds)
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

}
