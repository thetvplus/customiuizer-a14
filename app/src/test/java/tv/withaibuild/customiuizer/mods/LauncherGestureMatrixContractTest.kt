package tv.withaibuild.customiuizer.mods

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherGestureMatrixContractTest {

    private val prefs = Files.readString(Path.of("src/main/res/xml/prefs_launcher.xml"))
    private val launcherUi = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/subs/Launcher.kt")
    )
    private val features = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt")
    )
    private val hooks = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt")
    )
    private val folders = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt")
    )
    private val config = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/utils/GlobalActionConfig.kt")
    )
    private val shake = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/mods/utils/ShakeManager.kt")
    )

    @Test
    fun tenGestureUiKeysOpenMultiActionAndHaveRuntimeKeys() {
        val uiKeys = listOf(
            "pref_key_launcher_swipedown",
            "pref_key_launcher_swipedown2",
            "pref_key_launcher_swipeup",
            "pref_key_launcher_swipeup2",
            "pref_key_launcher_swiperight",
            "pref_key_launcher_swipeleft",
            "pref_key_launcher_shake",
            "pref_key_launcher_doubletap",
            "pref_key_launcher_pinch",
            "pref_key_launcher_spread",
        )
        for (key in uiKeys) {
            assertTrue("$key missing from prefs_launcher.xml", prefs.contains("android:key=\"$key\""))
            assertTrue("$key missing MultiAction click", launcherUi.contains("\"$key\""))
        }
        val runtimeKeys = listOf(
            "launcher_swipedown",
            "launcher_swipedown2",
            "launcher_swipeup",
            "launcher_swipeup2",
            "launcher_swiperight",
            "launcher_swipeleft",
            "launcher_shake",
            "launcher_doubletap",
            "launcher_pinch",
            "launcher_spread",
        )
        for (key in runtimeKeys) {
            assertTrue("$key missing from GlobalActionConfig", config.contains("\"$key\""))
        }
    }

    @Test
    fun verticalFourShareHomescreenSwipesFeatureAndResolver() {
        assertTrue(features.contains("prefs.getInt(\"launcher_swipedown_action\", 1) != 1"))
        assertTrue(features.contains("prefs.getInt(\"launcher_swipeup_action\", 1) != 1"))
        assertTrue(features.contains("prefs.getInt(\"launcher_swipedown2_action\", 1) != 1"))
        assertTrue(features.contains("prefs.getInt(\"launcher_swipeup2_action\", 1) != 1"))
        assertTrue(features.contains("LauncherGestureHooks.HomescreenSwipesHook"))
        assertTrue(hooks.contains("onVerticalGesture"))
        assertTrue(hooks.contains("LauncherVerticalGesture.resolveKey("))
        assertTrue(hooks.contains("GlobalActions.handleAction(helperContext, key)"))
    }

    @Test
    fun remainingSixHaveDedicatedInstallersAndActionKeys() {
        assertTrue(features.contains("prefs.getInt(\"launcher_swipeleft_action\", 1) != 1"))
        assertTrue(features.contains("prefs.getInt(\"launcher_swiperight_action\", 1) != 1"))
        assertTrue(features.contains("LauncherGestureHooks.HotSeatSwipesHook"))
        assertTrue(hooks.contains("\"launcher_swipeleft\""))
        assertTrue(hooks.contains("\"launcher_swiperight\""))

        assertTrue(features.contains("prefs.getInt(\"launcher_shake_action\", 1) != 1"))
        assertTrue(features.contains("LauncherGestureHooks.ShakeHook"))
        assertTrue(shake.contains("handleAction(helperContext, \"pref_key_launcher_shake\")"))

        assertTrue(features.contains("prefs.getInt(\"launcher_doubletap_action\", 1) != 1"))
        assertTrue(features.contains("LauncherGestureHooks.LauncherDoubleTapHook"))
        assertTrue(hooks.contains("DoubleTapController(args[0] as Context, \"launcher_doubletap\")"))

        assertTrue(features.contains("prefs.getInt(\"launcher_pinch_action\", 1) != 1"))
        assertTrue(features.contains("LauncherGestureHooks.LauncherPinchHook"))
        assertTrue(hooks.contains("\"launcher_pinch\""))

        assertTrue(features.contains("prefs.getBoolean(\"launcher_privacyapps_gest\") || prefs.getInt(\"launcher_spread_action\", 1) != 1"))
        assertTrue(folders.contains("startSecurityHide"))
        assertTrue(folders.contains("\"launcher_spread\""))
    }
}
