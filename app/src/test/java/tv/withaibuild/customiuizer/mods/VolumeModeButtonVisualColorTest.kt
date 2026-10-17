package tv.withaibuild.customiuizer.mods

import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class VolumeModeButtonVisualColorTest {

    @Suppress("unused")
    class FakeHelper {
        @JvmField
        val mStandardView: View? = null
        @JvmField
        val mIcon: ImageView? = null
    }

    @Test
    fun colorStateBindsViewsWeakly() {
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(FakeHelper())
        assertNotNull(state.standardViewRef)
        assertNotNull(state.iconRef)
        assertEquals(null, state.standardViewRef.get())
        assertEquals(null, state.iconRef.get())
    }

    @Test
    fun backgroundFilterReusedForSameColor() {
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(FakeHelper())
        val first = state.getOrCreateBackgroundFilter(0xffff0000.toInt())
        val second = state.getOrCreateBackgroundFilter(0xffff0000.toInt())
        assertSame(first, second)
        assertNotNull(first)
    }

    @Test
    fun backgroundFilterRecreatedForDifferentColor() {
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(FakeHelper())
        val first = state.getOrCreateBackgroundFilter(0xffff0000.toInt())
        val second = state.getOrCreateBackgroundFilter(0xff00ff00.toInt())
        assertNotSame(first, second)
    }

    @Test
    fun iconFilterReusedForSameColor() {
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(FakeHelper())
        val first = state.getOrCreateIconFilter(0xff00aaff.toInt())
        val second = state.getOrCreateIconFilter(0xff00aaff.toInt())
        assertSame(first, second)
        assertNotNull(first)
    }

    @Test
    fun iconFilterRecreatedForDifferentColor() {
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(FakeHelper())
        val first = state.getOrCreateIconFilter(0xff0000ff.toInt())
        val second = state.getOrCreateIconFilter(0xffff00ff.toInt())
        assertNotSame(first, second)
    }

    @Test
    fun backgroundAndIconFiltersAreIndependent() {
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(FakeHelper())
        val bg = state.getOrCreateBackgroundFilter(0xffffffff.toInt())
        val icon = state.getOrCreateIconFilter(0xffffffff.toInt())
        assertNotSame(bg, icon)
    }

}
