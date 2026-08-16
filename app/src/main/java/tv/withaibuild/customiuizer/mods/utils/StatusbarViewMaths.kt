package tv.withaibuild.customiuizer.mods.utils

/**
 * Pure, Android-independent math helpers for status bar view insertion.
 *
 * These functions intentionally do not touch any Android classes so they can be
 * unit-tested on the JVM without a robolectric or device environment.
 */
object StatusbarViewMaths {

    /**
     * Clamp a requested insert index to a safe [0, childCount] range.
     *
     * @param requested the index requested by the caller
     * @param childCount the current child count of the target ViewGroup
     * @return a safe index that is guaranteed not to throw
     *         [IndexOutOfBoundsException] when used with [android.view.ViewGroup.addView]
     */
    @JvmStatic
    fun clampStatusIconInsertIndex(requested: Int, childCount: Int): Int = when {
        requested < 0 -> 0
        requested > childCount -> childCount
        else -> requested
    }
}
