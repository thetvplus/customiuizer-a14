package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class IslandEdgeGeometryTest {
    @Test fun inwardOffsetsStopAtTheUsableSideInsteadOfDroppingBelowTheBar() {
        for (alignment in listOf(0, 2)) for (offset in -240..240) {
            val placed = IslandPosition.layout(160, 40, 88, 12, 388, alignment, offset,
                8, 0, 188, 212, 0, 32, 8)!!
            assertEquals(8, placed.top)
            assertTrue(placed.width >= 88)
            assertTrue(if (alignment == 0) placed.left + placed.width <= 180 else placed.left >= 220)
        }
    }

    @Test fun roundedCornerClearanceTracksItsActualArcAndShrinksBelowTheCorner() {
        assertEquals(100, IslandPosition.cornerInset(100, 0))
        assertEquals(40, IslandPosition.cornerInset(100, 20))
        assertEquals(14, IslandPosition.cornerInset(100, 50))
        assertEquals(0, IslandPosition.cornerInset(100, 100))
        var previous = 100
        for (top in 0..100) {
            val inset = IslandPosition.cornerInset(100, top)
            assertTrue(inset <= previous)
            assertTrue((100 - inset).toDouble() * (100 - inset) + (100 - top).toDouble() * (100 - top) <= 10000)
            previous = inset
        }
    }

    @Test fun centerOffsetsMoveContinuouslyToTheirCameraSafeLimitsWithoutSelectingAnotherAnchor() {
        var previous = -1
        for (offset in -80..80) {
            val placed = IslandPosition.layout(184, 40, 80, 0, 400, 1, offset, 0, 40, 188, 212, 0, 28)!!
            assertTrue(placed.left >= previous)
            if (previous >= 0) assertTrue(placed.left - previous <= 1)
            assertTrue(placed.left + 8 <= 188)
            assertTrue(placed.left + placed.width - 8 >= 212)
            previous = placed.left
        }
        assertEquals(14, IslandSizing.horizontalOffsetLimit(168, 68, 40, 40))
        assertEquals(24, IslandSizing.horizontalOffsetLimit(168, 168, 40, 40))
        assertEquals(0, IslandSizing.horizontalOffsetLimit(96, 68, 40, 40))
    }
    @Test fun realCameraWidthDensityManualWidthsAndOffsetsRetainTheSelectedSafeEdge() {
        for (density in listOf(.75f, 1f, 2.75f, 3f, 4f)) {
            fun px(dp: Int) = (dp * density).roundToInt()
            for (holeWidth in listOf(12, 24, 40)) for (width in listOf(160, 184, 224, 240, 280)) {
                val leftHole = px(200 - holeWidth / 2)
                val rightHole = px(200 + holeWidth / 2)
                for (alignment in listOf(0, 2)) for (offset in listOf(-80, 0, 80)) {
                    var expectedEdge = (if (alignment == 0) px(4) + px(offset) else px(394) + px(offset))
                        .coerceIn(px(4), px(394))
                    expectedEdge = if (alignment == 0) minOf(expectedEdge, leftHole - px(8) - px(80))
                        else maxOf(expectedEdge, rightHole + px(8) + px(80))
                    val layout = IslandPosition.layout(px(width), px(40), px(80), px(4), px(394),
                        alignment, px(offset), px(8), 0, leftHole, rightHole, 0, px(32), px(8))
                    if (if (alignment == 0) px(394) - expectedEdge < px(80) else expectedEdge - px(4) < px(80)) {
                        assertNull(layout); continue
                    }
                    val placed = requireNotNull(layout)
                    assertEquals(0, placed.gap)
                    assertEquals(expectedEdge, if (alignment == 0) placed.left else placed.left + placed.width)
                    assertTrue(placed.left >= px(4) && placed.left + placed.width <= px(394))
                    assertTrue(placed.top >= px(32) || placed.left + placed.width <= leftHole - px(8) ||
                        placed.left >= rightHole + px(8))
                }
            }
        }
    }

    @Test fun aCornerCutoutOrInsufficientSideSpaceMovesDownWithoutJumpingAcrossTheScreen() {
        val left = IslandPosition.layout(240, 40, 80, 0, 400, 0, 0, 0, 0, 0, 36, 0, 40, 8)!!
        assertEquals(IslandPosition.Layout(0, 48, 240, 0), left)
        val right = IslandPosition.layout(240, 40, 80, 0, 400, 2, 0, 0, 0, 364, 400, 0, 40, 8)!!
        assertEquals(IslandPosition.Layout(160, 48, 240, 0), right)
        assertNull(IslandPosition.layout(240, 40, 100, 0, 90, 0, 0, 0, 0))
    }

    @Test fun sideSizingUsesContiguousContentAndIgnoresTheClockBatteryClearance() {
        assertEquals(164, IslandSizing.collapsed(1f, 400, 400, 0, 24f, 100f, centered = false))
        assertEquals(218, IslandSizing.maximumWidth(1f, 240, 400, centered = false))
        assertEquals(192, IslandSizing.maximumWidth(1f, 240, 400)) // 24 dp per centered edge.
        val camera = IslandPosition.layout(184, 40, 140, 0, 400, 1, 0, 8, 40, 192, 208, 0, 32)!!
        assertEquals(108, camera.left)
        assertEquals(40, camera.gap)
        // Rotation can change physical safe edges without changing alignment semantics.
        assertEquals(32, IslandPosition.layout(184, 40, 80, 32, 788, 0, 0, 48, 0)!!.left)
    }
}
