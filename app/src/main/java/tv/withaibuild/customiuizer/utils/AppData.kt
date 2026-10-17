package tv.withaibuild.customiuizer.utils

import java.util.Locale

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

    /** Prepare once after populating a row, before it is shared with a filter or loader. */
    fun prepareForList() {
        labelLower = label.lowercase(Locale.ROOT)
        actNameLower = actName.lowercase(Locale.ROOT)
        iconKey = if (actName.isNotEmpty()) "$pkgName|$actName" else pkgName
    }
}
