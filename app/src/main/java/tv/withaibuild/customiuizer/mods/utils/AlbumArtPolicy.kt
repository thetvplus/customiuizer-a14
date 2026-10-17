package tv.withaibuild.customiuizer.mods.utils

/**
 * The decisions behind the lock-screen album art pipeline, separated from the bitmap work.
 *
 * All three exist because the previous version got them wrong in ways no compiler or lint
 * check could see, and none of them can be tested through the controller itself: every entry
 * point there takes a Bitmap, a Canvas or a LruCache.
 */
object AlbumArtPolicy {

    /**
     * How much the processed-art cache may hold, in bytes.
     *
     * The cache used to be bounded at three *entries*, which on a 1080x2400 screen is three
     * full-screen ARGB_8888 frames - about 31 MB held inside SystemUI for a feature the user
     * sees only on the lock screen. Bounding it by allocation size instead makes the limit
     * mean the same thing on every device.
     *
     * Two frames: the one on screen and the one before it, so going back to the previous
     * track is still free.
     */
    const val CACHE_BUDGET_FRAMES = 2

    private const val BYTES_PER_PIXEL = 4

    fun cacheBudgetBytes(targetWidth: Int, targetHeight: Int): Int {
        if (targetWidth <= 0 || targetHeight <= 0) return 0
        val frame = targetWidth.toLong() * targetHeight.toLong() * BYTES_PER_PIXEL
        val budget = frame * CACHE_BUDGET_FRAMES
        return budget.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Whether a cache built for [currentBudget] can still be used for [newBudget].
     *
     * A changed budget means the target view changed size, so every entry in it is the wrong
     * size to publish and is only holding memory.
     */
    fun shouldRebuildCache(currentBudget: Int, newBudget: Int): Boolean = currentBudget != newBudget

    /**
     * Whether a finished result may be published.
     *
     * Cancelling a job does not stop a CPU blur that is already running - it only stops the
     * coroutine at its next suspension point, and the blur has none. A late result therefore
     * still arrives, and without this check the lock screen would flick back to the artwork
     * of a track the user has already skipped past.
     */
    fun shouldPublish(resultGeneration: Long, currentGeneration: Long): Boolean =
        resultGeneration == currentGeneration
}
