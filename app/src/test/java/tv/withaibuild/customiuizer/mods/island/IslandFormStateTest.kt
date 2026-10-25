package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandFormStateTest {
    @Test fun onlyCurrentIdentityMayStayExpandedAndThereIsNoMessageHistory() {
        val forms = IslandFormState()
        val media = Any(); val notice = Any()
        forms.select(media)
        assertTrue(forms.expand())
        forms.select(media)
        assertTrue(forms.expanded)
        forms.select(notice)
        assertFalse(forms.expanded)
        assertTrue(forms.expand())
        forms.select(media)
        assertFalse(forms.expanded)
        forms.select(null)
        assertFalse(forms.expand())
    }

    @Test fun idleDismissalSurvivesRepeatedRendersButNewActivityOrWakeRestoresIt() {
        val forms = IslandFormState()
        forms.dismissIdle(); forms.select(null)
        assertTrue(forms.idleSuppressed)
        forms.wake(); assertFalse(forms.idleSuppressed)
        forms.dismissIdle(); forms.select(Any())
        assertFalse(forms.idleSuppressed)
    }

    @Test fun threeFormsKeepTheSharedHeightAndPhysicalCutoutWithinTheirSafeWidths() {
        for (density in listOf(1f, 2.75f, 3f)) for (hole in 0..40) {
            val heightDp = IslandSizing.automaticHeight(hole)
            assertTrue(heightDp in 36..40)
            val short = IslandSizing.collapsed(density, 1200, 1000, (40 * density).toInt())
            val idle = IslandSizing.idleWidth(density, (heightDp * density).toInt(), (hole * density).toInt(), short)
            assertTrue(idle <= short)
            assertTrue(idle >= (heightDp * density).toInt())
            assertTrue(idle >= ((hole + 16) * density).toInt() - 1)
            assertTrue(IslandSizing.maximumWidth(density, 1200, 1000) >= short)
        }
    }
}
