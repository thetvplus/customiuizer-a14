package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandSizingTest {
    @Test fun expandedManualRangeCanUseTheViewportWhileAutomaticWidthRetainsItsClearance() {
        assertEquals(320, IslandSizing.collapsed(1f, 400, 400, 40, 24f, 0f,
            shortDp = 320, longDp = 400, manual = true))
        assertEquals(376, IslandSizing.collapsed(1f, 400, 400, 40, 24f, 1000f,
            shortDp = 320, longDp = 400, manual = true))
        assertEquals(220, IslandSizing.maximumWidth(1f, 400, 220, heightDp = 64))
        assertEquals(240, IslandSizing.maximumWidth(1f, 400, 400))
    }
    @Test fun animationUsesTheSameConfiguredAndNativeSafeLimitAsLayout() {
        assertEquals(240, IslandSizing.maximumWidth(1f, 400, 320))
        assertEquals(280, IslandSizing.maximumWidth(1f, 400, 320, manual = true, longDp = 280))
        assertEquals(210, IslandSizing.maximumWidth(1f, 400, 210, manual = true, longDp = 280))
        assertEquals(200, IslandSizing.maximumWidth(1f, 400, 320, romMaximum = 200))
        assertEquals(176, IslandSizing.maximumWidth(1f, 200, 320))
    }
    @Test fun manualShortAndMediumWidthsStillYieldToNativeEssentialIconsAndScreenBounds() {
        assertEquals(200, IslandSizing.collapsed(1f, 372, 300, 40, 24f, 0f,
            shortDp = 200, longDp = 260, manual = true))
        assertEquals(260, IslandSizing.collapsed(1f, 372, 300, 40, 24f, 100f,
            shortDp = 200, longDp = 260, manual = true))
        assertEquals(220, IslandSizing.collapsed(1f, 372, 220, 40, 24f, 100f,
            shortDp = 200, longDp = 260, manual = true))
        assertEquals(184, IslandSizing.collapsed(1f, 372, 300, 40, 24f, 0f,
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
        assertEquals(184, IslandSizing.collapsed(1f, 372, 230, 40, 24f, 0f))
        val medium = IslandSizing.collapsed(1f, 372, 230, 40, 24f, 78f)
        assertEquals(228, medium)
        assertTrue((medium - 40) / 2 - 16 >= 78)
        assertEquals(230, IslandSizing.collapsed(1f, 372, 230, 40, 24f, 300f))
        assertEquals(160, IslandSizing.collapsed(1f, 320, 160, 40, 24f, 78f))
        assertEquals(200, IslandSizing.collapsed(1f, 372, 250, 40, 24f, 200f, 200))
    }
}
