package tv.withaibuild.customiuizer.mods.utils.feature

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

class DynamicIslandWindowEnvelopeTest {

    @Test
    fun topEnvelope_hasExpectedGeometry() {
        val envelope = DynamicIslandWindowEnvelope.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomClearancePx = 24,
            maxEdgeTravelPx = 55
        )

        assertEquals(StrongToastPosition.TOP, envelope.position)
        // margin + capsule + clearance, with no corner-radius term.
        assertEquals(183, envelope.requiredHostHeightPx)
        assertEquals(18, envelope.shellTopMarginPx)
        assertEquals(0, envelope.shellBottomMarginPx)
        assertEquals(0, envelope.parentPaddingTopPx)
        assertEquals(24, envelope.parentPaddingBottomPx)
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, envelope.parentGravity)
        assertEquals(18, envelope.restingShellTopPx)
        assertEquals(159, envelope.restingShellBottomPx)
        assertEquals(0f, envelope.pivotY, 0.0001f)
        assertEquals(-18f, envelope.entranceStartTranslationY, 0.0001f)
        assertEquals(-18f, envelope.maxDragTranslationY, 0.0001f)
    }

    @Test
    fun bottomEnvelope_hasExpectedGeometry() {
        val envelope = DynamicIslandWindowEnvelope.forBottom(
            visualHeightPx = 141,
            topClearancePx = 18,
            bottomPaddingPx = 90,
            maxEdgeTravelPx = 55
        )

        assertEquals(StrongToastPosition.BOTTOM, envelope.position)
        assertEquals(249, envelope.requiredHostHeightPx)
        assertEquals(0, envelope.shellTopMarginPx)
        assertEquals(90, envelope.shellBottomMarginPx)
        assertEquals(18, envelope.parentPaddingTopPx)
        assertEquals(0, envelope.parentPaddingBottomPx)
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, envelope.parentGravity)
        assertEquals(18, envelope.restingShellTopPx)
        assertEquals(159, envelope.restingShellBottomPx)
        assertEquals(141f, envelope.pivotY, 0.0001f)
        // The travel is capped at maxEdgeTravelPx even though the padding is larger.
        assertEquals(55f, envelope.entranceStartTranslationY, 0.0001f)
        assertEquals(55f, envelope.maxDragTranslationY, 0.0001f)
    }

    @Test
    fun hostHeightIsMarginPlusCapsulePlusClearanceOnly() {
        // No rounding/corner safety term may be folded into the host height: the pill's radius is
        // inside the capsule rectangle and is owned by DynamicIslandCapsuleView.
        val top = DynamicIslandWindowEnvelope.forTop(132, 17, 22, 55)
        assertEquals(132 + 17 + 22, top.requiredHostHeightPx)

        val bottom = DynamicIslandWindowEnvelope.forBottom(132, 17, 22, 55)
        assertEquals(132 + 17 + 22, bottom.requiredHostHeightPx)
    }

    @Test
    fun topFitsAllPhases() {
        val envelope = DynamicIslandWindowEnvelope.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomClearancePx = 24,
            maxEdgeTravelPx = 55
        )

        assertTrue(
            "capsule must fit at every phase including the entrance start",
            envelope.fitsAllPhases(141, envelope.restingShellTopPx)
        )
    }

    @Test
    fun bottomFitsAllPhases() {
        val envelope = DynamicIslandWindowEnvelope.forBottom(
            visualHeightPx = 141,
            topClearancePx = 18,
            bottomPaddingPx = 90,
            maxEdgeTravelPx = 55
        )

        assertTrue(
            "capsule must fit at every phase including the entrance start",
            envelope.fitsAllPhases(141, envelope.restingShellTopPx)
        )
    }

    @Test
    fun bottomSmallBottomPaddingStillFits() {
        // Simulates a device with very small bottom safe inset/gap: the envelope must
        // still keep the entrance start inside the host Window.
        val envelope = DynamicIslandWindowEnvelope.forBottom(
            visualHeightPx = 100,
            topClearancePx = 4,
            bottomPaddingPx = 4,
            maxEdgeTravelPx = 55
        )

        assertTrue(envelope.fitsAllPhases(100, envelope.restingShellTopPx))
        assertEquals(4f, envelope.entranceStartTranslationY, 0.0001f)
    }

    @Test
    fun entranceStartScaleShrinksUniformlyAndNeverOvershoots() {
        val top = DynamicIslandWindowEnvelope.forTop(141, 18, 24, 55)
        val bottom = DynamicIslandWindowEnvelope.forBottom(141, 18, 90, 55)

        assertTrue(top.entranceStartScale > 0f && top.entranceStartScale <= 1f)
        assertEquals(top.entranceStartScale, bottom.entranceStartScale, 0.0001f)
        assertEquals(
            DynamicIslandWindowEnvelope.ENTRANCE_START_SCALE,
            top.entranceStartScale,
            0.0001f
        )
    }

    @Test
    fun negativeInputsAreClampedInsteadOfProducingNegativeGeometry() {
        val envelope = DynamicIslandWindowEnvelope.forTop(-10, -5, -7, -3)

        assertEquals(0, envelope.requiredHostHeightPx)
        assertEquals(0, envelope.shellTopMarginPx)
        assertEquals(0, envelope.restingShellTopPx)
        assertEquals(0f, envelope.entranceStartTranslationY, 0.0001f)
    }
}
