package tv.withaibuild.customiuizer.installers

import io.github.libxposed.api.XposedModuleInterface
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class SystemUiInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (scope == ProcessScope.SYSTEM_UI) {",
            "if (scope == ProcessScope.GUARD_PROVIDER) {"
        )

        assertTrue(
            "MainModule must delegate SystemUI bootstrap to SystemUiBootstrapCoordinator",
            section.contains("SystemUiBootstrapCoordinator.install(lpparam, mPrefs, this::initPrefs, prefReady);")
        )
    }

    @Test
    fun coordinatorOwnsSystemUiLifecycle() {
        val coordinator = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt")

        assertTrue(
            "SystemUiBootstrapCoordinator must install a hook on SystemUIInitializer.init",
            coordinator.contains("com.android.systemui.SystemUIInitializer") &&
                coordinator.contains("\"init\"")
        )
        assertTrue(
            "SystemUiBootstrapCoordinator must initialize ReflectionCache",
            coordinator.contains("ReflectionCache.onSafeLifecycle")
        )
        assertTrue(
            "10-second restart guard must live in SystemUiBootstrapCoordinator",
            coordinator.contains("currentTime - restartTime < restartThresholdMs")
        )
        assertTrue(
            "SystemUiBootstrapCoordinator must delegate non-essential hooks to SystemUiInstaller",
            coordinator.contains("SystemUiInstaller.install(lpparam, mPrefs)")
        )
        assertTrue(
            "SystemUiBootstrapCoordinator must call FatalErrors.rethrowIfFatal in catch(Throwable)",
            coordinator.contains("FatalErrors.rethrowIfFatal(")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installerClass = Class.forName("tv.withaibuild.customiuizer.installers.SystemUiInstaller")

        val install = installerClass.getMethod(
            "install",
            XposedModuleInterface.PackageReadyParam::class.java,
            PrefMap::class.java
        )

        assertTrue("install method must be public", java.lang.reflect.Modifier.isPublic(install.modifiers))
        assertTrue("install method must be static", java.lang.reflect.Modifier.isStatic(install.modifiers))
        assertEquals("install must return void", Void.TYPE, install.returnType)
        assertEquals(
            "install first parameter must be libxposed PackageReadyParam",
            XposedModuleInterface.PackageReadyParam::class.java,
            install.parameterTypes[0]
        )
        assertEquals(
            "install second parameter must be PrefMap",
            PrefMap::class.java,
            install.parameterTypes[1]
        )

        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.java")
        assertFalse(
            "installer must not reference legacy Xposed package",
            installer.contains("de.robv.android.xposed")
        )
        assertFalse(
            "installer must not use legacy XC_LoadPackage",
            installer.contains("XC_LoadPackage")
        )
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        val candidates = when {
            relativePath.endsWith(".java") -> listOf(
                relativePath.replace(".java", ".kt"),
                relativePath
            )
            relativePath.endsWith(".kt") -> listOf(
                relativePath,
                relativePath.replace(".kt", ".java")
            )
            else -> listOf(relativePath)
        }
        while (true) {
            for (path in candidates) {
                val candidate = File(directory, path)
                if (candidate.isFile) return candidate.readText()
            }
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return substring(startIndex, endIndex)
    }
}
