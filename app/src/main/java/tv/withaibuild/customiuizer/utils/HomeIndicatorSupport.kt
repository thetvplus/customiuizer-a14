package tv.withaibuild.customiuizer.utils

/** Settings can check the platform; the host resolves the actual drawing contract. */
internal object HomeIndicatorSupport {
    fun supported(sdk: Int, abis: Array<String>) = sdk == 34 && abis.firstOrNull() == "arm64-v8a"
}
