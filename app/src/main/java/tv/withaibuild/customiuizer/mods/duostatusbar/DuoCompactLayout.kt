package tv.withaibuild.customiuizer.mods.duostatusbar

/** Direct primitive translation of 24B91020 compact_selectors.derived.hpp.
 * A14 supplies normal-update/Wi-Fi/airplane inputs; absent platform sources
 * remain false rather than being inferred from loss of cellular service. */
internal object DuoCompactLayout {
    const val EMPTY=3
    const val WIFI=4
    const val AIRPLANE=7
    const val CELLULAR=8
    fun primary(update: Int,liquid: Boolean,wifi: Boolean,satellite: Boolean,airplane: Boolean): Int {
        if(update!=3) return update
        if(wifi) return if(liquid && airplane) 6 else WIFI
        if(liquid || satellite) return if(liquid) 6 else 5
        return if(airplane) EMPTY else CELLULAR
    }
    fun secondary(primary: Int,airplane: Boolean,liquid: Boolean,sos: Boolean): Int {
        if(airplane) return AIRPLANE
        if(primary==3 || primary==7 || primary==8 || primary==9) return if(sos) 9 else EMPTY
        if(liquid && primary!=6) return 6
        return if(sos) 9 else CELLULAR
    }
    fun primary(network: Int) = primary(3,false,network and DuoNetworkState.WIFI_CONNECTED!=0,
        false,network and DuoNetworkState.AIRPLANE!=0)
    fun secondary(network: Int) = secondary(primary(network),network and DuoNetworkState.AIRPLANE!=0,false,false)
}
