package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class IslandSizingTest {
    @Test fun longContentLeavesVisibleSpaceBeforeTheScreenCornersAtEveryHeight() {
        for (height in 32..64) for (density in listOf(1f, 2.75f, 3f)) {
            val screen = (360 * density).roundToInt()
            val width = IslandSizing.expanded(density, screen, screen, (40 * density).roundToInt(),
                10000f, height)
            assertTrue("height=$height density=$density width=$width", width <= screen - (48 * density).roundToInt())
        }
    }

    @Test fun elasticTravelUsesOnlyTheRemainingSafeSpaceOnBothSides() {
        assertEquals(328, IslandSizing.elasticMaximum(320, 40, 24, 376, 1f))
        assertEquals(320, IslandSizing.elasticMaximum(320, 24, 24, 376, 1f))
        assertEquals(324, IslandSizing.elasticMaximum(320, 54, 24, 376, 1f))
        assertEquals(320, IslandSizing.elasticMaximum(320, 40, 40, 360, 1f))
    }

    @Test fun automaticHeightFitsTheNativeRowWithoutPretendingToFillIt() {
        assertEquals(38, IslandSizing.automaticHeight(24, 46))
        assertEquals(36, IslandSizing.automaticHeight(24, 42))
        assertEquals(34, IslandSizing.automaticHeight(26, 40))
        assertEquals(38, IslandSizing.automaticHeight(24))
    }
    @Test fun idleAutomaticWidthUsesOneHundredDpAndLeavesRoomForTheShortForm() {
        assertEquals(100, IslandSizing.idleWidth(1f, 38, 20, 164))
        assertEquals(300, IslandSizing.idleWidth(3f, 114, 60, 492))
        assertEquals(68, IslandSizing.idleWidth(1f, 38, 20, 164, 68))
        assertEquals(120, IslandSizing.idleWidth(1f, 64, 20, 136))
        assertEquals(116, IslandSizing.idleWidth(1f, 38, 100, 132))
    }
    @Test fun expandedContentUsesMoreRoomWithoutChangingTheCompactCeiling() {
        val expanded = IslandSizing.expanded(1f, 372, 372, 40, 150f, 38)
        assertTrue(expanded in 273..312)
        assertEquals(218, IslandSizing.maximumWidth(1f, 372, 372))
        assertEquals(232, IslandSizing.expanded(1f, 280, 280, 40, 500f))
        assertTrue(IslandSizing.expanded(1f, 372, 372, 40, 150f) >=
            IslandSizing.expanded(1f, 372, 372, 40, 20f))
    }
    @Test fun expandedManualRangeCanUseTheViewportWhileAutomaticWidthRetainsItsClearance() {
        assertEquals(320, IslandSizing.collapsed(1f, 400, 400, 40, 24f, 0f,
            shortDp = 320, longDp = 400, manual = true))
        assertEquals(352, IslandSizing.collapsed(1f, 400, 400, 40, 24f, 1000f,
            shortDp = 320, longDp = 400, manual = true))
        assertEquals(220, IslandSizing.maximumWidth(1f, 400, 220, heightDp = 64))
        assertEquals(218, IslandSizing.maximumWidth(1f, 400, 400))
    }
    @Test fun animationUsesTheSameConfiguredAndNativeSafeLimitAsLayout() {
        assertEquals(218, IslandSizing.maximumWidth(1f, 400, 320))
        assertEquals(280, IslandSizing.maximumWidth(1f, 400, 320, manual = true, longDp = 280))
        assertEquals(210, IslandSizing.maximumWidth(1f, 400, 210, manual = true, longDp = 280))
        assertEquals(200, IslandSizing.maximumWidth(1f, 400, 320, romMaximum = 200))
        assertEquals(152, IslandSizing.maximumWidth(1f, 200, 320))
    }
    @Test fun manualShortAndMediumWidthsStillYieldToNativeEssentialIconsAndScreenBounds() {
        assertEquals(200, IslandSizing.collapsed(1f, 372, 300, 40, 24f, 0f,
            shortDp = 200, longDp = 260, manual = true))
        assertEquals(260, IslandSizing.collapsed(1f, 372, 300, 40, 24f, 100f,
            shortDp = 200, longDp = 260, manual = true))
        assertEquals(220, IslandSizing.collapsed(1f, 372, 220, 40, 24f, 100f,
            shortDp = 200, longDp = 260, manual = true))
        assertEquals(160, IslandSizing.collapsed(1f, 372, 300, 40, 24f, 0f,
            shortDp = 200, longDp = 260, manual = false))
        assertEquals(224, IslandSizing.collapsed(1f, 372, 300, 40, 24f, 100f,
            shortDp = 224, longDp = 184, manual = true))
    }
    @Test fun reducingManualShortWidthCannotPromoteUnchangedMusicToTheLongForm() {
        for (density in listOf(1f, 2.75f, 3f)) {
            val before = IslandSizing.collapsed(density, 4000, 4000, (40 * density).toInt(),
                24 * density, 24 * density, shortDp = 184, longDp = 240, manual = true)
            val after = IslandSizing.collapsed(density, 4000, 4000, (40 * density).toInt(),
                24 * density, 24 * density, shortDp = 96, longDp = 240, manual = true)
            assertEquals((96 * density).toInt(), after)
            assertTrue(after < before)
        }
    }

    @Test fun manualFormThresholdTracksNaturalHeightRatherThanEitherWidthSlider() {
        for (height in 32..64) for (caption in listOf(0f, 56f, 56.01f, 100f)) {
            var previous = Int.MAX_VALUE
            for (short in 320 downTo 96) {
                val width = IslandSizing.collapsed(1f, 1000, 1000, 40, 24f, caption,
                    shortDp = short, longDp = 400, manual = true, heightDp = height)
                assertTrue("Shrinking short $short at height $height must not grow $caption", width <= previous)
                previous = width
            }
        }
        assertEquals(96, IslandSizing.collapsed(1f, 1000, 1000, 40, 24f, 56f,
            shortDp = 96, longDp = 240, manual = true))
        assertEquals(240, IslandSizing.collapsed(1f, 1000, 1000, 40, 24f, 56.01f,
            shortDp = 96, longDp = 240, manual = true))
    }

    @Test fun longWidthNeverGrowsIntoTheRetiredPanelRange() {
        for (content in listOf(0f,100f,10000f)) {
            assertTrue(IslandSizing.collapsed(3f,1080,1080,120,24f,content) <= 720)
            assertTrue(IslandSizing.collapsed(3f,1080,600,120,24f,content) <= 600)
        }
    }
    @Test fun compactAndMediumFormsFitTheirContentOnlyWithinTheEssentialIconsClearance() {
        assertEquals(160, IslandSizing.collapsed(1f, 372, 230, 40, 24f, 0f))
        val medium = IslandSizing.collapsed(1f, 372, 230, 40, 24f, 73f)
        assertEquals(218, medium)
        assertTrue((medium - 40) / 2 - 16 >= 73)
        assertEquals(218, IslandSizing.collapsed(1f, 372, 230, 40, 24f, 300f))
        assertEquals(160, IslandSizing.collapsed(1f, 320, 160, 40, 24f, 76f))
        assertEquals(200, IslandSizing.collapsed(1f, 372, 250, 40, 24f, 200f, 200))
    }
}
