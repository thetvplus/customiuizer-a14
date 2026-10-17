package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeModeButtonColorsContractTest {
    @Test
    fun colorsArePreparedAsSnapshotAndReappliedAtTheRomStateBoundary() {
        val source = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt"
        )
        val section = source.substringAfter("fun VolumeModeButtonColorsHook(")
            .substringBefore("fun ControlCenterPluginHook(")

        // The hook must refresh the process-owned snapshot at install time and register the
        // process-owned preference observer exactly once through a single helper.
        assertTrue(section.contains("installVolumeModeButtonColorSnapshot()"))

        // The ROM helper must still be hooked at constructor and updateState boundaries.
        assertTrue(section.contains("MiuiRingerModeLayout\\\$RingerButtonHelper"))
        assertTrue(section.contains("\"updateState\""))

        // The hot callback must read from the prepared snapshot, not from preferences.
        assertTrue(section.contains("val snapshot = volumeModeButtonColorSnapshot"))
        assertTrue(section.contains("if (!snapshot.enabled)"))

        // Color must be applied through the actual visual owners: the standard view's
        // background drawable (SmoothContainerDrawable chain) and the icon's setColorFilter.
        assertTrue(section.contains("standardView.background"))
        assertTrue(section.contains("setColorFilter"))
        assertTrue(section.contains("icon.setColorFilter"))

        // The whole source prepares immutable PorterDuffColorFilters and removes the stale
        // ColorStateList tint layer.
        assertTrue(source.contains("PorterDuffColorFilter"))
        assertTrue(source.contains("PorterDuff.Mode.SRC_IN"))
        assertTrue(source.contains("PorterDuff.Mode.SRC_ATOP"))

        // The old ineffective view-tint paths must no longer be the primary implementation.
        assertFalse(source.contains("backgroundTintList"))
        assertFalse(source.contains("imageTintList"))

        val applyColorsBody = section.substringAfter("val applyColors = { helper: Any ->")
            .substringBefore("ModuleHelper.hookAllConstructors")

        // The hot applyColors body must not perform preference reads, resource discovery,
        // or ColorStateList preparation; all values must come from the prepared snapshot.
        assertFalse(applyColorsBody.contains("MainModule.mPrefs.get"))
        assertFalse(applyColorsBody.contains("ColorStateList.valueOf"))
        assertFalse(applyColorsBody.contains("resources.getIdentifier"))
        assertFalse(applyColorsBody.contains("XposedHelpers.getObjectField"))

        // The active-state is resolved once at install time from the exact plugin ClassLoader.
        // The hot callback uses the prepared Field directly and fails open if resolution failed.
        assertTrue(section.contains("resolveVolumeModeButtonStateField("))
        assertTrue(source.contains("XposedHelpers.findFieldIfExists"))
        assertTrue(applyColorsBody.contains("preparedField.getBoolean(helper)"))
        assertFalse(applyColorsBody.contains("getBooleanField"))
        assertFalse(applyColorsBody.contains("findField"))
        assertFalse(applyColorsBody.contains("declaredFields"))

        // The active-state color semantics must be respected: the custom color is applied only
        // when the ROM helper reports mState == true (selected), and the module's own color
        // filters are cleared on the active -> inactive transition when the ROM has not already
        // replaced them. Filter identity is checked through the cached PorterDuffColorFilter.
        assertTrue(applyColorsBody.contains("isSelected"))
        assertTrue(applyColorsBody.contains("if (isSelected)"))
        assertTrue(applyColorsBody.contains("background.colorFilter"))
        assertTrue(applyColorsBody.contains("icon.colorFilter"))
        assertTrue(applyColorsBody.contains("clearColorFilter()"))
        assertTrue(applyColorsBody.contains("icon.setColorFilter(null)"))
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
