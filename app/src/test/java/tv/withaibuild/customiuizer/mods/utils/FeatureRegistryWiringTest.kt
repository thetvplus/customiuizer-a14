package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedModuleInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.feature.PackagePermissionsFeatureId
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File
import java.lang.reflect.Proxy

class FeatureRegistryWiringTest {

    @Test
    fun packagePermissionsFeatureIsRegisteredInSystemServerInstaller() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt")
        val installMethod = methodBody(source, "fun install(lpparam:")

        assertTrue(
            "SystemServerInstaller should create a FeatureInstallRegistry",
            installMethod.contains("FeatureInstallRegistry()")
        )
        assertTrue(
            "SystemServerInstaller should register the PackagePermissions feature",
            installMethod.contains("PackagePermissionsFeature(")
        )
        assertTrue(
            "SystemServerInstaller should install SYSTEM_SERVER features at SYSTEM_SERVER_STARTING",
            installMethod.contains("installAll(FeatureTarget.SYSTEM_SERVER, InstallPhase.SYSTEM_SERVER_STARTING, mPrefs)")
        )
        assertFalse(
            "SystemServerInstaller install() should no longer call PackagePermissions.hook directly",
            installMethod.contains("PackagePermissions.hook(lpparam)")
        )

        val classBody = classBody(source, "internal class PackagePermissionsFeature")
        assertTrue(
            "PackagePermissions feature install() must call PackagePermissions.hook",
            classBody.contains("PackagePermissions.hook(lpparam)")
        )
        assertTrue(
            "PackagePermissions feature install() must return Installed",
            classBody.contains("FeatureInstallResult.INSTALLED")
        )
    }

    @Test
    fun packagePermissionsFeatureDefinitionIsCorrect() {
        val feature = PackagePermissionsFeature(fakeSystemServerStartingParam(), PrefMap())

        assertEquals(PackagePermissionsFeatureId, feature.id)
        assertEquals("Package permissions", feature.name)
        assertNull(feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)
        assertEquals(InstallPhase.SYSTEM_SERVER_STARTING, feature.phase)
        assertTrue(feature.isEnabled(PrefMap()))
    }

    @Test
    fun featureTargetSystemServerExists() {
        assertEquals(FeatureTarget.SYSTEM_SERVER, FeatureTarget.valueOf("SYSTEM_SERVER"))
    }

    private fun fakeSystemServerStartingParam(): XposedModuleInterface.SystemServerStartingParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.SystemServerStartingParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.SystemServerStartingParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "toString" -> "FakeSystemServerStartingParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.SystemServerStartingParam
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun methodBody(source: String, header: String): String {
        val start = source.indexOf(header)
        check(start >= 0) { "Method header '$header' not found" }
        val bodyStart = source.indexOf('{', start)
        check(bodyStart >= 0) { "Method body start not found for '$header'" }
        var braceCount = 0
        var i = bodyStart
        while (i < source.length) {
            when (source[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        return source.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        error("Method closing brace not found for '$header'")
    }

    private fun classBody(source: String, header: String): String {
        val start = source.indexOf(header)
        check(start >= 0) { "Class header '$header' not found" }
        val bodyStart = source.indexOf('{', start)
        check(bodyStart >= 0) { "Class body start not found for '$header'" }
        var braceCount = 0
        var i = bodyStart
        while (i < source.length) {
            when (source[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        return source.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        error("Class closing brace not found for '$header'")
    }
}
