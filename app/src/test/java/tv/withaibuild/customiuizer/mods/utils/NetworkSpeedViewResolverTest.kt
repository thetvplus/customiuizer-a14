package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class NetworkSpeedViewResolverTest {

    companion object {
        private const val AOSP_NAME = "com.android.systemui.statusbar.views.NetworkSpeedView"
        private const val MIUI_NAME = "com.miui.systemui.statusbar.views.NetworkSpeedView"
    }

    @Test
    fun resolvesAospNetworkSpeedView() {
        val loader = ClassLoader.getSystemClassLoader()
        val probe: (String, ClassLoader) -> Class<*>? = { name, _ ->
            if (name == AOSP_NAME) String::class.java else null
        }
        assertEquals(AOSP_NAME, DeviceInfoMonitor.resolveNetworkSpeedViewClassName(loader, probe))
    }

    @Test
    fun resolvesMiuiNetworkSpeedViewWhenAospMissing() {
        val loader = ClassLoader.getSystemClassLoader()
        val probe: (String, ClassLoader) -> Class<*>? = { name, _ ->
            if (name == MIUI_NAME) String::class.java else null
        }
        assertEquals(MIUI_NAME, DeviceInfoMonitor.resolveNetworkSpeedViewClassName(loader, probe))
    }

    @Test
    fun returnsNullWhenNeitherClassExists() {
        val loader = ClassLoader.getSystemClassLoader()
        val probe: (String, ClassLoader) -> Class<*>? = { _, _ -> null }
        assertNull(DeviceInfoMonitor.resolveNetworkSpeedViewClassName(loader, probe))
    }

    @Test
    fun propagatesFatalErrorsDuringResolution() {
        val loader = ClassLoader.getSystemClassLoader()
        val probe: (String, ClassLoader) -> Class<*>? = { _, _ -> throw OutOfMemoryError("oom") }
        try {
            DeviceInfoMonitor.resolveNetworkSpeedViewClassName(loader, probe)
            fail("OutOfMemoryError must propagate")
        } catch (_: OutOfMemoryError) {
            // Expected.
        }
    }
}
