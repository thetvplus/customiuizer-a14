package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeModeButtonVisibilityContractTest {
    @Test
    fun visibilityIsPreparedAsSnapshotAndAppliedAtTheRomStateBoundary() {
        val source = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt"
        )
        val section = source.substringAfter("fun VolumeModeButtonColorsHook(")
            .substringBefore("fun ControlCenterPluginHook(")

        // The hook must refresh the process-owned visibility snapshot at install time and register
        // the process-owned preference observer exactly once through a single helper.
        assertTrue(section.contains("installVolumeModeButtonVisibilitySnapshot()"))

        // The ROM helper must still be hooked at constructor and updateState boundaries.
        assertTrue(section.contains("MiuiRingerModeLayout\\\$RingerButtonHelper"))
        assertTrue(section.contains("\"updateState\""))

        // The role and root must be bound from proven ROM inputs at construction:
        // the constructor View argument is the whole shortcut root, and mIsZen is the explicit
        // role field (false = Mute, true = DND).  No resource-name heuristics or mRingerMode
        // fallback may remain in the production hook.
        assertTrue(section.contains("getArgs().getOrNull(1)"))
        assertTrue(section.contains("getBooleanField(helper, \"mIsZen\")"))
        assertTrue(section.contains("WeakReference(root)"))
        assertTrue(section.contains("VolumeModeButtonVisibilityOwnership"))
        assertTrue(section.contains("romVisibility = root.visibility"))
        assertTrue(!section.contains("mRingerMode"))
        assertTrue(!section.contains("getResourceEntryName"))
        assertTrue(!section.contains("getParent"))
        assertTrue(!section.contains("classifyVolumeModeButton"))

        // The hot visibility callback must read from the prepared snapshot and pre-bound role/root.
        val visibilityBody = section.substringAfter("val applyVisibility = { helper: Any ->")
            .substringBefore("val applyColors = { helper: Any ->")
        assertTrue(visibilityBody.contains("val snapshot = volumeModeButtonVisibilitySnapshot"))
        assertTrue(visibilityBody.contains("snapshot.hideMute"))
        assertTrue(visibilityBody.contains("snapshot.hideDnd"))
        assertTrue(visibilityBody.contains("reconcileVolumeModeButtonVisibility"))
        assertTrue(visibilityBody.contains("NO_VISIBILITY_WRITE"))
        assertTrue(!visibilityBody.contains("View.VISIBLE"))

        assertTrue(!visibilityBody.contains("MainModule.mPrefs.get"))
        assertTrue(!visibilityBody.contains("getResourceEntryName"))
        assertTrue(!visibilityBody.contains("getIdentifier"))
        assertTrue(!visibilityBody.contains("findViewById"))
        assertTrue(!visibilityBody.contains("getParent"))
        assertTrue(!visibilityBody.contains("resources"))
        assertTrue(!visibilityBody.contains("mRingerMode"))
        assertTrue(!visibilityBody.contains("classifyVolumeModeButton"))

        // Constructor and updateState hooks must not swallow ROM throwables.
        assertTrue(section.contains("callback.getThrowable()"))
        assertTrue(!section.contains("setThrowable(null)"))

        // Shared container visibility must be bound at construction and reconciled on the hot path
        // using the same ownership state machine as the button roots.
        assertTrue(section.contains("VolumeModeSharedVisibilityState"))
        assertTrue(section.contains("bindVolumeModeButtonSharedState"))
        assertTrue(section.contains("applyVolumeModeSharedVisibility"))
        assertTrue(section.contains("shouldHideVolumeModeDivider"))
        assertTrue(section.contains("shouldHideVolumeModeContainer"))
        assertTrue(!section.contains("View.VISIBLE"))

        // Exact shared view names may only appear in the cold bind path, not the hot apply path.
        assertTrue(source.contains("miui_volume_ringer_divider"))
        assertTrue(!visibilityBody.contains("miui_volume_ringer"))

        // No updateState before hook is required. Color operations are owned by
        // VolumeModeButtonColorsContractTest; this contract only checks visibility boundaries.
        assertTrue(!section.contains("BeforeHookCallback"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
