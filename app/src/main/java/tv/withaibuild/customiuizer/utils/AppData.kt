package tv.withaibuild.customiuizer.utils

class AppData {
    @JvmField
    var label: String = ""

    @JvmField
    var pkgName: String = ""

    @JvmField
    var actName: String = ""

    @JvmField
    var enabled: Boolean = false

    @JvmField
    var user: Int = 0

    /** Cached, locale-aware lowercase forms to avoid repeated allocation in filtering/sorting. */
    @JvmField
    var labelLower: String = ""

    @JvmField
    var actNameLower: String = ""

    /** Stable cache key for [Helpers.memoryCache] and icon loaders. */
    @JvmField
    var iconKey: String = ""
}
