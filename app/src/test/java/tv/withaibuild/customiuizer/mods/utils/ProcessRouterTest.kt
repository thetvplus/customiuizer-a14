package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessRouterTest {

    @Test
    fun resolvesPrimaryProcesses() {
        assertEquals(ProcessScope.SYSTEM_SERVER, ProcessRouter.resolve("android", "system_server"))
        assertEquals(ProcessScope.SYSTEM_UI, ProcessRouter.resolve("com.android.systemui", "com.android.systemui"))
        assertEquals(ProcessScope.LAUNCHER, ProcessRouter.resolve("com.miui.home", "com.miui.home"))
        assertEquals(ProcessScope.POWER_KEEPER, ProcessRouter.resolve("com.miui.powerkeeper", "com.miui.powerkeeper"))
        assertEquals(ProcessScope.GUARD_PROVIDER, ProcessRouter.resolve("com.miui.guardprovider", "com.miui.guardprovider"))
        assertEquals(ProcessScope.PHONE, ProcessRouter.resolve("com.android.incallui", "com.android.incallui"))
        assertEquals(ProcessScope.PACKAGE_INSTALLER, ProcessRouter.resolve("com.miui.packageinstaller", "com.miui.packageinstaller"))
        assertEquals(ProcessScope.PERMISSION_CONTROLLER, ProcessRouter.resolve("com.android.permissioncontroller", null))
        assertEquals(ProcessScope.PERMISSION_CONTROLLER, ProcessRouter.resolve("com.google.android.permissioncontroller", null))
        assertEquals(
            ProcessScope.UNSUPPORTED,
            ProcessRouter.resolve(
                "com.google.android.permissioncontroller",
                "com.google.android.permissioncontroller:remote"
            )
        )
    }

    @Test
    fun resolvesSystemUiPluginAsNonInstallable() {
        assertEquals(ProcessScope.SYSTEM_UI, ProcessRouter.resolve("com.android.systemui", "com.android.systemui"))
        assertEquals(ProcessScope.SYSTEM_UI, ProcessRouter.resolve("com.android.systemui", null))
        assertEquals(ProcessScope.SYSTEM_UI_PLUGIN, ProcessRouter.resolve("com.android.systemui", "com.android.systemui:plugin"))
        assertEquals(ProcessScope.SYSTEM_UI_PLUGIN, ProcessRouter.resolve("com.android.systemui", "com.android.systemui:remote"))
        assertTrue(ProcessScope.SYSTEM_UI.isInstallable)
        assertFalse(ProcessScope.SYSTEM_UI_PLUGIN.isInstallable)
    }

    @Test
    fun resolvesSettingsAndSecurityCenterVariants() {
        assertEquals(ProcessScope.SETTINGS_MAIN, ProcessRouter.resolve("com.android.settings", "com.android.settings"))
        assertEquals(ProcessScope.SETTINGS_REMOTE, ProcessRouter.resolve("com.android.settings", "com.android.settings:remote"))
        assertEquals(ProcessScope.SECURITY_CENTER_MAIN, ProcessRouter.resolve("com.miui.securitycenter", "com.miui.securitycenter"))
        assertEquals(ProcessScope.SECURITY_CENTER_BOOTAWARE, ProcessRouter.resolve("com.miui.securitycenter", "com.miui.securitycenter.bootaware"))
        assertEquals(ProcessScope.SECURITY_CENTER_REMOTE, ProcessRouter.resolve("com.miui.securitycenter", "com.miui.securitycenter:remote"))
    }

    @Test
    fun resolvesMediaAndWallpaper() {
        assertEquals(ProcessScope.WALLPAPER, ProcessRouter.resolve("com.miui.miwallpaper", "com.miui.miwallpaper"))
        assertEquals(ProcessScope.MEDIA, ProcessRouter.resolve("com.miui.screenshot", "com.miui.screenshot"))
        assertEquals(ProcessScope.MEDIA, ProcessRouter.resolve("com.miui.gallery", "com.miui.gallery"))
    }

    @Test
    fun resolvesInputMethods() {
        val exact = listOf(
            "com.baidu.input",
            "com.baidu.input_mi",
            "com.iflytek.inputmethod",
            "com.iflytek.inputmethod.miui",
            "com.sohu.inputmethod.sogou",
            "com.sohu.inputmethod.sogou.xiaomi",
        )
        for (pkg in exact) {
            assertEquals(pkg, ProcessScope.INPUT_METHOD, ProcessRouter.resolve(pkg, pkg))
        }
        assertEquals(ProcessScope.INPUT_METHOD, ProcessRouter.resolve("com.google.android.inputmethod.pinyin", null))
        assertEquals(ProcessScope.INPUT_METHOD, ProcessRouter.resolve("com.touchtype.swiftkey", null))
        assertEquals(ProcessScope.INPUT_METHOD, ProcessRouter.resolve("com.tencent.wetype", null))
    }

    @Test
    fun resolvesUnsupportedAndGeneric() {
        assertEquals(ProcessScope.UNSUPPORTED, ProcessRouter.resolve("com.android.location.fused", null))
        assertEquals(ProcessScope.NETWORK_STACK, ProcessRouter.resolve("com.android.networkstack", null))
        assertEquals(ProcessScope.GENERIC_APP, ProcessRouter.resolve("com.example.someapp", null))
    }

    @Test
    fun installableScopes() {
        assertTrue(ProcessScope.SYSTEM_UI.isInstallable)
        assertFalse(ProcessScope.SYSTEM_UI_PLUGIN.isInstallable)
        assertTrue(ProcessScope.LAUNCHER.isInstallable)
        assertTrue(ProcessScope.SETTINGS_MAIN.isInstallable)
        assertFalse(ProcessScope.SETTINGS_REMOTE.isInstallable)
        assertFalse(ProcessScope.SECURITY_CENTER_REMOTE.isInstallable)
        assertFalse(ProcessScope.SECURITY_CENTER_BOOTAWARE.isInstallable)
        assertFalse(ProcessScope.NETWORK_STACK.isInstallable)
        assertFalse(ProcessScope.UNSUPPORTED.isInstallable)
    }
}
