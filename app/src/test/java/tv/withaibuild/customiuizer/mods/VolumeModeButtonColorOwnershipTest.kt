package tv.withaibuild.customiuizer.mods

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.ref.WeakReference

/** Behavioral drawables avoid a new graphics runtime dependency in the host test suite. */
class VolumeModeButtonColorOwnershipTest {
    class Gradient(initial: ColorStateList) : GradientDrawable() {
        var fill = initial
        var mutations = 0
        override fun getColor(): ColorStateList = fill
        override fun setColor(colorStateList: ColorStateList?) { fill = colorStateList!! }
        override fun mutate(): Drawable { mutations++; return this }
    }

    class Smooth(val child: Drawable) : Drawable() {
        fun getChildDrawable(): Drawable = child
        override fun draw(canvas: Canvas) {}
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    class Icon : ImageView(null) {
        var filter: ColorFilter? = null
        var writes = 0
        override fun getColorFilter(): ColorFilter? = filter
        override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; writes++ }
    }

    class Helper(icon: ImageView) {
        @JvmField val mStandardView: View? = null
        @JvmField val mIcon: ImageView = icon
    }

    private fun colors(argb: Int) = ColorStateList(arrayOf(intArrayOf()), intArrayOf(argb))
    private fun filter(argb: Int) = PorterDuffColorFilter(argb, PorterDuff.Mode.SRC_IN)

    @Test
    fun directAndSmoothChildAreMutatedBeforeAnyFillWrite() {
        val original = colors(0xff123456.toInt())
        val direct = Gradient(original)
        assertSame(direct, SystemUIControlCenterHooks.volumeModeGradientDrawable(direct, null))
        assertEquals(1, direct.mutations)
        assertSame(original, direct.fill)
        val child = Gradient(original)
        val smooth = Smooth(child)
        assertSame(child, SystemUIControlCenterHooks.volumeModeGradientDrawable(
            smooth, Smooth::class.java.getDeclaredMethod("getChildDrawable")))
        assertEquals(1, child.mutations)
        assertSame(original, child.fill)
    }

    @Test
    fun restoringOwnedPaintRestoresTheOriginalFillAndIconFilter() {
        val original = colors(0xff123456.toInt())
        val custom = colors(0x80b9f5dc.toInt())
        val gradient = Gradient(custom)
        val icon = Icon()
        val romFilter = filter(0xff555555.toInt())
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(Helper(icon))
        state.coloredDrawableRef = WeakReference(gradient)
        state.originalBackgroundColors = original
        state.appliedBackgroundColors = custom
        state.originalIconFilter = romFilter
        state.iconFilterApplied = true
        icon.filter = state.getOrCreateIconFilter(0xff277af7.toInt())
        state.restoreOwnedColors()
        assertSame(original, gradient.fill)
        assertSame(romFilter, icon.filter)
        assertEquals(1, icon.writes)
        assertNull(state.appliedBackgroundColors)
        assertEquals(false, state.iconFilterApplied)
        // A repeat restore does not repaint either native surface.
        state.restoreOwnedColors()
        assertSame(original, gradient.fill)
        assertEquals(1, icon.writes)
    }

    @Test
    fun newRomOrThirdPartyPaintIsNeverOverwrittenByCleanup() {
        val original = colors(1)
        val custom = colors(2)
        val newRom = colors(3)
        val gradient = Gradient(newRom)
        val icon = Icon()
        val newRomFilter = filter(4)
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(Helper(icon))
        state.coloredDrawableRef = WeakReference(gradient)
        state.originalBackgroundColors = original
        state.appliedBackgroundColors = custom
        state.originalIconFilter = filter(5)
        state.iconFilterApplied = true
        state.getOrCreateIconFilter(6)
        icon.filter = newRomFilter
        state.restoreOwnedColors()
        assertSame(newRom, gradient.fill)
        assertSame(newRomFilter, icon.filter)
        assertEquals(0, icon.writes)
    }

    @Test
    fun unsupportedShapeFailsOpen() {
        assertNull(SystemUIControlCenterHooks.volumeModeGradientDrawable(null, null))
        assertNull(SystemUIControlCenterHooks.volumeModeGradientDrawable(Smooth(Smooth(Gradient(colors(1)))), null))
    }

    @Test
    fun wrappedFatalFromThePreparedChildGetterEscapes() {
        val smooth = object : Drawable() {
            @Suppress("unused") fun getChildDrawable(): Drawable = throw ThreadDeath()
            override fun draw(canvas: Canvas) {}
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
        val method = smooth.javaClass.getDeclaredMethod("getChildDrawable").apply { isAccessible = true }
        try {
            SystemUIControlCenterHooks.volumeModeGradientDrawable(smooth, method)
            throw AssertionError("fatal child getter was swallowed")
        } catch (_: ThreadDeath) {}
    }
}
