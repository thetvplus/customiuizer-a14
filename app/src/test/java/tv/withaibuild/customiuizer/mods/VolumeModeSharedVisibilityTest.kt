package tv.withaibuild.customiuizer.mods

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeModeSharedVisibilityTest {

    @Test
    fun neitherHiddenKeepsSharedViewsUnchanged() {
        assertFalse(SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, false))
        assertFalse(SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, false))

        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        val layoutResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, false),
            View.VISIBLE
        )
        val dividerResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, false),
            View.VISIBLE
        )

        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, layoutResult)
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, dividerResult)
        assertFalse(layout.customHidden)
        assertFalse(divider.customHidden)
    }

    @Test
    fun singleHiddenHidesDividerButKeepsContainer() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        // Mute hidden, DND visible
        val layoutResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, false),
            View.VISIBLE
        )
        val dividerResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, false),
            View.VISIBLE
        )

        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, layoutResult)
        assertEquals(View.GONE, dividerResult)
        assertFalse(layout.customHidden)
        assertTrue(divider.customHidden)
        assertEquals(View.VISIBLE, divider.romVisibility)
    }

    @Test
    fun otherSingleHiddenHidesDividerButKeepsContainer() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        val layoutResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, true),
            View.VISIBLE
        )
        val dividerResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, true),
            View.VISIBLE
        )

        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, layoutResult)
        assertEquals(View.GONE, dividerResult)
        assertFalse(layout.customHidden)
        assertTrue(divider.customHidden)
    }

    @Test
    fun bothHiddenHidesContainerAndDivider() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        val layoutResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true),
            View.VISIBLE
        )
        val dividerResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, true),
            View.VISIBLE
        )

        assertEquals(View.GONE, layoutResult)
        assertEquals(View.GONE, dividerResult)
        assertTrue(layout.customHidden)
        assertTrue(divider.customHidden)
        assertEquals(View.VISIBLE, layout.romVisibility)
        assertEquals(View.VISIBLE, divider.romVisibility)
    }

    @Test
    fun singleHiddenToBothVisibleRestoresDividerOnly() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        // Start: Mute hidden
        SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, false),
            View.VISIBLE
        )

        // End: both visible
        val layoutResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, false),
            View.VISIBLE
        )
        val dividerResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, false),
            View.GONE
        )

        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, layoutResult)
        assertEquals(View.VISIBLE, dividerResult)
        assertFalse(divider.customHidden)
    }

    @Test
    fun bothHiddenToOneEnabledRestoresContainerDividerStaysHidden() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        // Start: both hidden
        SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true),
            View.VISIBLE
        )
        SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, true),
            View.VISIBLE
        )

        // End: only DND hidden
        val layoutResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, true),
            View.GONE
        )
        val dividerResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, true),
            View.GONE
        )

        assertEquals(View.VISIBLE, layoutResult)
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, dividerResult)
        assertFalse(layout.customHidden)
        assertTrue(divider.customHidden)
    }

    @Test
    fun bothHiddenToBothEnabledRestoresExactBaseline() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        // Start: both hidden
        SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true),
            View.VISIBLE
        )
        SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, true),
            View.VISIBLE
        )

        // End: both visible
        val layoutResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, false),
            View.GONE
        )
        val dividerResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, false),
            View.GONE
        )

        assertEquals(View.VISIBLE, layoutResult)
        assertEquals(View.VISIBLE, dividerResult)
        assertFalse(layout.customHidden)
        assertFalse(divider.customHidden)
    }

    @Test
    fun romAlreadyGoneIsNotClaimed() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.GONE)
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.GONE)

        val layoutResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true),
            View.GONE
        )
        val dividerResult = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, true),
            View.GONE
        )

        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, layoutResult)
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, dividerResult)
        assertFalse(layout.customHidden)
        assertFalse(divider.customHidden)
    }

    @Test
    fun externalVisibilityChangeWhileHiddenAdoptedAsBaseline() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        // Module hides container
        SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true),
            View.VISIBLE
        )

        // ROM/external changes container to INVISIBLE while still module-hidden
        val keepHidden = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true),
            View.INVISIBLE
        )
        assertEquals(View.GONE, keepHidden)
        assertEquals(View.INVISIBLE, layout.romVisibility)

        // Unhide: restore the newer baseline, not VISIBLE
        val restore = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, false),
            View.GONE
        )
        assertEquals(View.INVISIBLE, restore)
    }

    @Test
    fun externalVisibilityChangeBeforeUnhideWins() {
        val divider = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        // Module hides divider
        SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, false),
            View.VISIBLE
        )

        // External restores divider before module disables hide
        val noWrite = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            divider,
            SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, false),
            View.INVISIBLE
        )
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, noWrite)
        assertEquals(View.INVISIBLE, divider.romVisibility)
        assertFalse(divider.customHidden)
    }

    @Test
    fun twoHelperCallbacksAreIdempotent() {
        val layout = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        val first = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true),
            View.VISIBLE
        )
        assertEquals(View.GONE, first)

        val second = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            layout,
            SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true),
            View.GONE
        )
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, second)
        assertTrue(layout.customHidden)
    }

    @Test
    fun policiesMatchExpectedMatrix() {
        assertFalse(SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, false))
        assertTrue(SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, false))
        assertTrue(SystemUIControlCenterHooks.shouldHideVolumeModeDivider(false, true))
        assertTrue(SystemUIControlCenterHooks.shouldHideVolumeModeDivider(true, true))

        assertFalse(SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, false))
        assertFalse(SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, false))
        assertFalse(SystemUIControlCenterHooks.shouldHideVolumeModeContainer(false, true))
        assertTrue(SystemUIControlCenterHooks.shouldHideVolumeModeContainer(true, true))
    }
}
