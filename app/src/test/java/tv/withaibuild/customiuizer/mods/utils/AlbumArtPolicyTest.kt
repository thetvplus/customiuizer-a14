package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lock-screen art pipeline runs inside SystemUI and allocates a full-screen ARGB_8888
 * frame per result, so its two limits - what may be published and what may be held - are
 * memory limits before they are correctness ones.
 */
class AlbumArtPolicyTest {

    @Test
    fun theBudgetIsMeasuredInFramesOfTheActualTarget() {
        // 1080x2400 ARGB_8888 is 10.368 MB a frame. The old bound was three *entries*, which
        // is what made this cost 31 MB on a tall screen and a fraction of that on a short one.
        val oneFrame = 1080 * 2400 * 4

        assertEquals(oneFrame * 2, AlbumArtPolicy.cacheBudgetBytes(1080, 2400))
        assertEquals(AlbumArtPolicy.CACHE_BUDGET_FRAMES * oneFrame, AlbumArtPolicy.cacheBudgetBytes(1080, 2400))
    }

    @Test
    fun aBudgetForAnUnmeasuredTargetIsZero() {
        // applyTo is called before the view has been laid out; a cache must not be built for
        // a size that is about to change.
        assertEquals(0, AlbumArtPolicy.cacheBudgetBytes(0, 2400))
        assertEquals(0, AlbumArtPolicy.cacheBudgetBytes(1080, 0))
        assertEquals(0, AlbumArtPolicy.cacheBudgetBytes(-1, -1))
    }

    @Test
    fun anImplausiblyLargeTargetDoesNotOverflowIntoANegativeBudget() {
        // A negative budget would make LruCache throw and take the hook down with it.
        assertTrue(AlbumArtPolicy.cacheBudgetBytes(Int.MAX_VALUE, Int.MAX_VALUE) > 0)
    }

    @Test
    fun aChangedTargetSizeInvalidatesEveryCachedFrame() {
        val portrait = AlbumArtPolicy.cacheBudgetBytes(1080, 2400)
        val shorter = AlbumArtPolicy.cacheBudgetBytes(1080, 2000)

        assertTrue("entries built for another size can never be published", AlbumArtPolicy.shouldRebuildCache(portrait, shorter))
        assertFalse(AlbumArtPolicy.shouldRebuildCache(portrait, portrait))
    }

    @Test
    fun onlyTheNewestRequestMayPublish() {
        // Skipping through a playlist: generations 1..4 are all in flight or still computing
        // because a CPU blur has no cancellation point, and only the last may reach the view.
        assertFalse(AlbumArtPolicy.shouldPublish(resultGeneration = 1L, currentGeneration = 4L))
        assertFalse(AlbumArtPolicy.shouldPublish(resultGeneration = 3L, currentGeneration = 4L))
        assertTrue(AlbumArtPolicy.shouldPublish(resultGeneration = 4L, currentGeneration = 4L))
    }

    @Test
    fun aResultThatArrivesAfterAClearIsNotPublished() {
        // clear() bumps the generation, so work started before it can never write to the
        // static field it was cleared from.
        assertFalse(AlbumArtPolicy.shouldPublish(resultGeneration = 7L, currentGeneration = 8L))
    }
}
