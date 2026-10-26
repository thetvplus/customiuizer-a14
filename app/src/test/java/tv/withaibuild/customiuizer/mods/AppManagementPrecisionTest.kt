package tv.withaibuild.customiuizer.mods

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AppManagementPrecisionTest {
    @Test fun defaultSortOnlySeedsFreshStateAndPreservesNativeRestoration() {
        for (option in 2..4) {
            val original = Bundle().apply { putString("native", "keep") }
            val seeded = Various.initialAppSort(original, option)!!
            assertNotSame(original, seeded); assertFalse(original.containsKey("current_sort_type"))
            assertEquals("keep", seeded.getString("native"))
            assertEquals(option - 1, seeded.getInt("current_sory_type"))
            assertEquals(option - 1, seeded.getInt("current_sort_type"))
            seeded.putInt("current_sort_type", 0)
            assertSame("Restored user selection wins over the configured initial sort", seeded, Various.initialAppSort(seeded, 4))
        }
        assertNull(Various.initialAppSort(null, 1)); assertNull(Various.initialAppSort(null, 99))
    }

    @Test fun enablingAManifestDisabledAppUsesAnExplicitOverrideAndNeverTouchesAReplacedUid() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val info = PackageInfo().apply {
            packageName = "test.disabled"
            applicationInfo = ApplicationInfo().apply { packageName = "test.disabled"; uid = 10001; enabled = false }
        }
        shadowOf(pm).installPackage(info)
        val target = Various.AppStateTarget("test.disabled", 10001)
        assertTrue(Various.changeApplicationState(pm, target, true))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, pm.getApplicationEnabledSetting(target.packageName))
        assertTrue(pm.getApplicationInfo(target.packageName, 0).enabled)
        assertTrue(Various.changeApplicationState(pm, target, false))
        assertFalse(pm.getApplicationInfo(target.packageName, PackageManager.MATCH_DISABLED_COMPONENTS).enabled)
        assertFalse(Various.changeApplicationState(pm, target.copy(uid = 10002), true))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getApplicationEnabledSetting(target.packageName))
    }

    @Test fun springDurationScaleIsContinuousAndMatchesTheSquareRootFrequencyRelationship() {
        for (scale in listOf(.25f, .5f, .75f, .99f, 1f, 1.01f, 2f, 3f, 10f)) {
            val stiffness = LauncherAnimationHooks.scaleStiffness(1000f, scale)
            val durationRatio = kotlin.math.sqrt(1000.0 / stiffness)
            assertEquals(scale.toDouble(), durationRatio, .0001)
        }
        assertTrue(LauncherAnimationHooks.scaleStiffness(1000f, 0f).isFinite())
        assertTrue(LauncherAnimationHooks.scaleStiffness(1000f, 0f) > 1000f)
        assertEquals(1000f, LauncherAnimationHooks.scaleStiffness(1000f, Float.NaN), 0f)
        assertEquals(1000f, LauncherAnimationHooks.scaleStiffness(1000f, -1f), 0f)
    }
}
