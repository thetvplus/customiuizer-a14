package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A14 P4-C1 architecture gates.
 *
 * These tests read source and compiled contracts so they can be RED before the
 * production refactor lands.  They do not rely on JVM class-loading of
 * SystemUIStatusBarHooks, which would itself initialize the object and break the
 * very property they are meant to verify.
 */
class SystemUiResourceBootstrapArchitectureTest {

    @Test
    fun bootstrapCoordinator_doesNotReferenceSystemUIStatusBarHooksSetupStatusBar() {
        val coordinator = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt")

        assertFalse(
            "SystemUiBootstrapCoordinator must not call SystemUIStatusBarHooks.setupStatusBar",
            coordinator.contains("SystemUIStatusBarHooks.setupStatusBar")
        )
        assertTrue(
            "SystemUiBootstrapCoordinator must call setupSystemUiResources",
            coordinator.contains("setupSystemUiResources(context)")
        )
    }

    @Test
    fun bootstrapCoordinator_doesNotImportSystemUIStatusBarHooks() {
        val coordinator = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt")

        assertFalse(
            "SystemUiBootstrapCoordinator no longer needs SystemUIStatusBarHooks import",
            coordinator.contains("import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks")
        )
    }

    @Test
    fun systemUiResourceBootstrap_existsAsTopLevelFile() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "SystemUiResourceBootstrap must expose setupSystemUiResources",
            bootstrap.contains("fun setupSystemUiResources(")
        )
        assertTrue(
            "SystemUiResourceBootstrap must own statusbarTextIconLayoutResId",
            bootstrap.contains("statusbarTextIconLayoutResId")
        )
    }

    @Test
    fun systemUiResourceBootstrap_hasNoAbstractions() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertFalse(
            "SystemUiResourceBootstrap must not declare an object",
            bootstrap.contains("object ")
        )
        assertFalse(
            "SystemUiResourceBootstrap must not declare a class",
            bootstrap.contains("class ")
        )
        assertFalse(
            "SystemUiResourceBootstrap must not declare a data class",
            bootstrap.contains("data class ")
        )
        assertFalse(
            "SystemUiResourceBootstrap must not use Manager",
            bootstrap.contains("Manager")
        )
        assertFalse(
            "SystemUiResourceBootstrap must not use Registry",
            bootstrap.contains("Registry")
        )
        assertFalse(
            "SystemUiResourceBootstrap must not use State",
            bootstrap.contains("State")
        )
    }

    @Test
    fun systemUIStatusBarHooks_hasNoSetupStatusBar() {
        val hooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt")

        assertFalse(
            "SystemUIStatusBarHooks.setupStatusBar must be removed",
            hooks.contains("fun setupStatusBar(")
        )
        assertFalse(
            "SystemUIStatusBarHooks must not own statusbarTextIconLayoutResId",
            hooks.contains("private var statusbarTextIconLayoutResId")
        )
    }

    @Test
    fun systemUIStatusBarHooks_createStatusbarTextIconUsesExternalLayoutId() {
        val hooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt")

        assertFalse(
            "SystemUIStatusBarHooks must not declare its own statusbarTextIconLayoutResId field",
            hooks.contains("private var statusbarTextIconLayoutResId")
        )
        assertTrue(
            "SystemUIStatusBarHooks.createStatusbarTextIcon must use the layout id from SystemUiResourceBootstrap",
            hooks.contains("SystemUiResourceBootstrap") ||
                hooks.contains("tv.withaibuild.customiuizer.mods.utils.statusbarTextIconLayoutResId")
        )
    }

    @Test
    fun noProductionSetupStatusBarCallers() {
        val root = projectRoot()
        val mainDir = File(root, "app/src/main")
        val matches = mainDir.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
            .filter { it.readText().contains("SystemUIStatusBarHooks.setupStatusBar") }
            .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
            .toList()

        assertEquals(
            "There must be no production callers of SystemUIStatusBarHooks.setupStatusBar: $matches",
            emptyList<String>(),
            matches
        )
    }

    @Test
    fun newBootstrapCallerIsOnlyInCoordinatorAndBootstrapFile() {
        val root = projectRoot()
        val mainDir = File(root, "app/src/main")
        val matches = mainDir.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
            .filter { it.readText().contains("setupSystemUiResources(") }
            .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
            .sorted()
            .toList()

        assertEquals(
            "setupSystemUiResources must only appear in the bootstrap file and coordinator",
            listOf(
                "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt",
                "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt"
            ),
            matches
        )
    }

    private fun source(relativePath: String): String {
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun projectRoot(): File {
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            if (File(directory, ".git").isDirectory) return directory
            directory = directory.parentFile
                ?: error("Repository root not found")
        }
    }
}
