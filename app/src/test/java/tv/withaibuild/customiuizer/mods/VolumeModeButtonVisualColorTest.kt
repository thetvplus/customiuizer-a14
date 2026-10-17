package tv.withaibuild.customiuizer.mods

import android.view.View
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
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
    fun selectedBlendUsesConfiguredAlphaAndNativeMode() {
        val snapshot = SystemUIControlCenterHooks.VolumeModeButtonColorSnapshot(
            true, 0x80b9f5dc.toInt(), 0xff123456.toInt()
        )
        assertArrayEquals(intArrayOf(0x80b9f5dc.toInt(), 3),
            SystemUIControlCenterHooks.volumeModeBlendColors(snapshot, true, true))
        assertArrayEquals(intArrayOf(0xff123456.toInt(), 3),
            SystemUIControlCenterHooks.volumeModeBlendColors(snapshot, true, false))
    }

    @Test
    fun inactiveAndDisabledBlendLeaveTheNativeColorsUntouched() {
        val enabled = SystemUIControlCenterHooks.VolumeModeButtonColorSnapshot(true, 1, 2)
        val disabled = enabled.copy(enabled = false)
        assertNull(SystemUIControlCenterHooks.volumeModeBlendColors(enabled, false, true))
        assertNull(SystemUIControlCenterHooks.volumeModeBlendColors(enabled, false, false))
        assertNull(SystemUIControlCenterHooks.volumeModeBlendColors(disabled, true, true))
        assertNull(SystemUIControlCenterHooks.volumeModeBlendColors(disabled, true, false))
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
    fun repeatedBlendReadsReuseThePreparedArraysAndRolesStaySeparate() {
        val snapshot = SystemUIControlCenterHooks.VolumeModeButtonColorSnapshot(true, 1, 2)
        assertSame(snapshot.backgroundBlendColors,
            SystemUIControlCenterHooks.volumeModeBlendColors(snapshot, true, true))
        assertSame(snapshot.iconBlendColors,
            SystemUIControlCenterHooks.volumeModeBlendColors(snapshot, true, false))
        assertNotSame(snapshot.backgroundBlendColors, snapshot.iconBlendColors)
    }

    @Test
    fun restoringWithReleasedViewsClearsOnlyPerHelperOwnership() {
        val state = SystemUIControlCenterHooks.VolumeModeButtonColorState(FakeHelper())
        state.backgroundBlended = true
        state.iconBlended = true
        state.iconFilterApplied = true
        state.restoreOwnedColors()
        assertEquals(false, state.backgroundBlended)
        assertEquals(false, state.iconBlended)
        assertEquals(false, state.iconFilterApplied)
        assertNull(state.coloredDrawableRef)
        assertNull(state.originalBackgroundColors)
        assertNull(state.appliedBackgroundColors)
        assertNull(state.originalIconFilter)
    }

}
