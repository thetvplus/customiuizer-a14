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
        assertTrue(section.contains("colorState.snapshot = volumeModeButtonColorSnapshot"))
        assertTrue(section.contains("if (!snapshot.enabled)"))

        // Preserve the ROM's blur/outline pipeline and smooth drawable mask. Selected blend
        // arrays replace native inputs; non-blur shapes receive the configured solid fill.
        assertTrue(section.contains("standardView.background"))
        assertTrue(section.contains("\"setMiViewBlurAndBlendColor\""))
        assertTrue(section.contains("callback.getArgs()[colorsIndex] = colors"))
        assertTrue(section.contains("volumeModeGradientDrawable(background, smoothChildMethod)"))
        assertTrue(source.contains("?.mutate() as? GradientDrawable"))
        assertTrue(section.contains("gradient.color = colors"))
        assertTrue(section.contains("icon.colorFilter = filter"))

        // The icon fallback replaces source color/alpha rather than compounding native alpha.
        assertTrue(source.contains("PorterDuffColorFilter"))
        assertTrue(source.contains("PorterDuff.Mode.SRC_IN"))

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
        assertTrue(applyColorsBody.contains("mStateField?.getBoolean(helper)"))
        assertFalse(applyColorsBody.contains("getBooleanField"))
        assertFalse(applyColorsBody.contains("findField"))
        assertFalse(applyColorsBody.contains("declaredFields"))

        // Restore our writes before the ROM paints inactive/theme/disabled states; retain
        // native writes by checking exact ownership, including the smooth drawable child.
        assertTrue(applyColorsBody.contains("isSelected"))
        assertTrue(applyColorsBody.contains("restoreOwnedColors()"))
        assertTrue(source.contains("drawable.color === appliedBackgroundColors"))
        assertTrue(source.contains("icon.colorFilter === iconFilter"))
        assertTrue(source.contains("icon.colorFilter = originalIconFilter"))
        assertTrue(source.contains("WeakReference<GradientDrawable>"))
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
