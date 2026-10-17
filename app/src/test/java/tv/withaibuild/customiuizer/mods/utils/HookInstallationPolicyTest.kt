package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallationPolicyTest {
    @Test
    fun stableHookIdsAreFixedShortUniqueStrings() {
        val ids = listOf(HookInstallation.STABLE_ID_SYSTEMUI_INIT,
            HookInstallation.STABLE_ID_PLUGIN_CREATE, HookInstallation.STABLE_ID_APPLICATION_ATTACH)
        assertEquals(listOf("systemui.init", "systemui.plugin.create", "application.attach"), ids)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotEmpty() && it.length <= 32 })
    }
}
