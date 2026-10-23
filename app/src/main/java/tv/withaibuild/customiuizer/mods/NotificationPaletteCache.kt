package tv.withaibuild.customiuizer.mods

import android.graphics.Bitmap
import java.lang.ref.WeakReference

internal data class NotificationPalette(val protection: Int, val primary: Int, val secondary: Int, val background: Int) {
    val tint: Int get() = (background and 0x00ffffff) or (158 shl 24)
}

/** One entry per live Notification; no global bitmap/app cache and no retained producer. */
internal class NotificationPaletteCache {
    private var icon = WeakReference<Bitmap>(null)
    private var generation = 0
    private var uid = -1
    private var pkg = ""
    private var dark = false
    private var colors: NotificationPalette? = null

    @Synchronized
    fun current(uid: Int, pkg: String, dark: Boolean): NotificationPalette? =
        colors?.takeIf { this.uid == uid && this.pkg == pkg && this.dark == dark }

    @Synchronized
    fun resolve(bitmap: Bitmap, uid: Int, pkg: String, dark: Boolean, produce: () -> NotificationPalette): NotificationPalette {
        val previous = current(uid, pkg, dark)
        if (previous != null && icon.get() === bitmap && generation == bitmap.generationId) return previous
        val next = produce() // Failure leaves the last complete state; never publish partial colors.
        icon = WeakReference(bitmap)
        generation = bitmap.generationId
        this.uid = uid
        this.pkg = pkg
        this.dark = dark
        colors = next
        return next
    }
}
