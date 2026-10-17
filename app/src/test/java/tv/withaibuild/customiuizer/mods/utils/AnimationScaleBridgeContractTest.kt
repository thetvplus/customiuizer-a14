package tv.withaibuild.customiuizer.mods.utils

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks
import tv.withaibuild.customiuizer.mods.GlobalActions
import java.io.File

class AnimationScaleBridgeContractTest {
    @Test
    fun typeMappingIsExactAndBounded() {
        assertEquals(Settings.Global.WINDOW_ANIMATION_SCALE, GlobalActionSystemServerHooks.resolveAnimationScaleKey(0))
        assertEquals(Settings.Global.TRANSITION_ANIMATION_SCALE, GlobalActionSystemServerHooks.resolveAnimationScaleKey(1))
        assertEquals(Settings.Global.ANIMATOR_DURATION_SCALE, GlobalActionSystemServerHooks.resolveAnimationScaleKey(2))
        assertNull(GlobalActionSystemServerHooks.resolveAnimationScaleKey(-1))
        assertNull(GlobalActionSystemServerHooks.resolveAnimationScaleKey(3))
    }

    @Test
    fun bridgeIsAlwaysInstalledAndSignatureProtected() {
        val catalog = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt")
        val bridge = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt")
        val screen = source("app/src/main/java/tv/withaibuild/customiuizer/subs/System.kt")
        assertTrue(catalog.contains("enabled = { true }"))
        assertTrue(catalog.contains("AnimationScaleBridgeFeature(lpparam)"))
        assertTrue(bridge.contains("GlobalActions.BROADCAST_PERMISSION"))
        assertTrue(bridge.contains("ModuleHelper.isTrustedBroadcast"))
        assertTrue(screen.contains("sendOrderedBroadcastWithIdentity"))
        assertTrue(screen.contains("GlobalActions.ACTION_UNHANDLED"))
        assertEquals(
            "tv.withaibuild.customiuizer.mods.action.SetAnimationScale",
            GlobalActions.SET_ANIMATION_SCALE_ACTION
        )
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
