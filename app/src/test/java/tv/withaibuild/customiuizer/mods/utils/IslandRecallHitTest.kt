package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandRecallHitTest {

    @Test
    fun marginIsDensityAwareAndCapped() {
        val slop = 16
        val mdpi = IslandRecallHit.extraMarginPx(1f, slop)
        val xxhdpi = IslandRecallHit.extraMarginPx(2.75f, slop)
        assertEquals(32, mdpi)
        assertTrue(xxhdpi > mdpi)
        assertTrue(xxhdpi <= (36f * 2.75f + 0.5f).toInt())
    }

    @Test
    fun delegateStaysAroundCapsuleNotFullParent() {
        val rect = IslandRecallHit.delegateRect(1080, 208, 420, 36, 240, 80, 32)
        assertEquals(388, rect.left)
        assertEquals(4, rect.top)
        assertEquals(692, rect.right)
        assertEquals(148, rect.bottom)
        assertTrue(rect.width < 1080)
        assertTrue(rect.height < 208)
    }

    @Test
    fun invalidBoundsYieldEmptyRect() {
        val empty = IslandRecallHit.delegateRect(0, 208, 10, 10, 40, 40, 16)
        assertTrue(empty.isEmpty)
    }
}
