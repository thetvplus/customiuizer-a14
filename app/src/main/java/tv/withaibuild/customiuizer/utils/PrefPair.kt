package tv.withaibuild.customiuizer.utils

/**
 * Encoding of the `first|second` preference values used across the module.
 *
 * The format is stored by the settings app and read back by hooks: `package|activity`,
 * `bssid|ssid`, `address|name`. It used to be decoded ad hoc in six places, half of them with a
 * freshly compiled [Regex] per call, on paths that run per list row and per keyguard evaluation.
 *
 * Everything here is dependency free so it stays unit testable, and the matching helpers do not
 * allocate: hook callbacks only ever need the first component.
 */
object PrefPair {

    const val DELIMITER = '|'

    /** Length of the first component of [pair], i.e. the part before the first [DELIMITER]. */
    private fun firstLength(pair: String): Int {
        val separator = pair.indexOf(DELIMITER)
        return if (separator < 0) pair.length else separator
    }

    /** Returns the part before the first [DELIMITER], or the whole string when there is none. */
    @JvmStatic
    fun first(pair: String): String {
        val length = firstLength(pair)
        return if (length == pair.length) pair else pair.substring(0, length)
    }

    /** Case-insensitive comparison of the first component of [pair] with [needle], without allocating. */
    @JvmStatic
    fun firstEquals(pair: String, needle: String): Boolean {
        val length = firstLength(pair)
        return length == needle.length && pair.regionMatches(0, needle, 0, length, ignoreCase = true)
    }

    /** True when any entry of [pairs] has [needle] as its first component. */
    @JvmStatic
    fun containsFirst(pairs: Set<String>?, needle: String): Boolean {
        if (pairs.isNullOrEmpty()) return false
        for (pair in pairs) if (firstEquals(pair, needle)) return true
        return false
    }
}
