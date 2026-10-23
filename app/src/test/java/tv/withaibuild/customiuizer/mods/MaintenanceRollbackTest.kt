package tv.withaibuild.customiuizer.mods

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowApplicationPackageManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [MaintenanceRollbackTest.ThrowAfterMutation::class])
class MaintenanceRollbackTest {
    @Test fun partialComponentFailureRestoresEvenTheSetterThatChangedStateThenThrew() {
        val context: Context = RuntimeEnvironment.getApplication()
        val names = arrayOf("com.miui.daemon.performance.cloudcontrol.CloudControlSyncService",
            "com.miui.daemon.mqsas.jobs.EventUploadService")
        val packageId = "com.miui.daemon"
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = packageId
            applicationInfo = ApplicationInfo().apply { packageName = packageId; flags = ApplicationInfo.FLAG_SYSTEM }
            services = names.map { ServiceInfo().apply { packageName = packageId; name = it } }.toTypedArray()
        })
        context.packageManager.setComponentEnabledSetting(ComponentName(packageId, names[0]), 1, 0)
        val apply = GlobalActionSystemServerHooks::class.java.getDeclaredMethod("applyXiaomiComponentStates",
            Context::class.java, Array<String>::class.java, Array<String>::class.java, IntArray::class.java).apply { isAccessible = true }
        assertEquals(false, apply.invoke(GlobalActionSystemServerHooks, context, arrayOf(packageId, packageId), names, intArrayOf(2, 2)))
        assertEquals(1, context.packageManager.getComponentEnabledSetting(ComponentName(packageId, names[0])))
        assertEquals(0, context.packageManager.getComponentEnabledSetting(ComponentName(packageId, names[1])))
    }

    @Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
    class ThrowAfterMutation : ShadowApplicationPackageManager() {
        @Implementation
        override fun setComponentEnabledSetting(componentName: ComponentName, newState: Int, flags: Int) {
            super.setComponentEnabledSetting(componentName, newState, flags)
            if (componentName.className.endsWith("EventUploadService") && newState == 2)
                throw IllegalStateException("Binder failed after mutation")
        }
    }
}
