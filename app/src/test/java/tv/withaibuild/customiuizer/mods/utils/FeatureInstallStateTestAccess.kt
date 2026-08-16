package tv.withaibuild.customiuizer.mods.utils

/**
 * Test-side helper that clears the private [FeatureInstallState.states] map under the same
 * synchronization used by production, without broadening the field visibility.
 */
object FeatureInstallStateTestAccess {

    @JvmStatic
    fun clear() {
        val field = FeatureInstallState::class.java.getDeclaredField("states")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(null) as MutableMap<Int, FeatureState>
        synchronized(map) { map.clear() }
    }
}
