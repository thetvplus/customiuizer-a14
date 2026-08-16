package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PermissionControllerHooksContractTest {
    private val hooks = source(
        "app/src/main/java/tv/withaibuild/customiuizer/mods/PermissionControllerHooks.kt"
    )
    private val api102 = source(
        "app/src/main/java/tv/withaibuild/customiuizer/utils/Api102ScopeRequester.kt"
    )
    private val serviceManager = source(
        "app/src/main/java/tv/withaibuild/customiuizer/utils/XposedServiceManager.kt"
    )

    @Test
    fun nativeDialogIsFinishedWithoutChangingPermissionState() {
        assertTrue(hooks.contains("mRequestedPermissions"))
        assertTrue(hooks.contains("PermissionPromptPolicy.shouldSuppress("))
        assertTrue(hooks.contains("callMethod(activity, \"setResultAndFinish\")"))
        assertTrue(hooks.contains("callback.returnAndSkip(null)"))
        assertFalse(hooks.contains("grantRuntimePermission"))
        assertFalse(hooks.contains("revokeRuntimePermission"))
    }

    @Test
    fun api102ExclusiveScopeTypesStayInIsolatedFile() {
        assertTrue(api102.contains("XposedService.OnScopeEventListener"))
        assertTrue(api102.contains("service.requestScope("))
        assertFalse(serviceManager.contains("XposedService.OnScopeEventListener"))
        assertFalse(serviceManager.contains(".requestScope("))
        assertFalse(serviceManager.contains(".scope"))
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
