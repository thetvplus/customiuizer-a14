package tv.withaibuild.customiuizer.mods

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeModeButtonVisibilityOwnershipTest {

    @Test
    fun visibleBaselineHidesAndRestoresExactly() {
        val ownership = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)

        val first = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = true,
            currentVisibility = View.VISIBLE
        )
        assertEquals(View.GONE, first)
        assertTrue(ownership.customHidden)
        assertEquals(View.VISIBLE, ownership.romVisibility)

        val second = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = false,
            currentVisibility = View.GONE
        )
        assertEquals(View.VISIBLE, second)
        assertFalse(ownership.customHidden)
    }

    @Test
    fun invisibleBaselineHidesAndRestoresInvisible() {
        val ownership = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.INVISIBLE)

        val first = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = true,
            currentVisibility = View.INVISIBLE
        )
        assertEquals(View.GONE, first)
        assertTrue(ownership.customHidden)
        assertEquals(View.INVISIBLE, ownership.romVisibility)

        val second = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = false,
            currentVisibility = View.GONE
        )
        assertEquals(View.INVISIBLE, second)
        assertFalse(ownership.customHidden)
    }

    @Test
    fun romAlreadyGoneIsNotClaimedByModule() {
        val ownership = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.GONE)

        val first = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = true,
            currentVisibility = View.GONE
        )
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, first)
        assertFalse(ownership.customHidden)
        assertEquals(View.GONE, ownership.romVisibility)

        val second = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = false,
            currentVisibility = View.GONE
        )
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, second)
        assertFalse(ownership.customHidden)
    }

    @Test
    fun keepHiddenDoesNotRepeatWrite() {
        val ownership = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        ownership.customHidden = true

        val result = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = true,
            currentVisibility = View.GONE
        )
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, result)
        assertTrue(ownership.customHidden)
        assertEquals(View.VISIBLE, ownership.romVisibility)
    }

    @Test
    fun externalChangeWhileHiddenAdoptsAndRehides() {
        val ownership = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        ownership.customHidden = true

        val result = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = true,
            currentVisibility = View.INVISIBLE
        )
        assertEquals(View.GONE, result)
        assertTrue(ownership.customHidden)
        assertEquals(View.INVISIBLE, ownership.romVisibility)

        val restore = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = false,
            currentVisibility = View.GONE
        )
        assertEquals(View.INVISIBLE, restore)
        assertFalse(ownership.customHidden)
    }

    @Test
    fun externalRestoreBeforeUnhideAdoptsAndDoesNotOverride() {
        val ownership = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        ownership.customHidden = true

        val result = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = false,
            currentVisibility = View.INVISIBLE
        )
        assertEquals(SystemUIControlCenterHooks.NO_VISIBILITY_WRITE, result)
        assertFalse(ownership.customHidden)
        assertEquals(View.INVISIBLE, ownership.romVisibility)
    }

    @Test
    fun noOpDisabledDoesNotWriteAnything() {
        for (current in listOf(View.VISIBLE, View.INVISIBLE, View.GONE)) {
            val ownership = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(current)
            val result = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
                ownership,
                shouldHide = false,
                currentVisibility = current
            )
            assertEquals(
                "disabled with visibility $current must not write",
                SystemUIControlCenterHooks.NO_VISIBILITY_WRITE,
                result
            )
            assertFalse(ownership.customHidden)
        }
    }

    @Test
    fun disabledAfterModuleHideReleasesAndRestoresOriginal() {
        val ownership = SystemUIControlCenterHooks.VolumeModeButtonVisibilityOwnership(View.VISIBLE)
        ownership.customHidden = true

        val result = SystemUIControlCenterHooks.reconcileVolumeModeButtonVisibility(
            ownership,
            shouldHide = false,
            currentVisibility = View.GONE
        )
        assertEquals(View.VISIBLE, result)
        assertFalse(ownership.customHidden)
    }
}
