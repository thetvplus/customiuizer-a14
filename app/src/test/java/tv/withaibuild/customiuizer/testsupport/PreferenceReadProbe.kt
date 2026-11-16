package tv.withaibuild.customiuizer.testsupport

import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicReference

/** Installs the test's counting map without copying away its read counters. */
internal fun PrefMap.installPreferenceReadProbe(values: Map<String, Any>) {
    val snapshot = PrefMap::class.java.declaredClasses.single { it.simpleName == "Snapshot" }
        .getDeclaredConstructor(Map::class.java).apply { isAccessible = true }.newInstance(values)
    val field = PrefMap::class.java.getDeclaredField("snapshot").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    (field.get(this) as AtomicReference<Any>).set(snapshot)
}
