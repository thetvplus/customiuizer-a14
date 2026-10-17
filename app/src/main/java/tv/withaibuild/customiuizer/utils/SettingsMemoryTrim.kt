package tv.withaibuild.customiuizer.utils

import android.content.ComponentCallbacks2

/**
 * Releases Settings-app caches that can be rebuilt on the next user visit.
 *
 * User selections, preference snapshots and still-visible adapter contents are not owned here.
 * Callers must not rebuild these lists from a trim callback.
 */
object SettingsMemoryTrim {

    @JvmStatic
    fun shouldReleaseRegenerableCaches(level: Int): Boolean =
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN

    @JvmStatic
    fun releaseRegenerableCaches() {
        Helpers.memoryCache.evictAll()
        AppHelper.installedAppsList = null
        Helpers.shareAppsList = null
        Helpers.openWithAppsList = null
        Helpers.launchableAppsList = null
    }
}
